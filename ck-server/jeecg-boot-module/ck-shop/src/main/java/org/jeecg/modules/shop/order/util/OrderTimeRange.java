package org.jeecg.modules.shop.order.util;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.entity.dto.OrderQuery;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;

/**
 * <p>
 * 订单统计/列表的**时间区间**（已解析并规范化后的内部模型）。
 * </p>
 *
 * <h3>为什么单独一个类</h3>
 * 查询 DTO 里的时间是 String（见 {@link QueryDateUtils} 的说明），
 * 而 SQL 需要 Date。如果每个 Service 各自解析，
 * "结束时间要不要 +1 天"这种语义迟早会在两处写得不一样，
 * 然后就是"列表能查到、统计查不到"这类无法解释的差异。
 * 这里把解析与默认值规则收口一次。
 *
 * <h3>默认区间：最近 N 天</h3>
 * 前端不传时间时给一个默认区间，而不是"不限时间"：
 * 订单表是增长最快的表，一个不限时间的聚合查询在大促后能把数据库拖垮。
 * 默认 30 天，且**只作用于统计接口**；后台列表不传时间就是不限（列表有分页兜底）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
public class OrderTimeRange implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 下单时间左边界（含） */
    private Date start;

    /** 下单时间右边界（不含） */
    private Date end;

    /** 支付时间左边界（含） */
    private Date payStart;

    /** 支付时间右边界（不含） */
    private Date payEnd;

    /** 是否使用了默认区间（前端没传时间时置 true，便于在返回里说明口径） */
    private boolean defaulted;

    /**
     * 从查询条件解析出时间区间（不填默认值），用于**列表**查询。
     */
    public static OrderTimeRange parse(OrderQuery query) {
        OrderTimeRange range = new OrderTimeRange();
        if (query == null) {
            return range;
        }
        range.setStart(QueryDateUtils.parseStart(query.getStartTime(), "下单开始时间"));
        range.setEnd(QueryDateUtils.parseEnd(query.getEndTime(), "下单结束时间"));
        range.setPayStart(QueryDateUtils.parseStart(query.getPayStartTime(), "支付开始时间"));
        range.setPayEnd(QueryDateUtils.parseEnd(query.getPayEndTime(), "支付结束时间"));
        validate(range);
        return range;
    }

    /**
     * 从查询条件解析出时间区间，**缺失时套用默认区间**（最近 defaultDays 天），
     * 用于**统计**查询。
     */
    public static OrderTimeRange parseWithDefault(OrderQuery query, int defaultDays) {
        OrderQuery condition = query == null ? new OrderQuery() : query;
        boolean missing = StringUtils.isBlank(condition.getStartTime())
                && StringUtils.isBlank(condition.getEndTime());
        Date end = QueryDateUtils.parseEnd(condition.getEndTime(), "统计结束时间");
        Date start = QueryDateUtils.parseStart(condition.getStartTime(), "统计开始时间");
        boolean defaulted = false;
        if (end == null) {
            end = tomorrowStart();
        }
        if (start == null) {
            int days = defaultDays < 1 ? 30 : defaultDays;
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(end);
            calendar.add(Calendar.DAY_OF_YEAR, -days);
            start = calendar.getTime();
            defaulted = true;
        }

        OrderTimeRange range = new OrderTimeRange()
                .setStart(start)
                .setEnd(end)
                .setDefaulted(defaulted || missing);
        range.setPayStart(QueryDateUtils.parseStart(condition.getPayStartTime(), "支付开始时间"));
        range.setPayEnd(QueryDateUtils.parseEnd(condition.getPayEndTime(), "支付结束时间"));
        validate(range);
        return range;
    }

    /** 今日起点（左闭） */
    public static Date todayStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /** 明日起点（左闭右开区间的右边界，保证"今天"被完整包含） */
    public static Date tomorrowStart() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(todayStart());
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        return calendar.getTime();
    }

    private static void validate(OrderTimeRange range) {
        if (range.getStart() != null && range.getEnd() != null
                && range.getStart().after(range.getEnd())) {
            throw new JeecgBootException("开始时间不能晚于结束时间");
        }
        if (range.getPayStart() != null && range.getPayEnd() != null
                && range.getPayStart().after(range.getPayEnd())) {
            throw new JeecgBootException("支付开始时间不能晚于支付结束时间");
        }
        Date now = new Date();
        if (range.getStart() != null && range.getStart().after(now)) {
            throw new JeecgBootException("开始时间不能晚于当前时间");
        }
    }
}
