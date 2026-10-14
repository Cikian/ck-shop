package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 后台订单详情。
 * </p>
 *
 * <h3>"完整可追溯"的落地</h3>
 * 后台详情把与这张订单有关的一切摊开：商品快照、金额构成、成本与毛利、
 * 收货地址快照、支付单（含渠道流水号）、退款单（含审批与渠道退款流水号）、
 * 以及**全部操作流水**（含后台备注、人工补单等内部操作）。
 *
 * <p>这是"数据可追溯"要求的最终交付物：任何一笔订单出问题，
 * 在这个接口里都能找到"谁、什么时候、做了什么、前后状态是什么"。
 * 材料不全的订单详情，等于事故复盘时只能靠猜。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "后台订单详情")
public class AdminOrderDetailVO extends AdminOrderListVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "支付单列表(含渠道流水号)")
    private List<OrderPaymentVO> payments = new ArrayList<>();

    @Schema(description = "退款单列表(含审批与渠道退款流水号)")
    private List<OrderRefundVO> refunds = new ArrayList<>();

    @Schema(description = "完整操作流水(含后台内部操作)")
    private List<OrderEventVO> events = new ArrayList<>();

    @Schema(description = "完整收货地址JSON快照")
    private String addressSnapshotJson;
}
