package org.jeecg.modules.shop.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.promotion.entity.ShopPromotionProduct;

import java.util.List;

/**
 * <p>
 * 促销活动-商品关联 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-13
 */

@Mapper
public interface ShopPromotionProductMapper extends BaseMapper<ShopPromotionProduct> {

    /**
     * 物理删除某活动的全部关联商品行。
     *
     * <p>该表**没有 del_flag 列**（见实体注释），删除活动时关联行只能物理删除；
     * 它们不影响任何历史订单（订单记的是商品/SKU，不是活动关联行），
     * 留着反而会让"已删除的活动"继续占用商品。
     */
    @Delete("DELETE FROM ck_shop_promotion_product WHERE promotion_id = #{promotionId}")
    boolean deleteByPromotionId(@Param("promotionId") String promotionId);

    /**
     * 物理批量删除多个活动的关联商品行。
     */
    @Delete("<script>DELETE FROM ck_shop_promotion_product WHERE promotion_id IN "
            + "<foreach collection='promotionIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    boolean deleteByPromotionIds(@Param("promotionIds") List<String> promotionIds);
}

