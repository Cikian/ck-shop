package org.jeecg.modules.shop.order.util;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * <p>
 * 查询条件里的日期字符串解析工具。
 * </p>
 *
 * <h3>为什么查询条件用 String 而不是 {@code java.util.Date}</h3>
 * 这不是"图省事"，而是踩过坑之后的选择：
 * <p>Spring 的 {@code DateFormatterRegistrar} 在未显式注册 {@code DateFormatter} 时，
 * 只给 {@code java.util.Date} 注册了与 {@code Long}/{@code Calendar} 的转换器，
 * **不会**注册 String→Date 的格式化器。也就是说，DTO 里写
 * {@code private Date startTime;} 而前端传 {@code 2026-10-14 18:30:00} 时，
 * 绑定会直接抛 {@code TypeMismatchException}，接口返回 400——
 * 而且**只有带时间筛选的请求才会 400**，不带时间筛选一切正常。
 * 这类"只在一个筛选条件下崩"的问题极难在联调前发现。
 *
 * <p>本项目的促销模块早已改用同样的方式（{@code PromotionQuery.startTimeBegin} 为 String，
 * 由 Controller 解析），这里保持一致并对解析逻辑做了统一收口。
 *
 * <h3>结束时间的语义：按给定精度**包含**</h3>
 * 用户在选择"10-14 ~ 10-14"时，期望的是"10 月 14 日一整天"，
 * 而不是"10 月 14 日 00:00:00 这一瞬间"。因此 {@link #parseEnd} 返回的是
 * 与查询配合使用的**右开边界**：
 * <ul>
 *   <li>{@code yyyy-MM-dd} / {@code yyyyMMdd} → 次日 00:00:00（覆盖整天）</li>
 *   <li>{@code yyyy-MM-dd HH:mm} → 该分钟 + 1 分钟（覆盖整分钟）</li>
 *   <li>{@code yyyy-MM-dd HH:mm:ss} → 该秒 + 1 秒（覆盖整秒）</li>
 *   <li>{@code yyyy-MM-dd 00:00:00} → **次日 00:00:00**。
 *       很多按天粒度的日期选择器会把选中的日期格式化成
 *       {@code "2026-10-14 00:00:00"}，此时用户的意图显然是"到 10-14 为止"。
 *       不补这一天会把 10-14 一整天的订单**静默漏掉**（用户以为"这段时间没单"），
 *       而多算一天肉眼可见。交易系统里宁可多算，不可少算。</li>
 * </ul>
 * 与 SQL 侧的 {@code < end} 配合，语义就是"左闭右闭且不重叠"，
 * 既不会漏掉最后一秒的订单，也不会因为用 {@code BETWEEN} 而重复统计边界记录。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public final class QueryDateUtils {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private QueryDateUtils() {
    }

    /**
     * 解析开始时间（左闭）。
     *
     * @param value       可为空；为空返回 null（表示不限）
     * @param fieldName   字段中文名，用于拼错误提示
     */
    public static Date parseStart(String value, String fieldName) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return toDate(parseLocal(trimmed, fieldName), fieldName);
    }

    /**
     * 解析结束时间，返回**右开边界**（比用户给定的时刻多一个最小单位）。
     *
     * @see QueryDateUtils 类注释中的"结束时间的语义"
     */
    public static Date parseEnd(String value, String fieldName) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String normalized = normalize(trimmed);
        if (normalized.length() == 8) {
            return toDate(LocalDate.parse(normalized, COMPACT).plusDays(1).atStartOfDay(), fieldName);
        }
        if (normalized.length() == 10) {
            return toDate(LocalDate.parse(normalized, DATE).plusDays(1).atStartOfDay(), fieldName);
        }
        if (normalized.length() == 16) {
            return toDate(LocalDateTime.parse(normalized, MINUTE).plusMinutes(1), fieldName);
        }
        LocalDateTime end = LocalDateTime.parse(normalized, SECOND);
        if (end.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            // 关键的用户意图判定：很多日期选择器（按天粒度）会把选中的日期格式化成
            // "2026-10-14 00:00:00"。用户的意图是"到 10-14 为止"，
            // 而不是"到 10-14 的零点这一瞬间"。
            //
            // 这里**主动补一整天**。理由：两种理解导致错误的方向完全不同——
            //  - 补一天：最坏情况是多包含一天的数据，用户一眼能看出来；
            //  - 不补：会把 10-14 一整天的订单全部漏掉，而用户看到的是"这个时间段没有订单"，
            //    属于**静默漏数据**，可能直接导致对账短款、客诉查不到单。
            // 在交易系统里，宁愿多算，不可少算。
            return toDate(end.plusDays(1), fieldName);
        }
        return toDate(end.plusSeconds(1), fieldName);
    }

    /**
     * 宽松解析：兼容 {@code yyyy-MM-dd}、{@code yyyy-MM-dd HH:mm}、
     * {@code yyyy-MM-dd HH:mm:ss}、{@code yyyyMMdd}，
     * 以及 {@code T} 分隔的 ISO 形式（前端 Date.toISOString() 常见）。
     * 日期部分里的 {@code /} 与 {@code .} 统一成 {@code -}。
     */
    private static LocalDateTime parseLocal(String value, String fieldName) {
        String normalized = normalize(value);
        try {
            switch (normalized.length()) {
                case 8:
                    return LocalDate.parse(normalized, COMPACT).atStartOfDay();
                case 10:
                    return LocalDate.parse(normalized, DATE).atStartOfDay();
                case 16:
                    return LocalDateTime.parse(normalized, MINUTE);
                case 19:
                    return LocalDateTime.parse(normalized, SECOND);
                default:
                    throw new JeecgBootException(fieldName + "格式不正确：[" + value
                            + "]，支持 yyyy-MM-dd / yyyy-MM-dd HH:mm / yyyy-MM-dd HH:mm:ss");
            }
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            throw new JeecgBootException(fieldName + "格式不正确：[" + value
                    + "]，支持 yyyy-MM-dd / yyyy-MM-dd HH:mm / yyyy-MM-dd HH:mm:ss");
        }
    }

    private static String normalize(String value) {
        String normalized = value.trim().replace('T', ' ').replace('/', '-').replace('.', '-');
        // 去掉时区后缀（前端 toISOString 会带 Z 或 +08:00）
        int zoneIndex = normalized.indexOf('Z');
        if (zoneIndex > 0) {
            normalized = normalized.substring(0, zoneIndex);
        }
        int spaceIndex = normalized.indexOf(' ');
        if (spaceIndex > 0) {
            String tail = normalized.substring(spaceIndex + 1);
            int plus = tail.indexOf('+');
            if (plus > 0) {
                normalized = normalized.substring(0, spaceIndex + 1) + tail.substring(0, plus);
            }
        }
        // JS 的 "2026-10-14 18:30:00.000" 截到秒
        if (normalized.length() > 19) {
            normalized = normalized.substring(0, 19);
        }
        return normalized.trim();
    }

    private static Date toDate(LocalDateTime dateTime, String fieldName) {
        try {
            return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            throw new JeecgBootException(fieldName + "解析失败，请检查格式");
        }
    }
}
