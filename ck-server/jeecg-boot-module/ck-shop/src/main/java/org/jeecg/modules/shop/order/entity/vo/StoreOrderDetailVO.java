package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 前台订单详情。
 * </p>
 *
 * 比列表多出的内容：
 * <ul>
 *   <li>完整收货地址（买家本人可见，不做脱敏）；</li>
 *   <li>金额明细（把优惠构成摊开，减少"为什么是这个价"的客服咨询）；</li>
 *   <li>支付单与退款单（用户可以自己看到"退了多少钱、什么时候到账"）；</li>
 *   <li>操作流水（**买家也能看到的关键节点**，例如"系统于 xx 时间自动关闭"）。</li>
 * </ul>
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "前台订单详情")
public class StoreOrderDetailVO extends StoreOrderListVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "省")
    private String receiverProvince;

    @Schema(description = "市")
    private String receiverCity;

    /**
     * 地区快照（权威码 + 当地语言国名）。
     *
     * <p>下发给买家的意义：他看到的 {@code receiverProvince/receiverCity} 是当地文字
     * （寄件用的写法），而这两个码能让他/客服确认"这单到底寄到哪个国家的哪个城市"，
     * 也是售后核对运费分区的唯一依据。
     *
     * <p>{@code receiverCountryCode} 为空 = 改造前的历史订单，前端按老格式渲染。
     */
    @Schema(description = "国家码(ISO alpha-2, 快照; 老订单为空)")
    private String receiverCountryCode;

    @Schema(description = "国名(当地语言快照)")
    private String receiverCountry;

    @Schema(description = "省/州编码(ISO 3166-2, 快照)")
    private String receiverProvinceCode;

    @Schema(description = "城市ID(cities.id, 快照; 手填时为空)")
    private Integer receiverCityId;

    @Schema(description = "区/县")
    private String receiverDistrict;

    @Schema(description = "详细地址")
    private String receiverAddress;

    @Schema(description = "邮编")
    private String receiverPostalCode;

    @Schema(description = "地址标签")
    private String receiverTag;

    /**
     * 收货地址整串（当地语言，面单用）。
     *
     * <p><b>只读派生字段</b>：由订单快照列拼出来，不落库、也不回查地址簿——
     * 用户后来改了地址簿不能影响历史订单，这是订单快照的底线。
     * 拼接规则与地址簿 {@code AddressVO.fullAddress} 是<b>同一份实现</b>
     * （{@code ShopAddressTextUtils}）：有国家信息走国际顺序 + 逗号分隔 + 相邻同名去重，
     * 没有国家信息（老订单）走原来的连写格式。
     */
    @Schema(description = "收货地址整串(当地语言, 面单用)")
    private String receiverFullAddress;

    /**
     * 收货地址整串（访客语言，屏幕展示用）。
     *
     * <p>快照里只有当地文字（面单要用的写法），译名是拿
     * {@code receiverCountryCode / receiverProvinceCode / receiverCityId}
     * 反查 {@code translations} <b>现算</b>的；查不到就回退快照原文，绝不给 null。
     *
     * <p>老订单（没有国家信息）时它等于 {@link #receiverFullAddress}，
     * 与改造前的展示串完全一致。
     */
    @Schema(description = "收货地址整串(访客语言, 展示用)")
    private String receiverFullAddressLocalized;

    @Schema(description = "商品原价合计")
    private BigDecimal totalAmount;

    @Schema(description = "商品成交金额合计")
    private BigDecimal productAmount;

    @Schema(description = "促销优惠")
    private BigDecimal promotionDiscount;

    @Schema(description = "支付渠道")
    private String payChannel;

    @Schema(description = "支付方式")
    private String payType;

    @Schema(description = "发货备注")
    private String shipRemark;

    @Schema(description = "支付单列表")
    private List<OrderPaymentVO> payments = new ArrayList<>();

    @Schema(description = "退款单列表")
    private List<OrderRefundVO> refunds = new ArrayList<>();

    /**
     * 买家可见的操作流水。
     *
     * <p>只暴露"订单生命周期节点"（下单、支付、发货、收货、关闭、退款），
     * 后台备注、人工补单等内部操作**不下发**——
     * 买家看到"客服于 xx 改过备注"只会产生新的疑问。
     */
    @Schema(description = "订单节点时间线(买家可见节点)")
    private List<OrderEventVO> events = new ArrayList<>();
}
