package org.jeecg.modules.shop.account.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 登录/注册成功返回值（token + 用户信息）
 * </p>
 *
 * 契约 2.2 要求 {@code { token, userInfo, storeCode }}，
 * 2.3 / 2.4 要求 {@code { token, userInfo }}，
 * 2.5 要求 {@code { token, userInfo, isNewUser }}。
 *
 * 这里用**一个** VO 承载三者，靠 {@code @JsonInclude(NON_NULL)} 实现
 * "用不到就不出现"：
 * <ul>
 *   <li>注册 → storeCode 有值，isNewUser 为 null（不输出）；</li>
 *   <li>密码/验证码登录 → 两者都不输出；</li>
 *   <li>Google 登录 → isNewUser 有值。</li>
 * </ul>
 *
 * 契约原文的 JSON 形状因此逐字成立，同时不用维护三个近乎相同的类。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Accessors(chain = true)
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
@Schema(description = "登录成功返回值")
public class LoginResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "访问token")
    private String token;

    @Schema(description = "用户信息")
    private LoginUserVO userInfo;

    @Schema(description = "注册来源店铺编码(仅注册接口返回)")
    private String storeCode;

    @Schema(description = "是否为本次自动注册的新用户(仅Google登录返回)")
    private Boolean isNewUser;
}
