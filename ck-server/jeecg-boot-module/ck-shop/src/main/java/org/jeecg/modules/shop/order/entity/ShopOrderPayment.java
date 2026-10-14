package org.jeecg.modules.shop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 支付单（ck_shop_order_payment）—— 与订单分离的资金载体
 * </p>
 *
 * <h3>为什么必须与订单分离（文档第 2 节的核心）</h3>
 * <ul>
 *   <li><b>一笔订单可能有多笔支付尝试</b>：用户第一次调起微信支付后放弃了，
 *       支付单超时关闭；第二次换支付宝成功。只有一个"支付信息"字段的订单表
 *       根本表达不了这个事实。</li>
 *   <li><b>渠道回调是按支付单来的</b>：回调里带的是 {@code out_trade_no}（我方支付单号）
 *       和 {@code transaction_id}（渠道流水号）。订单侧没有这两个号，回调就无从落地。</li>
 *   <li><b>对账是"渠道流水 vs 我方支付单"逐笔比</b>：如果支付信息混在订单里，
 *       对账口径会被订单状态污染，长短款根本查不出来。</li>
 * </ul>
 *
 * <h3>refund_amount 是退款额度的锁</h3>
 * 多次部分退款累计不能超过 {@code amount}。实现方式不是"先求和再判断"（并发下必错），
 * 而是带条件的原子更新：
 * <pre>
 *   UPDATE ck_shop_order_payment
 *      SET refund_amount = refund_amount + #{amt}, version = version + 1
 *    WHERE id = #{id} AND status = 'SUCCESS'
 *      AND refund_amount + #{amt} &lt;= amount
 * </pre>
 * 影响行数为 0 → 说明超额或状态不对，直接拒绝。
 * 这条 UPDATE 就是"退款并发超额"的根治手段。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_order_payment")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "支付单")
public class ShopOrderPayment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    /** 本系统支付单号，同时作为提交给渠道的 {@code out_trade_no} */
    @Schema(description = "支付单号(=渠道 out_trade_no)")
    private String paymentNo;

    @Schema(description = "订单ID")
    private String orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "店铺ID")
    private String storeId;

    @Schema(description = "店铺部门编码")
    private String storeOrgCode;

    // ==================================================================
    // 渠道
    // ==================================================================

    @Schema(description = "支付渠道: MOCK/WECHAT/ALIPAY")
    private String channel;

    @Schema(description = "支付方式细分: JSAPI/NATIVE/H5/APP")
    private String payType;

    @Schema(description = "应付金额(下单时订单应付额)")
    private BigDecimal amount;

    /** 渠道实收金额：以渠道回调为准，用于发现"回调金额与订单金额不一致"的异常单 */
    @Schema(description = "渠道实收金额")
    private BigDecimal paidAmount;

    @Schema(description = "累计已退金额(退款额度校验依据)")
    private BigDecimal refundAmount;

    @Schema(description = "币种")
    private String currency;

    // ==================================================================
    // 状态与渠道流水
    // ==================================================================

    @Schema(description = "支付单状态: INIT/PENDING/SUCCESS/FAILED/CLOSED")
    private String status;

    /** 渠道流水号：对账时与渠道账单比对的主键 */
    @Schema(description = "渠道流水号")
    private String channelTradeNo;

    @Schema(description = "渠道付款方标识(预留)")
    private String channelBuyerId;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "支付单过期时间")
    private Date expireTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "渠道确认支付成功时间")
    private Date payTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "关闭时间")
    private Date closeTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最近一次收到回调的时间")
    private Date notifyTime;

    /** 累计回调次数：渠道普遍重推 5~8 次，这个计数偏高说明回调处理有异常 */
    @Schema(description = "累计收到回调次数")
    private Integer notifyCount;

    /** 最近一次回调报文（脱敏后），排查"回调到底说了什么"的第一手材料 */
    @Schema(description = "最近一次回调报文(脱敏)")
    private String lastNotifyBody;

    @Schema(description = "失败错误码")
    private String errorCode;

    @Schema(description = "失败原因")
    private String errorMsg;

    /** 发起支付的幂等键：同一订单并发点两次支付只应产生一笔有效支付单 */
    @Schema(description = "发起支付幂等键")
    private String requestId;

    @Schema(description = "乐观锁版本")
    private Integer version;

    // ==================================================================
    // 审计
    // ==================================================================

    @Schema(description = "创建人")
    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private Date createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新日期")
    private Date updateTime;

    @Schema(description = "归档标记: 0-正常, 1-已归档")
    private Integer delFlag;
}
