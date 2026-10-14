package org.jeecg.modules.shop.cart.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 修改购物车数量入参
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "修改购物车数量入参")
public class CartQuantityDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "购物车明细ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemId;

    @Schema(description = "目标数量（>=1）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;

    @Schema(description = "来源")
    private String source;
}
