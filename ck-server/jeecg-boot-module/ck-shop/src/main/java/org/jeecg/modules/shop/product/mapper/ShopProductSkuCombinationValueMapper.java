package org.jeecg.modules.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.shop.product.entity.ShopProductSkuCombinationValue;

/**
 * <p>
 * 商品SKU组合-规格值明细 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Mapper
public interface ShopProductSkuCombinationValueMapper extends BaseMapper<ShopProductSkuCombinationValue> {

    /**
     * 软删除某个组合的规格值明细。
     *
     * 刻意**不做物理删除**：明细记录了"这个 SKU 当时由哪些规格值组成"，
     * 属于订单可追溯的历史信息。改写时只把旧明细标记为 del_flag = 1，
     * 保证任何时刻都能还原某个 SKU 的规格构成。
     */
    @Update("UPDATE ck_shop_product_sku_combination_value SET del_flag = 1 "
            + "WHERE del_flag = 0 AND combination_id = #{combinationId}")
    int softDeleteByCombinationId(@Param("combinationId") String combinationId);
}
