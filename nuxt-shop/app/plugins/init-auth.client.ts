// app/plugins/init-auth.client.ts
//
// 文件名里的 .client.ts 表示这个插件只在浏览器端执行（服务端渲染阶段跳过），
// Nuxt 会在应用启动时自动执行这里的代码。
//
// 与 app/middleware/auth-seed.global.ts 的分工：
//   · SSR 阶段已经用 useRequestFetch() 查过 /api/auth/session，并把
//     userInfo / authChecked / 最近校验时间戳种进 useState → 首屏 HTML 与 hydration
//     用的是同一份数据，公开页不会再闪「登录/注册」。
//   · 因此本插件在**有权威结论**时什么都不用做，只需与那个结论对齐（见下面第 1 步）。
//
// 这里做三件事，顺序不能反：
//
//   1) 与权威结论对齐：若 SSR 刚刚校验过（结论新鲜），就**不再相信 localStorage**。
//      这一步是修「被踢到 /login 之后右上角过一会儿又变回已登录」的关键：
//      服务端已经明确说"这张 cookie 不认"，而 localStorage 里的 user-info 是上一次
//      登录留下的死缓存，restoreUser() 会把它读回来 —— 于是页面上出现
//      "地址簿说未登录、右上角说已登录"的自相矛盾（用户实测报告过的现象）。
//      所以在新鲜期内的唯一真相是 useState 里的 userInfo（SSR 结论），
//      它是空的就把 localStorage 一起清掉。
//
//   2) restoreUser()：只在**没有**权威结论时兜底回显（SSR 未播种、后端抖动、SPA 场景）。
//      目的只是"别闪"——已经登录的用户刷新页面时，头像不会先消失再出现。
//
//   3) refreshSession()：问一次服务端，**以服务端结果为准**。
//      这是修「登录过期了头像还在」的关键：token 存在 httpOnly cookie 里，它过期时
//      localStorage 里的 user-info 不会跟着消失，光靠本地缓存会一直显示"已登录"，
//      但实际调后端接口全是 401。refreshSession() 会在服务端确认失效时清空 userInfo
//      与本地缓存，于是头像/角标自动消失。
//
// 注意：refreshSession() **不需要 await**（插件不必阻塞应用启动）。让用户先看到页面，
// 服务端结果回来后响应式状态自己更新即可；要"先验证再渲染"的是受保护页面，
// 那件事由 app/middleware/auth.ts 在路由层完成（它发现结论足够新鲜时直接复用，不再重复请求）。
//
// 另外：token 是在**两次校验之间**过期的，所以除了这里的冷启动校验，还有两处兜底 ——
// 受保护页导航前的按新鲜度重校验（app/middleware/auth.ts）与接口 401 的全局处理
// （app/plugins/api-session-guard.client.ts）。

import { useAuth } from '~~/composables/useAuth'

export default defineNuxtPlugin(() => {
  const { restoreUser, refreshSession, userInfo, authVerifiedAt, isSessionCheckFresh, clearSession } = useAuth()

  /*
   * 冷启动去重（清单 S20）与"以权威结论为准"。
   *
   * SSR 刚刚查过一次 /api/auth/session 并种进了 useState，这里再问一次就是
   * 同一秒里的重复请求（还会跟 Header 的 /api/cart/count 撞在一起）。
   * 只要结论足够新（AUTH_SEED_FRESH_MS 内），就跳过这次校验：
   *   · 播种来自服务端，本身就是权威结果；
   *   · 超时后（或 SPA 场景没有播种）照旧走 refreshSession 的完整校验；
   *   · authVerifiedAt 为 0 说明从来没校验过（连时间戳都没有），也必须走完整校验 ——
   *     只凭 authChecked 无法判断"这份结论有多旧"，而"多旧"正是 token 失效能否被发现的关键。
   */
  if (authVerifiedAt.value > 0 && isSessionCheckFresh()) {
    // 权威结论 = 未登录，但 localStorage 里还留着上次登录的缓存：
    // 必须清掉，否则 restoreUser() 会把它读回来，页面就会"服务端说未登录、右上角说已登录"。
    if (!userInfo.value) clearSession()
    return
  }

  // 没有权威结论：先用本地缓存回显（避免头像闪没），再问服务端定位真实状态。
  // 失败（断网等）时 refreshSession 会把本地缓存清掉并按未登录处理 ——
  // 这是有意的 fail-closed：无法确认登录态时，不能继续显示"已登录"。
  // 它同时会刷新「最近校验时间」，后续客户端导航的 auth 中间件据此决定要不要再探测。
  restoreUser()
  refreshSession()
})
