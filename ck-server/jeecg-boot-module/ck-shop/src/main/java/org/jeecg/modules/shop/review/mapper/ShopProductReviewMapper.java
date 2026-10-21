package org.jeecg.modules.shop.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.review.entity.ShopProductReview;
import org.jeecg.modules.shop.review.entity.vo.ReviewTargetVO;
import org.jeecg.modules.shop.review.entity.vo.StoreReviewableItemVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 商品评价 Mapper
 * </p>
 *
 * 评价列表的分页直接走 MyBatis-Plus（实体上有 {@code @TableLogic}，未删除的行自动过滤），
 * 这里只放 MP 表达不了的几条：
 * <ul>
 *   <li>星级分布（一次 GROUP BY 出 5 个桶，绝不循环查 5 次）；</li>
 *   <li>待评价明细（要跨订单表判断"已支付且订单未取消"，属于跨模块只读查询）；</li>
 *   <li>提交前的资格校验（拿到这一行明细的订单状态与归属）。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Mapper
public interface ShopProductReviewMapper extends BaseMapper<ShopProductReview> {

    /**
     * 某商品各星级的评价条数（只统计展示中、未删除的评价）。
     *
     * @return 每行 {@code rating}（1-5）与 {@code cnt}；没有评价的星级不会出现在结果里，
     *         由 Service 补齐成 5 个桶
     */
    List<Map<String, Object>> selectRatingBuckets(@Param("productId") String productId);

    /**
     * 某笔订单里**可以评价**的明细行（含是否已评价标记）。
     *
     * 过滤条件（缺一不可）：
     * <ul>
     *   <li>订单属于当前登录人 —— 否则任何人都能拿别人的订单号去刷评价；</li>
     *   <li>订单已支付（{@code pay_status='PAID'}）且未取消/关闭 —— 没付钱的东西不能评价；</li>
     *   <li>订单与明细都未删除。</li>
     * </ul>
     */
    List<StoreReviewableItemVO> selectReviewableItems(@Param("orderNo") String orderNo,
                                                      @Param("userId") String userId);

    /**
     * 提交评价前的资格查询：拿到这一行明细的订单状态、归属店铺与买家。
     * 查不到（不存在 / 不属于本人 / 已删除）时返回 null。
     */
    ReviewTargetVO selectReviewTarget(@Param("orderItemId") String orderItemId,
                                      @Param("userId") String userId);

    /**
     * 这行明细是否已经评价过（返回已有评价的 id，没有则 null）。
     *
     * <p>它只是"提前给出友好提示"的辅助检查；真正的防重是
     * {@code uk_review_order_item} 唯一索引（并发下先查后插必输）。
     */
    String selectReviewIdByOrderItem(@Param("orderItemId") String orderItemId);
}
