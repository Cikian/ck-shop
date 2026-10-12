package org.jeecg.modules.shop.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.shop.tag.entity.ShopTag;
import org.jeecg.modules.shop.tag.entity.ShopTagTranslation;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 标签 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Mapper
public interface ShopTagMapper extends BaseMapper<ShopTag> {

    @Delete("DELETE FROM ck_shop_tag_translation WHERE tag_id = #{tagId}")
    int deleteTranslationsByTagId(@Param("tagId") String tagId);

    @Delete("<script>DELETE FROM ck_shop_tag_translation WHERE tag_id IN "
            + "<foreach collection='tagIds' item='tid' open='(' separator=',' close=')'>#{tid}</foreach></script>")
    int deleteTranslationsByTagIds(@Param("tagIds") List<String> tagIds);

    @Select("SELECT * FROM ck_shop_tag_translation WHERE tag_id = #{tagId}")
    List<ShopTagTranslation> selectTranslationsByTagId(@Param("tagId") String tagId);

    @Select("<script>SELECT * FROM ck_shop_tag_translation WHERE tag_id IN "
            + "<foreach collection='tagIds' item='tid' open='(' separator=',' close=')'>#{tid}</foreach></script>")
    List<ShopTagTranslation> selectTranslationsByTagIds(@Param("tagIds") Collection<String> tagIds);

    @Select("SELECT * FROM ck_shop_tag_translation WHERE tag_id = #{tagId} AND language_code = #{languageCode} LIMIT 1")
    ShopTagTranslation selectTranslation(@Param("tagId") String tagId,
                                         @Param("languageCode") String languageCode);

    @Delete("DELETE FROM sk_shop_product_tag_relation WHERE product_id = #{productId}")
    int deleteProductRelations(@Param("productId") String productId);

    /**
     * 只解除某个商品与某个标签的关联（软删除）。
     * 用于"增量同步标签"：只删本次确实被移除的关联，不再整批删了重建。
     */
    @Update("UPDATE sk_shop_product_tag_relation SET del_flag = 1, update_time = NOW() "
            + "WHERE del_flag = 0 AND product_id = #{productId} AND tag_id = #{tagId}")
    int deleteProductRelation(@Param("productId") String productId, @Param("tagId") String tagId);

    /**
     * 批量解除关联（**软删除**）。
     * 与商品子表保持一致的原则：业务数据只做逻辑删除，不物理清除，保留可追溯性。
     */
    @Update("<script>UPDATE sk_shop_product_tag_relation SET del_flag = 1, update_time = NOW() "
            + "WHERE del_flag = 0 AND product_id IN "
            + "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach></script>")
    int softDeleteProductRelationsByProductIds(@Param("productIds") Collection<String> productIds);

    @Insert("INSERT INTO sk_shop_product_tag_relation "
            + "(id, product_id, tag_id, del_flag, create_by, create_time, update_by, update_time) "
            + "VALUES (#{id}, #{productId}, #{tagId}, 0, #{username}, #{now}, #{username}, #{now})")
    int insertProductTagRelation(@Param("id") String id,
                                 @Param("productId") String productId,
                                 @Param("tagId") String tagId,
                                 @Param("username") String username,
                                 @Param("now") java.util.Date now);

    /**
     * 商品当前有效的标签 id。
     * 必须带 {@code del_flag = 0}：关联是**软删除**的，漏了这个条件会读到已解除的关联——
     * 表现为"取消了标签但列表/详情还带着它"，并且回写时把软删行当成"已存在"从而跳过重新插入，
     * 于是重新勾选也回不来（评审发现的确定 bug）。
     */
    @Select("SELECT tag_id FROM sk_shop_product_tag_relation WHERE del_flag = 0 AND product_id = #{productId}")
    List<String> selectTagIdsByProductId(@Param("productId") String productId);

    /** 同上，批量版本（同样必须过滤 del_flag） */
    @Select("<script>SELECT product_id, tag_id FROM sk_shop_product_tag_relation WHERE del_flag = 0 AND product_id IN "
            + "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    List<java.util.Map<String, Object>> selectProductTagRelations(@Param("productIds") Collection<String> productIds);
}
