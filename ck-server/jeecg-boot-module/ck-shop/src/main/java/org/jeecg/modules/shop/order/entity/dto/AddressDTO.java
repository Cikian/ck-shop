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

    @Schema(description = "省")
    private String province;

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
