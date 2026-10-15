package org.jeecg.modules.shop.order.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.constant.FundBizType;
import org.jeecg.modules.shop.order.constant.OperatorType;
import org.jeecg.modules.shop.order.constant.OrderEventType;
import org.jeecg.modules.shop.order.constant.OrderStatus;
import org.jeecg.modules.shop.order.constant.PayStatus;
import org.jeecg.modules.shop.order.constant.PaymentStatus;
import org.jeecg.modules.shop.order.constant.RefundOrderStatus;
import org.jeecg.modules.shop.order.constant.RefundStatus;
import org.jeecg.modules.shop.order.entity.ShopFundFlow;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.ShopOrderItem;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;
import org.jeecg.modules.shop.order.entity.ShopOrderRefund;
import org.jeecg.modules.shop.order.entity.ShopOrderRefundItem;
import org.jeecg.modules.shop.order.entity.dto.RefundApplyDTO;
import org.jeecg.modules.shop.order.entity.dto.RefundAuditDTO;
import org.jeecg.modules.shop.order.entity.dto.RefundItemDTO;
import org.jeecg.modules.shop.order.entity.dto.RefundQuery;
import org.jeecg.modules.shop.order.entity.vo.OrderRefundVO;
import org.jeecg.modules.shop.order.mapper.ShopFundFlowMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderItemMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderPaymentMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderRefundItemMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderRefundMapper;
import org.jeecg.modules.shop.order.pay.PaymentGateway;
import org.jeecg.modules.shop.order.pay.PaymentGatewayRegistry;
import org.jeecg.modules.shop.order.pay.model.PaymentModels;
import org.jeecg.modules.shop.order.service.IShopOrderEventService;
import org.jeecg.modules.shop.order.service.IShopRefundService;
import org.jeecg.modules.shop.order.service.IShopStockService;
import org.jeecg.modules.shop.order.service.OrderContext;
import org.jeecg.modules.shop.order.util.MoneyUtils;
import org.jeecg.modules.shop.order.util.OrderNoGenerator;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 退款服务实现。
 * </p>
 *
 * <h3>退款金额是怎么算出来的</h3>
 * 请求体里**没有金额字段**——让客服/前端填金额是"退款退多了"这类事故的直接来源，
 * 而且少退了没人投诉、多退了就是净损失，几乎无法事后发现。
 * 这里的算法是：
 * <ol>
 *   <li>取订单明细的**行可退总额**（{@code actual_amount − refund_amount}）；</li>
 *   <li>按"本次退的数量 / 行总数量"用**最大余额法**分摊（{@code MoneyUtils.allocate}），
 *       保证分摊结果之和严格等于行可退总额，不会因为逐行舍入而出现几分钱的差异；</li>
 *   <li>累加得到退款总额，再由支付单的原子条件更新做最终额度校验。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@Service
public class ShopRefundServiceImpl
        extends ServiceImpl<ShopOrderRefundMapper, ShopOrderRefund>
        implements IShopRefundService {

    @Autowired
    private ShopOrderRefundMapper refundMapper;

    @Autowired
    private ShopOrderRefundItemMapper refundItemMapper;

    @Autowired
    private ShopOrderMapper orderMapper;

    @Autowired
    private ShopOrderItemMapper orderItemMapper;

    @Autowired
    private ShopOrderPaymentMapper paymentMapper;

    @Autowired
    private ShopFundFlowMapper fundFlowMapper;

    @Autowired
    private PaymentGatewayRegistry gatewayRegistry;

    @Autowired
    private IShopStockService stockService;

    @Autowired
    private IShopOrderEventService eventService;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    // ==================================================================
    // 申请
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderRefundVO apply(OrderContext context, RefundApplyDTO dto) {
        if (context == null || StringUtils.isBlank(context.getUserId())) {
            throw new JeecgBootException("请先登录后再申请退款");
        }
        if (dto == null || StringUtils.isBlank(dto.getOrderNo())) {
            throw new JeecgBootException("缺少订单号");
        }
        String requestId = StringUtils.trimToNull(dto.getRequestId());
        if (requestId == null || requestId.length() > 64) {
            throw new JeecgBootException("缺少退款申请幂等键，请刷新页面后重试");
        }

        // 幂等：命中就直接返回原退款单，不做任何写操作
        ShopOrderRefund existed = refundMapper.selectOne(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getRequestId, requestId)
                .last("LIMIT 1"));
        if (existed != null) {
            if (!existed.getUserId().equals(context.getUserId())) {
                throw new JeecgBootException("退款申请异常，请刷新页面后重试");
            }
            return toVO(existed, listItems(existed.getId()));
        }

        ShopOrder order = requireOwnedOrder(context, dto.getOrderNo());
        assertRefundable(order);

        // 同一订单同时只允许一个进行中的退款申请：多个并发申请会让
        // "到底以哪一笔为准"变得无法解释，也容易被用来放大退款额度
        Long applying = refundMapper.selectCount(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getOrderId, order.getId())
                .in(ShopOrderRefund::getStatus, java.util.Arrays.asList(
                        RefundOrderStatus.APPLYING.name(), RefundOrderStatus.REFUNDING.name())));
        if (applying != null && applying > 0) {
            throw new JeecgBootException("该订单已有退款申请正在处理中，请等待处理完成");
        }

        List<ShopOrderItem> orderItems = orderItemMapper.selectByOrderIds(
                java.util.Collections.singletonList(order.getId()));
        if (orderItems == null || orderItems.isEmpty()) {
            throw new JeecgBootException("订单明细缺失，无法计算退款金额，请联系客服");
        }

        Plan plan = plan(order, orderItems, dto.getItems());
        if (!MoneyUtils.isPositive(plan.refundAmount)) {
            throw new JeecgBootException("该订单已无可退金额");
        }
        BigDecimal refundable = refundableAmount(order.getOrderNo());
        if (MoneyUtils.gt(plan.refundAmount, refundable)) {
            // 早失败：让用户在提交前就知道能退多少，而不是审核时才被拒
            throw new JeecgBootException("退款金额 " + plan.refundAmount.toPlainString()
                    + " 元超过可退金额 " + refundable.toPlainString() + " 元");
        }

        Date now = new Date();
        ShopOrderRefund refund = new ShopOrderRefund()
                .setRefundNo(OrderNoGenerator.refundNo())
                .setOrderId(order.getId())
                .setOrderNo(order.getOrderNo())
                .setPaymentId(plan.payment == null ? null : plan.payment.getId())
                .setPaymentNo(plan.payment == null ? null : plan.payment.getPaymentNo())
                .setUserId(order.getUserId())
                .setUsername(order.getUsername())
                .setStoreId(order.getStoreId())
                .setStoreOrgCode(order.getStoreOrgCode())
                .setRefundType(plan.fullRefund ? "FULL" : "PARTIAL")
                .setRefundAmount(plan.refundAmount)
                .setItemCount(plan.lines.size())
                .setTotalQuantity(plan.totalQuantity)
                .setApplyReason(StringUtils.left(StringUtils.trimToNull(dto.getReason()), 100))
                .setApplyDesc(StringUtils.left(StringUtils.trimToNull(dto.getDescription()), 500))
                .setApplyImages(dto.getImages() == null || dto.getImages().isEmpty()
                        ? null : JSON.toJSONString(dto.getImages()))
                .setStatus(RefundOrderStatus.APPLYING.name())
                .setApplyTime(now)
                .setRetryCount(0)
                .setRequestId(requestId)
                .setVersion(0)
                .setDelFlag(0)
                .setCreateBy(context.getUsername())
                .setCreateTime(now)
                .setUpdateBy(context.getUsername())
                .setUpdateTime(now);

        try {
            refundMapper.insert(refund);
        } catch (DuplicateKeyException e) {
            ShopOrderRefund concurrent = refundMapper.selectOne(new LambdaQueryWrapper<ShopOrderRefund>()
                    .eq(ShopOrderRefund::getRequestId, requestId)
                    .last("LIMIT 1"));
            if (concurrent == null) {
                throw e;
            }
            return toVO(concurrent, listItems(concurrent.getId()));
        }

        List<ShopOrderRefundItem> savedItems = new ArrayList<>();
        for (RefundLine line : plan.lines) {
            ShopOrderRefundItem item = new ShopOrderRefundItem()
                    .setRefundId(refund.getId())
                    .setRefundNo(refund.getRefundNo())
                    .setOrderId(order.getId())
                    .setOrderItemId(line.orderItem.getId())
                    .setProductId(line.orderItem.getProductId())
                    .setSkuId(StringUtils.trimToEmpty(line.orderItem.getSkuId()))
                    .setProductName(line.orderItem.getProductName())
                    .setSkuName(line.orderItem.getSkuName())
                    .setPrice(line.orderItem.getPrice())
                    .setQuantity(line.quantity)
                    .setRefundAmount(line.refundAmount)
                    .setCreateTime(now);
            refundItemMapper.insert(item);
            savedItems.add(item);
        }

        // 订单上的售后状态：让列表一眼看到"这单有退款在走"
        orderMapper.update(new ShopOrder()
                        .setRefundStatus(RefundStatus.APPLYING.name())
                        .setUpdateBy(context.getUsername())
                        .setUpdateTime(now),
                new LambdaUpdateWrapper<ShopOrder>().eq(ShopOrder::getId, order.getId()));

        eventService.recordNote(order, OrderEventType.REFUND_APPLY, OperatorType.USER,
                context.getUsername(),
                "申请退款 " + plan.refundAmount.toPlainString() + " 元，原因："
                        + StringUtils.defaultIfBlank(refund.getApplyReason(), "未填写"),
                "{\"refundNo\":\"" + refund.getRefundNo() + "\",\"amount\":\""
                        + plan.refundAmount.toPlainString() + "\"}");

        return toVO(refund, savedItems);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(OrderContext context, String refundNo) {
        ShopOrderRefund refund = requireOwnedRefund(context, refundNo);
        int rows = refundMapper.update(new ShopOrderRefund()
                        .setStatus(RefundOrderStatus.CLOSED.name())
                        .setUpdateBy(context.getUsername())
                        .setUpdateTime(new Date()),
                new LambdaUpdateWrapper<ShopOrderRefund>()
                        .eq(ShopOrderRefund::getId, refund.getId())
                        .eq(ShopOrderRefund::getStatus, RefundOrderStatus.APPLYING.name()));
        if (rows == 0) {
            throw new JeecgBootException("退款单已进入处理流程，无法撤销");
        }
        refreshOrderRefundStatus(refund.getOrderId(), context.getUsername());
        ShopOrder order = orderMapper.selectById(refund.getOrderId());
        eventService.recordNote(order, OrderEventType.REFUND_AUDIT, OperatorType.USER,
                context.getUsername(), "买家撤销退款申请", null);
    }

    // ==================================================================
    // 审核
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(RefundAuditDTO dto, String operator) {
        if (dto == null || StringUtils.isBlank(dto.getRefundNo())) {
            throw new JeecgBootException("缺少退款单号");
        }
        if (dto.getApproved() == null) {
            throw new JeecgBootException("请选择审核结果");
        }
        ShopOrderRefund refund = requireAdminRefund(dto.getRefundNo());
        if (!RefundOrderStatus.APPLYING.name().equals(refund.getStatus())) {
            throw new JeecgBootException("退款单当前状态为["
                    + RefundOrderStatus.of(refund.getStatus()).getLabel() + "]，无法审核");
        }

        if (!Boolean.TRUE.equals(dto.getApproved())) {
            String rejectReason = StringUtils.trimToNull(dto.getRejectReason());
            if (rejectReason == null) {
                // 拒绝必须给理由：没有理由的拒绝会让用户反复提交同一笔申请，
                // 客服工作量成倍增加，投诉也无法定位
                throw new JeecgBootException("拒绝退款时必须填写拒绝原因");
            }
            int rows = refundMapper.update(new ShopOrderRefund()
                            .setStatus(RefundOrderStatus.REJECTED.name())
                            .setAuditBy(operator)
                            .setAuditTime(new Date())
                            .setAuditRemark(StringUtils.left(StringUtils.trimToNull(dto.getAuditRemark()), 500))
                            .setRejectReason(StringUtils.left(rejectReason, 255))
                            .setUpdateBy(operator)
                            .setUpdateTime(new Date()),
                    new LambdaUpdateWrapper<ShopOrderRefund>()
                            .eq(ShopOrderRefund::getId, refund.getId())
                            .eq(ShopOrderRefund::getStatus, RefundOrderStatus.APPLYING.name()));
            if (rows == 0) {
                throw new JeecgBootException("退款单状态已变化，请刷新后重试");
            }
            refreshOrderRefundStatus(refund.getOrderId(), operator);
            ShopOrder order = orderMapper.selectById(refund.getOrderId());
            eventService.recordNote(order, OrderEventType.REFUND_AUDIT, OperatorType.ADMIN, operator,
                    "退款申请被拒绝：" + rejectReason, null);
            return;
        }

        ShopOrderPayment payment = resolvePayment(refund, orderMapper.selectById(refund.getOrderId()));
        if (payment == null) {
            throw new JeecgBootException("该订单没有可退款的支付记录，无法退款");
        }
        // 绑定支付单（申请时若还没有支付单，这里补上）
        if (StringUtils.isBlank(refund.getPaymentId())) {
            refund.setPaymentId(payment.getId());
            refund.setPaymentNo(payment.getPaymentNo());
        }

        // ★ 先占额度：这一条 UPDATE 是"并发退款超额"的根治手段。
        //   若先提交渠道再占额度，就会出现"钱退出去了、系统认为没退"的不可挽回局面。
        int locked = paymentMapper.accumulateRefundAmount(payment.getId(),
                MoneyUtils.scale(refund.getRefundAmount()), operator);
        if (locked == 0) {
            throw new JeecgBootException("退款额度不足（该支付单可退金额已不足或状态异常），"
                    + "可能有并发退款或已出现超额，请刷新后核对。本次退款已拒绝");
        }

        int rows = refundMapper.update(new ShopOrderRefund()
                        .setStatus(RefundOrderStatus.REFUNDING.name())
                        .setPaymentId(payment.getId())
                        .setPaymentNo(payment.getPaymentNo())
                        .setAuditBy(operator)
                        .setAuditTime(new Date())
                        .setAuditRemark(StringUtils.left(StringUtils.trimToNull(dto.getAuditRemark()), 500))
                        .setUpdateBy(operator)
                        .setUpdateTime(new Date()),
                new LambdaUpdateWrapper<ShopOrderRefund>()
                        .eq(ShopOrderRefund::getId, refund.getId())
                        .eq(ShopOrderRefund::getStatus, RefundOrderStatus.APPLYING.name()));
        if (rows == 0) {
            // 状态被并发改掉了：必须把刚占的额度还回去，否则额度会被永久占用
            paymentMapper.releaseRefundAmount(payment.getId(),
                    MoneyUtils.scale(refund.getRefundAmount()), operator);
            throw new JeecgBootException("退款单状态已变化，请刷新后重试");
        }
        refund.setStatus(RefundOrderStatus.REFUNDING.name());

        ShopOrder order = orderMapper.selectById(refund.getOrderId());
        eventService.recordNote(order, OrderEventType.REFUND_AUDIT, OperatorType.ADMIN, operator,
                "审核通过，提交渠道退款 " + MoneyUtils.scale(refund.getRefundAmount()).toPlainString() + " 元",
                "{\"refundNo\":\"" + refund.getRefundNo() + "\"}");

        submitChannelRefund(refund, payment, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryChannelRefund(String refundNo, String operator) {
        ShopOrderRefund refund = requireAdminRefund(refundNo);
        if (!RefundOrderStatus.FAILED.name().equals(refund.getStatus())
                && !RefundOrderStatus.REFUNDING.name().equals(refund.getStatus())) {
            throw new JeecgBootException("只有【退款失败】或【退款中】的退款单可以重试");
        }
        ShopOrderPayment payment = StringUtils.isNotBlank(refund.getPaymentId())
                ? paymentMapper.selectById(refund.getPaymentId())
                : resolvePayment(refund, orderMapper.selectById(refund.getOrderId()));
        if (payment == null) {
            throw new JeecgBootException("找不到对应的支付记录，无法重试退款");
        }

        if (RefundOrderStatus.FAILED.name().equals(refund.getStatus())) {
            // 失败单要重新占额度（失败时已经归还过），再试一次
            int locked = paymentMapper.accumulateRefundAmount(payment.getId(),
                    MoneyUtils.scale(refund.getRefundAmount()), operator);
            if (locked == 0) {
                throw new JeecgBootException("退款额度不足，无法重试。请核对支付单的可退金额");
            }
            int rows = refundMapper.update(new ShopOrderRefund()
                            .setStatus(RefundOrderStatus.REFUNDING.name())
                            .setRetryCount((refund.getRetryCount() == null ? 0 : refund.getRetryCount()) + 1)
                            .setFailCode(null)
                            .setFailReason(null)
                            .setUpdateBy(operator)
                            .setUpdateTime(new Date()),
                    new LambdaUpdateWrapper<ShopOrderRefund>()
                            .eq(ShopOrderRefund::getId, refund.getId())
                            .eq(ShopOrderRefund::getStatus, RefundOrderStatus.FAILED.name()));
            if (rows == 0) {
                paymentMapper.releaseRefundAmount(payment.getId(),
                        MoneyUtils.scale(refund.getRefundAmount()), operator);
                throw new JeecgBootException("退款单状态已变化，请刷新后重试");
            }
            refund.setStatus(RefundOrderStatus.REFUNDING.name());
        }
        submitChannelRefund(refund, payment, operator);
    }

    /**
     * 提交渠道退款。
     *
     * <p>三种结果都要处理清楚：
     * <ul>
     *   <li>{@code SUCCESS}：立即成功（多数渠道的小额退款是同步的）；</li>
     *   <li>{@code PROCESSING}：**受理中**，绝不能当失败——很多渠道是异步退款的，
     *       真正的结果要靠退款回调或退款查询确认；</li>
     *   <li>{@code FAILED}：归还已占的额度并置为失败，保留重试入口。</li>
     * </ul>
     */
    private void submitChannelRefund(ShopOrderRefund refund, ShopOrderPayment payment, String operator) {
        PaymentGateway gateway = gatewayRegistry.require(payment.getChannel());
        PaymentModels.RefundResult result;
        try {
            result = gateway.refund(new PaymentModels.RefundRequest()
                    .setRefundNo(refund.getRefundNo())
                    .setPaymentNo(payment.getPaymentNo())
                    .setChannelTradeNo(payment.getChannelTradeNo())
                    .setTotalAmount(MoneyUtils.scale(payment.getAmount()))
                    .setRefundAmount(MoneyUtils.scale(refund.getRefundAmount()))
                    .setCurrency(payment.getCurrency())
                    .setReason(StringUtils.left(refund.getApplyReason(), 100)));
        } catch (Exception e) {
            // 渠道异常（网络/超时）：结果**未知**，保留 REFUNDING 等回调或人工查询，
            // 绝不能当失败——当失败就会归还额度，而钱可能已经退出去了
            log.error("提交渠道退款异常，退款单保持【退款中】等待回调或查询：refundNo={}, err={}",
                    refund.getRefundNo(), e.getMessage(), e);
            refundMapper.update(new ShopOrderRefund()
                            .setFailCode("CHANNEL_TIMEOUT")
                            .setFailReason(StringUtils.left("渠道调用异常：" + e.getMessage(), 500))
                            .setUpdateTime(new Date()),
                    new LambdaUpdateWrapper<ShopOrderRefund>()
                            .eq(ShopOrderRefund::getId, refund.getId()));
            return;
        }

        if (result.isSuccess()) {
            applyChannelRefundResult(refund, true, result.getChannelRefundNo(),
                    result.getRefundTime(), null, operator);
            return;
        }
        if (result.isProcessing()) {
            refundMapper.update(new ShopOrderRefund()
                            .setChannelRefundNo(result.getChannelRefundNo())
                            .setUpdateTime(new Date()),
                    new LambdaUpdateWrapper<ShopOrderRefund>()
                            .eq(ShopOrderRefund::getId, refund.getId()));
            log.info("渠道退款已受理（异步），等待回调或查询确认：refundNo={}, channelRefundNo={}",
                    refund.getRefundNo(), result.getChannelRefundNo());
            return;
        }

        // 明确失败：归还额度 + 置失败，保留重试入口
        paymentMapper.releaseRefundAmount(payment.getId(),
                MoneyUtils.scale(refund.getRefundAmount()), operator);
        refundMapper.update(new ShopOrderRefund()
                        .setStatus(RefundOrderStatus.FAILED.name())
                        .setFailCode(result.getErrorCode())
                        .setFailReason(StringUtils.left(result.getErrorMsg(), 500))
                        .setUpdateBy(operator)
                        .setUpdateTime(new Date()),
                new LambdaUpdateWrapper<ShopOrderRefund>()
                        .eq(ShopOrderRefund::getId, refund.getId())
                        .eq(ShopOrderRefund::getStatus, RefundOrderStatus.REFUNDING.name()));
        refreshOrderRefundStatus(refund.getOrderId(), operator);
        ShopOrder order = orderMapper.selectById(refund.getOrderId());
        eventService.recordNote(order, OrderEventType.REFUND_FAIL, OperatorType.ADMIN, operator,
                "渠道退款失败：" + StringUtils.defaultIfBlank(result.getErrorMsg(), "未知原因")
                        + "（额度已归还，可重试）", null);
    }

    // ==================================================================
    // 渠道结果落地（回调与查询共用）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyChannelRefundResult(ShopOrderRefund refund, boolean success,
                                        String channelRefundNo, Date refundTime,
                                        String failReason, String operator) {
        if (refund == null) {
            return;
        }
        if (!success) {
            ShopOrderPayment payment = StringUtils.isNotBlank(refund.getPaymentId())
                    ? paymentMapper.selectById(refund.getPaymentId()) : null;
            if (payment != null) {
                // 失败必须归还额度，否则额度被永久占用，"可退金额"越用越少
                paymentMapper.releaseRefundAmount(payment.getId(),
                        MoneyUtils.scale(refund.getRefundAmount()), operator);
            }
            int rows = refundMapper.update(new ShopOrderRefund()
                            .setStatus(RefundOrderStatus.FAILED.name())
                            .setFailCode("CHANNEL_FAIL")
                            .setFailReason(StringUtils.left(StringUtils.defaultIfBlank(failReason, "渠道退款失败"), 500))
                            .setUpdateBy(operator)
                            .setUpdateTime(new Date()),
                    new LambdaUpdateWrapper<ShopOrderRefund>()
                            .eq(ShopOrderRefund::getId, refund.getId())
                            .eq(ShopOrderRefund::getStatus, RefundOrderStatus.REFUNDING.name()));
            if (rows > 0) {
                refreshOrderRefundStatus(refund.getOrderId(), operator);
                ShopOrder order = orderMapper.selectById(refund.getOrderId());
                eventService.recordNote(order, OrderEventType.REFUND_FAIL, OperatorType.SYSTEM, operator,
                        "退款失败：" + failReason, null);
            }
            return;
        }

        Date actualTime = refundTime == null ? new Date() : refundTime;
        // CAS：只有从 REFUNDING 才能变 SUCCESS。影响 0 行 = 已经成功过（重复回调/重复查询），直接返回
        int rows = refundMapper.update(new ShopOrderRefund()
                        .setStatus(RefundOrderStatus.SUCCESS.name())
                        .setChannelRefundNo(channelRefundNo)
                        .setRefundTime(actualTime)
                        .setFailCode(null)
                        .setFailReason(null)
                        .setUpdateBy(operator)
                        .setUpdateTime(new Date()),
                new LambdaUpdateWrapper<ShopOrderRefund>()
                        .eq(ShopOrderRefund::getId, refund.getId())
                        .eq(ShopOrderRefund::getStatus, RefundOrderStatus.REFUNDING.name()));
        if (rows == 0) {
            log.info("退款单[{}]已非【退款中】状态，跳过重复结算", refund.getRefundNo());
            return;
        }

        ShopOrder order = orderMapper.selectById(refund.getOrderId());
        if (order == null) {
            log.error("退款成功但订单不存在：refundNo={}, orderId={}",
                    refund.getRefundNo(), refund.getOrderId());
            return;
        }

        // ① 订单累计退款额：用 SQL 原子累加（与支付单的额度锁配合，双保险）
        int orderRows = orderMapper.accumulateRefundAmount(order.getId(),
                MoneyUtils.scale(refund.getRefundAmount()), operator);
        if (orderRows == 0) {
            // 订单侧额度对不上（例如已全额退过）——支付单的锁保证了资金额度不会超额，
            // 这里是数据一致性问题，必须报警让人查
            log.error("【资金异常】订单累计退款额累加失败（可能已超订单已付金额）："
                            + "orderNo={}, refundNo={}, amount={}，请人工核对",
                    order.getOrderNo(), refund.getRefundNo(), refund.getRefundAmount());
        }

        // ② 退款状态与支付状态
        ShopOrder latest = orderMapper.selectById(order.getId());
        BigDecimal paid = MoneyUtils.scale(latest.getPaidAmount());
        BigDecimal refunded = MoneyUtils.scale(latest.getRefundAmount());
        boolean fullRefund = MoneyUtils.gte(refunded, paid);
        orderMapper.update(new ShopOrder()
                        .setRefundStatus(fullRefund ? RefundStatus.FULL.name() : RefundStatus.PARTIAL.name())
                        .setPayStatus(fullRefund ? PayStatus.REFUNDED.name() : PayStatus.PARTIAL_REFUND.name())
                        .setUpdateBy(operator)
                        .setUpdateTime(new Date()),
                new LambdaUpdateWrapper<ShopOrder>().eq(ShopOrder::getId, order.getId()));

        // ③ 未发货且全额退款 → 订单转为已取消。
        //    这是"已付款但卖家没发货，用户退款成功"的正常归宿；
        //    已发货/已完成的订单不做状态回退（货已经出去了，用 refund_status 表达即可）
        if (fullRefund && OrderStatus.WAIT_SHIP.name().equals(latest.getOrderStatus())) {
            int cancelRows = orderMapper.update(new ShopOrder()
                            .setOrderStatus(OrderStatus.CANCELLED.name())
                            .setCancelType("REFUND")
                            .setCancelReason("全额退款，订单关闭")
                            .setCancelTime(new Date())
                            .setFinishTime(new Date())
                            .setUpdateBy(operator)
                            .setUpdateTime(new Date()),
                    new LambdaUpdateWrapper<ShopOrder>()
                            .eq(ShopOrder::getId, order.getId())
                            .eq(ShopOrder::getOrderStatus, OrderStatus.WAIT_SHIP.name())
                            .setSql("version = version + 1"));
            if (cancelRows > 0) {
                log.info("全额退款完成，订单转为已取消：orderNo={}", order.getOrderNo());
            }
        }

        // ④ 库存回补（按**退款明细**精确回补，而不是整单回补）
        restoreStockByRefundLines(refund, order);

        // ⑤ 资金流水（退款为负）
        writeRefundFundFlow(refund, actualTime);

        // ⑥ 行/单退款额更新
        updateOrderItemRefunded(refund);

        ShopOrder finalOrder = orderMapper.selectById(order.getId());
        eventService.recordStatusChange(finalOrder, OrderEventType.REFUND_SUCCESS,
                latest.getOrderStatus(), finalOrder.getOrderStatus(),
                latest.getPayStatus(), finalOrder.getPayStatus(),
                StringUtils.startsWith(operator, "CHANNEL") ? OperatorType.CHANNEL : OperatorType.ADMIN,
                operator,
                "退款成功，金额 " + MoneyUtils.scale(refund.getRefundAmount()).toPlainString() + " 元",
                MoneyUtils.scale(refund.getRefundAmount()),
                "{\"refundNo\":\"" + refund.getRefundNo() + "\",\"channelRefundNo\":\""
                        + StringUtils.trimToEmpty(channelRefundNo) + "\"}");
    }

    private void restoreStockByRefundLines(ShopOrderRefund refund, ShopOrder order) {
        List<ShopOrderRefundItem> refundItems = listItems(refund.getId());
        if (refundItems.isEmpty()) {
            return;
        }
        Map<String, ShopOrderItem> orderItemMap = new HashMap<>();
        List<ShopOrderItem> orderItems = orderItemMapper.selectByOrderIds(
                java.util.Collections.singletonList(order.getId()));
        if (orderItems != null) {
            for (ShopOrderItem item : orderItems) {
                orderItemMap.put(item.getId(), item);
            }
        }
        // 按 SKU 汇总回补数量，避免同一 SKU 多行退款时重复回补
        Map<String, Integer> restoreByLine = new LinkedHashMap<>();
        Map<String, ShopOrderItem> forLog = new HashMap<>();
        for (ShopOrderRefundItem refundItem : refundItems) {
            ShopOrderItem orderItem = orderItemMap.get(refundItem.getOrderItemId());
            if (orderItem == null) {
                continue;
            }
            String key = orderItem.getProductId() + "|" + StringUtils.trimToEmpty(orderItem.getSkuId());
            restoreByLine.merge(key, refundItem.getQuantity() == null ? 0 : refundItem.getQuantity(),
                    Integer::sum);
            forLog.put(key, orderItem);
        }
        for (Map.Entry<String, Integer> entry : restoreByLine.entrySet()) {
            ShopOrderItem template = forLog.get(entry.getKey());
            if (template == null || entry.getValue() <= 0) {
                continue;
            }
            ShopOrderItem restoreItem = new ShopOrderItem()
                    .setProductId(template.getProductId())
                    .setSkuId(StringUtils.trimToEmpty(template.getSkuId()))
                    .setQuantity(entry.getValue())
                    .setStoreId(template.getStoreId());
            stockService.restore(order.getOrderNo(),
                    java.util.Collections.singletonList(restoreItem),
                    refund.getRefundNo(), "SYSTEM");
        }
    }

    private void updateOrderItemRefunded(ShopOrderRefund refund) {
        List<ShopOrderRefundItem> refundItems = listItems(refund.getId());
        for (ShopOrderRefundItem refundItem : refundItems) {
            ShopOrderItem orderItem = orderItemMapper.selectById(refundItem.getOrderItemId());
            if (orderItem == null) {
                continue;
            }
            int refundedQty = (orderItem.getRefundQuantity() == null ? 0 : orderItem.getRefundQuantity())
                    + (refundItem.getQuantity() == null ? 0 : refundItem.getQuantity());
            BigDecimal refundedAmount = MoneyUtils.add(orderItem.getRefundAmount(),
                    MoneyUtils.scale(refundItem.getRefundAmount()));
            boolean full = refundedQty >= (orderItem.getQuantity() == null ? 0 : orderItem.getQuantity());
            orderItemMapper.update(new ShopOrderItem()
                            .setRefundQuantity(refundedQty)
                            .setRefundAmount(refundedAmount)
                            .setRefundStatus(full ? RefundStatus.FULL.name() : RefundStatus.PARTIAL.name())
                            .setUpdateTime(new Date()),
                    new LambdaUpdateWrapper<ShopOrderItem>()
                            .eq(ShopOrderItem::getId, orderItem.getId()));
        }
    }

    private void writeRefundFundFlow(ShopOrderRefund refund, Date tradeTime) {
        ShopFundFlow flow = new ShopFundFlow()
                .setFlowNo(OrderNoGenerator.fundFlowNo())
                .setBizType(FundBizType.REFUND_OUT)
                .setOrderNo(refund.getOrderNo())
                .setPaymentNo(refund.getPaymentNo())
                .setRefundNo(refund.getRefundNo())
                .setStoreId(refund.getStoreId())
                .setStoreOrgCode(refund.getStoreOrgCode())
                .setUserId(refund.getUserId())
                .setChannelTradeNo(refund.getChannelRefundNo())
                // 带符号：退款为负，"净流入 = SUM(amount)"一条 SQL 就能算
                .setAmount(MoneyUtils.scale(refund.getRefundAmount()).negate())
                .setDirection(FundBizType.DIRECTION_OUT)
                .setCurrency("CNY")
                .setStatus("SUCCESS")
                .setTradeTime(tradeTime)
                .setSyncTime(new Date())
                .setRemark("退款出账")
                .setCreateTime(new Date());
        try {
            fundFlowMapper.insert(flow);
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底：这笔退款已经记过账了，正是我们想要的结果
            log.warn("退款资金流水已存在，跳过重复入账：refundNo={}", refund.getRefundNo());
        }
    }

    // ==================================================================
    // 金额规划
    // ==================================================================

    /**
     * 规划退款明细与金额。
     *
     * <p>不传明细 = 整单全退（退掉所有行剩余的可退金额）。
     * 传了明细则逐行按数量分摊：使用**最大余额法**保证
     * "各行退款额之和 == 目标退款额"，不会因为逐行舍入产生几分钱差异。
     */
    private Plan plan(ShopOrder order, List<ShopOrderItem> orderItems, List<RefundItemDTO> requested) {
        Plan plan = new Plan();
        plan.payment = findRefundablePayment(order.getId());

        Map<String, ShopOrderItem> itemMap = new HashMap<>();
        for (ShopOrderItem item : orderItems) {
            itemMap.put(item.getId(), item);
        }

        List<RefundLine> lines = new ArrayList<>();
        if (requested == null || requested.isEmpty()) {
            // 整单全退
            for (ShopOrderItem item : orderItems) {
                int ordered = item.getQuantity() == null ? 0 : item.getQuantity();
                int refunded = item.getRefundQuantity() == null ? 0 : item.getRefundQuantity();
                int remain = ordered - refunded;
                if (remain <= 0) {
                    continue;
                }
                BigDecimal lineRefundable = lineRefundable(item);
                RefundLine line = new RefundLine();
                line.orderItem = item;
                line.quantity = remain;
                line.refundAmount = lineRefundable;
                lines.add(line);
            }
        } else {
            for (RefundItemDTO dto : requested) {
                if (dto == null || StringUtils.isBlank(dto.getOrderItemId())) {
                    throw new JeecgBootException("退款明细缺少订单明细ID");
                }
                ShopOrderItem item = itemMap.get(dto.getOrderItemId());
                if (item == null) {
                    throw new JeecgBootException("退款明细与订单不匹配，请刷新后重试");
                }
                int ordered = item.getQuantity() == null ? 0 : item.getQuantity();
                int refunded = item.getRefundQuantity() == null ? 0 : item.getRefundQuantity();
                int remain = ordered - refunded;
                int want = dto.getQuantity() == null ? 0 : dto.getQuantity();
                if (want <= 0) {
                    throw new JeecgBootException("退款数量必须大于 0");
                }
                if (want > remain) {
                    throw new JeecgBootException("商品[" + item.getProductName() + "]最多还能退 "
                            + remain + " 件（已退 " + refunded + " 件）");
                }
                BigDecimal lineRefundable = lineRefundable(item);
                // 按"本次退的数量 / 剩余可退数量"分摊，保证不丢分也不多退
                BigDecimal[] allocated = MoneyUtils.allocate(lineRefundable,
                        new BigDecimal[]{BigDecimal.valueOf(want), BigDecimal.valueOf(remain - want)});
                RefundLine line = new RefundLine();
                line.orderItem = item;
                line.quantity = want;
                line.refundAmount = allocated[0];
                lines.add(line);
            }
        }

        if (lines.isEmpty()) {
            throw new JeecgBootException("没有可退款的商品明细，该订单可能已全额退款");
        }

        BigDecimal total = BigDecimal.ZERO;
        int totalQuantity = 0;
        for (RefundLine line : lines) {
            total = total.add(line.refundAmount);
            totalQuantity += line.quantity;
        }
        plan.lines = lines;
        plan.refundAmount = MoneyUtils.scale(total);
        plan.totalQuantity = totalQuantity;
        plan.fullRefund = "FULL".equals(computeRefundType(orderItems, lines));
        return plan;
    }

    private String computeRefundType(List<ShopOrderItem> orderItems, List<RefundLine> lines) {
        Map<String, Integer> refundingByItem = new HashMap<>();
        for (RefundLine line : lines) {
            refundingByItem.merge(line.orderItem.getId(), line.quantity, Integer::sum);
        }
        for (ShopOrderItem item : orderItems) {
            int ordered = item.getQuantity() == null ? 0 : item.getQuantity();
            int refunded = item.getRefundQuantity() == null ? 0 : item.getRefundQuantity();
            int thisTime = refundingByItem.getOrDefault(item.getId(), 0);
            if (refunded + thisTime < ordered) {
                return "PARTIAL";
            }
        }
        return "FULL";
    }

    /** 某行还能退多少钱 = 实付金额 − 已退金额 */
    private BigDecimal lineRefundable(ShopOrderItem item) {
        BigDecimal refundable = MoneyUtils.subtract(
                MoneyUtils.scale(item.getActualAmount()), MoneyUtils.scale(item.getRefundAmount()));
        return refundable.compareTo(BigDecimal.ZERO) < 0 ? MoneyUtils.zero() : refundable;
    }

    private ShopOrderPayment findRefundablePayment(String orderId) {
        List<ShopOrderPayment> payments = paymentMapper.selectList(new LambdaQueryWrapper<ShopOrderPayment>()
                .eq(ShopOrderPayment::getOrderId, orderId)
                .eq(ShopOrderPayment::getStatus, PaymentStatus.SUCCESS.name())
                .orderByAsc(ShopOrderPayment::getPayTime));
        return payments.isEmpty() ? null : payments.get(0);
    }

    private ShopOrderPayment resolvePayment(ShopOrderRefund refund, ShopOrder order) {
        if (StringUtils.isNotBlank(refund.getPaymentId())) {
            ShopOrderPayment payment = paymentMapper.selectById(refund.getPaymentId());
            if (payment != null) {
                return payment;
            }
        }
        // 申请时可能还没有支付单（极少数时序），审核时补查
        List<ShopOrderPayment> payments = paymentMapper.selectList(new LambdaQueryWrapper<ShopOrderPayment>()
                .eq(ShopOrderPayment::getOrderId, refund.getOrderId())
                .eq(ShopOrderPayment::getStatus, PaymentStatus.SUCCESS.name())
                .orderByAsc(ShopOrderPayment::getPayTime));
        return payments.isEmpty() ? null : payments.get(0);
    }

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public List<OrderRefundVO> listByOrderNo(OrderContext context, String orderNo) {
        ShopOrder order = requireOwnedOrder(context, orderNo);
        return toVOs(refundMapper.selectList(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getOrderId, order.getId())
                .orderByDesc(ShopOrderRefund::getCreateTime)));
    }

    @Override
    public Page<OrderRefundVO> adminPage(RefundQuery query) {
        RefundQuery condition = query == null ? new RefundQuery() : query;
        List<String> scope = resolveScope(condition.getStoreId());
        LambdaQueryWrapper<ShopOrderRefund> wrapper = new LambdaQueryWrapper<>();
        applyOrgScope(wrapper, scope);
        if (StringUtils.isNotBlank(condition.getRefundNo())) {
            wrapper.eq(ShopOrderRefund::getRefundNo, condition.getRefundNo().trim());
        }
        if (StringUtils.isNotBlank(condition.getOrderNo())) {
            wrapper.eq(ShopOrderRefund::getOrderNo, condition.getOrderNo().trim());
        }
        if (StringUtils.isNotBlank(condition.getStatus())) {
            wrapper.eq(ShopOrderRefund::getStatus, condition.getStatus().trim().toUpperCase());
        }
        // 申请时间：同样用 String 入参 + 统一解析（结束时间按精度包含）
        Date applyStart = org.jeecg.modules.shop.order.util.QueryDateUtils
                .parseStart(condition.getStartTime(), "申请开始时间");
        Date applyEnd = org.jeecg.modules.shop.order.util.QueryDateUtils
                .parseEnd(condition.getEndTime(), "申请结束时间");
        if (applyStart != null) {
            wrapper.ge(ShopOrderRefund::getApplyTime, applyStart);
        }
        if (applyEnd != null) {
            wrapper.lt(ShopOrderRefund::getApplyTime, applyEnd);
        }
        wrapper.orderByDesc(ShopOrderRefund::getCreateTime);

        int pageNo = condition.getPageNo() == null || condition.getPageNo() < 1 ? 1 : condition.getPageNo();
        int pageSize = condition.getPageSize() == null || condition.getPageSize() < 1
                ? 10 : Math.min(condition.getPageSize(), 100);
        Page<ShopOrderRefund> entityPage = refundMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Page<OrderRefundVO> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(),
                entityPage.getTotal());
        result.setRecords(toVOs(entityPage.getRecords()));
        return result;
    }

    @Override
    public OrderRefundVO adminDetail(String refundNo) {
        ShopOrderRefund refund = requireAdminRefund(refundNo);
        return toVO(refund, listItems(refund.getId()));
    }

    @Override
    public ShopOrderRefund getByRefundNo(String refundNo) {
        if (StringUtils.isBlank(refundNo)) {
            return null;
        }
        return refundMapper.selectOne(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getRefundNo, refundNo)
                .last("LIMIT 1"));
    }

    @Override
    public BigDecimal adminRefundableAmount(String orderNo) {
        // 历史缺陷：后台 /refund/refundable 直接调用 refundableAmount(orderNo)，
        // 而那个方法只按 orderNo 查订单、**不做任何归属校验** ——
        // 任何持有 shop-refund:list 的运营都能用别人的订单号读到"已付/可退金额"，
        // 顺带把订单号是否存在也探测出来（adminDetail 是校验的，这里漏了）。
        ShopOrder order = requireAdminOrder(orderNo);
        return refundableAmount(order.getOrderNo());
    }

    @Override
    public BigDecimal refundableAmount(String orderNo) {
        ShopOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo)
                .last("LIMIT 1"));
        if (order == null) {
            return MoneyUtils.zero();
        }
        BigDecimal paid = MoneyUtils.scale(order.getPaidAmount());
        // 还要扣掉"已申请但尚未成功"的金额：否则用户可以对同一笔钱申请两次退款，
        // 第二次审核时才会被额度锁拒绝，但用户体验很差（等了半天被告知退不了）
        BigDecimal pending = BigDecimal.ZERO;
        List<ShopOrderRefund> pendingRefunds = refundMapper.selectList(
                new LambdaQueryWrapper<ShopOrderRefund>()
                        .eq(ShopOrderRefund::getOrderId, order.getId())
                        .in(ShopOrderRefund::getStatus, java.util.Arrays.asList(
                                RefundOrderStatus.APPLYING.name(), RefundOrderStatus.REFUNDING.name())));
        for (ShopOrderRefund refund : pendingRefunds) {
            pending = pending.add(MoneyUtils.scale(refund.getRefundAmount()));
        }
        BigDecimal refundable = MoneyUtils.subtract(
                MoneyUtils.subtract(paid, MoneyUtils.scale(order.getRefundAmount())), pending);
        return refundable.compareTo(BigDecimal.ZERO) < 0 ? MoneyUtils.zero() : refundable;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private void assertRefundable(ShopOrder order) {
        if (PayStatus.UNPAID.name().equals(order.getPayStatus())) {
            throw new JeecgBootException("订单未支付，无需退款");
        }
        OrderStatus status = OrderStatus.parse(order.getOrderStatus());
        if (status == null) {
            throw new JeecgBootException("订单状态异常，无法退款，请联系客服");
        }
        if (status == OrderStatus.CANCELLED || status == OrderStatus.CLOSED) {
            throw new JeecgBootException("订单已结束，无法申请退款");
        }
    }

    private ShopOrder requireOwnedOrder(OrderContext context, String orderNo) {
        if (context == null || StringUtils.isBlank(context.getUserId())) {
            throw new JeecgBootException("请先登录");
        }
        ShopOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo)
                .eq(ShopOrder::getUserId, context.getUserId())
                .eq(ShopOrder::getStoreId, context.getStoreId())
                .last("LIMIT 1"));
        if (order == null) {
            throw new JeecgBootException("订单不存在或无权操作");
        }
        return order;
    }

    private ShopOrderRefund requireOwnedRefund(OrderContext context, String refundNo) {
        if (context == null || StringUtils.isBlank(context.getUserId())) {
            throw new JeecgBootException("请先登录");
        }
        ShopOrderRefund refund = refundMapper.selectOne(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getRefundNo, refundNo)
                .eq(ShopOrderRefund::getUserId, context.getUserId())
                .last("LIMIT 1"));
        if (refund == null) {
            throw new JeecgBootException("退款单不存在或无权操作");
        }
        return refund;
    }

    private ShopOrderRefund requireAdminRefund(String refundNo) {
        ShopOrderRefund refund = getByRefundNo(refundNo);
        if (refund == null) {
            throw new JeecgBootException("退款单不存在：" + refundNo);
        }
        orgScopeResolver.checkAccessible(refund.getStoreOrgCode(), "退款单");
        return refund;
    }

    /**
     * 后台口径的订单归属校验：与 adminDetail / 后台订单接口使用同一套
     * 「可见组织范围」规则（见 ShopOrgScopeResolver#checkAccessible）。
     */
    private ShopOrder requireAdminOrder(String orderNo) {
        ShopOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo)
                .last("LIMIT 1"));
        if (order == null) {
            throw new JeecgBootException("订单不存在：" + orderNo);
        }
        orgScopeResolver.checkAccessible(order.getStoreOrgCode(), "订单");
        return order;
    }

    /** 重新计算并回写订单的售后状态（拒绝/撤销/失败之后要还原） */
    private void refreshOrderRefundStatus(String orderId, String operator) {
        ShopOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        BigDecimal paid = MoneyUtils.scale(order.getPaidAmount());
        BigDecimal refunded = MoneyUtils.scale(order.getRefundAmount());
        String refundStatus = RefundStatus.NONE.name();
        String payStatus = order.getPayStatus();
        if (MoneyUtils.gte(refunded, paid) && MoneyUtils.isPositive(paid)) {
            refundStatus = RefundStatus.FULL.name();
            payStatus = PayStatus.REFUNDED.name();
        } else if (MoneyUtils.isPositive(refunded)) {
            refundStatus = RefundStatus.PARTIAL.name();
            payStatus = PayStatus.PARTIAL_REFUND.name();
        } else {
            refundStatus = RefundStatus.NONE.name();
            payStatus = MoneyUtils.isPositive(paid) ? PayStatus.PAID.name() : PayStatus.UNPAID.name();
        }
        Long processing = refundMapper.selectCount(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getOrderId, orderId)
                .in(ShopOrderRefund::getStatus, java.util.Arrays.asList(
                        RefundOrderStatus.APPLYING.name(), RefundOrderStatus.REFUNDING.name())));
        if (processing != null && processing > 0) {
            refundStatus = RefundStatus.APPLYING.name();
        }
        orderMapper.update(new ShopOrder()
                        .setRefundStatus(refundStatus)
                        .setPayStatus(payStatus)
                        .setUpdateBy(operator)
                        .setUpdateTime(new Date()),
                new LambdaUpdateWrapper<ShopOrder>().eq(ShopOrder::getId, orderId));
    }

    private List<ShopOrderRefundItem> listItems(String refundId) {
        if (StringUtils.isBlank(refundId)) {
            return new ArrayList<>();
        }
        return refundItemMapper.selectList(new LambdaQueryWrapper<ShopOrderRefundItem>()
                .eq(ShopOrderRefundItem::getRefundId, refundId));
    }

    private List<OrderRefundVO> toVOs(List<ShopOrderRefund> refunds) {
        List<OrderRefundVO> list = new ArrayList<>();
        if (refunds == null) {
            return list;
        }
        for (ShopOrderRefund refund : refunds) {
            list.add(toVO(refund, null));
        }
        return list;
    }

    private OrderRefundVO toVO(ShopOrderRefund refund, List<ShopOrderRefundItem> items) {
        if (refund == null) {
            return null;
        }
        OrderRefundVO vo = new OrderRefundVO();
        org.springframework.beans.BeanUtils.copyProperties(refund, vo);
        vo.setStatusLabel(RefundOrderStatus.of(refund.getStatus()).getLabel());
        vo.setRefundTypeLabel("FULL".equals(refund.getRefundType()) ? "全额退款" : "部分退款");
        vo.setItems(items == null ? listItems(refund.getId()) : items);
        return vo;
    }

    private List<String> resolveScope(String storeId) {
        List<String> visible = orgScopeResolver.resolveVisibleOrgCodes();
        if (StringUtils.isBlank(storeId)) {
            return visible;
        }
        String storeOrgCode = orgScopeResolver.resolveStoreOrgCode(storeId);
        return orgScopeResolver.mergeScope(storeOrgCode, visible);
    }

    private void applyOrgScope(LambdaQueryWrapper<ShopOrderRefund> wrapper, List<String> scope) {
        if (scope == null) {
            return;
        }
        if (scope.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(w -> {
            for (int i = 0; i < scope.size(); i++) {
                if (i > 0) {
                    w.or();
                }
                w.likeRight(ShopOrderRefund::getStoreOrgCode, scope.get(i));
            }
        });
    }

    // ==================================================================
    // 内部数据结构
    // ==================================================================

    private static class Plan {
        private ShopOrderPayment payment;
        private List<RefundLine> lines = new ArrayList<>();
        private BigDecimal refundAmount = BigDecimal.ZERO;
        private int totalQuantity;
        private boolean fullRefund;
    }

    private static class RefundLine {
        private ShopOrderItem orderItem;
        private int quantity;
        private BigDecimal refundAmount;
    }
}
