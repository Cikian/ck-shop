package org.jeecg.modules.shop.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.favorite.entity.ShopFavorite;

/**
 * <p>
 * 商品收藏 Mapper
 * </p>
 *
 * 关于"为什么要用裸 SQL 而不是 MyBatis-Plus 的 deleteById"：
 * 实体上打了 {@code @TableLogic}，MP 会自动给所有 update/delete 追加 {@code AND del_flag = 0}，
 * 于是**已取消收藏的行永远匹配不到**，而"取消后再收藏要把旧行复活"这个场景恰好需要写到它。
 * 因此复活类操作用 XML 显式写 SQL，并且自己控制 del_flag 条件（与购物车同一取舍）。
 *
 * 列表分页（"我的收藏"按收藏时间倒序）直接走 MP 的 selectPage + @TableLogic，
 * 不需要额外 SQL；这里只放 MP 表达不了的那几条。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Mapper
public interface ShopFavoriteMapper extends BaseMapper<ShopFavorite> {

    /**
     * 按"用户 + 店铺 + 商品"取一行，**包含已软删除（已取消收藏）的行**。
     *
     * 唯一索引 uk_favorite_user_store_product 不含 del_flag，所以被取消的行仍占着这个键；
     * 再次收藏时必须先把它查出来（不管死活），再决定是 INSERT 还是"复活"，
     * 否则 INSERT 会撞唯一索引。
     */
    ShopFavorite selectAnyByProduct(@Param("userId") String userId,
                                    @Param("storeId") String storeId,
                                    @Param("productId") String productId);

    /**
     * 复活（del_flag 置回 0）并刷新时间。
     *
     * @return 受影响行数（1 = 确实从"已取消"变回"已收藏"，0 = 行不存在）
     */
    int restoreFavorite(@Param("userId") String userId,
                        @Param("storeId") String storeId,
                        @Param("productId") String productId,
                        @Param("operator") String operator);

    /**
     * 取消收藏（只软删除**当前有效**的那一行）。
     *
     * 返回值刻意保留：只有"确实删掉了 1 行"才应该把商品的 favorite_count 减 1，
     * 重复点两次取消不能让计数掉两次。
     *
     * @return 受影响行数（1 = 取消成功，0 = 本来就没收藏）
     */
    int softDeleteByProduct(@Param("userId") String userId,
                            @Param("storeId") String storeId,
                            @Param("productId") String productId,
                            @Param("operator") String operator);
}
