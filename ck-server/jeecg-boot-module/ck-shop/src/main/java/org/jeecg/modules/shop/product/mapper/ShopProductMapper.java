package org.jeecg.modules.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.product.entity.ShopProduct;

import java.util.List;

/**
 * <p>
 * 商品 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Mapper
public interface ShopProductMapper extends BaseMapper<ShopProduct> {

    /**
     * 按标签ID查询商品ID（仅未删除商品）
     *
     * @param tagId 标签ID
     * @return 商品ID列表
     */
    @Select("SELECT p.id FROM ck_shop_product p "
            + "INNER JOIN sk_shop_product_tag_relation r ON r.product_id = p.id AND r.del_flag = 0 "
            + "WHERE p.del_flag = 0 AND r.tag_id = #{tagId}")
    List<String> selectProductIdsByTagId(@Param("tagId") String tagId);

    /**
     * 按 SPU 编码查询（**显式包含已软删除的行**）。
     * 用途：SPU 唯一性预检。表上有 uk_spu_code(spu_code, status)，唯一索引不区分 del_flag，
     * 因此只查未删除行会漏判，导致软删后重建同一 SPU 撞唯一键。
     * LIMIT 1 同时兜住历史上已存在多行同 SPU 时的 TooManyResultsException。
     */
    @Select("SELECT id, spu_code, status, del_flag FROM ck_shop_product "
            + "WHERE spu_code = #{spuCode} LIMIT 1")
    ShopProduct selectAnyBySpuCode(@Param("spuCode") String spuCode);

    /**
     * 按 spu_code 取**未删除**的商品（店铺端路由用）。
     * spu_code 已收紧为全局唯一且禁止回收复用，因此分享出去的链接永远指向同一个商品、
     * 不会在商品删除后被另一个商品"接管"。
     */
    @Select("SELECT * FROM ck_shop_product WHERE spu_code = #{spuCode} AND del_flag = 0 LIMIT 1")
    ShopProduct selectAliveBySpuCode(@Param("spuCode") String spuCode);
}
