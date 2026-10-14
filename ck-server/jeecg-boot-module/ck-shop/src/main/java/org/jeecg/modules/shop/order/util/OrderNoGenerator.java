package org.jeecg.modules.shop.order.util;

import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * <p>
 * 订单号 / 支付单号 / 退款单号 / 流水号生成器。
 * </p>
 *
 * <h3>为什么不用自增 / 数据库序列</h3>
 * <ul>
 *   <li><b>自增会被爬</b>：订单号 +1 就能遍历别人的订单，是经典的越权入口
 *       （配合一个"按订单号查询且不校验归属"的接口就是灾难）。</li>
 *   <li><b>不利于分库分表</b>：自增号无法在应用侧预先确定，
 *       未来按单号路由分片时没有依据。</li>
 *   <li><b>会泄露经营数据</b>：竞品每天下一单就能算出你的日均单量。</li>
 * </ul>
 *
 * <h3>编号结构（长度均 ≤ 32，与 DDL 对齐）</h3>
 * <pre>
 *   订单号   SO + yyyyMMddHHmmssSSS(17) + 店铺短码(4) + 随机(6)  = 29
 *   支付单号 PAY + yyyyMMddHHmmssSSS(17) + 随机(8)              = 28
 *   退款单号 RF + yyyyMMddHHmmssSSS(17) + 随机(8)               = 27
 *   资金流水 FF + yyyyMMddHHmmssSSS(17) + 随机(8)               = 27
 * </pre>
 *
 * <p><b>时间前缀仍然保留</b>：它不是唯一性来源（唯一性由数据库唯一索引 + 冲突重试兜底），
 * 而是运维排查的抓手——拿到一个单号就能立刻知道它是什么时候下的，
 * 不用查库就能判断"这是不是昨天那批故障单"。
 *
 * <p>随机段用 {@link SecureRandom}：{@code java.util.Random} 的种子可预测，
 * 攻击者能推算出前后订单号，等于绕过了"不用自增"的防护。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public final class OrderNoGenerator {

    private OrderNoGenerator() {
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 订单号前缀 */
    public static final String ORDER_PREFIX = "SO";
    /** 支付单号前缀 */
    public static final String PAYMENT_PREFIX = "PAY";
    /** 退款单号前缀 */
    public static final String REFUND_PREFIX = "RF";
    /** 资金流水号前缀 */
    public static final String FUND_FLOW_PREFIX = "FF";
    /** 渠道回调去重键前缀 */
    public static final String NOTIFY_PREFIX = "NT";

    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成订单号。
     *
     * @param storeShortCode 店铺短码（由店铺编码哈希而来，仅用于人工辨识，不参与唯一性）
     */
    public static String orderNo(String storeShortCode) {
        return ORDER_PREFIX + timestamp() + shortCode(storeShortCode) + random(6);
    }

    /** 生成支付单号 */
    public static String paymentNo() {
        return PAYMENT_PREFIX + timestamp() + random(8);
    }

    /** 生成退款单号 */
    public static String refundNo() {
        return REFUND_PREFIX + timestamp() + random(8);
    }

    /** 生成资金流水号 */
    public static String fundFlowNo() {
        return FUND_FLOW_PREFIX + timestamp() + random(8);
    }

    /**
     * 生成渠道回调去重键。
     *
     * <p>组成里**必须包含渠道事件时间/状态**，而不能只用"渠道流水号"：
     * 真实渠道对同一笔交易会推送多种事件（支付成功、支付关闭、退款成功），
     * 如果只按流水号去重，第二条合法事件会被当成重复回调丢弃，
     * 结果就是"钱退了但系统不知道"。
     */
    public static String notifyDedupKey(String channel, String notifyType, String tradeNo,
                                        String eventStatus, String eventTime) {
        return String.join("|",
                StringUtils.trimToEmpty(channel),
                StringUtils.trimToEmpty(notifyType),
                StringUtils.trimToEmpty(tradeNo),
                StringUtils.trimToEmpty(eventStatus),
                StringUtils.trimToEmpty(eventTime));
    }

    /** 17 位毫秒级时间戳，固定长度，保证单号长度稳定（不必按长度做分支） */
    private static String timestamp() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    /**
     * 店铺短码：把店铺编码转成 4 位可读字符。
     *
     * <p>只是给人工看单号时的一个辨识线索（哪个店出的单），
     * **不承担唯一性**，因此用哈希而不是直接截取（截取会让 "default" 和 "default2" 撞码，
     * 反而误导排查）。
     */
    private static String shortCode(String storeCode) {
        String source = StringUtils.trimToEmpty(storeCode).toUpperCase();
        if (source.isEmpty()) {
            return random(4);
        }
        long hash = 0L;
        for (int i = 0; i < source.length(); i++) {
            hash = hash * 31 + source.charAt(i);
        }
        long positive = Math.abs(hash);
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET[(int) (positive % ALPHABET.length)]);
            positive /= ALPHABET.length;
        }
        return sb.toString();
    }

    /** 生成指定长度的随机大写字母数字串 */
    public static String random(int length) {
        int len = Math.max(1, length);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
