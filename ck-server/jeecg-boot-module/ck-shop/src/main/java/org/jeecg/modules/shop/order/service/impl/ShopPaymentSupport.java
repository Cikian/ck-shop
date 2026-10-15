package org.jeecg.modules.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.shop.order.util.MoneyUtils;
import org.jeecg.modules.shop.order.constant.PaymentStatus;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;
import org.jeecg.modules.shop.order.entity.ShopOrderRefund;
import org.jeecg.modules.shop.order.mapper.ShopOrderPaymentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 支付单的**只读查询与纯函数**支撑组件。
 *
 * <p>为什么单独拆出来：{@code ShopPaymentServiceImpl} 原本 900+ 行，把"改状态/记资金/回调处理"
 * 这类有副作用的逻辑，与"查支付单 / 算可退金额 / 取签名头 / 算哈希 / 算剩余秒数"
 * 这类**无副作用**的逻辑混在一起。这里只搬后者 —— 纯搬运，公共契约不变：
 * {@code IShopPaymentService} 的方法仍由 ShopPaymentServiceImpl 实现，只是委托给本类。
 *
 * <p>本类**不做任何写操作**，也不依赖支付网关；因此可以安全地复用于查询与后台任务。
 */
@Component
public class ShopPaymentSupport {

    @Autowired
    private ShopOrderPaymentMapper paymentMapper;

    /** 按支付单号查（支付单号是业务唯一键） */
    public ShopOrderPayment findByPaymentNo(String paymentNo) {
        if (StringUtils.isBlank(paymentNo)) {
            return null;
        }
        return paymentMapper.selectOne(new LambdaQueryWrapper<ShopOrderPayment>()
                .eq(ShopOrderPayment::getPaymentNo, paymentNo)
                .last("LIMIT 1"));
    }

    /** 某订单的全部支付单，按创建时间升序（同一订单可能先失败后成功） */
    public List<ShopOrderPayment> listByOrderId(String orderId) {
        if (StringUtils.isBlank(orderId)) {
            return new ArrayList<>();
        }
        return paymentMapper.selectList(new LambdaQueryWrapper<ShopOrderPayment>()
                .eq(ShopOrderPayment::getOrderId, orderId)
                .orderByAsc(ShopOrderPayment::getCreateTime));
    }

    /** 可继续结算的支付单（INIT / PENDING） */
    public List<ShopOrderPayment> listSettleable(String orderId) {
        return paymentMapper.selectList(new LambdaQueryWrapper<ShopOrderPayment>()
                .eq(ShopOrderPayment::getOrderId, orderId)
                .in(ShopOrderPayment::getStatus,
                        Arrays.asList(PaymentStatus.INIT.name(), PaymentStatus.PENDING.name())));
    }

    /** 找一张还有可退余额的成功支付单（退款时用） */
    public ShopOrderPayment findRefundablePayment(String orderId) {
        List<ShopOrderPayment> payments = paymentMapper.selectList(new LambdaQueryWrapper<ShopOrderPayment>()
                .eq(ShopOrderPayment::getOrderId, orderId)
                .eq(ShopOrderPayment::getStatus, PaymentStatus.SUCCESS.name())
                .orderByAsc(ShopOrderPayment::getPayTime));
        for (ShopOrderPayment payment : payments) {
            if (MoneyUtils.isPositive(refundableAmount(payment))) {
                return payment;
            }
        }
        return null;
    }

    /**
     * 可退金额。
     *
     * <p>以"实收"为上限而不是"应付"：组合支付或部分到账时，按应付退会退超出实际收到的钱。
     */
    public BigDecimal refundableAmount(ShopOrderPayment payment) {
        if (payment == null) {
            return MoneyUtils.zero();
        }
        BigDecimal paid = MoneyUtils.scale(payment.getPaidAmount());
        if (!MoneyUtils.isPositive(paid)) {
            paid = MoneyUtils.scale(payment.getAmount());
        }
        BigDecimal refundable = MoneyUtils.subtract(paid, payment.getRefundAmount());
        return refundable.compareTo(BigDecimal.ZERO) < 0 ? MoneyUtils.zero() : refundable;
    }

    /** 退款单对应的支付渠道；取不到返回 UNKNOWN */
    public String channelOfRefund(ShopOrderRefund refund) {
        if (refund == null || StringUtils.isBlank(refund.getPaymentId())) {
            return "UNKNOWN";
        }
        ShopOrderPayment payment = paymentMapper.selectById(refund.getPaymentId());
        return payment == null ? "UNKNOWN" : payment.getChannel();
    }

    /** 距离截止还有多少秒（负数归零；无截止时间返回 null） */
    public Long remainSeconds(Date deadline) {
        if (deadline == null) {
            return null;
        }
        long remain = (deadline.getTime() - System.currentTimeMillis()) / 1000L;
        return Math.max(remain, 0L);
    }

    /**
     * 从回调请求头里取签名原文（落库留证用）。
     *
     * <p>不同渠道的签名头名五花八门（微信 {@code Wechatpay-Signature}、支付宝 {@code sign}、
     * 模拟渠道 {@code mock-signature}），这里统一识别几种常见写法。取不到不影响主流程——
     * 真正的验签由网关实现负责，这只是给人看的证据。
     */
    public String resolveSignature(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String[] candidates = {"mock-signature", "wechatpay-signature", "sign",
                "signature", "alipay-signature", "x-signature"};
        for (String candidate : candidates) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && candidate.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /** SHA-256 十六进制（回调去重/留证用的摘要） */
    public String hashValue(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(StringUtils.trimToEmpty(value)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.nanoTime());
        }
    }
}
