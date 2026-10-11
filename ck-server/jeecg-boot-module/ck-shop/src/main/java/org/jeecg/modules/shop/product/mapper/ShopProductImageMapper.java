package org.jeecg.modules.shop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.product.entity.ShopProductImage;

import java.util.List;

/**
 * <p>
 * 商品图集 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Mapper
public interface ShopProductImageMapper extends BaseMapper<ShopProductImage> {

    @Select("SELECT * FROM ck_shop_product_image WHERE del_flag = 0 AND product_id = #{productId} "
            + "ORDER BY sort_order ASC, id ASC")
    List<ShopProductImage> selectByProductId(@Param("productId") String productId);

    /*
     * 这里刻意**不提供** purge（物理清除软删行）方法。
     * 商品图片可能被订单/售后用于追溯，物理删除不可逆；del_flag=1 的行永久保留。
     */
}
