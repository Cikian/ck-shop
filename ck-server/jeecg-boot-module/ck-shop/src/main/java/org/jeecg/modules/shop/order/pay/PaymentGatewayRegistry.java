package org.jeecg.modules.shop.order.pay;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 支付网关注册表：按渠道编码找实现。
 * </p>
 *
 * Spring 会把所有 {@link PaymentGateway} 实现注入到 List 里，
 * 这里按 {@code channel()} 建索引。新增渠道实现类后**无需改任何代码**即可生效。
 *
 * <h3>重复渠道的处置</h3>
 * 两个实现声明了同一个渠道编码时**直接抛异常**，而不是"后一个覆盖前一个"。
 * 覆盖式注册会让"接入支付宝时不小心复制了微信的实现类、忘了改 channel()"
 * 这种低级错误变成线上事故：请求打到错的渠道，用户付了钱，系统却在微信查不到单。
 * 启动期就炸掉，才能在这个错误上线之前发现它。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Slf4j
@Component
public class PaymentGatewayRegistry {

    private final Map<String, PaymentGateway> gateways = new HashMap<>();

    public PaymentGatewayRegistry(List<PaymentGateway> gatewayList) {
        for (PaymentGateway gateway : gatewayList) {
            String channel = StringUtils.trimToEmpty(gateway.channel()).toUpperCase();
            if (StringUtils.isBlank(channel)) {
                throw new IllegalStateException(
                        "支付网关实现 " + gateway.getClass().getName() + " 未声明渠道编码（channel() 返回空）");
            }
            PaymentGateway exists = gateways.put(channel, gateway);
            if (exists != null) {
                throw new IllegalStateException(String.format(
                        "支付渠道[%s]被重复注册：%s 与 %s。请在其中一个实现上修改 channel()，"
                                + "否则请求可能打到错误的渠道。",
                        channel, exists.getClass().getName(), gateway.getClass().getName()));
            }
        }
        log.info("支付网关注册完成，已支持渠道：{}", gateways.keySet());
    }

    /**
     * 按渠道取网关。
     *
     * @throws JeecgBootException 渠道未实现（**不做静默兜底**：
     *                            退回默认渠道会让用户付到错误的账户）
     */
    public PaymentGateway require(String channel) {
        String key = StringUtils.trimToEmpty(channel).toUpperCase();
        PaymentGateway gateway = gateways.get(key);
        if (gateway == null) {
            throw new JeecgBootException("不支持的支付渠道[" + channel + "]，"
                    + "当前已接入：" + gateways.keySet());
        }
        return gateway;
    }

    /** 是否已接入某渠道，用于"渠道维护中就不要展示该支付方式" */
    public boolean supports(String channel) {
        return gateways.containsKey(StringUtils.trimToEmpty(channel).toUpperCase());
    }

    /** 已接入的渠道列表 */
    public List<String> supportedChannels() {
        return new java.util.ArrayList<>(gateways.keySet());
    }
}
