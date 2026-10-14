package org.jeecg.modules.shop.order.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * 前台订单列表项。
 * </p>
 *
 * <b>显式声明字段而不是继承 {@code ShopOrder}</b>：订单实体里有
 * {@code costAmount}（成本合计）与 {@code grossProfit}（毛利）。
 * 直接返回实体等于把成本结构发给每一个买家——
 * 竞品下一单就能知道你的进货价和毛利率。
 * 用独立 VO 把字段列出来，是唯一能在编译期杜绝"手滑把敏感字段带出去"的办法。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
@Schema(description = "前台订单列表项")
public class StoreOrderListVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "订单状态")
    private String orderStatus;

    @Schema(description = "订单状态中文")
    private String orderStatusLabel;

    @Schema(description = "支付状态")
    private String payStatus;

    @Schema(description = "退款状态")
    private String refundStatus;

    @Schema(description = "店铺名称")
    private String storeName;

    @Schema(description = "应付金额")
    private BigDecimal payAmount;

    @Schema(description = "已支付金额")
    private BigDecimal paidAmount;

    @Schema(description = "累计已退款金额")
    private BigDecimal refundAmount;

    @Schema(description = "优惠合计")
    private BigDecimal discountAmount;

    @Schema(description = "运费")
    private BigDecimal freightAmount;

    @Schema(description = "商品总件数")
    private Integer totalQuantity;

    @Schema(description = "商品种类数")
    private Integer itemCount;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "下单时间")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "支付截止时间")
    private Date payDeadline;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "支付时间")
    private Date payTime;

    @Schema(description = "剩余支付秒数(待支付时有值, 前端倒计时用)")
    private Long remainPaySeconds;

    @Schema(description = "物流公司")
    private String logisticsCompany;

    @Schema(description = "物流单号")
    private String logisticsNo;

    @Schema(description = "取消原因")
    private String cancelReason;

    @Schema(description = "收货人姓名")
    private String receiverName;

    @Schema(description = "收货人手机号")
    private String receiverPhone;

    @Schema(description = "买家备注")
    private String buyerRemark;

    @Schema(description = "商品明细")
    private List<StoreOrderItemVO> items = new ArrayList<>();

    @Schema(description = "是否已归档")
    private Boolean archived;

    /** 用于列表页的"可执行操作"提示，避免前端重复实现状态判断逻辑 */
    @Schema(description = "可支付")
    private Boolean canPay;

    @Schema(description = "可取消")
    private Boolean canCancel;

    @Schema(description = "可确认收货")
    private Boolean canReceive;

    @Schema(description = "可申请退款")
    private Boolean canRefund;
}
