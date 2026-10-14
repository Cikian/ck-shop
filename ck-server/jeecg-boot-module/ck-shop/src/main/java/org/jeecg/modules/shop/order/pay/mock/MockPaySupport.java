package org.jeecg.modules.shop.order.pay.mock;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 模拟支付支持接口 —— **仅用于尚未接入真实支付渠道的阶段**。
 * </p>
 *
 * <h3>关键设计：模拟支付也走完整回调链路</h3>
 * 最容易做错的做法是"点一下支付就把订单改成已支付"：
 * 那样回调处理、验签、幂等、资金流水、库存实扣这几段代码在开发期<u>从来没被执行过</u>，
 * 等接入真实渠道时它们第一次运行就是在生产环境。
 *
 * <p>这里的做法是：模拟渠道**自己生成一条带签名的回调报文**，
 * 然后把它喂进与真实渠道完全相同的 {@code PaymentNotifyService} 入口。
 * 于是"点支付 → 回调 → 验签 → 去重 → 状态机 → 资金流水 → 库存实扣"
 * 整条链路在开发阶段就被反复跑通；
 * 接入微信/支付宝时，只是把"谁来生成这条报文"从模拟器换成真实渠道而已。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public interface MockPaySupport {

    /**
     * 构造一条**已签名**的模拟回调。
     *
     * @param paymentNo       支付单号
     * @param channelTradeNo  渠道预支付流水号（来自 prepay 阶段）
     * @param paidAmount      实付金额（元）
     * @param eventStatus     事件状态：SUCCESS / FAILED / CLOSED
     * @return 可直接投递给通知处理器的报文与请求头
     */
    SignedNotify buildSignedNotify(String paymentNo, String channelTradeNo,
                                   BigDecimal paidAmount, String eventStatus);

    /**
     * 已签名的回调报文 + 请求头。
     */
    @Data
    @Accessors(chain = true)
    class SignedNotify implements Serializable {
        private static final long serialVersionUID = 1L;

        private Map<String, String> headers = new HashMap<>();
        private String body;
    }
}
