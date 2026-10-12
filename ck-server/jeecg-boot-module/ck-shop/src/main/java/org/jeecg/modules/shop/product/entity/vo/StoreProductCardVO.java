package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 店铺端商品卡片 VO（列表页）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@Schema(description = "店铺端商品卡片")
public class StoreProductCardVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "商品ID，同时作为店铺端 slug")
    private String id;

    @Schema(description = "商品SPU编码")
    private String spuCode;

    @Schema(description = "标题（按当前语言）")
    private String title;

    @Schema(description = "副标题（按当前语言）")
    private String subTitle;

    @Schema(description = "封面（按当前语言，缺失回退商品主图）")
    private String coverUrl;

    @Schema(description = "商品主图")
    private String mainImage;

    @Schema(description = "展示价：优先 minPrice，其次基准价")
    private BigDecimal price;

    @Schema(description = "最低售价")
    private BigDecimal minPrice;

    @Schema(description = "最高售价")
    private BigDecimal maxPrice;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "可售库存合计")
    private Integer stock;

    @Schema(description = "状态: N-下架, Y-上架")
    private String status;

    @Schema(description = "是否推荐: N-否, Y-是")
    private String isRecommend;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "分类ID")
    private String categoryId;

    @Schema(description = "分类名称（按当前语言）")
    private String categoryName;

    @Schema(description = "标签")
    private List<TagRefVO> tags;
}
