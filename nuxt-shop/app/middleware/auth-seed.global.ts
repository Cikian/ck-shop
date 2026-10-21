// app/middleware/auth-seed.global.ts
//
// 服务端登录态播种（全局中间件，公开页与受保护页都会经过）。
//
// 解决什么问题：
//   token 在 httpOnly cookie 里，浏览器 JS 读不到；localStorage 里只有一份用于回显的
//   user-info 缓存。改造前只有**受保护页**的 auth 中间件会在 SSR 阶段问一次服务端，
//   于是首页/分类/商品这些公开页的 SSR 输出必然是「登录/注册」+ 空购物车角标，
//   等客户端插件 restoreUser()/refreshCount() 跑完才翻成头像/数字 —— 每次刷新闪一下。
//
// 现在：
//   · SSR 每个请求只查一次 /api/auth/session（`authChecked` 保证不会重复查）；
//   · 查到的 userInfo 直接种进 useAuth() 的 useState（复用同名 key，
//     客户端 hydration 用的是同一份数据，不会出现"服务端已登录、首帧却渲染未登录"）；
//   · 已登录再补一次 /api/cart/count，把角标也播种进 SSR，避免 0 → N 的跳动。
//   · 只在 import.meta.server 执行；客户端恢复/重新校验仍由
//     app/plugins/init-auth.client.ts 负责（它才是"token 是否过期"的最终判定）。
//
// 安全：这里只写**展示用**的 userInfo（后端 profile 的返回），
// token 依旧只在 httpOnly cookie 里，不会进 payload、也不会出现在 HTML 中。
//
// 为什么用 useRequestFetch()：服务端 $fetch 是"服务端 → 自己的 Nitro"，
// 不会自动转发浏览器的 cookie；useRequestFetch() 会带上当前请求的 cookie/头。

import type { AuthSession } from '#server/api/auth/session.get'
import { CART_COUNT_STATE_KEY, CART_UNAUTHORIZED_STATE_KEY } from '~~/composables/useCart'
import { useAuth } from '~~/composables/useAuth'
import type { StoreCartCount } from '~~/types/shop-cart'
import { normalizeCartCount } from '~~/types/shop-cart'

export default defineNuxtRouteMiddleware(async () => {
  // 客户端不做任何事：那里的登录态由 init-auth.client.ts + 各页面自己的刷新负责
  if (!import.meta.server) return

  const { authChecked, userInfo } = useAuth()

  // 已经播种过（同一次 SSR 里不会发生第二次，但保留幂等，
  // 也方便将来在别的中间件里主动调用）
  if (authChecked.value) return

  const requestFetch = useRequestFetch()
  const count = useState<StoreCartCount>(CART_COUNT_STATE_KEY, () => ({ totalItems: 0, totalQuantity: 0 }))
  const unauthorized = useState<boolean>(CART_UNAUTHORIZED_STATE_KEY, () => false)

  try {
    // /api/auth/session 契约上永远返回 200，用 body.authenticated 表达结果
    const session = await requestFetch<AuthSession>('/api/auth/session')
    const sessionUser = session?.authenticated ? session.userInfo : null

    if (sessionUser) {
      // 只补空值：若同一请求里已经有别处种过（理论上不会），不覆盖。
      // 类型桥接说明：/api/auth/session 的 userInfo 声明为 Record<string, any>
      // （profile 是扁平对象），展示层 UserInfo 的 realname/username 由后端保证一定存在，
      // 这里只是让 TS 接受同一个运行时对象，不做任何字段变换。
      if (!userInfo.value) {
        userInfo.value = sessionUser as unknown as NonNullable<typeof userInfo.value>
      }
      authChecked.value = true

      // 角标失败不影响页面（与 useCart.refreshCount 的"失败静默"口径一致）
      try {
        count.value = normalizeCartCount(await requestFetch('/api/cart/count'))
        unauthorized.value = false
      } catch {
        // 保持默认 0 角标
      }
      return
    }

    // 明确未登录：也标记 authChecked，避免受保护页的 auth.ts 再问一次服务端
    authChecked.value = true
    unauthorized.value = true
  } catch {
    // 服务端这次拿不到会话（后端不可用/网络异常）：
    // **不**标记 authChecked，把判断权交回 auth.ts（它对受保护页 fail-closed，
    // 公开页则维持"未登录"的默认状态）。绝不因为一次抖动就把用户种成已登录。
  }
})
