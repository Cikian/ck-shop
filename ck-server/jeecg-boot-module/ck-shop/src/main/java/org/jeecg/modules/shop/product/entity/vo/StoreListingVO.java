package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 商品上架到某个店铺的渠道参数（管理端回显用）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品-店铺渠道参数")
public class StoreListingVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "店铺ID")
    private String storeId;

    @Schema(description = "店铺编码")
    private String storeCode;

    @Schema(description = "店铺名称")
    private String storeName;

    @Schema(description = "本店分类")
    private String categoryId;

    @Schema(description = "本店售价")
    private BigDecimal priceOverride;

    @Schema(description = "本店是否上架")
    private String status;

    @Schema(description = "本店排序")
    private Integer sortOrder;

    @Schema(description = "本店是否推荐")
    private String isRecommend;
}
