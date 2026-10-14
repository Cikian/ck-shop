package org.jeecg.modules.shop.order.pay.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 支付网关 SPI 的入参/出参模型合集。
 * </p>
 *
 * <h3>为什么把 9 个模型塞在一个文件里</h3>
 * 它们只服务于 {@code PaymentGateway} 这一个接口，且**共同构成一份契约**：
 * 看接口时能一眼看到全部出入参，改契约时不会漏掉某个分文件。
 * 拆成 9 个文件除了让目录更长，并不会让代码更清楚。
 *
 * <h3>设计约束（接入真实渠道时必须遵守）</h3>
 * <ol>
 *   <li><b>不含任何加解密细节</b>：密钥、证书、签名算法都在具体实现类内部，
 *       订单/支付业务代码永远看不到它们。渠道换密钥不该导致业务代码重新编译。</li>
 *   <li><b>金额一律 BigDecimal</b>：渠道报文里的"分"与"元"转换只在实现类里做一次，
 *       这是最容易出现 100 倍错误的边界。</li>
 *   <li><b>结果必须显式三态</b>：成功 / 失败 / <b>未知</b>。
 *       渠道超时是"不知道成功没有"，不能粗暴当成失败——
 *       当成失败去关单，就制造了"钱扣了订单关了"的掉单事故。</li>
 * </ol>
 *
 * @author Cikian
 * @since 2026-10-14
 */
public final class PaymentModels {

    private PaymentModels() {
    }

    // ==================================================================
    // 下单（预支付）
    // ==================================================================

    @Data
    @Accessors(chain = true)
    public static class PrepayRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 我方支付单号，作为渠道 out_trade_no。**必须唯一且稳定**，是回调的关联键 */
        private String paymentNo;
        private String orderNo;
        /** 应付金额（元） */
        private BigDecimal amount;
        private String currency = "CNY";
        /** 商品标题（渠道账单展示用，超长渠道会截断，不参与业务） */
        private String subject;
        private String description;
        /** 支付方式细分：JSAPI/NATIVE/H5/APP */
        private String payType;
        /** 异步通知地址（渠道回调本系统用） */
        private String notifyUrl;
        /** 同步跳转地址（用户支付完回到哪个页面，**不可作为支付结果依据**） */
        private String returnUrl;
        private String clientIp;
        /** 支付单过期时间：渠道侧也应同步失效，否则用户能"隔天再付" */
        private Date expireTime;
        private Map<String, Object> extra = new HashMap<>();
    }

    @Data
    @Accessors(chain = true)
    public static class PrepayResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 是否成功向渠道下单（不是"用户付款成功"） */
        private boolean success;
        /** 渠道预支付流水号（微信 prepay_id / 支付宝 trade_no） */
        private String channelTradeNo;
        /**
         * 前端调起支付所需参数。
         * 真实渠道这里放的是 {@code timeStamp/nonceStr/package/signType/paySign} 等，
         * **由网关实现拼装**，前端原样透传给渠道 SDK，业务代码不参与构造。
         */
        private Map<String, String> payParams = new HashMap<>();
        private String errorCode;
        private String errorMsg;

        public static PrepayResult ok() {
            return new PrepayResult().setSuccess(true);
        }

        public static PrepayResult fail(String code, String msg) {
            return new PrepayResult().setSuccess(false).setErrorCode(code).setErrorMsg(msg);
        }
    }

    // ==================================================================
    // 异步回调
    // ==================================================================

    @Data
    @Accessors(chain = true)
    public static class NotifyRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 回调原始请求头（验签常需要 Wechatpay-Signature / Alipay-Signature 等） */
        private Map<String, String> headers = new HashMap<>();
        /** 回调原始报文 */
        private String body;
        private String clientIp;
    }

    /**
     * 回调解析结果。
     *
     * <p>{@code signatureValid = false} 时**其余字段一律不可信**，
     * 调用方必须直接拒绝，绝不能"先看看内容再说"。
     */
    @Data
    @Accessors(chain = true)
    public static class NotifyResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 验签是否通过。这是整条回调链路的唯一门禁 */
        private boolean signatureValid;
        /** 回调类型：PAY / REFUND */
        private String notifyType;
        /** 我方支付单号 */
        private String paymentNo;
        /** 我方退款单号（退款回调时有值） */
        private String refundNo;
        /** 渠道流水号 */
        private String channelTradeNo;
        /** 渠道事件状态：SUCCESS / FAILED / CLOSED。用于构造去重键 */
        private String eventStatus;
        /** 渠道实付金额（元）。与订单金额不一致时必须挂异常单，而不是照单全收 */
        private BigDecimal paidAmount;
        /** 渠道退款金额（元） */
        private BigDecimal refundAmount;
        /** 渠道事件时间（**防重放校验与对账归日都用它，不用本地时间**） */
        private Date eventTime;
        /** 验签失败或解析失败的原因（仅内部日志用） */
        private String failReason;

        public static NotifyResult invalid(String reason) {
            return new NotifyResult().setSignatureValid(false).setFailReason(reason);
        }

        /**
         * 去重键：渠道 + 事件类型 + 渠道流水号 + 事件状态 + 事件时间。
         *
         * <p>必须包含事件状态与时间：同一笔交易会推"支付成功""支付关闭""退款成功"
         * 多条合法回调，只用流水号去重会把后面那些当成重复丢掉。
         */
        public String dedupKey() {
            return String.join("|",
                    channelTradeNo == null ? "" : channelTradeNo,
                    notifyType == null ? "" : notifyType,
                    eventStatus == null ? "" : eventStatus,
                    eventTime == null ? "" : String.valueOf(eventTime.getTime()),
                    paymentNo == null ? "" : paymentNo);
        }
    }

    // ==================================================================
    // 主动查询（四层兜底里的第二层）
    // ==================================================================

    @Data
    @Accessors(chain = true)
    public static class QueryRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        private String paymentNo;
        private String channelTradeNo;
    }

    /**
     * 查询结果。{@code tradeStatus} 是三态：
     * {@code SUCCESS / FAILED / PENDING(结果未知) / NOT_FOUND(渠道查无此单)}。
     * <p>{@code PENDING} 必须被当作"还没定论"继续等待，不能等同于失败。
     */
    @Data
    @Accessors(chain = true)
    public static class QueryResult implements Serializable {
        private static final long serialVersionUID = 1L;

        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String PENDING = "PENDING";
        public static final String NOT_FOUND = "NOT_FOUND";

        private String tradeStatus;
        private String channelTradeNo;
        private BigDecimal paidAmount;
        /** 渠道交易时间，用于资金流水归日 */
        private Date payTime;
        private String errorCode;
        private String errorMsg;

        public static QueryResult of(String status) {
            return new QueryResult().setTradeStatus(status);
        }
    }

    // ==================================================================
    // 退款
    // ==================================================================

    @Data
    @Accessors(chain = true)
    public static class RefundRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        private String refundNo;
        private String paymentNo;
        private String channelTradeNo;
        /** 原订单总金额（部分渠道退款接口必须传，且要求与支付时一致） */
        private BigDecimal totalAmount;
        private BigDecimal refundAmount;
        private String currency = "CNY";
        private String reason;
        private String notifyUrl;
    }

    @Data
    @Accessors(chain = true)
    public static class RefundResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /** SUCCESS / FAILED / PROCESSING(受理中，结果未知) */
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String PROCESSING = "PROCESSING";

        private String refundStatus;
        private String channelRefundNo;
        private Date refundTime;
        private String errorCode;
        private String errorMsg;

        public boolean isSuccess() {
            return SUCCESS.equals(refundStatus);
        }

        public boolean isProcessing() {
            return PROCESSING.equals(refundStatus);
        }

        public static RefundResult fail(String code, String msg) {
            return new RefundResult().setRefundStatus(FAILED).setErrorCode(code).setErrorMsg(msg);
        }

        public static RefundResult processing(String channelRefundNo) {
            return new RefundResult().setRefundStatus(PROCESSING).setChannelRefundNo(channelRefundNo);
        }

        public static RefundResult success(String channelRefundNo, Date refundTime) {
            return new RefundResult().setRefundStatus(SUCCESS)
                    .setChannelRefundNo(channelRefundNo).setRefundTime(refundTime);
        }
    }

    // ==================================================================
    // 关单
    // ==================================================================

    @Data
    @Accessors(chain = true)
    public static class CloseRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        private String paymentNo;
        private String channelTradeNo;
        private String reason;
    }
}
