package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 收货地址出参。
 * </p>
 *
 * <h3>手机号脱敏规则（按场景区分，由服务层决定）</h3>
 * <ul>
 *   <li><b>买家查看自己的地址簿 / 自己的订单</b>：返回完整号码。
 *       号码是他自己填的，脱敏只会让他无法核对，是纯粹的功能损失。</li>
 *   <li><b>后台订单列表 / 操作流水 / 日志</b>：返回掩码（138****8000）。
 *       后台列表会被投屏和截图，完整号码没有必要出现在那里。</li>
 *   <li><b>后台发货详情</b>：返回完整号码——发货必须用真号，
 *       且这条路径有权限校验与操作流水留痕。</li>
 * </ul>
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
@Schema(description = "收货地址")
public class AddressVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "地址ID")
    private String id;

    @Schema(description = "收货人姓名")
    private String receiverName;

    @Schema(description = "收货人手机号(按场景脱敏)")
    private String receiverPhone;

    @Schema(description = "省")
    private String province;

    @Schema(description = "市")
    private String city;

    @Schema(description = "区/县")
    private String district;

    @Schema(description = "详细地址")
    private String detailAddress;

    @Schema(description = "邮政编码")
    private String postalCode;

    @Schema(description = "地址标签")
    private String addressTag;

    // ==================================================================
    // 地区级联（编码 + 快照 + 展示名）
    // ==================================================================

    @Schema(description = "国家码(ISO alpha-2, 权威; 老数据为空)")
    private String countryCode;

    /** 落库的当地语言国名快照：编辑回显与"面单上是这么写的"核对用 */
    @Schema(description = "国名(当地语言快照)")
    private String countryName;

    @Schema(description = "省/州编码(ISO 3166-2, 权威)")
    private String provinceCode;

    @Schema(description = "城市ID(cities.id, 权威; 手填时为空)")
    private Integer cityId;

    /**
     * 展示名按<b>当前请求的访客语言</b>现算，不落库。
     *
     * <p>回退链 译文 → 拉丁名 → 落库快照：中文界面显示"曼谷"、法语界面显示"Bangkok"，
     * 而 {@code fullAddress} 与 {@code province}/{@code city} 始终是当地文字原件——
     * 面单与界面永远是两件事，混在一起就再也分不清"哪个才是寄件用的写法"。
     */
    @Schema(description = "当前访客语言的国名")
    private String countryDisplay;

    @Schema(description = "当前访客语言的省/州名")
    private String provinceDisplay;

    @Schema(description = "当前访客语言的城市名")
    private String cityDisplay;

    @Schema(description = "是否默认地址")
    private Boolean isDefault;

    @Schema(description = "是否被脱敏")
    private Boolean masked;

    @Schema(description = "被使用下单次数")
    private Integer useCount;

    @Schema(description = "最近使用时间")
    private Date lastUsedTime;

    /**
     * 面单文案（当地语言，落库原件）。前端可直接展示，避免各处各拼一遍导致格式不一致。
     *
     * <p>拼装规则按"这条地址有没有国家信息"分叉（见 {@code ShopUserAddressServiceImpl}）：
     * <ul>
     *   <li>老数据（{@code countryCode} 与 {@code countryName} 都为 null）→ 省市区连写，
     *       <b>与改造前逐字一致</b>，这条验收线不能破；</li>
     *   <li>有国家信息（码或手填国名）→ 国际面单格式 {@code 详址, 区, 市, 省, 邮编, 国家}：
     *       逗号分隔、相邻同值去重（曼谷既是府又是市）、补国家段。</li>
     * </ul>
     */
    @Schema(description = "完整地址文案(面单用, 当地语言)")
    private String fullAddress;

    /**
     * 当前访客语言的整串地址（与 {@code fullAddress} 同一套拼装规则，只有名称语言不同）。
     *
     * <p>为什么不能只靠 {@link #fullAddress}：{@code fullAddress} 用的是当地文字
     * （面单要给快递员看），中文用户读起来像乱码。这里把国家/省/市换成访客语言的译名，
     * 详址与区县仍是用户自己填的原件（翻译它们没有意义，也没人去翻）。
     *
     * <p>老数据（没有国家信息）时它<b>等于</b> {@code fullAddress}——
     * 改造前没有这些字段，展示必须一模一样，不能因为加了功能就让老地址换个样子。
     */
    @Schema(description = "当前访客语言的整串地址(国际顺序)")
    private String fullAddressLocalized;
}
