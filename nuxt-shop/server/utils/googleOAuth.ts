// server/utils/googleOAuth.ts
//
// Google 登录用到的两个安全辅助函数。
//
// 【1】origin 必须可配置，不能只靠请求头推导
//   redirect_uri 由 BFF 拼给 Google，Google 会逐字节校验它与签发 code 时一致。
//   之前两端都用 getRequestURL(event).origin —— 它采信 Host / X-Forwarded-Host，
//   若前置代理不校验 Host，攻击者可以伪造 Host，让 BFF 拿一份**攻击者指定**的
//   origin 去换 code。配置了 STORE_PUBLIC_ORIGIN 后，origin 完全由本站配置决定，
//   伪造 Host 不再起任何作用（未配置时才退回请求 origin，并在 README 里说明）。
//
// 【2】state 必须与浏览器会话绑定（否则是 login CSRF）
//   后端的 state 是它自己写进 Redis 的一次性令牌，BFF 无法替换值，只能**额外**
//   把同一个值写进一张 httpOnly cookie，并在回调时比对：
//     攻击者可以在自己浏览器里跑完授权流程、拿到 {code, state}，
//     再诱使受害者浏览器去 POST 这组值（BFF 是匿名接口，谁来都收）。
//     没有 cookie 绑定，受害者的会话就会被换成攻击者的账号 ——
//     之后他自己的下单、地址、订单全部落在攻击者账号里。
//     有了绑定，受害者的浏览器里没有这张 cookie（或值与攻击者那份不同）→ 直接拒绝。
//   这也是 Google 官方推荐的做法（state 与用户会话关联）。

import type { H3Event } from 'h3'

/** 与后端 state 的 TTL（契约 2.5：10 分钟）保持一致 */
export const OAUTH_STATE_COOKIE = 'oauth-state'
const OAUTH_STATE_MAX_AGE_SECONDS = 600

/**
 * 本站对外 origin：配置优先，未配置才回退到本次请求的 origin。
 * 返回值不带结尾斜杠，便于直接拼接回调路径。
 */
export function resolvePublicOrigin(event: H3Event): string {
  const config = useRuntimeConfig(event)
  const configured = String(config.publicOrigin || '').trim().replace(/\/+$/, '')
  return configured || getRequestURL(event).origin
}

/** 把本次授权流程的 state 绑定到浏览器会话（httpOnly，10 分钟） */
export function bindOAuthState(event: H3Event, state: string): void {
  const config = useRuntimeConfig(event)
  setCookie(event, OAUTH_STATE_COOKIE, state, {
    httpOnly: true,
    secure: Boolean(config.cookieSecure),
    sameSite: 'lax',
    path: '/',
    maxAge: OAUTH_STATE_MAX_AGE_SECONDS,
  })
}

/**
 * 取出并**立即作废**绑定的 state（一次性）。
 * 无论后续校验成功与否都调用它，避免同一张 cookie 被反复重放。
 */
export function consumeOAuthState(event: H3Event): string {
  const value = getCookie(event, OAUTH_STATE_COOKIE) || ''
  deleteCookie(event, OAUTH_STATE_COOKIE, { path: '/' })
  return value
}
