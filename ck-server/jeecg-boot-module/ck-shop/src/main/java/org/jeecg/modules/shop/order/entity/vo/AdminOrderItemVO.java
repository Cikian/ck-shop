package org.jeecg.modules.shop.order.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.modules.shop.order.entity.ShopOrderItem;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 后台订单明细（**含成本**）。
 * </p>
 *
 * 继承实体是刻意的：后台是成本与毛利的**唯一**可见方，
 * 而且后台详情需要能下钻到 SKU 编码、分类快照等字段用于核对。
 * 前台用的是另一套 {@code StoreOrderItemVO}，两者靠类型彻底隔离。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "后台订单明细(含成本)")
public class AdminOrderItemVO extends ShopOrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "退款状态中文")
    private String refundStatusLabel;

    /**
     * 本行可退金额 = 实付金额 − 已退金额。
     *
     * <p>由服务端算好下发，而不是让前端自己减：前端算错会导致
     * 客服界面提示"可退 100"，实际提交时被服务端拒成"可退 80"，体验很差。
     */
    @Schema(description = "本行可退金额")
    private java.math.BigDecimal refundableAmount;
}
