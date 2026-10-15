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

/** 写入登录态 cookie（httpOnly：前端 JS 读不到 token） */
export function setAccessTokenCookie(event: H3Event, token: string): void {
  const config = useRuntimeConfig(event)
  setCookie(event, ACCESS_TOKEN_COOKIE, token, {
    httpOnly: true,
    secure: Boolean(config.cookieSecure),
    sameSite: 'lax',
    path: '/',
    maxAge: ACCESS_TOKEN_MAX_AGE_SECONDS,
  })
}

/** 清除登录态 cookie（登出、token 已失效时调用） */
export function clearAccessTokenCookie(event: H3Event): void {
  deleteCookie(event, ACCESS_TOKEN_COOKIE, { path: '/' })
}
