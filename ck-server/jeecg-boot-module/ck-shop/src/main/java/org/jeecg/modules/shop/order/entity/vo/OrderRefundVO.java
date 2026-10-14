package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.modules.shop.order.entity.ShopOrderRefund;
import org.jeecg.modules.shop.order.entity.ShopOrderRefundItem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 退款单出参。
 * </p>
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "退款单")
public class OrderRefundVO extends ShopOrderRefund implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "退款状态中文")
    private String statusLabel;

    @Schema(description = "退款类型中文")
    private String refundTypeLabel;

    @Schema(description = "退款明细")
    private List<ShopOrderRefundItem> items = new ArrayList<>();
}
