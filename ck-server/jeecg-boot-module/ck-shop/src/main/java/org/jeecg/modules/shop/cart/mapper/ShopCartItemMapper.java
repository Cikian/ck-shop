package org.jeecg.modules.shop.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.cart.entity.ShopCartItem;
import org.jeecg.modules.shop.cart.entity.vo.StoreCartCountVO;
import org.jeecg.modules.shop.cart.entity.vo.StoreCartItemVO;

import java.util.List;

/**
 * <p>
 * 购物车明细 Mapper
 * </p>
 *
 * 关于"为什么这几个方法要用裸 SQL 而不是 MyBatis-Plus 的 updateById"：
 * 实体上打了 {@code @TableLogic}，MP 会自动给所有 update/delete 追加 {@code AND del_flag = 0}，
 * 于是**已软删除的行永远匹配不到**，而"删掉商品后重新加购要把旧行复活"这个场景恰好需要写到它。
 * 因此复活类操作用 XML 显式写 SQL，并且自己控制 del_flag 条件。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Mapper
public interface ShopCartItemMapper extends BaseMapper<ShopCartItem> {

    /**
     * 查询当前用户在当前店铺的购物车明细（一次性带出商品、翻译、SKU 信息）。
     *
     * 之所以用一条 join 查询而不是"先查购物车行、再逐条查商品"：
     * 逐条查会产生 N+1 次数据库往返，购物车越大越慢，而且很难在一处保证状态判定口径一致。
     *
     * @param userId      用户ID
     * @param storeId     店铺ID
     * @param lang        当前语言（用于取商品标题/封面）
     * @param defaultLang 兜底语言（当前语言没有翻译时用它）
     * @return 明细列表（按最近修改时间倒序）
     */
    List<StoreCartItemVO> selectStoreCartItems(@Param("userId") String userId,
                                               @Param("storeId") String storeId,
                                               @Param("lang") String lang,
                                               @Param("defaultLang") String defaultLang);

    /**
     * 按"用户 + 店铺 + 商品 + SKU"取一行，**包含已软删除的行**。
     *
     * 唯一索引 uk_cart_user_store_sku 不含 del_flag，所以被删除的行仍占着这个键；
     * 重新加购时必须先把它查出来（不管死活），再决定是 INSERT 还是"复活 + 累加"，
     * 否则 INSERT 会撞唯一索引。
     */
    ShopCartItem selectAnyBySku(@Param("userId") String userId,
                                @Param("storeId") String storeId,
                                @Param("productId") String productId,
                                @Param("skuId") String skuId);

    /**
     * 直接写入目标数量，并把 del_flag 置回 0（复活）。
     *
     * 幂等：行本来就没删除时，等价于一次普通的数量更新。
     *
     * @return 受影响行数
     */
    int mergeQuantity(@Param("userId") String userId,
                      @Param("storeId") String storeId,
                      @Param("productId") String productId,
                      @Param("skuId") String skuId,
                      @Param("quantity") int quantity,
                      @Param("operator") String operator);

    /**
     * 软删除指定明细（只能是自己的、且属于当前店铺的行）。
     *
     * @param ids 明细ID集合，调用方保证非空
     * @return 实际删除行数（用于告知用户"有几条已被别处删除"）
     */
    int softDeleteByIds(@Param("userId") String userId,
                        @Param("storeId") String storeId,
                        @Param("ids") List<String> ids,
                        @Param("operator") String operator);

    /**
     * 清空当前用户在当前店铺的购物车。
     *
     * @return 实际删除行数
     */
    int softDeleteAll(@Param("userId") String userId,
                      @Param("storeId") String storeId,
                      @Param("operator") String operator);

    // ------------------------------------------------------------------
    // 后台统计用的"当前购物车快照"（轻量聚合，不加载明细）
    // ------------------------------------------------------------------

    /**
     * <b>买家本人</b>在当前店铺的购物车角标（条目数 + 件数），一条 SQL 出两个聚合值。
     *
     * <p>为什么必须单独有这个方法：下面的 countItems/sumQuantity 是**后台统计口径** ——
     * 它们只按 store_id 聚合，统计的是"整个店铺所有买家的购物车"。买家接口
     * （/shop/cart/store/count）如果调用它们，得到的是全店数字：角标显示错误，
     * 同时把"本店当前有多少购物车条目/件商品"这个经营数据泄露给任意登录用户。
     * 两个口径互相独立，不能复用。
     */
    StoreCartCountVO countUserCart(@Param("userId") String userId,
                                   @Param("storeId") String storeId);

    /** 当前购物车条目数；storeIds 为 null 表示不限店铺（**后台统计口径**，按店铺聚合） */
    Long countItems(@Param("storeIds") List<String> storeIds);

    /** 当前购物车件数；storeIds 为 null 表示不限店铺（**后台统计口径**，按店铺聚合） */
    Long sumQuantity(@Param("storeIds") List<String> storeIds);

    /** 当前购物车涉及的用户数；storeIds 为 null 表示不限店铺 */
    Long countUsers(@Param("storeIds") List<String> storeIds);
}
