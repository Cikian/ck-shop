package org.jeecg.modules.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
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

    /**
     * 增量维护收藏数（{@code ck_shop_product.favorite_count}）。
     *
     * <p>为什么是"库内自增"而不是"读出来 +1 再写回"：后者在并发收藏下必然丢更新
     * （两个人同时读出 10，各自写 11，实际收了两次却只 +1）。这里把加减放到一条 UPDATE 里，
     * 由数据库保证原子性。
     *
     * <p>为什么用 {@code CASE} 而不是 {@code GREATEST}：CASE 是 SQL92 标准写法，
     * MySQL / PostgreSQL / Oracle 行为完全一致，不依赖具体方言的函数实现。
     * 下限夹到 0 是为了兜住"历史计数本来就比关系表行数少"的脏数据——宁可少算，不出现负数。
     *
     * <p>刻意**不动 update_time**：收藏是买家行为，不该让运营在后台看到"这件商品刚被修改过"
     * 而去找一个不存在的编辑记录。
     *
     * @param productId 商品主键
     * @param delta     增量，+1 收藏 / -1 取消收藏
     * @return 受影响行数（0 表示商品不存在或已软删除）
     */
    @Update("UPDATE ck_shop_product SET favorite_count = "
            + "CASE WHEN COALESCE(favorite_count, 0) + #{delta} < 0 THEN 0 "
            + "ELSE COALESCE(favorite_count, 0) + #{delta} END "
            + "WHERE id = #{productId} AND del_flag = 0")
    int incrementFavoriteCount(@Param("productId") String productId, @Param("delta") int delta);
}
