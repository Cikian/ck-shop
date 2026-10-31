// server/utils/analytics.ts
//
// 用户行为分析（Phase 1）· 服务端公共逻辑。
//
// 职责边界（以 docs/用户行为分析-接口契约.md 为唯一权威）：
//   - 事件结构：snake_case 字段名，对齐 §3.2 表列 / §4.2 公共属性；
//   - UA 解析：轻量正则得出 device_type / os / browser，无第三方依赖；
//   - 语言 / UTM / 商品路径识别；
//   - session_id / visitor_id 的 httpOnly cookie 生成与刷新（30 分钟滑动会话）；
//   - 内存队列 → 批量异步 flush（500 条或 2s）→ Java /shop/analytics/track/events。
//
// 明确**不做**（避免与 Java 重复、也避免每次请求重复解析）：
//   - country / province / city：由 Java 用 GeoLite2 解析 ip 得到，本文件只传原始 ip；
//   - org_code / store_id：由 Java 经 X-Store-Code（ShopOrgScopeResolver）解析，
//     本文件只把店铺编码放进 flush 的 X-Store-Code 头（复用 apiClient 既有约定）。
//
// 性能/无感硬约束：只内存入队立即返回；flush 失败/超时静默丢弃，绝不重试风暴、绝不阻塞响应。

import type { H3Event } from 'h3'
import CryptoJS from 'crypto-js'
import { getClientIp } from './clientIp'
import { STORE_SUPPORTED_LANGS, resolveStoreLang } from '~~/types/store-locale'

// ---------------------------------------------------------------------------
// 类型
// ---------------------------------------------------------------------------

/** Phase 1 受控事件清单（§4.1；add_to_cart / order_* 从业务表复用，不在此列） */
export type AnalyticsEventName = 'page_view' | 'product_view' | 'page_leave'

/**
 * 一条行为事件（BFF → Java 的 JSON 字段，snake_case）。
 *
 * 注意：不包含 country_code / country_name / province_name / city_name / province_code，
 * 这些由 Java 用 GeoLite2 解析 ip 后回填（§4.2 注释）。也不包含 user_id：登录态回填由
 * Java 侧完成，BFF 不在中间件里解析 JWT。
 */
export interface AnalyticsTrackEvent {
  event_name: AnalyticsEventName
  /** 事件发生时间（epoch 毫秒） */
  event_time: number
  session_id: string
  visitor_id: string
  /** 商品类事件（product_view）必带；其余为空 */
  product_id?: string | null
  page_url: string
  page_path: string
  referrer?: string | null
  utm_source?: string | null
  utm_medium?: string | null
  utm_campaign?: string | null
  device_type?: string | null
  os?: string | null
  browser?: string | null
  /** 屏幕分辨率（仅客户端事件才有） */
  screen?: string | null
  lang: string
  /** 原始客户端 IP（本期不脱敏，Java 解析地域） */
  ip?: string | null
  /** 停留时长（仅 page_leave） */
  duration_ms?: number | null
  props?: Record<string, unknown> | null
}

// ---------------------------------------------------------------------------
// 常量
// ---------------------------------------------------------------------------

/** 会话 cookie 名（§3.2 的 session_id；与 access-token / i18n_redirected 不冲突） */
const SESSION_COOKIE = 'ck_sid'
/** 匿名访客 cookie 名（§3.2 的 visitor_id） */
const VISITOR_COOKIE = 'ck_vid'

/** 会话滑动窗口：相邻事件间隔 ≤ 30 分钟归为同一会话（§7） */
const SESSION_MAX_AGE_SECONDS = 30 * 60
/** 访客 cookie 有效期：2 年（无「访客表」，visitor_id 即长期去重标识） */
const VISITOR_MAX_AGE_SECONDS = 60 * 60 * 24 * 365 * 2

/** 批量 flush 条数上限（§5.1） */
const FLUSH_BATCH_SIZE = 500
/** 距上次 flush 的最长间隔（§5.1） */
const FLUSH_INTERVAL_MS = 2000
/** 转发到 Java 的超时（毫秒）；超时即静默丢弃，不重试 */
const FLUSH_TIMEOUT_MS = 3000

/** Java 采集接收接口（§5.3；相对 apiBaseUrl 的路径） */
const TRACK_ENDPOINT = '/shop/analytics/track/events'

/** 受控事件名白名单（前端事件只接受这些） */
const CLIENT_EVENT_NAMES: ReadonlySet<string> = new Set(['page_view', 'product_view', 'page_leave'])

// ---------------------------------------------------------------------------
// 随机标识 / UA 解析
// ---------------------------------------------------------------------------

function randomId(): string {
  const cryptoObj = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto
  if (cryptoObj && typeof cryptoObj.randomUUID === 'function') {
    try {
      return cryptoObj.randomUUID()
    } catch {
      // 退回自拼随机串
    }
  }
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}${Math.random().toString(36).slice(2, 12)}`
}

/** 校验 cookie 里的标识，避免把伪造/损坏的值原样写回或上报 */
function isSafeId(value: string | undefined): value is string {
  return typeof value === 'string' && /^[A-Za-z0-9._-]{8,128}$/.test(value)
}

export interface ParsedUserAgent {
  deviceType: 'desktop' | 'mobile' | 'tablet' | 'unknown'
  os: string | null
  browser: string | null
}

/**
 * 轻量 UA 解析（§3.2 的 device_type / os / browser）。
 *
 * 刻意只用正则：不引 ua-parser-js 之类的依赖（多一个依赖多一份体积/风险），
 * 覆盖电商场景的主流 UA 足够。判断顺序重要：Edge/Opera/Samsung 都带 "chrome"，
 * iOS Chrome 是 "crios"、iOS Safari 不含 "chrome"。
 */
export function parseUserAgent(ua: string | null | undefined): ParsedUserAgent {
  const raw = (ua || '').toLowerCase()
  if (!raw) return { deviceType: 'unknown', os: null, browser: null }

  let deviceType: ParsedUserAgent['deviceType'] = 'desktop'
  if (/ipad|playbook|silk|kindle/.test(raw) || (/android/.test(raw) && !/mobile/.test(raw))) {
    deviceType = 'tablet'
  } else if (/mobi|iphone|ipod|android|blackberry|windows phone|iemobile|opera mini/.test(raw)) {
    deviceType = 'mobile'
  }

  let os: string | null = null
  if (/windows nt/.test(raw)) os = 'Windows'
  else if (/android/.test(raw)) os = 'Android'
  else if (/iphone|ipad|ipod/.test(raw)) os = 'iOS'
  else if (/mac os x|macintosh/.test(raw)) os = 'macOS'
  else if (/cros/.test(raw)) os = 'ChromeOS'
  else if (/linux/.test(raw)) os = 'Linux'

  let browser: string | null = null
  if (/edg(e|a|ios)?\//.test(raw)) browser = 'Edge'
  else if (/opr\/|opera mini/.test(raw)) browser = 'Opera'
  else if (/samsungbrowser\//.test(raw)) browser = 'Samsung Internet'
  else if (/chrome|crios/.test(raw)) browser = 'Chrome'
  else if (/firefox|fxios/.test(raw)) browser = 'Firefox'
  else if (/safari/.test(raw)) browser = 'Safari'

  return { deviceType, os, browser }
}

// ---------------------------------------------------------------------------
// 真实客户端 IP（含兜底链）
// ---------------------------------------------------------------------------

/**
 * 规范化 getRequestIP 返回的地址：去引号 / 逗号链 / 中括号，拒绝 'unknown'。
 * 回环 / 内网地址（127.0.0.1、::1、::ffff:127.0.0.1）原样保留，本地开发也要能看到 IP。
 */
function normalizeIpValue(raw: string | undefined): string | null {
  if (!raw) return null
  let ip = raw.trim()
  if (ip.startsWith('"') && ip.endsWith('"') && ip.length > 2) {
    ip = ip.slice(1, -1).trim()
  }
  // XFF 可能是逗号分隔链，只取首个（h3 已 shift 过，这里防御性兜底）
  const first = ip.split(',')[0]?.trim() || ''
  if (!first || first.toLowerCase() === 'unknown') return null
  if (first.startsWith('[') && first.endsWith(']')) {
    return first.slice(1, -1)
  }
  return first.length <= 128 ? first : null
}

/**
 * 解析真实客户端 IP。
 *
 * getClientIp() 的信任模型在「取不到 socket.remoteAddress / clientAddress」时返回
 * 'unknown'（本地 / 隧道访问常见），此时 IP 不能直接置 null —— 否则今日IP=0、地域全空。
 * 这里补两级兜底：
 *   1) getRequestIP(event, { xForwardedFor: true })：采信 XFF 首跳；
 *   2) getRequestIP(event)：socket 对端原始地址。
 * 回环 / 内网地址照常保留；全部取不到才返回 null。
 */
function resolveClientIp(event: H3Event): string | null {
  const primary = getClientIp(event)
  if (primary && primary !== 'unknown') return primary

  const forwarded = normalizeIpValue(getRequestIP(event, { xForwardedFor: true }))
  if (forwarded) return forwarded

  return normalizeIpValue(getRequestIP(event))
}

// ---------------------------------------------------------------------------
// 语言 / UTM / 商品路径
// ---------------------------------------------------------------------------

/**
 * 解析本次请求的语言。
 *
 * 中间件在 locale.ts **之前**运行（文件名排序 analytics < locale），
 * 因此这里不能依赖 event.context.locale，自己按优先级解析：
 * URL 前缀（最可靠）> ?lang= > i18n_redirected cookie > 默认 en。
 */
function detectLang(event: H3Event, pathname: string): string {
  const first = pathname.split('/').filter(Boolean)[0]?.toLowerCase() || ''
  if (first && STORE_SUPPORTED_LANGS.includes(first)) return first

  const query = getQuery(event)
  const queryLang = typeof query.lang === 'string' ? query.lang : undefined
  const cookieLang = getCookie(event, 'i18n_redirected')
  return resolveStoreLang(queryLang ?? cookieLang)
}

/** 从 query 提取 UTM 归因（首跳落地页的渠道参数） */
function parseUtm(query: Record<string, unknown>): {
  utm_source: string | null
  utm_medium: string | null
  utm_campaign: string | null
} {
  const pick = (key: string): string | null => {
    const value = query[key]
    return typeof value === 'string' && value.trim() ? value.trim().slice(0, 128) : null
  }
  return {
    utm_source: pick('utm_source'),
    utm_medium: pick('utm_medium'),
    utm_campaign: pick('utm_campaign'),
  }
}

/** 从完整 URL 的 query 提取 UTM（客户端 SPA page_view 的 page_url 可能带 UTM 参数） */
function parseUtmFromUrl(rawUrl: string): {
  utm_source: string | null
  utm_medium: string | null
  utm_campaign: string | null
} {
  try {
    const entries = Object.fromEntries(new URL(rawUrl).searchParams.entries()) as Record<string, unknown>
    return parseUtm(entries)
  } catch {
    return { utm_source: null, utm_medium: null, utm_campaign: null }
  }
}

/**
 * 从页面路径识别商品详情（product_view）。
 *
 * 商品 id 即店铺端 slug（server/api/products/[slug].get.ts 的契约），
 * 路径形如 /en/products/:id 或 /products/:id。允许最前面有一个语言段。
 */
function detectProductId(pagePath: string): string | null {
  const segments = pagePath.split('/').filter(Boolean)
  const index = segments.indexOf('products')
  if (index < 0 || index > 1) return null
  if (segments.length !== index + 2) return null
  const id = segments[index + 1]
  return id && id.length <= 128 ? id : null
}

// ---------------------------------------------------------------------------
// session / visitor cookie
// ---------------------------------------------------------------------------

/** 生成/刷新 session_id 与 visitor_id 的 httpOnly cookie（§3.2） */
function ensureIdentityCookies(event: H3Event): { sessionId: string; visitorId: string } {
  const config = useRuntimeConfig(event)
  const secure = Boolean(config.cookieSecure)
  const base = {
    httpOnly: true,
    secure,
    sameSite: 'lax' as const,
    path: '/',
  }

  let visitorId = getCookie(event, VISITOR_COOKIE)
  if (!isSafeId(visitorId)) {
    visitorId = randomId()
    setCookie(event, VISITOR_COOKIE, visitorId, { ...base, maxAge: VISITOR_MAX_AGE_SECONDS })
  }

  let sessionId = getCookie(event, SESSION_COOKIE)
  if (!isSafeId(sessionId)) {
    sessionId = randomId()
  }
  // 每次页面请求都刷新会话滑动窗口（30 分钟），保证「相邻事件间隔 ≤ 30 分钟」的会话口径
  setCookie(event, SESSION_COOKIE, sessionId, { ...base, maxAge: SESSION_MAX_AGE_SECONDS })

  return { sessionId, visitorId }
}

// ---------------------------------------------------------------------------
// 事件构建
// ---------------------------------------------------------------------------

/** 是否为页面导航（区别于 /api/*、静态资源、图片/脚本等） */
function isPageRequest(event: H3Event): boolean {
  const method = getMethod(event).toUpperCase()
  if (method !== 'GET' && method !== 'HEAD') return false

  const pathname = getRequestURL(event).pathname
  if (pathname.startsWith('/api/')) return false
  if (pathname.startsWith('/_nuxt/') || pathname.startsWith('/_ipx/') || pathname.startsWith('/_server/')) return false

  // 页面导航的 Accept 必然含 text/html；图片/脚本/字体请求用 image/*、*/* 等
  const accept = getRequestHeader(event, 'accept') || ''
  return accept.includes('text/html')
}

/**
 * 由服务端中间件构建一条 page_view / product_view 事件。
 * 非页面请求返回 null（中间件据此跳过）。
 */
export function collectPageView(event: H3Event): AnalyticsTrackEvent | null {
  if (!isPageRequest(event)) return null

  const { sessionId, visitorId } = ensureIdentityCookies(event)
  const url = getRequestURL(event)
  const pagePath = url.pathname
  const productId = detectProductId(pagePath)
  const parsed = parseUserAgent(getRequestHeader(event, 'user-agent'))
  const ip = resolveClientIp(event)

  return {
    event_name: productId ? 'product_view' : 'page_view',
    event_time: Date.now(),
    session_id: sessionId,
    visitor_id: visitorId,
    product_id: productId,
    page_url: url.toString(),
    page_path: pagePath,
    referrer: getRequestHeader(event, 'referer') || null,
    ...parseUtm(getQuery(event)),
    device_type: parsed.deviceType,
    os: parsed.os,
    browser: parsed.browser,
    lang: detectLang(event, pagePath),
    ip,
  }
}

function toEpochMs(value: unknown): number | null {
  const num = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(num) && num > 0 ? Math.trunc(num) : null
}

/**
 * 由 /api/track/events 端点补全「前端看不到的服务端字段」：
 * session_id / visitor_id（httpOnly cookie）、ip、UA（device_type/os/browser）、
 * 语言、referrer 兜底。前端只负责 event_name / screen / duration_ms / 行为字段。
 */
export function enrichClientEvent(event: H3Event, clientEvent: Record<string, unknown>): AnalyticsTrackEvent | null {
  const name = String(clientEvent.event_name || '')
  if (!CLIENT_EVENT_NAMES.has(name)) return null

  const { sessionId, visitorId } = ensureIdentityCookies(event)
  const url = getRequestURL(event)
  const clientPath = typeof clientEvent.page_path === 'string' ? clientEvent.page_path : url.pathname
  const productId = (typeof clientEvent.product_id === 'string' && clientEvent.product_id)
    ? clientEvent.product_id
    : detectProductId(clientPath)
  const parsed = parseUserAgent(getRequestHeader(event, 'user-agent'))
  const ip = resolveClientIp(event)

  const hasExplicitUtm = Boolean(clientEvent.utm_source || clientEvent.utm_medium || clientEvent.utm_campaign)
  const utm = hasExplicitUtm
    ? parseUtm({
        utm_source: clientEvent.utm_source,
        utm_medium: clientEvent.utm_medium,
        utm_campaign: clientEvent.utm_campaign,
      })
    : parseUtmFromUrl(typeof clientEvent.page_url === 'string' ? clientEvent.page_url : '')

  const screen = typeof clientEvent.screen === 'string' && clientEvent.screen.trim()
    ? clientEvent.screen.trim().slice(0, 32)
    : null
  const rawDuration = clientEvent.duration_ms
  const durationMs = name === 'page_leave'
    && typeof rawDuration === 'number' && Number.isFinite(rawDuration) && rawDuration >= 0
    ? Math.trunc(rawDuration)
    : null

  return {
    event_name: name as AnalyticsEventName,
    event_time: toEpochMs(clientEvent.event_time) ?? Date.now(),
    session_id: sessionId,
    visitor_id: visitorId,
    product_id: productId,
    page_url: typeof clientEvent.page_url === 'string' ? clientEvent.page_url.slice(0, 512) : url.toString(),
    page_path: clientPath.slice(0, 256),
    referrer: typeof clientEvent.referrer === 'string' && clientEvent.referrer.trim()
      ? clientEvent.referrer.trim().slice(0, 512)
      : null,
    ...utm,
    device_type: parsed.deviceType,
    os: parsed.os,
    browser: parsed.browser,
    screen,
    lang: detectLang(event, clientPath),
    ip,
    duration_ms: durationMs,
    props: clientEvent.props && typeof clientEvent.props === 'object' && !Array.isArray(clientEvent.props)
      ? clientEvent.props as Record<string, unknown>
      : null,
  }
}

// ---------------------------------------------------------------------------
// 内存队列 + 批量异步 flush（§5.1 / §9）
// ---------------------------------------------------------------------------

const queue: AnalyticsTrackEvent[] = []
let flushTimer: ReturnType<typeof setTimeout> | null = null
let flushing = false

interface FlushConfig {
  apiBaseUrl: string
  signSecret: string
  storeCode: string
}

/**
 * 在第一次入队（请求上下文内）捕获配置，避免 flush 的定时器回调里没有请求上下文。
 * 配置项与 apiClient 完全一致：signSecret / apiBaseUrl / storeCode。
 */
let cachedConfig: FlushConfig | null = null
function getFlushConfig(): FlushConfig | null {
  if (cachedConfig) return cachedConfig
  const config = useRuntimeConfig()
  const apiBaseUrl = String(config.apiBaseUrl || '').trim()
  const signSecret = String(config.signSecret || '').trim()
  if (!apiBaseUrl || !signSecret) return null
  cachedConfig = {
    apiBaseUrl,
    signSecret,
    storeCode: String(config.storeCode || '').trim(),
  }
  return cachedConfig
}

/** 把一批事件异步 POST 到 Java；失败静默丢弃（绝不重试） */
async function postBatch(events: AnalyticsTrackEvent[]): Promise<void> {
  const config = getFlushConfig()
  if (!config) return

  const body = { events }
  const ts = String(Date.now())
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Track-Timestamp': ts,
    'X-Track-Sign': CryptoJS.MD5(config.signSecret + ':' + ts).toString(),
  }
  // 店铺归属交给 Java 经 X-Store-Code 解析（复用 apiClient 既有约定）
  if (config.storeCode) {
    headers['X-Store-Code'] = config.storeCode
  }

  try {
    await $fetch<unknown>(TRACK_ENDPOINT, {
      baseURL: config.apiBaseUrl,
      method: 'POST',
      body,
      headers,
      timeout: FLUSH_TIMEOUT_MS,
      retry: 0,
    })
  } catch (error) {
    // 静默丢弃（丢行为数据，绝不丢业务、绝不重试风暴）。仅在 dev 打一行摘要便于联调。
    if (import.meta.dev) {
      console.warn('[analytics] flush dropped', events.length, 'events:', (error as Error)?.message)
    }
  }
}

/** 若队列非空且没有在途 flush / 已排定定时器，则排一个 2s 后的 flush */
function ensureScheduled(): void {
  if (flushing || flushTimer !== null || queue.length === 0) return
  flushTimer = setTimeout(() => {
    flushTimer = null
    void flush()
  }, FLUSH_INTERVAL_MS)
}

async function flush(): Promise<void> {
  if (flushing) return
  if (queue.length === 0) return
  flushing = true

  const batch = queue.splice(0, FLUSH_BATCH_SIZE)
  try {
    await postBatch(batch)
  } finally {
    flushing = false
    // 在 flush 期间又积累了事件：达到阈值立刻再冲，否则重新排定时器
    if (queue.length >= FLUSH_BATCH_SIZE) {
      setTimeout(() => { void flush() }, 0)
    } else {
      ensureScheduled()
    }
  }
}

/** 入队（中间件与 /api/track/events 共用同一内存队列）。立即返回，绝不 await 落库。 */
export function enqueueEvents(events: AnalyticsTrackEvent[]): void {
  if (!events.length) return
  // 确保配置在请求上下文里完成首次捕获
  getFlushConfig()

  queue.push(...events)
  if (queue.length >= FLUSH_BATCH_SIZE) {
    if (flushTimer !== null) {
      clearTimeout(flushTimer)
      flushTimer = null
    }
    if (!flushing) {
      setTimeout(() => { void flush() }, 0)
    }
    return
  }
  ensureScheduled()
}
