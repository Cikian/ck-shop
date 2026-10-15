// server/middleware/csrf.ts
//
// 同源校验（CSRF 第二道防线）。
//
// 现状：登录态放在 httpOnly cookie 里，浏览器发**跨站**请求时会自动带上它 ——
// 这正是 CSRF 的前提。应用的所有写接口（下单、退款、删地址、改密码……）都
// 依赖 cookie 鉴权，而目前唯一的防护是 cookie 的 SameSite=Lax。
//
// 为什么不够：SameSite 是**一个开关**。将来为了嵌 iframe 结账、跨站单点登录
// 等原因把它改成 'none'，所有写接口会立刻变成"任意网站都能替你提交"的原语，
// 而代码里没有任何地方会报错提醒。
//
// 所以这里补一道与 SameSite 无关的检查：对携带登录 cookie 的非 GET 请求，
// 如果浏览器给了 Origin/Referer，就必须是同源的。
//
// 为什么"没带就放行"：浏览器（含 XHR/fetch/form POST）对跨站写请求一定会带
// Origin，页面内脚本无法伪造或删除它；攻击者用 curl 也无所谓 —— 他没有受害者
// 的 cookie，构不成 CSRF。因此缺头时放行不会削弱这道防线，却避免了误伤
// 移动端内嵌 WebView、以及未来可能的非浏览器调用方。

import { ACCESS_TOKEN_COOKIE } from '../utils/authCookie'

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/** 允许的同源集合：显式配置的 publicOrigin 优先，其次本次请求自身的 origin */
function allowedOrigins(event: any): string[] {
  const config = useRuntimeConfig(event)
  const list: string[] = []
  if (config.publicOrigin) {
    list.push(String(config.publicOrigin).replace(/\/+$/, ''))
  }
  list.push(getRequestURL(event).origin)
  return list
}

function originOf(url: string | undefined): string | null {
  if (!url) return null
  try {
    return new URL(url).origin
  } catch {
    return null
  }
}

export default defineEventHandler((event) => {
  const method = (event.method || getMethod(event) || 'GET').toUpperCase()
  if (SAFE_METHODS.has(method)) return

  // 只保护 BFF 的业务接口；静态资源、页面渲染不在此列
  const path = event.path || ''
  if (!path.startsWith('/api/')) return

  // 没有登录 cookie 的请求构不成 CSRF（也就没有"被盗用身份"一说）
  if (!getCookie(event, ACCESS_TOKEN_COOKIE)) return

  const allowed = allowedOrigins(event)
  const origin = getHeader(event, 'origin')
  const referer = getHeader(event, 'referer')

  const actual = origin || originOf(referer)
  if (!actual) return

  if (!allowed.includes(actual.replace(/\/+$/, ''))) {
    throw createError({
      statusCode: 403,
      statusMessage: '请求来源校验失败，请通过本站页面操作',
    })
  }
})
