package org.jeecg.modules.shop.account.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <p>
 * Google OAuth 配置
 * </p>
 *
 * 配置项（契约要求）：{@code google.client-id} / {@code google.client-secret} /
 * {@code google.redirect-uri}，密钥写本地 {@code .env}，不入仓库。
 *
 * <b>未配置时的行为</b>：不抛启动异常。原因——
 * 本项目是一体化部署，Google 登录只是店铺端的一个可选入口；
 * 若因为没配 Google 密钥就让**整个后端启动失败**，管理端、商品、订单会一起挂掉，
 * 这是典型的"一个可选功能绑架整站"。
 * 因此这里只提供 {@link #isConfigured()}，由接口在调用时返回明确的业务错误
 * （契约 2.5：auth-url 返回明确错误，前端据此禁用按钮）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Component
@ConfigurationProperties(prefix = "google")
public class GoogleOAuthProperties {

    /** Google OAuth 客户端 ID（未配置为 null/空） */
    private String clientId;

    /** Google OAuth 客户端密钥 */
    private String clientSecret;

    /**
     * 默认回调地址。当前实现里回调地址由请求显式传入
     * （Google 要求授权与换 token 两处的 redirect_uri 逐字一致），
     * 这个配置项作为**未传时的兜底**与文档用途保留。
     */
    private String redirectUri;

    /**
     * 是否已具备发起 Google 登录的必要条件。
     *
     * 只检查 clientId + clientSecret：redirectUri 由每次请求带入，
     * 缺它不影响"按钮该不该显示"这个判断。
     */
    public boolean isConfigured() {
        return StringUtils.isNotBlank(clientId) && StringUtils.isNotBlank(clientSecret);
    }

    /**
     * 未配置时的统一错误文案。
     *
     * 刻意写清"是配置问题、找谁解决"：用户看到"登录失败"只会反复重试，
     * 看到"未配置"才能转给管理员。
     */
    public String notConfiguredMessage() {
        return "Google 登录尚未配置（缺少 google.client-id / google.client-secret），请联系管理员在服务端 .env 中补齐后重启后端";
    }
}
