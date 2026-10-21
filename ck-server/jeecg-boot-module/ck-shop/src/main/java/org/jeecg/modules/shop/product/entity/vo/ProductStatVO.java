package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 商品的**真实统计**聚合结果（销量 / 收藏数 / 评价数与评分）。
 * </p>
 *
 * 为什么单独搞一个 VO，而不是直接读 {@code ck_shop_product} 上的同名列：
 * 那几列是**冗余缓存**，历史上被演示脚本写死过（已售 2068 / 4.5 分 / 2653 收藏全是编的），
 * 只修一次数据并不足以保证它以后不再漂移。店铺端展示必须以**实时聚合**为准：
 * <ul>
 *   <li>销量 = 已支付订单明细件数合计（{@code ck_shop_order_item} ⋈ {@code ck_shop_order}）；</li>
 *   <li>收藏数 = {@code ck_shop_favorite} 未取消的行数；</li>
 *   <li>评价数 / 评分 = {@code ck_shop_product_review} 未隐藏评价的条数与平均值。</li>
 * </ul>
 *
 * 字段名刻意与 {@code StoreProductCardVO / ShopProduct} 里的同名字段一致，
 * 这样"把统计值覆盖到出参上"只是一次属性拷贝，前端解析代码一行都不用改。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Data
@Accessors(chain = true)
@Schema(description = "商品真实统计(销量/收藏/评价)")
public class ProductStatVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "累计销量(已支付订单件数合计)")
    private Integer salesCount;

    @Schema(description = "近30天销量")
    private Integer monthSalesCount;

    @Schema(description = "收藏数(收藏关系表行数)")
    private Integer favoriteCount;

    @Schema(description = "评价数(未隐藏的评价条数)")
    private Integer reviewCount;

    @Schema(description = "平均评分 0.0-5.0，无评价时为 0")
    private BigDecimal ratingAvg;
}
