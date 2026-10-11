package org.jeecg.modules.shop.brand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.brand.entity.ShopBrandTranslation;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 品牌多语言 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Mapper
public interface ShopBrandTranslationMapper extends BaseMapper<ShopBrandTranslation> {

    @Select("SELECT * FROM ck_shop_brand_translation WHERE brand_id = #{brandId}")
    List<ShopBrandTranslation> selectByBrandId(@Param("brandId") String brandId);

    /**
     * 按业务 id 批量查询。
     * 注意：不能用 BaseMapper.selectBatchIds —— 它按**翻译表自身主键 id** 查，
     * 而调用方传进来的是 brand_id，结果恒为空（本地化名称会静默回退成编码）。
     */
    @Select("<script>SELECT * FROM ck_shop_brand_translation WHERE brand_id IN "
            + "<foreach collection='brandIds' item='bid' open='(' separator=',' close=')'>#{bid}</foreach>"
            + "</script>")
    List<ShopBrandTranslation> selectByBrandIds(@Param("brandIds") Collection<String> brandIds);
}
