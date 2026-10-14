package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 订单状态分布（饼图）。
 * </p>
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
@Schema(description = "订单状态分布")
public class OrderStatsStatusVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "状态码")
    private String status;

    @Schema(description = "状态中文")
    private String statusLabel;

    @Schema(description = "订单数")
    private Long orderCount;

    @Schema(description = "金额合计(应付)")
    private BigDecimal amount;
}
