package org.jeecg.modules.shop.order.constant;

/**
 * <p>
 * 退款类型。
 * </p>
 *
 * <p>以前 {@code refundTypeLabel} 在 ShopRefundSupport 里写死三目表达式
 * （"全额退款"/"部分退款"），散落在业务代码中；新增类型或改文案要改多处。
 * 这里收敛成枚举，与 RefundStatus / PayStatus 等保持同一套写法。
 */
public enum RefundType {

    /** 整单全额退款 */
    FULL("全额退款"),

    /** 部分商品/部分数量退款 */
    PARTIAL("部分退款");

    private final String label;

    RefundType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 未知/空值按"部分退款"处理（与旧实现一致），避免详情页出现空标签 */
    public static RefundType of(String value) {
        if (value == null) {
            return PARTIAL;
        }
        for (RefundType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return PARTIAL;
    }
}
