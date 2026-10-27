// app/middleware/auth.ts
// 命名路由中间件：受保护页面的登录守卫。
//
// 用法：在页面 <script setup> 里写一行
//   definePageMeta({ middleware: 'auth' })
// Nuxt 会在进入该页面前先执行这里的逻辑。
//
// ⚠️ 为什么文件在 app/ 下而不是仓库根目录的 middleware/：
// 本项目的 srcDir 是 app/（app/ 目录存在时 Nuxt 4 的默认行为），
// Nuxt 只在 srcDir 下扫描 middleware/pages/components。
// 根目录那个 middleware/auth.ts 不会被注册 —— 实测访问受保护页会直接
// 500 "Unknown route middleware: 'auth'. Valid middleware: ."。
// 所以真正生效的必须是本文件。
//
// 关于登录态判定：**绝不能**用 useAuth().isLoggedIn。
// 它是 computed(() => !!userInfo.value)，而 userInfo 来自 localStorage 缓存，
// cookie 过期后缓存仍在 → isLoggedIn 依然为 true →「登录过期」永远识别不出来。
// 唯一权威来源是服务端：读 httpOnly cookie 并向后端校验（/api/auth/session）。
//
// ⚠️ 但「判定」之外还有一件同样重要的事：**把判定结果里的 userInfo 种给页面**。
// 只判定不种数据，就会出现「服务端放行了、页面却按未登录渲染」的自相矛盾，
// 表现为刷新时闪一下未登录引导 —— 详见下方注释。
//
// ⚠️ 「只查过一次」不等于「结论一直有效」（2026-10-27 修）：
// 旧版只要看到 authChecked === true 就直接复用上次结论、**再也不问服务端**。
// 而 token 会在两次校验之间失效（TTL 到点、管理员踢人、别处登录挤掉），
// 于是出现用户实测到的严重问题：
//   「token 已经失效，却仍能进入个人中心、打开地址管理；
//     点『新增地址』才报 token 失效，而右上角一直显示已登录。」
// 现在改为按「新鲜度」复用：isSessionCheckFresh()（默认 60s）之内才信任上次结论，
// 超过就重新问一次服务端；服务端说不认识这张 cookie 时，**顺手清掉前端缓存**
// （clearSession 会删 localStorage 里的 user-info），让页面立刻回到未登录样子。
// 接口层面的 401 另有全局兜底（app/plugins/api-session-guard.client.ts），
// 两者一起才能覆盖「停在页面上不动，token 恰好过期」这种情况。
//
// 显式 import 而不是靠自动导入：composables/ 在 srcDir(app/) 之外，Nuxt 不会自动注册。
import type { AuthSession } from '#server/api/auth/session.get'
import { useAuth } from '~~/composables/useAuth'
import { useSessionExpiry } from '~~/composables/useSessionExpiry'

export default defineNuxtRouteMiddleware(async (to) => {
  const { userInfo, isSessionCheckFresh, markVerified, clearSession } = useAuth()
  const { notifySessionExpired } = useSessionExpiry()

  let authenticated: boolean

  if (isSessionCheckFresh()) {
    // ★ 上一次权威校验的结论还新鲜（默认 60 秒内）：直接复用，**不再重复请求** /api/auth/session。
    //
    // 谁会刷新这个新鲜度：
    //   · SSR 阶段 app/middleware/auth-seed.global.ts 已经播种（公开页也经过它）；
    //   · 客户端 app/plugins/init-auth.client.ts 的 refreshSession() 在应用启动时跑过；
    //   · 登录/注册成功时 useAuth().acceptAuthResult()；
    //   · 上一次走到下面的"重新校验"分支。
    // 以前这里只要 authChecked 为 true 就复用，导致受保护页在 token 失效后仍被放行；
    // 现在多了时间维度，过期即重查 —— 但 60 秒内仍只有至多一次 session 请求（清单 S20）。
    authenticated = !!userInfo.value
  } else {
    // 结论过期（或从来没校验过）：自己去问服务端。
    //
    // 用 useRequestFetch() 而不是 $fetch：SSR 期间 $fetch 是"服务端 → 自己的 Nitro"，
    // 不会自动携带浏览器 cookie；useRequestFetch() 会转发当前请求的 cookie，
    // 客户端则等价于普通 $fetch（浏览器自动带同源 cookie）。
    const requestFetch = useRequestFetch()
    try {
      const session = await requestFetch<AuthSession>('/api/auth/session')
      if (session?.authenticated && session.userInfo) {
        // 种给页面：否则会出现"中间件判定已登录、页面按未登录渲染"的首屏闪烁。
        // 直接覆盖旧值是有意的 —— 服务端结果是权威的，缓存里的可能是过期快照。
        // 类型桥接：session 返回扁平 Record<string, any>，展示层 UserInfo 的
        // realname/username 由后端保证存在（见 auth-seed.global.ts 的同类说明）。
        userInfo.value = session.userInfo as unknown as NonNullable<typeof userInfo.value>
        markVerified()
        authenticated = true
      } else {
        // 服务端明确不认这张 cookie：**必须清掉本地缓存**。
        // 只把 authenticated 置 false 是不够的 —— localStorage 里的 user-info 还在，
        // 下一次刷新 restoreUser() 又会把"已登录"读回来，右上角继续显示头像。
        //
        // 另外：如果前端原本认为自己"已登录"，说明这是一次"登录过期"，
        // 要明确告诉用户为什么被拦下来（否则他只看到"突然让我登录"）。
        const wasLocallyLoggedIn = !!userInfo.value
        clearSession()
        if (wasLocallyLoggedIn) notifySessionExpired({ source: 'route-guard' })
        authenticated = false
      }
    } catch {
      // /api/auth/session 契约上永远返回 200，走到这里说明是网络层异常。
      // fail-closed：无法确认登录态时按未登录处理，宁可让用户重新登录，
      // 也不能把私有页面放出去。这里**不清缓存**：可能只是后端抖了一下，
      // 缓存留着，等后端恢复后用户刷新页面还能自动回到登录态。
      authenticated = false
    }
  }

  if (authenticated) return

  // 必须用 useLocalePath()：i18n 策略是 prefix，所有语言都带前缀（/zh/、/en/）。
  // 直接 navigateTo('/login') 会跳到一个没有语言前缀的路径，
  // 对 prefix 策略来说是非法路由（会被再重定向一次，或落到错误语言）。
  const localePath = useLocalePath()

  // 带上 redirect 让用户登录后回到原本想去的页面。
  // to.fullPath 天然是站内路径（形如 /zh/cart?sku=1），
  // 正好通过 login.vue 里 /^\/(?!\/)/ 的开放重定向校验；且含语言前缀，登录后能原样回跳。
  //
  // replace 而不是 push：这次进不去的页面不该留在历史里，
  // 否则用户按"后退"会反复撞回同一个被拦截的地址。
  return navigateTo(localePath({ path: '/login', query: { redirect: to.fullPath } }), { replace: true })
})
