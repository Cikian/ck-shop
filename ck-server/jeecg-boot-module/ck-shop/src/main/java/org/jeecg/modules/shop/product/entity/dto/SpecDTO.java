package org.jeecg.modules.shop.product.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.product.entity.ShopProductSpec;

import java.util.List;

/**
 * <p>
 * 规格定义 DTO（规格 + 可选取值）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class SpecDTO extends ShopProductSpec {
    /**
     * 规格可选取值，listOrder 即数组顺序，前端拖拽调整后按新顺序提交
     */
    private List<SpecValueDTO> values;
}
