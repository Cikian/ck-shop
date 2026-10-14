package org.jeecg.modules.shop.cart.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 加入购物车入参
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "加入购物车入参")
public class CartAddDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "商品ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productId;

    /**
     * SKU 组合ID。商品有规格时必填；无规格商品必须留空。
     * 后端会做校验：有规格却不传 SKU 一律拒绝，避免把"未选规格"的东西塞进购物车。
     */
    @Schema(description = "SKU组合ID；有规格商品必填，无规格商品留空")
    private String skuId;

    @Schema(description = "数量，默认 1")
    private Integer quantity;

    @Schema(description = "来源: detail-商品详情 / list-商品列表 / cart-购物车页 / api-其它")
    private String source;
}
