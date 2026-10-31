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
 * 用户收货地址簿（ck_shop_user_address）
 * </p>
 *
 * 地址是**用户级**数据，不按店铺隔离：同一个人在 A 店和 B 店用的是同一份地址簿。
 *
 * <p>地址簿与订单地址是**两份数据**：
 * 订单里存的是下单那一刻的快照，用户后来修改/删除地址簿都不会影响历史订单。
 * 这是售后与纠纷处理的底线——"我明明寄到家里，订单上怎么是公司地址"
 * 这种问题只能靠快照回答。
 *
 * <p><b>地区字段是"编码 + 名称"双轨</b>（见 {@code db/ck-shop-address-region.sql}）：
 * {@code country_code / province_code / city_id} 是权威（校验、分区、物流对接），
 * {@code country_name / province / city} 是下单时刻的当地语言文字快照（面单要写当地字）。
 * 两者缺一不可：只有码，面单印不出来；只有文字，没法校验也没法统计。
 * 界面要显示访客语言的译名，是拿码反查译文现算的，<b>永远不落库</b>。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_user_address")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "用户收货地址")
public class ShopUserAddress implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "用户账号(冗余, 后台展示免回表)")
    private String username;

    @Schema(description = "收货人姓名")
    private String receiverName;

    /**
     * 收货人手机号。
     *
     * <p>明文落库：发货必须用完整号码，脱敏保存等于把数据弄丢。
     * 脱敏在**出参**阶段按场景做（见 {@code ShopMaskUtils}）——
     * 后台列表返回掩码，买家查看自己的地址返回完整值。
     */
    @Schema(description = "收货人手机号(接口按场景脱敏)")
    private String receiverPhone;

    /**
     * 国家码（ISO 3166-1 alpha-2）。
     *
     * <p><b>这是权威码</b>：校验、运费分区、物流对接只认它，不认国名字符串
     * （"United States"/"USA"/"美国" 是同一个国家，字符串没法判等）。
     *
     * <p>为 NULL 表示<b>改造前的老数据</b>：老客户端的入口不会传国家码，
     * 这类地址的展示与拼串必须与改造前完全一致（见 {@code AddressVO#fullAddressLocalized}）。
     */
    @Schema(description = "国家码(ISO alpha-2, 权威; 老数据为空)")
    private String countryCode;

    /**
     * 国名快照（当地语言）。
     *
     * <p>与 {@link #countryCode} 是"快照 vs 权威"的关系：
     * 国名按当地语言落一份在这里，是因为面单/报关单上要写国家，
     * 而 {@code countries} 表是上游整表重灌的参考数据——它改名不该影响历史地址。
     * 界面要显示成访客语言的国名，是拿 {@code countryCode} 反查译文现算的，<b>不写回这一列</b>。
     */
    @Schema(description = "国名(当地语言快照)")
    private String countryName;

    /**
     * 省/州编码（ISO 3166-2，如 {@code TH-10}）。
     *
     * <p>权威码。上游有 28 个州级记录没有 ISO 编码，那种情况落库的是
     * 服务端回退出来的 {@code "S" + states.id}（保证前端总能回传一个标识）。
     */
    @Schema(description = "省/州编码(ISO 3166-2, 权威)")
    private String provinceCode;

    @Schema(description = "省/州(当地语言快照)")
    private String province;

    @Schema(description = "市(当地语言快照)")
    private String city;

    /**
     * 城市 id（{@code cities.id}）。
     *
     * <p>权威码。为空表示城市是<b>用户手填</b>的——自贸区、工业园、军事基地这类地址
     * 任何数据集都不会有，此时 {@link #city} 的文字就是唯一来源，服务端不覆盖它。
     */
    @Schema(description = "城市ID(cities.id, 权威; 手填时为空)")
    private Integer cityId;

    @Schema(description = "区/县")
    private String district;

    @Schema(description = "详细地址")
    private String detailAddress;

    @Schema(description = "邮政编码")
    private String postalCode;

    @Schema(description = "地址标签: 家/公司/学校")
    private String addressTag;

    @Schema(description = "是否默认地址: 0-否, 1-是")
    private Integer isDefault;

    /** 被使用下单次数：既是智能排序依据，也是"这个地址被谁用过"的排查线索 */
    @Schema(description = "被使用下单次数")
    private Integer useCount;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最近一次下单使用时间")
    private Date lastUsedTime;

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
     * 删除标记。
     *
     * <p>刻意**不加 {@code @TableLogic}**：地址簿的"删除"是用户主动行为，
     * 但订单快照里可能还引用着它，出问题时需要能按 id 反查（例如"这个地址当时是什么"）。
     * 因此过滤条件写在各处显式的 {@code del_flag = 0} 上，
     * 需要追溯时仍可用 includeDeleted 的方式读到。
     */
    @Schema(description = "删除标记: 0-正常, 1-已删除")
    private Integer delFlag;
}
