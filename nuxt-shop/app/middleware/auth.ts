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
// 表现为刷新时闪一下未登录引导 —— 详见下方 authenticated 分支里的注释。
// 显式 import 而不是靠自动导入：composables/ 在 srcDir(app/) 之外，Nuxt 不会自动注册。
import { useAuth } from '~~/composables/useAuth'

export default defineNuxtRouteMiddleware(async (to) => {
  const { userInfo, authChecked } = useAuth()

  let authenticated: boolean
  let sessionUser: Record<string, any> | null = null

  if (authChecked.value) {
    // ★ 已经权威校验过：直接复用结果，**不再重复请求** /api/auth/session。
    //
    // 谁会把它置为 true：
    //   · SSR 阶段 app/middleware/auth-seed.global.ts 已经播种（公开页也经过它）；
    //   · 客户端 app/plugins/init-auth.client.ts 的 refreshSession() 在应用启动时跑过；
    //   · 登录/注册成功时 useAuth().acceptAuthResult()。
    // 以前这里无论是否校验过都要再问一次服务端，导致受保护页每次加载打两个 session 请求
    // （SSR 播种一个 + 本中间件一个），客户端切页再多一个。
    authenticated = !!userInfo.value
    sessionUser = userInfo.value as Record<string, any> | null
  } else {
    // 兜底路径：没有被播种时自己查一次。
    //
    // 用 useRequestFetch() 而不是 $fetch：SSR 期间 $fetch 是"服务端 → 自己的 Nitro"，
    // 不会自动携带浏览器 cookie；useRequestFetch() 会转发当前请求的 cookie，
    // 客户端则等价于普通 $fetch（浏览器自动带同源 cookie）。
    // 这一步拿到的 userInfo 必须一起种进 useState，否则会出现
    // "中间件判定已登录、页面按未登录渲染"的首屏闪烁。
    const requestFetch = useRequestFetch()
    try {
      const session = await requestFetch<{ authenticated: boolean, userInfo: Record<string, any> | null }>(
        '/api/auth/session',
      )
      authenticated = !!session?.authenticated
      sessionUser = session?.userInfo ?? null
    } catch {
      // /api/auth/session 契约上永远返回 200，走到这里说明是网络层异常。
      // fail-closed：无法确认登录态时按未登录处理，宁可让用户重新登录，
      // 也不能把私有页面放出去。
      authenticated = false
    }
  }

  if (authenticated) {
    // 只同步**纯展示**用的 userInfo；真正的凭证 token 仍只在 httpOnly cookie 里，不落到前端。
    // 类型桥接：session 返回的是扁平 Record<string, any>，展示层 UserInfo 的
    // realname/username 由后端保证存在（见 auth-seed.global.ts 的同类说明）。
    if (sessionUser && !userInfo.value) {
      userInfo.value = sessionUser as unknown as NonNullable<typeof userInfo.value>
    }
    // 服务端/客户端刚刚已经权威校验过登录态，标记为已校验，
    // 免得后续导航或 refreshSession 再白问一次。
    authChecked.value = true
    return
  }

  // 必须用 useLocalePath()：i18n 策略是 prefix，所有语言都带前缀（/zh/、/en/）。
  // 直接 navigateTo('/login') 会跳到一个没有语言前缀的路径，
  // 对 prefix 策略来说是非法路由（会被再重定向一次，或落到错误语言）。
  const localePath = useLocalePath()

  // 带上 redirect 让用户登录后回到原本想去的页面。
  // to.fullPath 天然是站内路径（形如 /zh/cart?sku=1），
  // 正好通过 login.vue 里 /^\/(?!\/)/ 的开放重定向校验；且含语言前缀，登录后能原样回跳。
  return navigateTo(localePath({ path: '/login', query: { redirect: to.fullPath } }))
})
