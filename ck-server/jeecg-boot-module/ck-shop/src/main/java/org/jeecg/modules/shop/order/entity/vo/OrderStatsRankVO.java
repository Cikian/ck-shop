package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 订单排行（热销商品 / SKU）。
 * </p>
 *
 * <p>统计口径基于**订单明细快照**，因此即使商品后来被改名、改价、甚至删除，
 * 排行里的名称与金额仍然是当时的真实值。
 * 如果 join 商品表取名称，就会出现"上个月的热销榜里商品名变成了这个月的新名字"。
 *
 * <p>只统计**已支付**订单的明细（{@code WAIT_SHIP / SHIPPED / COMPLETED}）：
 * 待支付订单还没成交，算进销量会让运营做出错误的补货决策。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
@Schema(description = "订单排行项")
public class OrderStatsRankVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "排名")
    private Integer rank;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "SKU组合ID")
    private String skuId;

    @Schema(description = "商品名称(快照)")
    private String productName;

    @Schema(description = "规格文案(快照)")
    private String skuName;

    @Schema(description = "商品主图(快照)")
    private String productImage;

    @Schema(description = "分类名称(快照)")
    private String categoryName;

    @Schema(description = "销量(件)")
    private Long quantity;

    @Schema(description = "订单数")
    private Long orderCount;

    @Schema(description = "销售额(实付)")
    private BigDecimal salesAmount;

    @Schema(description = "毛利")
    private BigDecimal grossProfit;
}
