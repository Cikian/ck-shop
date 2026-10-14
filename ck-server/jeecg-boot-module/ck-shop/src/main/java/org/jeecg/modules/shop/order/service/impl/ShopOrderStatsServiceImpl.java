package org.jeecg.modules.shop.order.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.shop.order.constant.OrderStatus;
import org.jeecg.modules.shop.order.entity.dto.OrderQuery;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsOverviewVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsRankVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsStatusVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsTrendVO;
import org.jeecg.modules.shop.order.mapper.ShopOrderItemMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderStatsMapper;
import org.jeecg.modules.shop.order.service.IShopOrderStatsService;
import org.jeecg.modules.shop.order.util.MoneyUtils;
import org.jeecg.modules.shop.order.util.OrderTimeRange;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * <p>
 * 订单统计实现。
 * </p>
 *
 * <h3>为什么全部用实时聚合，不做日汇总表</h3>
 * 日汇总表能提升大屏性能，但它引入了一个新的、更隐蔽的风险：
 * **汇总与明细不一致**。只要有一次补单、一次人工改状态、一次任务重跑，
 * 汇总表就可能与订单表对不上，而这种偏差没有自动手段能发现——
 * 最后变成"看板和报表数字不一样，但不知道哪个对"。
 *
 * <p>当前方案依赖已有的索引（{@code idx_order_store_status_time}、
 * {@code idx_order_store_paytime}、{@code idx_item_store_time} 等）做区间聚合，
 * 在十万级订单量下响应在百毫秒级。真到了需要汇总表的量级，
 * 应当由离线数仓承接，而不是在交易库里维护一张"可能不准"的表。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@Service
public class ShopOrderStatsServiceImpl implements IShopOrderStatsService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 趋势区间最长天数：再长由前端改成分页/按周，避免一次返回几年的数据把图表拖垮 */
    private static final int MAX_TREND_DAYS = 180;

    @Autowired
    private ShopOrderMapper orderMapper;

    @Autowired
    private ShopOrderItemMapper orderItemMapper;

    @Autowired
    private ShopOrderStatsMapper statsMapper;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    /** 默认统计区间（天） */
    @Value("${ck.shop.order.stats.default-days:30}")
    private int defaultDays;

    // ==================================================================
    // 总览
    // ==================================================================

    @Override
    public OrderStatsOverviewVO overview(OrderQuery query) {
        OrderQuery condition = query == null ? new OrderQuery() : query;
        OrderTimeRange range = OrderTimeRange.parseWithDefault(condition, defaultDays);
        List<String> scope = resolveScope(condition.getStoreId());

        OrderStatsOverviewVO vo = orderMapper.statsOverview(scope, null, range.getStart(), range.getEnd());
        if (vo == null) {
            vo = new OrderStatsOverviewVO();
        }
        fillNulls(vo);
        computeRates(vo);

        // 今日指标：用同一套口径再算一次当日区间，保证与总览完全可比
        OrderStatsOverviewVO today = orderMapper.statsOverview(scope, null,
                OrderTimeRange.todayStart(), OrderTimeRange.tomorrowStart());
        if (today != null) {
            vo.setTodayOrders(nvl(today.getTotalOrders()))
                    .setTodayPaidAmount(nvl(today.getPaidAmount()))
                    .setTodayRefundAmount(nvl(today.getRefundAmount()))
                    .setTodayGmv(nvl(today.getGmv()));
        }

        // 风险指标：不受时间区间影响，永远看"当前状态"，否则会漏掉区间外的异常单
        vo.setClosedButPaidOrders(nvl(orderMapper.countClosedButPaid(scope, null)));
        vo.setPendingPayments(nvl(statsMapper.countPendingPayments(scope, null,
                minutesAgo(10))));
        vo.setPendingRefunds(nvl(statsMapper.countPendingRefunds(scope, null)));

        // 加购用户数：用于"加购 → 下单"漏斗。独立于订单表，口径单独说明
        vo.setCartUsers(nvl(statsMapper.countCartUsers(scope, null, range.getStart(), range.getEnd())));
        if (vo.getCartUsers() > 0 && nvl(vo.getOrderUsers()) > 0) {
            vo.setCartConversionRate(ratio(vo.getOrderUsers(), vo.getCartUsers()));
        } else {
            vo.setCartConversionRate(BigDecimal.ZERO.setScale(4));
        }
        return vo;
    }

    private void fillNulls(OrderStatsOverviewVO vo) {
        vo.setTotalOrders(nvl(vo.getTotalOrders()));
        vo.setWaitPayOrders(nvl(vo.getWaitPayOrders()));
        vo.setWaitShipOrders(nvl(vo.getWaitShipOrders()));
        vo.setShippedOrders(nvl(vo.getShippedOrders()));
        vo.setCompletedOrders(nvl(vo.getCompletedOrders()));
        vo.setCancelledOrders(nvl(vo.getCancelledOrders()));
        vo.setClosedOrders(nvl(vo.getClosedOrders()));
        vo.setPaidOrders(nvl(vo.getPaidOrders()));
        vo.setTotalQuantity(nvl(vo.getTotalQuantity()));
        vo.setOrderUsers(nvl(vo.getOrderUsers()));
        vo.setGmv(MoneyUtils.scale(vo.getGmv()));
        vo.setPaidAmount(MoneyUtils.scale(vo.getPaidAmount()));
        vo.setRefundAmount(MoneyUtils.scale(vo.getRefundAmount()));
        vo.setDiscountAmount(MoneyUtils.scale(vo.getDiscountAmount()));
        vo.setFreightAmount(MoneyUtils.scale(vo.getFreightAmount()));
        vo.setCostAmount(MoneyUtils.scale(vo.getCostAmount()));
        vo.setGrossProfit(MoneyUtils.scale(vo.getGrossProfit()));
    }

    private void computeRates(OrderStatsOverviewVO vo) {
        vo.setNetAmount(MoneyUtils.subtract(vo.getPaidAmount(), vo.getRefundAmount()));
        if (vo.getPaidOrders() > 0) {
            vo.setAvgOrderAmount(MoneyUtils.scale(
                    MoneyUtils.scale(vo.getPaidAmount())
                            .divide(BigDecimal.valueOf(vo.getPaidOrders()), 2, RoundingMode.HALF_UP)));
        } else {
            vo.setAvgOrderAmount(MoneyUtils.zero());
        }
        vo.setPayConversionRate(ratio(vo.getPaidOrders(), vo.getTotalOrders()));
        // 退款率按**金额**而不是单数：退 1 件 9999 元与退 10 件 9.9 元，
        // 对资金的影响差三个数量级，按单数算会严重低估风险
        vo.setRefundRate(MoneyUtils.isPositive(vo.getPaidAmount())
                ? ratio(vo.getRefundAmount(), vo.getPaidAmount())
                : BigDecimal.ZERO.setScale(4));
        // 毛利率的分母用"成交金额"（优惠后），而不是 GMV：
        // 用 GMV 会把优惠算成利润
        BigDecimal netSales = MoneyUtils.subtract(vo.getGmv(), vo.getDiscountAmount());
        vo.setGrossProfitRate(MoneyUtils.isPositive(netSales)
                ? ratio(vo.getGrossProfit(), netSales)
                : BigDecimal.ZERO.setScale(4));
    }

    // ==================================================================
    // 状态分布
    // ==================================================================

    @Override
    public List<OrderStatsStatusVO> statusDistribution(OrderQuery query) {
        OrderQuery condition = query == null ? new OrderQuery() : query;
        OrderTimeRange range = OrderTimeRange.parseWithDefault(condition, defaultDays);
        List<String> scope = resolveScope(condition.getStoreId());
        List<OrderStatsStatusVO> rows = orderMapper.statsByStatus(scope, null,
                range.getStart(), range.getEnd());
        List<OrderStatsStatusVO> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (OrderStatsStatusVO row : rows) {
            row.setStatusLabel(OrderStatus.labelOf(row.getStatus()));
            row.setAmount(MoneyUtils.scale(row.getAmount()));
            if (row.getOrderCount() == null) {
                row.setOrderCount(0L);
            }
            result.add(row);
        }
        return result;
    }

    // ==================================================================
    // 趋势
    // ==================================================================

    @Override
    public List<OrderStatsTrendVO> trend(OrderQuery query) {
        OrderQuery condition = query == null ? new OrderQuery() : query;
        OrderTimeRange range = OrderTimeRange.parseWithDefault(condition, defaultDays);
        List<String> scope = resolveScope(condition.getStoreId());

        // 三种时间口径分别聚合，再按日期合并 —— 强行写进一条 SQL 会让
        // "按下单/按支付/按退款"三个归日维度互相污染
        Map<String, OrderStatsTrendVO> merged = new TreeMap<>();
        mergeOrderTrend(merged, orderMapper.statsOrderTrend(scope, null, range.getStart(), range.getEnd()));
        mergePayTrend(merged, orderMapper.statsPayTrend(scope, null, range.getStart(), range.getEnd()));
        mergeRefundTrend(merged, orderMapper.statsRefundTrend(scope, null, range.getStart(), range.getEnd()));

        // 补齐区间内没有数据的日期：图表缺日期会让人误以为"那天系统没运行"
        fillEmptyDates(merged, range.getStart(), range.getEnd());

        List<OrderStatsTrendVO> result = new ArrayList<>(merged.values());
        for (OrderStatsTrendVO row : result) {
            row.setGmv(MoneyUtils.scale(row.getGmv()));
            row.setPaidAmount(MoneyUtils.scale(row.getPaidAmount()));
            row.setRefundAmount(MoneyUtils.scale(row.getRefundAmount()));
            row.setNetAmount(MoneyUtils.subtract(row.getPaidAmount(), row.getRefundAmount()));
            if (row.getOrderCount() == null) {
                row.setOrderCount(0L);
            }
            if (row.getPaidCount() == null) {
                row.setPaidCount(0L);
            }
            if (row.getQuantity() == null) {
                row.setQuantity(0L);
            }
        }
        return result;
    }

    private void mergeOrderTrend(Map<String, OrderStatsTrendVO> merged,
                                 List<OrderStatsTrendVO> rows) {
        if (rows == null) {
            return;
        }
        for (OrderStatsTrendVO row : rows) {
            OrderStatsTrendVO target = target(merged, row.getDate());
            target.setOrderCount(row.getOrderCount());
            target.setQuantity(row.getQuantity());
        }
    }

    private void mergePayTrend(Map<String, OrderStatsTrendVO> merged,
                              List<OrderStatsTrendVO> rows) {
        if (rows == null) {
            return;
        }
        for (OrderStatsTrendVO row : rows) {
            OrderStatsTrendVO target = target(merged, row.getDate());
            target.setPaidCount(row.getPaidCount());
            target.setGmv(row.getGmv());
            target.setPaidAmount(row.getPaidAmount());
        }
    }

    private void mergeRefundTrend(Map<String, OrderStatsTrendVO> merged,
                                 List<OrderStatsTrendVO> rows) {
        if (rows == null) {
            return;
        }
        for (OrderStatsTrendVO row : rows) {
            OrderStatsTrendVO target = target(merged, row.getDate());
            target.setRefundAmount(row.getRefundAmount());
        }
    }

    private OrderStatsTrendVO target(Map<String, OrderStatsTrendVO> merged, String date) {
        String key = StringUtils.defaultIfBlank(date, "unknown");
        return merged.computeIfAbsent(key, k -> new OrderStatsTrendVO().setDate(k));
    }

    private void fillEmptyDates(Map<String, OrderStatsTrendVO> merged, Date start, Date end) {
        if (start == null || end == null) {
            return;
        }
        LocalDate from = toLocalDate(start);
        LocalDate to = toLocalDate(end).minusDays(1);
        if (to.isBefore(from)) {
            return;
        }
        int days = (int) (to.toEpochDay() - from.toEpochDay()) + 1;
        if (days > MAX_TREND_DAYS) {
            return;
        }
        for (int i = 0; i < days; i++) {
            String key = from.plusDays(i).format(DATE_FORMAT);
            if (!merged.containsKey(key)) {
                merged.put(key, new OrderStatsTrendVO().setDate(key));
            }
        }
    }

    // ==================================================================
    // 排行
    // ==================================================================

    @Override
    public List<OrderStatsRankVO> rankByProduct(OrderQuery query, Integer limit) {
        OrderQuery condition = query == null ? new OrderQuery() : query;
        OrderTimeRange range = OrderTimeRange.parseWithDefault(condition, defaultDays);
        List<String> scope = resolveScope(condition.getStoreId());
        List<OrderStatsRankVO> rows = orderItemMapper.statsRankByProduct(scope, null,
                range.getStart(), range.getEnd(), normalizeLimit(limit));
        return decorate(rows);
    }

    @Override
    public List<OrderStatsRankVO> rankByCategory(OrderQuery query, Integer limit) {
        OrderQuery condition = query == null ? new OrderQuery() : query;
        OrderTimeRange range = OrderTimeRange.parseWithDefault(condition, defaultDays);
        List<String> scope = resolveScope(condition.getStoreId());
        List<OrderStatsRankVO> rows = orderItemMapper.statsRankByCategory(scope, null,
                range.getStart(), range.getEnd(), normalizeLimit(limit));
        return decorate(rows);
    }

    private List<OrderStatsRankVO> decorate(List<OrderStatsRankVO> rows) {
        List<OrderStatsRankVO> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        int rank = 1;
        for (OrderStatsRankVO row : rows) {
            row.setRank(rank++);
            row.setSalesAmount(MoneyUtils.scale(row.getSalesAmount()));
            row.setGrossProfit(MoneyUtils.scale(row.getGrossProfit()));
            if (row.getQuantity() == null) {
                row.setQuantity(0L);
            }
            if (row.getOrderCount() == null) {
                row.setOrderCount(0L);
            }
            result.add(row);
        }
        return result;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 10;
        }
        // 上限 100：防止有人用 limit=100000 拉全表
        return Math.min(limit, 100);
    }

    // ==================================================================
    // 区间与可见域
    // ==================================================================

    @Override
    public OrderTimeRange resolveRange(OrderQuery query) {
        return OrderTimeRange.parseWithDefault(query == null ? new OrderQuery() : query, defaultDays);
    }

    /**
     * 解析可见域：账号可见域 ∩ 店铺筛选域。
     *
     * <p>返回 {@code null} 表示不限；返回空列表表示"两个域无交集"，
     * 映射到 SQL 是恒假条件 → 查不到任何数据（fail-closed）。
     * **绝不把"没有交集"当成"不过滤"**，那等于越权看到全部数据。
     */
    private List<String> resolveScope(String storeId) {
        List<String> visible = orgScopeResolver.resolveVisibleOrgCodes();
        if (StringUtils.isBlank(storeId)) {
            return visible;
        }
        String storeOrgCode = orgScopeResolver.resolveStoreOrgCode(storeId);
        return orgScopeResolver.mergeScope(storeOrgCode, visible);
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 比率，统一保留 4 位小数（百分比前端自己 ×100），避免各种精度混用 */
    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal n = nvl(numerator);
        BigDecimal d = nvl(denominator);
        if (d.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return n.divide(d, 4, RoundingMode.HALF_UP);
    }

    private Date minutesAgo(int minutes) {
        return new Date(System.currentTimeMillis() - minutes * 60_000L);
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** 供未来扩展：把按店铺维度汇总的统计结果导出为 CSV */
    @SuppressWarnings("unused")
    private Map<String, Object> emptyRow() {
        return new LinkedHashMap<>();
    }
}
