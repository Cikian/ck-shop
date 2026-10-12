package org.jeecg.modules.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.product.entity.ShopProduct;

import java.util.List;
import java.util.Map;

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
     * 按 SPU 编码 + 归属部门查询（**显式包含已软删除的行**）。
     * 用途：SPU 唯一性预检。唯一索引是 {@code uk_spu_code(sys_org_code, spu_code)}，
     * 索引不区分 del_flag，因此只查未删除行会漏判，导致软删后重建同一 SPU 撞唯一键。
     * 同时必须带上 sys_org_code：spu_code 只在部门内唯一，不同店铺可以重复。
     * LIMIT 1 兜住历史上已存在多行同 SPU 时的 TooManyResultsException。
     */
    @Select("SELECT id, spu_code, sys_org_code, status, del_flag FROM ck_shop_product "
            + "WHERE spu_code = #{spuCode} AND sys_org_code = #{orgCode} LIMIT 1")
    ShopProduct selectAnyBySpuCode(@Param("spuCode") String spuCode, @Param("orgCode") String orgCode);

    /**
     * 按 spu_code 取**未删除**的商品（店铺端路由用）。
     *
     * 必须同时约束归属部门 {@code LIKE 'orgCode%'}：spu_code 现在只在部门内唯一，
     * 同一个编码可能在多个店铺各有一件商品；少了这个条件，
     * A 店的分享链接就可能打开 B 店的商品（多店铺最典型的越权事故）。
     */
    @Select("SELECT * FROM ck_shop_product WHERE spu_code = #{spuCode} AND del_flag = 0 "
            + "AND sys_org_code LIKE CONCAT(#{orgCode}, '%') LIMIT 1")
    ShopProduct selectAliveBySpuCode(@Param("spuCode") String spuCode, @Param("orgCode") String orgCode);

    /**
     * 某个归属部门（含下级部门）下、每个分类的**已上架**商品数。
     * 供店铺端分类树显示角标：一次分组查询搞定，**不要在遍历分类时逐个 COUNT**（那就是 N+1）。
     */
    @Select("SELECT category_id AS categoryId, COUNT(*) AS productCount FROM ck_shop_product "
            + "WHERE del_flag = 0 AND status = 'Y' AND sys_org_code LIKE CONCAT(#{orgCode}, '%') "
            + "GROUP BY category_id")
    List<Map<String, Object>> countCategoryProducts(@Param("orgCode") String orgCode);

    /**
     * 某分类下（未删除的）商品数。用于"分类还有商品时不允许删除/改归属"的前置校验，
     * 避免产生指向已删分类的 category_id（前台按分类浏览时商品凭空消失）。
     */
    @Select("SELECT COUNT(*) FROM ck_shop_product WHERE del_flag = 0 AND category_id = #{categoryId}")
    int countAliveByCategoryId(@Param("categoryId") String categoryId);
}
