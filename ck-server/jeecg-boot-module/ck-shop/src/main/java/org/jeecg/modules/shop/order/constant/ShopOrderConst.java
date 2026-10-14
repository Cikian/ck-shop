package org.jeecg.modules.shop.order.constant;

/**
 * <p>
 * 订单模块的公共常量与业务上限。
 * </p>
 *
 * @author Cikian
 * @since 2026-10-14
 */
public final class ShopOrderConst {

    private ShopOrderConst() {
    }

    /** 下单来源：PC 网页 */
    public static final String SOURCE_WEB = "web";
    /** 下单来源：移动端 H5 */
    public static final String SOURCE_H5 = "h5";
    /** 下单来源：App */
    public static final String SOURCE_APP = "app";
    /** 下单来源：服务端/其它 */
    public static final String SOURCE_API = "api";

    /** 单笔订单最多商品种类数（防刷：有人会把 5000 个 SKU 塞进一单打爆库存与运费计算） */
    public static final int MAX_ITEM_TYPES = 100;
    /** 单个 SKU 单笔最大购买数量 */
    public static final int MAX_QUANTITY_PER_ITEM = 999;
    /** 订单号最大长度（与 DDL varchar(32) 对齐） */
    public static final int ORDER_NO_MAX_LENGTH = 32;

    /** 默认支付超时时间（分钟）：未支付订单在此时长后被自动关闭 */
    public static final int DEFAULT_PAY_TIMEOUT_MINUTES = 30;
    /** 支付超时时间的上下限（分钟）：防止配置成 0 或 100 年导致关单任务失效或订单永不过期 */
    public static final int MIN_PAY_TIMEOUT_MINUTES = 1;
    public static final int MAX_PAY_TIMEOUT_MINUTES = 24 * 60;

    /** 结算价格令牌有效期（秒）：防止用户拿着很久以前的预览价格下单 */
    public static final int PRICE_TOKEN_TTL_SECONDS = 15 * 60;

    /** 包邮门槛判定用：金额为 0 的运费 */
    public static final java.math.BigDecimal ZERO_AMOUNT = java.math.BigDecimal.ZERO.setScale(2);
}
