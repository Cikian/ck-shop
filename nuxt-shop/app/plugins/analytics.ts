// app/plugins/analytics.ts
//
// 用户行为分析（Phase 1）· 前端轻量埋点 SDK。
//
// 只补「服务端看不到」的行为（§5.2）：
//   1) page_leave：visibilitychange / pagehide + navigator.sendBeacon 上报，带 duration_ms；
//   2) SPA 客户端路由跳转的 page_view / product_view（SSR 全页加载已由 server/middleware/analytics.ts 覆盖）；
//   3) 事件公共属性里的 screen。
//
// 硬约束：requestIdleCallback 延迟初始化、失败静默、无轮询定时器（无 setInterval）、
// 批量合并、只在离开时打一次点。session/visitor 是 httpOnly cookie，前端读不到、
// 也不该碰，由 BFF 的 /api/track/events 端点补全。

import type { Router } from 'vue-router'

interface ClientTrackEvent {
  event_name: 'page_view' | 'product_view' | 'page_leave'
  event_time: number
  screen?: string
  page_url?: string
  page_path?: string
  product_id?: string | null
  referrer?: string | null
  duration_ms?: number
}

/** 商品详情路径识别（与服务端同一口径：商品 id 即 slug） */
function detectProductId(path: string): string | null {
  const segments = path.split('/').filter(Boolean)
  const index = segments.indexOf('products')
  if (index < 0 || index > 1) return null
  if (segments.length !== index + 2) return null
  const id = segments[index + 1]
  return id && id.length <= 128 ? id : null
}

export default defineNuxtPlugin((nuxtApp) => {
  if (import.meta.server) return

  // requestIdleCallback 延迟初始化；不支持时退回 setTimeout(0)
  const scheduleIdle = (cb: () => void) => {
    if (typeof window.requestIdleCallback === 'function') {
      window.requestIdleCallback(() => cb(), { timeout: 2000 })
    } else {
      setTimeout(cb, 0)
    }
  }

  scheduleIdle(() => {
    try {
      initAnalytics(nuxtApp.$router as Router)
    } catch {
      // 失败静默：埋点绝不干扰页面
    }
  })
})

function initAnalytics(router: Router): void {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') return

  const screen = `${window.screen.width}x${window.screen.height}`

  let entryTime = Date.now()
  let left = false

  let pending: ClientTrackEvent[] = []
  let flushTimer: ReturnType<typeof setTimeout> | null = null

  const deliver = (events: ClientTrackEvent[]): void => {
    if (!events.length) return
    const payload = JSON.stringify(events)
    let sent = false
    try {
      if (typeof navigator.sendBeacon === 'function') {
        sent = navigator.sendBeacon('/api/track/events', new Blob([payload], { type: 'application/json' }))
      }
    } catch {
      // ignore
    }
    if (!sent) {
      try {
        fetch('/api/track/events', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: payload,
          keepalive: true,
        }).catch(() => {})
      } catch {
        // ignore
      }
    }
  }

  const queueEvent = (event: ClientTrackEvent, immediate: boolean): void => {
    pending.push(event)
    if (immediate) {
      if (flushTimer !== null) {
        clearTimeout(flushTimer)
        flushTimer = null
      }
      deliver(pending.splice(0))
      return
    }
    // 批量合并：同一 tick 内的多次导航合并成一次上报
    if (flushTimer === null) {
      flushTimer = setTimeout(() => {
        flushTimer = null
        deliver(pending.splice(0))
      }, 0)
    }
  }

  const buildPageEvent = (path: string, fullUrl: string, referrer: string | null): ClientTrackEvent => {
    const productId = detectProductId(path)
    return {
      event_name: productId ? 'product_view' : 'page_view',
      event_time: Date.now(),
      screen,
      page_url: fullUrl,
      page_path: path,
      product_id: productId,
      referrer,
    }
  }

  // SPA 客户端路由跳转的 page_view / product_view。
  // 全页加载（SSR）已由服务端中间件覆盖，这里只补客户端跳转：
  // 初次水合导航（from.matched 为空）跳过，避免与 SSR 重复计数。
  let lastPath = ''
  router.afterEach((to, from) => {
    if (!from || from.matched.length === 0) {
      lastPath = to.fullPath
      return
    }
    if (to.fullPath === lastPath) return
    lastPath = to.fullPath

    const fullUrl = window.location.origin + to.fullPath
    const referrer = from.fullPath ? window.location.origin + from.fullPath : null
    entryTime = Date.now()
    queueEvent(buildPageEvent(to.path, fullUrl, referrer), false)
  })

  const leave = (): void => {
    if (left) return
    left = true
    queueEvent(
      {
        event_name: 'page_leave',
        event_time: Date.now(),
        screen,
        page_url: window.location.href,
        page_path: window.location.pathname,
        product_id: detectProductId(window.location.pathname),
        duration_ms: Math.max(0, Date.now() - entryTime),
      },
      true,
    )
  }

  const onVisibilityChange = (): void => {
    if (document.visibilityState === 'hidden') {
      leave()
    } else if (document.visibilityState === 'visible') {
      // 回到前台：重新计时下一次停留，但不打点
      left = false
      entryTime = Date.now()
    }
  }

  document.addEventListener('visibilitychange', onVisibilityChange, { passive: true })
  window.addEventListener('pagehide', leave)
}
