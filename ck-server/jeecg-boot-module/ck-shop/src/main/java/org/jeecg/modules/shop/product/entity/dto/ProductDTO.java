package org.jeecg.modules.shop.product.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.ShopProductImage;
import org.jeecg.modules.shop.product.entity.ShopProductSkuCombination;
import org.jeecg.modules.shop.product.entity.ShopProductSpec;
import org.jeecg.modules.shop.product.entity.ShopProductTranslation;

import java.util.List;

/**
 * <p>
 * 商品新增&修改 DTO
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class ProductDTO extends ShopProduct {
    /**
     * 多语言文案
     */
    private List<ShopProductTranslation> translations;
    /**
     * 图集（含主图）
     */
    private List<ShopProductImage> images;
    /**
     * 关联标签ID集合
     */
    private List<String> tagIds;
    /**
     * 规格定义（含可选值）
     */
    private List<SpecDTO> specs;
    /**
     * SKU 规格组合（笛卡尔积的每一行）
     */
    private List<SkuCombinationDTO> combinations;
}
