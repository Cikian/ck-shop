package org.jeecg.modules.shop.order.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 发起支付的结果。
 * </p>
 *
 * <h3>payParams 为什么是 Map 而不是结构化字段</h3>
 * 每个渠道调起支付所需的参数完全不同（微信要 {@code prepay_id + paySign}，
 * 支付宝要 {@code orderStr}，H5 要跳转 URL）。把它们统一成固定字段，
 * 每接一个渠道就要改一次前端与后端契约。
 * 这里让**网关实现**决定 payParams 的内容，前端原样透传给渠道 SDK，
 * 于是新增渠道时前后端都不用改。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
@Schema(description = "发起支付结果")
public class OrderPrepayVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "支付单号")
    private String paymentNo;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "支付渠道")
    private String channel;

    @Schema(description = "支付方式")
    private String payType;

    @Schema(description = "应付金额")
    private BigDecimal amount;

    @Schema(description = "支付单状态")
    private String status;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "支付截止时间")
    private Date expireTime;

    @Schema(description = "剩余支付秒数")
    private Long remainPaySeconds;

    /** 调起渠道支付所需参数，由支付网关实现填充，前端原样透传给渠道 SDK */
    @Schema(description = "渠道调起支付参数")
    private Map<String, String> payParams = new HashMap<>();
}
