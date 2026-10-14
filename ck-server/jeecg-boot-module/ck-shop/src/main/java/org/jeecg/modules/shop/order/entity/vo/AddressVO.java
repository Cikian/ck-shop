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

    @Schema(description = "是否默认地址")
    private Boolean isDefault;

    @Schema(description = "是否被脱敏")
    private Boolean masked;

    @Schema(description = "被使用下单次数")
    private Integer useCount;

    @Schema(description = "最近使用时间")
    private Date lastUsedTime;

    /** 拼好的完整地址文案，前端可直接展示，避免各处各拼一遍导致格式不一致 */
    @Schema(description = "完整地址文案")
    private String fullAddress;
}
