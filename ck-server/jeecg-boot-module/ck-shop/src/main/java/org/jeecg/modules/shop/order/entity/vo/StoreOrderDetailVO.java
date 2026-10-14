package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 前台订单详情。
 * </p>
 *
 * 比列表多出的内容：
 * <ul>
 *   <li>完整收货地址（买家本人可见，不做脱敏）；</li>
 *   <li>金额明细（把优惠构成摊开，减少"为什么是这个价"的客服咨询）；</li>
 *   <li>支付单与退款单（用户可以自己看到"退了多少钱、什么时候到账"）；</li>
 *   <li>操作流水（**买家也能看到的关键节点**，例如"系统于 xx 时间自动关闭"）。</li>
 * </ul>
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "前台订单详情")
public class StoreOrderDetailVO extends StoreOrderListVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "省")
    private String receiverProvince;

    @Schema(description = "市")
    private String receiverCity;

    @Schema(description = "区/县")
    private String receiverDistrict;

    @Schema(description = "详细地址")
    private String receiverAddress;

    @Schema(description = "邮编")
    private String receiverPostalCode;

    @Schema(description = "地址标签")
    private String receiverTag;

    @Schema(description = "商品原价合计")
    private BigDecimal totalAmount;

    @Schema(description = "商品成交金额合计")
    private BigDecimal productAmount;

    @Schema(description = "促销优惠")
    private BigDecimal promotionDiscount;

    @Schema(description = "支付渠道")
    private String payChannel;

    @Schema(description = "支付方式")
    private String payType;

    @Schema(description = "发货备注")
    private String shipRemark;

    @Schema(description = "支付单列表")
    private List<OrderPaymentVO> payments = new ArrayList<>();

    @Schema(description = "退款单列表")
    private List<OrderRefundVO> refunds = new ArrayList<>();

    /**
     * 买家可见的操作流水。
     *
     * <p>只暴露"订单生命周期节点"（下单、支付、发货、收货、关闭、退款），
     * 后台备注、人工补单等内部操作**不下发**——
     * 买家看到"客服于 xx 改过备注"只会产生新的疑问。
     */
    @Schema(description = "订单节点时间线(买家可见节点)")
    private List<OrderEventVO> events = new ArrayList<>();
}
