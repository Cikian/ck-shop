package org.jeecg.modules.shop.order.statemachine;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.constant.OrderEvent;
import org.jeecg.modules.shop.order.constant.OrderStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * <p>
 * 订单状态机 —— 订单状态流转的**唯一权威**。
 * </p>
 *
 * <h3>为什么必须集中成一张表</h3>
 * 订单状态如果散落在各个 Service 里"顺手 if 一下"，迟早会出现
 * "从已取消改成已支付"、"已发货退回到待发货"这类非法回退。
 * 这类数据一旦写进库，没有任何自动手段能判断哪一个是正确状态。
 * 因此所有状态变更都必须经过 {@link #next(OrderStatus, OrderEvent)}，
 * 非法流转**直接抛异常**，不存在"警告一下继续走"。
 *
 * <h3>允许的流转</h3>
 * <pre>
 *   WAIT_PAY ──PAY_SUCCESS──► WAIT_SHIP
 *        │
 *        ├──USER_CANCEL─────► CANCELLED
 *        ├──ADMIN_CANCEL────► CANCELLED
 *        └──TIMEOUT_CLOSE───► CLOSED
 *
 *   WAIT_SHIP ──SHIP────────► SHIPPED
 *        └──────ADMIN_CANCEL► CANCELLED   （前置条件：已全额退款，见下）
 *
 *   SHIPPED ───CONFIRM_RECEIVE─► COMPLETED
 *        └──────ADMIN_CANCEL────► CANCELLED（前置条件：已全额退款，见下）
 *
 *   COMPLETED / CANCELLED / CLOSED 为终态，不接受任何流转。
 * </pre>
 *
 * <h3>前置条件（由 Service 强制校验，本类只负责拓扑）</h3>
 * <ul>
 *   <li>{@code PAY_SUCCESS}：必须存在一笔 {@code SUCCESS} 支付单，且金额与订单应付额一致；
 *       回调/查询必须能对上支付单号。</li>
 *   <li>{@code ADMIN_CANCEL} 作用于 {@code WAIT_SHIP / SHIPPED}：
 *       必须先完成**全额退款**（{@code refund_amount == paid_amount}），
 *       否则会出现"订单关了钱没退"——这是比掉单更严重的资金事故。</li>
 *   <li>{@code TIMEOUT_CLOSE}：只允许在 {@code WAIT_PAY} 且已过 {@code pay_deadline} 时使用；
 *       定时任务必须按 {@code pay_deadline} 扫描，不能"看到待支付就关"。</li>
 *   <li>{@code CONFIRM_RECEIVE}：只允许买家本人或后台代确认。</li>
 * </ul>
 *
 * <h3>与"支付成功"的竞态</h3>
 * {@code PAY_SUCCESS} 与 {@code TIMEOUT_CLOSE} 都可以把 {@code WAIT_PAY} 推走，
 * 这就是文档里"最后一秒支付、系统已关单"的掉单场景。处理方式不是靠状态机，
 * 而是靠 **CAS + 支付单二次确认**：
 * <ol>
 *   <li>关单走 {@code UPDATE ... WHERE order_status='WAIT_PAY' AND version=?}，
 *       支付成功也走同样的 CAS，两者只有一个能成功；</li>
 *   <li>若支付成功时发现订单已 {@code CLOSED}，**绝不丢弃这笔钱**：
 *       支付单照常置为 SUCCESS、写资金流水、写一条 {@code PAY_SUCCESS} 事件，
 *       同时把订单标记为"需要人工介入的已关闭但已收款"（退款或补单），
 *       由后台的异常单列表处理。宁可留一条待处理记录，也不能把钱悄悄吞掉。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public final class OrderStateMachine {

    private OrderStateMachine() {
    }

    /**
     * 流转表：from → (event → to)。
     * 用 UnmodifiableMap 包一层，防止任何调用方在运行期改坏状态机。
     */
    private static final Map<OrderStatus, Map<OrderEvent, OrderStatus>> TRANSITIONS;

    static {
        Map<OrderStatus, Map<OrderEvent, OrderStatus>> table = new EnumMap<>(OrderStatus.class);

        Map<OrderEvent, OrderStatus> waitPay = new EnumMap<>(OrderEvent.class);
        waitPay.put(OrderEvent.PAY_SUCCESS, OrderStatus.WAIT_SHIP);
        waitPay.put(OrderEvent.USER_CANCEL, OrderStatus.CANCELLED);
        waitPay.put(OrderEvent.ADMIN_CANCEL, OrderStatus.CANCELLED);
        waitPay.put(OrderEvent.TIMEOUT_CLOSE, OrderStatus.CLOSED);
        table.put(OrderStatus.WAIT_PAY, waitPay);

        Map<OrderEvent, OrderStatus> waitShip = new EnumMap<>(OrderEvent.class);
        waitShip.put(OrderEvent.SHIP, OrderStatus.SHIPPED);
        // 已付款但未发货：只有"已全额退款"之后才允许走到这里（Service 强制校验）
        waitShip.put(OrderEvent.ADMIN_CANCEL, OrderStatus.CANCELLED);
        table.put(OrderStatus.WAIT_SHIP, waitShip);

        Map<OrderEvent, OrderStatus> shipped = new EnumMap<>(OrderEvent.class);
        shipped.put(OrderEvent.CONFIRM_RECEIVE, OrderStatus.COMPLETED);
        shipped.put(OrderEvent.ADMIN_CANCEL, OrderStatus.CANCELLED);
        table.put(OrderStatus.SHIPPED, shipped);

        // COMPLETED / CANCELLED / CLOSED：刻意留空 = 终态，任何事件都不允许

        Map<OrderStatus, Map<OrderEvent, OrderStatus>> frozen = new EnumMap<>(OrderStatus.class);
        for (Map.Entry<OrderStatus, Map<OrderEvent, OrderStatus>> entry : table.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        TRANSITIONS = Collections.unmodifiableMap(frozen);
    }

    /**
     * 计算流转结果。
     *
     * @throws JeecgBootException 非法流转（含终态回退、事件不适用当前状态）
     */
    public static OrderStatus next(OrderStatus from, OrderEvent event) {
        if (from == null) {
            throw new JeecgBootException("订单状态缺失，无法执行状态流转");
        }
        if (event == null) {
            throw new JeecgBootException("状态流转事件缺失");
        }
        Map<OrderEvent, OrderStatus> allowed = TRANSITIONS.get(from);
        OrderStatus to = allowed == null ? null : allowed.get(event);
        if (to == null) {
            throw new JeecgBootException(String.format(
                    "非法的订单状态流转：%s(%s) 不允许执行【%s】。请刷新订单最新状态后重试。",
                    from.name(), from.getLabel(), event.getLabel()));
        }
        return to;
    }

    /** 判断某流转是否合法，用于"要不要尝试"的预判（例如补单任务里跳过已关单的订单） */
    public static boolean canTransfer(OrderStatus from, OrderEvent event) {
        if (from == null || event == null) {
            return false;
        }
        Map<OrderEvent, OrderStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.get(event) != null;
    }

    /** 该状态下是否还允许买家支付（用于下单/支付接口的前置判断） */
    public static boolean payable(OrderStatus status) {
        return status == OrderStatus.WAIT_PAY;
    }
}
