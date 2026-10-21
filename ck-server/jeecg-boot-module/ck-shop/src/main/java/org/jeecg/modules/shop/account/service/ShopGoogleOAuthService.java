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
import java.util.Base64;
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
 * 超时（连接 5s / 读取 20s / 单次调用 25s）：Google 的 API 在墙内经常要经过
 * 代理、时快时慢（实测同一台机器 0.2s～15s 都有），10s 读取超时会把"慢但能用"
 * 的请求误杀成登录失败；而登录路径又不能让用户无限等待，所以用 callTimeout
 * 给单次调用封顶，再配合 BFF 侧的 30s 超时（见 nuxt-shop/server/api/auth/google/callback.post.ts），
 * 保证后端总能先于 BFF 返回真实错误，而不是被 BFF 的"后端服务响应超时"盖住。
 *
 * <b>为什么优先用 id_token 而不是 userinfo</b>：授权码流程里 token 端点本来就会
 * 返回 id_token（scope 含 openid），里面的 sub / email / name / picture 与 userinfo
 * 是同一批声明。直接解析 id_token 可以把"两次跨境请求"降成"一次"，少一次失败机会，
 * 也少一次网络往返。只有 id_token 缺失/校验不过时才回退 userinfo（保留兜底）。
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

    /**
     * userinfo 兜底专用客户端：超时更短。
     *
     * 主路径只用 token 端点一次请求（上限 25s）；万一 id_token 不可用还要再打一次
     * userinfo，两次叠加不能超过 BFF 的 30s，所以兜底这次给 4s 封顶
     * （健康网络下 userinfo 实测 0.2～0.3s，4s 足够；慢到 4s 还没结果的话，
     * 这次登录本来也已经不可用了，不如尽快失败并让用户重新发起）。
     */
    private final OkHttpClient userInfoHttpClient;

    public ShopGoogleOAuthService(GoogleOAuthProperties properties, RedisUtil redisUtil) {
        this.properties = properties;
        this.redisUtil = redisUtil;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(20))
                .writeTimeout(Duration.ofSeconds(10))
                // 单次调用（DNS + 建连 + TLS + 读取）的总上限：
                // 读取超时只约束"两次数据之间的间隔"，代理卡住时仍可能拖很久；
                // 25s 比 BFF 的 30s 小，才能保证用户看到的是后端的真实失败原因。
                .callTimeout(Duration.ofSeconds(25))
                // 不自动跟随重定向：token 端点不该返回 3xx，
                // 若真返回了，跟随到别的域会把 client_secret 带过去
                .followRedirects(false)
                .build();
        // 复用同一个连接池/调度器，只收紧这一次兜底调用的时间预算
        this.userInfoHttpClient = this.httpClient.newBuilder()
                .readTimeout(Duration.ofSeconds(4))
                .callTimeout(Duration.ofSeconds(4))
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
     * 用授权码换 access_token，并顺带取出 id_token。
     *
     * <p>id_token 是 Google 在 token 端点直接返回的 JWT（scope 含 openid 时必然存在），
     * 调用方应优先用 {@link #parseIdToken(String)} 从里面取用户信息，
     * 只有它缺失/校验不过时才回退 {@link #fetchUserInfo(String)}。</p>
     *
     * @throws JeecgBootException 网络异常 / Google 返回错误 / 返回体缺 access_token
     */
    public TokenResult exchangeCode(String code, String redirectUri) {
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

        JSONObject json = execute(httpClient, request, "换取 Google access_token 失败");
        String accessToken = json.getString("access_token");
        if (StringUtils.isBlank(accessToken)) {
            log.warn("Google token 响应缺少 access_token，error={}，error_description={}",
                    json.getString("error"), json.getString("error_description"));
            throw new JeecgBootException("Google 登录失败：未能获取授权凭证，请重试或改用邮箱登录");
        }
        return new TokenResult(accessToken, json.getString("id_token"));
    }

    // ==================================================================
    // 2.5) id_token → 用户信息（首选，避免第二次跨境请求）
    // ==================================================================

    /** Google 签发的 id_token 的 issuer（历史上有带/不带 https:// 两种写法） */
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_ISSUER_SHORT = "accounts.google.com";

    /**
     * 从 id_token（JWT）里解析并校验用户信息。
     *
     * <p><b>安全说明</b>：按 OpenID Connect Core 3.1.3.7，id_token 是
     * <i>通过 TLS 直接从 token 端点</i>拿到的，此时允许用 TLS 服务端校验替代
     * 签名校验（JWT 的签名防的是"别人伪造/篡改"，而这里整段响应来自 Google
     * 的 TLS 连接）。因此本方法不验签，但仍必须校验：</p>
     * <ul>
     *   <li>iss：必须是 Google 的 issuer；</li>
     *   <li>aud：必须是本应用自己的 client_id（防"别的应用的 id_token"被塞进来）；</li>
     *   <li>exp：必须未过期（留 60s 时钟偏差）；</li>
     *   <li>sub：必须存在（账号唯一标识）。</li>
     * </ul>
     *
     * @return 校验通过返回用户信息；id_token 缺失/格式错/校验不过返回 {@code null}，
     *         由调用方回退 userinfo（见 {@link #fetchUserInfo(String)}）
     */
    public GoogleUserInfo parseIdToken(String idToken) {
        if (StringUtils.isBlank(idToken)) {
            return null;
        }
        try {
            // JWT = header.payload.signature，只用到第二段
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                log.warn("Google id_token 格式非法（段数={}），回退 userinfo", parts.length);
                return null;
            }
            String payload = new String(
                    Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8);
            JSONObject json = JSONObject.parseObject(payload);

            String iss = json.getString("iss");
            if (!GOOGLE_ISSUER.equals(iss) && !GOOGLE_ISSUER_SHORT.equals(iss)) {
                log.warn("Google id_token issuer 不匹配：{}，回退 userinfo", iss);
                return null;
            }
            // aud 在单客户端场景是字符串；按字符串比较即可，避免把数组当字符串误判
            String aud = json.getString("aud");
            if (!StringUtils.equals(aud, properties.getClientId())) {
                log.warn("Google id_token audience 不匹配，回退 userinfo");
                return null;
            }
            long exp = json.getLongValue("exp");
            if (exp <= System.currentTimeMillis() / 1000 - 60) {
                log.warn("Google id_token 已过期（exp={}），回退 userinfo", exp);
                return null;
            }
            String sub = json.getString("sub");
            if (StringUtils.isBlank(sub)) {
                log.warn("Google id_token 缺少 sub，回退 userinfo");
                return null;
            }

            GoogleUserInfo info = new GoogleUserInfo();
            info.setSub(sub);
            info.setEmail(ShopEmailCodeService.normalizeEmail(json.getString("email")));
            info.setName(json.getString("name"));
            info.setPicture(json.getString("picture"));
            info.setEmailVerified(json.getBooleanValue("email_verified"));
            return info;
        } catch (Exception e) {
            // 解析失败不是致命错误：回退 userinfo 即可，不能因此让用户登不进来
            log.warn("Google id_token 解析失败（{}），回退 userinfo", e.getMessage());
            return null;
        }
    }

    /** Base64URL 解码前补齐 padding（Java 的 decoder 要求 4 的倍数） */
    private String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 2) {
            return value + "==";
        }
        if (remainder == 3) {
            return value + "=";
        }
        return value;
    }

    // ==================================================================
    // 3) access_token → userinfo（id_token 不可用时的兜底）
    // ==================================================================

    /**
     * 取 Google 用户信息（兜底路径）。
     *
     * <p>正常流程不会走到这里：token 响应里的 id_token 已经带了同样的声明，
     * 优先用 {@link #parseIdToken(String)} 解析。保留本方法是为了在
     * id_token 缺失或校验不过时仍能登录，代价是多一次跨境请求。</p>
     *
     * @throws JeecgBootException 网络异常 / 返回体缺 sub
     */
    public GoogleUserInfo fetchUserInfo(String accessToken) {
        Request request = new Request.Builder()
                .url(ShopAccountConstant.GOOGLE_USERINFO_ENDPOINT)
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        JSONObject json = execute(userInfoHttpClient, request, "获取 Google 用户信息失败");
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
    private JSONObject execute(OkHttpClient client, Request request, String errorPrefix) {
        try (Response response = client.newCall(request).execute()) {
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
     * token 端点结果：access_token 必填，id_token 可能为空（理论上不会）。
     * 单独一个类而不是两个返回值，调用方就不容易把参数顺序写反。
     */
    public static class TokenResult {
        private final String accessToken;
        private final String idToken;

        public TokenResult(String accessToken, String idToken) {
            this.accessToken = accessToken;
            this.idToken = idToken;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getIdToken() {
            return idToken;
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
