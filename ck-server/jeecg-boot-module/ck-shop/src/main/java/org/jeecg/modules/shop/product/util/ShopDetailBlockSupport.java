package org.jeecg.modules.shop.product.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * 商品详情模块内容（content）的校验、清洗与 JSON 序列化/反序列化收口。
 * </p>
 *
 * <p>契约依据：docs/商品详情-设计契约.md 第 1.3 节（模块类型与 content 结构、字段长度上限）、
 * 第 1.4 节（模块间距）、第 4.3 节（圆角 / 宫格列数与图片间距）、第五节（RICH_TEXT 白名单清洗）。</p>
 *
 * <p>关键口径：</p>
 * <ul>
 *   <li><b>保存时</b>未知 blockType 拒绝（前端升级不同步时宁可报错，也不写出脏数据）；
 *       布局数值（radius / columns / itemMargin）越界或非整数也拒绝；</li>
 *   <li><b>读取时</b>未知 blockType / content 不是合法 JSON 的行<b>跳过并 log.warn</b>；
 *       缺失的布局字段补默认值、越界的按范围夹取，历史脏数据绝不能把整页打成 500；</li>
 *   <li>长度超限抛明确中文错误，<b>不静默截断</b>。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
@Slf4j
public final class ShopDetailBlockSupport {

    private ShopDetailBlockSupport() {
    }

    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_IMAGE_GRID = "IMAGE_GRID";
    public static final String TYPE_IMAGE_TEXT = "IMAGE_TEXT";
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_RICH_TEXT = "RICH_TEXT";
    public static final String TYPE_VIDEO = "VIDEO";
    public static final String TYPE_TITLE = "TITLE";
    public static final String TYPE_DIVIDER = "DIVIDER";

    /** 合同 1.3 的全部模块类型（顺序固定，便于错误提示里原样展示） */
    public static final Set<String> BLOCK_TYPES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            TYPE_IMAGE, TYPE_IMAGE_GRID, TYPE_IMAGE_TEXT, TYPE_TEXT,
            TYPE_RICH_TEXT, TYPE_VIDEO, TYPE_TITLE, TYPE_DIVIDER)));

    /** 字段长度上限（契约 1.3，逐字对齐） */
    private static final int MAX_URL_LENGTH = 500;
    private static final int MAX_POSTER_LENGTH = 500;
    private static final int MAX_SHORT_TEXT_LENGTH = 120;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_HTML_LENGTH = 20000;

    /** IMAGE_GRID 图片数量上限（契约 1.3：1–9 张） */
    private static final int MAX_GRID_IMAGES = 9;
    /** 视频来源白名单（契约 1.3 / 4.3） */
    private static final Set<String> VIDEO_PROVIDERS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "bilibili", "youtube", "file")));

    /*
     * 布局数值范围（契约 1.3 / 1.4 / 3.3）。
     * 这些值同时被保存校验与读取夹取使用：保存越界拒绝，读取越界夹取，绝不 500。
     */
    /** 图片圆角（radius）：0–200 px，默认 0（默认直角，配合长图无缝拼接） */
    public static final int MIN_RADIUS = 0;
    public static final int MAX_RADIUS = 200;
    public static final int DEFAULT_RADIUS = 0;
    /** 多图宫格列数（columns）：1–6，默认 2 */
    public static final int MIN_GRID_COLUMNS = 1;
    public static final int MAX_GRID_COLUMNS = 6;
    public static final int DEFAULT_GRID_COLUMNS = 2;
    /** 宫格图片间距（itemMargin.*）：0–200 px，默认 0 */
    public static final int MIN_ITEM_MARGIN = 0;
    public static final int MAX_ITEM_MARGIN = 200;

    /**
     * 某类型是否属于契约定义的模块类型。
     */
    public static boolean isKnownType(String blockType) {
        return blockType != null && BLOCK_TYPES.contains(blockType);
    }

    /**
     * 保存前校验并归一化 content（同时完成 RICH_TEXT 白名单清洗）。
     *
     * @param blockType 模块类型（调用方已保证属于 {@link #BLOCK_TYPES}）
     * @param raw       前端提交的 content（可为 null，DIVIDER 允许）
     * @param index     该模块在本次提交数组中的下标（从 1 开始，仅用于错误提示定位）
     * @return 归一化后的新 Map（只含该类型的已知字段，顺序稳定）
     * @throws JeecgBootException 校验失败（含字段缺失、类型错误、长度超限）
     */
    public static Map<String, Object> normalizeContent(String blockType, Map<String, Object> raw, int index) {
        Map<String, Object> content = raw == null ? Collections.emptyMap() : raw;
        String where = "第 " + index + " 个详情模块";
        Map<String, Object> result = new LinkedHashMap<>();
        switch (blockType) {
            case TYPE_IMAGE: {
                result.put("url", requireUrl(content.get("url"), where));
                putIfNotBlank(result, "alt", optionalShortText(content.get("alt"), MAX_SHORT_TEXT_LENGTH, where, "替代文字"));
                // radius 缺省 0 也要显式入库：默认直角是"长图切割无缝拼接"的前提，
                // 前端不需要传也必须能读到明确的 0（契约 1.3 / 1.4）
                result.put("radius", optionalInt(content.get("radius"), MIN_RADIUS, MAX_RADIUS, DEFAULT_RADIUS,
                        where + "（图片）", "图片圆角"));
                break;
            }
            case TYPE_IMAGE_GRID: {
                // 列数放开到 1–6（契约 1.3）：1 列 + 间距 0 + 圆角 0 就是"长图切成 N 段上下紧贴"的用法
                String gridWhere = where + "（多图宫格）";
                int columns = optionalInt(content.get("columns"), MIN_GRID_COLUMNS, MAX_GRID_COLUMNS,
                        DEFAULT_GRID_COLUMNS, gridWhere, "列数");
                List<?> images = asList(content.get("images"));
                if (images == null || images.isEmpty()) {
                    throw new JeecgBootException(where + "（多图宫格）至少需要 1 张图片");
                }
                if (images.size() > MAX_GRID_IMAGES) {
                    throw new JeecgBootException(where + "（多图宫格）最多只能有 " + MAX_GRID_IMAGES
                            + " 张图片，当前 " + images.size() + " 张");
                }
                List<Map<String, Object>> normalizedImages = new ArrayList<>(images.size());
                for (int i = 0; i < images.size(); i++) {
                    Map<String, Object> image = asMap(images.get(i));
                    String imageWhere = where + "（多图宫格）第 " + (i + 1) + " 张图片";
                    if (image == null) {
                        throw new JeecgBootException(imageWhere + "格式不正确，缺少图片地址");
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("url", requireUrl(image.get("url"), imageWhere));
                    putIfNotBlank(item, "alt", optionalShortText(image.get("alt"), MAX_SHORT_TEXT_LENGTH,
                            imageWhere, "替代文字"));
                    normalizedImages.add(item);
                }
                result.put("columns", columns);
                // 缺省时也显式写入 4 个 0，保证"前端不传"与"前端传 0"在库里完全一致
                result.put("itemMargin", normalizeItemMargin(content.get("itemMargin"), gridWhere));
                result.put("radius", optionalInt(content.get("radius"), MIN_RADIUS, MAX_RADIUS, DEFAULT_RADIUS,
                        gridWhere, "图片圆角"));
                result.put("images", normalizedImages);
                break;
            }
            case TYPE_IMAGE_TEXT: {
                result.put("url", requireUrl(content.get("url"), where));
                putIfNotBlank(result, "title", optionalShortText(content.get("title"),
                        MAX_SHORT_TEXT_LENGTH, where, "标题"));
                putIfNotBlank(result, "text", optionalText(content.get("text"), MAX_TEXT_LENGTH, where, "正文"));
                String layout = trimToNull(content.get("layout"));
                if (layout == null) {
                    // 老客户端可能没传 layout；按契约默认"图在左"，不让整条保存失败
                    layout = "left";
                }
                if (!"left".equals(layout) && !"right".equals(layout)) {
                    throw new JeecgBootException(where + "（图文）的布局只能是 left（图在左）或 right（图在右）");
                }
                result.put("layout", layout);
                putIfNotBlank(result, "alt", optionalShortText(content.get("alt"),
                        MAX_SHORT_TEXT_LENGTH, where, "替代文字"));
                result.put("radius", optionalInt(content.get("radius"), MIN_RADIUS, MAX_RADIUS, DEFAULT_RADIUS,
                        where + "（图文）", "图片圆角"));
                break;
            }
            case TYPE_TEXT: {
                putIfNotBlank(result, "title", optionalShortText(content.get("title"),
                        MAX_SHORT_TEXT_LENGTH, where, "标题"));
                result.put("text", requireText(content.get("text"), MAX_TEXT_LENGTH, where, "正文"));
                break;
            }
            case TYPE_RICH_TEXT: {
                String html = content.get("html") == null ? null : String.valueOf(content.get("html"));
                if (StringUtils.isBlank(html)) {
                    throw new JeecgBootException(where + "缺少富文本内容");
                }
                // 先清洗再判长度：上限约束的是真正落库的那份 HTML
                String cleaned = ShopRichTextSanitizer.sanitize(html);
                if (StringUtils.isBlank(cleaned)) {
                    throw new JeecgBootException(where + "（富文本）清洗后内容为空，请补充正文");
                }
                checkLength(cleaned, MAX_HTML_LENGTH, where, "富文本");
                result.put("html", cleaned);
                break;
            }
            case TYPE_VIDEO: {
                String provider = trimToNull(content.get("provider"));
                if (provider == null) {
                    throw new JeecgBootException(where + "（视频）缺少视频来源 provider");
                }
                provider = provider.toLowerCase(Locale.ROOT);
                if (!VIDEO_PROVIDERS.contains(provider)) {
                    throw new JeecgBootException(where + "（视频）的来源不受支持：" + provider
                            + "（允许 bilibili / youtube / file）");
                }
                result.put("provider", provider);
                result.put("url", requireUrl(content.get("url"), where));
                String poster = trimToNull(content.get("poster"));
                if (poster != null) {
                    checkLength(poster, MAX_POSTER_LENGTH, where, "封面图地址");
                    result.put("poster", poster);
                }
                break;
            }
            case TYPE_TITLE: {
                result.put("text", requireText(content.get("text"), MAX_TEXT_LENGTH, where, "标题"));
                putIfNotBlank(result, "sub", optionalShortText(content.get("sub"),
                        MAX_SHORT_TEXT_LENGTH, where, "副标题"));
                break;
            }
            case TYPE_DIVIDER: {
                // 分隔线是纯装饰，content 固定存空对象（丢弃前端误传的字段）
                break;
            }
            default:
                // 调用方已用 isKnownType 校验，这里只是防御
                throw new JeecgBootException("不支持的详情模块类型：" + blockType);
        }
        return result;
    }

    /**
     * 把归一化后的 content 序列化成入库字符串。空内容存 "{}"（DIVIDER 走这里）。
     */
    public static String toJson(Map<String, Object> content) {
        if (content == null || content.isEmpty()) {
            return "{}";
        }
        try {
            return JSON.toJSONString(content);
        } catch (Exception e) {
            // 走到这里说明 content 里有无法序列化的对象（理论上不会发生，因为已归一化）
            log.warn("详情模块内容无法序列化为 JSON", e);
            throw new JeecgBootException("详情模块内容格式不正确，无法保存");
        }
    }

    /**
     * 反序列化入库的 content。
     *
     * @return 解析成功返回 Map（保持字段顺序）；<b>不是合法 JSON 时返回 null</b>，
     *         调用方据此跳过该行（契约：读取时跳过历史脏数据，不能让整页 500）
     */
    public static Map<String, Object> parseContent(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            JSONObject object = JSON.parseObject(json);
            if (object == null) {
                return new LinkedHashMap<>();
            }
            return new LinkedHashMap<>(object);
        } catch (Exception e) {
            log.warn("详情模块 content 不是合法 JSON，已跳过该行：{}", StringUtils.abbreviate(json, 200));
            return null;
        }
    }

    /**
     * <p>
     * 读取时容错归一化（契约 1.4 / 任务要求）：缺失的布局字段补默认值、越界的历史值按范围夹取，
     * <b>绝不抛异常</b>——旧数据 / 绕过接口直接写库的脏数据都不能让详情页 500。
     * </p>
     *
     * <p>只处理已知的布局字段（radius / columns / itemMargin），其它内容原样保留，
     * 渲染端自行忽略未知字段。</p>
     *
     * @param blockType 模块类型
     * @param content   {@link #parseContent} 的解析结果（可为 null）
     * @return 补全/夹取后的新 Map
     */
    public static Map<String, Object> normalizeContentForRead(String blockType, Map<String, Object> content) {
        Map<String, Object> result = content == null ? new LinkedHashMap<>() : new LinkedHashMap<>(content);
        if (!isKnownType(blockType)) {
            return result;
        }
        switch (blockType) {
            case TYPE_IMAGE:
            case TYPE_IMAGE_TEXT:
                result.put("radius", clampInt(result.get("radius"), MIN_RADIUS, MAX_RADIUS, DEFAULT_RADIUS));
                break;
            case TYPE_IMAGE_GRID:
                result.put("columns", clampInt(result.get("columns"), MIN_GRID_COLUMNS, MAX_GRID_COLUMNS,
                        DEFAULT_GRID_COLUMNS));
                result.put("itemMargin", clampItemMargin(result.get("itemMargin")));
                result.put("radius", clampInt(result.get("radius"), MIN_RADIUS, MAX_RADIUS, DEFAULT_RADIUS));
                break;
            default:
                break;
        }
        return result;
    }

    /**
     * 读取时：把任意形状的 itemMargin 夹取成"4 个键一定存在"的对象（缺省全 0），不抛错。
     */
    private static Map<String, Object> clampItemMargin(Object value) {
        Map<String, Object> raw = asMap(value);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("top", clampInt(raw == null ? null : raw.get("top"), MIN_ITEM_MARGIN, MAX_ITEM_MARGIN, 0));
        result.put("right", clampInt(raw == null ? null : raw.get("right"), MIN_ITEM_MARGIN, MAX_ITEM_MARGIN, 0));
        result.put("bottom", clampInt(raw == null ? null : raw.get("bottom"), MIN_ITEM_MARGIN, MAX_ITEM_MARGIN, 0));
        result.put("left", clampInt(raw == null ? null : raw.get("left"), MIN_ITEM_MARGIN, MAX_ITEM_MARGIN, 0));
        return result;
    }

    // ------------------------------------------------------------------
    // 校验小工具
    // ------------------------------------------------------------------

    private static String requireUrl(Object value, String where) {
        String url = trimToNull(value);
        if (url == null) {
            throw new JeecgBootException(where + "缺少图片/视频地址");
        }
        checkLength(url, MAX_URL_LENGTH, where, "地址");
        return url;
    }

    private static String requireText(Object value, int maxLength, String where, String label) {
        String text = value == null ? null : String.valueOf(value);
        if (StringUtils.isBlank(text)) {
            throw new JeecgBootException(where + "缺少" + label + "内容");
        }
        checkLength(text, maxLength, where, label);
        return text;
    }

    private static String optionalText(Object value, int maxLength, String where, String label) {
        String text = value == null ? null : String.valueOf(value);
        if (StringUtils.isBlank(text)) {
            return null;
        }
        checkLength(text, maxLength, where, label);
        return text;
    }

    private static String optionalShortText(Object value, int maxLength, String where, String label) {
        return optionalText(value, maxLength, where, label);
    }

    private static void checkLength(String value, int maxLength, String where, String label) {
        if (value.length() > maxLength) {
            throw new JeecgBootException(where + "的" + label + "过长（最多 " + maxLength
                    + " 字符，当前 " + value.length() + " 字符）");
        }
    }

    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private static String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 保存路径的整数校验：null/空白按默认值；非整数或越界抛明确中文错误（不静默截断）。
     *
     * @param where 错误提示前缀（定位到"第 N 个详情模块（多图宫格）"这一级）
     * @param label 字段中文名（如"图片圆角"）
     */
    private static int optionalInt(Object value, int min, int max, int defaultValue, String where, String label) {
        if (isBlankValue(value)) {
            return defaultValue;
        }
        Integer parsed = toInteger(value);
        if (parsed == null) {
            throw new JeecgBootException(where + "的" + label + "必须是整数");
        }
        if (parsed < min || parsed > max) {
            throw new JeecgBootException(where + "的" + label + "只能是 " + min + "–" + max
                    + " 的整数，当前为 " + parsed);
        }
        return parsed;
    }

    /**
     * 读取路径的整数容错：null/非法 → 默认值；越界 → 夹取到范围内；<b>不抛错</b>。
     */
    private static int clampInt(Object value, int min, int max, int defaultValue) {
        Integer parsed = toInteger(value);
        if (parsed == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, parsed));
    }

    /**
     * 把 JSON 值 / 字符串解析成整数；非整数（12.5、"abc"、对象、超出 int 范围）返回 null。
     */
    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                return null;
            }
            return (int) number;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean isBlankValue(Object value) {
        return value == null || (value instanceof String && StringUtils.isBlank((String) value));
    }

    /**
     * 保存路径：itemMargin 必须是对象（或缺失），4 个方向分别校验；缺省时显式写入 4 个 0。
     */
    private static Map<String, Object> normalizeItemMargin(Object value, String where) {
        if (value != null && !(value instanceof Map)) {
            throw new JeecgBootException(where + "的图片间距 itemMargin 必须是 { top, right, bottom, left } 对象");
        }
        Map<String, Object> raw = asMap(value);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("top", optionalInt(raw == null ? null : raw.get("top"),
                MIN_ITEM_MARGIN, MAX_ITEM_MARGIN, 0, where, "图片间距(上)"));
        result.put("right", optionalInt(raw == null ? null : raw.get("right"),
                MIN_ITEM_MARGIN, MAX_ITEM_MARGIN, 0, where, "图片间距(右)"));
        result.put("bottom", optionalInt(raw == null ? null : raw.get("bottom"),
                MIN_ITEM_MARGIN, MAX_ITEM_MARGIN, 0, where, "图片间距(下)"));
        result.put("left", optionalInt(raw == null ? null : raw.get("left"),
                MIN_ITEM_MARGIN, MAX_ITEM_MARGIN, 0, where, "图片间距(左)"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<?> asList(Object value) {
        return value instanceof List ? (List<?>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }
}
