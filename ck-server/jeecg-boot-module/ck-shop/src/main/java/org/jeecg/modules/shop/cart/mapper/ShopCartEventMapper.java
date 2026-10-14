package org.jeecg.modules.shop.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.cart.entity.ShopCartEvent;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsEventVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsOverviewVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsRankVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsTrendVO;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * 加购行为流水 Mapper（后台统计专用）
 * </p>
 *
 * 全部方法的 {@code storeIds} 语义统一：
 * <ul>
 *   <li>{@code null} —— 不限店铺（总览/总部视角）；</li>
 *   <li>空列表 —— 没有任何可见店铺，直接返回空结果（SQL 里落成 {@code 1 = 0}）；</li>
 *   <li>非空 —— 限定在这些店铺内。</li>
 * </ul>
 * 用"店铺ID精确集合"而不是"部门编码前缀"来做可见域，是因为购物车本来就按 store_id 隔离，
 * 精确集合既能走索引，也不存在 {@code LIKE 'A06%'} 误命中 {@code A061} 这类跨店风险。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Mapper
public interface ShopCartEventMapper extends BaseMapper<ShopCartEvent> {

    /**
     * 加购汇总（只统计 ADD / INCR 两类事件）。
     * 返回对象里只有加购类字段有值，当前购物车快照由 Service 另外查。
     */
    CartStatsOverviewVO selectAddOverview(@Param("storeIds") List<String> storeIds,
                                          @Param("start") Date start,
                                          @Param("end") Date end);

    /**
     * 按日趋势。没有数据的日期不会被返回，由 Service 补齐成连续日期。
     */
    List<CartStatsTrendVO> selectDailyTrend(@Param("storeIds") List<String> storeIds,
                                            @Param("start") Date start,
                                            @Param("end") Date end);

    /**
     * 商品/SKU 热度排行。
     *
     * @param limit 取前 N 条（调用方已做上限收敛）
     * @param keyword 商品标题/SPU 模糊搜索，可为空
     */
    List<CartStatsRankVO> selectRank(@Param("storeIds") List<String> storeIds,
                                     @Param("start") Date start,
                                     @Param("end") Date end,
                                     @Param("limit") int limit,
                                     @Param("keyword") String keyword,
                                     @Param("lang") String lang,
                                     @Param("defaultLang") String defaultLang);

    /**
     * 加购明细分页（含全部事件类型，便于排查）。
     */
    IPage<CartStatsEventVO> selectEventPage(Page<CartStatsEventVO> page,
                                            @Param("storeIds") List<String> storeIds,
                                            @Param("start") Date start,
                                            @Param("end") Date end,
                                            @Param("keyword") String keyword,
                                            @Param("lang") String lang,
                                            @Param("defaultLang") String defaultLang);
}
