package org.jeecg.modules.shop.product.entity.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.product.entity.ShopProduct;

import java.util.List;

/**
 * <p>
 * 商品列表 VO（管理端列表页使用）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class ProductListVO extends ShopProduct {
    /**
     * 商品名称（默认语言，缺失回退首个非空标题）
     */
    private String title;
    /**
     * 默认语言封面
     */
    private String coverUrl;
    /**
     * 分类内部名称
     */
    private String categoryName;
    /**
     * 关联标签
     */
    private List<TagRefVO> tags;
}
