package org.jeecg.modules.shop.account.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 发送邮箱验证码请求
 * </p>
 *
 * 字段名与契约 2.1 **逐字一致**（email / scene / ticket）。
 *
 * <p>{@code ticket} 由 CikVerify 验证码服务签发（前端通过 BFF 调
 * {@code /api/captcha/challenge} + {@code /api/captcha/verify} 拿到），
 * 服务端用 Java SDK 的 {@code CaptchaTicketService#consume} 消费：**一次性**、
 * 默认 120 秒有效，并绑定客户端 IP 与 User-Agent。
 * 邮箱验证码本身是免费资源，没有这道闸门，攻击者可以把它当成"免费发信机"
 * 把发信额度打满（顺带把发信域名打进黑名单）。</p>
 *
 * <p>旧的图形验证码字段（{@code captcha} / {@code checkKey}）已移除，
 * 由 CikVerify ticket 取代。</p>
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

    /**
     * CikVerify 验证通过后签发的 ticket。
     * 一次性消费、默认 120 秒有效，绑定客户端 IP + User-Agent。
     */
    @Schema(description = "CikVerify 验证通过后签发的 ticket（一次性，默认 120 秒有效）")
    private String ticket;
}
