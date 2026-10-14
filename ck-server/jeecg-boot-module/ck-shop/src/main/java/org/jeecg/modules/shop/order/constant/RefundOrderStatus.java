package org.jeecg.modules.shop.order.constant;

import org.apache.commons.lang3.StringUtils;

/**
 * <p>
 * 退款单状态（资金退回维度）。
 * </p>
 *
 * 状态机：
 * <pre>
 *   APPLYING ──► REJECTED        （后台审核拒绝）
 *      │
 *      ├──► REFUNDING ──► SUCCESS
 *      │        └───────► FAILED   （渠道退款失败，可重试）
 *      └──► CLOSED                 （用户撤销/订单已处理）
 * </pre>
 *
 * @author Cikian
 * @since 2026-10-14
 */
public enum RefundOrderStatus {

    /** 待审核 */
    APPLYING("待审核"),
    /** 已拒绝 */
    REJECTED("已拒绝"),
    /** 退款中（已提交渠道，结果未知） */
    REFUNDING("退款中"),
    /** 退款成功 */
    SUCCESS("退款成功"),
    /** 退款失败 */
    FAILED("退款失败"),
    /** 已撤销 */
    CLOSED("已撤销");

    private final String label;

    RefundOrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static RefundOrderStatus parse(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (RefundOrderStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return null;
    }

    public static RefundOrderStatus of(String code) {
        RefundOrderStatus status = parse(code);
        if (status == null) {
            throw new IllegalArgumentException("未知的退款单状态: " + code);
        }
        return status;
    }
}
