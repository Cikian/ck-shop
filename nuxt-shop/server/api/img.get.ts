// server/api/img.get.ts
//
// 图片响应式管线（BFF 侧，无需外部图片 CDN）。
//
// 背景：商品图/轮播图都是外部原图（demo 数据甚至是 Unsplash 的 900×900），
// 卡片里 150px 的缩略图与详情页 800px 的主图用的是**同一个 URL**，
// 首屏 25+ 张图全按原图下载，移动端流量和 LCP 都不可控。
//
// 这个接口按 `w` 实时缩放并转 WebP，配合前端 srcset 输出多档尺寸：
//   /api/img?src=<encoded>&w=320
//
// 安全：
//   · 只允许 http/https；
//   · 解析目标域名，拒绝回环/内网/链路本地地址（SSRF 防护，含 IPv6）；
//   · 限制下载体积与超时，图片类型必须 image/*。
//
// 缓存（2026-10-23 重做，务必看）：
//   图片内容不可变（同一 URL 同一宽度），本来用 nuxt.config 的 routeRules 挂 `swr: 86400`。
//   **但那条规则会把二进制响应体序列化成 JSON 再回放** —— 命中缓存时浏览器拿到的其实是
//   `{"type":"Buffer","data":[82,73,70,70,…]}` 这段文本（前 16 字节是 `7b0a2020227479706522`），
//   `<img>` 立刻触发 error，于是前端 ShopImage 退回原图 URL，用户看到的是：
//   "刷新后图片裂了、3~5 秒后才出来"。
//   现在改成本文件内的**进程内 Buffer LRU**（只存原始字节，不经过任何序列化）：
//     · 命中：直接写 buffer，几十微秒；
//     · 未命中：同一个 key 并发只跑一次 sharp（in-flight 去重），避免一屏 27 张图把 CPU 打满；
//     · 浏览器侧仍靠 `cache-control: immutable` 缓存一年。

import { lookup } from 'node:dns/promises'
import { isIP } from 'node:net'
import sharp from 'sharp'

const MIN_WIDTH = 16
const MAX_WIDTH = 1600
const DEFAULT_QUALITY = 72
const MAX_BYTES = 12 * 1024 * 1024
const FETCH_TIMEOUT_MS = 8000

/** 内网/保留地址判断（IPv4 + IPv6） */
function isPrivateAddress(address: string): boolean {
  if (isIP(address) === 4) {
    const [a, b] = address.split('.').map(Number)
    if (a === 10 || a === 127 || a === 0) return true
    if (a === 169 && b === 254) return true
    if (a === 172 && b >= 16 && b <= 31) return true
    if (a === 192 && b === 168) return true
    return false
  }
  const normalized = address.toLowerCase()
  if (normalized === '::1' || normalized === '::') return true
  if (normalized.startsWith('fc') || normalized.startsWith('fd')) return true
  if (normalized.startsWith('fe80')) return true
  // IPv4-mapped IPv6（::ffff:127.0.0.1）
  if (normalized.startsWith('::ffff:')) {
    return isPrivateAddress(normalized.slice('::ffff:'.length))
  }
  return false
}

async function assertPublicUrl(raw: string): Promise<URL> {
  let url: URL
  try {
    url = new URL(raw)
  } catch {
    throw createError({ statusCode: 400, statusMessage: 'Invalid image src' })
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw createError({ statusCode: 400, statusMessage: 'Unsupported image protocol' })
  }
  const host = url.hostname.replace(/^\[|\]$/g, '')
  if (isIP(host)) {
    if (isPrivateAddress(host)) {
      throw createError({ statusCode: 403, statusMessage: 'Private address is not allowed' })
    }
    return url
  }
  let addresses: Array<{ address: string }>
  try {
    addresses = await lookup(host, { all: true })
  } catch {
    throw createError({ statusCode: 502, statusMessage: 'Image host resolution failed' })
  }
  if (!addresses.length || addresses.some(item => isPrivateAddress(item.address))) {
    throw createError({ statusCode: 403, statusMessage: 'Private address is not allowed' })
  }
  return url
}

function clampNumber(value: number, min: number, max: number, fallback: number): number {
  if (!Number.isFinite(value) || value <= 0) return fallback
  return Math.min(Math.max(Math.round(value), min), max)
}

// ===== 进程内二进制 LRU =====
/**
 * 只存 `Buffer` 的 LRU。**不要**把这条缓存换回 routeRules，原因见文件头。
 * 上限按字节算：64MB 足够放几千张缩略图，同时不会把容器内存吃满。
 */
const CACHE_MAX_BYTES = 64 * 1024 * 1024
/** 单张超过这个体积就不入缓存（大图进缓存只会把小的挤出去，收益低） */
const CACHE_ENTRY_MAX_BYTES = 2 * 1024 * 1024

const imageCache = new Map<string, CachedImage>()
let cachedBytes = 0

interface CachedImage {
  buffer: Buffer
  contentType: string
}

function cacheGet(key: string): CachedImage | undefined {
  const hit = imageCache.get(key)
  if (hit) {
    // Map 保序：删了再放回去 = 标记为"最近用过"
    imageCache.delete(key)
    imageCache.set(key, hit)
  }
  return hit
}

function cacheSet(key: string, value: CachedImage) {
  if (value.buffer.byteLength > CACHE_ENTRY_MAX_BYTES) return
  const existing = imageCache.get(key)
  if (existing) {
    cachedBytes -= existing.buffer.byteLength
    imageCache.delete(key)
  }
  imageCache.set(key, value)
  cachedBytes += value.buffer.byteLength
  while (cachedBytes > CACHE_MAX_BYTES) {
    const oldestKey = imageCache.keys().next().value
    if (oldestKey === undefined) break
    const oldest = imageCache.get(oldestKey)
    if (oldest) cachedBytes -= oldest.buffer.byteLength
    imageCache.delete(oldestKey)
  }
}

/** 同一张图并发请求只跑一次 sharp（首屏一屏 20+ 张图时差别很大） */
const inflight = new Map<string, Promise<CachedImage>>()

/**
 * 上游并发闸门（2026-10-23）。
 *
 * 首屏一次会请求 20+ 张图，每张都要"抓原图 + sharp 转码"。全部同时跑会把这个
 * Node 进程占满 —— 在 dev 下最直观的后果是 **SSR 与 Vite 模块请求被挤后**，
 * 页面水合被拖到 5~8 秒，用户看到的就是"图片加载期间所有按钮都点不动"。
 *
 * 机理：sharp 的转码跑在 libuv 线程池里，而 `dns.lookup`（SSR 调后端）与 Vite 读文件
 * 用的是**同一个线程池**。并发 4 个 sharp 就能把池子占满，SSR 的 DNS 查询只能排队。
 *
 * 所以这里做两件事：
 *   1) `sharp.concurrency(1)`：单张图只占一个线程；
 *   2) 上游抓取+转码并发上限 2：单张略慢，但**页面更早可交互**。
 * 命中进程内 LRU 时完全不经过闸门，热缓存没有额外开销。
 */
const MAX_UPSTREAM_CONCURRENCY = 2
sharp.concurrency(1)

let upstreamActive = 0
const upstreamQueue: Array<() => void> = []

async function withUpstreamSlot<T>(task: () => Promise<T>): Promise<T> {
  if (upstreamActive >= MAX_UPSTREAM_CONCURRENCY) {
    await new Promise<void>((resolve) => { upstreamQueue.push(resolve) })
  }
  upstreamActive++
  try {
    return await task()
  } finally {
    upstreamActive--
    const next = upstreamQueue.shift()
    if (next) next()
  }
}

/**
 * 抓原图 + 缩放。返回 webp 字节（SVG 原样透传）；拿不到时抛错，
 * 由调用方决定是 302 回原图还是往上抛。
 */
async function resizeRemote(url: URL, width: number, quality: number): Promise<CachedImage> {
  const response = await fetch(url, {
    headers: { accept: 'image/*' },
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
  })
  if (!response.ok) {
    const error = new Error(`Upstream ${response.status}`) as Error & { redirect?: string }
    error.redirect = url.toString()
    throw error
  }

  const contentType = (response.headers.get('content-type') || '').toLowerCase()
  if (!contentType.startsWith('image/')) {
    throw createError({ statusCode: 415, statusMessage: 'Not an image' })
  }

  const lengthHeader = Number(response.headers.get('content-length') || 0)
  if (lengthHeader > MAX_BYTES) {
    throw createError({ statusCode: 413, statusMessage: 'Image too large' })
  }

  const input = Buffer.from(await response.arrayBuffer())
  if (input.byteLength > MAX_BYTES) {
    throw createError({ statusCode: 413, statusMessage: 'Image too large' })
  }

  // SVG 不做栅格化：sharp 会把它渲染成位图，反而失真；直接透传原文件
  if (contentType.includes('svg')) {
    return { buffer: input, contentType: 'image/svg+xml' }
  }

  try {
    const output = await sharp(input)
      .rotate() // 按 EXIF 方向摆正
      .resize({ width, withoutEnlargement: true, fit: 'inside' })
      .webp({ quality })
      .toBuffer()

    return { buffer: output, contentType: 'image/webp' }
  } catch {
    // 解不出来（损坏/非位图格式/内存不足）→ 原样透传，不能让图片位直接裂掉
    return { buffer: input, contentType: contentType || 'application/octet-stream' }
  }
}

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const rawSrc = String(query.src || '').trim()
  if (!rawSrc) {
    throw createError({ statusCode: 400, statusMessage: 'Missing src' })
  }

  const width = clampNumber(Number(query.w), MIN_WIDTH, MAX_WIDTH, 640)
  const quality = clampNumber(Number(query.q), 40, 90, DEFAULT_QUALITY)
  const url = await assertPublicUrl(rawSrc)

  const key = `${width}|${quality}|${url.toString()}`

  const cached = cacheGet(key)
  if (cached) {
    setHeader(event, 'content-type', cached.contentType)
    setHeader(event, 'cache-control', 'public, max-age=31536000, immutable')
    return cached.buffer
  }

  let pending = inflight.get(key)
  if (!pending) {
    pending = withUpstreamSlot(() => resizeRemote(url, width, quality))
      .then((image) => {
        cacheSet(key, image)
        return image
      })
      .finally(() => { inflight.delete(key) })
    inflight.set(key, pending)
  }

  try {
    const image = await pending
    setHeader(event, 'content-type', image.contentType)
    setHeader(event, 'cache-control', 'public, max-age=31536000, immutable')
    return image.buffer
  } catch (error) {
    const redirect = (error as { redirect?: string }).redirect
    if (redirect) {
      // 上游 4xx/5xx → 302 回原图：浏览器自己能打开，比 500 让 <img> 直接裂掉好
      return sendRedirect(event, redirect, 302)
    }
    // 请求本身不合法（缺参/协议不对/内网地址/超大/非图片）→ 如实返回 4xx
    const status = (error as { statusCode?: number }).statusCode
    if (status && status >= 400 && status < 500) {
      throw error
    }
    /*
     * 其它失败（抓原图超时、sharp 崩溃、线程池排队过久…）一律 302 回原图。
     * 这里必须**兜住**：一旦抛 500，浏览器 <img> 立刻 error，前端只能退回原图，
     * 用户看到的就是"图片裂一下再出来"。302 至少让浏览器自己把原图拉回来。
     */
    return sendRedirect(event, url.toString(), 302)
  }
})
