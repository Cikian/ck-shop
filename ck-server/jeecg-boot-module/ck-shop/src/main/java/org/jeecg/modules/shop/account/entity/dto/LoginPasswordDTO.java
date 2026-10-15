package org.jeecg.modules.shop.account.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 邮箱 + 密码登录请求
 * </p>
 *
 * 字段名与契约 2.3 逐字一致：email / password。
 *
 * password 是**AES 加密后的密文**（前端加密传输），服务端用
 * {@code AesEncryptUtil.resolvePassword} 解密；解密失败按明文处理，
 * 与 {@code LoginController} 的既有口径一致（否则从 curl/Postman 调接口
 * 就没法登录，联调成本会显著变高）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Schema(description = "邮箱+密码登录请求")
public class LoginPasswordDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "密码(明文或AES密文)")
    private String password;
}
