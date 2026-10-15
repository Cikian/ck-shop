package org.jeecg.modules.shop.account.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 发送邮箱验证码请求
 * </p>
 *
 * 字段名与契约 2.1 **逐字一致**（email / scene / captcha / checkKey）。
 * captcha + checkKey 是**图形**验证码，用来挡住"脚本批量刷邮件"——
 * 邮箱验证码本身是免费资源，没有图形验证码在前面挡一层，
 * 攻击者可以用它把我们的发信额度打满（顺带把发信域名打进黑名单）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Schema(description = "发送邮箱验证码请求")
public class SendEmailCodeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "邮箱")
    private String email;

    /** register | login | reset */
    @Schema(description = "场景: register | login | reset")
    private String scene;

    @Schema(description = "图形验证码")
    private String captcha;

    @Schema(description = "图形验证码的 key")
    private String checkKey;
}
