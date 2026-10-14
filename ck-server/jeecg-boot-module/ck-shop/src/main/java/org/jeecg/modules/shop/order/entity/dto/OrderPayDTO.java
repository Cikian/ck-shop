package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 发起支付入参。
 * </p>
 *
 * <b>注意这里没有金额字段</b>：支付金额永远取自订单的 {@code pay_amount}，
 * 而不是客户端传什么就收什么。历史上大量"1 分钱买手机"的事故都源于
 * 支付接口信任了客户端传来的金额。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "发起支付入参")
public class OrderPayDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderNo;

    @Schema(description = "支付渠道: MOCK(默认)/WECHAT/ALIPAY")
    private String channel;

    @Schema(description = "支付方式细分: JSAPI/NATIVE/H5/APP")
    private String payType;

    /**
     * 发起支付的幂等键。
     * 不传时服务端用"订单号 + 当前待支付支付单"兜底，保证不会因为连点产生多笔支付单。
     */
    @Schema(description = "幂等键(可选)")
    private String requestId;
}
