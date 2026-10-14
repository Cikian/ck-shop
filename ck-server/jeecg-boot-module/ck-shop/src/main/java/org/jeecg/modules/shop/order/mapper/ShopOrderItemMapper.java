package org.jeecg.modules.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.order.entity.ShopOrderItem;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsRankVO;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * 订单明细 Mapper。
 * </p>
 *
 * 统计一律基于**明细快照**而不是 join 商品表：
 * 商品改名/改价/删除之后，上个月的榜单必须仍然显示当时的名字与金额，
 * 否则"历史报表每次跑都不一样"，没人敢拿它做决策。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Mapper
public interface ShopOrderItemMapper extends BaseMapper<ShopOrderItem> {

    /**
     * 热销商品/SKU 排行。
     *
     * @param scope     部门编码前缀；null = 不限
     * @param storeIds  店铺ID；null = 不限
     * @param start     订单支付时间起（含）
     * @param end       订单支付时间止（不含）
     * @param limit     取前 N 条
     */
    List<OrderStatsRankVO> statsRankByProduct(@Param("scope") List<String> scope,
                                              @Param("storeIds") List<String> storeIds,
                                              @Param("start") Date start,
                                              @Param("end") Date end,
                                              @Param("limit") Integer limit);

    /** 热销分类排行（按类目快照聚合） */
    List<OrderStatsRankVO> statsRankByCategory(@Param("scope") List<String> scope,
                                               @Param("storeIds") List<String> storeIds,
                                               @Param("start") Date start,
                                               @Param("end") Date end,
                                               @Param("limit") Integer limit);

    /** 按订单ID批量取明细（列表页一次取全，杜绝 N+1 查询） */
    List<ShopOrderItem> selectByOrderIds(@Param("orderIds") List<String> orderIds);
}
