package org.jeecg.modules.shop.account.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 邮箱 + 邮箱验证码登录请求
 * </p>
 *
 * 字段名与契约 2.4 逐字一致：email / emailCode。
 *
 * 这个入口**兼做注册**：邮箱没注册过时自动建号。
 * 因此它不能要求"用户已存在"，否则新用户永远进不来。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Schema(description = "邮箱+邮箱验证码登录请求")
public class LoginEmailCodeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "邮箱验证码(6位)")
    private String emailCode;
}
