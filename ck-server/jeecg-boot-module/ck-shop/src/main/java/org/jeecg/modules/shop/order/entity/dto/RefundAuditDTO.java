package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 退款审核入参。
 * </p>
 *
 * <p>审核是**两步式**的（审核通过 → 提交渠道退款），而不是"点通过就退钱"：
 * 渠道退款可能失败/超时，必须能表达"已批准但退款失败"这个中间状态，
 * 并支持重试。否则一次渠道抖动就会让这笔退款永远消失。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "退款审核入参")
public class RefundAuditDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "退款单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refundNo;

    @Schema(description = "是否通过", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean approved;

    @Schema(description = "审核备注")
    private String auditRemark;

    /**
     * 拒绝原因。
     * 拒绝时**必填**：退款被拒却没有任何理由，用户会反复提交同一笔申请，
     * 客服的工作量会成倍增加，投诉也无法定位。
     */
    @Schema(description = "拒绝原因(拒绝时必填)")
    private String rejectReason;
}
