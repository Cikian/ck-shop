package org.jeecg.modules.shop.brand.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.brand.entity.ShopBrand;
import org.jeecg.modules.shop.brand.entity.ShopBrandTranslation;

import java.util.List;

/**
 * <p>
 * 品牌新增&修改 DTO
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class BrandDTO extends ShopBrand {
    /**
     * 多语言文案
     */
    private List<ShopBrandTranslation> translations;
}
