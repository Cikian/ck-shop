package org.jeecg.modules.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.product.entity.ShopProductDetailBlock;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 商品详情模块 Mapper
 * </p>
 *
 * <p>本表**没有 del_flag 列**，删除一律是物理 DELETE（契约 1.1）；
 * 所有 SQL 只对本表操作，不触碰任何既有业务表。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
@Mapper
public interface ShopProductDetailBlockMapper extends BaseMapper<ShopProductDetailBlock> {

    /**
     * 取某商品的全部详情模块（**全部语言**）。
     * 排序口径与表索引 idx_block_product_lang 一致：先语言、再同语言内的 sort_order；
     * 末尾再按 id 兜底，保证同 sortOrder 时读取顺序稳定（回填/整体替换都依赖这个稳定性）。
     */
    @Select("SELECT * FROM ck_shop_product_detail_block "
            + "WHERE product_id = #{productId} "
            + "ORDER BY language_code ASC, sort_order ASC, id ASC")
    List<ShopProductDetailBlock> selectByProductId(@Param("productId") String productId);

    /**
     * 整体替换前清空某商品的**全部语言**模块（物理删除）。
     */
    @Delete("DELETE FROM ck_shop_product_detail_block WHERE product_id = #{productId}")
    int deleteByProductId(@Param("productId") String productId);

    /**
     * 商品删除时级联物理清除（批量，避免 N 次 DELETE）。
     */
    @Delete("<script>DELETE FROM ck_shop_product_detail_block WHERE product_id IN "
            + "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</script>")
    int deleteByProductIds(@Param("productIds") Collection<String> productIds);
}
