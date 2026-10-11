package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 店铺端 SKU 组合 VO
 * 说明：只返回"存在"的组合（即管理端保存过的笛卡尔积行）。
 * 不存在的组合不会出现在列表中，店铺端据此把对应的规格值置灰不可选。
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@Schema(description = "店铺端SKU组合")
public class StoreSkuVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "组合ID")
    private String id;

    @Schema(description = "SKU编码")
    private String skuCode;

    @Schema(description = "组合唯一键（规格值ID按 specs 顺序以 - 连接）")
    private String combinationKey;

    @Schema(description = "该组合包含的规格值ID，与 specs 数组顺序一致")
    private List<String> specValueIds;

    @Schema(description = "组合文案，如 白色/8G/256G")
    private String combinationName;

    @Schema(description = "SKU售价，为空表示回退商品基准价")
    private BigDecimal price;

    @Schema(description = "最终生效价格（已回退计算）")
    private BigDecimal effectivePrice;

    @Schema(description = "库存")
    private Integer stock;

    @Schema(description = "SKU配图")
    private String imageUrl;

    @Schema(description = "商家标记是否可售: Y-可售, N-不可售")
    private String saleable;
}
