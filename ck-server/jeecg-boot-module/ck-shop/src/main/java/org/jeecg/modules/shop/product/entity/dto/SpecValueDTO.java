package org.jeecg.modules.shop.product.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.product.entity.ShopProductSpecValue;

/**
 * <p>
 * 规格可选取值 DTO
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class SpecValueDTO extends ShopProductSpecValue {
    /**
     * 前端临时主键（新增规格值时由前端生成），仅用于组合与规格值之间的引用匹配，不落库
     */
    private String tempId;
}
