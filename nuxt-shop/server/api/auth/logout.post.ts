// server/api/auth/logout.post.ts
//
// 登出必须**同时**做两件事：
//   1) 让后端把这张令牌作废（JeecgBoot 的 /sys/logout 会删掉 Redis 里的登录态，
//      JWT 本身在过期前仍然"签名有效"，不作废的话被复制走的令牌还能继续用）；
//   2) 清掉浏览器里的 httpOnly cookie。
//
// 之前这里只做了第 2 步，第 1 步是注释掉的死代码 —— 用户点了"退出登录"，
// 服务端那张令牌却依然有效（在共享电脑、被 XSS 的富域名、日志泄露等场景下，
// 这是真实可利用的窗口）。
//
// 但顺序上仍以"用户体验"兜底：即使后端作废失败（后端没起、网络抖动），
// 也**必须**清掉本地 cookie，否则用户会卡在"点退出没反应"的状态。
// 失败情况记 warn 日志，并把 revoked 回给前端便于排查。

import type { H3Event } from 'h3'

export default defineEventHandler(async (event: H3Event) => {
  const token = getCookie(event, ACCESS_TOKEN_COOKIE)

  let revoked = false
  if (token) {
    try {
      const api = createApiClient(event, { withAuth: true })
      await api('/sys/logout', { method: 'POST' })
      revoked = true
    } catch (error: any) {
      console.warn(
        `[/api/auth/logout] 后端令牌作废失败（仍会清除本地 cookie）: `
        + `${error?.statusMessage || error?.message || error}`,
      )
    }
  }

  clearAccessTokenCookie(event)

  return { success: true, revoked }
})
