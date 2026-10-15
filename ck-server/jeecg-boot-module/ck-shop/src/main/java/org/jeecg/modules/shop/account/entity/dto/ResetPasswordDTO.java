package org.jeecg.modules.shop.account.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 重置密码请求（忘记密码）
 * </p>
 *
 * 字段名与契约 2.6 逐字一致：email / emailCode / newPassword / confirmPassword。
 *
 * 刻意**不接收 username**：邮箱是唯一标识，且邮箱验证码已经证明了
 * "请求者控制着这个邮箱"。若同时允许传 username，就多了一条
 * "改别人密码"的路径需要单独防越权，纯属自找麻烦。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Schema(description = "重置密码请求")
public class ResetPasswordDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "邮箱验证码(scene=reset)")
    private String emailCode;

    @Schema(description = "新密码")
    private String newPassword;

    @Schema(description = "确认密码")
    private String confirmPassword;
}
