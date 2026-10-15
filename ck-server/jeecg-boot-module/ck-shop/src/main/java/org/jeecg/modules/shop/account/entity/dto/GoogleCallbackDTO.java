package org.jeecg.modules.shop.account.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * Google 回调请求
 * </p>
 *
 * 字段名与契约 2.5 逐字一致：code / state / redirectUri。
 *
 * ⚠️ redirectUri 必须与申请 auth-url 时用的**完全一致**（Google 会逐字比对），
 * 所以它由前端回传而不是后端自己拼。BFF 层从站点 origin 拼好再传进来，
 * 不信任浏览器直接给的值 —— 否则攻击者可以构造一个指向自己站点的 redirectUri，
 * 把授权码换到自己的域名下（虽然 code 是一次性的，但足以完成一次钓鱼式登录）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Schema(description = "Google 登录回调请求")
public class GoogleCallbackDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Google 授权码")
    private String code;

    @Schema(description = "防CSRF的state")
    private String state;

    @Schema(description = "回调地址(须与申请auth-url时一致)")
    private String redirectUri;
}
