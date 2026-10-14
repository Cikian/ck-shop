package org.jeecg.modules.shop.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.promotion.entity.ShopPromotionTranslation;

import java.util.List;

/**
 * <p>
 * 促销活动多语言表 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Mapper
public interface ShopPromotionTranslationMapper extends BaseMapper<ShopPromotionTranslation> {

    /**
     * 按活动 id 取该活动的全部语言行。
     */
    @Select("SELECT * FROM ck_shop_promotion_translation WHERE promotion_id = #{promotionId}")
    List<ShopPromotionTranslation> selectByPromotionId(@Param("promotionId") String promotionId);

    /**
     * 按活动 id 批量取多语言行。
     *
     * <p>不能用 {@code BaseMapper.selectBatchIds}——它按**翻译表自身主键 id** 查，
     * 而调用方传进来的是 promotion_id，结果恒为空（列表页会静默回退成主表中文）。
     */
    @Select("<script>SELECT * FROM ck_shop_promotion_translation WHERE promotion_id IN "
            + "<foreach collection='promotionIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    List<ShopPromotionTranslation> selectByPromotionIds(@Param("promotionIds") List<String> promotionIds);

    /**
     * 物理删除某活动的全部语言行（软删除活动时同步清理，表无 del_flag）。
     */
    @Delete("DELETE FROM ck_shop_promotion_translation WHERE promotion_id = #{promotionId}")
    boolean deleteByPromotionId(@Param("promotionId") String promotionId);

    /**
     * 物理批量删除多个活动的语言行。
     */
    @Delete("<script>DELETE FROM ck_shop_promotion_translation WHERE promotion_id IN "
            + "<foreach collection='promotionIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    boolean deleteByPromotionIds(@Param("promotionIds") List<String> promotionIds);
}
