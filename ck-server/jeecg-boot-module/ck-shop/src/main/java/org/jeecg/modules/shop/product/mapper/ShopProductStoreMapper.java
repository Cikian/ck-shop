package org.jeecg.modules.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.shop.product.entity.ShopProductStore;
import org.jeecg.modules.shop.product.entity.vo.StoreProductRowVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 商品-店铺上架关系 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Mapper
public interface ShopProductStoreMapper extends BaseMapper<ShopProductStore> {

    /**
     * 写入/更新一条上架关系。
     *
     * 用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 一条语句同时覆盖三种情况：
     * <ol>
     *   <li>首次上架 → 插入；</li>
     *   <li>已上架再保存 → 原地更新（主键不变）；</li>
     *   <li><b>曾经下架过再重新上架 → 复活</b>（{@code del_flag = 0}）。</li>
     * </ol>
     * 第 3 种最关键：唯一键 {@code (product_id, store_id)} 不含 del_flag，
     * 软删行仍占着这个键，"先删再插"会直接撞唯一键。
     */
    @Insert("INSERT INTO ck_shop_product_store "
            + "(id, product_id, store_id, category_id, price_override, status, sort_order, is_recommend, del_flag, create_by, create_time, update_by, update_time) "
            + "VALUES (#{id}, #{productId}, #{storeId}, #{categoryId}, #{priceOverride}, #{status}, #{sortOrder}, #{isRecommend}, 0, #{username}, NOW(), #{username}, NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "category_id = VALUES(category_id), price_override = VALUES(price_override), status = VALUES(status), "
            + "sort_order = VALUES(sort_order), is_recommend = VALUES(is_recommend), del_flag = 0, "
            + "update_by = VALUES(update_by), update_time = VALUES(update_time)")
    int upsertListing(@Param("id") String id,
                      @Param("productId") String productId,
                      @Param("storeId") String storeId,
                      @Param("categoryId") String categoryId,
                      @Param("priceOverride") BigDecimal priceOverride,
                      @Param("status") String status,
                      @Param("sortOrder") Integer sortOrder,
                      @Param("isRecommend") String isRecommend,
                      @Param("username") String username);

    /**
     * 逻辑删除"本次没有勾选"的店铺上架关系。
     * keepStoreIds 为空表示用户取消了所有店铺 → 全部下架（只软删，不物理删除）。
     */
    @Update("<script>UPDATE ck_shop_product_store SET del_flag = 1, update_time = NOW() "
            + "WHERE del_flag = 0 AND product_id = #{productId} "
            + "<if test='keepStoreIds != null and keepStoreIds.size() > 0'>"
            + "  AND store_id NOT IN <foreach collection='keepStoreIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach>"
            + "</if>"
            + "</script>")
    int softDeleteMissing(@Param("productId") String productId, @Param("keepStoreIds") List<String> keepStoreIds);

    /**
     * 某商品的全部生效上架关系。
     */
    @Select("SELECT * FROM ck_shop_product_store WHERE del_flag = 0 AND product_id = #{productId}")
    List<ShopProductStore> selectByProductId(@Param("productId") String productId);

    /**
     * 批量取上架关系（管理端列表展示"上架到哪些店"，按当前页商品一次性取）。
     */
    @Select("<script>SELECT * FROM ck_shop_product_store WHERE del_flag = 0 AND product_id IN "
            + "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    List<ShopProductStore> selectByProductIds(@Param("productIds") List<String> productIds);

    /**
     * 店铺端：某店已上架商品的分页（同时约束商品总开关与店铺上架状态）。
     */
    IPage<StoreProductRowVO> selectStoreProductPage(Page<StoreProductRowVO> page,
                                                    @Param("storeId") String storeId,
                                                    @Param("categoryIds") List<String> categoryIds,
                                                    @Param("brandId") String brandId,
                                                    @Param("keyword") String keyword);

    /**
     * 管理端：按店铺筛选商品分页。与店铺端的差别是**不强制**商品已上架（管理员要能看到下架商品）。
     */
    IPage<StoreProductRowVO> selectAdminStoreProductPage(Page<StoreProductRowVO> page,
                                                         @Param("storeId") String storeId,
                                                         @Param("status") String status,
                                                         @Param("categoryIds") List<String> categoryIds,
                                                         @Param("brandId") String brandId,
                                                         @Param("spuCode") String spuCode,
                                                         @Param("tagId") String tagId,
                                                         @Param("keyword") String keyword);

    /**
     * 店铺端：某店每个分类下的已上架商品数（用于分类页显示角标）。
     * 分类归属按"本店分类优先、回退商品主分类"归并，与列表口径一致。
     */
    List<Map<String, Object>> countCategoryProducts(@Param("storeId") String storeId);
}
