package org.jeecg.modules.shop.analytics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.modules.shop.analytics.entity.TrackEvent;
import org.jeecg.modules.shop.analytics.entity.TrackSummary;
import org.jeecg.modules.shop.analytics.entity.dto.AnalyticsQuery;
import org.jeecg.modules.shop.analytics.entity.vo.DeviceVO;
import org.jeecg.modules.shop.analytics.entity.vo.FunnelStepVO;
import org.jeecg.modules.shop.analytics.entity.vo.FunnelVO;
import org.jeecg.modules.shop.analytics.entity.vo.PageVO;
import org.jeecg.modules.shop.analytics.entity.vo.RankProductVO;
import org.jeecg.modules.shop.analytics.entity.vo.RealtimeOverviewVO;
import org.jeecg.modules.shop.analytics.entity.vo.RegionVO;
import org.jeecg.modules.shop.analytics.entity.vo.SourceVO;
import org.jeecg.modules.shop.analytics.entity.vo.TrendVO;
import org.jeecg.modules.shop.analytics.mapper.TrackEventMapper;
import org.jeecg.modules.shop.analytics.mapper.TrackSummaryMapper;
import org.jeecg.modules.shop.analytics.service.IAnalyticsQueryService;
import org.jeecg.modules.shop.analytics.service.TrackRedisService;
import org.jeecg.modules.shop.cart.entity.ShopCartEvent;
import org.jeecg.modules.shop.cart.mapper.ShopCartEventMapper;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsOverviewVO;
import org.jeecg.modules.shop.order.mapper.ShopOrderMapper;
import org.jeecg.modules.shop.order.util.MoneyUtils;
import org.jeecg.modules.shop.order.util.QueryDateUtils;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 后台行为分析查询实现。
 * </p>
 *
 * <p>查询只读汇总表 + Redis + 业务表回填，不实时聚合大表（契约 §9.2）。
 * 比率字段返回字符串"百分数数值、不带 %、保留 2 位"（如 "12.34"）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Slf4j
@Service
public class AnalyticsQueryServiceImpl implements IAnalyticsQueryService {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 默认统计区间 30 天 */
    private static final int DEFAULT_DAYS = 30;

    private final TrackSummaryMapper summaryMapper;
    private final TrackEventMapper trackEventMapper;
    private final ShopOrderMapper orderMapper;
    private final ShopCartEventMapper cartEventMapper;
    private final ShopOrgScopeResolver orgScopeResolver;
    private final TrackRedisService trackRedisService;

    public AnalyticsQueryServiceImpl(TrackSummaryMapper summaryMapper,
                                     TrackEventMapper trackEventMapper,
                                     ShopOrderMapper orderMapper,
                                     ShopCartEventMapper cartEventMapper,
                                     ShopOrgScopeResolver orgScopeResolver,
                                     TrackRedisService trackRedisService) {
        this.summaryMapper = summaryMapper;
        this.trackEventMapper = trackEventMapper;
        this.orderMapper = orderMapper;
        this.cartEventMapper = cartEventMapper;
        this.orgScopeResolver = orgScopeResolver;
        this.trackRedisService = trackRedisService;
    }

    // ==================================================================
    // 实时大盘
    // ==================================================================

    @Override
    public RealtimeOverviewVO realtimeOverview() {
        RealtimeOverviewVO vo = new RealtimeOverviewVO();
        vo.setOnlineCount(trackRedisService.onlineCount());

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        long pv = 0, uv = 0, ip = 0, visit = 0;
        for (TrackSummary s : readSummary("global", "day", todayStart, tomorrowStart, null, null)) {
            pv += nvl(s.getPv());
            uv += nvl(s.getUv());
            ip += nvl(s.getIpCount());
            visit += nvl(s.getVisitCount());
        }
        vo.setTodayPv(pv);
        vo.setTodayUv(uv);
        vo.setTodayIp(ip);
        vo.setTodayVisit(visit);

        // 今日订单数 / 今日实收：与订单统计总览"今日"口径一致（按下单时间归日）
        List<String> scope = resolveBusinessScope(null);
        OrderStatsOverviewVO order = orderMapper.statsOverview(scope, null,
                toDate(todayStart), toDate(tomorrowStart));
        vo.setTodayOrderCount(order == null || order.getTotalOrders() == null ? 0L : order.getTotalOrders());
        vo.setTodayPaidAmount(order == null || order.getPaidAmount() == null
                ? "0.00" : MoneyUtils.scale(order.getPaidAmount()).toPlainString());
        return vo;
    }

    // ==================================================================
    // 趋势
    // ==================================================================

    @Override
    public List<TrendVO> trend(AnalyticsQuery query) {
        LocalDateTime[] range = resolveRange(query);
        String granularity = StringUtils.defaultIfBlank(query == null ? null : query.getGranularity(), "day");
        String dim = StringUtils.defaultIfBlank(query == null ? null : query.getDim(), "global");

        List<TrackSummary> rows = readSummary(dim, granularity, range[0], range[1],
                query == null ? null : query.getStoreId(), null);

        Map<LocalDateTime, TrendVO> result = new LinkedHashMap<>();
        Map<LocalDateTime, long[]> acc = new HashMap<>();
        for (TrackSummary s : rows) {
            LocalDateTime t = s.getStatTime();
            TrendVO vo = result.computeIfAbsent(t, k -> new TrendVO().setTime(DATETIME_FMT.format(k)));
            vo.setPv(addLong(vo.getPv(), s.getPv()));
            vo.setUv(addLong(vo.getUv(), s.getUv()));
            vo.setVisit(addLong(vo.getVisit(), s.getVisitCount()));
            vo.setAddCartCount(addLong(vo.getAddCartCount(), s.getAddCartCount()));
            vo.setOrderCount(addLong(vo.getOrderCount(), s.getOrderCount()));
            vo.setPaidCount(addLong(vo.getPaidCount(), s.getPaidCount()));
            long[] a = acc.computeIfAbsent(t, k -> new long[2]);
            a[0] += nvl(s.getTotalDurationMs());
            a[1] += nvl(s.getVisitCount());
        }
        for (Map.Entry<LocalDateTime, long[]> e : acc.entrySet()) {
            result.get(e.getKey()).setAvgDurationMs(avgDuration(e.getValue()[0], e.getValue()[1]));
        }
        return fillBuckets(result, range[0], range[1], granularity);
    }

    /**
     * 把缺失的时间桶补 0，保证趋势图是连续曲线。
     *
     * <p>不补零时，只有"有数据的那几个桶"会返回：新站第一天只有 1 个点，
     * 图表看上去像空的（用户反馈"每个页面都很空"的直接原因之一）。
     *
     * @param start 区间起点（含）
     * @param end   区间终点（不含；与 summary 查询口径一致）
     */
    private List<TrendVO> fillBuckets(Map<LocalDateTime, TrendVO> existing,
                                      LocalDateTime start, LocalDateTime end, String granularity) {
        List<TrendVO> out = new ArrayList<>();
        boolean hour = "hour".equals(granularity);
        LocalDateTime cursor = hour
                ? start.withMinute(0).withSecond(0).withNano(0)
                : start.toLocalDate().atStartOfDay();
        // 上限保护：自定义超大区间最多 400 个点，避免把接口拖慢
        int guard = 0;
        while (cursor.isBefore(end) && guard++ < 400) {
            TrendVO vo = existing.get(cursor);
            if (vo == null) {
                vo = new TrendVO().setTime(DATETIME_FMT.format(cursor));
                vo.setPv(0L);
                vo.setUv(0L);
                vo.setVisit(0L);
                vo.setAddCartCount(0L);
                vo.setOrderCount(0L);
                vo.setPaidCount(0L);
                vo.setAvgDurationMs(0L);
            }
            out.add(vo);
            cursor = hour ? cursor.plusHours(1) : cursor.plusDays(1);
        }
        return out.isEmpty() ? new ArrayList<>(existing.values()) : out;
    }

    // ==================================================================
    // 商品排行
    // ==================================================================

    @Override
    public List<RankProductVO> rankProduct(AnalyticsQuery query) {
        LocalDateTime[] range = resolveRange(query);
        List<TrackSummary> rows = readSummary("product", "day", range[0], range[1],
                query == null ? null : query.getStoreId(), null);

        Map<String, long[]> acc = new LinkedHashMap<>();
        for (TrackSummary s : rows) {
            String pid = s.getDimValue();
            long[] a = acc.computeIfAbsent(pid, k -> new long[8]);
            a[0] += nvl(s.getPv());              // viewCount = product_view
            a[1] += nvl(s.getVisitCount());
            a[2] += nvl(s.getTotalDurationMs());
            a[3] += nvl(s.getAddCartCount());
            a[4] += nvl(s.getOrderCount());
            a[5] += nvl(s.getPaidCount());
            a[6] += nvl(s.getExposeCount());
            a[7] += nvl(s.getClickCount());
        }

        List<RankProductVO> result = new ArrayList<>();
        for (Map.Entry<String, long[]> e : acc.entrySet()) {
            long[] a = e.getValue();
            RankProductVO vo = new RankProductVO();
            vo.setProductId(e.getKey());
            vo.setViewCount(a[0]);
            vo.setVisitCount(a[1]);
            vo.setAvgDurationMs(avgDuration(a[2], a[1]));
            vo.setAddCartCount(a[3]);
            vo.setOrderCount(a[4]);
            vo.setPaidCount(a[5]);
            vo.setClickRate(percent(a[7], a[6]));
            vo.setAddCartRate(percent(a[3], a[0]));
            vo.setConversionRate(percent(a[4], a[0]));
            result.add(vo);
        }
        result.sort((x, y) -> Long.compare(y.getViewCount(), x.getViewCount()));
        int limit = normalizeLimit(query == null ? null : query.getLimit());
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    // ==================================================================
    // 漏斗
    // ==================================================================

    @Override
    public FunnelVO funnel(AnalyticsQuery query) {
        LocalDateTime[] range = resolveRange(query);
        String storeId = query == null ? null : query.getStoreId();
        List<String> scope = resolveBusinessScope(storeId);

        long visit = 0;
        for (TrackSummary s : readSummary("global", "day", range[0], range[1], storeId, null)) {
            visit += nvl(s.getPv());
        }
        long addCart = cartEventMapper.selectCount(cartWrapper(range, scope));
        long order = orderMapper.selectCount(orderCreateWrapper(range, scope));
        long paid = orderMapper.selectCount(orderPaidWrapper(range, scope));

        FunnelVO vo = new FunnelVO();
        List<FunnelStepVO> steps = new ArrayList<>();
        steps.add(step("visit", "访问", visit, null));
        steps.add(step("add_cart", "加购", addCart, visit));
        steps.add(step("order", "下单", order, addCart));
        steps.add(step("paid", "支付", paid, order));
        vo.setSteps(steps);
        return vo;
    }

    private FunnelStepVO step(String key, String label, long count, Long prev) {
        FunnelStepVO s = new FunnelStepVO();
        s.setStep(key);
        s.setLabel(label);
        s.setCount(count);
        s.setRate(prev == null ? "100.00" : percent(count, prev));
        return s;
    }

    // ==================================================================
    // 地域 / 来源 / 设备 / 页面
    // ==================================================================

    @Override
    public List<RegionVO> region(AnalyticsQuery query) {
        LocalDateTime[] range = resolveRange(query);
        String level = StringUtils.defaultIfBlank(query == null ? null : query.getLevel(), "country");
        String prefix = "province".equals(level) ? "p:" : ("city".equals(level) ? "t:" : "c:");
        List<TrackSummary> rows = readSummary("region", "day", range[0], range[1],
                query == null ? null : query.getStoreId(), prefix);

        Map<String, RegionVO> map = new LinkedHashMap<>();
        for (TrackSummary s : rows) {
            RegionVO vo = map.computeIfAbsent(s.getDimValue(), AnalyticsQueryServiceImpl::parseRegion);
            vo.setPv(addLong(vo.getPv(), s.getPv()));
            vo.setUv(addLong(vo.getUv(), s.getUv()));
            vo.setIp(addLong(vo.getIp(), s.getIpCount()));
        }
        List<RegionVO> result = new ArrayList<>(map.values());
        result.sort((x, y) -> Long.compare(y.getPv(), x.getPv()));
        return result;
    }

    @Override
    public List<SourceVO> source(AnalyticsQuery query) {
        LocalDateTime[] range = resolveRange(query);
        List<TrackSummary> rows = readSummary("source", "day", range[0], range[1],
                query == null ? null : query.getStoreId(), null);

        Map<String, SourceVO> map = new LinkedHashMap<>();
        for (TrackSummary s : rows) {
            SourceVO vo = map.computeIfAbsent(s.getDimValue(), k -> new SourceVO().setSource(k));
            vo.setPv(addLong(vo.getPv(), s.getPv()));
            vo.setUv(addLong(vo.getUv(), s.getUv()));
        }
        List<SourceVO> result = new ArrayList<>(map.values());
        result.sort((x, y) -> Long.compare(y.getPv(), x.getPv()));
        return result;
    }

    @Override
    public List<DeviceVO> device(AnalyticsQuery query) {
        LocalDateTime[] range = resolveRange(query);
        List<TrackSummary> rows = readSummary("device", "day", range[0], range[1],
                query == null ? null : query.getStoreId(), null);

        Map<String, DeviceVO> map = new LinkedHashMap<>();
        for (TrackSummary s : rows) {
            DeviceVO vo = map.computeIfAbsent(s.getDimValue(), AnalyticsQueryServiceImpl::parseDevice);
            vo.setPv(addLong(vo.getPv(), s.getPv()));
            vo.setUv(addLong(vo.getUv(), s.getUv()));
        }
        List<DeviceVO> result = new ArrayList<>(map.values());
        result.sort((x, y) -> Long.compare(y.getPv(), x.getPv()));
        return result;
    }

    @Override
    public List<PageVO> page(AnalyticsQuery query) {
        LocalDateTime[] range = resolveRange(query);
        List<TrackSummary> rows = readSummary("page", "day", range[0], range[1],
                query == null ? null : query.getStoreId(), null);

        Map<String, PageVO> result = new LinkedHashMap<>();
        Map<String, long[]> acc = new HashMap<>();
        for (TrackSummary s : rows) {
            String path = s.getDimValue();
            PageVO vo = result.computeIfAbsent(path, k -> new PageVO().setPagePath(k));
            vo.setPv(addLong(vo.getPv(), s.getPv()));
            vo.setUv(addLong(vo.getUv(), s.getUv()));
            long[] a = acc.computeIfAbsent(path, k -> new long[3]);
            a[0] += nvl(s.getTotalDurationMs());
            a[1] += nvl(s.getVisitCount());
            a[2] += nvl(s.getBounceCount());
        }
        for (Map.Entry<String, long[]> e : acc.entrySet()) {
            PageVO vo = result.get(e.getKey());
            vo.setAvgDurationMs(avgDuration(e.getValue()[0], e.getValue()[1]));
            vo.setBounceRate(percent(e.getValue()[2], e.getValue()[1]));
        }
        List<PageVO> list = new ArrayList<>(result.values());
        list.sort((x, y) -> Long.compare(y.getPv(), x.getPv()));
        return list;
    }

    // ==================================================================
    // 行为明细
    // ==================================================================

    @Override
    public IPage<TrackEvent> events(AnalyticsQuery query) {
        LocalDateTime[] range = resolveRange(query);
        List<String> scope = resolveBusinessScope(query == null ? null : query.getStoreId());

        QueryWrapper<TrackEvent> wrapper = new QueryWrapper<>();
        wrapper.ge("event_time", range[0]).lt("event_time", range[1]);
        if (query != null) {
            if (StringUtils.isNotBlank(query.getEventName())) {
                wrapper.eq("event_name", query.getEventName());
            }
            if (StringUtils.isNotBlank(query.getProductId())) {
                wrapper.eq("product_id", query.getProductId());
            }
            if (StringUtils.isNotBlank(query.getKeyword())) {
                String kw = query.getKeyword();
                wrapper.and(w -> w.like("visitor_id", kw)
                        .or().like("user_id", kw)
                        .or().like("session_id", kw)
                        .or().like("page_path", kw));
            }
        }
        orgScopeResolver.applyScope(wrapper, scope, "org_code");
        wrapper.orderByDesc("event_time");

        int pageNo = query == null || query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query == null || query.getPageSize() == null || query.getPageSize() < 1
                ? 10 : Math.min(query.getPageSize(), 200);
        return trackEventMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    // ==================================================================
    // 汇总读取与可见域
    // ==================================================================

    /** 汇总表可见域：allRows=true 看 org_code='ALL'；false 按部门前缀看 per-org 行 */
    private List<TrackSummary> readSummary(String dimType, String granularity,
                                           LocalDateTime start, LocalDateTime end,
                                           String storeId, String dimValuePrefix) {
        if (StringUtils.isNotBlank(storeId)) {
            String storeOrg = orgScopeResolver.resolveStoreOrgCode(storeId);
            List<String> visible = isGlobalView() ? null : orgScopeResolver.resolveVisibleOrgCodes();
            List<String> scope = orgScopeResolver.mergeScope(storeOrg, visible);
            return summaryMapper.selectByDim(dimType, granularity, start, end, scope, false, dimValuePrefix);
        }
        if (isGlobalView()) {
            return summaryMapper.selectByDim(dimType, granularity, start, end, null, true, dimValuePrefix);
        }
        return summaryMapper.selectByDim(dimType, granularity, start, end,
                orgScopeResolver.resolveVisibleOrgCodes(), false, dimValuePrefix);
    }

    /** 业务表（订单/加购/明细）可见域：null=全局，空=无交集 */
    private List<String> resolveBusinessScope(String storeId) {
        List<String> visible = isGlobalView() ? null : orgScopeResolver.resolveVisibleOrgCodes();
        if (StringUtils.isBlank(storeId)) {
            return visible;
        }
        String storeOrg = orgScopeResolver.resolveStoreOrgCode(storeId);
        return orgScopeResolver.mergeScope(storeOrg, visible);
    }

    /** 是否全局汇总视角：admin 角色或 shop-analytics:all 权限 */
    private boolean isGlobalView() {
        try {
            Subject subject = SecurityUtils.getSubject();
            return subject != null && (subject.hasRole("admin") || subject.isPermitted("shop-analytics:all"));
        } catch (Exception e) {
            return false;
        }
    }

    // ==================================================================
    // 区间解析
    // ==================================================================

    private LocalDateTime[] resolveRange(AnalyticsQuery query) {
        LocalDateTime end = null;
        LocalDateTime start = null;
        if (query != null) {
            if (StringUtils.isNotBlank(query.getEndTime())) {
                end = toLdt(QueryDateUtils.parseEnd(query.getEndTime(), "结束时间"));
            }
            if (StringUtils.isNotBlank(query.getStartTime())) {
                start = toLdt(QueryDateUtils.parseStart(query.getStartTime(), "开始时间"));
            }
        }
        if (end == null) {
            end = LocalDate.now().plusDays(1).atStartOfDay();
        }
        if (start == null) {
            start = end.minusDays(DEFAULT_DAYS);
        }
        return new LocalDateTime[]{start, end};
    }

    // ==================================================================
    // 查询条件
    // ==================================================================

    private QueryWrapper<ShopCartEvent> cartWrapper(LocalDateTime[] range, List<String> scope) {
        QueryWrapper<ShopCartEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("del_flag", 0).in("event_type", "ADD", "INCR");
        wrapper.ge("create_time", toDate(range[0])).lt("create_time", toDate(range[1]));
        orgScopeResolver.applyScope(wrapper, scope, "org_code");
        return wrapper;
    }

    private QueryWrapper<ShopOrder> orderCreateWrapper(LocalDateTime[] range, List<String> scope) {
        QueryWrapper<ShopOrder> wrapper = new QueryWrapper<>();
        wrapper.ge("create_time", toDate(range[0])).lt("create_time", toDate(range[1]));
        orgScopeResolver.applyScope(wrapper, scope, "store_org_code");
        return wrapper;
    }

    private QueryWrapper<ShopOrder> orderPaidWrapper(LocalDateTime[] range, List<String> scope) {
        QueryWrapper<ShopOrder> wrapper = new QueryWrapper<>();
        wrapper.ne("pay_status", "UNPAID")
                .isNotNull("pay_time")
                .ge("pay_time", toDate(range[0]))
                .lt("pay_time", toDate(range[1]));
        orgScopeResolver.applyScope(wrapper, scope, "store_org_code");
        return wrapper;
    }

    // ==================================================================
    // 解析 / 工具
    // ==================================================================

    private static RegionVO parseRegion(String dimValue) {
        String[] parts = dimValue.split("\\|", -1);
        RegionVO vo = new RegionVO();
        String head = parts.length > 0 ? parts[0] : "";
        String code = head.length() > 2 ? head.substring(2) : head;
        vo.setCountryCode(emptyToNull(code));
        vo.setCountryName(parts.length > 1 ? emptyToNull(parts[1]) : null);
        vo.setProvinceName(parts.length > 2 ? emptyToNull(parts[2]) : null);
        vo.setCityName(parts.length > 3 ? emptyToNull(parts[3]) : null);
        return vo;
    }

    private static DeviceVO parseDevice(String dimValue) {
        int idx = dimValue.indexOf(':');
        String kind = idx > 0 ? dimValue.substring(0, idx) : "";
        String value = idx > 0 ? dimValue.substring(idx + 1) : dimValue;
        DeviceVO vo = new DeviceVO();
        switch (kind) {
            case "device" -> vo.setDeviceType(emptyToNull(value));
            case "os" -> vo.setOs(emptyToNull(value));
            case "browser" -> vo.setBrowser(emptyToNull(value));
            case "lang" -> vo.setLang(emptyToNull(value));
            default -> vo.setDeviceType(emptyToNull(value));
        }
        return vo;
    }

    private static String emptyToNull(String s) {
        String t = StringUtils.trimToNull(s);
        return t == null || "unknown".equals(t) ? null : t;
    }

    /** 比率：百分数数值、不带 %、保留 2 位（如 "12.34"） */
    private String percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return "0.00";
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private long avgDuration(long totalMs, long visit) {
        return visit <= 0 ? 0L : totalMs / visit;
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private Long addLong(Long base, Long delta) {
        return nvl(base) + nvl(delta);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 10;
        }
        return Math.min(limit, 100);
    }

    private LocalDateTime toLdt(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
