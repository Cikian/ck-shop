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

    @Schema(description = "内联省")
    private String receiverProvince;

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
