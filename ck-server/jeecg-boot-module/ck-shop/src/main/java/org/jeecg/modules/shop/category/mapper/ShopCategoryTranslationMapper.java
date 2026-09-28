package org.jeecg.modules.shop.category.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
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
}
