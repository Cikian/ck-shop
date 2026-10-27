// app/plugins/api-session-guard.client.ts
//
// **登录态失效的全局兜底**：把 app 里所有 `$fetch` 调用都包一层，
// 只要某个 `/api/*` 请求返回 401（且本地还认为已登录），就交给
// composables/useSessionExpiry.ts 统一处理（清态 + 提示 + 受保护页跳登录）。
//
// ## 为什么要做到"全局"这一层
//
// 改造前 401 是各自处理的：有人置一个 `unauthorized` 标记显示"请先登录"，
// 有人直接 `return`，有人当业务错误弹提示 —— 但**谁都没有清前端的登录态**。
// 于是 token 过期后：右上角仍显示已登录、还能进个人中心/地址管理，
// 直到点了「新增地址」才蹦出一条「未登录或登录已过期」，而人还"登录着"。
//
// 逐个 catch 去补是补不完的（本仓库有 20+ 个调用点，以后还会加）。所以这里
// 从唯一入口兜底：**只要请求 401，登录态就一定被清掉**，各页面不需要记得处理。
// 页面自己的 catch 仍可以继续做它该做的事（比如显示"请先登录"引导），
// 只是不该再弹"操作失败"这类与真实原因无关的提示（见各处的 isUnauthorizedError 早退）。
//
// ## 为什么是包一层而不是 `$fetch.create({ onResponseError })`
//
// `$fetch.create()` 会**丢掉** Nuxt 已经给全局 $fetch 配好的默认值（并且会绕过
// Nuxt 后续对全局实例的替换）。包一层只观察"异常"，请求/响应行为 100% 保持原样，
// 也不改变任何调用签名与返回值。
//
// ## 只认 401，不认 403
//
// 403 是"已登录但没有权限"，把用户踢下线是错的；401 才是"凭证无效/已过期"。
// 调用点上的 `isUnauthorizedError()` 仍然把 401/403 一起当"要引导登录"，
// 那是页面级语义，与"强制下线"不是一回事。

import { useSessionExpiry } from '~~/composables/useSessionExpiry'

/**
 * 这些接口的 401 **不代表登录态过期**，不能触发"强制下线"：
 *   · 登录/注册/发码/重置密码 —— 401 属于"这次登录没成功"，由登录页自己提示；
 *   · google 回调/授权地址 —— 同上；
 *   · logout —— 本来就是来登出的；
 *   · session —— 契约上永远返回 200，且它自己会清 cookie；
 *   · captcha —— 匿名验证码网关，与登录态无关。
 *
 * 注意 `/api/auth/profile`、`/api/auth/change-password` **不在**名单里：
 * 它们确实要求登录，401 就是过期，必须走统一处理。
 */
const SKIP_PREFIXES = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/send-code',
  '/api/auth/reset-password',
  '/api/auth/google',
  '/api/auth/logout',
  '/api/auth/session',
  '/api/captcha',
]

/** 把 `$fetch` 的第一个参数（字符串 / URL / Request）统一收敛成"路径 + 查询" */
function requestPathOf(input: unknown): string {
  if (typeof input === 'string') return input
  try {
    if (input instanceof URL) return `${input.pathname}${input.search}`
    if (typeof Request !== 'undefined' && input instanceof Request) {
      const url = new URL(input.url)
      return `${url.pathname}${url.search}`
    }
  } catch {
    // 取不出路径就当作"未知路径"：下面会按"需要处理"对待（宁可多清一次登录态）
  }
  return ''
}

/** 是不是一次 401（ofetch 的 FetchError 上 statusCode / status / response.status 都可能有） */
function isUnauthorizedStatus(error: unknown): boolean {
  const err = error as any
  const status = err?.statusCode ?? err?.status ?? err?.response?.status
  return status === 401
}

export default defineNuxtPlugin(() => {
  const { handleSessionExpired } = useSessionExpiry()

  const original = (globalThis as any).$fetch as typeof globalThis.$fetch | undefined
  if (typeof original !== 'function') return

  const wrapped = (async (input: any, options: any) => {
    try {
      return await (original as any)(input, options)
    } catch (error) {
      // 重点：**不吞掉异常**。调用方该拿到的错误照旧往上抛，
      // 这里只做"顺手把登录态收拾干净"，不改变任何既有控制流。
      if (isUnauthorizedStatus(error)) {
        const path = requestPathOf(input)
        if (!SKIP_PREFIXES.some(prefix => path.startsWith(prefix))) {
          handleSessionExpired({ source: path || 'unknown-api' })
        }
      }
      throw error
    }
  }) as typeof globalThis.$fetch

  // 保留 ofetch 实例上的 `create` / `raw`：别处若用 $fetch.create 定制实例，行为不变
  ;(wrapped as any).create = (original as any).create
  ;(wrapped as any).raw = (original as any).raw

  ;(globalThis as any).$fetch = wrapped
})
