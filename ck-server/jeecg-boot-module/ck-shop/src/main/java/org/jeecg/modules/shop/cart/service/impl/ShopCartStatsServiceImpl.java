package org.jeecg.modules.shop.cart.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.cart.entity.dto.CartStatsQuery;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsEventVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsOverviewVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsRankVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsTrendVO;
import org.jeecg.modules.shop.cart.mapper.ShopCartEventMapper;
import org.jeecg.modules.shop.cart.mapper.ShopCartItemMapper;
import org.jeecg.modules.shop.cart.service.IShopCartStatsService;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.mapper.ShopStoreMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 后台加购统计实现
 * </p>
 *
 * 三个刻意的设计：
 * <ol>
 *   <li><b>可见域用"店铺ID精确集合"表达</b>，而不是部门编码前缀 LIKE。
 *       购物车本来就按 store_id 隔离，精确集合既能命中索引，也不会出现
 *       {@code LIKE 'A06%'} 误把 {@code A061} 的数据算进来的跨店事故。</li>
 *   <li><b>"不限部门"的账号不加店铺过滤</b>（storeIds = null），
 *       这样已停用/已删除店铺的历史加购记录仍然计入——否则统计口径会随时间莫名其妙"缩水"。</li>
 *   <li><b>趋势补齐缺口日期</b>：SQL 的 GROUP BY 不会返回没有数据的日期，
 *       直接丢给前端画折线会出现"日期跳档"，看起来像数据丢了。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Slf4j
@Service
public class ShopCartStatsServiceImpl implements IShopCartStatsService {

    /** 默认统计最近 30 天 */
    private static final int DEFAULT_DAYS = 30;
    /** 统计区间上限 90 天：再长就不是"运营看趋势"而是"跑报表"了，交给专业报表工具 */
    private static final int MAX_DAYS = 90;
    /** 排行榜默认 10 条 */
    private static final int DEFAULT_LIMIT = 10;
    /** 排行榜上限 100 条 */
    private static final int MAX_LIMIT = 100;
    /** 明细分页默认 20 条 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 翻译兜底语言 */
    private static final String DEFAULT_LANG = "zh";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ShopCartEventMapper cartEventMapper;

    @Autowired
    private ShopCartItemMapper cartItemMapper;

    @Autowired
    private ShopStoreMapper shopStoreMapper;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    // ==================================================================
    // 对外接口
    // ==================================================================

    @Override
    public CartStatsOverviewVO overview(CartStatsQuery query, String language) {
        TimeRange range = resolveRange(query);
        List<String> storeIds = resolveStoreScope(query);

        CartStatsOverviewVO vo = cartEventMapper.selectAddOverview(storeIds, range.start, range.end);
        if (vo == null) {
            vo = new CartStatsOverviewVO();
        }
        vo.setAddTimes(nvl(vo.getAddTimes()));
        vo.setAddQuantity(nvl(vo.getAddQuantity()));
        vo.setAddUsers(nvl(vo.getAddUsers()));
        vo.setAddProducts(nvl(vo.getAddProducts()));
        vo.setAddSkus(nvl(vo.getAddSkus()));
        vo.setQuantityPerUser(vo.getAddUsers() > 0
                ? BigDecimal.valueOf(vo.getAddQuantity())
                        .divide(BigDecimal.valueOf(vo.getAddUsers()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        // 当前购物车快照：不受统计区间影响，只受店铺可见域影响
        vo.setCartItems(nvl(cartItemMapper.countItems(storeIds)));
        vo.setCartQuantity(nvl(cartItemMapper.sumQuantity(storeIds)));
        vo.setCartUsers(nvl(cartItemMapper.countUsers(storeIds)));

        vo.setRangeStart(DATETIME_FMT.format(range.start.toInstant().atZone(ZoneId.systemDefault())));
        vo.setRangeEnd(DATETIME_FMT.format(range.end.toInstant().atZone(ZoneId.systemDefault())));
        return vo;
    }

    @Override
    public List<CartStatsRankVO> rank(CartStatsQuery query, String language) {
        TimeRange range = resolveRange(query);
        List<String> storeIds = resolveStoreScope(query);
        int limit = clamp(query == null || query.getLimit() == null ? DEFAULT_LIMIT : query.getLimit(),
                1, MAX_LIMIT);
        String lang = normalizeLanguage(language);
        String keyword = query == null ? null : StringUtils.trimToNull(query.getKeyword());

        List<CartStatsRankVO> list = cartEventMapper.selectRank(
                storeIds, range.start, range.end, limit, keyword, lang, DEFAULT_LANG);
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        // 名次与"相对热度"由服务端算好：前端拿来就能画进度条，不用自己找最大值
        long top = list.get(0).getAddTimes() == null ? 0L : list.get(0).getAddTimes();
        for (int i = 0; i < list.size(); i++) {
            CartStatsRankVO item = list.get(i);
            item.setRank(i + 1);
            item.setAddTimes(nvl(item.getAddTimes()));
            item.setAddQuantity(nvl(item.getAddQuantity()));
            item.setAddUsers(nvl(item.getAddUsers()));
            item.setSkuId(StringUtils.trimToEmpty(item.getSkuId()));
            item.setHeat(top > 0
                    ? BigDecimal.valueOf(item.getAddTimes())
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(top), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        }
        return list;
    }

    @Override
    public List<CartStatsTrendVO> trend(CartStatsQuery query, String language) {
        TimeRange range = resolveRange(query);
        List<String> storeIds = resolveStoreScope(query);

        List<CartStatsTrendVO> rows = cartEventMapper.selectDailyTrend(storeIds, range.start, range.end);
        Map<String, CartStatsTrendVO> byDate = new LinkedHashMap<>();
        if (rows != null) {
            for (CartStatsTrendVO row : rows) {
                if (row.getStatDate() == null) {
                    continue;
                }
                row.setAddTimes(nvl(row.getAddTimes()));
                row.setAddQuantity(nvl(row.getAddQuantity()));
                row.setAddUsers(nvl(row.getAddUsers()));
                row.setAddProducts(nvl(row.getAddProducts()));
                byDate.put(row.getStatDate(), row);
            }
        }

        // 补齐区间内每一天：缺的日期补 0，保证前端折线的 X 轴连续
        List<CartStatsTrendVO> result = new ArrayList<>();
        LocalDate cursor = toLocalDate(range.start);
        LocalDate last = toLocalDate(range.end);
        while (!cursor.isAfter(last)) {
            String key = DATE_FMT.format(cursor);
            CartStatsTrendVO hit = byDate.get(key);
            if (hit != null) {
                result.add(hit);
            } else {
                CartStatsTrendVO empty = new CartStatsTrendVO();
                empty.setStatDate(key);
                empty.setAddTimes(0L);
                empty.setAddQuantity(0L);
                empty.setAddUsers(0L);
                empty.setAddProducts(0L);
                result.add(empty);
            }
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    @Override
    public IPage<CartStatsEventVO> eventPage(CartStatsQuery query, String language) {
        TimeRange range = resolveRange(query);
        List<String> storeIds = resolveStoreScope(query);
        int pageNo = query == null || query.getPageNo() == null ? 1 : Math.max(1, query.getPageNo());
        int pageSize = query == null || query.getPageSize() == null
                ? DEFAULT_PAGE_SIZE : clamp(query.getPageSize(), 1, 200);
        String lang = normalizeLanguage(language);
        String keyword = query == null ? null : StringUtils.trimToNull(query.getKeyword());

        Page<CartStatsEventVO> page = new Page<>(pageNo, pageSize);
        IPage<CartStatsEventVO> result = cartEventMapper.selectEventPage(
                page, storeIds, range.start, range.end, keyword, lang, DEFAULT_LANG);
        if (result == null) {
            result = new Page<>(pageNo, pageSize, 0);
        }
        if (result.getRecords() != null) {
            result.getRecords().forEach(item -> item.setSkuId(StringUtils.trimToEmpty(item.getSkuId())));
        }
        return result;
    }

    @Override
    public List<ShopStore> storeOptions() {
        List<ShopStore> stores = shopStoreMapper.selectEnabledWithDepart();
        if (stores == null) {
            return new ArrayList<>();
        }
        List<String> visibleOrgCodes = orgScopeResolver.resolveVisibleOrgCodes();
        if (visibleOrgCodes == null) {
            return stores;
        }
        List<ShopStore> filtered = new ArrayList<>();
        for (ShopStore store : stores) {
            if (orgScopeResolver.inScope(store.getDepartOrgCode(), visibleOrgCodes)) {
                filtered.add(store);
            }
        }
        return filtered;
    }

    // ==================================================================
    // 可见域 & 时间区间
    // ==================================================================

    /**
     * 求出本次统计允许访问的店铺集合。
     *
     * @return {@code null} 表示不限（总部视角，连已停用店铺的历史也计入）；
     *         空列表表示没有任何可见店铺，查询会落成 {@code 1 = 0}
     * @throws JeecgBootException 指定了无权查看的店铺
     */
    private List<String> resolveStoreScope(CartStatsQuery query) {
        String requested = query == null ? null : StringUtils.trimToNull(query.getStoreId());
        List<String> visibleOrgCodes = orgScopeResolver.resolveVisibleOrgCodes();

        if (requested != null) {
            ShopStore store = shopStoreMapper.selectById(requested);
            if (store == null) {
                throw new JeecgBootException("店铺不存在或已删除");
            }
            if (visibleOrgCodes != null) {
                String orgCode = StringUtils.isBlank(store.getDepartId())
                        ? null : shopStoreMapper.selectDepartOrgCodeById(store.getDepartId());
                if (!orgScopeResolver.inScope(orgCode, visibleOrgCodes)) {
                    // 明确拒绝，而不是"过滤后返回空"：返回空会让人以为这家店真的没有数据
                    throw new JeecgBootException("无权查看该店铺的统计数据");
                }
            }
            return Collections.singletonList(requested);
        }

        if (visibleOrgCodes == null) {
            return null;
        }

        List<ShopStore> stores = shopStoreMapper.selectEnabledWithDepart();
        List<String> ids = new ArrayList<>();
        if (stores != null) {
            for (ShopStore store : stores) {
                if (orgScopeResolver.inScope(store.getDepartOrgCode(), visibleOrgCodes)) {
                    ids.add(store.getId());
                }
            }
        }
        return ids;
    }

    /** 时间区间：优先用显式日期，否则按 days 取"最近 N 天（含今天）" */
    private TimeRange resolveRange(CartStatsQuery query) {
        String startText = query == null ? null : StringUtils.trimToNull(query.getStartDate());
        String endText = query == null ? null : StringUtils.trimToNull(query.getEndDate());

        LocalDate start;
        LocalDate end;
        if (startText != null || endText != null) {
            end = endText == null ? LocalDate.now() : parseDate(endText, "结束日期");
            start = startText == null ? end.minusDays(DEFAULT_DAYS - 1L) : parseDate(startText, "开始日期");
            if (start.isAfter(end)) {
                throw new JeecgBootException("开始日期不能晚于结束日期");
            }
            if (start.plusDays(MAX_DAYS).isBefore(end)) {
                throw new JeecgBootException("统计区间最长 " + MAX_DAYS + " 天，请缩小范围");
            }
        } else {
            int days = clamp(query == null || query.getDays() == null ? DEFAULT_DAYS : query.getDays(),
                    1, MAX_DAYS);
            end = LocalDate.now();
            start = end.minusDays(days - 1L);
        }
        return new TimeRange(toStartOfDay(start), toEndOfDay(end));
    }

    private LocalDate parseDate(String text, String label) {
        try {
            return LocalDate.parse(text, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new JeecgBootException(label + "格式不正确，应为 yyyy-MM-dd");
        }
    }

    private Date toStartOfDay(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Date toEndOfDay(LocalDate date) {
        return Date.from(date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private String normalizeLanguage(String language) {
        if (StringUtils.isBlank(language)) {
            return DEFAULT_LANG;
        }
        String lang = language.trim().toLowerCase();
        int dash = lang.indexOf('-');
        if (dash > 0) {
            lang = lang.substring(0, dash);
        }
        return StringUtils.isBlank(lang) ? DEFAULT_LANG : lang;
    }

    /** 统计区间（闭区间，精确到秒） */
    private static class TimeRange {
        private final Date start;
        private final Date end;

        private TimeRange(Date start, Date end) {
            this.start = start;
            this.end = end;
        }
    }
}
