package org.jeecg.modules.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.constant.OperatorType;
import org.jeecg.modules.shop.order.constant.OrderEventType;
import org.jeecg.modules.shop.order.constant.OrderStatus;
import org.jeecg.modules.shop.order.constant.PaymentStatus;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;
import org.jeecg.modules.shop.order.mapper.ShopOrderMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderPaymentMapper;
import org.jeecg.modules.shop.order.service.IShopOrderEventService;
import org.jeecg.modules.shop.order.service.IShopOrderService;
import org.jeecg.modules.shop.order.service.IShopOrderTaskService;
import org.jeecg.modules.shop.order.service.IShopPaymentService;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * 订单补偿任务实现。
 * </p>
 *
 * <h3>为什么"定时任务重复执行"在这套设计里不会造成事故</h3>
 * 文档 §6 提到"定时任务重复执行：补单、关单、对账任务多实例跑"。
 * 本模块的正确性**不依赖任务只跑一次**：
 * <ul>
 *   <li>关单用 CAS（{@code WHERE order_status='WAIT_PAY' AND version=?}），
 *       第二个实例影响 0 行 → 跳过；</li>
 *   <li>补单用支付单 CAS（{@code WHERE status IN ('INIT','PENDING')}），
 *       重复结算 0 行 → 跳过；</li>
 *   <li>资金流水有唯一索引兜底。</li>
 * </ul>
 * 也就是说，多跑几次最多是白跑，不会重复扣库存、重复入账。
 * 另外 Quartz 配置为 JDBC JobStore（集群模式），同一时刻只有一个节点会触发。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@Service
public class ShopOrderTaskServiceImpl implements IShopOrderTaskService {

    @Autowired
    private ShopOrderMapper orderMapper;

    @Autowired
    private ShopOrderPaymentMapper paymentMapper;

    @Autowired
    private IShopOrderService orderService;

    @Autowired
    private IShopPaymentService paymentService;

    @Autowired
    private IShopOrderEventService eventService;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    /** 补单只处理创建超过该分钟数的支付单：太新的单渠道可能还没落账，查了也是"查无此单" */
    @Value("${ck.shop.order.compensate.min-age-minutes:2}")
    private int compensateMinAgeMinutes;

    @Override
    public TaskResult closeTimeoutOrders(int batchSize) {
        long started = System.currentTimeMillis();
        TaskResult result = new TaskResult();
        int limit = normalizeBatch(batchSize);

        List<ShopOrder> overdue = orderMapper.selectList(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderStatus, OrderStatus.WAIT_PAY.name())
                .isNotNull(ShopOrder::getPayDeadline)
                .lt(ShopOrder::getPayDeadline, new Date())
                .orderByAsc(ShopOrder::getPayDeadline)
                .last("LIMIT " + limit));
        result.scanned(overdue.size());
        if (overdue.isEmpty()) {
            return result.costMillis(System.currentTimeMillis() - started)
                    .setMessage("没有超时待支付订单");
        }

        for (ShopOrder order : overdue) {
            try {
                // closeIfOverdue 内部会先向渠道确认支付结果，再 CAS 关单并释放库存。
                // 每笔订单是独立事务：一笔失败不影响其它订单
                if (orderService.closeIfOverdue(order)) {
                    result.succeeded(result.getSucceeded() + 1);
                } else {
                    result.skipped(result.getSkipped() + 1);
                }
            } catch (Exception e) {
                result.failed(result.getFailed() + 1);
                log.error("超时关单失败：orderNo={}, err={}", order.getOrderNo(), e.getMessage(), e);
            }
        }
        result.costMillis(System.currentTimeMillis() - started);
        if (result.getFailed() > 0) {
            log.error("超时关单任务存在失败项（需要关注）：{}", result);
        } else {
            log.info("超时关单任务完成：{}", result);
        }
        return result;
    }

    @Override
    public TaskResult compensatePendingPayments(int batchSize) {
        long started = System.currentTimeMillis();
        TaskResult result = new TaskResult();
        int limit = normalizeBatch(batchSize);

        Date createdBefore = new Date(System.currentTimeMillis()
                - Math.max(compensateMinAgeMinutes, 1) * 60_000L);
        List<ShopOrderPayment> pending = paymentMapper.selectStalePending(createdBefore, limit);
        result.scanned(pending.size());
        if (pending.isEmpty()) {
            return result.costMillis(System.currentTimeMillis() - started)
                    .setMessage("没有待补单的支付单");
        }

        for (ShopOrderPayment payment : pending) {
            try {
                if (paymentService.queryAndSettle(payment, "SYSTEM")) {
                    result.succeeded(result.getSucceeded() + 1);
                    log.warn("【补单成功】主动查询发现支付成功，已补正：paymentNo={}, orderNo={}。"
                                    + "说明此前回调丢失或延迟，请关注渠道回调链路",
                            payment.getPaymentNo(), payment.getOrderNo());
                } else {
                    // 渠道说"处理中"或"查无此单"：不是错误，等超时关单流程兜底
                    result.skipped(result.getSkipped() + 1);
                }
            } catch (Exception e) {
                result.failed(result.getFailed() + 1);
                log.error("补单处理异常：paymentNo={}, err={}", payment.getPaymentNo(), e.getMessage(), e);
            }
        }
        result.costMillis(System.currentTimeMillis() - started);
        log.info("支付补单任务完成：{}", result);
        return result;
    }

    @Override
    public TaskResult compensateOrder(String orderNo) {
        long started = System.currentTimeMillis();
        TaskResult result = new TaskResult();
        if (StringUtils.isBlank(orderNo)) {
            throw new JeecgBootException("缺少订单号");
        }
        ShopOrder order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            throw new JeecgBootException("订单不存在：" + orderNo);
        }
        // 归属校验：这个方法是**后台手动补偿**入口（定时任务走的是另两个批量方法），
        // 之前只按 orderNo 查订单就动手，等于任何持有 shop-order:compensate 的运营
        // 都能对别家店铺的订单触发渠道查询与补正。校验规则与后台其它接口一致。
        orgScopeResolver.checkAccessible(order.getStoreOrgCode(), "订单");
        result.scanned(1);

        // 手动补偿的三步：先查渠道、再尝试补正、最后给出结论
        boolean settled = false;
        List<ShopOrderPayment> settleable = paymentService.listSettleable(order.getId());
        for (ShopOrderPayment payment : settleable) {
            if (paymentService.queryAndSettle(payment, "SYSTEM")) {
                settled = true;
            }
        }

        if (settled) {
            result.succeeded(1).setMessage("渠道确认已支付，订单已补正为待发货");
        } else {
            result.skipped(1).setMessage(settleable.isEmpty()
                    ? "该订单没有处于待支付/初始化状态的支付单，无需补单"
                    : "渠道查询未确认支付成功（可能仍在处理中），订单状态保持不变");
        }

        // 人工补偿也必须留痕：否则事后无法区分"系统自动补的"和"人工补的"
        ShopOrder latest = orderService.getByOrderNo(orderNo);
        eventService.recordNote(latest, OrderEventType.COMPENSATE, OperatorType.ADMIN, "SYSTEM",
                "人工触发订单补偿：" + result.getMessage(),
                "{\"paymentCount\":" + settleable.size() + "}");
        result.costMillis(System.currentTimeMillis() - started);
        return result;
    }

    @Override
    public TaskResult compensatePayment(String paymentNo) {
        long started = System.currentTimeMillis();
        TaskResult result = new TaskResult();
        if (StringUtils.isBlank(paymentNo)) {
            throw new JeecgBootException("缺少支付单号");
        }
        ShopOrderPayment payment = paymentService.getByPaymentNo(paymentNo);
        if (payment == null) {
            throw new JeecgBootException("支付单不存在：" + paymentNo);
        }
        result.scanned(1);
        if (PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            return result.skipped(1).costMillis(System.currentTimeMillis() - started)
                    .setMessage("该支付单已是成功状态，无需补单");
        }
        if (PaymentStatus.CLOSED.name().equals(payment.getStatus())
                || PaymentStatus.FAILED.name().equals(payment.getStatus())) {
            // 已终结的支付单不补单：即使渠道后来变成功，也应走"掉单告警 + 人工处理"路径，
            // 而不是悄悄把它改回成功（那会让"谁改了订单"无法追溯）
            return result.skipped(1).costMillis(System.currentTimeMillis() - started)
                    .setMessage("该支付单已处于终态[" + payment.getStatus() + "]，不自动补单，如有疑问请人工核对");
        }
        boolean settled = paymentService.queryAndSettle(payment, "SYSTEM");
        if (settled) {
            result.succeeded(1).setMessage("渠道确认已支付，订单已补正");
        } else {
            result.skipped(1).setMessage("渠道查询未确认支付成功");
        }
        ShopOrder order = orderService.getByOrderNo(payment.getOrderNo());
        if (order != null) {
            eventService.recordNote(order, OrderEventType.COMPENSATE, OperatorType.ADMIN, "SYSTEM",
                    "人工触发支付单补偿：" + result.getMessage(), null);
        }
        result.costMillis(System.currentTimeMillis() - started);
        return result;
    }

    private int normalizeBatch(int batchSize) {
        if (batchSize < 1) {
            return 100;
        }
        // 上限 500：一次处理太多会让单次任务执行时间过长，
        // 与 Quartz 的下一次触发重叠（虽然有 @DisallowConcurrentExecution 兜底）
        return Math.min(batchSize, 500);
    }
}
