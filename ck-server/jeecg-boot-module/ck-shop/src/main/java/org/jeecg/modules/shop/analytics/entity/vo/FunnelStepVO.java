package org.jeecg.modules.shop.analytics.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 漏斗步骤（/funnel）。{@code rate} 为"相对上一环节转化率"，字符串百分数数值。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Accessors(chain = true)
@Schema(description = "漏斗步骤")
public class FunnelStepVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "步骤标识: visit/add_cart/order/paid")
    private String step;

    @Schema(description = "步骤展示名")
    private String label;

    @Schema(description = "数量")
    private Long count;

    @Schema(description = "相对上一环节转化率(%)")
    private String rate;
}
