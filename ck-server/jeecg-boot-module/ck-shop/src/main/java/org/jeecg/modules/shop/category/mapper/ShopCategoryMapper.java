package org.jeecg.modules.shop.category.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.category.entity.ShopCategory;

import java.util.List;

/**
 * <p>
 * 商品分类
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-08-03
 */

@Mapper
public interface ShopCategoryMapper extends BaseMapper<ShopCategory> {

    /**
     * 某店铺**可见**的分类（用于店铺端分类树）。
     *
     * 可见域：全局共享节点（store_id 为空）+ 本店专属节点；
     * 且必须 is_show='Y' 且未删除。
     *
     * 这里刻意用裸 SQL 把三个条件写死，而不是让调用方自己拼 wrapper：
     * "忘了带店铺条件"是多店铺最典型的事故，把它收口在 SQL 里最安全。
     * 索引 idx_ck_shop_category_store_show(store_id, is_show, sort_order) 正好可用。
     */
    @Select("SELECT * FROM ck_shop_category "
            + "WHERE del_flag = 0 AND is_show = 'Y' AND (store_id IS NULL OR store_id = #{storeId}) "
            + "ORDER BY sort_order ASC, id ASC")
    List<ShopCategory> selectVisibleByStore(@Param("storeId") String storeId);

    /**
     * 管理端：按店铺筛选分类（含隐藏的分类，管理员要能看到全部）。
     * storeId 为空表示不按店铺过滤。
     */
    @Select("<script>SELECT * FROM ck_shop_category WHERE del_flag = 0 "
            + "<if test=\"storeId != null and storeId != ''\"> AND (store_id IS NULL OR store_id = #{storeId})</if>"
            + " ORDER BY sort_order ASC, id ASC</script>")
    List<ShopCategory> selectByStoreForAdmin(@Param("storeId") String storeId);
}
