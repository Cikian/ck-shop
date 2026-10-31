// app/plugins/ga4.ts
//
// GA4 普通接入（契约 §1.2 / §10）：加载 gtag.js + 上报 page_view。
//
// 【为什么必须手动上报 page_view】
//   Nuxt 是 SSR + SPA 混合：首屏由服务端渲染，之后站内跳转是**客户端路由切换**，
//   浏览器不会整页刷新。GA4 的自动 page_view 只在"真正整页加载"时触发，
//   因此站内所有跳转都会被漏计（浏览量会严重偏低）。
//   所以这里关闭自动上报（send_page_view:false），改为在路由完成时补发 page_view。
//
// 【双保险 + 去重】
//   同时挂 `page:finish`（保证首屏 hydrate 后一定发一次）和 `router.afterEach`
//   （保证站内跳转发）。两个钩子可能对同一次导航都触发，用「同路径 1 秒内只发一次」去重，
//   既不会漏计，也不会重复计数。
//
// 【约定】
//   - **不接 Consent Mode**，也**不因 Cookie 同意状态控制加载**（与 Cookie 横幅完全解耦）；
//   - 测量 ID 从 runtimeConfig.public.ga4Id 注入（NUXT_PUBLIC_GA4_ID，兼容 NUXT_GA4_ID）；
//   - 未配置测量 ID 时**静默跳过**：不加载脚本、不报错、不影响站点。

export default defineNuxtPlugin((nuxtApp) => {
  if (import.meta.server) return

  const config = useRuntimeConfig()
  const measurementId = String(config.public.ga4Id || '').trim()
  if (!measurementId) return

  type Gtag = (...args: unknown[]) => void
  const w = window as unknown as { dataLayer?: unknown[]; gtag?: Gtag }

  w.dataLayer = w.dataLayer || []
  const gtag: Gtag = (...args) => {
    ;(w.dataLayer as unknown[]).push(args)
  }
  w.gtag = gtag

  gtag('js', new Date())
  // 关闭自动 page_view：改由下方路由钩子统一上报，避免首屏重复计数
  gtag('config', measurementId, { send_page_view: false })

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(measurementId)}`
  document.head.appendChild(script)

  // ---- page_view 上报（首屏 + 每次站内跳转，同一路径 1 秒内去重） ----
  let lastPath = ''
  let lastAt = 0

  const sendPageView = () => {
    const path = window.location.pathname + window.location.search
    const now = Date.now()
    if (path === lastPath && now - lastAt < 1000) return
    lastPath = path
    lastAt = now
    gtag('event', 'page_view', {
      page_location: window.location.href,
      page_path: path,
      page_title: document.title,
    })
  }

  nuxtApp.hook('page:finish', () => {
    void nextTick(sendPageView)
  })

  nuxtApp.$router.afterEach(() => {
    void nextTick(sendPageView)
  })
})
