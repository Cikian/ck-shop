package org.jeecg.modules.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.product.entity.ShopProductSpecValue;

import java.util.List;

/**
 * <p>
 * 商品规格可选取值 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Mapper
public interface ShopProductSpecValueMapper extends BaseMapper<ShopProductSpecValue> {

    @Select("SELECT * FROM ck_shop_product_spec_value WHERE del_flag = 0 AND spec_id = #{specId} "
            + "ORDER BY sort_order ASC, id ASC")
    List<ShopProductSpecValue> selectBySpecId(@Param("specId") String specId);

    @Select("SELECT * FROM ck_shop_product_spec_value WHERE del_flag = 0 AND product_id = #{productId} "
            + "ORDER BY sort_order ASC, id ASC")
    List<ShopProductSpecValue> selectByProductId(@Param("productId") String productId);

    /*
     * 这里刻意**不提供** purge（物理清除软删行）方法：
     * 规格值决定 SKU 的含义，订单/售后可能需要追溯，del_flag=1 的行永久保留。
     */
}
