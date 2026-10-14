package org.jeecg.modules.shop.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;
import org.jeecg.modules.shop.order.entity.dto.OrderPayDTO;
import org.jeecg.modules.shop.order.entity.vo.OrderPrepayVO;
import org.jeecg.modules.shop.order.service.IShopPaymentService;
import org.jeecg.modules.shop.order.service.OrderContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 店铺端（前台）支付接口。
 * </p>
 *
 * <h3>本期是"模拟支付"，但预留了完整接入位</h3>
 * <ol>
 *   <li>{@code POST /pay} 发起支付 —— 创建支付单并调渠道预支付。当前默认渠道是
 *       {@code MOCK}，接入真实渠道后只需把 {@code ck.shop.pay.default-channel}
 *       改成 {@code WECHAT}/{@code ALIPAY}，并新增对应的 {@code PaymentGateway} 实现，
 *       **本 Controller 与订单侧代码一行都不用改**。</li>
 *   <li>{@code POST /pay/mock-confirm} 模拟"用户完成付款" —— 仅 MOCK 渠道可用。
 *       它不做任何业务处理，而是让模拟渠道生成一条**已签名**的回调，
 *       再走与真实渠道完全相同的回调链路（验签 → 去重 → 状态机 → 资金流水 → 库存实扣）。
 *       这样整条支付链路在开发阶段就被反复执行，接入真渠道时不是第一次运行。</li>
 *   <li>{@code GET /pay/status} 查询支付状态 —— 前端支付页轮询用。
 *       渠道回调可能延迟，前端不能"跳转回来就认为已支付"。</li>
 *   <li>{@code POST /pay/query} 主动向渠道查询（用户手动"我已支付，刷新一下"）。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Tag(name = "店铺端-支付")
@RestController
@RequestMapping("/shop/order/store/pay")
@Slf4j
public class ShopStorePaymentController {

    @Autowired
    private IShopPaymentService paymentService;

    @Autowired
    private OrderRequestContextResolver contextResolver;

    @Operation(summary = "店铺端-发起支付")
    @PostMapping(value = "/prepay")
    public Result<OrderPrepayVO> prepay(@RequestBody OrderPayDTO dto, HttpServletRequest req) {
        if (dto == null) {
            return Result.error("支付参数缺失");
        }
        OrderContext context = contextResolver.build(req, false);
        return Result.OK(paymentService.prepay(context, dto.getOrderNo(), dto.getChannel(),
                dto.getPayType(), dto.getRequestId()));
    }

    @Operation(summary = "店铺端-模拟支付确认(仅模拟渠道，点击即付款成功)")
    @PostMapping(value = "/mock-confirm")
    public Result<Map<String, Object>> mockConfirm(@RequestBody OrderPayDTO dto, HttpServletRequest req) {
        if (dto == null || dto.getOrderNo() == null) {
            return Result.error("缺少支付单号");
        }
        OrderContext context = contextResolver.build(req, false);
        // 前端传的是订单号，这里换成支付单号：优先取该订单当前待支付的支付单
        List<ShopOrderPayment> settleable = findSettleableByOrderNo(dto.getOrderNo(), context);
        if (settleable.isEmpty()) {
            return Result.error("该订单没有待支付的支付单，可能已完成支付或已关闭");
        }
        ShopOrderPayment payment = settleable.get(0);
        boolean ok = paymentService.mockConfirm(payment.getPaymentNo(), context);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentNo", payment.getPaymentNo());
        data.put("paid", ok);
        return Result.OK(ok ? "模拟支付成功" : "模拟支付未完成", data);
    }

    @Operation(summary = "店铺端-支付状态查询(前端支付页轮询)")
    @GetMapping(value = "/status")
    public Result<Map<String, Object>> status(String orderNo, HttpServletRequest req) {
        OrderContext context = contextResolver.build(req, false);
        List<ShopOrderPayment> payments = paymentService.listByOrderId(
                requireOrderId(orderNo, context));
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ShopOrderPayment payment : payments) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("paymentNo", payment.getPaymentNo());
            row.put("channel", payment.getChannel());
            row.put("status", payment.getStatus());
            row.put("amount", payment.getAmount());
            row.put("paidAmount", payment.getPaidAmount());
            row.put("payTime", payment.getPayTime());
            row.put("channelTradeNo", payment.getChannelTradeNo());
            list.add(row);
        }
        data.put("payments", list);
        data.put("paid", payments.stream().anyMatch(p -> "SUCCESS".equals(p.getStatus())));
        return Result.OK(data);
    }

    @Operation(summary = "店铺端-主动查询渠道支付结果(用户点击'我已支付')")
    @PostMapping(value = "/query")
    public Result<Map<String, Object>> query(@RequestBody OrderPayDTO dto, HttpServletRequest req) {
        if (dto == null) {
            return Result.error("参数缺失");
        }
        OrderContext context = contextResolver.build(req, false);
        List<ShopOrderPayment> settleable = findSettleableByOrderNo(dto.getOrderNo(), context);
        boolean settled = false;
        for (ShopOrderPayment payment : settleable) {
            if (paymentService.queryAndSettle(payment, context.getUsername())) {
                settled = true;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("settled", settled);
        data.put("message", settled
                ? "已确认支付成功"
                : "渠道尚未确认支付结果。若您已付款，请稍候片刻后再次刷新；"
                + "系统也会自动补单，确认后会更新订单状态");
        return Result.OK(data);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 按订单号取该用户在该店铺下"待支付"的支付单。
     *
     * <p>走订单归属校验再取支付单，而不是直接按订单号查支付单：
     * 后者只要猜到一个订单号就能看到别人的支付单号与渠道流水号。
     */
    private List<ShopOrderPayment> findSettleableByOrderNo(String orderNo, OrderContext context) {
        return paymentService.listSettleable(requireOrderId(orderNo, context));
    }

    private String requireOrderId(String orderNo, OrderContext context) {
        // 通过订单服务取订单（带归属校验），再把 id 交给支付服务
        org.jeecg.modules.shop.order.entity.ShopOrder order = orderService.getByOrderNo(orderNo);
        if (order == null
                || !context.getUserId().equals(order.getUserId())
                || !context.getStoreId().equals(order.getStoreId())) {
            throw new org.jeecg.common.exception.JeecgBootException("订单不存在或无权操作");
        }
        return order.getId();
    }

    @Autowired
    private org.jeecg.modules.shop.order.service.IShopOrderService orderService;
}
