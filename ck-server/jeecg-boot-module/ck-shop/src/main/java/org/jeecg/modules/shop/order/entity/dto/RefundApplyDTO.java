package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 退款申请入参。
 * </p>
 *
 * <h3>金额从哪来</h3>
 * 请求体里**没有退款金额字段**。退款金额由服务端按"订单行的成交价 × 退款数量
 * − 该行已分摊的优惠"计算，并受"累计退款 ≤ 实收金额"的约束。
 * 让客服填金额是"退款金额填错"这类事故的直接来源，而且几乎无法事后发现
 * （少了没人投诉，多了就是资金损失）。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "退款申请入参")
public class RefundApplyDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderNo;

    /**
     * 幂等键：用户在"申请退款"上连点两次只产生一张退款单。
     * 同样是必填——后端不提供"没传就自动生成"的兜底，那等于放弃幂等。
     */
    @Schema(description = "幂等键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestId;

    @Schema(description = "退款商品明细；不传表示整单全退")
    private List<RefundItemDTO> items;

    @Schema(description = "申请原因(字典 shop_refund_reason)")
    private String reason;

    @Schema(description = "申请说明")
    private String description;

    @Schema(description = "凭证图片URL列表")
    private List<String> images;
}
