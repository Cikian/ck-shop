package org.jeecg.modules.shop.product.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 商品列表查询条件
 * 显式声明筛选字段，避免把 keyword / tagId / pageNo / pageSize 等非实体字段
 * 混进 QueryGenerator 的自动条件拼装里
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@Schema(description = "商品列表查询条件")
public class ProductQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "SPU编码，精确匹配")
    private String spuCode;

    @Schema(description = "状态: N-下架, Y-上架")
    private String status;

    @Schema(description = "分类ID")
    private String categoryId;

    @Schema(description = "品牌ID")
    private String brandId;

    @Schema(description = "是否推荐: N-否, Y-是")
    private String isRecommend;

    @Schema(description = "商品名称关键字，按所有语言的标题模糊匹配")
    private String keyword;

    @Schema(description = "标签ID")
    private String tagId;
    /**
     * 销售渠道筛选：只查"上架到该店铺"的商品。
     * 传了就按店铺维度分页（走 ck_shop_product_store 的连接查询），不传则是全量商品视角。
     */
    private String storeId;
}
