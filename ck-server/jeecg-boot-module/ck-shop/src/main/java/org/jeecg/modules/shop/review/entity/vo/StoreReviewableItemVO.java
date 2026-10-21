package org.jeecg.modules.shop.review.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 待评价明细（订单详情页「去评价」按钮的数据来源）。
 * </p>
 *
 * 只暴露"我要评价哪一行"，不带金额、不带收货人信息 —— 评价接口不需要知道这些。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Data
@Accessors(chain = true)
@Schema(description = "待评价明细")
public class StoreReviewableItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单明细ID(提交评价时回传)")
    private String orderItemId;

    @Schema(description = "商品ID(用于评价后跳转商品页)")
    private String productId;

    @Schema(description = "商品名称(下单时快照)")
    private String productName;

    @Schema(description = "规格文案(下单时快照)")
    private String skuName;

    @Schema(description = "商品主图(下单时快照)")
    private String productImage;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "是否已评价；true 时前端置灰「已评价」")
    private Boolean reviewed;
}
