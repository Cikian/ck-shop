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
 * 订单主表（ck_shop_order）—— 一店一单，严禁物理删除
 * </p>
 *
 * <h3>金额字段的口径（必须严格区分，混用必出对账差异）</h3>
 * <pre>
 *   total_amount       吊牌价合计        Σ original_price × qty      （只用于展示"省了多少"）
 *   product_amount     成交金额合计      Σ price × qty               （优惠前）
 *   discount_amount    优惠合计          促销 + 券 + 积分 + 会员
 *   freight_amount     运费
 *   pay_amount         应付 = product_amount - discount_amount + freight_amount + other_fee
 *   paid_amount        实收（以支付单成功金额回写，**不由前端传**）
 *   refund_amount      累计已退（退款成功的累加值，退款额度校验的唯一依据）
 * </pre>
 * 其中 {@code pay_amount} 与 {@code paid_amount} **必须分开**：
 * 未支付时应付额已经确定但实收为 0；部分付款（组合支付）时两者不等；
 * 只有把它们分开，才能表达"订单要收 100、实际收到 80"这种真实状态。
 *
 * <h3>为什么 IdType.ASSIGN_ID 而不是自增</h3>
 * 订单 ID 会出现在前端 URL 与日志里。自增 ID 会泄露经营数据（单量），
 * 也让"改一个数字看别人的订单"变得容易。用雪花 ID + 独立业务单号 {@code orderNo}。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_order")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "订单")
public class ShopOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    // ==================================================================
    // 标识
    // ==================================================================

    /** 业务订单号，对外唯一可见的标识 */
    @Schema(description = "订单号")
    private String orderNo;

    /** 父订单号：为未来"跨店合单/拆单"预留，当前恒为空 */
    @Schema(description = "父订单号(拆单/合单预留)")
    private String parentOrderNo;

    @Schema(description = "是否子单: 0-否, 1-是")
    private Integer splitFlag;

    /**
     * 下单幂等键。
     *
     * <p>由客户端（前端）在进入结算页时生成并在提交时携带。
     * 用户连点两次提交、或网络超时后重试，都会带同一个键，
     * 数据库唯一索引会挡住第二次插入，服务端据此返回**同一笔订单**而不是报错。
     * 这比"查一下有没有重复订单"可靠得多——后者在并发下必然漏。
     */
    @Schema(description = "下单幂等键")
    private String idempotentKey;

    // ==================================================================
    // 归属（双维度）
    // ==================================================================

    @Schema(description = "店铺ID")
    private String storeId;

    /** 店铺名称快照：店铺改名不应改变历史订单展示 */
    @Schema(description = "店铺名称快照")
    private String storeName;

    /**
     * 店铺归属部门编码快照 —— 后台数据权限隔离维度。
     *
     * <p>与商品/分类用 {@code sys_org_code} 隔离保持一致，但**订单额外冗余一份**：
     * 订单是财务数据，统计查询量极大，如果每次都 join 店铺表取部门编码，
     * 既慢又容易因为店铺改绑部门而导致历史订单"换了归属"。
     */
    @Schema(description = "店铺部门编码快照(数据权限隔离维度)")
    private String storeOrgCode;

    // ==================================================================
    // 用户
    // ==================================================================

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "用户账号(冗余)")
    private String username;

    // ==================================================================
    // 状态
    // ==================================================================

    @Schema(description = "订单状态: WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED")
    private String orderStatus;

    @Schema(description = "支付状态: UNPAID/PAID/PARTIAL_REFUND/REFUNDED")
    private String payStatus;

    @Schema(description = "退款状态: NONE/APPLYING/REJECTED/PARTIAL/FULL")
    private String refundStatus;

    /**
     * 乐观锁版本。
     *
     * <p>所有状态变更一律是
     * {@code UPDATE ... SET order_status=?, version=version+1 WHERE id=? AND order_status=? AND version=?}。
     * 影响行数为 0 即视为并发冲突并失败。
     * <b>绝不允许"先 select 判断状态、再 update"</b>——那中间的时间窗就是掉单与重复发货的来源。
     */
    @Schema(description = "乐观锁版本")
    private Integer version;

    // ==================================================================
    // 金额
    // ==================================================================

    @Schema(description = "商品原价合计(吊牌价×数量)")
    private BigDecimal totalAmount;

    @Schema(description = "商品成交金额合计(优惠前)")
    private BigDecimal productAmount;

    @Schema(description = "促销活动优惠合计")
    private BigDecimal promotionDiscount;

    @Schema(description = "优惠券抵扣(预留)")
    private BigDecimal couponDiscount;

    @Schema(description = "积分抵扣(预留)")
    private BigDecimal pointsDiscount;

    @Schema(description = "会员优惠(预留)")
    private BigDecimal memberDiscount;

    @Schema(description = "运费")
    private BigDecimal freightAmount;

    @Schema(description = "其它费用(预留)")
    private BigDecimal otherFee;

    /** 优惠合计：冗余落库，保证所有统计口径用的是同一个数，而不是各处自己加 */
    @Schema(description = "优惠合计(促销+券+积分+会员)")
    private BigDecimal discountAmount;

    @Schema(description = "应付金额 = 成交金额 - 优惠 + 运费 + 其它")
    private BigDecimal payAmount;

    @Schema(description = "已支付金额(由支付单回写)")
    private BigDecimal paidAmount;

    @Schema(description = "累计已退款金额")
    private BigDecimal refundAmount;

    @Schema(description = "商品成本合计(毛利统计)")
    private BigDecimal costAmount;

    @Schema(description = "毛利(统计冗余)")
    private BigDecimal grossProfit;

    @Schema(description = "币种")
    private String currency;

    // ==================================================================
    // 数量（统计维度）
    // ==================================================================

    @Schema(description = "商品种类数")
    private Integer itemCount;

    @Schema(description = "商品总件数")
    private Integer totalQuantity;

    @Schema(description = "总重量(kg)")
    private BigDecimal totalWeight;

    // ==================================================================
    // 收货地址快照
    // ==================================================================

    @Schema(description = "收货人姓名(快照)")
    private String receiverName;

    /**
     * 收货人手机号（快照，明文）。
     *
     * <p>发货必须用完整号码，所以订单里存明文；
     * 后台列表 / 操作流水 / 日志一律经 {@code ShopMaskUtils} 脱敏后再出参。
     */
    @Schema(description = "收货人手机号(快照)")
    private String receiverPhone;

    /**
     * 国家码（ISO 3166-1 alpha-2，快照）。
     *
     * <p>订单里存的必须是<b>快照而不是外键</b>：{@code countries} 表是上游整表重灌的
     * 参考数据，没有外键（也不允许建，见 db 脚本注释），而且国家改名/改码的历史事实
     * 不该被"现在表里是什么"覆盖。物流对账按这一列分区，所以它能空（老订单）但不能猜。
     */
    @Schema(description = "国家码(ISO alpha-2, 快照; 老订单为空)")
    private String receiverCountryCode;

    /** 国名快照（当地语言）：面单/报关单要印的是这个，不是界面译名 */
    @Schema(description = "国名(当地语言快照)")
    private String receiverCountry;

    /** 省/州编码快照（ISO 3166-2，或服务端回退的 "S"+id） */
    @Schema(description = "省/州编码(ISO 3166-2, 快照)")
    private String receiverProvinceCode;

    /** 城市 id 快照（{@code cities.id}）；手填城市时为空 */
    @Schema(description = "城市ID(cities.id, 快照; 手填时为空)")
    private Integer receiverCityId;

    @Schema(description = "省(快照)")
    private String receiverProvince;

    @Schema(description = "市(快照)")
    private String receiverCity;

    @Schema(description = "区/县(快照)")
    private String receiverDistrict;

    @Schema(description = "详细地址(快照)")
    private String receiverAddress;

    @Schema(description = "邮编(快照)")
    private String receiverPostalCode;

    @Schema(description = "地址标签(快照)")
    private String receiverTag;

    /** 完整地址 JSON 快照（含地址簿 ID 与全部原始字段），用于事后逐字段核对 */
    @Schema(description = "完整地址JSON快照")
    private String addressSnapshot;

    // ==================================================================
    // 支付
    // ==================================================================

    @Schema(description = "支付渠道: MOCK/WECHAT/ALIPAY")
    private String payChannel;

    @Schema(description = "支付方式细分: JSAPI/NATIVE/H5/APP")
    private String payType;

    @Schema(description = "最近一笔支付单号")
    private String paymentNo;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "支付截止时间(超时关单依据)")
    private Date payDeadline;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "支付成功时间")
    private Date payTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "发货时间")
    private Date shipTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "确认收货时间")
    private Date receiveTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "取消时间")
    private Date cancelTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "超时关闭时间")
    private Date closeTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "进入终态的时间")
    private Date finishTime;

    // ==================================================================
    // 物流
    // ==================================================================

    @Schema(description = "物流公司")
    private String logisticsCompany;

    @Schema(description = "物流单号")
    private String logisticsNo;

    @Schema(description = "发货备注")
    private String shipRemark;

    // ==================================================================
    // 取消
    // ==================================================================

    @Schema(description = "取消来源: USER/ADMIN/TIMEOUT/REFUND")
    private String cancelType;

    @Schema(description = "取消原因")
    private String cancelReason;

    // ==================================================================
    // 来源与统计
    // ==================================================================

    @Schema(description = "下单来源: web/h5/app/api")
    private String orderSource;

    @Schema(description = "下单IP")
    private String clientIp;

    @Schema(description = "User-Agent")
    private String userAgent;

    @Schema(description = "来源页面")
    private String referrer;

    @Schema(description = "渠道/推广位编码")
    private String channelCode;

    @Schema(description = "UTM来源")
    private String utmSource;

    // ==================================================================
    // 备注
    // ==================================================================

    @Schema(description = "买家备注")
    private String buyerRemark;

    @Schema(description = "卖家备注(后台填写)")
    private String sellerRemark;

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

    /**
     * 归档标记。
     *
     * <p><b>不是删除标记。</b>
     * <ul>
     *   <li>有效订单（待支付/待发货/已发货）**禁止归档**，归档接口会硬校验状态；</li>
     *   <li>已取消/已关闭的订单可以归档，归档只是"不再出现在默认列表里"，
     *       订单及其全部明细、流水、资金记录仍然完整保留，可按单号检索；</li>
     *   <li>任何情况下都**不提供物理删除入口**，DDL 层也不做级联删除。</li>
     * </ul>
     *
     * <p>刻意**不加 {@code @TableLogic}**：加了之后 {@code selectById} 会静默返回 null，
     * 财务/客服按单号查一张归档订单会得到"订单不存在"——对交易系统而言，
     * "查不到"比"查得到"危险得多。过滤条件一律显式写在查询里。
     */
    @Schema(description = "归档标记: 0-正常, 1-已归档")
    private Integer delFlag;
}
