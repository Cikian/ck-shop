package org.jeecg.modules.shop.order.service;

import org.jeecg.modules.shop.order.calc.OrderAmountSnapshot;
import org.jeecg.modules.shop.order.entity.ShopOrderItem;

import java.util.List;

/**
 * <p>
 * 库存服务 —— 预占 / 释放 / 实扣 / 回补，以及每一步的库存流水。
 * </p>
 *
 * <h3>为什么四种操作必须分开且都要记流水</h3>
 * 库存对不上的时候，能救命的唯一信息是"某一次变动到底动了哪一列"。
 * 如果只写"库存 +3"，就无法区分那是"预占"（stock 不动、frozen 动）
 * 还是"退款回补"（stock 动）。两者的排查方向完全相反：
 * <ul>
 *   <li>预占没释放 → 订单卡在待支付，看订单表就能找到；</li>
 *   <li>实扣重复 → 有订单被扣了两次，看流水才能发现同一订单号有两条 DEDUCT。</li>
 * </ul>
 *
 * <h3>失败语义</h3>
 * 所有方法在"原子条件不满足"时**抛异常**并让事务回滚，
 * 而不是返回 false 让调用方自己判断——漏判一个 false 就是超卖。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public interface IShopStockService {

    /**
     * 下单预占。
     *
     * <p>逐行调用带条件的原子 UPDATE，影响 0 行即抛异常并回滚整单。
     * 因为是在同一个事务里，前面几行已成功的预占会随事务一起回滚，
     * 不会出现"部分商品占了、部分没占"的中间状态。
     *
     * @throws org.jeecg.common.exception.JeecgBootException 任一明细库存不足/已下架
     */
    void occupy(OrderContext context, OrderAmountSnapshot snapshot, String orderNo);

    /**
     * 释放预占（取消订单 / 超时关单）。
     *
     * <p><b>幂等</b>：预占已经释放过时影响 0 行，视为成功而**不报错**。
     * 重复取消、定时任务与用户操作撞车都会走到这里，
     * 报错只会制造无意义的告警噪音。
     */
    void release(String orderNo, List<ShopOrderItem> items, String operator, String reason);

    /**
     * 支付实扣：{@code stock -= qty}，同时释放等量预占。
     *
     * <p>影响 0 行说明预占已被别处释放（例如定时任务误判超时）——
     * 这是**必须告警**的异常：钱已经收了，货却还在可售池里，等于超卖。
     * 因此这里记 ERROR 日志而不是静默。
     */
    void deduct(String orderNo, List<ShopOrderItem> items, String bizId, String operator);

    /** 退款回补实际库存 */
    void restore(String orderNo, List<ShopOrderItem> items, String bizId, String operator);

    /**
     * 占用秒杀限量。
     *
     * @return true = 占用成功；false = 已抢光（调用方据此拒绝下单）
     */
    boolean occupySeckill(String promotionId, int quantity, String orderNo, String operator);

    /** 归还秒杀限量（取消/关单时） */
    void releaseSeckill(String promotionId, int quantity, String orderNo, String operator);
}
