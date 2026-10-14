package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 结算 / 下单的一条商品明细入参。
 * </p>
 *
 * <b>刻意只有三个字段</b>：商品、规格、数量。
 * 价格、优惠、运费一律由服务端计算——请求体里**没有金额字段**，
 * 因此"客户端改价"这条路在接口层面就不存在，而不是靠"记得校验一下"。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "结算明细入参")
public class OrderItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "商品ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productId;

    @Schema(description = "SKU组合ID；无规格商品留空")
    private String skuId;

    @Schema(description = "购买数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;
}
