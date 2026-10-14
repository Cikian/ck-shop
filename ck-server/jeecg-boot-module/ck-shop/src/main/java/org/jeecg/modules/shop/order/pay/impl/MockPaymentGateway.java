package org.jeecg.modules.shop.order.pay.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.shop.order.constant.PayChannel;
import org.jeecg.modules.shop.order.pay.PaymentGateway;
import org.jeecg.modules.shop.order.pay.mock.MockPaySupport;
import org.jeecg.modules.shop.order.pay.model.PaymentModels;
import org.jeecg.modules.shop.order.util.OrderNoGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 模拟支付网关 —— 未接入真实渠道时期的实现。
 * </p>
 *
 * <h3>它模拟得很"真"的地方（这些才是接真渠道时真正要验的东西）</h3>
 * <ol>
 *   <li><b>预支付 → 回调 两段式</b>：{@code prepay} 只产生渠道流水号与调起参数，
 *       支付成功必须由一条**带签名的异步回调**驱动。前端"点一下"触发的是
 *       {@code /pay/mock-confirm}，它内部生成签名回调再走标准通知入口。</li>
 *   <li><b>验签 + 时间戳窗口</b>：回调必须带 {@code mock-timestamp / mock-nonce / mock-signature}，
 *       签名是 HMAC-SHA256；时间戳超过容差直接拒绝——这正是防重放攻击的做法。</li>
 *   <li><b>可模拟失败与延迟</b>：{@code fail-rate} 让"渠道拒绝"这条分支在测试环境可控地出现；
 *       {@code delay-seconds} 用来构造"用户点了支付但回调还没到"的中间状态，
 *       这是补单逻辑唯一的验证手段。</li>
 *   <li><b>查询返回 NOT_FOUND 而不是编造成功</b>：让"主动查询发现渠道无此单 → 关单"
 *       这条分支能在本地被真实触发。</li>
 * </ol>
 *
 * <h3>生产环境必须关掉</h3>
 * {@code ck.shop.pay.mock.enabled=false} 会让本类不注册，
 * 于是默认渠道 MOCK 会变成"不支持的支付渠道"而直接报错——
 * 这是我们希望的失败方式：宁可报错，也不能在生产环境里让人白拿商品。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ck.shop.pay.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway, MockPaySupport {

    /** 签名请求头 */
    public static final String HEADER_TIMESTAMP = "mock-timestamp";
    public static final String HEADER_NONCE = "mock-nonce";
    public static final String HEADER_SIGNATURE = "mock-signature";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 模拟渠道的签名密钥。生产接入真实渠道时会换成渠道密钥，这里的值只在校验模拟回调时使用。 */
    @Value("${ck.shop.pay.mock.secret:ck-shop-mock-pay-secret}")
    private String mockSecret;

    /** 模拟"渠道拒绝"的概率（百分比 0-100）。用于验证失败分支。 */
    @Value("${ck.shop.pay.mock.fail-rate:0}")
    private int failRate;

    /** 模拟渠道处理延迟（秒）。用于构造"回调尚未到达"的中间态。 */
    @Value("${ck.shop.pay.mock.delay-seconds:0}")
    private int delaySeconds;

    /** 回调时间戳容差（秒）：超过则认为报文来自重放攻击 */
    @Value("${ck.shop.pay.mock.notify-tolerance-seconds:300}")
    private int notifyToleranceSeconds;

    /**
     * 模拟"主动查询"的返回。
     *
     * <p>默认 {@code true} → 查询返回 {@code NOT_FOUND}，
     * 从而让"渠道查无此单 → 关闭本地支付单"这条分支可以被触发。
     * 设为 false 则返回 {@code PENDING}，用于验证"结果未知时不得关单"的保护逻辑。
     */
    @Value("${ck.shop.pay.mock.query-not-found:true}")
    private boolean queryNotFound;

    @Override
    public String channel() {
        return PayChannel.MOCK;
    }

    // ==================================================================
    // 预支付
    // ==================================================================

    @Override
    public PaymentModels.PrepayResult prepay(PaymentModels.PrepayRequest request) {
        if (request == null || StringUtils.isBlank(request.getPaymentNo())) {
            return PaymentModels.PrepayResult.fail("MOCK_PARAM", "支付单号缺失");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            // 0 元订单在真实渠道同样会被拒。这里也拒绝，避免"0 元支付"这种畸形数据流进订单表。
            return PaymentModels.PrepayResult.fail("MOCK_AMOUNT", "支付金额必须大于 0");
        }
        if (simulateFailure()) {
            return PaymentModels.PrepayResult.fail("MOCK_REJECT", "模拟渠道拒绝：本次支付请求被风控拦截");
        }
        if (delaySeconds > 0) {
            try {
                Thread.sleep(delaySeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return PaymentModels.PrepayResult.fail("MOCK_INTERRUPTED", "模拟渠道调用被中断");
            }
        }

        String channelTradeNo = "MOCKTRADE" + OrderNoGenerator.random(16);
        Map<String, String> payParams = new HashMap<>(8);
        payParams.put("channel", PayChannel.MOCK);
        payParams.put("paymentNo", request.getPaymentNo());
        payParams.put("channelTradeNo", channelTradeNo);
        payParams.put("amount", request.getAmount().toPlainString());
        payParams.put("subject", StringUtils.defaultString(request.getSubject()));
        payParams.put("payType", StringUtils.defaultIfBlank(request.getPayType(), "NATIVE"));
        // 真实渠道这里是 JSAPI/NATIVE 的调起参数；模拟渠道给的是"确认支付"要用到的信息
        payParams.put("mockPayUrl", "/api/pay/mock/confirm?paymentNo=" + request.getPaymentNo());

        log.info("[模拟支付] 预支付成功 paymentNo={}, amount={}, channelTradeNo={}",
                request.getPaymentNo(), request.getAmount(), channelTradeNo);
        return PaymentModels.PrepayResult.ok()
                .setChannelTradeNo(channelTradeNo)
                .setPayParams(payParams);
    }

    // ==================================================================
    // 回调：验签 → 时间戳窗口 → 解析
    // ==================================================================

    @Override
    public PaymentModels.NotifyResult parseNotify(PaymentModels.NotifyRequest request) {
        if (request == null || StringUtils.isBlank(request.getBody())) {
            return PaymentModels.NotifyResult.invalid("回调报文为空");
        }
        Map<String, String> headers = normalizeHeaders(request.getHeaders());
        String timestamp = headers.get(HEADER_TIMESTAMP);
        String nonce = headers.get(HEADER_NONCE);
        String signature = headers.get(HEADER_SIGNATURE);

        if (StringUtils.isAnyBlank(timestamp, nonce, signature)) {
            return PaymentModels.NotifyResult.invalid("回调缺少签名相关请求头");
        }

        // ① 时间戳窗口：先做，成本最低，能挡掉绝大多数重放
        long notifyMillis;
        try {
            notifyMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return PaymentModels.NotifyResult.invalid("回调时间戳格式错误");
        }
        long driftSeconds = Math.abs(System.currentTimeMillis() - notifyMillis) / 1000L;
        if (driftSeconds > notifyToleranceSeconds) {
            // 注意返回的是"验签不通过"：调用方据此直接拒绝整条回调。
            // 不区分"签名错"和"过期"是刻意的——对攻击者不透露失败原因。
            return PaymentModels.NotifyResult.invalid(
                    "回调时间戳超出容差（" + driftSeconds + "s > " + notifyToleranceSeconds + "s），疑似重放");
        }

        // ② 验签
        String expected = sign(request.getBody(), timestamp, nonce);
        if (!constantTimeEquals(expected, signature)) {
            return PaymentModels.NotifyResult.invalid("回调签名校验不通过");
        }

        // ③ 解析（验签通过之后才解析，否则内容一律不可信）
        JSONObject json;
        try {
            json = JSON.parseObject(request.getBody());
        } catch (Exception e) {
            return PaymentModels.NotifyResult.invalid("回调报文不是合法 JSON");
        }
        if (json == null) {
            return PaymentModels.NotifyResult.invalid("回调报文为空");
        }

        Date eventTime = parseTime(json.getString("eventTime"));
        return new PaymentModels.NotifyResult()
                .setSignatureValid(true)
                .setNotifyType(StringUtils.defaultIfBlank(json.getString("notifyType"), "PAY"))
                .setPaymentNo(json.getString("paymentNo"))
                .setRefundNo(json.getString("refundNo"))
                .setChannelTradeNo(json.getString("channelTradeNo"))
                .setEventStatus(StringUtils.defaultIfBlank(json.getString("eventStatus"), "SUCCESS"))
                .setPaidAmount(json.getBigDecimal("paidAmount"))
                .setRefundAmount(json.getBigDecimal("refundAmount"))
                .setEventTime(eventTime == null ? new Date() : eventTime);
    }

    // ==================================================================
    // 主动查询
    // ==================================================================

    @Override
    public PaymentModels.QueryResult query(PaymentModels.QueryRequest request) {
        // 模拟渠道没有真实账户，不可能"查到一笔成功交易"。
        // 这里刻意不编造 SUCCESS：那会让"查询补偿"这条分支永远走不到，
        // 上线时才发现补单逻辑是错的。
        String status = queryNotFound
                ? PaymentModels.QueryResult.NOT_FOUND
                : PaymentModels.QueryResult.PENDING;
        log.info("[模拟支付] 主动查询 paymentNo={} → {}",
                request == null ? null : request.getPaymentNo(), status);
        return PaymentModels.QueryResult.of(status);
    }

    // ==================================================================
    // 退款
    // ==================================================================

    @Override
    public PaymentModels.RefundResult refund(PaymentModels.RefundRequest request) {
        if (request == null || StringUtils.isBlank(request.getRefundNo())) {
            return PaymentModels.RefundResult.fail("MOCK_PARAM", "退款单号缺失");
        }
        if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentModels.RefundResult.fail("MOCK_AMOUNT", "退款金额必须大于 0");
        }
        if (simulateFailure()) {
            // 退款失败必须是"可重试"的：调用方会把退款单置为 FAILED 并保留重试入口，
            // 而不是把退款单删掉重开——那会让"退了几次"彻底不可追溯。
            return PaymentModels.RefundResult.fail("MOCK_REJECT", "模拟渠道拒绝：退款请求失败，可重试");
        }
        String channelRefundNo = "MOCKREFUND" + OrderNoGenerator.random(16);
        log.info("[模拟支付] 退款成功 refundNo={}, amount={}, channelRefundNo={}",
                request.getRefundNo(), request.getRefundAmount(), channelRefundNo);
        return PaymentModels.RefundResult.success(channelRefundNo, new Date());
    }

    @Override
    public boolean close(PaymentModels.CloseRequest request) {
        log.info("[模拟支付] 关闭渠道支付单 paymentNo={}",
                request == null ? null : request.getPaymentNo());
        return true;
    }

    // ==================================================================
    // MockPaySupport：生成带签名的模拟回调
    // ==================================================================

    @Override
    public SignedNotify buildSignedNotify(String paymentNo, String channelTradeNo,
                                          BigDecimal paidAmount, String eventStatus) {
        Date now = new Date();
        JSONObject payload = new JSONObject();
        payload.put("notifyType", "PAY");
        payload.put("paymentNo", paymentNo);
        payload.put("channelTradeNo", channelTradeNo);
        payload.put("eventStatus", eventStatus);
        payload.put("eventTime", new SimpleDateFormat(TIME_PATTERN).format(now));
        payload.put("paidAmount", paidAmount == null ? "0.00" : paidAmount.toPlainString());
        payload.put("currency", "CNY");
        payload.put("buyerId", "mock_buyer");

        String body = payload.toJSONString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = OrderNoGenerator.random(16);

        Map<String, String> headers = new HashMap<>(4);
        headers.put(HEADER_TIMESTAMP, timestamp);
        headers.put(HEADER_NONCE, nonce);
        headers.put(HEADER_SIGNATURE, sign(body, timestamp, nonce));

        return new SignedNotify().setHeaders(headers).setBody(body);
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /**
     * 签名算法：{@code HMAC-SHA256(body + "&timestamp=" + ts + "&nonce=" + nonce, secret)} 十六进制小写。
     *
     * <p>把 timestamp 与 nonce 纳入签名范围是防重放的必要条件：
     * 只签 body 的话，攻击者可以把同一份合法报文反复重放，
     * 每次换一个时间戳就绕过了时间窗校验。
     */
    private String sign(String body, String timestamp, String nonce) {
        try {
            String content = body + "&timestamp=" + timestamp + "&nonce=" + nonce;
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(mockSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("模拟渠道签名失败", e);
        }
    }

    private boolean simulateFailure() {
        return failRate > 0 && RANDOM.nextInt(100) < failRate;
    }

    /** 请求头名大小写不敏感：不同容器/客户端的大小写行为不一致，这里统一小写化 */
    private Map<String, String> normalizeHeaders(Map<String, String> raw) {
        Map<String, String> normalized = new HashMap<>(8);
        if (raw != null) {
            raw.forEach((k, v) -> {
                if (k != null) {
                    normalized.put(k.toLowerCase(), v);
                }
            });
        }
        return normalized;
    }

    private Date parseTime(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return new SimpleDateFormat(TIME_PATTERN).parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < left.length; i++) {
            diff |= left[i] ^ right[i];
        }
        return diff == 0;
    }
}
