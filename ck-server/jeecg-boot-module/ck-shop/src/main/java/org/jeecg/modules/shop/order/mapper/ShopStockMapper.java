package org.jeecg.modules.shop.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 库存原子操作 Mapper —— 防超卖的全部实现都在这里。
 * </p>
 *
 * <h3>为什么所有库存变动都必须走这里</h3>
 * 库存与预占必须**在同一条 UPDATE 里**完成判断与修改，否则就有竞态：
 * <pre>
 *   错误写法：                                   正确写法：
 *   stock = select stock where id = 1;           UPDATE ... SET frozen_stock = frozen_stock + 3
 *   if (stock &gt;= 3) {                               WHERE id = 1 AND stock - frozen_stock &gt;= 3
 *       update set frozen_stock = frozen_stock+3;  -- 影响 0 行就是没抢到，天然抗并发
 *   }
 *   ↑ 两个请求都读到 5，都判定"够"，都加 3 → 超卖
 * </pre>
 *
 * <h3>模型</h3>
 * <pre>
 *   下单      OCCUPY   frozen_stock += qty        （stock 不动）
 *   支付成功  DEDUCT   stock -= qty, frozen -= qty （预占转实扣）
 *   取消/关单 RELEASE  frozen_stock -= qty        （stock 不动）
 *   退款成功  RESTORE  stock += qty               （货回到可售）
 * </pre>
 * 其中 {@code stock} 是**实际库存**，{@code frozen_stock} 是**已下单未付款的预占**，
 * 真正"还能卖多少" = {@code stock - frozen_stock}。
 * 这个差值就是下面所有 WHERE 条件的判据。
 *
 * <h3>不继承 BaseMapper</h3>
 * 这里只提供上面那几个明确的原子操作，不给"随手 update 商品表"的入口——
 * 绕过本接口改库存，会让 {@code ck_shop_stock_log} 与实际值对不上，
 * 库存流水就失去了"能查清"的价值。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Mapper
public interface ShopStockMapper {

    // ==================================================================
    // 无规格商品（ck_shop_product）
    // ==================================================================

    /**
     * 预占：{@code frozen_stock += qty}。
     *
     * @return 1 = 预占成功；0 = 可售不足（{@code stock - frozen_stock < qty}）或商品已下架/已删除
     */
    int occupyProductStock(@Param("productId") String productId, @Param("quantity") Integer quantity);

    /**
     * 释放预占：{@code frozen_stock -= qty}。
     *
     * <p>带 {@code frozen_stock >= qty} 条件，防止把预占减成负数。
     * 影响 0 行说明预占已经被释放过（重复关单/重复取消），
     * 调用方应视为"已释放"而不是报错——重复取消必须幂等。
     */
    int releaseProductStock(@Param("productId") String productId, @Param("quantity") Integer quantity);

    /** 支付实扣：{@code stock -= qty, frozen_stock -= qty} */
    int deductProductStock(@Param("productId") String productId, @Param("quantity") Integer quantity);

    /** 回补实际库存（退款成功）：{@code stock += qty} */
    int restoreProductStock(@Param("productId") String productId, @Param("quantity") Integer quantity);

    /**
     * 按 SKU 实际库存重算 SPU 的汇总库存。
     *
     * <p><b>为什么必须有这一步</b>：{@code ck_shop_product.stock} 是
     * "该 SPU 所有 SKU 库存之和"的**汇总字段**（商品列表页显示的就是它）。
     * 多规格商品下单时若只减 SKU 行、不重算汇总，商品列表页会永远显示下单前的库存——
     * 顾客看到"有货"，点进去选规格才发现没货。这类"列表与详情不一致"的问题
     * 用户会当成系统故障，而且因为列表有缓存/分页，排查时极易被忽略。
     *
     * <p>这里用 SUM 重算而不是 {@code stock = stock - qty}：
     * 如果中间有运营手工改过某个 SKU 的库存，增量式的加减会把误差永久留在汇总上，
     * 重算则每次都自动对齐。代价是每次下单多一次子查询，但下单不是高频操作。
     */
    int syncProductStockFromSku(@Param("productId") String productId);

    /**
     * 累加 SPU 累计销量。
     *
     * <p>SKU 表没有 {@code sales_count} 列（销量是 SPU 级指标），
     * 因此多规格商品下单时必须显式累加 SPU 销量，否则"累计销量/近 30 天销量"
     * 对多规格商品永远是 0，商品列表按销量排序也就永远把它们排在最后。
     *
     * <p><b>退款不回滚销量</b>：销量是"卖出去过"的累计指标，
     * 退货率是另一个指标。回滚会让"近 30 天销量"随退款波动，报表一天一个样。
     */
    int increaseProductSales(@Param("productId") String productId, @Param("quantity") Integer quantity);

    // ==================================================================
    // 有规格商品（ck_shop_product_sku_combination）
    // ==================================================================

    /** 预占 SKU 库存；附加 {@code saleable = 'Y'} 条件，停售规格不可下单 */
    int occupySkuStock(@Param("skuId") String skuId, @Param("quantity") Integer quantity);

    int releaseSkuStock(@Param("skuId") String skuId, @Param("quantity") Integer quantity);

    int deductSkuStock(@Param("skuId") String skuId, @Param("quantity") Integer quantity);

    int restoreSkuStock(@Param("skuId") String skuId, @Param("quantity") Integer quantity);

    // ==================================================================
    // 秒杀限量（ck_shop_promotion.seckill_stock）
    // ==================================================================

    /**
     * 占用秒杀限量。
     *
     * <p>只在 {@code seckill_stock IS NOT NULL} 时才占用：
     * 该列为 NULL 表示"不限量"，此时不做减法。
     * 调用方需先读出促销判断是否限量，再决定要不要调用本方法。
     *
     * @return 1 = 占用成功；0 = 已抢光（或该活动不限量，无需占用）
     */
    int occupyPromotionStock(@Param("promotionId") String promotionId, @Param("quantity") Integer quantity);

    /** 归还秒杀限量（取消/关单时） */
    int releasePromotionStock(@Param("promotionId") String promotionId, @Param("quantity") Integer quantity);
}
