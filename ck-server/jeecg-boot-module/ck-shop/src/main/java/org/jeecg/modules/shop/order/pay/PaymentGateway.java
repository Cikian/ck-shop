package org.jeecg.modules.shop.order.pay;

import org.jeecg.modules.shop.order.pay.model.PaymentModels;

/**
 * <p>
 * 支付网关 SPI —— 接入任何支付渠道只需实现这一个接口。
 * </p>
 *
 * <h3>为什么要有这一层（"预留日后接入"的具体含义）</h3>
 * 如果订单服务里直接 {@code if ("WECHAT".equals(channel)) {...} else if (...)}，
 * 那么接入支付宝时就要改订单代码，改订单代码就要回归测试订单全部流程，
 * 而订单是最不能动的模块。SPI 把"渠道差异"关在一个实现类里：
 * <ul>
 *   <li>订单侧只认 {@code channel} 字符串 + 这套出入参；</li>
 *   <li>新增渠道 = 新增一个 {@code @Component}，**订单侧零改动**；</li>
 *   <li>渠道换密钥/换域名/换协议版本，同样不影响业务代码。</li>
 * </ul>
 *
 * <h3>接入真实渠道时的检查清单</h3>
 * <ol>
 *   <li>{@link #parseNotify} 里**先验签再解析**，并且校验时间戳窗口防重放；
 *       返回 {@code signatureValid=false} 时调用方会直接拒绝整条回调。</li>
 *   <li>{@link #query} 必须能区分"渠道说没有"（NOT_FOUND）与"渠道说不清"（PENDING），
 *       前者可以关单，后者只能继续等——把它们混为一谈就是掉单的开始。</li>
 *   <li>{@link #refund} 的"受理中"（PROCESSING）不能被当成失败：
 *       很多渠道是异步退款的，接口返回成功只代表"已受理"，
 *       真正的到账结果要靠退款回调或退款查询确认。</li>
 *   <li>金额单位换算（元 ↔ 分）只在本实现类里做，且必须有单元测试。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public interface PaymentGateway {

    /**
     * 渠道编码，与 {@code PaymentGatewayRegistry} 的注册键一致。
     * 取值见 {@code PayChannel}。
     */
    String channel();

    /**
     * 向渠道下单（预支付）。
     *
     * <p>失败**不代表**交易失败，只代表这次下单请求没成功，调用方可以换支付单重试。
     */
    PaymentModels.PrepayResult prepay(PaymentModels.PrepayRequest request);

    /**
     * 解析并验证渠道异步回调。
     *
     * <p><b>实现必须做的三件事</b>：验签、校验时间戳窗口、解析出规范化的事件信息。
     * 任何一步失败都必须返回 {@code signatureValid=false}，而不是抛异常让上层去猜。
     */
    PaymentModels.NotifyResult parseNotify(PaymentModels.NotifyRequest request);

    /**
     * 主动查询支付结果。用于"渠道超时、结果未知"与定时补单。
     */
    PaymentModels.QueryResult query(PaymentModels.QueryRequest request);

    /**
     * 发起退款。
     */
    PaymentModels.RefundResult refund(PaymentModels.RefundRequest request);

    /**
     * 关闭渠道侧的支付单（订单取消/超时关单时调用）。
     *
     * <p>这一步不能省：只关本地不关渠道，用户可能拿着一张仍然有效的
     * 支付链接付款成功，而系统这边订单已关闭——又变成掉单。
     * 实现应当对"渠道说该单已支付"的情况返回明确失败，让调用方去查证。
     *
     * @return true 表示渠道侧已关闭（或本来就不存在）；false 表示关闭失败，需要人工关注
     */
    boolean close(PaymentModels.CloseRequest request);

    /**
     * 处理成功时应当返回给渠道的应答报文。
     *
     * <p>提供默认实现是为了让接入第一个真实渠道时不必回头改订单侧代码；
     * 但**接入真实渠道时必须覆盖它**：微信要求返回 JSON，支付宝要求返回纯文本
     * {@code success}，返回格式不对会被渠道判定为"处理失败"并无限重推，
     * 最终演变成回调风暴。
     */
    default String successAck() {
        return "{\"code\":\"SUCCESS\",\"message\":\"OK\"}";
    }

    /**
     * 处理失败/验签不通过时返回的应答。
     *
     * <p>同样需要在接入真实渠道时按渠道要求覆盖。
     * 注意：**验签失败必须返回失败应答**（而不是"假装成功"），
     * 否则我们等于替攻击者确认了这笔伪造交易。
     */
    default String failAck(String reason) {
        return "{\"code\":\"FAIL\",\"message\":\"" + (reason == null ? "FAIL" : reason) + "\"}";
    }
}
