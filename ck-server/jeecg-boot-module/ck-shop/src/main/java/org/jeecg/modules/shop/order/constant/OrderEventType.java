package org.jeecg.modules.shop.order.constant;

/**
 * <p>
 * 订单操作流水事件类型（{@code ck_shop_order_event.event_type}）。
 * </p>
 *
 * 用常量类而不是枚举：事件类型是"只追加的日志枚举"，未来随时会加新类型，
 * 而读日志时不应该因为"读到未知类型"就抛异常——日志的价值恰恰在于它记录了
 * 你不知道发生过的事。因此这里的类型永远只用于**写入**端校验。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public final class OrderEventType {

    private OrderEventType() {
    }

    /** 创建订单 */
    public static final String CREATE = "CREATE";
    /** 发起支付（已向渠道下单） */
    public static final String PAY_REQUEST = "PAY_REQUEST";
    /** 支付成功 */
    public static final String PAY_SUCCESS = "PAY_SUCCESS";
    /** 支付失败 */
    public static final String PAY_FAIL = "PAY_FAIL";
    /** 支付结果未知，主动查询后确认 */
    public static final String PAY_QUERY = "PAY_QUERY";
    /** 用户取消 */
    public static final String CANCEL = "CANCEL";
    /** 超时/后台关闭 */
    public static final String CLOSE = "CLOSE";
    /** 发货 */
    public static final String SHIP = "SHIP";
    /** 确认收货 */
    public static final String RECEIVE = "RECEIVE";
    /** 订单归档（仅已取消/已关闭） */
    public static final String ARCHIVE = "ARCHIVE";
    /** 修改卖家备注 */
    public static final String REMARK = "REMARK";
    /** 申请退款 */
    public static final String REFUND_APPLY = "REFUND_APPLY";
    /** 退款审核 */
    public static final String REFUND_AUDIT = "REFUND_AUDIT";
    /** 退款成功 */
    public static final String REFUND_SUCCESS = "REFUND_SUCCESS";
    /** 退款失败 */
    public static final String REFUND_FAIL = "REFUND_FAIL";
    /** 人工补单/补偿 */
    public static final String COMPENSATE = "COMPENSATE";
}
