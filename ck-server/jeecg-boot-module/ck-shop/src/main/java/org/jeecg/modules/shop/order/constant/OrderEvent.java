package org.jeecg.modules.shop.order.constant;

/**
 * <p>
 * 订单状态机事件。
 * </p>
 *
 * 事件与状态分离是关键：{@code PAY_SUCCESS} 是事件，{@code WAIT_SHIP} 是结果。
 * 把两者混在一起（例如直接调用 {@code updateStatus(WAIT_SHIP)}）就无法表达
 * "什么原因导致的状态变化"，操作流水也就失去了意义。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public enum OrderEvent {

    /** 支付成功（渠道回调或主动查询确认） */
    PAY_SUCCESS("支付成功"),
    /** 用户主动取消 */
    USER_CANCEL("用户取消"),
    /** 后台取消/关闭 */
    ADMIN_CANCEL("后台取消"),
    /** 超时未支付，定时任务关闭 */
    TIMEOUT_CLOSE("超时关闭"),
    /** 商家发货 */
    SHIP("发货"),
    /** 买家确认收货 */
    CONFIRM_RECEIVE("确认收货");

    private final String label;

    OrderEvent(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
