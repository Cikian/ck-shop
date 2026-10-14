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
 * 退款单（ck_shop_order_refund）
 * </p>
 *
 * <h3>为什么退款单必须独立成表</h3>
 * <ul>
 *   <li><b>多次部分退款是常态</b>：一单三件商品退了两件，剩下的还要能退。
 *       把退款塞进支付单的一个 {@code refunded} 布尔字段，只能表达"全退"。</li>
 *   <li><b>审核是有过程的</b>：用户申请 → 客服审核 → 提交渠道 → 到账，
 *       每一步都可能失败、都要能查询、都要能重试。这是独立的状态机。</li>
 *   <li><b>渠道退款有自己的流水号</b>（{@code out_refund_no} / {@code refund_id}），
 *       对账时"渠道退款流水 vs 我方退款单"必须能一一对应。</li>
 * </ul>
 *
 * <p><b>退款额度不在这张表上做校验</b>，而是回到支付单上做原子累加（见
 * {@code ShopOrderPayment#refundAmount} 的注释）。原因是"并发"：
 * 两个客服同时对同一笔支付发起退款，如果各自 {@code select sum(refund_amount)} 再判断，
 * 两边都会看到"还够"，然后都提交——累计退款超过支付金额。
 * 只有把额度校验压进一条带条件的 UPDATE，才真正防得住。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_order_refund")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "退款单")
public class ShopOrderRefund implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    /** 本系统退款单号，同时作为提交给渠道的 {@code out_refund_no} */
    @Schema(description = "退款单号(=渠道 out_refund_no)")
    private String refundNo;

    @Schema(description = "订单ID")
    private String orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "支付单ID")
    private String paymentId;

    @Schema(description = "支付单号")
    private String paymentNo;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "用户账号")
    private String username;

    @Schema(description = "店铺ID")
    private String storeId;

    @Schema(description = "店铺部门编码")
    private String storeOrgCode;

    // ==================================================================
    // 退款内容
    // ==================================================================

    @Schema(description = "退款类型: FULL全额/PARTIAL部分")
    private String refundType;

    @Schema(description = "申请退款金额")
    private BigDecimal refundAmount;

    @Schema(description = "退款商品种类数")
    private Integer itemCount;

    @Schema(description = "退款商品件数")
    private Integer totalQuantity;

    @Schema(description = "申请原因(字典 shop_refund_reason)")
    private String applyReason;

    @Schema(description = "申请说明")
    private String applyDesc;

    /** 凭证图片 JSON 数组：售后纠纷时是唯一的举证材料 */
    @Schema(description = "凭证图片(JSON数组)")
    private String applyImages;

    // ==================================================================
    // 状态与审核
    // ==================================================================

    @Schema(description = "退款状态: APPLYING/REJECTED/REFUNDING/SUCCESS/FAILED/CLOSED")
    private String status;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "申请时间")
    private Date applyTime;

    @Schema(description = "审核人")
    private String auditBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "审核时间")
    private Date auditTime;

    @Schema(description = "审核备注")
    private String auditRemark;

    @Schema(description = "拒绝原因")
    private String rejectReason;

    // ==================================================================
    // 渠道退款结果
    // ==================================================================

    @Schema(description = "渠道退款流水号")
    private String channelRefundNo;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "退款到账时间(渠道时间)")
    private Date refundTime;

    @Schema(description = "退款失败码")
    private String failCode;

    @Schema(description = "退款失败原因")
    private String failReason;

    @Schema(description = "渠道退款重试次数")
    private Integer retryCount;

    /**
     * 幂等键。
     *
     * <p>用户在"申请退款"按钮上连点两次、或网络重试，只会产生一张退款单。
     * 唯一索引在数据库层兜底，比"查一下有没有申请中的退款"可靠。
     */
    @Schema(description = "幂等键")
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
