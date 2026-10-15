package org.jeecg.modules.shop.account.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * Google 授权地址返回值
 * </p>
 *
 * 契约 2.5 要求 {@code { authUrl, state }}。
 *
 * {@code state} 单独返回而不是塞进 authUrl 里：BFF 需要把它暂存/透传，
 * 前端也可能需要做自己的状态校验。让它成为一个独立字段比从 URL 里
 * 正则抠出来可靠得多。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Accessors(chain = true)
@Schema(description = "Google 授权地址")
public class GoogleAuthUrlVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Google 授权页地址")
    private String authUrl;

    @Schema(description = "防CSRF的state(10分钟内一次性)")
    private String state;
}
