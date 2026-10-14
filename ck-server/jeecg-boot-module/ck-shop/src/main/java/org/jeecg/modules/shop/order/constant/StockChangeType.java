package org.jeecg.modules.shop.order.constant;

/**
 * <p>
 * 库存变动类型（{@code ck_shop_stock_log.change_type}）。
 * </p>
 *
 * 一次完整的下单→支付→退款链路会依次写：
 * <pre>
 *   下单      OCCUPY   frozen +qty                （stock 不变）
 *   支付成功  DEDUCT   stock -qty, frozen -qty
 *   取消/关单 RELEASE  frozen -qty                （stock 不变）
 *   退款回补  RESTORE  stock +qty
 * </pre>
 * 把这四种分开记录，才能在"库存对不上"时立刻定位是预占没释放、
 * 还是实扣重复扣了、还是退款多补了。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public final class StockChangeType {

    private StockChangeType() {
    }

    /** 下单预占：frozen_stock + quantity */
    public static final String OCCUPY = "OCCUPY";
    /** 释放预占：frozen_stock - quantity（取消/超时关单） */
    public static final String RELEASE = "RELEASE";
    /** 支付实扣：stock - quantity，同时释放等量预占 */
    public static final String DEDUCT = "DEDUCT";
    /** 回补实际库存：stock + quantity（退款成功） */
    public static final String RESTORE = "RESTORE";
    /** 人工调整（运营在后台改库存） */
    public static final String ADJUST = "ADJUST";
}
