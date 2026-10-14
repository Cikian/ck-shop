package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 结算预览入参。
 * </p>
 *
 * 预览与提交用的是**同一套商品明细结构**，且都走同一个算钱服务
 * （{@code OrderAmountCalculator}）。这样"预览看到 100 元、提交变成 120 元"
 * 这种问题不可能发生——两边算的是同一段代码。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "结算预览入参")
public class OrderPreviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "结算商品明细", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<OrderItemDTO> items;
}
