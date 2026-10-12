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
     * 某店铺（= 某部门）**可见**的分类（用于店铺端分类树）。
     *
     * 可见域收口在 SQL 里、不靠调用方自觉：{@code sys_org_code LIKE 'orgCode%'}
     * 且必须 {@code is_show='Y'} 且未删除。
     * org_code 是前缀式编码，因此"本部门及其下级部门"的分类天然都在可见范围内；
     * "忘了带部门条件"是多店铺最典型的事故，写死在这里最安全。
     * 索引 {@code idx_ck_shop_category_org_show(sys_org_code, is_show, sort_order)} 正好可用。
     */
    @Select("SELECT * FROM ck_shop_category "
            + "WHERE del_flag = 0 AND is_show = 'Y' AND sys_org_code LIKE CONCAT(#{orgCode}, '%') "
            + "ORDER BY sort_order ASC, id ASC")
    List<ShopCategory> selectVisibleByOrg(@Param("orgCode") String orgCode);
}
