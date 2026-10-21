package org.jeecg.modules.shop.review.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 待评价明细的**内部**查询结果（不复用 {@link StoreReviewableItemVO}）。
 * </p>
 *
 * 为什么单独一个类：提交评价时要校验"这行明细能不能评价"，需要订单的支付状态、
 * 归属店铺、买家 ID 等字段；这些是服务端判断依据，绝不能出现在给前端的 VO 上。
 * 混用一个类，早晚有人图省事把 orderStatus 一路带到响应里。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Data
@Accessors(chain = true)
@Schema(description = "待评价明细(内部查询结果)")
public class ReviewTargetVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单明细ID")
    private String orderItemId;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "商品名称快照")
    private String productName;

    @Schema(description = "规格文案快照")
    private String skuName;

    @Schema(description = "商品主图快照")
    private String productImage;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "SKU组合ID快照")
    private String skuId;

    @Schema(description = "订单ID")
    private String orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "店铺ID")
    private String storeId;

    @Schema(description = "店铺绑定部门")
    private String storeOrgCode;

    @Schema(description = "订单买家ID")
    private String userId;

    @Schema(description = "订单支付状态")
    private String payStatus;

    @Schema(description = "订单状态")
    private String orderStatus;
}
