package org.jeecg.modules.shop.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * 订单看板的跨表计数 Mapper。
 * </p>
 *
 * <h3>为什么单独一个 Mapper</h3>
 * 看板需要的几个数字分散在四张表上（加购行为、退款单、支付单、订单），
 * 而它们都是**纯计数**、没有实体映射需求。硬塞进各自的 Mapper 会让
 * "看板要什么"这件事散落在四个文件里；集中在这里，
 * 改看板时只需看这一个文件。
 *
 * <p>刻意不继承 BaseMapper：这里没有任何实体映射，
 * 让它只能执行下面这几个明确声明的查询，避免被误用成"万能表操作入口"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Mapper
public interface ShopOrderStatsMapper {

    /**
     * 加购用户数（按加购时间）。
     *
     * <p>用于计算"加购 → 下单"的漏斗转化率。加购行为取自
     * {@code ck_shop_cart_event} 而不是购物车当前快照：
     * 快照里的行会因为用户清空购物车而消失，用它算转化率会随用户操作而变。
     */
    Long countCartUsers(@Param("scope") List<String> scope,
                        @Param("storeIds") List<String> storeIds,
                        @Param("start") Date start,
                        @Param("end") Date end);

    /** 待审核退款单数 */
    Long countPendingRefunds(@Param("scope") List<String> scope,
                             @Param("storeIds") List<String> storeIds);

    /**
     * 支付中（结果未知）的支付单数。
     *
     * <p>这是"是否有人在掉单"的第一手信号：
     * 正常情况下这个数字应该长期接近 0，持续增长说明回调链路或渠道出问题了。
     */
    Long countPendingPayments(@Param("scope") List<String> scope,
                              @Param("storeIds") List<String> storeIds,
                              @Param("createdBefore") Date createdBefore);
}
