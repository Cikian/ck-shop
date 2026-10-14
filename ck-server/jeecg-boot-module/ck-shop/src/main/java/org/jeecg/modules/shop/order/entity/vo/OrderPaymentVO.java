package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 支付单出参。
 * </p>
 *
 * 前台也能看到自己的支付单（"我这笔到底付出去没有、渠道流水号是多少"），
 * 因此这里**不含任何渠道密钥类信息**；报文 {@code lastNotifyBody} 已在落库前脱敏。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "支付单")
public class OrderPaymentVO extends ShopOrderPayment implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "支付单状态中文")
    private String statusLabel;

    /**
     * 剩余可退金额 = 实收金额 − 累计已退。
     *
     * <p>服务端算好下发。这个数直接决定客服能退多少，
     * 让前端自己减很容易因为四舍五入或读到的不是最新值而算错。
     */
    @Schema(description = "剩余可退金额")
    private BigDecimal refundableAmount;
}
