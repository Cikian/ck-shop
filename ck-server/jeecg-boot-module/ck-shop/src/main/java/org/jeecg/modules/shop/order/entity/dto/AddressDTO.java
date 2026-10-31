package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 收货地址新增/编辑入参。
 * </p>
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "收货地址入参")
public class AddressDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 为空表示新增，非空表示编辑；编辑时服务端会校验该地址归属当前用户 */
    @Schema(description = "地址ID(空=新增)")
    private String id;

    @Schema(description = "收货人姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String receiverName;

    @Schema(description = "收货人手机号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String receiverPhone;

    /**
     * 国家码（ISO alpha-2）。
     *
     * <p>三个级联字段<b>全部可空</b>，是刻意的向后兼容：老版本前端/老书签页面
     * 仍然只提交省市区文字，服务端必须继续接受（走旧逻辑），不能因此拒单。
     * 新前端提交时这三个字段是必填的，由前端表单保证；
     * 服务端只校验"给了就必须是对的"。
     */
    @Schema(description = "国家码(ISO alpha-2, 可空向后兼容)")
    private String countryCode;

    /**
     * 手填国名（可选，最长 100）。
     *
     * <h3>为什么有了 countryCode 还要一个纯文字国名</h3>
     * 级联组件每一级都有"找不到？手动填写"的兜底（自贸区、工业园、军事基地这类地址
     * 任何数据集都没有，没有兜底入口就是功能倒退）。用户手填国家时前端给不出 ISO 码，
     * 只传这一个字段——服务端若不接住，这类地址在列表和面单上就没有国家。
     *
     * <h3>优先级（服务端强制，见 ShopUserAddressServiceImpl）</h3>
     * <ol>
     *   <li>{@code countryCode} 有值 → <b>永远</b>用 region 表解析出的当地语言国名，
     *       忽略这里传的值（否则前端可以塞任意国名，与码对不上就是脏数据）；</li>
     *   <li>{@code countryCode} 为空、本字段有值 → 存用户手填的国名（{@code country_name} 落库）；</li>
     *   <li>两者都空 → {@code country_name} 为 NULL，即老数据形态。</li>
     * </ol>
     */
    @Schema(description = "手填国名(countryCode 为空时才会被采信)")
    private String countryName;

    /** 省/州编码（ISO 3166-2）；同时接受服务端回退出来的 "S"+id 形式 */
    @Schema(description = "省/州编码(ISO 3166-2)")
    private String provinceCode;

    /**
     * 城市 id。
     *
     * <p>为空表示<b>手填城市</b>：自贸区、工业园、军事基地这类地址任何数据集都没有，
     * 这时 {@link #city} 的文字才是唯一来源，服务端不覆盖它。
     */
    @Schema(description = "城市ID(cities.id, 手填时为空)")
    private Integer cityId;

    /** 省/州文字：给了 provinceCode 时会被服务端用当地语言标准名覆盖，只在没给编码时才采信 */
    @Schema(description = "省")
    private String province;

    /** 市文字：给了 cityId 时会被覆盖；为空/手填场景才采信（见 cityId 注释） */
    @Schema(description = "市")
    private String city;

    @Schema(description = "区/县")
    private String district;

    @Schema(description = "详细地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String detailAddress;

    @Schema(description = "邮政编码")
    private String postalCode;

    @Schema(description = "地址标签: 家/公司/学校")
    private String addressTag;

    /**
     * 是否默认地址。
     *
     * <p>{@code null} 表示"不改动当前默认状态"；{@code true} 表示设为默认
     * （服务端会把该用户其它地址的默认标记清掉，保证"有且只有一个默认地址"）。
     * 用 null 区分"没传"和"传了 false"，避免编辑一个非默认地址时
     * 把用户真正想保留的默认地址顺手取消掉。
     */
    @Schema(description = "是否默认(null=不改动)")
    private Boolean isDefault;
}
