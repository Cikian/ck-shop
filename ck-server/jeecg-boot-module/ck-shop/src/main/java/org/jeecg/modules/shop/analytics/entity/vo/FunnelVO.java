package org.jeecg.modules.shop.analytics.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * 转化漏斗（/funnel）。步骤顺序固定 visit→add_cart→order→paid。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Accessors(chain = true)
@Schema(description = "转化漏斗")
public class FunnelVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "漏斗步骤(顺序 visit→add_cart→order→paid)")
    private List<FunnelStepVO> steps;
}
