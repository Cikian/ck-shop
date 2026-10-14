package org.jeecg.modules.shop.order.constant;

import org.apache.commons.lang3.StringUtils;

/**
 * <p>
 * 订单支付状态（订单维度，非支付单维度）。
 * </p>
 *
 * 与 {@code ck_shop_order_payment.status}（支付单状态）**不是一回事**：
 * 订单可能有多次支付尝试（第一笔超时关闭、第二笔成功），
 * 而订单维度的支付状态只关心"这笔订单最终收没收到钱、退了多少"。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public enum PayStatus {

    /** 未支付 */
    UNPAID("未支付"),
    /** 已支付（全额） */
    PAID("已支付"),
    /** 部分退款 */
    PARTIAL_REFUND("部分退款"),
    /** 已全额退款 */
    REFUNDED("已退款");

    private final String label;

    PayStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static PayStatus parse(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (PayStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return null;
    }

    public static PayStatus of(String code) {
        PayStatus status = parse(code);
        if (status == null) {
            throw new IllegalArgumentException("未知的支付状态: " + code);
        }
        return status;
    }
}
