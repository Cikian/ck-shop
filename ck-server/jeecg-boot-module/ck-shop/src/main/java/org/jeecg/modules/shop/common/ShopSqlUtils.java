package org.jeecg.modules.shop.common;

/**
 * 店铺模块的 SQL 字符串工具。
 *
 * <p>为什么要有这个类：LIKE 关键字转义原本在**三处**各写了一份完全相同的实现
 * （{@code ShopProductServiceImpl}、{@code ShopPromotionServiceImpl}、{@code ShopPromotionController}），
 * 转义字符一旦有一处漏写，就会出现"模糊查询命中范围与预期不一致"这类很难查的问题。
 * 现在收敛到这一处。
 *
 * <p>注意：转义字符是反斜杠，调用方的 SQL 需要相应处理（MySQL 默认 {@code \} 为转义符；
 * 其他数据库方言下若转义符不同，应在这里集中适配，而不是各自修改调用点）。
 */
public final class ShopSqlUtils {

    private ShopSqlUtils() {
    }

    /**
     * 关键字进 LIKE 之前的转义：反斜杠、单引号、{@code %}、{@code _}。
     *
     * @param keyword 用户输入（调用方需保证非 null；方法内已 trim）
     */
    public static String escapeLike(String keyword) {
        return keyword.trim()
                .replace("\\", "\\\\")
                .replace("'", "''")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
