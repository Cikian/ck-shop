package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 金额明细（对外出参）。
 * </p>
 *
 * <b>刻意不含成本与毛利</b>：这两个是经营敏感数据，只有后台订单详情才可见。
 * 用两个不同的类型（{@code OrderAmountVO} 与后台 VO 里的字段）把它们隔开，
 * 避免"某个接口顺手复用了这个类，把成本带到了前台"。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
@Schema(description = "金额明细")
public class OrderAmountVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "商品原价合计(吊牌价×数量)")
    private BigDecimal totalAmount;

    @Schema(description = "商品成交金额合计(优惠前)")
    private BigDecimal productAmount;

    @Schema(description = "促销优惠合计")
    private BigDecimal promotionDiscount;

    @Schema(description = "优惠券抵扣")
    private BigDecimal couponDiscount;

    @Schema(description = "积分抵扣")
    private BigDecimal pointsDiscount;

    @Schema(description = "会员优惠")
    private BigDecimal memberDiscount;

    @Schema(description = "优惠合计")
    private BigDecimal discountAmount;

    @Schema(description = "运费")
    private BigDecimal freightAmount;

    @Schema(description = "其它费用")
    private BigDecimal otherFee;

    @Schema(description = "应付金额")
    private BigDecimal payAmount;

    @Schema(description = "商品种类数")
    private Integer itemCount;

    @Schema(description = "商品总件数")
    private Integer totalQuantity;
}
