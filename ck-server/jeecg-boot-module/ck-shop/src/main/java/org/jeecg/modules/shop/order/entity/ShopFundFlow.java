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
 * 资金流水（ck_shop_fund_flow）—— 只追加的资金账本
 * </p>
 *
 * <h3>为什么不直接看支付单/退款单就够了</h3>
 * 支付单只记"这一笔要收多少、收没收到"，退款单只记"这一笔要退多少、退没退成"。
 * 而**资金账本**关心的是"钱进出账户的总额"。两者的差别在于：
 * <ul>
 *   <li>同一个入口可能被多条路径触发（异步回调、主动查询、人工补单）。
 *       三条路径都可能写支付单，但资金只应入账一次——这靠
 *       {@code uk_fund_biz(biz_type, channel, channel_trade_no)} 这个唯一索引来兜底，
 *       业务层幂等一旦失效，数据库会挡住重复入账；</li>
 *   <li>对账时要按**渠道交易时间**归日（{@code trade_time}）而不是本地入账时间，
 *       否则跨日切的那几分钟会把 T+1 结算算错。所以两个时间都记。</li>
 * </ul>
 *
 * <p>金额带符号：收款为正、退款为负。这样"某店铺某天净流入"
 * 就是一条 {@code SUM(amount)}，不需要按类型分别求和再相减——
 * 后者每加一种新业务类型都要改统计代码，迟早漏掉一种。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_fund_flow")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "资金流水")
public class ShopFundFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "资金流水号")
    private String flowNo;

    @Schema(description = "业务类型: PAY_IN收款/REFUND_OUT退款")
    private String bizType;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "支付单号")
    private String paymentNo;

    @Schema(description = "退款单号")
    private String refundNo;

    @Schema(description = "店铺ID")
    private String storeId;

    @Schema(description = "店铺部门编码")
    private String storeOrgCode;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "渠道")
    private String channel;

    @Schema(description = "渠道流水号")
    private String channelTradeNo;

    @Schema(description = "金额(带符号: 收款为正, 退款为负)")
    private BigDecimal amount;

    @Schema(description = "资金方向: IN/OUT")
    private String direction;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "流水状态: SUCCESS/FAILED")
    private String status;

    /** 渠道交易时间：对账按此归日，避免本地时间与渠道时间跨日不一致 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "渠道交易时间")
    private Date tradeTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "入账时间(本地)")
    private Date syncTime;

    @Schema(description = "备注")
    private String remark;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private Date createTime;
}
