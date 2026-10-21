package org.jeecg.modules.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.constant.OperatorType;
import org.jeecg.modules.shop.order.constant.OrderEventType;
import org.jeecg.modules.shop.order.constant.PayChannel;
import org.jeecg.modules.shop.order.constant.PaymentStatus;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;
import org.jeecg.modules.shop.order.entity.ShopPaymentNotifyLog;
import org.jeecg.modules.shop.order.entity.vo.OrderPrepayVO;
import org.jeecg.modules.shop.order.mapper.ShopOrderMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderPaymentMapper;
import org.jeecg.modules.shop.order.mapper.ShopPaymentNotifyLogMapper;
import org.jeecg.modules.shop.order.pay.PaymentGateway;
import org.jeecg.modules.shop.order.pay.PaymentGatewayRegistry;
import org.jeecg.modules.shop.order.pay.mock.MockPayGuard;
import org.jeecg.modules.shop.order.pay.mock.MockPaySupport;
import org.jeecg.modules.shop.order.pay.model.PaymentModels;
import org.jeecg.modules.shop.order.service.IShopOrderEventService;
import org.jeecg.modules.shop.order.service.IShopPaymentService;
import org.jeecg.modules.shop.order.service.OrderContext;
import org.jeecg.modules.shop.order.util.MoneyUtils;
import org.jeecg.modules.shop.order.util.OrderNoGenerator;
import org.jeecg.modules.shop.order.util.ShopMaskUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
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
 * <h3>本类承担的四件难事</h3>
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
 *   <li><b>回调的事务边界</b>：结算（支付单/订单/库存/资金流水）放在
 *       {@link ShopPaymentSettlementService} 里，用 {@code REQUIRES_NEW} 独立提交；
 *       本类只负责"落回调流水 → 调结算 → 提交后再 ACK"，任何异常都不许被吞掉。
 *       旧实现把结算写在本类且在事务内 catch 了异常，导致半完成状态被提交、去重键又堵死重试。</li>
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

    /**
     * 支付单 Mapper。
     *
     * <p><b>必须保留 {@code @Autowired}：</b>这里刻意用一个同类型的独立字段（而不是直接用父类
     * {@code ServiceImpl} 的 {@code baseMapper}），但 Spring 不会自动把它跟 baseMapper 关联；
     * 少了注解它就是 null，第一个用到它的地方（发起支付）会抛
     * {@code Cannot invoke "ShopOrderPaymentMapper.selectOne(...)" because "this.paymentMapper" is null}
     * —— 表现为"所有支付都发不出去"，而订单本身是好的，很容易被误判成渠道问题。
     */
    @Autowired
    private ShopOrderPaymentMapper paymentMapper;

    @Autowired
    private ShopOrderMapper orderMapper;

    @Autowired
    private ShopPaymentNotifyLogMapper notifyLogMapper;

    @Autowired
    private PaymentGatewayRegistry gatewayRegistry;

    /** 模拟支付门禁（配置 + profile 双重判定，非 dev 一律不可用） */
    @Autowired
    private MockPayGuard mockPayGuard;

    /**
     * 结算逻辑（支付单 CAS → 订单 CAS → 库存实扣 → 资金流水）的独立 Bean。
     *
     * <p>必须是**另一个 Bean**：只有跨 Bean 调用 @Transactional(REQUIRES_NEW) 才会经过代理，
     * 自调用不会。旧实现把结算写在本类里，异常被 catch 后事务照常提交，形成半完成状态。
     */
    @Autowired
    private ShopPaymentSettlementService settlementService;

    @Autowired
    private IShopOrderEventService eventService;

    /**
     * 默认支付渠道。
     *
     * <p><b>不再默认 MOCK</b>：旧版写死 {@code :MOCK}，配合"模拟网关默认注册"，
     * 生产环境会静默地让所有人走模拟支付。现在必须由配置显式指定
     * （dev 的 application-dev.yml 配 MOCK，生产配真实渠道），缺失时直接报错而不是猜一个渠道。
     */
    @Value("${ck.shop.pay.default-channel:}")
    private String defaultChannel;

    /** 支付超时时间（分钟）：与订单的 pay_deadline 保持一致 */
    @Value("${ck.shop.order.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

    /**
     * "处理中"回调流水的可回收时间（秒）。
     *
     * <p>进程在"插入 RECEIVED 之后、结算提交之前"被杀掉时，流水会永远停在 RECEIVED。
     * 渠道重推会撞唯一键、被当成"重复回调"而 ACK —— 结算却从未发生。
     * 超过这个时间仍未变成 PROCESSED/FAILED 的，视为"上次处理已中断"，
     * 允许本次重推接管重试。取 120 秒是为了不误伤"结算正在慢慢跑"的正常请求。
     */
    @Value("${ck.shop.pay.notify.stale-received-seconds:120}")
    private long staleReceivedSeconds;

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
        /*
         * 锁定订单行（SELECT ... FOR UPDATE，锁到事务提交）。
         *
         * 旧实现是"先 findPendingPayment、没有就 createPayment"，中间没有锁：
         * 用户双击 / 前端重试两个并发请求会各自看到"还没有支付单"，于是各建一张
         * 有效支付单。两张都能付成功，而订单只会结算一次 —— 多付的那笔只能人工退款。
         * 加锁后第二个请求会阻塞，等第一笔支付单提交后重新查询并复用它。
         */
        ShopOrder lockedOrder = orderMapper.lockById(order.getId());
        if (lockedOrder == null) {
            throw new JeecgBootException("订单不存在或已归档");
        }
        order = lockedOrder;

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
        if (StringUtils.isBlank(targetChannel)) {
            // 宁可报错，也不要"猜"一个渠道：默认渠道配错，用户付出去的钱会落到错误主体
            throw new JeecgBootException("未配置默认支付渠道（ck.shop.pay.default-channel），"
                    + "或请在前端显式传入支付渠道");
        }
        // 渠道白名单：模拟渠道只能在 dev 被显式启用时使用，其余情况一律按"渠道不存在"拒绝。
        // 仅靠 gatewayRegistry 也不够——万一模拟网关被误注册到非 dev，这里仍然拦住。
        if (PayChannel.MOCK.equalsIgnoreCase(targetChannel) && !mockPayGuard.isEnabled()) {
            throw new JeecgBootException("模拟支付渠道未启用，请使用真实支付渠道");
        }
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

    /**
     * 渠道回调统一入口 —— <b>故意不加 {@code @Transactional}</b>。
     *
     * <p>这里只做三件事：验签 → 落回调流水（去重）→ 调用独立 Bean 完成结算。
     * 事务边界在 {@link ShopPaymentSettlementService}（{@code REQUIRES_NEW}），
     * 所以"结算要么全部提交、要么全部回滚"，而回调流水的写入不受结算回滚影响。
     *
     * <p>处理顺序（每一步都不能省）：
     * <ol>
     *   <li>落 {@code RECEIVED} 流水并占住唯一去重键 —— 并发重推只有一条能进来；</li>
     *   <li>结算（独立事务）；</li>
     *   <li><b>结算提交之后</b>才把流水改为 PROCESSED、才回渠道成功应答 ——
     *       先 ACK 再提交的话，一旦提交失败渠道就再也不会重推，这笔钱就丢了；</li>
     *   <li>结算抛异常：事务回滚 → 流水置 FAILED → <b>释放去重键</b> → 回失败应答。
     *       这样渠道重推时会被重新处理，而不是被当成"重复回调"回成功而永久卡死。</li>
     * </ol>
     */
    @Override
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
            // 唯一键冲突有两种可能：
            //   ① 渠道重复推送同一条回调（正常，直接 ACK）；
            //   ② 上一次处理在"落流水"和"结算提交"之间被中断（进程被杀/宕机），
            //      流水卡在 RECEIVED —— 此时若当成重复 ACK 掉，结算永远不会发生。
            // ②只能靠"超时回收"识别：超过 staleReceivedSeconds 仍是 RECEIVED 的，允许本次重推接管。
            ShopPaymentNotifyLog reclaimed = reclaimStaleReceived(dedupKey);
            if (reclaimed == null) {
                log.info("渠道[{}]重复回调已忽略：dedupKey={}", channel, dedupKey);
                return NotifyHandleResult.ok(gateway.successAck(), "重复回调，已忽略");
            }
            log.warn("发现上次未处理完的回调流水，已回收并重新处理：id={}, dedupKey={}",
                    reclaimed.getId(), dedupKey);
            notifyLog = reclaimed;
        }

        try {
            // 结算走独立 Bean 的 REQUIRES_NEW 事务：异常会一路抛回这里，事务已整体回滚
            ShopPaymentSettlementService.Outcome outcome = "REFUND".equalsIgnoreCase(notifyType)
                    ? settlementService.settleRefundNotify(notify)
                    : settlementService.settlePayNotify(notify);
            // 到这里结算事务已经提交，此时才写流水结论、才回成功应答
            updateNotifyLog(notifyLog, outcome.getLogStatus(), outcome.getLogMessage());
            return NotifyHandleResult.ok(gateway.successAck(), outcome.getAckMessage());
        } catch (Exception e) {
            // 处理失败：结算已回滚；把流水置失败并**释放去重键**，再把失败应答回给渠道
            // （渠道会重推——这正是我们要的，而且下次不会被当成"重复回调"）
            markNotifyFailedAndRelease(notifyLog, e);
            log.error("渠道[{}]回调处理异常，结算已回滚，等待渠道重推：paymentNo={}, dedupKey={}, err={}",
                    channel, notify.getPaymentNo(), dedupKey, e.getMessage(), e);
            return NotifyHandleResult.reject(gateway.failAck("PROCESS_ERROR"), e.getMessage());
        }
    }

    /**
     * 回调处理失败：流水置 FAILED，并把占住的去重键改成一个"已释放"的独立值。
     *
     * <p>为什么不是删掉那一行：回调流水是只追加的审计材料，
     * "这条回调失败过、失败原因是什么"必须留下。删行会让人误以为渠道从没推过。
     * 改键之后，同样的报文再推一次会插入一条新的 RECEIVED 记录并重新处理；
     * 旧的 FAILED 记录仍在，两条记录合起来正好还原"失败 → 重试成功"的全过程。
     */
    private void markNotifyFailedAndRelease(ShopPaymentNotifyLog notifyLog, Exception cause) {
        if (notifyLog == null) {
            return;
        }
        String releasedKey = releasedDedupKey(notifyLog);
        try {
            notifyLogMapper.update(new ShopPaymentNotifyLog()
                            .setProcessStatus("FAILED")
                            .setProcessMsg(StringUtils.left("处理异常：" + cause.getMessage(), 500))
                            .setDedupKey(releasedKey),
                    new LambdaUpdateWrapper<ShopPaymentNotifyLog>()
                            .eq(ShopPaymentNotifyLog::getId, notifyLog.getId()));
            log.warn("回调流水已置 FAILED 并释放去重键，允许渠道重推重新处理：id={}, releasedKey={}",
                    notifyLog.getId(), releasedKey);
        } catch (Exception updateError) {
            // 连释放都失败时，这条回调会被永远当成"重复"——必须打 ERROR 交人工介入
            log.error("释放回调去重键失败，该回调将无法自动重试，请人工处理：id={}, dedupKey={}, err={}",
                    notifyLog.getId(), notifyLog.getDedupKey(), updateError.getMessage(), updateError);
        }
    }

    /**
     * 回收"处理中"卡死的回调流水。
     *
     * <p>用 CAS（{@code process_status = 'RECEIVED'}）改成 {@code RETRYING}：
     * 多个重推同时到达时只有一个能抢到，其余继续按重复回调处理，不会并发结算。
     * 结算本身还有支付单 CAS 兜底，即使真的重复执行也只会被识别为"重复回调"。
     *
     * @return 抢到回收的流水；null 表示不是卡死记录（按重复回调处理）
     */
    private ShopPaymentNotifyLog reclaimStaleReceived(String dedupKey) {
        ShopPaymentNotifyLog existing = notifyLogMapper.selectOne(
                new LambdaQueryWrapper<ShopPaymentNotifyLog>()
                        .eq(ShopPaymentNotifyLog::getDedupKey, dedupKey)
                        .last("LIMIT 1"));
        if (existing == null) {
            return null;
        }
        // 只有"还没得出结论"的状态才可能卡死；PROCESSED/FAILED/DUPLICATE/REJECTED 都是终态
        String status = existing.getProcessStatus();
        if (!"RECEIVED".equals(status) && !"RETRYING".equals(status)) {
            return null;
        }
        Date received = existing.getReceiveTime();
        if (received != null && System.currentTimeMillis() - received.getTime()
                < staleReceivedSeconds * 1000L) {
            // 还在正常处理窗口内，不抢
            return null;
        }
        int rows = notifyLogMapper.update(new ShopPaymentNotifyLog()
                        .setProcessStatus("RETRYING")
                        .setProcessMsg("上次处理未完成（疑似进程中断），已由重推回收重试"),
                new LambdaUpdateWrapper<ShopPaymentNotifyLog>()
                        .eq(ShopPaymentNotifyLog::getId, existing.getId())
                        .eq(ShopPaymentNotifyLog::getProcessStatus, status));
        return rows == 1 ? existing : null;
    }

    /** 释放后的去重键：控制在 varchar(160) 内，且带上主键保证唯一 */
    private String releasedDedupKey(ShopPaymentNotifyLog notifyLog) {
        return StringUtils.left(StringUtils.defaultString(notifyLog.getDedupKey(), "notify"), 100)
                + "|R|" + notifyLog.getId();
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
            // 结算逻辑在独立 Bean 里；这里已有事务（@Transactional），所以与支付单状态同生共死
            return settlementService.settleSuccess(payment, StringUtils.defaultIfBlank(
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

    /**
     * 模拟支付确认（仅 dev）。
     *
     * <p><b>故意不加 {@code @Transactional}</b>：它只是"生成一条已签名回调 → 喂给
     * {@link #handleNotify}"。事务在 {@link ShopPaymentSettlementService} 里，
     * 所以自调用 {@code this.handleNotify(...)} 不经过代理也没有关系——
     * 旧实现里 {@code mockConfirm} 与 {@code handleNotify} 都是
     * {@code @Transactional}，自调用导致内层注解实际不生效，看起来"整体回滚"其实没有。
     */
    @Override
    public boolean mockConfirm(String paymentNo, OrderContext context) {
        // 门禁：即使有人绕过 Controller 直接调用 Service（后台任务、测试脚本），
        // 非 dev 环境也一样拒绝，绝不允许"点一下就把订单变已支付"。
        mockPayGuard.requireEnabled("模拟支付确认");
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
}
