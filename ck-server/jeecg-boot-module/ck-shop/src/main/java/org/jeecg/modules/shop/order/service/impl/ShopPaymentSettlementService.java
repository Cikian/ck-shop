package org.jeecg.modules.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.shop.order.constant.FundBizType;
import org.jeecg.modules.shop.order.constant.OperatorType;
import org.jeecg.modules.shop.order.constant.OrderEventType;
import org.jeecg.modules.shop.order.constant.OrderStatus;
import org.jeecg.modules.shop.order.constant.PayStatus;
import org.jeecg.modules.shop.order.constant.PaymentStatus;
import org.jeecg.modules.shop.order.constant.RefundOrderStatus;
import org.jeecg.modules.shop.order.entity.ShopFundFlow;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.ShopOrderItem;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;
import org.jeecg.modules.shop.order.entity.ShopOrderRefund;
import org.jeecg.modules.shop.order.mapper.ShopFundFlowMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderItemMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderPaymentMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderRefundMapper;
import org.jeecg.modules.shop.order.pay.model.PaymentModels;
import org.jeecg.modules.shop.order.service.IShopOrderEventService;
import org.jeecg.modules.shop.order.service.IShopRefundService;
import org.jeecg.modules.shop.order.service.IShopStockService;
import org.jeecg.modules.shop.order.util.MoneyUtils;
import org.jeecg.modules.shop.order.util.OrderNoGenerator;
import org.jeecg.modules.shop.order.util.ShopMaskUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * 支付/退款回调的<b>结算</b>：把"渠道资金事实"落到支付单、订单、库存、资金流水上。
 * </p>
 *
 * <h3>为什么必须独立成一个 Bean</h3>
 * 这是修复"回调半完成"的关键。旧实现把结算写在
 * {@code ShopPaymentServiceImpl#handleNotify} 里，而该方法带 {@code @Transactional}、
 * 又在方法内部 {@code catch} 了所有异常：
 * <ul>
 *   <li>异常被吞掉 → 事务提交 → 支付单已成功、订单已改、资金流水已写，
 *       但库存可能没扣（或反过来），形成**半完成状态**；</li>
 *   <li>而回调流水的去重行已经落库 → 渠道重推时被当成"重复回调"直接 ACK →
 *       半完成状态**再也没有机会自愈**。</li>
 * </ul>
 * 现在结算整体搬到这里，每个入口都用 {@link Propagation#REQUIRES_NEW}：
 * <b>结算要么全部提交，要么全部回滚</b>，且与"回调流水"的写入互不牵连——
 * 结算回滚后，外层才能安全地把去重键释放掉，等渠道下次重推重新处理。
 *
 * <h3>为什么 ACK 在事务之外</h3>
 * {@code handleNotify} 会等这里的方法<b>返回（事务已提交）</b>之后才给渠道回成功应答。
 * 如果先 ACK 再提交，一旦提交失败，渠道已经认为"处理成功"不再重推，这笔钱就丢了。
 *
 * <h3>落库顺序不可颠倒</h3>
 * <ol>
 *   <li><b>支付单 CAS</b>：只有从 INIT/PENDING 才能变为 SUCCESS。
 *       影响 0 行 = 已经是成功状态 = 重复回调，直接返回，不做后续任何动作
 *       （否则会重复写资金流水、重复扣库存）；</li>
 *   <li><b>订单 CAS</b>：WAIT_PAY → WAIT_SHIP。影响 0 行说明订单已被关单/取消，
 *       这就是掉单场景 —— 钱要照收、账要照记，但订单不能凭空复活，留给后台人工处理；</li>
 *   <li><b>库存实扣</b>（仅订单推进成功时）；</li>
 *   <li><b>资金流水 + 操作流水</b>，两者都靠唯一索引/事务保证只写一次。</li>
 * </ol>
 *
 * @author Cikian
 * @since 2026-10-22
 */
@Slf4j
@Service
public class ShopPaymentSettlementService {

    @Autowired
    private ShopOrderPaymentMapper paymentMapper;

    @Autowired
    private ShopOrderMapper orderMapper;

    @Autowired
    private ShopOrderItemMapper orderItemMapper;

    @Autowired
    private ShopOrderRefundMapper refundMapper;

    @Autowired
    private ShopFundFlowMapper fundFlowMapper;

    @Autowired
    private IShopStockService stockService;

    @Autowired
    private IShopOrderEventService eventService;

    @Autowired
    private IShopRefundService refundService;

    @Autowired
    private ShopPaymentSupport paymentSupport;

    // ==================================================================
    // 回调结算入口（独立事务）
    // ==================================================================

    /**
     * 支付回调结算。
     *
     * <p>任何异常都必须**向外抛**，由调用方 {@code handleNotify} 捕获后
     * 落 FAILED + 释放去重键 + 回渠道失败应答。这里禁止 catch 后返回"成功"。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Outcome settlePayNotify(PaymentModels.NotifyResult notify) {
        ShopOrderPayment payment = findByPaymentNo(notify.getPaymentNo());
        if (payment == null) {
            // 我方无此支付单，但渠道说有这笔交易 —— 典型的对账差异来源。
            // 必须留下证据（回调流水已落库），并**返回成功应答**：
            // 反复重推只会刷屏，这条记录交给人工/对账处理更有价值。
            log.error("【对账差异】渠道回调的支付单在我方不存在：channelTradeNo={}, paymentNo={}。"
                            + "请人工核对是否出现【渠道有流水、平台无订单】的情况",
                    notify.getChannelTradeNo(), notify.getPaymentNo());
            return Outcome.of("FAILED", "支付单不存在：" + notify.getPaymentNo(),
                    "支付单不存在，已记录待人工核对");
        }

        recordNotifyOnPayment(payment, notify);

        if (!PaymentStatus.SUCCESS.name().equals(notify.getEventStatus())) {
            // 渠道明确告知失败/关闭：只改支付单，不动订单（订单仍可继续支付）
            if (PaymentStatus.FAILED.name().equalsIgnoreCase(notify.getEventStatus())) {
                paymentMapper.markTerminal(payment.getId(), PaymentStatus.FAILED.name(),
                        java.util.Arrays.asList(PaymentStatus.INIT.name(), PaymentStatus.PENDING.name()),
                        "CHANNEL_FAIL", "渠道通知支付失败", "CHANNEL");
            }
            return Outcome.of("PROCESSED", "事件状态=" + notify.getEventStatus(), "已记录非成功事件");
        }

        boolean settled = settleSuccess(payment, notify.getChannelTradeNo(),
                notify.getPaidAmount(), notify.getEventTime(), OperatorType.CHANNEL,
                "CHANNEL:" + payment.getChannel(), "渠道回调支付成功");
        return Outcome.of("PROCESSED",
                settled ? "支付成功已结算" : "重复回调，状态已是成功",
                settled ? "支付成功" : "重复回调，已忽略");
    }

    /**
     * 退款回调结算（独立事务）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Outcome settleRefundNotify(PaymentModels.NotifyResult notify) {
        if (StringUtils.isBlank(notify.getRefundNo())) {
            return Outcome.of("FAILED", "退款回调缺少退款单号", "退款回调缺少退款单号，已记录");
        }
        ShopOrderRefund refund = refundMapper.selectOne(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getRefundNo, notify.getRefundNo())
                .last("LIMIT 1"));
        if (refund == null) {
            log.error("【对账差异】渠道退款回调的退款单在我方不存在：refundNo={}", notify.getRefundNo());
            return Outcome.of("FAILED", "退款单不存在：" + notify.getRefundNo(),
                    "退款单不存在，已记录待人工核对");
        }

        if (RefundOrderStatus.SUCCESS.name().equalsIgnoreCase(notify.getEventStatus())) {
            // 幂等：已成功的退款单再收到回调直接 ACK
            if (RefundOrderStatus.SUCCESS.name().equals(refund.getStatus())) {
                return Outcome.of("DUPLICATE", "退款已是成功状态", "重复回调，已忽略");
            }
            refundService.applyChannelRefundResult(refund, true, notify.getChannelTradeNo(),
                    notify.getEventTime(), null, "CHANNEL:" + paymentSupport.channelOfRefund(refund));
            return Outcome.of("PROCESSED", "退款成功已结算", "退款成功");
        }

        if (RefundOrderStatus.FAILED.name().equalsIgnoreCase(notify.getEventStatus())) {
            // 退款失败：退款服务会把先前预占的额度还回去，否则额度会被永久占用
            refundService.applyChannelRefundResult(refund, false, null, null,
                    notify.getFailReason(), "CHANNEL:" + paymentSupport.channelOfRefund(refund));
            return Outcome.of("PROCESSED", "退款失败，额度已归还", "退款失败已记录");
        }

        return Outcome.of("PROCESSED", "未识别的退款事件状态=" + notify.getEventStatus(), "已记录");
    }

    // ==================================================================
    // 结算：把"资金事实"落到订单
    // ==================================================================

    /**
     * 完成一次支付成功的结算。
     *
     * <p>不加事务注解：调用方（回调结算入口 / 主动查询 / 关单前查询）已经在事务里，
     * 这里必须与调用方**同生共死**——特别是主动查询路径，若库存扣减失败要连同
     * 支付单成功状态一起回滚。
     */
    public boolean settleSuccess(ShopOrderPayment payment, String channelTradeNo,
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
                        .setOrderStatus(OrderStatus.WAIT_SHIP.name())
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
                        .eq(ShopOrder::getOrderStatus, OrderStatus.WAIT_PAY.name())
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

    // ==================================================================
    // 内部
    // ==================================================================

    private ShopOrderPayment findByPaymentNo(String paymentNo) {
        if (StringUtils.isBlank(paymentNo)) {
            return null;
        }
        return paymentMapper.selectOne(new LambdaQueryWrapper<ShopOrderPayment>()
                .eq(ShopOrderPayment::getPaymentNo, paymentNo)
                .last("LIMIT 1"));
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
     * 结算结果：给"回调流水"和"渠道应答"用。
     *
     * <p>结算本身抛异常时不会走到这里——那种情况必须让事务回滚，
     * 由外层统一做"FAILED + 释放去重键 + 失败应答"。
     */
    @Data
    @Accessors(chain = true)
    public static class Outcome implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 回调流水的处理结果：PROCESSED / DUPLICATE / FAILED */
        private String logStatus;
        /** 回调流水的处理说明（落库，供排查） */
        private String logMessage;
        /** 回给渠道的说明（不落库） */
        private String ackMessage;

        public static Outcome of(String logStatus, String logMessage, String ackMessage) {
            return new Outcome().setLogStatus(logStatus).setLogMessage(logMessage).setAckMessage(ackMessage);
        }
    }
}
