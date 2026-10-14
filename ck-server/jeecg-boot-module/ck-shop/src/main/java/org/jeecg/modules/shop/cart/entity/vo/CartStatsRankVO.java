package org.jeecg.modules.shop.cart.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 后台加购统计 · 商品/SKU 热度排行
 * </p>
 *
 * 无规格商品用 {@code skuId = ""} 表示，前端按 {@code skuName} 是否为空决定展示"规格"行。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "加购热度排行")
public class CartStatsRankVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "排名，从 1 开始")
    private Integer rank;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "SKU组合ID；无规格商品为空串")
    private String skuId;

    @Schema(description = "商品标题（按查询语言，缺失回退SPU编码）")
    private String title;

    @Schema(description = "SPU编码")
    private String spuCode;

    @Schema(description = "封面")
    private String coverUrl;

    @Schema(description = "规格文案，如 白色/8G/256G")
    private String skuName;

    @Schema(description = "加购次数")
    private Long addTimes;

    @Schema(description = "加购件数")
    private Long addQuantity;

    @Schema(description = "加购用户数（去重）")
    private Long addUsers;

    @Schema(description = "商品是否仍在售（N 表示已下架，运营需要关注）")
    private String productStatus;

    @Schema(description = "最近一次加购时间")
    private Date lastAddTime;

    @Schema(description = "相对热度：占榜首加购次数的百分比，便于前端画进度条")
    private BigDecimal heat;
}
