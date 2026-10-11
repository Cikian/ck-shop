package org.jeecg.modules.shop.category.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.category.entity.ShopCategory;
import org.jeecg.modules.shop.category.entity.dto.CategoryDTO;
import org.jeecg.modules.shop.category.entity.vo.StoreCategoryVO;

import java.io.Serializable;
import java.util.Collection;
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

public interface IShopCategoryService extends IService<ShopCategory> {

    boolean save(CategoryDTO dto);

    boolean updateById(CategoryDTO dto);

    boolean removeById(String id);

    boolean removeByIds(List<String> ids);

    CategoryDTO getDtoById(String id);

    /**
     * 店铺端分类树：只返回"该店可见"的节点。
     *
     * 可见域规则写在 SQL 里，不靠调用方自觉：
     * {@code store_id IS NULL}（全局共享）或 {@code store_id = 当前店铺}，
     * 且 {@code is_show = 'Y'}、未删除。
     *
     * 若某节点的父节点不在可见范围内，该节点会被**丢弃**而不是提升为顶级——
     * 否则会出现"某个分类从别的店铺的父分类下冒出来"这种跨店串扰。
     */
    List<StoreCategoryVO> buildStoreTree(String storeId, String lang);
}
