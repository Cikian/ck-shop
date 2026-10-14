package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 订单统计总览。
 * </p>
 *
 * <h3>每个指标的口径（口径不一致的统计比没有统计更危险）</h3>
 * <ul>
 *   <li><b>GMV（成交额）</b>：已支付订单的 {@code pay_amount} 之和，
 *       <b>不扣退款</b>。这是行业惯例口径，用于对比"卖了多少"。</li>
 *   <li><b>实收（paidAmount）</b>：已支付订单的 {@code paid_amount} 之和。</li>
 *   <li><b>净收入（netAmount）</b>：{@code paid_amount − refund_amount}，
 *       这才是真正留在账上的钱。<b>不能拿 GMV 当收入看</b>。</li>
 *   <li><b>毛利（grossProfit）</b>：成交金额 − 优惠 − 成本，同样**不扣退款**；
 *       要算"退了之后的真实毛利"请用 netAmount − netCost。</li>
 *   <li><b>退款率</b>：退款金额 / 实收金额。按金额而不是按单数——
 *       退 1 件 9999 元的商品与退 10 件 9.9 元的商品，对资金的影响差三个数量级。</li>
 *   <li><b>支付转化率</b>：已支付订单数 / 下单总数。
 *       这是"下单后不付款"的直接度量，掉单、支付渠道故障都会先体现在这个指标上。</li>
 *   <li><b>加购转化率</b>：下单用户数 / 加购用户数，度量"购物车到下单"的漏斗。</li>
 * </ul>
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
@Schema(description = "订单统计总览")
public class OrderStatsOverviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    // ---------- 单量 ----------

    @Schema(description = "订单总数")
    private Long totalOrders;

    @Schema(description = "待支付订单数")
    private Long waitPayOrders;

    @Schema(description = "待发货订单数(已付款)")
    private Long waitShipOrders;

    @Schema(description = "已发货订单数")
    private Long shippedOrders;

    @Schema(description = "已完成订单数")
    private Long completedOrders;

    @Schema(description = "已取消订单数")
    private Long cancelledOrders;

    @Schema(description = "已关闭订单数(超时)")
    private Long closedOrders;

    @Schema(description = "已支付订单数")
    private Long paidOrders;

    // ---------- 金额 ----------

    @Schema(description = "GMV(已支付订单应付金额合计, 不扣退款)")
    private BigDecimal gmv;

    @Schema(description = "实收金额(已支付订单实收合计)")
    private BigDecimal paidAmount;

    @Schema(description = "退款金额合计")
    private BigDecimal refundAmount;

    @Schema(description = "净收入 = 实收 - 退款")
    private BigDecimal netAmount;

    @Schema(description = "优惠金额合计")
    private BigDecimal discountAmount;

    @Schema(description = "运费合计")
    private BigDecimal freightAmount;

    @Schema(description = "成本合计")
    private BigDecimal costAmount;

    @Schema(description = "毛利合计(不扣退款)")
    private BigDecimal grossProfit;

    @Schema(description = "客单价(实收 / 已支付订单数)")
    private BigDecimal avgOrderAmount;

    @Schema(description = "商品件数合计")
    private Long totalQuantity;

    // ---------- 转化与比率 ----------

    @Schema(description = "支付转化率 = 已支付订单数 / 订单总数")
    private BigDecimal payConversionRate;

    @Schema(description = "退款率 = 退款金额 / 实收金额")
    private BigDecimal refundRate;

    @Schema(description = "毛利率 = 毛利 / 成交金额")
    private BigDecimal grossProfitRate;

    @Schema(description = "加购用户数")
    private Long cartUsers;

    @Schema(description = "下单用户数")
    private Long orderUsers;

    @Schema(description = "加购转下单转化率 = 下单用户数 / 加购用户数")
    private BigDecimal cartConversionRate;

    // ---------- 当日 ----------

    @Schema(description = "今日订单数")
    private Long todayOrders;

    @Schema(description = "今日实收金额")
    private BigDecimal todayPaidAmount;

    @Schema(description = "今日退款金额")
    private BigDecimal todayRefundAmount;

    @Schema(description = "今日GMV")
    private BigDecimal todayGmv;

    // ---------- 风险指标（这才是最该盯的） ----------

    /**
     * 掉单数：已关闭但**收到过钱**的订单。
     *
     * <p>对应文档的典型事故 #3「超时关单后支付成功，钱收了订单关闭」。
     * 这个指标**必须长期为 0**；一旦不为 0，说明有用户付了钱却拿不到货，
     * 需要立即人工介入。它不体现在任何"常规"经营指标里，
     * 所以必须单独暴露出来，否则永远不会有人发现。
     */
    @Schema(description = "异常：已关闭但已收款订单数(必须为0, 不为0需立即人工介入)")
    private Long closedButPaidOrders;

    /** 支付中（结果未知）的支付单数量：补单任务的重点观察对象 */
    @Schema(description = "支付中(结果未知)的支付单数")
    private Long pendingPayments;

    /** 待审核退款单数：客服的处理压力 */
    @Schema(description = "待审核退款单数")
    private Long pendingRefunds;
}
