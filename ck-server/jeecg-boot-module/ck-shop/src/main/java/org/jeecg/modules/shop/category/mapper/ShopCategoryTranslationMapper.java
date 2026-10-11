package org.jeecg.modules.shop.category.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.category.entity.ShopCategoryTranslation;

import java.util.List;

/**
 * <p>
 * 分类多语言表
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-08-03
 */

@Mapper
public interface ShopCategoryTranslationMapper extends BaseMapper<ShopCategoryTranslation> {

    @Delete("DELETE FROM ck_shop_category_translation WHERE category_id = #{categoryId}")
    boolean deleteByCategoryId(String categoryId);

    @Delete("DELETE FROM ck_shop_category_translation WHERE category_id IN ( #{categoryIds} )")
    boolean deleteBatchCategoryId(List<String> categoryIds);

    @Select("SELECT * FROM ck_shop_category_translation WHERE category_id = #{categoryId}")
    List<ShopCategoryTranslation> selectByCategoryId(String categoryId);

    /**
     * 按业务 id 批量查询分类多语言。
     * 注意：不能用 BaseMapper.selectBatchIds —— 它按**翻译表自身主键 id** 查，
     * 而调用方传进来的是 category_id，结果恒为空（商品列表的分类本地化名会静默回退）。
     */
    @Select("<script>SELECT * FROM ck_shop_category_translation WHERE category_id IN "
            + "<foreach collection='categoryIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach>"
            + "</script>")
    List<ShopCategoryTranslation> selectByCategoryIds(@Param("categoryIds") List<String> categoryIds);
}
