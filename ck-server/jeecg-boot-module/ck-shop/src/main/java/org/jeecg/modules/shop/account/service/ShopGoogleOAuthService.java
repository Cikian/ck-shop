package org.jeecg.modules.shop.account.service;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.shop.account.config.GoogleOAuthProperties;
import org.jeecg.modules.shop.account.constant.ShopAccountConstant;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * <p>
 * Google OAuth2 授权码流程
 * </p>
 *
 * 三步：
 * <ol>
 *   <li>拼授权地址（带 state）；</li>
 *   <li>拿 code 换 access_token；</li>
 *   <li>用 access_token 取 userinfo（sub / email / name / picture）。</li>
 * </ol>
 *
 * <b>state 的管理</b>：写入 Redis（10 分钟、**一次性**）。
 * 校验时"读到就删"而不是"校验后再删"——先删的话并发重放第二次读不到，
 * 天然只能成功一次；这就是防重放的关键。state 只在服务端生成与消费，
 * 不接受调用方指定的 state（否则等于没有 CSRF 防护）。
 *
 * <b>HTTP 客户端</b>：用项目已有的 okhttp（jeecg-boot-base-core 传递依赖）。
 * 超时设短（连接 5s / 读取 10s）：Google 的 API 在墙内可能超时，
 * 而这个调用在登录路径上，卡 30 秒会让用户以为站点挂了。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Slf4j
@Service
public class ShopGoogleOAuthService {

    private final GoogleOAuthProperties properties;

    private final RedisUtil redisUtil;

    private final OkHttpClient httpClient;

    public ShopGoogleOAuthService(GoogleOAuthProperties properties, RedisUtil redisUtil) {
        this.properties = properties;
        this.redisUtil = redisUtil;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(10))
                // 不自动跟随重定向：token 端点不该返回 3xx，
                // 若真返回了，跟随到别的域会把 client_secret 带过去
                .followRedirects(false)
                .build();
    }

    // ==================================================================
    // 1) 授权地址
    // ==================================================================

    /**
     * 生成授权地址与 state。
     *
     * @param redirectUri 前端回调地址（Google 会与换 token 时逐字比对，必须一致）
     * @throws JeecgBootException 未配置 client-id / client-secret
     */
    public GoogleAuthResult buildAuthUrl(String redirectUri) {
        if (!properties.isConfigured()) {
            throw new JeecgBootException(properties.notConfiguredMessage());
        }
        if (StringUtils.isBlank(redirectUri)) {
            throw new JeecgBootException("缺少回调地址 redirectUri");
        }

        String state = UUID.randomUUID().toString().replace("-", "");
        // 把 redirectUri 一起存进去：回调时用它换 token，避免"回调不传 redirectUri 就对不上"。
        // 存的是**服务端生成的 state 对应的值**，不是前端可篡改的对象。
        redisUtil.set(ShopAccountConstant.OAUTH_STATE_PREFIX + state, redirectUri,
                ShopAccountConstant.OAUTH_STATE_TTL_SECONDS);

        String authUrl = ShopAccountConstant.GOOGLE_AUTH_ENDPOINT
                + "?client_id=" + urlEncode(properties.getClientId())
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&response_type=code"
                + "&scope=" + urlEncode(ShopAccountConstant.GOOGLE_SCOPE)
                + "&state=" + urlEncode(state)
                // prompt=select_account：让用户可以切换 Google 账号，
                // 否则浏览器里登录着的那个账号会被静默沿用，用户以为"登错了"
                + "&prompt=select_account"
                // access_type=online 足够：我们不在用户离线时代表他访问 Google，
                // 拿 refresh_token 只会平白扩大数据面
                + "&access_type=online";
        return new GoogleAuthResult(authUrl, state);
    }

    /**
     * 校验并消费 state。
     *
     * @return 申请 auth-url 时登记的 redirectUri
     * @throws JeecgBootException state 不存在 / 已过期 / 已使用
     */
    public String consumeState(String state) {
        if (StringUtils.isBlank(state)) {
            throw new JeecgBootException("缺少 state 参数，登录请求无效");
        }
        String key = ShopAccountConstant.OAUTH_STATE_PREFIX + state;
        Object cached = redisUtil.get(key);
        if (cached == null) {
            throw new JeecgBootException("登录已过期或该链接已被使用，请重新发起 Google 登录");
        }
        // 先删再返回：并发/重放请求里只有一个能读到，其余全部走"已使用"分支
        redisUtil.del(key);
        return cached.toString();
    }

    // ==================================================================
    // 2) code → access_token
    // ==================================================================

    /**
     * 用授权码换 access_token。
     *
     * @throws JeecgBootException 网络异常 / Google 返回错误 / 返回体缺 access_token
     */
    public String exchangeAccessToken(String code, String redirectUri) {
        if (!properties.isConfigured()) {
            throw new JeecgBootException(properties.notConfiguredMessage());
        }
        if (StringUtils.isBlank(code)) {
            throw new JeecgBootException("缺少 Google 授权码 code");
        }

        RequestBody body = new FormBody.Builder()
                .add("code", code)
                .add("client_id", properties.getClientId())
                .add("client_secret", properties.getClientSecret())
                .add("redirect_uri", StringUtils.defaultString(redirectUri))
                .add("grant_type", "authorization_code")
                .build();
        Request request = new Request.Builder()
                .url(ShopAccountConstant.GOOGLE_TOKEN_ENDPOINT)
                .post(body)
                .build();

        JSONObject json = execute(request, "换取 Google access_token 失败");
        String accessToken = json.getString("access_token");
        if (StringUtils.isBlank(accessToken)) {
            log.warn("Google token 响应缺少 access_token，error={}，error_description={}",
                    json.getString("error"), json.getString("error_description"));
            throw new JeecgBootException("Google 登录失败：未能获取授权凭证，请重试或改用邮箱登录");
        }
        return accessToken;
    }

    // ==================================================================
    // 3) access_token → userinfo
    // ==================================================================

    /**
     * 取 Google 用户信息。
     *
     * @throws JeecgBootException 网络异常 / 返回体缺 sub
     */
    public GoogleUserInfo fetchUserInfo(String accessToken) {
        Request request = new Request.Builder()
                .url(ShopAccountConstant.GOOGLE_USERINFO_ENDPOINT)
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        JSONObject json = execute(request, "获取 Google 用户信息失败");
        String sub = json.getString("sub");
        if (StringUtils.isBlank(sub)) {
            throw new JeecgBootException("Google 登录失败：未能获取账号标识(sub)");
        }
        GoogleUserInfo info = new GoogleUserInfo();
        info.setSub(sub);
        info.setEmail(ShopEmailCodeService.normalizeEmail(json.getString("email")));
        info.setName(json.getString("name"));
        info.setPicture(json.getString("picture"));
        info.setEmailVerified(json.getBooleanValue("email_verified"));
        return info;
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /**
     * 发请求并把响应体解析成 JSON。
     *
     * 所有失败路径都抛 {@link JeecgBootException}：
     * 这里若抛 IOException，上层会变成 500，而"Google 超时"、
     * "用户点了取消导致 code 无效"都是**可预期的业务失败**，应该是 200 + 业务错误码。
     */
    private JSONObject execute(Request request, String errorPrefix) {
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                // 不把 Google 原始错误直接透给前端（可能含 client_id 等），只记日志
                log.warn("{}，httpStatus={}，body={}", errorPrefix, response.code(), StringUtils.left(text, 500));
                throw new JeecgBootException(errorPrefix + "（Google 返回 " + response.code() + "），请重试或改用邮箱登录");
            }
            try {
                return JSONObject.parseObject(text);
            } catch (Exception e) {
                log.warn("{}，响应不是合法 JSON，body={}", errorPrefix, StringUtils.left(text, 500));
                throw new JeecgBootException(errorPrefix + "：Google 返回了无法解析的内容，请重试或改用邮箱登录");
            }
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            // 超时 / DNS / TLS 都属于这一类：网络不可达时给用户一个可操作的提示
            log.warn("{}，网络异常：{}", errorPrefix, e.getMessage());
            throw new JeecgBootException(errorPrefix + "：网络异常，请重试或改用邮箱登录");
        }
    }

    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 一定存在，理论不可达；不吞掉，转为运行时异常暴露问题
            throw new IllegalStateException("UTF-8 编码不可用", e);
        }
    }

    /** 授权地址结果（authUrl + state） */
    public static class GoogleAuthResult {
        private final String authUrl;
        private final String state;

        public GoogleAuthResult(String authUrl, String state) {
            this.authUrl = authUrl;
            this.state = state;
        }

        public String getAuthUrl() {
            return authUrl;
        }

        public String getState() {
            return state;
        }
    }

    /**
     * Google 用户信息。
     *
     * 单独一个类而不是 Map：Google 的 userinfo 响应字段有几十个，
     * 用 Map 会让"我们到底依赖了哪几个字段"变得不可见 ——
     * 将来 Google 调整字段时无从评估影响面。
     */
    public static class GoogleUserInfo {
        /** 唯一标识 */
        private String sub;
        private String email;
        private String name;
        private String picture;
        private boolean emailVerified;

        public String getSub() {
            return sub;
        }

        public void setSub(String sub) {
            this.sub = sub;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPicture() {
            return picture;
        }

        public void setPicture(String picture) {
            this.picture = picture;
        }

        public boolean isEmailVerified() {
            return emailVerified;
        }

        public void setEmailVerified(boolean emailVerified) {
            this.emailVerified = emailVerified;
        }
    }
}
