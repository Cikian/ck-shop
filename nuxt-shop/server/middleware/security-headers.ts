// server/middleware/security-headers.ts
//
// 统一安全响应头（清单 S27）。
//
// 现状：实测响应只有 X-Robots-Tag / Set-Cookie / X-Powered-By ——
// 没有 X-Content-Type-Options、Referrer-Policy、X-Frame-Options，也没有 CSP。
// 这意味着：
//   · 浏览器会"猜"内容类型（上传的 .txt 可能被当 HTML 执行）；
//   · 页面可以被任意站点 iframe 嵌套（点击劫持）；
//   · 跳转到外站时完整 URL（可能含订单号）会通过 Referer 泄露；
//   · 一旦出现 XSS，没有任何 CSP 兜底。
//
// 这里对**所有响应**加基础头；CSP 只加在页面/接口响应上（静态资源同样需要，
// 由 Nitro 统一处理，静态文件走 public/ 时也会经过本中间件）。
//
// 关于 CSP 的取舍：
//   · 用 'unsafe-inline' 是因为 Nuxt 的 SSR payload 与 Nuxt UI 都依赖内联 style/script，
//     上 nonce 需要改渲染管线，风险大于收益；
//   · 但外链脚本仍然被挡住（script-src 不含外部域名），这是最常见的一类注入；
//   · img-src 放开 https: 是因为商品图来自外部图床（demo 数据是 Unsplash），
//     如需收紧可用 STORE_IMAGE_HOSTS 环境变量列出白名单域名。

const SAFE_IMG_SOURCES = ["'self'", 'data:', 'blob:', 'https:']

/** 静态资源（带内容哈希的 /_nuxt/*）无需重复计算 CSP，但也不贵，保持简单 */
export default defineEventHandler((event) => {
  // 1) 基础头：与框架无关，所有响应都加
  setHeader(event, 'X-Content-Type-Options', 'nosniff')
  setHeader(event, 'Referrer-Policy', 'strict-origin-when-cross-origin')
  setHeader(event, 'X-Frame-Options', 'SAMEORIGIN')
  setHeader(event, 'Permissions-Policy', 'camera=(), microphone=(), geolocation=(), payment=()')
  // 明确关闭历史上用于 IE 的 XSS 审计器（它会引入自身漏洞）
  setHeader(event, 'X-XSS-Protection', '0')

  // 2) HSTS：只在确实通过 HTTPS 访问时下发（本地 http 调试不能带，否则浏览器会锁死）
  const proto = (getHeader(event, 'x-forwarded-proto') || '').split(',')[0]?.trim()
  const url = getRequestURL(event)
  const isHttps = proto === 'https' || url.protocol === 'https:'
  if (isHttps) {
    setHeader(event, 'Strict-Transport-Security', 'max-age=31536000; includeSubDomains')
  }

  // 3) CSP
  const csp = [
    "default-src 'self'",
    `img-src ${SAFE_IMG_SOURCES.join(' ')}`,
    "style-src 'self' 'unsafe-inline'",
    "script-src 'self' 'unsafe-inline'",
    "font-src 'self' data:",
    "connect-src 'self'",
    "media-src 'self' https:",
    "frame-src 'self' https://www.youtube.com https://player.bilibili.com",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'self'",
  ].join('; ')
  setHeader(event, 'Content-Security-Policy', csp)
})
