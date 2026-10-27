package org.jeecg.modules.shop.account.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 店铺端-保存个人资料
 * </p>
 *
 * 只允许改**昵称**与**手机号**这两项，理由：
 * <ul>
 *   <li><b>邮箱</b>是登录标识（邮箱密码 / 邮箱验证码登录都靠它），改邮箱必须先证明
 *       新邮箱可用，属于独立的"换绑"流程，不能和"改昵称"共用一个接口；</li>
 *   <li><b>username</b> 系统生成、永不可改（契约决策 7），请求体里根本没有这个字段；</li>
 *   <li><b>密码</b>有独立的 {@code /password/change}。</li>
 * </ul>
 *
 * <p>手机号是唯一索引列：为空时写 NULL（不写空串），否则多个"清空手机号"的账号
 * 会互相撞 {@code uniq_sys_user_phone}。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-27
 */
@Data
@Schema(description = "店铺端-保存个人资料")
public class UpdateProfileDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "昵称（1-30 字）")
    private String realname;

    @Schema(description = "手机号（可留空表示清空）")
    private String phone;
}
