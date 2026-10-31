package org.jeecg.modules.shop.analytics.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 页面排行项（/page）。{@code bounceRate} 为字符串百分数数值。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Accessors(chain = true)
@Schema(description = "页面排行项")
public class PageVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "页面路径")
    private String pagePath;

    @Schema(description = "浏览量")
    private Long pv;

    @Schema(description = "去重访客数")
    private Long uv;

    @Schema(description = "平均停留时长(ms)")
    private Long avgDurationMs;

    @Schema(description = "跳出率(%)")
    private String bounceRate;
}
