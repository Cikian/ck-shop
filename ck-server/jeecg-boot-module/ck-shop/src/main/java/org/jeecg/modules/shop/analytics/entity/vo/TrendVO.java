package org.jeecg.modules.shop.analytics.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 趋势点（/trend）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Accessors(chain = true)
@Schema(description = "趋势点")
public class TrendVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "时间 yyyy-MM-dd HH:mm:ss(day粒度到天)")
    private String time;

    @Schema(description = "浏览量")
    private Long pv;

    @Schema(description = "去重访客数(hour粒度为估算)")
    private Long uv;

    @Schema(description = "会话数")
    private Long visit;

    @Schema(description = "平均停留时长(ms)")
    private Long avgDurationMs;

    @Schema(description = "加购数")
    private Long addCartCount;

    @Schema(description = "下单数")
    private Long orderCount;

    @Schema(description = "支付数")
    private Long paidCount;
}
