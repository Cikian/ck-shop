package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.ShopProductImage;

import java.util.List;

/**
 * <p>
 * 店铺端商品详情 VO
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class StoreProductDetailVO extends ShopProduct {
    @Schema(description = "标题（按当前语言）")
    private String title;

    @Schema(description = "副标题（按当前语言）")
    private String subTitle;

    @Schema(description = "详细描述（按当前语言，富文本 HTML）")
    private String description;

    @Schema(description = "SEO 标题")
    private String seoTitle;

    @Schema(description = "SEO 关键词")
    private String seoKeywords;

    @Schema(description = "SEO 描述")
    private String seoDescription;

    @Schema(description = "封面（按当前语言，缺失回退商品主图）")
    private String coverUrl;

    @Schema(description = "分类名称（按当前语言）")
    private String categoryName;

    @Schema(description = "图集（按 sortOrder 升序）")
    private List<ShopProductImage> images;

    @Schema(description = "标签")
    private List<TagRefVO> tags;

    @Schema(description = "规格定义（按 sortOrder 升序）")
    private List<StoreSpecVO> specs;

    @Schema(description = "存在的 SKU 组合")
    private List<StoreSkuVO> combinations;

    @Schema(description = "商品详情模块（只含当前语言最终生效的数据，已按契约 2.3 整体回退；不含 id/languageCode）")
    private List<StoreDetailBlockVO> detailBlocks;

    @Schema(description = "规格参数（只含当前语言最终生效的数据，已按契约 2.3 整体回退；不含 id/languageCode）")
    private List<StoreParamVO> params;
}
