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
 * 订单操作流水（ck_shop_order_event）—— 只追加，不设删除标记
 * </p>
 *
 * <h3>这张表是"数据可追溯"的落点</h3>
 * 文档 §5 明确提到"权限与审计：后台可随意改价、退款、补单，无操作日志"。
 * 订单模块里**每一个改变订单的动作**都必须写一条事件：
 * 下单、发起支付、支付成功/失败、取消、关闭、发货、收货、退款申请/审核/成功、
 * 后台改备注、人工补单、归档。不留日志的能力等于没有追责能力。
 *
 * <h3>为什么整张表没有 del_flag</h3>
 * 它是**账本**，不是业务数据。账本条目被"删除"本身就是数据完整性问题：
 * 恰好是出问题的那条删掉了，事后什么都查不出来。
 * 要纠正一条错误的日志，只能再追加一条说明性的日志。
 * 同理 {@code ck_shop_payment_notify_log}、{@code ck_shop_stock_log}、
 * {@code ck_shop_fund_flow} 也都不设删除标记。
 *
 * <h3>记录什么</h3>
 * 除了"谁在什么时候做了什么"，还刻意记录了：
 * <ul>
 *   <li>{@code fromStatus/toStatus}：状态轨迹可回放，不必靠猜；</li>
 *   <li>{@code amount}：涉及金额的事件把金额单独提出来，一张表就能做资金审计；</li>
 *   <li>{@code clientIp/userAgent}：风控与异常排查（例如同一个 IP 疯狂下单）；</li>
 *   <li>{@code operatorType}：区分"用户干的 / 后台干的 / 系统自动的 / 渠道回调的"——
 *       这是事故复盘时最先要回答的问题。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_order_event")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "订单操作流水")
public class ShopOrderEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "订单ID")
    private String orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "店铺ID")
    private String storeId;

    @Schema(description = "店铺部门编码")
    private String storeOrgCode;

    @Schema(description = "事件类型")
    private String eventType;

    @Schema(description = "变更前订单状态")
    private String fromStatus;

    @Schema(description = "变更后订单状态")
    private String toStatus;

    @Schema(description = "变更前支付状态")
    private String fromPayStatus;

    @Schema(description = "变更后支付状态")
    private String toPayStatus;

    @Schema(description = "操作者类型: USER/ADMIN/SYSTEM/CHANNEL")
    private String operatorType;

    @Schema(description = "操作者ID")
    private String operatorId;

    @Schema(description = "操作者名称")
    private String operatorName;

    @Schema(description = "本次事件涉及金额")
    private BigDecimal amount;

    @Schema(description = "事件说明")
    private String remark;

    /** 事件明细 JSON：金额快照、渠道报文摘要、物流信息等，按事件类型不同而不同 */
    @Schema(description = "事件明细JSON")
    private String detail;

    @Schema(description = "来源IP")
    private String clientIp;

    @Schema(description = "来源UA")
    private String userAgent;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "发生时间")
    private Date createTime;
}
