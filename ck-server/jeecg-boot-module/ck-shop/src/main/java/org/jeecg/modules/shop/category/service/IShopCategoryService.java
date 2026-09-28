package org.jeecg.modules.shop.category.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.category.entity.ShopCategory;
import org.jeecg.modules.shop.category.entity.dto.CategoryDTO;

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
}
