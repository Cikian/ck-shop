package org.jeecg.modules.shop.order.constant;

/**
 * <p>
 * 支付渠道编码。
 * </p>
 *
 * 与 {@code PaymentGateway#channel()} 一一对应：注册表按这个码找网关实现，
 * 因此**新增渠道只需新增一个实现类 + 在这里加一个常量**，订单侧代码不需要任何改动。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public final class PayChannel {

    private PayChannel() {
    }

    /** 模拟渠道（当前唯一启用的实现；接入真实渠道后应关闭） */
    public static final String MOCK = "MOCK";
    /** 微信支付（预留） */
    public static final String WECHAT = "WECHAT";
    /** 支付宝（预留） */
    public static final String ALIPAY = "ALIPAY";
}
