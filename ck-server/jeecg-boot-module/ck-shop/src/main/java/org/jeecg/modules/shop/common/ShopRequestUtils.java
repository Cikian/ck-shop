package org.jeecg.modules.shop.common;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * <p>
 * 店铺端请求参数解析的收敛点。
 * </p>
 *
 * <h3>为什么需要它</h3>
 * 语言解析原本在 7 个 Controller 里各写一份，语义还不一致：
 *   · 有的先读 {@code lang} 参数，有的只读 header；
 *   · 商品侧不 trim、不改大小写，促销侧 trim + 小写 ——
 *     同一个 {@code ?lang=ZH-CN} 在商品页是 "ZH-CN"（查不到翻译、回退中文），
 *     在促销页是 "zh"（表现正常），排查时非常费劲。
 *
 * <p>这里统一为：query {@code lang} → header {@code x-language} → {@code Accept-Language}
 * → 默认 {@code zh}；然后 trim、转小写、截到主语言（{@code zh-CN → zh}）。
 *
 * @author Cikian
 * @since 2026-10-22
 */
public final class ShopRequestUtils {

    /** 默认语言：与 nuxt-shop 的 STORE_DEFAULT_LANG 保持一致 */
    public static final String DEFAULT_LANGUAGE = "zh";

    private ShopRequestUtils() {
    }

    /**
     * 解析当前请求的展示语言。
     *
     * <p>返回值永远是"主语言"形式（zh / en / fr），不会带地区后缀。
     */
    public static String resolveLanguage(HttpServletRequest request) {
        if (request == null) {
            return DEFAULT_LANGUAGE;
        }
        String language = request.getParameter("lang");
        if (StringUtils.isBlank(language)) {
            language = request.getHeader("x-language");
        }
        if (StringUtils.isBlank(language)) {
            language = request.getHeader("Accept-Language");
        }
        return normalizeLanguage(language);
    }

    /**
     * 归一化语言码：trim → 小写 → 取第一个语言标签 → 截到主语言。
     * 无法识别时返回默认语言。
     */
    public static String normalizeLanguage(String language) {
        if (StringUtils.isBlank(language)) {
            return DEFAULT_LANGUAGE;
        }
        String value = language.trim().toLowerCase(Locale.ROOT);
        int comma = value.indexOf(',');
        if (comma >= 0) {
            value = value.substring(0, comma);
        }
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon);
        }
        value = value.trim();
        int dash = value.indexOf('-');
        if (dash < 0) {
            dash = value.indexOf('_');
        }
        if (dash > 0) {
            value = value.substring(0, dash);
        }
        return value.isEmpty() ? DEFAULT_LANGUAGE : value;
    }
}
