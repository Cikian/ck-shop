package org.jeecg.modules.shop.review.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.shop.review.entity.dto.ReviewSubmitDTO;
import org.jeecg.modules.shop.review.entity.vo.ReviewSubmitResultVO;
import org.jeecg.modules.shop.review.entity.vo.StoreReviewSummaryVO;
import org.jeecg.modules.shop.review.entity.vo.StoreReviewVO;
import org.jeecg.modules.shop.review.entity.vo.StoreReviewableItemVO;
import org.jeecg.modules.shop.review.service.IShopProductReviewService;
import org.jeecg.modules.shop.review.service.ReviewContext;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.jeecg.modules.shop.store.StoreResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 店铺端（前台）商品评价
 * </p>
 *
 * 鉴权口径与商品接口一致：
 * <ul>
 *   <li>{@code summary / list} 打 {@link IgnoreAuth}：游客也能看评价，否则"评分 4.5"就成了登录才有的信息；</li>
 *   <li>{@code pending / submit} **必须登录**：评价资格是"买过这件东西的人"，
 *       匿名没有 userId 就无从校验。</li>
 * </ul>
 *
 * 店铺身份来自 {@link StoreResolver}，再经 {@link ShopOrgScopeResolver} 翻译成店铺绑定部门的
 * org_code —— 评价列表和商品列表必须用同一个可见性口径，否则会出现
 * "能看到商品却看不到它的评价"这种自相矛盾的页面。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Tag(name = "店铺端-商品评价")
@RestController
@RequestMapping("/shop/review/store")
@Slf4j
public class ShopStoreReviewController {

    @Autowired
    private IShopProductReviewService shopProductReviewService;

    @Autowired
    private StoreResolver storeResolver;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    @Operation(summary = "店铺端-商品评价汇总")
    @IgnoreAuth
    @GetMapping(value = "/summary")
    public Result<StoreReviewSummaryVO> summary(@RequestParam(name = "productId") String productId,
                                                HttpServletRequest req) {
        String orgCode = orgScopeResolver.resolveStoreOrgCodeOrNull(storeResolver.resolve(req));
        if (orgCode == null) {
            // 店铺没绑部门属于数据缺失：返回"零评价"而不是 500，评价区自然不渲染
            return Result.OK(emptySummary(productId));
        }
        return Result.OK(shopProductReviewService.summary(orgCode, productId));
    }

    @Operation(summary = "店铺端-商品评价列表（分页）")
    @IgnoreAuth
    @GetMapping(value = "/list")
    public Result<IPage<StoreReviewVO>> list(@RequestParam(name = "productId") String productId,
                                             @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                             @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                             HttpServletRequest req) {
        String orgCode = orgScopeResolver.resolveStoreOrgCodeOrNull(storeResolver.resolve(req));
        if (orgCode == null) {
            Page<StoreReviewVO> empty = new Page<>(pageNo == null ? 1 : pageNo,
                    pageSize == null ? 10 : pageSize, 0);
            empty.setRecords(new ArrayList<>());
            return Result.OK(empty);
        }
        return Result.OK(shopProductReviewService.pageByProduct(orgCode, productId, pageNo, pageSize));
    }

    @Operation(summary = "店铺端-某笔订单的待评价明细")
    @GetMapping(value = "/pending")
    public Result<List<StoreReviewableItemVO>> pending(@RequestParam(name = "orderNo") String orderNo,
                                                       HttpServletRequest req) {
        return Result.OK(shopProductReviewService.listReviewableItems(buildContext(req), orderNo));
    }

    @Operation(summary = "店铺端-提交评价")
    @PostMapping(value = "/submit")
    public Result<ReviewSubmitResultVO> submit(@RequestBody ReviewSubmitDTO dto, HttpServletRequest req) {
        return Result.OK(shopProductReviewService.submit(buildContext(req), dto));
    }

    // ==================================================================
    // 上下文构建
    // ==================================================================

    private ReviewContext buildContext(HttpServletRequest req) {
        LoginUser user = orgScopeResolver.currentLoginUser();
        if (user == null || user.getId() == null) {
            throw new JeecgBootException("请先登录后再评价");
        }
        ShopStore store = storeResolver.resolve(req);
        return new ReviewContext()
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setStoreId(store.getId())
                .setStoreName(store.getName())
                .setStoreOrgCode(orgScopeResolver.resolveStoreOrgCodeOrNull(store));
    }

    /** 零评价汇总：结构完整、各项为 0，前端按 reviewCount === 0 整块不渲染 */
    private StoreReviewSummaryVO emptySummary(String productId) {
        StoreReviewSummaryVO summary = new StoreReviewSummaryVO()
                .setProductId(productId)
                .setRatingAvg(BigDecimal.ZERO.setScale(1))
                .setReviewCount(0);
        List<StoreReviewSummaryVO.RatingBucketVO> distribution = new ArrayList<>(5);
        for (int star = 5; star >= 1; star--) {
            distribution.add(new StoreReviewSummaryVO.RatingBucketVO().setRating(star).setCount(0));
        }
        summary.setDistribution(distribution);
        return summary;
    }
}
