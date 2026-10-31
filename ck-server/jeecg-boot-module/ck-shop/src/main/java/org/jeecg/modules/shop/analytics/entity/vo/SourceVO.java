package org.jeecg.modules.shop.analytics.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 来源分布项（/source）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Accessors(chain = true)
@Schema(description = "来源分布项")
public class SourceVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "来源: direct/search/referral/utm")
    private String source;

    @Schema(description = "UTM媒介")
    private String medium;

    @Schema(description = "UTM活动")
    private String campaign;

    @Schema(description = "浏览量")
    private Long pv;

    @Schema(description = "去重访客数")
    private Long uv;
}
