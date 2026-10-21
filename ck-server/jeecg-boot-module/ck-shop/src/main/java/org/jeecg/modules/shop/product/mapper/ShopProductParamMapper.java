package org.jeecg.modules.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.product.entity.ShopProductParam;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 商品规格参数 Mapper
 * </p>
 *
 * <p>本表**没有 del_flag 列**，删除一律是物理 DELETE（契约 1.2）。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
@Mapper
public interface ShopProductParamMapper extends BaseMapper<ShopProductParam> {

    /**
     * 取某商品的全部规格参数（**全部语言**），排序口径同表索引 idx_param_product_lang。
     */
    @Select("SELECT * FROM ck_shop_product_param "
            + "WHERE product_id = #{productId} "
            + "ORDER BY language_code ASC, sort_order ASC, id ASC")
    List<ShopProductParam> selectByProductId(@Param("productId") String productId);

    /**
     * 整体替换前清空某商品的**全部语言**参数（物理删除）。
     */
    @Delete("DELETE FROM ck_shop_product_param WHERE product_id = #{productId}")
    int deleteByProductId(@Param("productId") String productId);

    /**
     * 商品删除时级联物理清除（批量）。
     */
    @Delete("<script>DELETE FROM ck_shop_product_param WHERE product_id IN "
            + "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    int deleteByProductIds(@Param("productIds") Collection<String> productIds);
}
