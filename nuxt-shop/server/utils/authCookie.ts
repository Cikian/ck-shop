// server/utils/authCookie.ts
//
// 登录 cookie 的**唯一写入点**。
//
// 为什么抽出来：此前 4 条登录路径（邮箱密码 / 邮箱验证码 / 注册 / Google）
// 各自复制了一份 setCookie，其中 secure 标记都写成
//   secure: process.env.NODE_ENV === 'production'
// 而生产启动命令（package.json 的 start）**从来没有设置 NODE_ENV**，
// 于是"生产环境"这个判断永远不成立 —— 7 天有效的登录 cookie 在线上
// 全程不带 Secure，明文的任何一个请求都可能把这个令牌带走。
//
// 现在标记由 runtimeConfig.cookieSecure 决定（默认 true，只有显式
// COOKIE_SECURE=false 才关闭），与 NODE_ENV 是否被设置彻底解耦。

import type { H3Event } from 'h3'

/** cookie 名：server/utils/apiClient.ts 与 session.get.ts 都按这个名字读，改名要同步 */
export const ACCESS_TOKEN_COOKIE = 'access-token'

/** 与后端 token 有效期对齐（7 天）。改动前请先确认 Java 侧 JWT 的过期时间。 */
const ACCESS_TOKEN_MAX_AGE_SECONDS = 60 * 60 * 24 * 7

/**
 * 解析 JWT 的 exp（秒）。
 *
 * <p>**不校验签名**：签名由 Java 侧校验，这里只用来决定"要不要续期"和"cookie 该活多久"。
 * 解析失败返回 null（当作未知，按固定 7 天处理）。
 */
export function accessTokenExpiresAt(token: string): number | null {
  const parts = String(token || '').split('.')
  if (parts.length !== 3) return null
  try {
    const payload = JSON.parse(Buffer.from(parts[1]!, 'base64url').toString('utf8'))
    const exp = Number(payload?.exp)
    return Number.isFinite(exp) && exp > 0 ? exp : null
  } catch {
    return null
  }
}

/** 写入登录态 cookie（httpOnly：前端 JS 读不到 token） */
export function setAccessTokenCookie(event: H3Event, token: string, maxAgeSeconds?: number): void {
  const config = useRuntimeConfig(event)
  /*
   * cookie 的生命周期以 **token 自身的 exp** 为准（清单 S25）。
   * 以前固定写 7 天：续期换到新 token 后，cookie 仍然按旧的 7 天算，
   * 于是出现"cookie 还在、token 已过期"或反过来"token 还有效、cookie 先没了"的错位。
   */
  const exp = accessTokenExpiresAt(token)
  const remaining = exp === null ? null : exp - Math.floor(Date.now() / 1000)
  const maxAge = maxAgeSeconds ?? (remaining !== null && remaining > 0 ? remaining : ACCESS_TOKEN_MAX_AGE_SECONDS)
  setCookie(event, ACCESS_TOKEN_COOKIE, token, {
    httpOnly: true,
    secure: Boolean(config.cookieSecure),
    sameSite: 'lax',
    path: '/',
    maxAge,
  })
}

/** 清除登录态 cookie（登出、token 已失效时调用） */
export function clearAccessTokenCookie(event: H3Event): void {
  deleteCookie(event, ACCESS_TOKEN_COOKIE, { path: '/' })
}
