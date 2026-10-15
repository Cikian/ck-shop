package org.jeecg.modules.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.constant.FundBizType;
import org.jeecg.modules.shop.order.constant.OperatorType;
import org.jeecg.modules.shop.order.constant.OrderEventType;
import org.jeecg.modules.shop.order.constant.PayChannel;
import org.jeecg.modules.shop.order.constant.PayStatus;
import org.jeecg.modules.shop.order.constant.PaymentStatus;
import org.jeecg.modules.shop.order.constant.RefundOrderStatus;
import org.jeecg.modules.shop.order.constant.ShopOrderConst;
import org.jeecg.modules.shop.order.entity.ShopFundFlow;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.ShopOrderItem;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;
import org.jeecg.modules.shop.order.entity.ShopOrderRefund;
import org.jeecg.modules.shop.order.entity.ShopPaymentNotifyLog;
import org.jeecg.modules.shop.order.entity.vo.OrderPrepayVO;
import org.jeecg.modules.shop.order.mapper.ShopFundFlowMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderItemMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderPaymentMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderRefundMapper;
import org.jeecg.modules.shop.order.mapper.ShopPaymentNotifyLogMapper;
import org.jeecg.modules.shop.order.pay.PaymentGateway;
import org.jeecg.modules.shop.order.pay.PaymentGatewayRegistry;
import org.jeecg.modules.shop.order.pay.mock.MockPaySupport;
import org.jeecg.modules.shop.order.pay.model.PaymentModels;
import org.jeecg.modules.shop.order.service.IShopOrderEventService;
import org.jeecg.modules.shop.order.service.IShopPaymentService;
import org.jeecg.modules.shop.order.service.IShopRefundService;
import org.jeecg.modules.shop.order.service.IShopStockService;
import org.jeecg.modules.shop.order.service.OrderContext;
import org.jeecg.modules.shop.order.util.MoneyUtils;
import org.jeecg.modules.shop.order.util.OrderNoGenerator;
import org.jeecg.modules.shop.order.util.ShopMaskUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 支付服务实现 —— 资金链路的全部核心逻辑。
 * </p>
 *
 * <h3>本类承担的三件难事</h3>
 * <ol>
 *   <li><b>重复回调</b>：渠道会重推 5~8 次。三道防线：回调流水的唯一去重键、
 *       支付单状态 CAS、资金流水的唯一索引。任何一道单独都不够可靠。</li>
 *   <li><b>掉单（支付成功但订单没动）</b>：订单状态用 CAS 更新，
 *       更新不到就说明订单已被关单/取消。此时**绝不丢弃这笔钱**：
 *       支付单照常置为成功、资金流水照常入账、操作流水写明异常，
 *       留给后台人工介入（退款或补单）。</li>
 *   <li><b>金额不一致</b>：渠道实付金额与订单应付不一致时，
 *       以渠道金额记账（钱确实收了），但**同时写一条异常事件并打 ERROR**，
 *       因为这种情况几乎必然是价格被篡改或算价 bug，必须有人查。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@Service
public class ShopPaymentServiceImpl
        extends ServiceImpl<ShopOrderPaymentMapper, ShopOrderPayment>
        implements IShopPaymentService {

    @Autowired
    private ShopPaymentSupport paymentSupport;

    private ShopOrderPaymentMapper paymentMapper;

    @Autowired
    private ShopOrderMapper orderMapper;

    @Autowired
    private ShopOrderItemMapper orderItemMapper;

    @Autowired
    private ShopOrderRefundMapper refundMapper;

    @Autowired
    private ShopPaymentNotifyLogMapper notifyLogMapper;

    @Autowired
    private ShopFundFlowMapper fundFlowMapper;

    @Autowired
    private PaymentGatewayRegistry gatewayRegistry;

    @Autowired
    private IShopStockService stockService;

    @Autowired
    private IShopOrderEventService eventService;

    /**
     * 退款结果落地**统一走退款服务**。
     *
     * <p>回调与主动查询两条路径如果各写一份"退款成功之后做什么"，
     * 迟早会分叉 —— 然后就会出现"回调进来时库存回补了、查询进来时没回补"
     * 这种对不上的情况。因此这里只做转发。
     */
    @Autowired
    private IShopRefundService refundService;

    /** 默认支付渠道。接入真实渠道后应改为 WECHAT 或 ALIPAY */
    @Value("${ck.shop.pay.default-channel:MOCK}")
    private String defaultChannel;

    /** 支付超时时间（分钟）：与订单的 pay_deadline 保持一致 */
    @Value("${ck.shop.order.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

    // ==================================================================
    // 发起支付
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderPrepayVO prepay(OrderContext context, String orderNo, String channel,
                               String payType, String requestId) {
        if (context == null || StringUtils.isBlank(context.getUserId())) {
            throw new JeecgBootException("请先登录后再支付");
        }
        ShopOrder order = requireOwnedOrder(context, orderNo);

        if (!"WAIT_PAY".equals(order.getOrderStatus())) {
            throw new JeecgBootException("订单当前状态为[" + order.getOrderStatus()
                    + "]，无需支付。请刷新页面查看最新状态");
        }
        if (order.getPayDeadline() != null && order.getPayDeadline().before(new Date())) {
            // 已过期：这里先不直接关单（关单要走带渠道确认的流程），
            // 但必须拒绝支付，否则用户付了钱而定时任务马上把单关掉
            throw new JeecgBootException("订单已超过支付时限，请重新下单");
        }

        BigDecimal amount = MoneyUtils.scale(order.getPayAmount());
        if (!MoneyUtils.isPositive(amount)) {
            throw new JeecgBootException("订单应付金额为 0，无需支付");
        }

        String targetChannel = StringUtils.defaultIfBlank(channel, defaultChannel).toUpperCase();
        PaymentGateway gateway = gatewayRegistry.require(targetChannel);

        // 幂等：同一订单已有未结算的支付单就复用它，绝不产生第二笔有效支付单
        ShopOrderPayment existed = findPendingPayment(order.getId(), targetChannel);
        ShopOrderPayment payment;
        boolean reused = existed != null;
        if (reused) {
            payment = existed;
        } else {
            payment = createPayment(context, order, targetChannel, payType, requestId);
        }

        PaymentModels.PrepayResult prepayResult = gateway.prepay(new PaymentModels.PrepayRequest()
                .setPaymentNo(payment.getPaymentNo())
                .setOrderNo(order.getOrderNo())
                .setAmount(amount)
                .setCurrency(order.getCurrency())
                .setSubject("订单 " + order.getOrderNo())
                .setDescription("店铺：" + order.getStoreName())
                .setPayType(StringUtils.defaultIfBlank(payType, payment.getPayType()))
                .setExpireTime(order.getPayDeadline())
                .setClientIp(context.getClientIp()));

        if (!prepayResult.isSuccess()) {
            // 渠道下单失败：把刚才创建的支付单置为 FAILED（复用已有支付单时不动它），
            // 让用户可以直接重试，而不是留下一笔永远 PENDING 的单拖着补单任务
            if (!reused) {
                paymentMapper.markTerminal(payment.getId(), PaymentStatus.FAILED.name(),
                        java.util.Arrays.asList(PaymentStatus.INIT.name(), PaymentStatus.PENDING.name()),
                        prepayResult.getErrorCode(), prepayResult.getErrorMsg(), "SYSTEM");
            }
            eventService.recordNote(order, OrderEventType.PAY_FAIL, OperatorType.SYSTEM,
                    "SYSTEM", "发起支付失败：" + prepayResult.getErrorMsg(), null);
            throw new JeecgBootException("发起支付失败：" + StringUtils.defaultIfBlank(
                    prepayResult.getErrorMsg(), "请稍后重试或更换支付方式"));
        }

        // 记录渠道流水号与状态
        ShopOrderPayment patch = new ShopOrderPayment()
                .setId(payment.getId())
                .setChannelTradeNo(prepayResult.getChannelTradeNo())
                .setStatus(PaymentStatus.PENDING.name())
                .setPayType(StringUtils.defaultIfBlank(payType, payment.getPayType()))
                .setUpdateTime(new Date());
        paymentMapper.updateById(patch);

        // 订单上记下"最近一笔支付单"，方便从订单直达支付单排查
        orderMapper.update(new ShopOrder()
                        .setPaymentNo(payment.getPaymentNo())
                        .setPayChannel(targetChannel)
                        .setUpdateTime(new Date()),
                new LambdaUpdateWrapper<ShopOrder>()
                        .eq(ShopOrder::getId, order.getId())
                        .isNull(ShopOrder::getPaymentNo));

        eventService.recordNote(order, OrderEventType.PAY_REQUEST, OperatorType.USER,
                context.getUsername(),
                "发起支付，渠道=" + targetChannel + "，金额=" + amount.toPlainString(),
                null);

        return new OrderPrepayVO()
                .setPaymentNo(payment.getPaymentNo())
                .setOrderNo(order.getOrderNo())
                .setChannel(targetChannel)
                .setPayType(patch.getPayType())
                .setAmount(amount)
                .setStatus(PaymentStatus.PENDING.name())
                .setExpireTime(payment.getExpireTime())
                .setRemainPaySeconds(paymentSupport.remainSeconds(payment.getExpireTime()))
                .setPayParams(prepayResult.getPayParams());
    }

    private ShopOrderPayment createPayment(OrderContext context, ShopOrder order, String channel,
                                          String payType, String requestId) {
        Date now = new Date();
        Date expire = order.getPayDeadline() != null ? order.getPayDeadline()
                : new Date(now.getTime() + payTimeoutMinutes * 60_000L);
        ShopOrderPayment payment = new ShopOrderPayment()
                .setPaymentNo(OrderNoGenerator.paymentNo())
                .setOrderId(order.getId())
                .setOrderNo(order.getOrderNo())
                .setUserId(order.getUserId())
                .setStoreId(order.getStoreId())
                .setStoreOrgCode(order.getStoreOrgCode())
                .setChannel(channel)
                .setPayType(StringUtils.trimToNull(payType))
                .setAmount(MoneyUtils.scale(order.getPayAmount()))
                .setPaidAmount(MoneyUtils.zero())
                .setRefundAmount(MoneyUtils.zero())
                .setCurrency(StringUtils.defaultIfBlank(order.getCurrency(), "CNY"))
                .setStatus(PaymentStatus.INIT.name())
                .setExpireTime(expire)
                .setNotifyCount(0)
                .setRequestId(StringUtils.trimToNull(requestId))
                .setVersion(0)
                .setDelFlag(0)
                .setCreateBy(context.getUsername())
                .setCreateTime(now)
                .setUpdateBy(context.getUsername())
                .setUpdateTime(now);
        paymentMapper.insert(payment);
        return payment;
    }

    private ShopOrderPayment findPendingPayment(String orderId, String channel) {
        return paymentMapper.selectOne(new LambdaQueryWrapper<ShopOrderPayment>()
                .eq(ShopOrderPayment::getOrderId, orderId)
                .eq(ShopOrderPayment::getChannel, channel)
                .in(ShopOrderPayment::getStatus,
                        java.util.Arrays.asList(PaymentStatus.INIT.name(), PaymentStatus.PENDING.name()))
                .orderByDesc(ShopOrderPayment::getCreateTime)
                .last("LIMIT 1"));
    }

    // ==================================================================
    // 回调处理
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotifyHandleResult handleNotify(String channel, Map<String, String> headers,
                                           String body, String clientIp) {
        PaymentGateway gateway = gatewayRegistry.require(channel);
        PaymentModels.NotifyResult notify =
                gateway.parseNotify(new PaymentModels.NotifyRequest()
                        .setHeaders(headers)
                        .setBody(body)
                        .setClientIp(clientIp));

        if (!notify.isSignatureValid()) {
            // 验签失败：落一条流水（内容不可信，只留摘要）后直接拒绝。
            // 绝不能"先看看内容再说"——伪造回调正是这样被放行的。
            saveNotifyLog(channel, "UNKNOWN", null, null, null,
                    "REJECTED|" + paymentSupport.hashValue(body), body, paymentSupport.resolveSignature(headers), null, false,
                    "REJECTED", notify.getFailReason(), null, clientIp);
            log.warn("渠道[{}]回调验签失败，已拒绝：{}，clientIp={}", channel, notify.getFailReason(), clientIp);
            return NotifyHandleResult.reject(gateway.failAck("SIGN_INVALID"),
                    "验签失败：" + notify.getFailReason());
        }

        String notifyType = StringUtils.defaultIfBlank(notify.getNotifyType(), "PAY");
        // 去重键必须包含渠道+事件类型+渠道流水号+事件状态+事件时间+我方单号
        String dedupKey = channel + "|" + notify.dedupKey();

        ShopPaymentNotifyLog notifyLog = saveNotifyLog(channel, notifyType, notify.getPaymentNo(),
                notify.getRefundNo(), notify.getChannelTradeNo(), dedupKey, body,
                paymentSupport.resolveSignature(headers), notify, true, "RECEIVED", null,
                notify.getEventTime(), clientIp);
        if (notifyLog == null) {
            // 唯一键冲突 = 重复回调。这是正常现象（渠道本来就会重推），
            // 必须返回成功应答，否则渠道会一直重推形成风暴
            log.info("渠道[{}]重复回调已忽略：dedupKey={}", channel, dedupKey);
            return NotifyHandleResult.ok(gateway.successAck(), "重复回调，已忽略");
        }

        try {
            if ("REFUND".equalsIgnoreCase(notifyType)) {
                return handleRefundNotify(gateway, notify, notifyLog, clientIp);
            }
            return handlePayNotify(gateway, notify, notifyLog, clientIp);
        } catch (Exception e) {
            // 处理失败：更新回调流水的处理结果，并把失败原因回给渠道（渠道会重推，正是我们需要的）
            updateNotifyLog(notifyLog, "FAILED", "处理异常：" + e.getMessage());
            log.error("渠道[{}]回调处理异常：paymentNo={}, err={}",
                    channel, notify.getPaymentNo(), e.getMessage(), e);
            return NotifyHandleResult.reject(gateway.failAck("PROCESS_ERROR"), e.getMessage());
        }
    }

    private NotifyHandleResult handlePayNotify(PaymentGateway gateway,
                                               PaymentModels.NotifyResult notify,
                                               ShopPaymentNotifyLog notifyLog,
                                               String clientIp) {
        ShopOrderPayment payment = getByPaymentNo(notify.getPaymentNo());
        if (payment == null) {
            // 我方无此支付单，但渠道说有这笔交易 —— 典型的对账差异来源。
            // 必须留下证据（回调流水已落库），并**返回成功应答**：
            // 反复重推只会刷屏，这条记录交给人工/对账处理更有价值。
            updateNotifyLog(notifyLog, "FAILED", "支付单不存在：" + notify.getPaymentNo());
            log.error("【对账差异】渠道回调的支付单在我方不存在：channelTradeNo={}, paymentNo={}。"
                            + "请人工核对是否出现【渠道有流水、平台无订单】的情况",
                    notify.getChannelTradeNo(), notify.getPaymentNo());
            return NotifyHandleResult.ok(gateway.successAck(), "支付单不存在，已记录待人工核对");
        }

        recordNotifyOnPayment(payment, notify);

        // 累计回调次数：偏高说明回调链路有异常
        if (!PaymentStatus.SUCCESS.name().equals(notify.getEventStatus())) {
            // 渠道明确告知失败/关闭：只改支付单，不动订单（订单仍可继续支付）
            if (PaymentStatus.FAILED.name().equalsIgnoreCase(notify.getEventStatus())) {
                paymentMapper.markTerminal(payment.getId(), PaymentStatus.FAILED.name(),
                        java.util.Arrays.asList(PaymentStatus.INIT.name(), PaymentStatus.PENDING.name()),
                        "CHANNEL_FAIL", "渠道通知支付失败", "CHANNEL");
            }
            updateNotifyLog(notifyLog, "PROCESSED", "事件状态=" + notify.getEventStatus());
            return NotifyHandleResult.ok(gateway.successAck(), "已记录非成功事件");
        }

        boolean settled = settleSuccess(payment, notify.getChannelTradeNo(),
                notify.getPaidAmount(), notify.getEventTime(), OperatorType.CHANNEL,
                "CHANNEL:" + payment.getChannel(), "渠道回调支付成功");
        updateNotifyLog(notifyLog, "PROCESSED", settled ? "支付成功已结算" : "重复回调，状态已是成功");
        return NotifyHandleResult.ok(gateway.successAck(),
                settled ? "支付成功" : "重复回调，已忽略");
    }

    private NotifyHandleResult handleRefundNotify(PaymentGateway gateway,
                                                  PaymentModels.NotifyResult notify,
                                                  ShopPaymentNotifyLog notifyLog,
                                                  String clientIp) {
        if (StringUtils.isBlank(notify.getRefundNo())) {
            updateNotifyLog(notifyLog, "FAILED", "退款回调缺少退款单号");
            return NotifyHandleResult.ok(gateway.successAck(), "退款回调缺少退款单号，已记录");
        }
        ShopOrderRefund refund = refundMapper.selectOne(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getRefundNo, notify.getRefundNo())
                .last("LIMIT 1"));
        if (refund == null) {
            updateNotifyLog(notifyLog, "FAILED", "退款单不存在：" + notify.getRefundNo());
            log.error("【对账差异】渠道退款回调的退款单在我方不存在：refundNo={}", notify.getRefundNo());
            return NotifyHandleResult.ok(gateway.successAck(), "退款单不存在，已记录待人工核对");
        }

        if (RefundOrderStatus.SUCCESS.name().equalsIgnoreCase(notify.getEventStatus())) {
            // 幂等：已成功的退款单再收到回调直接 ACK
            if (RefundOrderStatus.SUCCESS.name().equals(refund.getStatus())) {
                updateNotifyLog(notifyLog, "DUPLICATE", "退款已是成功状态");
                return NotifyHandleResult.ok(gateway.successAck(), "重复回调，已忽略");
            }
            refundService.applyChannelRefundResult(refund, true, notify.getChannelTradeNo(),
                    notify.getEventTime(), null, "CHANNEL:" + paymentSupport.channelOfRefund(refund));
            updateNotifyLog(notifyLog, "PROCESSED", "退款成功已结算");
            return NotifyHandleResult.ok(gateway.successAck(), "退款成功");
        }

        if (RefundOrderStatus.FAILED.name().equalsIgnoreCase(notify.getEventStatus())) {
            // 退款失败：退款服务会把先前预占的额度还回去，否则额度会被永久占用
            refundService.applyChannelRefundResult(refund, false, null, null,
                    notify.getFailReason(), "CHANNEL:" + paymentSupport.channelOfRefund(refund));
            updateNotifyLog(notifyLog, "PROCESSED", "退款失败，额度已归还");
            return NotifyHandleResult.ok(gateway.successAck(), "退款失败已记录");
        }

        updateNotifyLog(notifyLog, "PROCESSED", "未识别的退款事件状态=" + notify.getEventStatus());
        return NotifyHandleResult.ok(gateway.successAck(), "已记录");
    }

    /** 退款回调里没有渠道字段，从支付单反查（仅用于操作者标识） */

    // ==================================================================
    // 结算：把"资金事实"落到订单
    // ==================================================================

    /**
     * 完成一次支付成功的结算。
     *
     * <p>四步，顺序不可颠倒：
     * <ol>
     *   <li><b>支付单 CAS</b>：只有从 INIT/PENDING 才能变为 SUCCESS。
     *       影响 0 行 = 已经是成功状态 = 重复回调，直接返回，不做后续任何动作
     *       （否则会重复写资金流水、重复扣库存）；</li>
     *   <li><b>订单 CAS</b>：WAIT_PAY → WAIT_SHIP。影响 0 行说明订单已被关单/取消，
     *       这就是掉单场景 —— 钱要照收、账要照记，但订单不能凭空复活，
     *       留给后台人工处理；</li>
     *   <li><b>库存实扣</b>（仅订单推进成功时）；</li>
     *   <li><b>资金流水 + 操作流水</b>，两者都靠唯一索引/事务保证只写一次。</li>
     * </ol>
     */
    private boolean settleSuccess(ShopOrderPayment payment, String channelTradeNo,
                                  BigDecimal paidAmount, Date payTime,
                                  String operatorType, String operatorName, String remark) {
        Date now = new Date();
        Date actualPayTime = payTime == null ? now : payTime;
        BigDecimal actualPaid = MoneyUtils.scale(paidAmount);
        if (!MoneyUtils.isPositive(actualPaid)) {
            // 渠道回调和查询都可能没带金额；此时以支付单上的应付额为准，
            // 而不是记 0——记 0 会让对账"渠道有流水、我方金额为 0"
            actualPaid = MoneyUtils.scale(payment.getAmount());
        }

        int paymentRows = paymentMapper.markPaid(payment.getId(), channelTradeNo,
                actualPaid, actualPayTime, operatorName);
        if (paymentRows == 0) {
            // 已经是成功状态：重复回调。直接 ACK，不做任何后续动作
            log.info("支付单[{}]已是成功状态，跳过重复结算", payment.getPaymentNo());
            return false;
        }

        ShopOrder order = orderMapper.selectById(payment.getOrderId());
        if (order == null) {
            log.error("【数据异常】支付成功但订单不存在：paymentNo={}, orderId={}",
                    payment.getPaymentNo(), payment.getOrderId());
            writeFundFlow(payment, actualPaid, actualPayTime, operatorName, "收款（订单缺失，待人工核对）"
            );
            return true;
        }

        // 金额一致性核对：不一致几乎必然是算价 bug 或价格篡改，必须留下痕迹让人查
        if (!MoneyUtils.eq(payment.getAmount(), actualPaid)) {
            log.error("【金额异常】渠道实付金额与支付单应付金额不一致："
                            + "paymentNo={}, 应付={}, 实付={}。以渠道金额记账，请人工核对价格计算逻辑。",
                    payment.getPaymentNo(), payment.getAmount(), actualPaid);
            eventService.recordNote(order, OrderEventType.PAY_QUERY, OperatorType.SYSTEM, "SYSTEM",
                    "【金额异常】应付 " + MoneyUtils.scale(payment.getAmount()).toPlainString()
                            + " 元，渠道实付 " + actualPaid.toPlainString() + " 元，已按渠道金额记账，请人工核对",
                    null);
        }

        int orderRows = orderMapper.update(new ShopOrder()
                        .setOrderStatus(org.jeecg.modules.shop.order.constant.OrderStatus.WAIT_SHIP.name())
                        .setPayStatus(PayStatus.PAID.name())
                        .setPaidAmount(actualPaid)
                        .setPaymentNo(payment.getPaymentNo())
                        .setPayChannel(payment.getChannel())
                        .setPayType(payment.getPayType())
                        .setPayTime(actualPayTime)
                        .setUpdateBy(operatorName)
                        .setUpdateTime(now),
                new LambdaUpdateWrapper<ShopOrder>()
                        .eq(ShopOrder::getId, order.getId())
                        .eq(ShopOrder::getOrderStatus,
                                org.jeecg.modules.shop.order.constant.OrderStatus.WAIT_PAY.name())
                        .eq(ShopOrder::getVersion, order.getVersion())
                        .setSql("version = version + 1"));

        // 资金流水无论如何都要写：钱已经到账了，这是唯一的资金事实记录
        writeFundFlow(payment, actualPaid, actualPayTime, operatorName, "支付收款");

        if (orderRows == 0) {
            // ---- 掉单场景：钱收了，订单却已不在待支付 ----
            // 绝不"把钱退回去"或"把订单改回待支付"：前者需要用户同意，
            // 后者会让已释放的库存凭空占用。正确做法是留下异常记录交人工处理。
            log.error("【掉单告警】支付成功但订单状态已不是待支付：orderNo={}, 当前状态={}, paymentNo={}。"
                            + "该笔资金已入账，请立即人工介入（退款或恢复订单）。",
                    order.getOrderNo(), order.getOrderStatus(), payment.getPaymentNo());
            ShopOrder latest = orderMapper.selectById(order.getId());
            eventService.recordStatusChange(latest, OrderEventType.PAY_SUCCESS,
                    order.getOrderStatus(), order.getOrderStatus(),
                    order.getPayStatus(), PayStatus.PAID.name(),
                    operatorType, operatorName,
                    "【掉单异常】支付成功时订单已处于[" + order.getOrderStatus() + "]，"
                            + "资金已入账但订单未推进，需人工介入",
                    actualPaid,
                    "{\"channelTradeNo\":\"" + StringUtils.trimToEmpty(channelTradeNo) + "\"}");
            return true;
        }

        // 订单推进成功 → 库存实扣
        List<ShopOrderItem> items = orderItemMapper.selectByOrderIds(
                java.util.Collections.singletonList(order.getId()));
        try {
            stockService.deduct(order.getOrderNo(), items, payment.getPaymentNo(), operatorName);
        } catch (Exception e) {
            // 库存扣减失败会抛异常并回滚整个事务（含支付单成功状态）——
            // 这是刻意的：宁可让回调失败被渠道重推，也不能出现"收了钱没扣库存"的超卖。
            log.error("支付成功后扣减库存失败，事务将回滚并等待渠道重推：orderNo={}, err={}",
                    order.getOrderNo(), e.getMessage());
            throw e;
        }

        ShopOrder paid = orderMapper.selectById(order.getId());
        eventService.recordStatusChange(paid, OrderEventType.PAY_SUCCESS,
                order.getOrderStatus(), paid.getOrderStatus(),
                order.getPayStatus(), paid.getPayStatus(),
                operatorType, operatorName, remark, actualPaid,
                "{\"paymentNo\":\"" + payment.getPaymentNo() + "\",\"channelTradeNo\":\""
                        + StringUtils.trimToEmpty(channelTradeNo) + "\"}");
        return true;
    }

    private void writeFundFlow(ShopOrderPayment payment, BigDecimal amount, Date tradeTime,
                               String operator, String remark) {
        ShopFundFlow flow = new ShopFundFlow()
                .setFlowNo(OrderNoGenerator.fundFlowNo())
                .setBizType(FundBizType.PAY_IN)
                .setOrderNo(payment.getOrderNo())
                .setPaymentNo(payment.getPaymentNo())
                .setStoreId(payment.getStoreId())
                .setStoreOrgCode(payment.getStoreOrgCode())
                .setUserId(payment.getUserId())
                .setChannel(payment.getChannel())
                .setChannelTradeNo(payment.getChannelTradeNo())
                .setAmount(MoneyUtils.scale(amount))
                .setDirection(FundBizType.DIRECTION_IN)
                .setCurrency(payment.getCurrency())
                .setStatus("SUCCESS")
                .setTradeTime(tradeTime)
                .setSyncTime(new Date())
                .setRemark(remark)
                .setCreateTime(new Date());
        insertFundFlowSafely(flow);
    }

    /**
     * 写资金流水。
     *
     * <p>唯一索引 {@code uk_fund_biz(biz_type, channel, channel_trade_no)} 是最后一道防线：
     * 业务幂等一旦因为新回调路径而失效，重复入账会被数据库挡住。
     * 捕获冲突后**不抛异常**——那意味着这笔钱已经记过账了，正是我们想要的结果。
     */
    private void insertFundFlowSafely(ShopFundFlow flow) {
        try {
            fundFlowMapper.insert(flow);
        } catch (DuplicateKeyException e) {
            log.warn("资金流水已存在，跳过重复入账：bizType={}, channelTradeNo={}",
                    flow.getBizType(), flow.getChannelTradeNo());
        }
    }

    // ==================================================================
    // 主动查询（补单）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean queryAndSettle(ShopOrderPayment payment, String operator) {
        if (payment == null) {
            return false;
        }
        PaymentGateway gateway = gatewayRegistry.require(payment.getChannel());
        PaymentModels.QueryResult result;
        try {
            result = gateway.query(new PaymentModels.QueryRequest()
                    .setPaymentNo(payment.getPaymentNo())
                    .setChannelTradeNo(payment.getChannelTradeNo()));
        } catch (Exception e) {
            // 渠道查询本身失败（网络抖动）：本轮不处理，下一轮再试。
            // **绝不能**因为"查不到"就关单——那正是掉单的来源。
            log.warn("主动查询渠道失败，本轮跳过：paymentNo={}, err={}",
                    payment.getPaymentNo(), e.getMessage());
            return false;
        }
        return settleFromQueryResult(payment, result, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean settleFromQueryResult(ShopOrderPayment payment, PaymentModels.QueryResult result,
                                        String operator) {
        if (result == null || StringUtils.isBlank(result.getTradeStatus())) {
            return false;
        }
        if (PaymentModels.QueryResult.SUCCESS.equals(result.getTradeStatus())) {
            BigDecimal paid = result.getPaidAmount() != null
                    ? result.getPaidAmount() : payment.getAmount();
            return settleSuccess(payment, StringUtils.defaultIfBlank(
                            result.getChannelTradeNo(), payment.getChannelTradeNo()),
                    paid, result.getPayTime(), OperatorType.SYSTEM,
                    StringUtils.defaultIfBlank(operator, "SYSTEM"), "主动查询确认支付成功");
        }
        if (PaymentModels.QueryResult.FAILED.equals(result.getTradeStatus())) {
            paymentMapper.markTerminal(payment.getId(), PaymentStatus.FAILED.name(),
                    java.util.Arrays.asList(PaymentStatus.INIT.name(), PaymentStatus.PENDING.name()),
                    result.getErrorCode(), StringUtils.defaultIfBlank(result.getErrorMsg(), "渠道查询为失败"),
                    operator);
        }
        // PENDING / NOT_FOUND：**不做任何处置**。
        // NOT_FOUND 也不等于失败（渠道入账可能延迟），必须等超时关单流程来兜底，
        // 而超时关单又会再查一次。把"查不到"当失败会导致真实的掉单。
        return false;
    }

    // ==================================================================
    // 关闭
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closePayment(ShopOrderPayment payment, String reason, String operator) {
        if (payment == null || PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            return false;
        }
        if (PaymentStatus.CLOSED.name().equals(payment.getStatus())) {
            return true;
        }
        PaymentGateway gateway = gatewayRegistry.require(payment.getChannel());

        // 关本地之前先向渠道确认：渠道说已支付就**不关**，返回 false 让调用方转成功处理。
        // 这一步是"超时关单 + 最后一秒支付"这类掉单的正面防线。
        try {
            PaymentModels.QueryResult query = gateway.query(new PaymentModels.QueryRequest()
                    .setPaymentNo(payment.getPaymentNo())
                    .setChannelTradeNo(payment.getChannelTradeNo()));
            if (query != null && PaymentModels.QueryResult.SUCCESS.equals(query.getTradeStatus())) {
                log.warn("准备关闭支付单[{}]时渠道查询返回已支付，改为按支付成功处理（避免掉单）",
                        payment.getPaymentNo());
                settleFromQueryResult(payment, query, operator);
                return false;
            }
        } catch (Exception e) {
            log.warn("关闭支付单前查询渠道失败，将按本地状态关闭：paymentNo={}, err={}",
                    payment.getPaymentNo(), e.getMessage());
        }

        boolean channelClosed = gateway.close(new PaymentModels.CloseRequest()
                .setPaymentNo(payment.getPaymentNo())
                .setChannelTradeNo(payment.getChannelTradeNo())
                .setReason(reason));
        if (!channelClosed) {
            // 渠道侧关不掉：记 ERROR 但**仍然关闭本地单**。
            // 本地不关会让订单永远卡在待支付，而渠道侧那张单最坏的结果是
            // 用户后来付成功 → 走掉单告警流程人工处理，比"永远卡住"可控。
            log.error("渠道侧关闭支付单失败：paymentNo={}，本地仍将关闭，需关注是否出现掉单",
                    payment.getPaymentNo());
        }
        paymentMapper.markTerminal(payment.getId(), PaymentStatus.CLOSED.name(),
                java.util.Arrays.asList(PaymentStatus.INIT.name(), PaymentStatus.PENDING.name()),
                null, StringUtils.defaultIfBlank(reason, "订单关闭"), operator);
        return true;
    }

    // ==================================================================
    // 模拟支付
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean mockConfirm(String paymentNo, OrderContext context) {
        ShopOrderPayment payment = getByPaymentNo(paymentNo);
        if (payment == null) {
            throw new JeecgBootException("支付单不存在");
        }
        if (context != null && StringUtils.isNotBlank(context.getUserId())
                && !context.getUserId().equals(payment.getUserId())) {
            // 越权校验：不能替别人"确认支付"
            throw new JeecgBootException("支付单不存在或无权操作");
        }
        if (!PayChannel.MOCK.equalsIgnoreCase(payment.getChannel())) {
            throw new JeecgBootException("当前支付渠道[" + payment.getChannel()
                    + "]不支持模拟支付，请使用真实渠道完成付款");
        }
        PaymentGateway gateway = gatewayRegistry.require(payment.getChannel());
        if (!(gateway instanceof MockPaySupport)) {
            throw new JeecgBootException("当前支付渠道不支持模拟支付");
        }
        MockPaySupport mock = (MockPaySupport) gateway;
        // 关键：让模拟渠道生成**已签名**的回调，再走与真实渠道完全相同的处理链路。
        // 这样"验签 → 去重 → 状态机 → 资金流水 → 库存实扣"在开发期就被真实执行。
        MockPaySupport.SignedNotify signed = mock.buildSignedNotify(
                payment.getPaymentNo(), payment.getChannelTradeNo(),
                MoneyUtils.scale(payment.getAmount()), "SUCCESS");
        NotifyHandleResult result = handleNotify(payment.getChannel(), signed.getHeaders(),
                signed.getBody(), context == null ? null : context.getClientIp());
        if (!result.isAccepted()) {
            throw new JeecgBootException("模拟支付失败：" + result.getMessage());
        }
        return true;
    }

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public List<ShopOrderPayment> listByOrderId(String orderId) {
        return paymentSupport.listByOrderId(orderId);
    }

    @Override
    public List<ShopOrderPayment> listSettleable(String orderId) {
        return paymentSupport.listSettleable(orderId);
    }

    @Override
    public ShopOrderPayment findRefundablePayment(String orderId) {
        return paymentSupport.findRefundablePayment(orderId);
    }

    @Override
    public BigDecimal refundableAmount(ShopOrderPayment payment) {
        return paymentSupport.refundableAmount(payment);
    }

    @Override
    public ShopOrderPayment getByPaymentNo(String paymentNo) {
        return paymentSupport.findByPaymentNo(paymentNo);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private ShopOrder requireOwnedOrder(OrderContext context, String orderNo) {
        if (StringUtils.isBlank(orderNo)) {
            throw new JeecgBootException("缺少订单号");
        }
        ShopOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo)
                .eq(ShopOrder::getUserId, context.getUserId())
                .eq(ShopOrder::getStoreId, context.getStoreId())
                .last("LIMIT 1"));
        if (order == null) {
            // 不区分"订单不存在"和"不是你的订单"，避免被用来探测订单号
            throw new JeecgBootException("订单不存在或无权操作");
        }
        return order;
    }

    private ShopPaymentNotifyLog saveNotifyLog(String channel, String notifyType, String paymentNo,
                                               String refundNo, String channelTradeNo,
                                               String dedupKey, String body, String signature,
                                               PaymentModels.NotifyResult notify, boolean signVerified,
                                               String processStatus, String processMsg,
                                               Date notifyTime, String clientIp) {
        ShopPaymentNotifyLog entry = new ShopPaymentNotifyLog()
                .setChannel(channel)
                .setNotifyType(notifyType)
                .setPaymentNo(paymentNo)
                .setRefundNo(refundNo)
                .setChannelTradeNo(channelTradeNo)
                .setDedupKey(dedupKey)
                // 报文脱敏后保存：渠道回调里常带 token / openid / 买家标识
                .setRawBody(ShopMaskUtils.maskNotifyBody(body))
                .setSignature(StringUtils.left(signature, 500))
                .setSignVerified(signVerified ? 1 : 0)
                .setProcessStatus(processStatus)
                .setProcessMsg(processMsg)
                .setNotifyTime(notifyTime)
                .setReceiveTime(new Date())
                .setClientIp(clientIp);
        try {
            notifyLogMapper.insert(entry);
            return entry;
        } catch (DuplicateKeyException e) {
            // 唯一键冲突 = 重复回调
            return null;
        }
    }

    private void updateNotifyLog(ShopPaymentNotifyLog entry, String status, String message) {
        if (entry == null) {
            return;
        }
        try {
            notifyLogMapper.update(new ShopPaymentNotifyLog()
                            .setProcessStatus(status)
                            .setProcessMsg(StringUtils.left(message, 500)),
                    new LambdaUpdateWrapper<ShopPaymentNotifyLog>()
                            .eq(ShopPaymentNotifyLog::getId, entry.getId()));
        } catch (Exception e) {
            // 回调流水的状态更新失败不影响主流程（主流程已完成）
            log.warn("更新回调流水状态失败：id={}, err={}", entry.getId(), e.getMessage());
        }
    }

    private void recordNotifyOnPayment(ShopOrderPayment payment, PaymentModels.NotifyResult notify) {
        try {
            paymentMapper.recordNotify(payment.getId(),
                    notify.getEventTime() == null ? new Date() : notify.getEventTime(),
                    ShopMaskUtils.maskNotifyBody(StringUtils.left(
                            notify.getNotifyType() + "|" + notify.getEventStatus() + "|"
                                    + notify.getChannelTradeNo(), 2000)),
                    "CHANNEL");
        } catch (Exception e) {
            log.warn("累加支付单回调次数失败：paymentNo={}, err={}", payment.getPaymentNo(), e.getMessage());
        }
    }


    /**
     * 从回调请求头里取签名原文（落库留证用）。
     *
     * <p>不同渠道的签名头名五花八门（微信 {@code Wechatpay-Signature}、
     * 支付宝 {@code sign}、模拟渠道 {@code mock-signature}），
     * 这里统一识别几种常见写法。取不到不影响主流程——
     * 真正的验签由网关实现负责，这只是给人看的证据。
     */

}
