package org.jeecg.modules.shop.brand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.brand.entity.ShopBrand;
import org.jeecg.modules.shop.brand.entity.ShopBrandTranslation;

import java.util.List;

/**
 * <p>
 * 品牌 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Mapper
public interface ShopBrandMapper extends BaseMapper<ShopBrand> {

    @Delete("DELETE FROM ck_shop_brand_translation WHERE brand_id = #{brandId}")
    int deleteTranslationsByBrandId(@Param("brandId") String brandId);

    @Delete("<script>DELETE FROM ck_shop_brand_translation WHERE brand_id IN "
            + "<foreach collection='brandIds' item='bid' open='(' separator=',' close=')'>#{bid}</foreach></script>")
    int deleteTranslationsByBrandIds(@Param("brandIds") List<String> brandIds);

    @Select("SELECT * FROM ck_shop_brand_translation WHERE brand_id = #{brandId}")
    List<ShopBrandTranslation> selectTranslationsByBrandId(@Param("brandId") String brandId);

    @Select("SELECT * FROM ck_shop_brand_translation WHERE brand_id = #{brandId} AND language_code = #{languageCode} LIMIT 1")
    ShopBrandTranslation selectTranslation(@Param("brandId") String brandId,
                                           @Param("languageCode") String languageCode);

    @Select("<script>SELECT * FROM ck_shop_brand_translation WHERE brand_id IN "
            + "<foreach collection='brandIds' item='bid' open='(' separator=',' close=')'>#{bid}</foreach>"
            + "</script>")
    List<ShopBrandTranslation> selectTranslationsByBrandIds(@Param("brandIds") List<String> brandIds);
}
