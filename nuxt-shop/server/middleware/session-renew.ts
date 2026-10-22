// server/middleware/session-renew.ts
//
// 登录态滑动续期（清单 S25）。
//
// 现状：JWT 固定 7 天过期，cookie 也固定 7 天 —— 用户在第 7 天会被"突然"登出，
// 哪怕他每天都在用。对复购型电商来说这是明确的转化损失。
//
// 做法：
//   1. 每个 /api/* 请求先看 cookie 里的 token 剩余有效期；
//   2. 剩余不足 RENEW_THRESHOLD_SECONDS（1 天）时，调后端 /shop/account/refresh-token
//      换一张新 token（后端重新签名并写 Redis，有效期重新从 7 天算）；
//   3. 用新 token 覆盖 cookie（cookie 的 maxAge 也按新 token 的 exp 算）。
//
// 为什么不是"每次请求都续"：
//   · 每个响应都会多一个 Set-Cookie，破坏 CDN/浏览器缓存；
//   · 后端每次都要读一次用户表 + 写一次 Redis，纯属浪费。
//
// 失败处理：续期失败（后端 401/网络错误）**不阻断本次请求**，
// 让原有的鉴权流程去处理（该 401 就 401），避免因为"续期"把正常请求打挂。

import { ACCESS_TOKEN_COOKIE, accessTokenExpiresAt, clearAccessTokenCookie, setAccessTokenCookie } from '../utils/authCookie'

/** 剩余有效期少于这个值就续期（1 天） */
const RENEW_THRESHOLD_SECONDS = 24 * 60 * 60

export default defineEventHandler(async (event) => {
  const path = event.path || ''
  if (!path.startsWith('/api/')) return

  const token = getCookie(event, ACCESS_TOKEN_COOKIE)
  if (!token) return

  const exp = accessTokenExpiresAt(token)
  if (exp === null) return

  const remaining = exp - Math.floor(Date.now() / 1000)
  // 已过期：交给后端 401 流程清理，不在这里续
  if (remaining <= 0) return
  if (remaining > RENEW_THRESHOLD_SECONDS) return

  try {
    const api = createApiClient(event, { withAuth: true })
    const fresh = await api<string>('/shop/account/refresh-token', { method: 'POST' })
    if (fresh && typeof fresh === 'string' && fresh.split('.').length === 3) {
      setAccessTokenCookie(event, fresh)
    }
  } catch (error) {
    // 账号被冻结/注销时后端会拒绝：顺手清掉本地登录态，让前端回到登录页
    const status = (error as { statusCode?: number })?.statusCode
    if (status === 401 || status === 403) {
      clearAccessTokenCookie(event)
    }
    // 其它错误（网络抖动）忽略：本次请求继续用旧 token
  }
})
