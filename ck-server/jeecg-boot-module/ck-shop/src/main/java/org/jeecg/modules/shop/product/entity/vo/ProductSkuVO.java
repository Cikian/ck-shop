package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * SKU 组合展示 VO（管理端"展开行看库存"使用）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@Schema(description = "SKU组合展示")
public class ProductSkuVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "组合ID")
    private String id;

    @Schema(description = "SKU编码")
    private String skuCode;

    @Schema(description = "组合唯一键")
    private String combinationKey;

    @Schema(description = "组合文案，如 白色/8G/256G")
    private String combinationName;

    @Schema(description = "该组合包含的规格值ID")
    private List<String> specValueIds;

    @Schema(description = "SKU售价，为空表示回退商品基准价")
    private BigDecimal price;

    @Schema(description = "最终生效价格")
    private BigDecimal effectivePrice;

    @Schema(description = "库存")
    private Integer stock;

    @Schema(description = "SKU配图")
    private String imageUrl;

    @Schema(description = "是否可售: Y/N")
    private String saleable;
}
