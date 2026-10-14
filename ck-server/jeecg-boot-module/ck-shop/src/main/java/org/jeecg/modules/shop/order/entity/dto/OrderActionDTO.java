package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 通用"按订单号操作"入参（取消 / 确认收货 / 关闭）。
 * </p>
 *
 * 统一用 {@code orderNo} 而不是 {@code orderId} 作为对外标识：
 * 订单号是业务唯一键，客服、用户、日志里流转的都是它；
 * 而主键 ID 是内部实现细节，暴露出去只会让人把它当成可猜的编号。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "订单操作入参")
public class OrderActionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderNo;

    @Schema(description = "原因/备注")
    private String reason;
}
