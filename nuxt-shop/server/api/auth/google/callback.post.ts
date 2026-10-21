// server/api/auth/google-callback.post.ts
// 前端请求：POST /api/auth/google/callback
// body: { code, state }
// 对应转发到 Java 后端：POST {baseURL}/shop/account/google/callback
//
// 契约（见 docs/店铺端账号体系改造-接口契约.md 第 2.5 / 3.3 节）：
//   后端返回 Result<{ token, userInfo, isNewUser }>。
//
// ⚠️⚠️ redirectUri 由 BFF 服务端拼接，**绝不信任前端传入** ⚠️⚠️
//
//   这与 google-auth-url.get.ts 是同一道防线，且这里更要紧：
//   后端拿 redirectUri 去 Google 换 access_token，Google 会校验它必须与
//   签发 code 时用的 redirect_uri **逐字节相等**。
//   如果放前端传，攻击者可以：
//     1) 诱导受害者点击一个带攻击者 redirectUri 的授权链接；
//     2) 受害者的 code 被后端用攻击者的 redirectUri 兑换 → 账号被接管。
//
//   所以本接口**不接收 body.redirectUri**（即使前端传了也直接忽略），
//   一律用 getRequestURL(event).origin + 回调路径重新拼。
//
//   ⚠️ 回调路径必须带**语言前缀**：本项目 i18n 是 prefix 策略，
//   `/auth/google/callback`（无前缀）是 404。前缀从 ?lang= 推导，
//   与 google-auth-url.get.ts 的逻辑保持一致，两端才能拼出逐字相同的地址。

import { resolveStoreLang } from '~~/types/store-locale'

interface GoogleCallbackBody {
  code?: string
  state?: string
  lang?: string
}

/** Google 回调页的站内路径（不含语言前缀，与 google-auth-url.get.ts 保持一致） */
const CALLBACK_PATH = '/auth/google/callback'

/**
 * 带语言前缀的兜底回调路径。
 * 与 google-auth-url.get.ts 的 defaultCallbackPath 是同一套规则，
 * 改一处必须改另一处，否则两端拼出的 redirectUri 不一致 → redirect_uri_mismatch。
 */
function defaultCallbackPath(lang: string): string {
  const safe = /^[a-z]{2}(-[A-Za-z]{2,4})?$/.test(lang || '') ? lang : 'en'
  return `/${safe}${CALLBACK_PATH}`
}

export default defineEventHandler(async (event) => {
  const body = await readBody<GoogleCallbackBody>(event)

  const code = String(body?.code ?? '').trim()
  if (!code) {
    throw createError({ statusCode: 400, statusMessage: '缺少授权码' })
  }

  // state 是后端写入 Redis 的一次性防 CSRF 令牌（契约 2.5，TTL 10 分钟）。
  const state = String(body?.state ?? '').trim()
  if (!state) {
    throw createError({ statusCode: 400, statusMessage: '缺少 state，请重新发起 Google 登录' })
  }

  // ★ 会话绑定校验（防 login CSRF）：authorization-url 阶段 BFF 把后端签发的
  //   state 同时写进了一张 httpOnly cookie，这里必须逐字相等。
  //   没有这一步时，攻击者可以在自己浏览器跑完授权、拿着 {code, state} 诱使
  //   受害者浏览器提交 —— 受害者的会话会被静默换成攻击者账号。
  //   注意 consume 是**一次性**的（无论成败都作废），避免重放。
  const boundState = consumeOAuthState(event)
  if (!boundState || boundState !== state) {
    throw createError({
      statusCode: 400,
      statusMessage: '登录会话已失效，请重新发起 Google 登录',
    })
  }

  // lang 必须先算出来：兜底回调路径要按语言补前缀（见 defaultCallbackPath 注释）
  const lang = resolveStoreLang(body?.lang, 'en')

  // ★ 服务端自拼，忽略 body 里的任何 redirectUri（见文件头说明）。
  //   必须与 auth-url 阶段拼出**逐字相同**的地址，否则 Google 报 redirect_uri_mismatch。
  //   origin 同样优先取配置值，避免采信可伪造的 Host/X-Forwarded-Host。
  const origin = resolvePublicOrigin(event)
  const redirectUri = `${origin}${defaultCallbackPath(lang)}`

  // ★ 这 30s 是刻意放宽的，不要并回默认的 10s：
  //   backend 这一跳里还要去 Google 换 token（墙内经常要经代理，单次 0.2s～15s 都出现过）。
  //   后端 OkHttp 的单次调用上限是 25s（ShopGoogleOAuthService），BFF 必须比它大，
  //   否则 BFF 先超时 → 用户只看到"后端服务响应超时"，真正原因（Google 网络异常）被盖住；
  //   而且 state 已经被后端消费，用户点"重试"也只会得到"登录已过期"。
  const api = createApiClient(event, { lang, timeoutMs: 30000 })

  let result: { token: string; userInfo: Record<string, any>; isNewUser?: boolean }
  try {
    result = await api<{ token: string; userInfo: Record<string, any>; isNewUser?: boolean }>(
      '/shop/account/google/callback',
      {
        method: 'POST',
        body: { code, state, redirectUri },
      },
    )
  } catch (error: any) {
    // 后端业务文案原样透传：
    // "state 已失效，请重新登录" / "Google 授权失败" / "该账号已被冻结" 等。
    //
    // 504 是 BFF 自己等不到后端（后端还在等 Google）时抛的：此时 state 十有八九
    // 已被后端消费，复用同一组 code/state 重试必然失败，所以文案要直接引导
    // 用户回登录页重新发起，而不是给一句看不出下一步的"稍后重试"。
    const statusCode = error?.statusCode || 500
    const statusMessage = statusCode === 504
      ? '服务器访问 Google 超时，本次登录凭证已作废，请返回登录页重新发起 Google 登录'
      : (error?.statusMessage || 'Google 登录失败，请稍后重试')
    throw createError({ statusCode, statusMessage })
  }

  // ============================================================
  // ★★★ token 存储策略（与邮箱登录路径**完全一致**）★★★
  // token 只进 httpOnly cookie；返回值里没有 token。
  // Secure/有效期统一由 authCookie.ts 决定（默认 Secure=true）。
  // ============================================================
  setAccessTokenCookie(event, result.token)

  // isNewUser 透传给前端：Google 新注册用户没有密码，
  // 回调页可据此引导去设置密码（契约 2.7 的 change-password）。
  return {
    userInfo: result.userInfo,
    isNewUser: result.isNewUser ?? false,
  }
})
