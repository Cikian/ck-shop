package org.jeecg.modules.shop.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;
import org.jeecg.modules.shop.order.entity.dto.OrderPayDTO;
import org.jeecg.modules.shop.order.service.IShopPaymentService;
import org.jeecg.modules.shop.order.service.OrderContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 店铺端「模拟支付确认」接口 —— <b>只在 dev 环境注册</b>。
 * </p>
 *
 * <h3>为什么单独一个 Controller</h3>
 * 这个接口的效果是"点一下就付款成功"：任何登录买家调它，订单立刻变已支付并触发库存实扣。
 * 它属于开发期工具，不属于业务能力。之前它挂在 {@link ShopStorePaymentController} 上，
 * 只靠方法内的渠道判断兜底——生产环境一旦 MOCK 渠道 Bean 存在，就是一个 0 元购入口。
 *
 * <p>现在用 {@code @Profile("dev")} + {@code @ConditionalOnProperty} 双重条件：
 * 非 dev 环境下这个 Bean 根本不存在，路由不注册，请求得到 404（而不是"接口存在但拒绝执行"）。
 * 同时 {@code MockPayGuard} 会在启动时做自检，防止注解被人误删。
 *
 * <p>接口行为与之前完全一致：不做任何"假业务处理"，而是让模拟渠道生成一条
 * <b>已签名</b>的回调，再走与真实渠道完全相同的链路
 * （验签 → 去重 → 状态机 → 资金流水 → 库存实扣）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Tag(name = "店铺端-模拟支付(仅dev)")
@RestController
@RequestMapping("/shop/order/store/pay")
@Profile("dev")
@ConditionalOnProperty(name = "ck.shop.pay.mock.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class ShopStoreMockPayController {

    @Autowired
    private IShopPaymentService paymentService;

    @Autowired
    private OrderRequestContextResolver contextResolver;

    @Autowired
    private StorePaymentOrderResolver orderResolver;

    @Operation(summary = "店铺端-模拟支付确认(仅dev，点击即付款成功)")
    @PostMapping(value = "/mock-confirm")
    public Result<Map<String, Object>> mockConfirm(@RequestBody OrderPayDTO dto, HttpServletRequest req) {
        if (dto == null || dto.getOrderNo() == null) {
            return Result.error("缺少支付单号");
        }
        OrderContext context = contextResolver.build(req, false);
        // 前端传的是订单号，这里换成支付单号：取该订单当前待支付的支付单
        List<ShopOrderPayment> settleable = paymentService.listSettleable(
                orderResolver.requireOrderId(dto.getOrderNo(), context));
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
}
