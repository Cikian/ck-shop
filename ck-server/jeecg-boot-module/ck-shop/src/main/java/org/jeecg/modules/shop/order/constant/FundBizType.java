package org.jeecg.modules.shop.order.constant;

/**
 * <p>
 * 资金流水业务类型（{@code ck_shop_fund_flow.biz_type}）。
 * </p>
 *
 * @author Cikian
 * @since 2026-10-14
 */
public final class FundBizType {

    private FundBizType() {
    }

    /** 收款（支付成功） */
    public static final String PAY_IN = "PAY_IN";
    /** 退款（退款成功） */
    public static final String REFUND_OUT = "REFUND_OUT";

    /** 资金方向：流入 */
    public static final String DIRECTION_IN = "IN";
    /** 资金方向：流出 */
    public static final String DIRECTION_OUT = "OUT";
}
