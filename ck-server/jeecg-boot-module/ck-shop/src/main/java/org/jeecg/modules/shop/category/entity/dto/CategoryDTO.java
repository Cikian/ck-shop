package org.jeecg.modules.shop.category.entity.dto;


import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.category.entity.ShopCategory;
import org.jeecg.modules.shop.category.entity.ShopCategoryTranslation;

import java.util.List;

/**
 * <p>
 * 新增&修改DTO
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026/8/3
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class CategoryDTO extends ShopCategory {
    List<ShopCategoryTranslation> translations;
}
