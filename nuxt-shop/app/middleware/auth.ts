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
  // 透传浏览器 cookie 是 SSR 能判出登录态的前提。
  //
  // ⚠️ 这里为什么不用 refreshSession()：
  // 契约冻结的签名是 `refreshSession(): Promise<boolean>`，内部固定 `$fetch('/api/auth/session')`
  // 且不接受 headers 参数。而 SSR 期间 $fetch 是"服务端 → 自己的 Nitro"，
  // 不会自动携带浏览器 cookie —— 不透传就会被判成未登录，首屏白白闪一下。
  // 所以这里按契约第 3 节「服务端用 useRequestHeaders(['cookie']) 转发」的要求，
  // 自己发这一次探测请求；refreshSession() 仍由页面/Header 在客户端调用。
  //
  // 用 import.meta.server 守卫：useRequestHeaders 在客户端调用会直接抛错；
  // 客户端这里给 undefined，浏览器会自动带上同源 cookie。
  const headers = import.meta.server ? useRequestHeaders(['cookie']) : undefined

  let authenticated: boolean
  let sessionUser: Record<string, any> | null = null
  try {
    const session = await $fetch<{ authenticated: boolean, userInfo: Record<string, any> | null }>(
      '/api/auth/session',
      { headers },
    )
    authenticated = !!session?.authenticated
    sessionUser = session?.userInfo ?? null
  } catch {
    // /api/auth/session 契约上永远返回 200，走到这里说明是网络层异常。
    // fail-closed：无法确认登录态时按未登录处理，宁可让用户重新登录，
    // 也不能把私有页面放出去。公开页不经过本中间件，所以影响范围受控。
    authenticated = false
  }

  if (authenticated) {
    // ★ SSR 首屏必须把 userInfo 一起种进去，否则页面照样闪。
    //
    // 上面那次请求**已经**从后端拿到了完整的 userInfo（/api/auth/session 的返回值里就有），
    // 以前只取了 authenticated、把 userInfo 丢掉，于是出现这个自相矛盾的现象：
    //   中间件在服务端判定「已登录」并放行，但页面 `v-if="!isLoggedIn"` 读到的 userInfo 仍是 null
    //   （useState 在服务端没有 localStorage 可读，.client.ts 插件在 SSR 阶段也不执行）
    //   → **SSR 输出的 HTML 里是「您还没有登录」引导**；
    //   浏览器先把这段 HTML 画出来，等客户端插件 restoreUser() 从 localStorage 回填后才翻回已登录态
    //   —— 这就是用户反馈的「每次刷新都闪一下未登录页」。
    //
    // 把服务端拿到的真实用户种进 useState 后，SSR 输出与客户端 hydration 用的是同一份数据，
    // 首屏直接就是已登录内容，不需要等 localStorage 回填，闪烁消失。
    // 注意：这里只同步**纯展示**用的 userInfo；真正的凭证 token 仍只在 httpOnly cookie 里，不落到前端。
    if (sessionUser) {
      const { userInfo, authChecked } = useAuth()
      if (!userInfo.value) {
        userInfo.value = sessionUser
      }
      // 服务端刚刚已经权威校验过登录态，标记为已校验，
      // 免得客户端的 refreshSession() 在首屏再白问一次（它仍会跑，结果以它为准）。
      authChecked.value = true
    }
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
