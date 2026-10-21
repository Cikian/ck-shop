package org.jeecg.modules.shop.product.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.jeecg.modules.shop.product.entity.ShopProductImage;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * 店铺端商品详情 VO（匿名接口返回体）
 * </p>
 *
 * <h3>为什么不再 {@code extends ShopProduct}</h3>
 * 旧实现直接继承实体后 {@code BeanUtils.copyProperties(product, vo)}，
 * 于是匿名接口把 {@code costPrice（成本价）/ frozenStock（预占库存）/ sysOrgCode（组织编码）/
 * createBy / delFlag / upstreamSource / upstreamUrl} 一起吐给了浏览器。
 * 店铺端本来靠 BFF 的 {@code normalizeDetail} 做字段白名单兜底，
 * 但 Java 接口是 {@code @IgnoreAuth} 且可被直连——**兜底不能当作安全边界**。
 *
 * <p>现在这里只声明"确实要给买家看"的字段（与 BFF 的 StoreProductCard /
 * StoreProductDetail 类型一一对应），实体新增的任何内部字段都不会自动泄露。
 * 新增对外字段时必须在这里显式加一条，这就是这一层唯一的作用。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */
@Data
@Schema(description = "店铺端商品详情（对外白名单字段）")
public class StoreProductDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // 与列表卡片共用的公开字段
    // ------------------------------------------------------------------

    @Schema(description = "商品ID，同时作为店铺端 slug")
    private String id;

    @Schema(description = "商品SPU编码")
    private String spuCode;

    @Schema(description = "商品主图")
    private String mainImage;

    @Schema(description = "展示价：优先 minPrice，其次基准价")
    private BigDecimal price;

    @Schema(description = "划线原价/市场价，NULL=不展示划线")
    private BigDecimal originalPrice;

    @Schema(description = "最低售价")
    private BigDecimal minPrice;

    @Schema(description = "最高售价")
    private BigDecimal maxPrice;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "可售库存合计")
    private Integer stock;

    @Schema(description = "累计销量")
    private Integer salesCount;

    @Schema(description = "近 30 天销量")
    private Integer monthSalesCount;

    @Schema(description = "评价总数")
    private Integer reviewCount;

    @Schema(description = "平均评分 0.0-5.0")
    private BigDecimal ratingAvg;

    @Schema(description = "收藏数")
    private Integer favoriteCount;

    @Schema(description = "是否包邮")
    private Boolean freeShipping;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "上架时间")
    private Date listedAt;

    @Schema(description = "状态: N-下架, Y-上架")
    private String status;

    @Schema(description = "是否推荐: N-否, Y-是")
    private String isRecommend;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "分类ID")
    private String categoryId;

    // ------------------------------------------------------------------
    // 详情页专属字段（按当前语言装配）
    // ------------------------------------------------------------------

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
