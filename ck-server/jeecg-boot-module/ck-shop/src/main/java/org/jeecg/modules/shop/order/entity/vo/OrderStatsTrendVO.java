package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 订单趋势（按日）。
 * </p>
 *
 * <p>按**支付时间**归日，而不是下单时间：经营看板关心的是"钱哪天进来的"，
 * 用下单时间会出现"月底下的单、月初才付款"被算进上月 GMV 的错位，
 * 而退款又是按下单日归的话，跨月的净收入永远对不上财务。
 * 下单量本身另有一列 {@code orderCount}（按下单时间），两个口径都给出来。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
@Schema(description = "订单趋势")
public class OrderStatsTrendVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "日期 yyyy-MM-dd")
    private String date;

    @Schema(description = "下单数(按下单时间)")
    private Long orderCount;

    @Schema(description = "支付单数(按支付时间)")
    private Long paidCount;

    @Schema(description = "GMV(按支付时间)")
    private BigDecimal gmv;

    @Schema(description = "实收金额(按支付时间)")
    private BigDecimal paidAmount;

    @Schema(description = "退款金额(按退款到账时间)")
    private BigDecimal refundAmount;

    @Schema(description = "净收入 = 实收 - 退款")
    private BigDecimal netAmount;

    @Schema(description = "商品件数")
    private Long quantity;
}
