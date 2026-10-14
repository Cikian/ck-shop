package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 退款申请明细入参。
 * </p>
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "退款申请明细")
public class RefundItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单明细ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderItemId;

    @Schema(description = "退款数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;
}
