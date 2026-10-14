package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 后台修改卖家备注入参。
 * </p>
 *
 * <p>备注是**订单的**信息，不是订单行的，因此单独一个接口，并且会写操作流水——
 * 客服改了备注却在流水里查不到是谁改的，等于没有审计。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "修改卖家备注入参")
public class OrderRemarkDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderNo;

    @Schema(description = "卖家备注")
    private String sellerRemark;
}
