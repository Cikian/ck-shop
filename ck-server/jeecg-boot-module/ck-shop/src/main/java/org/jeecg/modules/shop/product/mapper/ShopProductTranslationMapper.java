package org.jeecg.modules.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.product.entity.ShopProductTranslation;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 商品多语言 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Mapper
public interface ShopProductTranslationMapper extends BaseMapper<ShopProductTranslation> {

    /**
     * 删除商品的全部多语言行。
     * 注意：ck_shop_product_translation 表**没有 del_flag 列**（与分类/品牌/标签的翻译表一致），
     * 因此这里必须是物理删除；不要在这张表上用 @TableLogic 或引用 del_flag，
     * 否则会抛 Unknown column 'del_flag' in 'where clause'。
     * 物理删除也顺带避开了 uk_product_lang (product_id, language_code) 唯一索引被软删行占住的问题。
     */
    @Delete("<script>DELETE FROM ck_shop_product_translation WHERE product_id IN "
            + "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    int deleteByProductIds(@Param("productIds") Collection<String> productIds);

    @Select("SELECT * FROM ck_shop_product_translation WHERE product_id = #{productId}")
    List<ShopProductTranslation> selectByProductId(@Param("productId") String productId);

    /**
     * 批量取多语言文案（避免列表页 N+1）
     */
    @Select("<script>SELECT * FROM ck_shop_product_translation WHERE product_id IN "
            + "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    List<ShopProductTranslation> selectByProductIds(@Param("productIds") Collection<String> productIds);
}
