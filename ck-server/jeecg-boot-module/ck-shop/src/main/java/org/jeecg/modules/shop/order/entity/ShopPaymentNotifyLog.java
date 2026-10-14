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
import java.util.Date;

/**
 * <p>
 * 渠道回调流水（ck_shop_payment_notify_log）—— 幂等 + 防重放 + 对账依据
 * </p>
 *
 * <h3>为什么每次回调都要落一条日志，哪怕它是重复的</h3>
 * 文档里"回调重复、乱序、丢失、延迟"是支付模块最典型的问题。
 * 只记"处理成功"的日志，出了问题时无法回答：
 * <ul>
 *   <li>渠道到底推了几次？（{@code notifyCount} 只给计数，这里给每一次的报文与时间）</li>
 *   <li>是不是有一次回调验签失败了被我静默丢掉了？</li>
 *   <li>回调是几点到的？延迟多久？</li>
 * </ul>
 * 因此重复回调也要落库，只是 {@code process_status} 记为 {@code DUPLICATE}——
 * 这恰恰是"渠道重复推送"最直接的证据。
 *
 * <h3>dedup_key 的设计</h3>
 * 唯一的去重键必须包含**渠道 + 事件类型 + 渠道流水号 + 渠道事件状态/时间**。
 * 如果只用渠道流水号去重，那么同一笔交易的"支付成功"与"退款成功"两条回调
 * 会被当成重复而丢掉后一条，后果是"钱退了但系统不知道"。
 *
 * <h3>防重放</h3>
 * {@code notify_time} 记的是**渠道事件时间**，配合验签与时间窗校验使用；
 * 一个几小时前签发的合法回调被恶意重放时，时间窗会拦下它。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_payment_notify_log")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "渠道回调流水")
public class ShopPaymentNotifyLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "支付渠道")
    private String channel;

    @Schema(description = "回调类型: PAY支付/REFUND退款")
    private String notifyType;

    @Schema(description = "支付单号")
    private String paymentNo;

    @Schema(description = "退款单号(退款回调时)")
    private String refundNo;

    @Schema(description = "渠道流水号")
    private String channelTradeNo;

    /** 去重键，唯一索引；重复回调插入失败即被识别为重复，不需要先查再插 */
    @Schema(description = "回调去重键")
    private String dedupKey;

    /** 原始报文（脱敏后）：对账与故障排查的第一手材料 */
    @Schema(description = "回调原始报文(脱敏)")
    private String rawBody;

    @Schema(description = "渠道签名")
    private String signature;

    @Schema(description = "验签结果: 0-未通过, 1-通过")
    private Integer signVerified;

    @Schema(description = "处理结果: RECEIVED/PROCESSED/DUPLICATE/REJECTED/FAILED")
    private String processStatus;

    @Schema(description = "处理说明")
    private String processMsg;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "渠道事件时间(防重放校验依据)")
    private Date notifyTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "接收时间")
    private Date receiveTime;

    @Schema(description = "回调来源IP(渠道白名单校验依据)")
    private String clientIp;
}
