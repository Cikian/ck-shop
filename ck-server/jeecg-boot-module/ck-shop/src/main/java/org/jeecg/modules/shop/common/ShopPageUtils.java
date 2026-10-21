package org.jeecg.modules.shop.common;

/**
 * <p>
 * 分页参数收敛（pageNo / pageSize）。
 * </p>
 *
 * <h3>为什么必须有这一层</h3>
 * 店铺端商品列表是**匿名接口**，之前把 {@code pageSize} 原样透传给
 * {@code new Page<>(pageNo, pageSize)}。任何人请求 {@code pageSize=1000000}
 * 都能拉全量商品，随后还会对全量 productId 做 translations / combinations / stats
 * 的 {@code IN} 查询——一次请求就能把数据库打满。
 *
 * <p>管理端列表同样没有上限，只是需要登录（风险低一些，但同样会拖垮库）。
 * 因此所有分页入口统一走这里：页码最小 1、每页条数落在 [1, max] 区间内。
 *
 * <p>注意：这是**代码层**的约束，不依赖 MyBatis-Plus 的全局 {@code maxLimit}——
 * 那个开关会影响整个 JeecgBoot 平台（其他模块有合法的 pageSize 200/500 请求），
 * 一刀切会把无关功能改坏。
 *
 * @author Cikian
 * @since 2026-10-22
 */
public final class ShopPageUtils {

    /** 店铺端（匿名）每页上限：够展示、又不至于一次拉走整店商品 */
    public static final int STORE_MAX_PAGE_SIZE = 60;

    /** 管理端每页上限：列表页 + 选择器够用 */
    public static final int ADMIN_MAX_PAGE_SIZE = 100;

    private ShopPageUtils() {
    }

    /** 页码：null / 0 / 负数一律按第 1 页 */
    public static int pageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    /** 每页条数：null 用默认值，其余夹在 [1, maxSize] */
    public static int pageSize(Integer pageSize, int defaultSize, int maxSize) {
        int size = pageSize == null ? defaultSize : pageSize;
        if (size < 1) {
            return 1;
        }
        return Math.min(size, maxSize);
    }

    /** 店铺端分页（默认 12，上限 60） */
    public static int storePageSize(Integer pageSize) {
        return pageSize(pageSize, 12, STORE_MAX_PAGE_SIZE);
    }

    /** 管理端分页（默认 10，上限 100） */
    public static int adminPageSize(Integer pageSize) {
        return pageSize(pageSize, 10, ADMIN_MAX_PAGE_SIZE);
    }
}
