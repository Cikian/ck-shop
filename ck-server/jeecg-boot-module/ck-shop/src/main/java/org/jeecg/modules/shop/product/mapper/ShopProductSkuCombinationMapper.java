package org.jeecg.modules.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.shop.product.entity.ShopProductSkuCombination;

import java.util.List;

/**
 * <p>
 * 商品SKU规格组合 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Mapper
public interface ShopProductSkuCombinationMapper extends BaseMapper<ShopProductSkuCombination> {

    /**
     * 查询某商品的全部 SKU 组合（未删除），并带出每个组合包含的规格值ID
     * 结果通过 XML 中的 collection 映射装配 specValueIds
     *
     * @param productId 商品ID
     * @return 组合列表
     */
    List<ShopProductSkuCombination> selectByProductIdWithValues(@Param("productId") String productId);

    /**
     * 批量查询多个商品的 SKU 组合（带规格值ID），用于列表页展开行
     *
     * @param productIds 商品ID集合
     * @return 组合列表
     */
    List<ShopProductSkuCombination> selectByProductIdsWithValues(@Param("productIds") List<String> productIds);

    /**
     * 查询某商品的全部组合，**包含已软删除的行**（只取匹配所需的三个字段）。
     *
     * 为什么需要它：本表有唯一索引 (product_id, combination_key)，且索引**不含 del_flag**，
     * 所以被软删除的行仍然占着那个组合键。用户删掉某个 SKU 之后又把它加回来时，
     * 直接 INSERT 会撞唯一索引，正确做法是把历史行"复活"（del_flag 置回 0）并原地更新，
     * 这样同时还能保住 SKU 主键不变（订单会引用 SKU 主键）。
     *
     * 刻意不查 del_flag：调用方不需要（复活操作本身幂等），
     * 也就不会因为字段映射的细节产生隐性依赖。
     */
    @Select("SELECT id, product_id AS productId, combination_key AS combinationKey "
            + "FROM ck_shop_product_sku_combination WHERE product_id = #{productId}")
    List<ShopProductSkuCombination> selectAllIncludingDeleted(@Param("productId") String productId);

    /**
     * 查询某商品**存活**（del_flag = 0）的组合 id。
     * 由数据库直接过滤，避免把"是否已删除"的判断寄托在实体字段映射上。
     */
    @Select("SELECT id FROM ck_shop_product_sku_combination WHERE del_flag = 0 AND product_id = #{productId}")
    List<String> selectLiveIdsByProductId(@Param("productId") String productId);

    /**
     * 复活一条被软删除的组合（del_flag 置回 0）。
     *
     * 必须用裸 SQL：MyBatis-Plus 的 @TableLogic 会给 updateById / update(wrapper) 自动追加
     * `AND del_flag = 0`，因此对已软删除的行，普通更新永远匹配不到，必须先单独把 del_flag 清掉。
     *
     * 该操作是幂等的：行本来就未删除时影响 0 行，没有副作用。
     *
     * @param id 组合ID
     * @return 受影响行数（1=确实复活的软删行，0=该行本来就未删除）
     */
    @Update("UPDATE ck_shop_product_sku_combination SET del_flag = 0 WHERE id = #{id} AND del_flag = 1")
    int reviveById(@Param("id") String id);
}
