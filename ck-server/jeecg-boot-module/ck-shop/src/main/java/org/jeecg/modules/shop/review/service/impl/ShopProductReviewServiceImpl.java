package org.jeecg.modules.shop.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.constant.OrderStatus;
import org.jeecg.modules.shop.order.constant.PayStatus;
import org.jeecg.modules.shop.product.mapper.ShopProductMapper;
import org.jeecg.modules.shop.product.mapper.ShopProductStatMapper;
import org.jeecg.modules.shop.review.entity.ShopProductReview;
import org.jeecg.modules.shop.review.entity.dto.ReviewSubmitDTO;
import org.jeecg.modules.shop.review.entity.vo.ReviewSubmitResultVO;
import org.jeecg.modules.shop.review.entity.vo.ReviewTargetVO;
import org.jeecg.modules.shop.review.entity.vo.StoreReviewSummaryVO;
import org.jeecg.modules.shop.review.entity.vo.StoreReviewVO;
import org.jeecg.modules.shop.review.entity.vo.StoreReviewableItemVO;
import org.jeecg.modules.shop.review.mapper.ShopProductReviewMapper;
import org.jeecg.modules.shop.review.service.IShopProductReviewService;
import org.jeecg.modules.shop.review.service.ReviewContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 商品评价 Service 实现
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Slf4j
@Service
public class ShopProductReviewServiceImpl extends ServiceImpl<ShopProductReviewMapper, ShopProductReview>
        implements IShopProductReviewService {

    /** 评价内容长度上限（与建表脚本 varchar(500) 对齐） */
    private static final int MAX_CONTENT_LENGTH = 500;

    /** 近 30 天销量的起点由 Java 算，SQL 里就不需要 DATE_SUB 这类方言函数 */
    private static final long MONTH_MILLIS = 30L * 24 * 60 * 60 * 1000;

    /** 星级桶：恒定 5 档，5 星在前（与页面展示顺序一致） */
    private static final int MAX_RATING = 5;

    @Autowired
    private ShopProductMapper shopProductMapper;

    @Autowired
    private ShopProductStatMapper shopProductStatMapper;

    // ==================================================================
    // 汇总 / 列表（匿名可访问）
    // ==================================================================

    @Override
    public StoreReviewSummaryVO summary(String orgCode, String productId) {
        String pid = requireVisibleProduct(orgCode, productId);
        return buildSummary(pid);
    }

    @Override
    public IPage<StoreReviewVO> pageByProduct(String orgCode, String productId, Integer pageNo, Integer pageSize) {
        String pid = requireVisibleProduct(orgCode, productId);
        int current = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);

        IPage<ShopProductReview> page = super.page(
                new Page<>(current, size),
                new LambdaQueryWrapper<ShopProductReview>()
                        .eq(ShopProductReview::getProductId, pid)
                        // 后台隐藏（status='N'）的评价对前台不存在：既不展示，也不进评分
                        .eq(ShopProductReview::getStatus, "Y")
                        .orderByDesc(ShopProductReview::getCreateTime)
                        .orderByDesc(ShopProductReview::getId));

        Page<StoreReviewVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<StoreReviewVO> records = new ArrayList<>(page.getRecords().size());
        for (ShopProductReview review : page.getRecords()) {
            records.add(toVO(review));
        }
        result.setRecords(records);
        return result;
    }

    // ==================================================================
    // 待评价明细
    // ==================================================================

    @Override
    public List<StoreReviewableItemVO> listReviewableItems(ReviewContext context, String orderNo) {
        requireLogin(context);
        if (StringUtils.isBlank(orderNo)) {
            throw new JeecgBootException("缺少订单号");
        }
        // 订单不存在 / 不属于本人 / 未支付 / 已取消，统一返回空列表：
        // 不区分这些情况，接口就不会变成"用订单号探测别人买过什么"的工具
        return baseMapper.selectReviewableItems(orderNo.trim(), context.getUserId());
    }

    // ==================================================================
    // 提交评价
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewSubmitResultVO submit(ReviewContext context, ReviewSubmitDTO dto) {
        requireLogin(context);
        if (dto == null || StringUtils.isBlank(dto.getOrderItemId())) {
            throw new JeecgBootException("缺少订单明细ID");
        }
        int rating = dto.getRating() == null ? 0 : dto.getRating();
        if (rating < 1 || rating > MAX_RATING) {
            throw new JeecgBootException("请选择 1-5 星评分");
        }
        String content = StringUtils.trimToNull(dto.getContent());
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            throw new JeecgBootException("评价内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }

        // 资格与归属全部从库里反查：前端只传了"哪一行明细"，别的什么都伪造不了
        ReviewTargetVO target = baseMapper.selectReviewTarget(dto.getOrderItemId().trim(), context.getUserId());
        if (target == null) {
            throw new JeecgBootException("订单明细不存在，或不属于当前账号");
        }
        PayStatus payStatus = PayStatus.parse(target.getPayStatus());
        // PAID 与 PARTIAL_REFUND 都算"收过钱"：部分退款的订单买家仍然买到了东西，理应能评价；
        // REFUNDED（全额退款）与 UNPAID 才拒绝
        if (payStatus != PayStatus.PAID && payStatus != PayStatus.PARTIAL_REFUND) {
            throw new JeecgBootException("该订单当前不可评价（未支付或已全额退款）");
        }
        OrderStatus orderStatus = OrderStatus.parse(target.getOrderStatus());
        if (orderStatus == OrderStatus.CANCELLED || orderStatus == OrderStatus.CLOSED) {
            throw new JeecgBootException("该订单当前不可评价（已取消或已关闭）");
        }
        // 评价必须落在"当前店铺"的商品上：本店只能看到本店商品的评价，
        // 提交时同样按本店口径落库（store_id 取订单上的归属，而不是前端声称的）
        if (StringUtils.isNotBlank(context.getStoreId())
                && StringUtils.isNotBlank(target.getStoreId())
                && !context.getStoreId().equals(target.getStoreId())) {
            throw new JeecgBootException("该订单不属于当前店铺，无法评价");
        }
        if (StringUtils.isNotBlank(baseMapper.selectReviewIdByOrderItem(target.getOrderItemId()))) {
            throw new JeecgBootException("该商品已评价过");
        }

        Date now = new Date();
        ShopProductReview review = new ShopProductReview()
                .setProductId(target.getProductId())
                .setStoreId(target.getStoreId())
                .setStoreOrgCode(target.getStoreOrgCode())
                .setOrderId(target.getOrderId())
                .setOrderNo(target.getOrderNo())
                .setOrderItemId(target.getOrderItemId())
                .setUserId(context.getUserId())
                .setUsername(context.getUsername())
                .setSkuCombinationId(StringUtils.trimToNull(target.getSkuId()))
                .setSkuName(target.getSkuName())
                .setRating(rating)
                .setContent(content)
                .setAnonymous(Boolean.TRUE.equals(dto.getAnonymous()))
                .setStatus("Y")
                .setCreateBy(context.getUsername())
                .setCreateTime(now)
                .setUpdateBy(context.getUsername())
                .setUpdateTime(now)
                .setDelFlag(0);
        try {
            baseMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_review_order_item 兜底：并发重复提交时先查后插必然漏过一次
            throw new JeecgBootException("该商品已评价过");
        }

        // 同事务回写商品缓存列：排序与后台统计走缓存，页面上看到的仍是实时聚合值
        shopProductStatMapper.refreshProductStats(target.getProductId(), monthSince());

        return new ReviewSubmitResultVO()
                .setReview(toVO(review))
                .setSummary(buildSummary(target.getProductId()));
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /**
     * 校验"这件商品在本店可见"，返回商品主键。
     *
     * 只查一次库里最轻的三个条件（未删除 + 已上架 + 归属部门前缀匹配），
     * 不像商品详情那样把规格/图集全捞一遍 —— 评价接口只需要一个"能不能看"的结论。
     *
     * <p>支持两种标识：商品主键，或 {@code spu_code}（前台路由里用的就是后者）。
     * 与商品详情接口同一个口径，否则会出现"商品能打开、评价区却是空的"。
     */
    private String requireVisibleProduct(String orgCode, String productIdOrSpuCode) {
        if (StringUtils.isBlank(orgCode)) {
            throw new JeecgBootException("当前店铺未绑定部门，暂时无法查看评价");
        }
        if (StringUtils.isBlank(productIdOrSpuCode)) {
            throw new JeecgBootException("缺少商品 ID");
        }
        String key = productIdOrSpuCode.trim();
        String pid = shopProductMapper.selectVisibleProductId(key, orgCode);
        if (StringUtils.isBlank(pid)) {
            // 前台路由 / 分享链接给的是 spu_code，这里补一次回退查询
            pid = shopProductMapper.selectVisibleProductIdBySpuCode(key, orgCode);
        }
        if (StringUtils.isBlank(pid)) {
            throw new JeecgBootException("商品在当前店铺不存在或已下架");
        }
        return pid;
    }

    private void requireLogin(ReviewContext context) {
        if (context == null || StringUtils.isBlank(context.getUserId())) {
            throw new JeecgBootException("请先登录");
        }
    }

    /** 近 30 天起点（Java 侧算好，避免 SQL 方言函数） */
    private Date monthSince() {
        return new Date(System.currentTimeMillis() - MONTH_MILLIS);
    }

    /**
     * 构建汇总：**一次 GROUP BY** 拿到各星级条数，条数与平均分都由它推导出来。
     *
     * 为什么不用两条 SQL（COUNT + AVG）：两条之间可能夹进别人的新评价，
     * 于是出现"12 条评价、平均分却是按 13 条算的"这种谁都不信的数字。
     */
    private StoreReviewSummaryVO buildSummary(String productId) {
        Map<Integer, Integer> bucketMap = new HashMap<>();
        for (Map<String, Object> row : baseMapper.selectRatingBuckets(productId)) {
            Integer rating = toInteger(row.get("rating"));
            Integer count = toInteger(row.get("cnt"));
            if (rating != null && count != null) {
                bucketMap.put(rating, count);
            }
        }

        List<StoreReviewSummaryVO.RatingBucketVO> distribution = new ArrayList<>(MAX_RATING);
        int total = 0;
        long weighted = 0;
        for (int star = MAX_RATING; star >= 1; star--) {
            int count = bucketMap.getOrDefault(star, 0);
            total += count;
            weighted += (long) star * count;
            distribution.add(new StoreReviewSummaryVO.RatingBucketVO().setRating(star).setCount(count));
        }

        // 保留 1 位小数；无评价给 0（前端按 reviewCount === 0 整块不渲染，不会显示"0.0 分"）
        BigDecimal avg = total == 0
                ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(weighted)
                        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);

        return new StoreReviewSummaryVO()
                .setProductId(productId)
                .setRatingAvg(avg)
                .setReviewCount(total)
                .setDistribution(distribution);
    }

    /**
     * 实体 → 出参。昵称在这里就打码：
     * 把完整账号发给浏览器，等于让任何人 F12 就能收集本店用户名单。
     * 匿名评价连打码后的账号都不给（username 为 null，前端按 i18n 显示「匿名用户」）。
     */
    private StoreReviewVO toVO(ShopProductReview review) {
        boolean anonymous = Boolean.TRUE.equals(review.getAnonymous());
        return new StoreReviewVO()
                .setId(review.getId())
                .setRating(review.getRating())
                .setContent(review.getContent())
                .setAnonymous(anonymous)
                .setUsername(anonymous ? null : maskNickname(review.getUsername()))
                .setSkuName(review.getSkuName())
                .setCreateTime(review.getCreateTime());
    }

    /**
     * 昵称打码：保留首 2 位与末 1 位（cikian → ci***n），短账号按长度递减。
     * 与手机号脱敏同样是"宁可信息少一点，也不要整份名单流出去"。
     */
    private String maskNickname(String username) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        String value = username.trim();
        if (value.length() <= 2) {
            return value.charAt(0) + "*";
        }
        if (value.length() == 3) {
            return value.substring(0, 2) + "*";
        }
        return value.substring(0, 2) + "***" + value.charAt(value.length() - 1);
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
