package org.jeecg.modules.shop.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.IpUtils;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.shop.order.service.IShopPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 支付渠道异步回调入口。
 * </p>
 *
 * <h3>这个接口必须 {@code @IgnoreAuth}</h3>
 * 回调来自支付渠道的服务器，不可能带我们的登录态。
 * 因此它的安全**完全依赖验签**，而不是依赖会话：
 * <ol>
 *   <li>{@code PaymentGateway#parseNotify} 先验签再解析内容；</li>
 *   <li>时间戳窗口校验挡住重放；</li>
 *   <li>回调流水的唯一去重键挡住重复处理；</li>
 *   <li>所有状态变更走 CAS，即使被绕过也无法重复扣库存。</li>
 * </ol>
 * 这四道里任何一道失效，都还有其他道兜底——这正是"验签失败绝不信任内容"的意义。
 *
 * <h3>为什么返回 raw 字符串而不是 {@code Result}</h3>
 * 渠道只认它自己的应答格式（微信要 JSON、支付宝要纯文本 success）。
 * 包一层本项目统一的 {@code {code,success,result}} 会被渠道判定为"处理失败"
 * 并无限重推，最终演变成回调风暴。应答格式由网关实现决定，这里原样透传。
 *
 * <h3>为什么永远不抛异常给容器</h3>
 * 抛异常会返回 500，而渠道对 500 的处理是**持续重推**。
 * 但"验签失败"这种情况重推一万次也不会成功（重放攻击），
 * 因此必须返回明确的失败应答让渠道停止（或按其策略记失败），
 * 同时把证据留在 {@code ck_shop_payment_notify_log} 里供排查。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Tag(name = "支付回调")
@RestController
@RequestMapping("/shop/pay/notify")
@Slf4j
public class ShopPaymentNotifyController {

    @Autowired
    private IShopPaymentService paymentService;

    /**
     * 统一回调入口：{@code /shop/pay/notify/{channel}}。
     *
     * <p>用路径变量区分渠道而不是"一个地址按内容猜渠道"：
     * 一个地址意味着要在验签之前先解析报文判断它来自哪个渠道，
     * 而解析不可信内容本身就是风险。让渠道各自拥有一个地址，
     * 一开始就能确定用哪套密钥验签。
     */
    @IgnoreAuth
    @Operation(summary = "支付渠道异步回调(验签+幂等+状态机)")
    @PostMapping(value = "/{channel}")
    public String notify(@PathVariable("channel") String channel,
                         @RequestBody(required = false) String body,
                         HttpServletRequest request) {
        String clientIp = resolveIp(request);
        String rawBody = body;
        if (rawBody == null) {
            // 少数渠道用 form 表单而不是 JSON body：Spring 已经消费掉流时这里兜底
            rawBody = "";
        }
        try {
            IShopPaymentService.NotifyHandleResult result =
                    paymentService.handleNotify(channel, collectHeaders(request), rawBody, clientIp);
            log.info("支付回调处理完成：channel={}, ip={}, accepted={}, msg={}",
                    channel, clientIp, result.isAccepted(), result.getMessage());
            return result.getResponseBody();
        } catch (Exception e) {
            // 走到这里说明连"取网关"都失败了（渠道未接入）。
            // 返回失败应答并把问题暴露在日志里，而不是 500 —— 500 只会招来无限重推。
            log.error("支付回调处理异常：channel={}, ip={}, err={}", channel, clientIp, e.getMessage(), e);
            return "{\"code\":\"FAIL\",\"message\":\"CHANNEL_NOT_SUPPORTED\"}";
        }
    }

    private Map<String, String> collectHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return headers;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    private String resolveIp(HttpServletRequest request) {
        try {
            return IpUtils.getIpAddr(request);
        } catch (Exception e) {
            return request == null ? null : request.getRemoteAddr();
        }
    }
}
