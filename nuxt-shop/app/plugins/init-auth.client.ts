// app/plugins/init-auth.client.ts
//
// 文件名里的 .client.ts 表示这个插件只在浏览器端执行（服务端渲染阶段跳过），
// Nuxt 会在应用启动时自动执行这里的代码。
//
// 现状（与 app/middleware/auth-seed.global.ts 配合）：
//   · SSR 阶段已经用 useRequestFetch() 查过 /api/auth/session，并把
//     userInfo / authChecked 种进 useState → 首屏 HTML 与 hydration 用的是同一份数据，
//     公开页不会再闪「登录/注册」。
//   · 因此这里 restoreUser() 在正常情况下是 no-op（userInfo 已有值），
//     只有 SSR 未播种（后端抖动、或 SPA 场景）时才靠 localStorage 回显。
//
// 这里仍然要做两件事，顺序不能反：
//
//   1) restoreUser()：用 localStorage 缓存兜底回显。
//      目的只是"别闪"——已经登录的用户刷新页面时，头像不会先消失再出现。
//
//   2) refreshSession()：问一次服务端，**以服务端结果为准**。
//      这一步是修「登录过期了头像还在」的关键：
//      token 存在 httpOnly cookie 里，它过期时 localStorage 里的 user-info
//      不会跟着消失，光靠本地缓存会一直显示"已登录"（头像、购物车角标都在），
//      但实际调后端接口全是 401。
//      refreshSession() 会在服务端确认失效时清空 userInfo 与本地缓存，
//      于是头像/角标自动消失，和真实登录态保持一致。
//
// 注意：这里**不需要 await**（插件不必阻塞应用启动）。让用户先看到页面，
// 服务端结果回来后响应式状态自己更新即可；要"先验证再渲染"的是受保护页面，
// 那件事由 app/middleware/auth.ts 在路由层完成（它发现 authChecked 已为 true 时直接复用，不再重复请求）。

import { useAuth } from '~~/composables/useAuth'

export default defineNuxtPlugin(() => {
  const { restoreUser, refreshSession } = useAuth()

  restoreUser()

  // 失败（断网等）时 refreshSession 会把本地缓存清掉并按未登录处理 ——
  // 这是有意的 fail-closed：无法确认登录态时，不能继续显示"已登录"。
  // 它同时会把 authChecked 置 true，后续客户端导航的 auth 中间件因此不再重复探测。
  refreshSession()
})
