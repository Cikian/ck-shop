package org.jeecg.modules.shop.order.constant;

import org.apache.commons.lang3.StringUtils;

/**
 * <p>
 * 支付单状态（资金维度）。
 * </p>
 *
 * 状态机（只允许下面这些流转）：
 * <pre>
 *   INIT ──► PENDING ──► SUCCESS
 *     │         │
 *     │         ├──► FAILED
 *     │         └──► CLOSED   （超时/订单取消主动关单）
 *     └─────────► SUCCESS     （免密/极速支付，直接成功）
 * </pre>
 *
 * <b>SUCCESS 是终态但可继续发生退款</b>：退款不改支付单状态（{@code status} 仍是 SUCCESS），
 * 只累加 {@code refund_amount}。这样"渠道成功流水"与"支付单状态"始终一一对应，
 * 对账时不会因为退款而"找不到对应的成功支付单"。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public enum PaymentStatus {

    /** 初始化（本地已建单，尚未请求渠道） */
    INIT("初始化"),
    /** 待支付（已向渠道下单，等待用户付款） */
    PENDING("待支付"),
    /** 支付成功 */
    SUCCESS("支付成功"),
    /** 支付失败（渠道明确拒绝） */
    FAILED("支付失败"),
    /** 已关闭（订单取消/超时，渠道侧已失效） */
    CLOSED("已关闭");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 是否还能继续推进（用于补单任务筛选"结果未知"的支付单） */
    public boolean isSettled() {
        return this == SUCCESS || this == FAILED || this == CLOSED;
    }

    public static PaymentStatus parse(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (PaymentStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return null;
    }

    public static PaymentStatus of(String code) {
        PaymentStatus status = parse(code);
        if (status == null) {
            throw new IllegalArgumentException("未知的支付单状态: " + code);
        }
        return status;
    }
}
