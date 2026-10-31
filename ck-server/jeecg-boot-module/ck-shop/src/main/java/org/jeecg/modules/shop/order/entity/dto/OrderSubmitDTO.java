package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 提交订单入参。
 * </p>
 *
 * <h3>字段设计的三个要点</h3>
 * <ol>
 *   <li><b>没有金额字段</b>：金额一律服务端算。这是"价格篡改"的根本防线。</li>
 *   <li><b>必须带 priceToken</b>：服务端用它在提交时复核"价格是否与你看到的一致"，
 *       防止"挂着 3 分钟前的促销价下今天的单"。详见 {@code PriceTokenUtils}。</li>
 *   <li><b>必须带 idempotentKey</b>：用户连点两次、网络重试都只会产生一笔订单。
 *       幂等键唯一索引是数据库层的兜底，不依赖"先查有没有重复"。</li>
 * </ol>
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "提交订单入参")
public class OrderSubmitDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 客户端生成的幂等键（UUID）。
     *
     * <p>前端应在**进入结算页时**生成一次，之后无论用户点几次"提交订单"、
     * 无论网络重试多少次，都用同一个值。
     * 若前端每次点击都新生成，幂等就失效了——所以后端把它设为必填并做长度校验，
     * 而不提供"没传就自动生成"的兜底（那等于永远不幂等）。
     */
    @Schema(description = "幂等键(前端进入结算页时生成, 所有重试复用)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String idempotentKey;

    @Schema(description = "结算价格令牌(预览接口返回)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String priceToken;

    @Schema(description = "商品明细", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<OrderItemDTO> items;

    @Schema(description = "收货地址ID(来自地址簿)")
    private String addressId;

    /** 兼容"直接下单"场景：允许内联传地址（不改地址簿） */
    @Schema(description = "内联收货地址(与 addressId 二选一)")
    private String receiverName;

    @Schema(description = "内联收货手机号")
    private String receiverPhone;

    /**
     * 内联地址的国家码 / 省州码 / 城市 id。
     *
     * <p>为什么内联地址也要这三个码：订单快照只写 "{@code receiverCity} 文字"的话，
     * 物流对接拿不到 {@code cities.id}、运费也按不了国家分区，事后只能靠人工看文字补数据。
     * 这里与地址簿走<b>同一套服务端校验</b>，保证两条路径落出来的订单长得一样。
     *
     * <p>三者都可空（老客户端仍可只传文字），但一旦传了就必须与国/州/市对得上。
     */
    @Schema(description = "内联国家码(ISO alpha-2)")
    private String receiverCountryCode;

    @Schema(description = "内联省/州编码(ISO 3166-2)")
    private String receiverProvinceCode;

    @Schema(description = "内联城市ID(cities.id, 手填时为空)")
    private Integer receiverCityId;

    /**
     * 内联<b>手填国名</b>（可空，最长 100）。
     *
     * <p>与地址簿 {@code AddressDTO.countryName} 是同一套规则、同一个理由：
     * 级联组件每一级都有"找不到？手动填写"的兜底（自贸区、工业园、军事基地这类地址
     * 任何数据集都没有），用户手填国家时前端给不出 ISO 码，只传这个字段。
     * BFF 的"直接下单不改地址簿"入口确实能走到这里（{@code order/submit.post.ts} 支持内联地址），
     * 所以不接住它，这类订单在后台/面单上就没有国家。
     *
     * <h3>优先级（服务端强制，见 ShopOrderServiceImpl.applyAddressSnapshot）</h3>
     * <ol>
     *   <li>{@code receiverCountryCode} 有值 → <b>永远</b>用 region 表解析出的当地语言国名，
     *       忽略这里传的值（否则可伪造：码写 TH、名写"美国"，面单与统计各读一列，谁也说不清）；</li>
     *   <li>无码、本字段有值 → 存用户手填的国名（{@code receiver_country} 落库）；</li>
     *   <li>两者都空 → {@code receiver_country} 为 NULL，即老订单形态。</li>
     * </ol>
     */
    @Schema(description = "内联手填国名(receiverCountryCode 为空时才会被采信)")
    private String receiverCountryName;

    /** 省文字：给了 receiverProvinceCode 时会被服务端用当地语言标准名覆盖 */
    @Schema(description = "内联省")
    private String receiverProvince;

    /** 市文字：给了 receiverCityId 时会被覆盖；手填场景才采信 */
    @Schema(description = "内联市")
    private String receiverCity;

    @Schema(description = "内联区/县")
    private String receiverDistrict;

    @Schema(description = "内联详细地址")
    private String receiverAddress;

    @Schema(description = "内联邮编")
    private String receiverPostalCode;

    @Schema(description = "买家备注")
    private String buyerRemark;

    @Schema(description = "下单来源: web/h5/app")
    private String orderSource;

    @Schema(description = "渠道/推广位编码")
    private String channelCode;

    @Schema(description = "UTM来源")
    private String utmSource;

    @Schema(description = "来源页面")
    private String referrer;

    /**
     * 购物车条目ID。
     *
     * <p>下单成功后只清除**这些**条目，而不是清空整个购物车——
     * 用户勾选了 3 件下单，购物车里另外 5 件没被选中的商品不能被顺手删掉。
     * 这是购物车功能里最常见也最让用户恼火的 bug。
     */
    @Schema(description = "购物车条目ID(下单后仅清除这些条目)")
    private List<String> cartItemIds;
}
