package org.jeecg.modules.shop.promotion.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 促销的**纯函数工具**支撑组件（无状态、无数据库依赖）。
 *
 * <p>为什么单独拆出来：{@code ShopPromotionServiceImpl} 原本 1200+ 行，活动流程里夹着一批
 * "参数必填校验 / 时间窗校验 / 逗号 ID 解析 / 时间解析 / LIKE 转义 / 商品短标签"这类纯函数。
 * 这里只搬纯函数 —— 无副作用、可复用、可单测。
 *
 * <p>刻意**没有**搬 {@code normalizeLanguage} / {@code appendCopySuffix} / {@code validateStatus}：
 * 它们依赖原类的私有常量（{@code DEFAULT_LANGUAGE} / {@code COPY_TITLE_SUFFIX} / {@code STATUS_*}），
 * 搬出来会造成常量两处定义，反而容易漂移。这类要么留在原类，要么连常量一起搬到公共常量类
 * （那属于更大的改动，留待后续）。
 */
@Component
public class ShopPromotionSupport {

    /** 与管理端表单一致的日期格式；lenient=false 由 parseDate 设置 */
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 必填校验（空则抛业务异常，消息由调用方给出以保留上下文） */
    public void requireNotBlank(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new JeecgBootException(message);
        }
    }

    /** 活动时间窗：两端必填，且开始必须早于结束 */
    public void validateTimeWindow(Date startTime, Date endTime) {
        if (startTime == null || endTime == null) {
            throw new JeecgBootException("活动开始时间与结束时间都不能为空!");
        }
        if (!startTime.before(endTime)) {
            throw new JeecgBootException("活动开始时间必须早于结束时间!");
        }
    }

    /** 商品短标签：优先 spuCode（对外可读），没有则退化为 id */
    public String labelOf(ShopProduct product) {
        return StringUtils.isNotBlank(product.getSpuCode()) ? product.getSpuCode() : product.getId();
    }

    /** 逗号分隔 ID 串 → 去重列表（忽略空白与空段，保持原顺序） */
    public List<String> splitIds(String ids) {
        if (StringUtils.isBlank(ids)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String item : ids.split(",")) {
            String trimmed = StringUtils.trimToNull(item);
            if (trimmed != null && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 解析 {@code yyyy-MM-dd HH:mm:ss}。
     *
     * <p>刻意**不**兜"任意格式"：查询参数格式不对时应该明确报错，
     * 而不是静默按别的格式解析出一个用户没预期的区间（那会查出错误的数据还说成功了）。
     */
    public Date parseDate(String value, String field) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN);
        // lenient=false：'2026-02-30' 这种非法日期必须报错，不能被"顺延"成 3 月 2 日
        format.setLenient(false);
        try {
            return format.parse(value.trim());
        } catch (ParseException e) {
            throw new JeecgBootException("时间格式不正确（" + field + "）：" + value + "，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    /** 关键字进 LIKE 前的转义（反斜杠、单引号、通配符） */
    public String escapeSqlLike(String keyword) {
        return keyword.trim()
                .replace("\\", "\\\\")
                .replace("'", "''")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
