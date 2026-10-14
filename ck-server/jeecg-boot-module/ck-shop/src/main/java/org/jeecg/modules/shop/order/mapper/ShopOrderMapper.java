package org.jeecg.modules.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsOverviewVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsStatusVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsTrendVO;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * 订单 Mapper。
 * </p>
 *
 * <h3>状态变更为什么不用 MyBatis-Plus 的 updateById</h3>
 * 订单的每一次状态推进都必须是**带前置条件的条件更新**（CAS），
 * 而 {@code updateById} 只会按主键覆盖，等于把"当前是什么状态"这件事交给了
 * 调用方在内存里的判断——两个并发请求都会认为自己是对的。
 * 因此这里把两个真正需要原子性的操作（归档、累加退款额）写成裸 SQL，
 * 其余状态流转用 Service 层的 {@code update(entity, wrapper)} 带
 * {@code order_status + version} 双条件完成。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Mapper
public interface ShopOrderMapper extends BaseMapper<ShopOrder> {

    /**
     * 归档订单。
     *
     * <p><b>只允许已取消 / 已关闭的订单归档</b>，条件写在 SQL 里而不是靠调用方判断：
     * 归档是不可逆的操作，"发了货的单被归档"意味着客服再也搜不到它。
     * 把限制压进 WHERE 子句，任何调用路径（包括未来新增的批量归档接口）都绕不过去。
     *
     * @return 受影响行数，0 表示状态不允许归档或订单不存在
     */
    int archiveById(@Param("id") String id,
                    @Param("operator") String operator);

    /**
     * 原子累加订单的已退款金额。
     *
     * <p>条件 {@code refund_amount + delta &lt;= paid_amount} 是"累计退款不得超过实收"
     * 的最后一道锁。放在这里而不是 Service 里做"查出来判断再加"，
     * 是因为多个客服并发退款时，查-判断-写之间必然存在窗口。
     *
     * @return 受影响行数；0 表示超出可退额度（或订单未支付），调用方必须拒绝本次退款
     */
    int accumulateRefundAmount(@Param("id") String id,
                               @Param("delta") BigDecimal delta,
                               @Param("operator") String operator);

    // ==================================================================
    // 统计（后台看板）
    // ==================================================================

    /**
     * 订单总量与金额总览。
     *
     * <p>一次查询把全部指标算出来，而不是前端发十几个请求分别统计——
     * 多个请求之间数据会漂移，看板上的"总数"与"分状态之和"对不上，
     * 运营会怀疑数据有问题。
     *
     * @param scope    部门编码前缀列表；null = 不限
     * @param storeIds 店铺ID列表；null = 不限（与 scope 是"与"关系）
     */
    OrderStatsOverviewVO statsOverview(@Param("scope") List<String> scope,
                                       @Param("storeIds") List<String> storeIds,
                                       @Param("start") Date start,
                                       @Param("end") Date end);

    /** 按订单状态分布 */
    List<OrderStatsStatusVO> statsByStatus(@Param("scope") List<String> scope,
                                           @Param("storeIds") List<String> storeIds,
                                           @Param("start") Date start,
                                           @Param("end") Date end);

    /** 趋势：按下单时间统计下单量 */
    List<OrderStatsTrendVO> statsOrderTrend(@Param("scope") List<String> scope,
                                            @Param("storeIds") List<String> storeIds,
                                            @Param("start") Date start,
                                            @Param("end") Date end);

    /**
     * 趋势：按**支付时间**统计 GMV 与实收。
     *
     * <p>与下单趋势分开统计是刻意的：经营看板关心"钱哪天进来的"，
     * 用下单时间会把"月底下单、月初付款"算进上月 GMV，与财务口径对不上，
     * 也会让退款（按退款时间）与收入落在不同的月份里。
     */
    List<OrderStatsTrendVO> statsPayTrend(@Param("scope") List<String> scope,
                                          @Param("storeIds") List<String> storeIds,
                                          @Param("start") Date start,
                                          @Param("end") Date end);

    /** 趋势：按退款到账时间统计退款额 */
    List<OrderStatsTrendVO> statsRefundTrend(@Param("scope") List<String> scope,
                                             @Param("storeIds") List<String> storeIds,
                                             @Param("start") Date start,
                                             @Param("end") Date end);

    /**
     * 风险指标：**已关闭但收到过钱**的订单数。
     *
     * <p>这是文档里"超时关单后支付成功，钱收了订单关闭"的直接度量，
     * 正常应恒为 0。它不体现在任何常规经营指标里，
     * 如果不单独统计，这类事故会一直静默存在到用户投诉。
     */
    Long countClosedButPaid(@Param("scope") List<String> scope,
                            @Param("storeIds") List<String> storeIds);

    /** 预计需要人工处理的订单：待支付且已过支付截止时间（定时任务尚未跑到） */
    Long countOverdueWaitPay(@Param("scope") List<String> scope,
                             @Param("storeIds") List<String> storeIds,
                             @Param("now") Date now);
}
