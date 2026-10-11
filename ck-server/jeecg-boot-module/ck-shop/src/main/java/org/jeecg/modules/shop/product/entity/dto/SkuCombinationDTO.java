package org.jeecg.modules.shop.product.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.product.entity.ShopProductSkuCombination;

import java.util.List;

/**
 * <p>
 * SKU 规格组合 DTO
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class SkuCombinationDTO extends ShopProductSkuCombination {
    /**
     * 该组合由哪些规格值构成。
     * 新增规格值时可传前端临时主键（tempId）；已存在的规格值传真实 id。
     * 服务端按 ProductDTO.specs 的顺序解析为 productId / specId / 真实 specValueId / combinationKey。
     */
    private List<String> specValueRefs;
}
