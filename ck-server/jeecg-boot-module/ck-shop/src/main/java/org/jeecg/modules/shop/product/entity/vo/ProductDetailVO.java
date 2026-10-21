package org.jeecg.modules.shop.product.entity.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.ShopProductImage;
import org.jeecg.modules.shop.product.entity.ShopProductTranslation;
import org.jeecg.modules.shop.product.entity.dto.SpecDTO;

import java.util.List;

/**
 * <p>
 * 商品详情 VO（管理端编辑页使用，一次性返回全部子表）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class ProductDetailVO extends ShopProduct {
    /**
     * 当前语言下的商品名称（从 translations 中按语言取出，缺失回退内部首个非空标题）
     */
    private String title;
    /**
     * 当前语言封面（缺失回退商品主图）
     */
    private String coverUrl;
    /**
     * 分类内部名称
     */
    private String categoryName;
    /**
     * 多语言文案
     */
    private List<ShopProductTranslation> translations;
    /**
     * 图集
     */
    private List<ShopProductImage> images;
    /**
     * 关联标签ID
     */
    private List<String> tagIds;
    /**
     * 关联标签详情
     */
    private List<TagRefVO> tags;
    /**
     * 规格定义（含可选值，按 sortOrder 升序）
     */
    private List<SpecDTO> specs;
    /**
     * SKU 组合（按 combinationKey 升序）
     */
    private List<ProductSkuVO> skus;
    /**
     * 商品详情模块（**全部语言**，按 languageCode、sortOrder 升序；含 id 与 languageCode，
     * 供管理端编辑页按语言回填）
     */
    private List<DetailBlockVO> detailBlocks;
    /**
     * 规格参数（**全部语言**，按 languageCode、sortOrder 升序；含 id 与 languageCode）
     */
    private List<ParamVO> params;
}
