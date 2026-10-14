package org.jeecg.modules.shop.order.constant;

import org.apache.commons.lang3.StringUtils;

/**
 * <p>
 * 订单主状态。
 * </p>
 *
 * 刻意用枚举而不是散落的字符串常量：状态是订单模块的"宪法"，
 * 一旦出现 {@code "WAITPAY"} 这种拼写错误而编译期发现不了，
 * 结果就是订单卡死在某个查不到的状态里——排查成本极高。
 *
 * <p>状态与含义：
 * <ul>
 *   <li>{@link #WAIT_PAY} 待支付 —— 下单成功，等待付款（有 pay_deadline）</li>
 *   <li>{@link #WAIT_SHIP} 待发货 —— 已付款，等待商家发货</li>
 *   <li>{@link #SHIPPED} 已发货 —— 已发出，等待买家确认收货</li>
 *   <li>{@link #COMPLETED} 已完成 —— 交易终态（退款只改 refundStatus，不回退订单状态）</li>
 *   <li>{@link #CANCELLED} 已取消 —— 终态；用户/后台取消，或全额退款后关闭</li>
 *   <li>{@link #CLOSED} 已关闭 —— 终态；超时未支付由定时任务关闭</li>
 * </ul>
 *
 * <b>终态不可回退</b>：{@code CANCELLED / CLOSED / COMPLETED} 之后不接受任何订单状态变更，
 * 这在 {@code OrderStateMachine} 里被硬编码约束，不依赖调用方自觉。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public enum OrderStatus {

    /** 待支付 */
    WAIT_PAY("待支付"),
    /** 待发货（已支付） */
    WAIT_SHIP("待发货"),
    /** 已发货 */
    SHIPPED("已发货"),
    /** 已完成 */
    COMPLETED("已完成"),
    /** 已取消 */
    CANCELLED("已取消"),
    /** 已关闭（超时未支付） */
    CLOSED("已关闭");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 是否终态。终态订单不允许再发生订单状态变更。
     */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED || this == CLOSED;
    }

    /**
     * 是否为"有效订单"。
     *
     * <p>有效订单指仍在履约链路中的订单，**逻辑层面也禁止删除/归档**。
     * 只有已取消/已关闭的订单才允许被归档（{@code del_flag = 1}）。
     */
    public boolean isActive() {
        return this == WAIT_PAY || this == WAIT_SHIP || this == SHIPPED;
    }

    /**
     * 宽松解析：数据库里可能残留历史值或大小写差异，解析失败时返回 {@code null}
     * 而不是抛异常——它主要用于"展示/兼容旧数据"场景，真正的状态流转一律用枚举本身。
     */
    public static OrderStatus parse(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (OrderStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 严格解析：解析失败直接抛异常。用于"从库里读出来的订单状态"，
     * 出现未知值说明数据已被外部改坏，必须立即暴露而不是静默继续。
     */
    public static OrderStatus of(String code) {
        OrderStatus status = parse(code);
        if (status == null) {
            throw new IllegalArgumentException("未知的订单状态: " + code);
        }
        return status;
    }

    /** 中文名，未知状态原样返回，便于前端把异常数据显示出来而不是显示空白 */
    public static String labelOf(String code) {
        OrderStatus status = parse(code);
        return status == null ? code : status.getLabel();
    }
}
