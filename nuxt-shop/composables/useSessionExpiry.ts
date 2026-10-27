// composables/useSessionExpiry.ts
//
// 「登录态已失效」的**统一处理**（清态 + 提示 + 引导重新登录）。
//
// ## 为什么必须有这个文件
//
// 改造前每一处调接口的 catch 都各自处理 401：有的把 `unauthorized` 置 true 显示一句
// 「请先登录」，有的直接 `return` 什么也不做，有的把 401 当普通业务错误弹一条
// 「新增地址失败：未登录或登录已过期」。但**没有任何一处**去清前端的登录态 ——
// 于是出现用户在意的那个现象：
//
//   token 已经过期 → 右上角仍显示头像/已登录 → 还能点进个人中心、地址管理
//   → 点「新增地址」时接口 401 → 只有一条红字报错，人还"登录着"。
//
// 现在把"失效"这件事收敛到一处，并且给两条入口：
//   1) **兜底**：app/plugins/api-session-guard.client.ts 挂在全局 $fetch 上，
//      任何 /api/* 返回 401 都会自动走到这里 —— 页面/组件不需要记得处理；
//   2) **显式**：地址簿这类"页面自己知道语义"的地方可以直接调用（见 useAddressBook）。
//
// ## 行为（顺序很重要，前两步必须是同步的）
//
//   1. 清前端登录态：userInfo + localStorage 缓存（否则刷新后又会"复活"），
//      同时把购物车角标/勾选一并清掉（角标是登录态最显眼的指示器）；
//   2. 让服务端把那张废票删掉：POST /api/auth/logout（失败也不影响 —— 见下）；
//   3. 弹一条「登录已过期，请重新登录」；
//   4. **只有当前页面确实需要登录**时才跳登录页，并带上 redirect 回跳。
//
// 第 4 步的"只有"很关键：首页/商品页这类公开页面上，Header 的购物车角标请求
// 也会 401。那种情况下把用户从正在逛的商品页踹到登录页是不可接受的，
// 正确做法是"静默掉线"（头像消失、角标归零）并在原位继续浏览。
//
// ## 为什么第 2 步失败也没关系
//
// /api/auth/logout 在 token 已经失效时后端会 401，但 BFF 的 handler 无论如何都会
// clearAccessTokenCookie（见 server/api/auth/logout.post.ts）。所以这个请求
// 既作废令牌又清 cookie，失败时也只是少了一次"服务端作废"，本地已经不掉队。

import { CART_COUNT_STATE_KEY, CART_UNAUTHORIZED_STATE_KEY } from '~~/composables/useCart'
import { useAuth } from '~~/composables/useAuth'
import type { StoreCart, StoreCartCount } from '~~/types/shop-cart'
import { emptyCart } from '~~/types/shop-cart'
import { STORE_DEFAULT_LANG, STORE_SUPPORTED_LANGS } from '~~/types/store-locale'

/**
 * 「正在处理登录失效」的并发闸门。
 *
 * 同一屏上往往有好几个请求同时 401（个人中心会一次拉资料、地址、订单、收藏），
 * 没有闸门就会有 N 条一模一样的 toast、N 次跳转、N 次 logout。
 */
export const SESSION_EXPIRY_HANDLING_KEY = 'session-expiry-handling'

export interface SessionExpiryOptions {
  /** 触发来源，只用于日志排查（例如 'address-list'），不影响行为 */
  source?: string
  /** 强制跳登录页（默认只在受保护页面才跳） */
  forceRedirect?: boolean
}

/** 走一次 /api/auth/logout 让服务端作废令牌并清 cookie；失败无所谓，本地已经清过了 */
async function revokeServerSession() {
  try {
    await $fetch('/api/auth/logout', { method: 'POST' })
  } catch {
    // token 已失效时后端会 401，但 BFF 仍会 clearAccessTokenCookie ——
    // 这里不需要任何补救动作，更不能把错误抛给用户。
  }
}

export const useSessionExpiry = () => {
  const nuxtApp = useNuxtApp()
  const { userInfo, clearSession } = useAuth()
  const handling = useState<boolean>(SESSION_EXPIRY_HANDLING_KEY, () => false)

  // 购物车状态与 SSR 播种（app/middleware/auth-seed.global.ts）共用同一批 useState key，
  // 这里直接按 key 操作、不调用 useCart()：useCart 里有 useI18n()，而本 composable
  // 还要被 Nuxt 插件（没有组件实例）调用，不能依赖组件上下文。
  const cartCount = useState<StoreCartCount>(CART_COUNT_STATE_KEY, () => ({ totalItems: 0, totalQuantity: 0 }))
  const cartUnauthorized = useState<boolean>(CART_UNAUTHORIZED_STATE_KEY, () => false)
  const cart = useState<StoreCart>('store-cart', () => emptyCart())
  const cartSelected = useState<string[]>('store-cart-selected', () => [])

  /**
   * 当前路由是否「必须登录」。
   *
   * 判据就是页面自己声明的 definePageMeta({ middleware: 'auth' }) —— Nuxt 会把它
   * 写进 route.meta.middleware。用页面声明而不是在这里维护一份路径清单，
   * 是为了以后新增受保护页时不需要回来改这个文件（漏改就会把用户丢在空白页）。
   */
  const routeRequiresAuth = (meta: Record<string, any> | undefined): boolean => {
    const middleware = meta?.middleware
    if (!middleware) return false
    if (typeof middleware === 'string') return middleware === 'auth'
    return Array.isArray(middleware) && middleware.includes('auth')
  }

  /** 当前语言前缀（i18n 是 prefix 策略，直接用路径第一段；取不到再用 i18n 的当前 locale） */
  const currentLang = (path: string): string => {
    const firstSegment = path.split('?')[0]?.split('/').filter(Boolean)[0] || ''
    if (STORE_SUPPORTED_LANGS.includes(firstSegment)) return firstSegment
    const i18n = (nuxtApp as any).$i18n
    const locale = i18n?.locale
    const code = typeof locale === 'string' ? locale : locale?.value
    return STORE_SUPPORTED_LANGS.includes(code) ? code : STORE_DEFAULT_LANG
  }

  /** 文案：优先走 i18n（$i18n 在应用初始化后一定就绪），取不到就退回中文兜底 */
  const translate = (key: string, fallback: string): string => {
    try {
      const value = (nuxtApp as any).$i18n?.t?.(key)
      return typeof value === 'string' && value && value !== key ? value : fallback
    } catch {
      return fallback
    }
  }

  /** 只在客户端弹 toast：服务端渲染阶段弹了也送不到用户面前 */
  const notify = (options: SessionExpiryOptions) => {
    const title = translate('auth.sessionExpired', '登录已过期')
    const description = translate('auth.sessionExpiredDesc', '你的登录状态已失效，请重新登录')
    try {
      // runWithContext：handler 是从 $fetch 的 catch 里回调的，那时没有组件实例，
      // 直接调 useToast() 拿不到 Nuxt 上下文。runWithContext 会把当前 app 补回去，
      // 于是 useState（Toast 队列）与 inject（toast 上限配置）都能正常工作。
      nuxtApp.runWithContext(() => {
        useToast().add({
          title,
          description,
          color: 'warning',
          icon: 'lucide:log-in',
          duration: 6000,
        })
      })
    } catch (error) {
      // 兜底里的兜底：toast 弹不出来绝不能让"清态 + 跳转"这两件正事失败
      console.warn(`[session-expiry] 提示弹出失败（${options.source || 'unknown'}）:`, error)
    }
  }

  /** 跳登录页（带 redirect 回跳；已在登录/注册页时不重复跳，避免自指循环） */
  const redirectToLogin = (source?: string) => {
    try {
      const router = (nuxtApp as any).$router
      const current = router?.currentRoute?.value
      const fullPath: string = current?.fullPath || '/'
      if (current?.path?.endsWith('/login') || current?.path?.endsWith('/register')) return

      const lang = currentLang(fullPath)
      const target = `/${lang}/login?redirect=${encodeURIComponent(fullPath)}`
      // replace：被踢下来的那一页已经作废，留在历史里会让"后退"反复撞回同一个跳转
      void router.replace(target)
    } catch (error) {
      console.warn(`[session-expiry] 跳转登录页失败（${source || 'unknown'}）:`, error)
    }
  }

  /**
   * 处理一次「登录态已失效」。
   *
   * @returns 是否真的处理了（false = 本来就没登录 / 已在处理中 / 非鉴权错误）
   */
  const handleSessionExpired = (options: SessionExpiryOptions = {}): boolean => {
    // 服务端渲染阶段什么都不做：那一步的登录判定由 app/middleware/auth.ts 负责，
    // 而 toast / 客户端路由跳转在 SSR 里没有意义（也不会传到浏览器）。
    if (!import.meta.client) return false

    // 本来就没登录：这不是"过期"，是"没登录"。匿名用户点加购/收藏时的 401 属于这一类，
    // 各页面自己的"请先登录"引导才是正确反馈，这里不能弹"登录已过期"。
    if (!userInfo.value) return false

    // 已经在处理（同屏多个 401）：不重复提示、不重复跳转
    if (handling.value) return false
    handling.value = true

    // ① 同步清态：必须先于任何 await，否则调用方的 catch 还会看到"已登录"的旧状态
    clearSession()
    cartCount.value = { totalItems: 0, totalQuantity: 0 }
    cartUnauthorized.value = true
    cart.value = emptyCart()
    cartSelected.value = []

    // ② 服务端作废令牌（不 await：跳转/提示不该被一次网络往返拖住）
    void revokeServerSession()

    // ③ 提示
    notify(options)

    // ④ 受保护页才跳登录（公开页静默掉线，别把正在逛商品的人踹走）
    const meta = (nuxtApp as any).$router?.currentRoute?.value?.meta
    if (options.forceRedirect || routeRequiresAuth(meta)) {
      redirectToLogin(options.source)
    } else if (import.meta.dev) {
      console.log(`[session-expiry] 公开页 401，仅清理本地登录态（${options.source || 'unknown'}）`)
    }

    // 闸门保持"关"一小会儿：一批并发 401 只算一次。
    // 用 setTimeout 而不是立刻复位，是因为同一批请求的错误会在同一 tick 附近陆续到达；
    // 复位太早会出现"清完态又被下一条 401 当成新事件"的重复 toast。
    setTimeout(() => { handling.value = false }, 2000)

    return true
  }

  /**
   * 只提示、不做别的（清态与跳转由调用方负责）。
   *
   * 目前唯一的调用方是路由守卫 `app/middleware/auth.ts`：它在"上次结论过期 →
   * 重新问服务端 → 服务端说不认识这张 cookie"这条路径上把用户拦到登录页，
   * 这时用户看到的是"突然让我登录"，得有一句话解释原因。
   */
  const notifySessionExpired = (options: SessionExpiryOptions = {}) => {
    if (!import.meta.client) return
    notify(options)
  }

  return { handleSessionExpired, notifySessionExpired }
}
