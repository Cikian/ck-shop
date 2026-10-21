package org.jeecg.modules.shop.review.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.review.entity.ShopProductReview;
import org.jeecg.modules.shop.review.entity.dto.ReviewSubmitDTO;
import org.jeecg.modules.shop.review.entity.vo.ReviewSubmitResultVO;
import org.jeecg.modules.shop.review.entity.vo.StoreReviewSummaryVO;
import org.jeecg.modules.shop.review.entity.vo.StoreReviewVO;
import org.jeecg.modules.shop.review.entity.vo.StoreReviewableItemVO;

import java.util.List;

/**
 * <p>
 * 商品评价 Service
 * </p>
 *
 * 四条规则：
 * <ol>
 *   <li><b>先确认商品在本店可见</b>：评价列表也是商品信息的一部分，
 *       A 店不能通过评价接口读 B 店商品的评价（否则接口就成了探测器和数据泄露口）；</li>
 *   <li><b>只有买过的人能评价</b>：资格一律从 {@code ck_shop_order_item} ⋈ {@code ck_shop_order}
 *       反查（已支付且订单未取消/关闭），不信任任何前端传参；</li>
 *   <li><b>一条明细只能评价一次</b>：依赖唯一索引 {@code uk_review_order_item}，
 *       业务层只负责把撞键翻译成一句人话；</li>
 *   <li><b>汇总永远是回读值</b>：提交后在同事务内重算商品缓存列，再实时聚合一次返回，
 *       不做"旧分 + 新分"的心算。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

public interface IShopProductReviewService extends IService<ShopProductReview> {

    /**
     * 商品评价汇总（评分 / 条数 / 星级分布）。
     *
     * @param orgCode   店铺绑定部门的 org_code（用于可见性校验）
     * @param productId 商品主键
     */
    StoreReviewSummaryVO summary(String orgCode, String productId);

    /**
     * 商品评价分页列表（按评价时间倒序）。
     */
    IPage<StoreReviewVO> pageByProduct(String orgCode, String productId, Integer pageNo, Integer pageSize);

    /**
     * 某笔订单里可评价 / 已评价的明细行。
     *
     * @param context 当前登录人 + 店铺
     * @param orderNo 订单号
     */
    List<StoreReviewableItemVO> listReviewableItems(ReviewContext context, String orderNo);

    /**
     * 提交一条评价。
     *
     * @return 新建的评价 + 商品最新汇总
     */
    ReviewSubmitResultVO submit(ReviewContext context, ReviewSubmitDTO dto);
}
