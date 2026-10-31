package org.jeecg.modules.shop.analytics.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 商品排行项（/rank/product）。比率字段返回字符串"百分数数值、不带 %、保留 2 位"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Accessors(chain = true)
@Schema(description = "商品排行项")
public class RankProductVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "商品标题")
    private String productTitle;

    @Schema(description = "访问量(product_view)")
    private Long viewCount;

    @Schema(description = "会话数")
    private Long visitCount;

    @Schema(description = "平均停留时长(ms)")
    private Long avgDurationMs;

    @Schema(description = "加购数")
    private Long addCartCount;

    @Schema(description = "下单数")
    private Long orderCount;

    @Schema(description = "支付数")
    private Long paidCount;

    @Schema(description = "点击率(%)")
    private String clickRate;

    @Schema(description = "加购率(%)")
    private String addCartRate;

    @Schema(description = "转化率(%)")
    private String conversionRate;
}
