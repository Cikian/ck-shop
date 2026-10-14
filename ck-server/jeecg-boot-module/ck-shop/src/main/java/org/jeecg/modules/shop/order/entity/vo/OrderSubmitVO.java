package org.jeecg.modules.shop.order.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 下单结果。
 * </p>
 *
 * <p>刻意**不返回订单详情**：下单成功后前端要做的就是"跳到支付页"，
 * 返回一大坨明细既浪费带宽，也会让前端把下单接口当详情接口用。
 * 这里只给"跳转与展示付款倒计时"真正需要的几个字段。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
@Schema(description = "下单结果")
public class OrderSubmitVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "订单状态")
    private String orderStatus;

    @Schema(description = "订单状态中文")
    private String orderStatusLabel;

    @Schema(description = "应付金额")
    private BigDecimal payAmount;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "支付截止时间(前端倒计时依据)")
    private Date payDeadline;

    @Schema(description = "剩余支付秒数")
    private Long remainPaySeconds;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "下单时间")
    private Date createTime;

    /**
     * 是否命中幂等（本次请求没有新建订单，返回的是之前那一笔）。
     *
     * <p>下发给前端的价值：前端可以据此提示"订单已提交，正在跳转"，
     * 而不是显示"下单成功"两次让用户以为买了两单。
     */
    @Schema(description = "是否命中幂等(复用了已存在的订单)")
    private Boolean idempotentReused;
}
