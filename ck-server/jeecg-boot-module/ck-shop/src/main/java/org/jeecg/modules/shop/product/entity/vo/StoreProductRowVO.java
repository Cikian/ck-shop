package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 店铺内商品分页查询的中间行（product + product_store 的连接结果）
 * </p>
 * 只承载"需要从关系表取出来的字段"，商品本身的字段由后续按 id 批量查询补齐。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "店铺内商品行")
public class StoreProductRowVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "本店售价(空则用商品基准价)")
    private BigDecimal storePrice;

    @Schema(description = "本店分类(空则用商品主分类)")
    private String storeCategoryId;

    @Schema(description = "本店排序")
    private Integer storeSortOrder;
}
