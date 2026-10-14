package org.jeecg.modules.shop.cart.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 店铺端购物车汇总 VO
 * </p>
 *
 * 汇总里的 {@code validAmount} 只统计**可结算**的条目（AVAILABLE / STOCK_INSUFFICIENT），
 * 失效条目一律不计入金额——否则用户会看到"总价里包含了一件已经下架的商品"。
 *
 * 勾选合计由前端根据勾选状态自行累加（勾选是纯前端交互状态，不必每次回后端），
 * 后端只保证每条明细的 {@code lineTotal} 与 {@code selectable} 是正确的。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "店铺端购物车汇总")
public class StoreCartSummaryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "明细条目数（含失效条目）")
    private Integer totalItems;

    @Schema(description = "商品总件数（含失效条目）")
    private Integer totalQuantity;

    @Schema(description = "可结算条目数")
    private Integer validItems;

    @Schema(description = "可结算件数")
    private Integer validQuantity;

    @Schema(description = "可结算金额合计")
    private BigDecimal validAmount;

    @Schema(description = "失效条目数（下架/无货/停售）")
    private Integer invalidItems;

    @Schema(description = "是否全部失效（前端据此展示「清空失效商品」引导）")
    private Boolean allInvalid;
}
