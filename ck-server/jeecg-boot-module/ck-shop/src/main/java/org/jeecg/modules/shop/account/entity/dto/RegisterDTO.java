package org.jeecg.modules.shop.account.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 邮箱注册请求
 * </p>
 *
 * 字段名与契约 2.2 逐字一致：email / emailCode / password / confirmPassword。
 *
 * ⚠️ 这里**没有 username 字段**，是刻意的：username 由系统生成
 * （`u_` + 8 位小写字母数字），永不可改。留一个入参就等于给前端一个
 * "看起来能自定义账号"的错觉，后端还得再拒绝一次——不如一开始就不给。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Schema(description = "邮箱注册请求")
public class RegisterDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "邮箱验证码(6位)")
    private String emailCode;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "确认密码")
    private String confirmPassword;
}
