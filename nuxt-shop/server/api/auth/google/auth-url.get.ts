// server/api/auth/google-auth-url.get.ts
// 前端请求：GET /api/auth/google/auth-url?redirectUri=/zh/auth/google/callback
// 对应转发到 Java 后端：GET {baseURL}/shop/account/google/auth-url?redirectUri=...
//
// 契约（见 docs/店铺端账号体系改造-接口契约.md 第 2.5 / 3.3 节）：
//   后端返回 Result<{ authUrl, state }>。
//
// ⚠️⚠️ 本文件的核心是**开放重定向（open redirect）防护**：⚠️⚠️
//
//   `redirectUri` 会被拼进 Google 授权链接，Google 校验通过后把用户
//   （连同一个一次性 code）重定向回这个地址；后端还会拿**同一个值**
//   去换 access_token。如果它是攻击者可控的任意 URL：
//     1) 后端会把 code 发给攻击者的服务器（code 泄露）；
//     2) 用户会被从本店跳走到外部站点。
//
//   所以这里的做法是：**忽略前端传入的值，由服务端从当前请求的 origin 自己拼**。
//   前端只需（可选）传一个站内**相对路径**，我们不直接用它，只用它做校验：
//   - 相对路径（以 `/` 开头但不是 `//`）→ 允许，拼到 origin 后面；
//   - 其它一律忽略，回退到默认回调路径。
//   这样即使前端被 XSS 注入，也不可能把 redirectUri 变成外部域名。

import { resolveStoreLang } from '~~/types/store-locale'

/** Google 回调页的站内路径（不含语言前缀）。契约 3.3：前端回调页 /auth/google/callback */
const CALLBACK_PATH = '/auth/google/callback'

/**
 * 本项目 i18n 是 `prefix` 策略：**所有**语言（含默认语言）都带前缀，
 * `/zh/...`、`/en/...` 才是合法路由，`/auth/google/callback` 这种裸路径是 404。
 *
 * ⚠️ 所以兜底路径**必须**带上语言前缀，否则：
 *   前端没传 redirectUri 时（异常场景）→ 兜底出裸路径 → Google 把用户带回一个 404 页面。
 * 这里从 ?lang= 取语言并按 `prefix_except_default` 之外的策略统一补前缀。
 */
function defaultCallbackPath(lang: string): string {
  const safe = /^[a-z]{2}(-[A-Za-z]{2,4})?$/.test(lang || '') ? lang : 'en'
  return `/${safe}${CALLBACK_PATH}`
}

/**
 * 把前端传来的回调地址收敛成**站内安全相对路径**。
 *
 * 只接受「以单个 `/` 开头」的路径：
 *   - `/zh/auth/google/callback`  → 接受
 *   - `//evil.com/x`              → 拒绝（协议相对 URL，浏览器会当成 https://evil.com）
 *   - `https://evil.com/x`        → 拒绝（绝对地址）
 *   - `javascript:...`            → 拒绝（不以 / 开头）
 */
function toSafeRelativePath(raw: unknown, fallback: string): string {
  const value = String(raw ?? '').trim()
  if (!value.startsWith('/') || value.startsWith('//')) {
    return fallback
  }
  // 反斜杠是个经典绕过：某些浏览器把 `/\evil.com` 归一化成 `//evil.com`。
  if (value.includes('\\')) {
    return fallback
  }
  return value
}

export default defineEventHandler(async (event) => {
  const query = getQuery(event)

  // ★ origin 由服务端决定：优先用配置好的 STORE_PUBLIC_ORIGIN，
  //   未配置时才退回本次请求的 origin（见 utils/googleOAuth.ts 的说明）。
  const origin = resolvePublicOrigin(event)

  // lang 必须先算出来：兜底回调路径要按语言补前缀（见 defaultCallbackPath 注释）
  const lang = resolveStoreLang(query.lang, 'en')

  const relativePath = toSafeRelativePath(query.redirectUri, defaultCallbackPath(lang))
  const redirectUri = `${origin}${relativePath}`

  const api = createApiClient(event, { lang })

  try {
    const result = await api<{ authUrl: string; state: string }>(
      '/shop/account/google/auth-url',
      {
        method: 'GET',
        // 传的是**服务端自己拼好的绝对地址**，前端的原值不会被转发。
        query: { redirectUri },
      },
    )

    // ★ 把后端签发的 state 绑定到本次浏览器会话（httpOnly cookie，10 分钟）。
    //   回调时用它来拒绝"把别人的 code/state 塞给受害者"的 login CSRF。
    bindOAuthState(event, result.state)

    // 把最终使用的 redirectUri 一起回给前端：
    // 回调页要拿**同一个值**去换 token，前端自己拼容易和这里不一致
    // （比如语言前缀或代理层 origin 不同），导致 Google 报 redirect_uri_mismatch。
    return { authUrl: result.authUrl, state: result.state, redirectUri }
  } catch (error: any) {
    // 契约 2.5：未配置 google.client-id/secret 时后端返回明确业务错误，
    // 前端据此禁用 Google 按钮。文案原样透传。
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || 'Google 登录暂不可用，请稍后重试',
    })
  }
})
