package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 订单/结算明细（前台出参，**不含成本**）。
 * </p>
 *
 * 同一个类服务两个场景：
 * <ul>
 *   <li><b>结算预览</b>：{@code available / invalidReason} 有值，用于渲染"已下架/无货"灰条；</li>
 *   <li><b>订单详情</b>：这些字段为 null，其余字段就是下单时的快照值。</li>
 * </ul>
 * 合并成一个类而不是两个，是为了保证"预览里显示的商品信息"与
 * "下单后订单里显示的商品信息"字段口径完全一致——
 * 两个类迟早会漂移，然后用户就会看到"结算页有规格、订单里没规格"。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
@Schema(description = "订单/结算明细")
public class StoreOrderItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "明细ID(结算预览时为空)")
    private String id;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "SKU组合ID; 无规格为空串")
    private String skuId;

    @Schema(description = "SKU编码")
    private String skuCode;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "规格文案")
    private String skuName;

    @Schema(description = "商品主图")
    private String productImage;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "原价/划线价")
    private BigDecimal originalPrice;

    @Schema(description = "成交单价")
    private BigDecimal price;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "原价小计")
    private BigDecimal totalAmount;

    @Schema(description = "成交小计")
    private BigDecimal productAmount;

    @Schema(description = "本行优惠金额")
    private BigDecimal discountAmount;

    @Schema(description = "本行实付")
    private BigDecimal actualAmount;

    @Schema(description = "命中的促销名称")
    private String promotionTitle;

    @Schema(description = "命中的促销类型")
    private String promotionType;

    @Schema(description = "行退款状态")
    private String refundStatus;

    @Schema(description = "已退数量")
    private Integer refundQuantity;

    @Schema(description = "已退金额")
    private BigDecimal refundAmount;

    // ==================================================================
    // 仅结算预览使用
    // ==================================================================

    @Schema(description = "是否可结算(仅预览)")
    private Boolean available;

    @Schema(description = "不可结算的原因(仅预览)")
    private String invalidReason;

    @Schema(description = "当前库存(仅预览)")
    private Integer stock;
}
