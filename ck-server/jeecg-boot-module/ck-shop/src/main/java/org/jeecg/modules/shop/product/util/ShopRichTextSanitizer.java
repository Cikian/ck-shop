package org.jeecg.modules.shop.product.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * 富文本白名单清洗器（后端保存时第一层清洗，契约第五节）。
 * </p>
 *
 * <p><b>为什么自己写而不是引入 jsoup / OWASP sanitizer：</b>
 * 本机 Maven 取包受限（私服不可用），新增依赖会直接破坏现有构建链路；
 * 因此用「标签白名单 + 属性白名单 + 协议白名单」实现。</p>
 *
 * <p><b>局限（必须知晓）：</b>这是<b>收敛风险</b>而非密码学级保证。
 * 正则解析 HTML 天然不完整（例如极端畸形嵌套），所以店铺端与管理端预览都还要
 * 再过一遍同一套白名单（契约 5.2 双层清洗）。富文本的写入入口只有管理端登录用户。</p>
 *
 * <p>规则（逐条对应契约第五节）：</p>
 * <ul>
 *   <li>允许标签：p br strong b em i u s ul ol li h2 h3 h4 blockquote a img span div
 *       table thead tbody tr td th hr；</li>
 *   <li>允许属性：a[href,title,target,rel]、img[src,alt,width,height]、td/th[colspan,rowspan]，
 *       其它标签的属性一律丢弃；</li>
 *   <li>强制：a 加 rel="noopener noreferrer"，外链再加 target="_blank"；img 的 src 只允许
 *       http(s):// 或 / 开头（否则整张图丢弃，避免坏图与探测）；</li>
 *   <li>移除：script style iframe object embed form input link meta（成对标签连同内容）、
 *       所有 on* 事件、javascript:/data:/vbscript: 协议。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
public final class ShopRichTextSanitizer {

    private ShopRichTextSanitizer() {
    }

    /** 允许保留的标签（契约 5.1 白名单，逐字对齐） */
    private static final Set<String> ALLOWED_TAGS = new HashSet<>(Arrays.asList(
            "p", "br", "strong", "b", "em", "i", "u", "s", "ul", "ol", "li",
            "h2", "h3", "h4", "blockquote", "a", "img", "span", "div",
            "table", "thead", "tbody", "tr", "td", "th", "hr"));

    /**
     * 危险元素：连同内容一起删除（script/style 里是代码；iframe/object/form 里是外部内容，
     * 都不能当正文留下）。反向引用保证开闭标签同名。
     */
    private static final Pattern DANGEROUS_ELEMENT = Pattern.compile(
            "(?is)<\\s*(script|style|iframe|object|form)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>");

    /**
     * 未闭合的危险元素：从开标签一直删到文本末尾（畸形/截断输入，宁可少显示不可留脚本）。
     * 用"温度贪婪"写法保证后面确实没有同名闭标签，避免把正常内容整段误删。
     */
    private static final Pattern UNCLOSED_DANGEROUS_ELEMENT = Pattern.compile(
            "(?is)<\\s*(script|style|iframe|object|form)\\b[^>]*>(?:(?!<\\s*/\\s*\\1).)*$");

    /** 危险标签的残留（自闭合 / 只剩单个开闭标签 / 内容已在上一步被删掉） */
    private static final Pattern DANGEROUS_LEFTOVER_TAG = Pattern.compile(
            "(?is)<\\s*/?\\s*(script|style|iframe|object|embed|form|input|link|meta)\\b[^>]*>");

    /** HTML 注释：里面可能藏条件注释脚本，直接丢掉 */
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");

    /** 任意标签：<name attr...> 或 </name> */
    private static final Pattern TAG = Pattern.compile("<\\s*(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");

    /** 单个属性：name 或 name="v" / name='v' / name=v */
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+)))?");

    /** 属性值里需要转义的字符（防止用单引号包裹的 href 里塞双引号逃逸出属性） */
    private static final Pattern ATTR_UNSAFE = Pattern.compile("[\"<>]");

    /**
     * 清洗富文本。输入为 null 时原样返回 null（调用方负责非空校验）。
     *
     * @param html 原始 HTML
     * @return 只含白名单标签/属性的 HTML
     */
    public static String sanitize(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        String source = COMMENT.matcher(html).replaceAll("");
        source = DANGEROUS_ELEMENT.matcher(source).replaceAll("");
        // 顺序很重要：先处理"未闭合的危险元素"（连同后续内容一起丢），
        // 再处理只剩标签的残留；反过来会把开标签先删掉，导致未闭合的脚本正文变成可见文本
        source = UNCLOSED_DANGEROUS_ELEMENT.matcher(source).replaceAll("");
        source = DANGEROUS_LEFTOVER_TAG.matcher(source).replaceAll("");

        Matcher tagMatcher = TAG.matcher(source);
        StringBuilder out = new StringBuilder(source.length());
        int last = 0;
        while (tagMatcher.find()) {
            // 标签之前的普通文本原样保留
            out.append(source, last, tagMatcher.start());
            last = tagMatcher.end();

            boolean closing = "/".equals(tagMatcher.group(1));
            String name = tagMatcher.group(2).toLowerCase(Locale.ROOT);
            if (!ALLOWED_TAGS.contains(name)) {
                // 未知标签：只丢标签，保留内部文本（例如 <font> 里的正文不能跟着消失）
                continue;
            }
            if (closing) {
                out.append("</").append(name).append('>');
                continue;
            }
            String built = buildOpeningTag(name, tagMatcher.group(3));
            if (built != null) {
                out.append(built);
            }
        }
        out.append(source, last, source.length());
        return out.toString();
    }

    /**
     * 按标签类型重建开标签：只保留白名单属性，并做协议校验/强制安全属性。
     *
     * @return 重建后的开标签；返回 null 表示该标签应整块丢弃（目前只有 img 的 src 不合法）
     */
    private static String buildOpeningTag(String name, String attrText) {
        Map<String, String> raw = parseAttributes(attrText);
        Map<String, String> safe = new LinkedHashMap<>();
        switch (name) {
            case "a": {
                // a 的 href 走协议白名单；不安全就丢 href，链接退化为纯文本锚点（内容仍保留）
                String href = safeHref(raw.get("href"));
                if (href != null) {
                    safe.put("href", href);
                }
                putIfPresent(raw, safe, "title");
                if (href != null && isExternal(href)) {
                    // 外链新窗口打开；rel 无条件强制，防止 window.opener 反向控制
                    safe.put("target", "_blank");
                }
                safe.put("rel", "noopener noreferrer");
                break;
            }
            case "img": {
                String src = safeImgSrc(raw.get("src"));
                if (src == null) {
                    return null;
                }
                safe.put("src", src);
                putIfPresent(raw, safe, "alt");
                putIfPresent(raw, safe, "width");
                putIfPresent(raw, safe, "height");
                break;
            }
            case "td":
            case "th": {
                putIfPresent(raw, safe, "colspan");
                putIfPresent(raw, safe, "rowspan");
                break;
            }
            default:
                // 其它白名单标签不带任何属性（class/style 等一律清掉，避免样式注入）
                break;
        }
        StringBuilder tag = new StringBuilder("<").append(name);
        for (Map.Entry<String, String> attr : safe.entrySet()) {
            tag.append(' ').append(attr.getKey()).append("=\"").append(escapeAttr(attr.getValue())).append('"');
        }
        return tag.append('>').toString();
    }

    /** 解析标签属性，先去掉全部 on* 事件（后来者不覆盖先出现的同名属性） */
    private static Map<String, String> parseAttributes(String attrText) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (attrText == null || attrText.isEmpty()) {
            return attrs;
        }
        Matcher matcher = ATTR.matcher(attrText);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            // 所有 on* 事件处理属性一律丢弃（onclick / onerror / onload ...）
            if (name.startsWith("on")) {
                continue;
            }
            String value = matcher.group(2) != null ? matcher.group(2)
                    : matcher.group(3) != null ? matcher.group(3)
                    : matcher.group(4) != null ? matcher.group(4) : "";
            attrs.putIfAbsent(name, value);
        }
        return attrs;
    }

    /**
     * a[href] 协议白名单：只允许 http(s) / mailto / tel / 相对路径（/、#、?、普通相对地址）。
     * 明确拒绝 javascript: / data: / vbscript:。
     * 校验前先去掉空白与控制字符，防止 {@code java\tscript:} 这类绕过。
     */
    private static String safeHref(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String compact = compact(trimmed);
        // 数值实体可能把 "javascript:" 拆成 "java&#115;cript:"，浏览器会解码 → 直接拒绝
        if (compact.contains("&#")) {
            return null;
        }
        String lower = compact.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            return null;
        }
        int colon = lower.indexOf(':');
        if (colon > 0) {
            int slash = lower.indexOf('/');
            int hash = lower.indexOf('#');
            int question = lower.indexOf('?');
            // 只有冒号出现在路径/查询/锚点之前，才被当成"协议"
            boolean hasScheme = (slash < 0 || colon < slash)
                    && (hash < 0 || colon < hash)
                    && (question < 0 || colon < question);
            if (hasScheme) {
                String scheme = lower.substring(0, colon);
                if (!"http".equals(scheme) && !"https".equals(scheme)
                        && !"mailto".equals(scheme) && !"tel".equals(scheme)) {
                    return null;
                }
            }
        }
        return trimmed;
    }

    /**
     * img[src] 只允许 http(s):// 或 / 开头（契约第五节），其余整张图丢弃。
     */
    private static String safeImgSrc(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String compact = compact(trimmed);
        if (compact.contains("&#")) {
            return null;
        }
        String lower = compact.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || compact.startsWith("/")) {
            return trimmed;
        }
        return null;
    }

    private static boolean isExternal(String href) {
        String lower = compact(href).toLowerCase(Locale.ROOT);
        // 协议相对地址 //host/path 同样是外链（浏览器按当前页面协议访问外部主机）
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//");
    }

    /** 去掉所有空白与控制字符：既用于协议判定，也用于「外链」判定 */
    private static String compact(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c > ' ' && c != 0x7f) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void putIfPresent(Map<String, String> raw, Map<String, String> target, String key) {
        String value = raw.get(key);
        if (value != null && !value.isEmpty()) {
            target.put(key, value);
        }
    }

    private static String escapeAttr(String value) {
        return ATTR_UNSAFE.matcher(value).replaceAll(match -> {
            switch (match.group()) {
                case "\"":
                    return "&quot;";
                case "<":
                    return "&lt;";
                default:
                    return "&gt;";
            }
        });
    }
}
