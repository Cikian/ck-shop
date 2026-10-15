package org.jeecg.modules.shop.account.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 发送邮箱验证码的返回值
 * </p>
 *
 * 契约 2.1 要求返回 {@code { cooldownSeconds: number }}。
 * 前端拿它直接初始化倒计时按钮，**不写死 60** ——
 * 后端若调整冷却时长（比如风控收紧到 120s），前端不需要跟着改。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Accessors(chain = true)
@Schema(description = "发送邮箱验证码返回值")
public class SendEmailCodeVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "下次可发送的冷却秒数")
    private Integer cooldownSeconds;
}
