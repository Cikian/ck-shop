package org.jeecg.modules.shop.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;
import org.jeecg.modules.shop.order.entity.vo.OrderPrepayVO;
import org.jeecg.modules.shop.order.pay.model.PaymentModels;
import org.jeecg.modules.shop.order.service.OrderContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 支付 Service —— 资金收付的唯一入口。
 * </p>
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li>创建/维护支付单（与订单分离的资金载体）；</li>
 *   <li>调渠道预支付、解析回调、主动查询、关闭渠道单；</li>
 *   <li>把"资金事实"落到订单上（订单状态、已支付金额、库存实扣、资金流水）。</li>
 * </ul>
 *
 * <h3>刻意不依赖订单 Service</h3>
 * 支付要改订单，订单在关单时又要查支付结果，天然互相依赖。
 * 这里选择让**订单依赖支付**（单向），支付侧直接操作
 * {@code ShopOrderMapper} 完成订单状态更新。
 * 好处是不会出现 Spring 的循环依赖，也不会出现
 * "支付成功到底该调订单的哪个方法"这种跨层调用链。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public interface IShopPaymentService extends IService<ShopOrderPayment> {

    /**
     * 发起支付。
     *
     * <p><b>幂等</b>：同一订单上已有"未结算"的支付单时直接复用它，
     * 不会因为用户连点两次产生两笔支付单。
     * 两笔有效支付单最坏的结果是用户付两次、系统只认一次，退款麻烦且体验极差。
     */
    OrderPrepayVO prepay(OrderContext context, String orderNo, String channel,
                         String payType, String requestId);

    /**
     * 渠道异步回调统一入口。
     *
     * <p>处理链：验签 → 落回调流水（RECEIVED，占住唯一去重键）→ 独立事务结算 →
     * 结算提交后把流水改为 PROCESSED → 返回渠道要求的应答。
     *
     * <p><b>事务语义：</b>本方法自身不开事务；结算在
     * {@code ShopPaymentSettlementService} 的 {@code REQUIRES_NEW} 事务里完成，
     * 要么全部提交、要么全部回滚。结算失败时会把流水置 FAILED 并<b>释放去重键</b>，
     * 所以渠道重推不会被误判成"重复回调"。
     *
     * <p><b>重复回调必须返回成功应答</b>：渠道收到失败应答会一直重推，
     * 形成"重推 → 处理失败 → 再重推"的死循环。识别为重复后直接 ACK。
     *
     * @return 处理结果（含应答体与是否需要重试）
     */
    NotifyHandleResult handleNotify(String channel, Map<String, String> headers,
                                    String body, String clientIp);

    /**
     * 主动向渠道查询并据此结算（四层兜底的第二层）。
     *
     * <p>适用场景：渠道说"处理中"、回调丢失、系统重启期间回调没收到。
     * 只有它能发现"钱已经收了但系统不知道"的掉单。
     *
     * @return true = 查询后确认支付成功并完成了结算
     */
    boolean queryAndSettle(ShopOrderPayment payment, String operator);

    /**
     * 关闭支付单（本地 + 渠道）。
     *
     * <p>关闭前会先向渠道查一次：如果渠道说已支付，**不关闭**并返回 false，
     * 由调用方转为支付成功处理。这是超时关单场景下防掉单的关键一步。
     */
    boolean closePayment(ShopOrderPayment payment, String reason, String operator);

    /**
     * 模拟支付确认（**仅 MOCK 渠道可用**）。
     *
     * <p>它不做任何业务处理，只是让模拟渠道生成一条**已签名**的回调，
     * 然后喂进 {@link #handleNotify}。因此开发期走的就是生产期的同一条链路。
     */
    boolean mockConfirm(String paymentNo, OrderContext context);

    /** 取订单的全部支付单（按创建时间正序，便于看到"尝试了几次"） */
    List<ShopOrderPayment> listByOrderId(String orderId);

    /** 取订单当前仍处于未结算状态的支付单（关单、补单用） */
    List<ShopOrderPayment> listSettleable(String orderId);

    /**
     * 取该订单可用于退款的支付单（{@code SUCCESS} 且还有可退额度）。
     *
     * @return null 表示无可退（未支付，或已全额退完）
     */
    ShopOrderPayment findRefundablePayment(String orderId);

    /** 剩余可退金额 = 实收 − 累计已退 */
    BigDecimal refundableAmount(ShopOrderPayment payment);

    /** 按支付单号查询 */
    ShopOrderPayment getByPaymentNo(String paymentNo);

    /**
     * 回调处理结果。
     */
    class NotifyHandleResult {
        /** 是否已被成功受理（含"识别为重复回调"） */
        private final boolean accepted;
        /** 应答渠道的报文 */
        private final String responseBody;
        /** 说明，用于日志与排查 */
        private final String message;

        public NotifyHandleResult(boolean accepted, String responseBody, String message) {
            this.accepted = accepted;
            this.responseBody = responseBody;
            this.message = message;
        }

        public static NotifyHandleResult ok(String responseBody, String message) {
            return new NotifyHandleResult(true, responseBody, message);
        }

        public static NotifyHandleResult reject(String responseBody, String message) {
            return new NotifyHandleResult(false, responseBody, message);
        }

        public boolean isAccepted() {
            return accepted;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public String getMessage() {
            return message;
        }
    }

    /** 供内部与任务使用：把一次渠道查询结果落到支付单与订单上 */
    boolean settleFromQueryResult(ShopOrderPayment payment,
                                 PaymentModels.QueryResult result,
                                 String operator);
}
