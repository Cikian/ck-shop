package org.jeecg.modules.shop.region;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * <p>
 * 访客 locale → region 译文语言的映射（契约 §2 的唯一落地点）。
 * </p>
 *
 * <h3>为什么不能直接用 {@code ShopRequestUtils.normalizeLanguage}</h3>
 * 那边的口径是"商品/分类翻译"：只认主语言，且把 {@code zh-CN} 一律截成 {@code zh}。
 * 地名译文的表里语言码是 {@code zh-CN}（简体）与 {@code zh-TW}（繁体），
 * 截成 {@code zh} 会查不到（{@code translations} 里 {@code zh} 只有几百行残缺数据）。
 * 所以这里保留地区后缀，只在该合并的地方合并。
 *
 * <h3>三条规矩（与契约一致，改动必须同步前端 {@code types/shop-region.ts}）</h3>
 * <ol>
 *   <li>{@code zh} / {@code zh-CN} / {@code zh-Hans} → {@code zh-CN}；</li>
 *   <li>{@code zh-TW} / {@code zh-HK} / {@code zh-Hant} → {@code zh-TW}；</li>
 *   <li>{@code en} → <b>不查译文</b>（返回 null）。上游 {@code en} 译文只覆盖 0.6%，
 *       查了反而会出现"一部分城市显示译名、一部分显示原名"的割裂，直接用 {@code name}；</li>
 *   <li>其余语言（{@code fr}/{@code th}/{@code vi}/{@code ja}/{@code ru}/{@code ar}…）
 *       原样同名查表，<b>不做白名单</b>——店铺以后加语言不该改代码。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-28
 */
public final class RegionLangResolver {

    /** 默认访客语言：与 nuxt-shop 的店铺默认语言一致 */
    public static final String DEFAULT_LOCALE = "zh";

    private RegionLangResolver() {
    }

    /**
     * 解析当前请求的访客 locale（形如 {@code zh} / {@code en} / {@code fr} / {@code zh-TW}）。
     *
     * <p>取值顺序：{@code ?lang=} → {@code x-language} 头 → {@code Accept-Language} → 默认 {@code zh}。
     * {@code ?lang=} 优先是为了让 BFF 透传时不必依赖请求头，也方便直接用 curl 验证。
     */
    public static String resolveLocale(HttpServletRequest request) {
        return resolveLocale(request, null);
    }

    /**
     * 同上，但允许调用方已经把 {@code lang} 参数取出来了（Controller 上标了
     * {@code @RequestParam} 时不必再读一遍 request，避免两处口径不一致）。
     */
    public static String resolveLocale(HttpServletRequest request, String langParam) {
        String raw = StringUtils.trimToNull(langParam);
        if (raw == null && request != null) {
            raw = StringUtils.trimToNull(request.getParameter("lang"));
        }
        if (raw == null && request != null) {
            raw = StringUtils.trimToNull(request.getHeader("x-language"));
        }
        if (raw == null && request != null) {
            raw = StringUtils.trimToNull(request.getHeader("Accept-Language"));
        }
        if (raw == null) {
            return DEFAULT_LOCALE;
        }
        // 只取第一个语言标签，并去掉 q=0.9 这类权重参数
        String value = raw.toLowerCase(Locale.ROOT).replace('_', '-');
        int comma = value.indexOf(',');
        if (comma >= 0) {
            value = value.substring(0, comma);
        }
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon);
        }
        value = value.trim();
        return value.isEmpty() ? DEFAULT_LOCALE : value;
    }

    /**
     * 把访客 locale 映射成 {@code translations.language}。
     *
     * @return 需要查译文的语言码；{@code en}（以及任何以 en 开头、以及空值）返回
     *         {@code null}，表示"不要查译文，直接用 name"
     */
    public static String toTranslationLanguage(String locale) {
        String value = StringUtils.trimToEmpty(locale).toLowerCase(Locale.ROOT).replace('_', '-');
        if (value.isEmpty()) {
            value = DEFAULT_LOCALE;
        }
        if (value.startsWith("zh")) {
            // 繁体：带 TW/HK/MO 地区后缀，或显式声明 Hant 脚本
            if (value.contains("tw") || value.contains("hk") || value.contains("mo")
                    || value.contains("hant")) {
                return "zh-TW";
            }
            return "zh-CN";
        }
        if (value.startsWith("en")) {
            // 契约 §2：en 译文只有 0.6%，查了没有意义（见类注释第 3 条）
            return null;
        }
        // 其它语言取主语言码（pt-BR → pt 时若库里两者都有，主语言码优先，
        // 因为它覆盖更完整；地区变体只在上游单独提供时才存在）
        int dash = value.indexOf('-');
        return dash > 0 ? value.substring(0, dash) : value;
    }
}
