package org.jeecg.modules.shop.order.util;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * <p>
 * 结算价格令牌（price token）—— 防"价格篡改"与"挂旧价下单"。
 * </p>
 *
 * <h3>它解决什么问题</h3>
 * 结算页给用户展示的价格，与用户点"提交订单"之间可能隔着几分钟（甚至是别人手改的请求体）。
 * 期间促销可能结束、商品可能调价、库存可能被别人买走。有两种错误做法：
 * <ol>
 *   <li><b>信任前端传来的金额</b> —— 直接把 price 改成 0.01 就能白拿，绝不能做；</li>
 *   <li><b>完全无视前端</b> —— 下单时重算价格，用户看到的 100 元结果按 120 元结算，
 *       属于"未告知的价格变更"，是明确的合规问题。</li>
 * </ol>
 * 正确做法是：结算预览时**服务端**算钱并签一个令牌把结果封存起来；
 * 提交订单时带回来，服务端**验签 + 重算**：
 * <ul>
 *   <li>令牌无效/过期 → 拒绝，让前端回到结算页重新确认价格；</li>
 *   <li>令牌有效但重算结果与令牌里的金额不一致（促销结束、调价）→ 拒绝，
 *       同样要求用户重新确认，<b>不静默按新价成交</b>。</li>
 * </ul>
 *
 * <h3>令牌内容</h3>
 * {@code userId | storeId | payAmount | itemsDigest | issuedAt | expireAt} + HMAC-SHA256 签名。
 * <ul>
 *   <li>绑 userId/storeId：A 用户的令牌不能给 B 用，A 店不能给 B 店用；</li>
 *   <li>绑 itemsDigest（明细规范化摘要）：改数量/换 SKU 会让摘要变化，令牌自然失效；</li>
 *   <li>带 issuedAt/expireAt：防止把几小时前的价格一直挂在手里。</li>
 * </ul>
 *
 * <h3>密钥</h3>
 * 从 {@code ck.shop.order.price-token-secret} 读取。**非 dev 环境必须显式配置**：
 * 未配置时启动即失败（见 {@link #validateSecret()}）；
 * 只有 dev 环境才允许退化为进程内随机密钥（仅让本地开发能跑通，并打 WARN）。
 * 多实例部署且未配置时，A 实例签的令牌在 B 实例必然验签失败，用户下单直接报错，
 * 这种故障必须在启动时暴露，而不是等第一位顾客下单才发现。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Slf4j
@Component
public class PriceTokenUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String FIELD_SEPARATOR = "\u0001";

    private final Environment environment;

    @Value("${ck.shop.order.price-token-secret:}")
    private String configuredSecret;

    @Value("${ck.shop.order.price-token-ttl-seconds:900}")
    private int ttlSeconds;

    /** 实际使用的密钥：配置为空时退化为进程内随机密钥（重启即失效，仅开发可用） */
    private volatile byte[] secretBytes;

    public PriceTokenUtils(Environment environment) {
        this.environment = environment;
    }

    /**
     * 启动即校验密钥，避免"配置漏了"这类问题被拖到用户下单时才以
     * "结算令牌校验失败"的形式暴露出来（那时候排查成本高得多）。
     */
    @PostConstruct
    void validateSecret() {
        if (StringUtils.isNotBlank(configuredSecret)) {
            return;
        }
        if (!isDevProfile()) {
            throw new IllegalStateException(
                    "未配置 ck.shop.order.price-token-secret（当前 profile="
                            + Arrays.toString(environment.getActiveProfiles()) + "）。"
                            + "该密钥用于结算价格令牌签名，多实例部署必须各实例一致，"
                            + "否则 A 实例签发的令牌在 B 实例验签失败、用户无法下单。"
                            + "请在 .env 中设置后重启（本地开发可切换到 dev profile）。");
        }
        log.warn("未配置 ck.shop.order.price-token-secret，dev 环境将使用进程内随机密钥："
                + "重启后此前签发的结算令牌全部失效，属预期行为。");
    }

    /** dev / local 之类的开发 profile 才允许随机密钥兜底 */
    private boolean isDevProfile() {
        String[] profiles = environment.getActiveProfiles();
        return Arrays.stream(profiles).anyMatch(p -> p.contains("dev") || p.contains("local"));
    }

    /**
     * 结算令牌载荷。
     */
    @Data
    @Accessors(chain = true)
    public static class PriceTokenPayload {
        /** 下单用户，防令牌串用 */
        private String userId;
        /** 店铺，防跨店串用 */
        private String storeId;
        /** 服务端算出的应付金额（字符串形式，避免 BigDecimal 的 scale 差异影响签名比对） */
        private String payAmount;
        /** 明细摘要 */
        private String itemsDigest;
        /** 签发时间（epoch 秒） */
        private long issuedAt;
        /** 过期时间（epoch 秒） */
        private long expireAt;
    }

    /**
     * 签发令牌。
     *
     * @param userId      当前登录用户
     * @param storeId     当前店铺
     * @param payAmount   服务端算出的应付金额
     * @param itemsDigest 明细摘要（见 {@link #digestItems(String)}）
     */
    public String issue(String userId, String storeId, BigDecimal payAmount, String itemsDigest) {
        long now = System.currentTimeMillis() / 1000L;
        long expireAt = now + Math.max(60, ttlSeconds);
        String canonical = String.join(FIELD_SEPARATOR,
                StringUtils.trimToEmpty(userId),
                StringUtils.trimToEmpty(storeId),
                MoneyUtils.scale(payAmount).toPlainString(),
                StringUtils.trimToEmpty(itemsDigest),
                String.valueOf(now),
                String.valueOf(expireAt));
        String encoded = base64Url(canonical.getBytes(StandardCharsets.UTF_8));
        String signature = base64Url(hmac(encoded));
        return encoded + "." + signature;
    }

    /**
     * 验签并解析令牌。
     *
     * @throws JeecgBootException 令牌缺失/格式错误/签名不匹配/已过期/不属于当前用户或店铺
     */
    public PriceTokenPayload verify(String token, String userId, String storeId) {
        if (StringUtils.isBlank(token)) {
            throw new JeecgBootException("结算信息已失效，请返回购物车重新结算");
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw new JeecgBootException("结算信息格式不正确，请返回购物车重新结算");
        }
        String encoded = token.substring(0, dot);
        String signature = token.substring(dot + 1);

        // 先验签再解析内容：否则解析出来的字段全部不可信
        String expected = base64Url(hmac(encoded));
        if (!constantTimeEquals(expected, signature)) {
            throw new JeecgBootException("结算信息校验失败，请返回购物车重新结算");
        }

        String canonical;
        try {
            canonical = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new JeecgBootException("结算信息格式不正确，请返回购物车重新结算");
        }

        String[] parts = canonical.split(FIELD_SEPARATOR, -1);
        if (parts.length != 6) {
            throw new JeecgBootException("结算信息格式不正确，请返回购物车重新结算");
        }

        PriceTokenPayload payload = new PriceTokenPayload()
                .setUserId(parts[0])
                .setStoreId(parts[1])
                .setPayAmount(parts[2])
                .setItemsDigest(parts[3])
                .setIssuedAt(parseLong(parts[4]))
                .setExpireAt(parseLong(parts[5]));

        long now = System.currentTimeMillis() / 1000L;
        if (payload.getExpireAt() <= now) {
            throw new JeecgBootException("结算页面停留时间过长，价格可能已变化，请返回购物车重新结算");
        }
        if (!StringUtils.equals(StringUtils.trimToEmpty(userId), payload.getUserId())) {
            throw new JeecgBootException("结算信息与当前登录用户不一致，请返回购物车重新结算");
        }
        if (!StringUtils.equals(StringUtils.trimToEmpty(storeId), payload.getStoreId())) {
            throw new JeecgBootException("结算信息与当前店铺不一致，请返回购物车重新结算");
        }
        return payload;
    }

    /**
     * 计算明细摘要。
     *
     * <p>调用方必须传**规范化**后的字符串：明细按 productId+skuId 升序拼接
     * （productId|skuId|quantity），这样"同样的商品组合、不同的选中顺序"得到同一摘要，
     * 而"改了数量或换了 SKU"一定得到不同摘要。
     */
    public String digestItems(String canonicalItems) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] hash = digest.digest(StringUtils.trimToEmpty(canonicalItems).getBytes(StandardCharsets.UTF_8));
            return base64Url(hash);
        } catch (Exception e) {
            // SHA-256 是 JDK 必备算法，走到这里说明 JVM 环境异常，属于不可恢复错误
            throw new IllegalStateException("计算明细摘要失败", e);
        }
    }

    private byte[] hmac(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(resolveSecret(), HMAC_ALGORITHM));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("结算令牌签名失败", e);
        }
    }

    private byte[] resolveSecret() {
        byte[] cached = this.secretBytes;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (this.secretBytes == null) {
                if (StringUtils.isBlank(configuredSecret)) {
                    byte[] generated = new byte[32];
                    new SecureRandom().nextBytes(generated);
                    log.warn("未配置 ck.shop.order.price-token-secret，已使用进程内随机密钥。"
                            + "本地开发没问题，但**生产环境必须显式配置**："
                            + "多实例部署时未配置会导致 A 实例签发的结算令牌在 B 实例验签失败，用户下单报错。");
                    this.secretBytes = generated;
                } else {
                    this.secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
                }
            }
            return this.secretBytes;
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new JeecgBootException("结算信息格式不正确，请返回购物车重新结算");
        }
    }

    /**
     * 定长时间比较，避免通过响应时间差逐个字节猜出签名（时序攻击）。
     */
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
