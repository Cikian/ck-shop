package org.jeecg.modules.shop.order.constant;

import org.apache.commons.lang3.StringUtils;

/**
 * <p>
 * 订单售后/退款状态（订单维度）。
 * </p>
 *
 * 刻意与 {@link PayStatus} 分开：
 * 用户"申请退款"时钱还没退（{@code APPLYING}），钱真的退出去之后才影响资金状态。
 * 把两者揉成一个字段会导致"申请中"的订单被统计成"已退款"，财务报表直接错。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public enum RefundStatus {

    /** 无售后 */
    NONE("无售后"),
    /** 有退款申请待处理 */
    APPLYING("退款申请中"),
    /** 申请被拒绝（可再次申请） */
    REJECTED("退款已拒绝"),
    /** 部分退款成功 */
    PARTIAL("部分退款"),
    /** 全额退款成功 */
    FULL("全额退款");

    private final String label;

    RefundStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static RefundStatus parse(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (RefundStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return null;
    }

    public static RefundStatus of(String code) {
        RefundStatus status = parse(code);
        if (status == null) {
            throw new IllegalArgumentException("未知的退款状态: " + code);
        }
        return status;
    }
}
