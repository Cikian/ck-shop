package org.jeecg.modules.shop.order.service;

import org.jeecg.modules.shop.order.entity.dto.OrderQuery;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsOverviewVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsRankVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsStatusVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsTrendVO;

import java.util.List;

/**
 * <p>
 * 订单统计 Service。
 * </p>
 *
 * <h3>三条贯穿全部指标的口径纪律</h3>
 * <ol>
 *   <li><b>不过滤归档标记</b>：归档只是后台列表的可见性开关，
 *       不是数据删除。统计把它排除会让历史报表随归档动作而变。</li>
 *   <li><b>下单量按下单时间、GMV/实收按支付时间、退款额按退款到账时间</b>。
 *       三种时间各归各的日，不混用。混用会出现"月底下单、月初付款"
 *       被算进上月 GMV，而退款落在下月，净收入永远对不上财务。</li>
 *   <li><b>数据可见域一律取交集</b>：账号可见域 ∩ 店铺筛选域，
 *       没有交集时返回空而不是全部（fail-closed）。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public interface IShopOrderStatsService {

    /** 总览（当前统计区间） */
    OrderStatsOverviewVO overview(OrderQuery query);

    /** 按订单状态分布（饼图） */
    List<OrderStatsStatusVO> statusDistribution(OrderQuery query);

    /** 按日趋势（下单量 / GMV / 实收 / 退款 / 净收入） */
    List<OrderStatsTrendVO> trend(OrderQuery query);

    /** 热销商品/SKU 排行 */
    List<OrderStatsRankVO> rankByProduct(OrderQuery query, Integer limit);

    /** 热销分类排行 */
    List<OrderStatsRankVO> rankByCategory(OrderQuery query, Integer limit);

    /**
     * 把前端传来的时间区间解析并规范化（缺省时套用"最近 N 天"）。
     *
     * <p>返回 {@code OrderTimeRange} 而不是直接改 DTO：DTO 里是 String
     * （Spring 不给 Date 注册 String→Date 格式化器，见 {@code QueryDateUtils}），
     * 解析结果与"结束时间按精度包含"的语义都收口在这个返回对象里，
     * 避免每个统计方法各写一遍导致口径漂移。
     */
    org.jeecg.modules.shop.order.util.OrderTimeRange resolveRange(OrderQuery query);
}
