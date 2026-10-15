package org.jeecg.modules.shop.account.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 设置 / 修改密码请求（已登录，个人中心）
 * </p>
 *
 * 字段名与契约 2.7 逐字一致：oldPassword / newPassword / confirmPassword。
 *
 * ⚠️ oldPassword **可以为空**：Google 自动注册、以及邮箱验证码首次登录自动注册的
 * 用户从来没设过密码（pwd_set = 0），要求他们先填"旧密码"等于把他们锁在门外。
 * 服务端据此判定：pwd_set = 0 时忽略 oldPassword；pwd_set = 1 时**必须**校验旧密码
 * （否则拿到 token 的人可以绕过旧密码直接换掉密码，等于 token 泄漏就永久失守）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Schema(description = "设置/修改密码请求")
public class ChangePasswordDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "旧密码(未设置过密码时可为空)")
    private String oldPassword;

    @Schema(description = "新密码")
    private String newPassword;

    @Schema(description = "确认密码")
    private String confirmPassword;
}
