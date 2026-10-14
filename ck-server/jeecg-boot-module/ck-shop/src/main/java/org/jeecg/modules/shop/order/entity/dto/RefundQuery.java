package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>后台退款单查询条件。</p>
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "退款单查询条件")
public class RefundQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "页码(从1开始)")
    private Integer pageNo = 1;

    @Schema(description = "每页条数")
    private Integer pageSize = 10;

    @Schema(description = "退款单号")
    private String refundNo;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "退款状态: APPLYING/REJECTED/REFUNDING/SUCCESS/FAILED/CLOSED")
    private String status;

    @Schema(description = "按店铺筛选")
    private String storeId;

    /**
     * 申请时间区间：用 String 而不是 {@code java.util.Date}。
     * 原因见 {@code OrderQuery.startTime} 的注释——Spring 默认不为 Date 注册
     * String→Date 格式化器，写 Date 会导致"只有带时间筛选的请求 400"。
     */
    @Schema(description = "申请开始时间(yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss)")
    private String startTime;

    @Schema(description = "申请结束时间(按给定精度包含)")
    private String endTime;

    public void setStartTime(String startTime) {
        this.startTime = trimToNull(startTime);
    }

    public void setEndTime(String endTime) {
        this.endTime = trimToNull(endTime);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
