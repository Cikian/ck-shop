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
// 缓存：图片内容不可变（同一 URL 同一宽度），响应带一年 immutable 缓存，
// 由浏览器/CDN 承担重复访问；Nitro 侧再挂 swr（见 nuxt.config routeRules）。

import { lookup } from 'node:dns/promises'
import { isIP } from 'node:net'
import sharp from 'sharp'

const MIN_WIDTH = 16
const MAX_WIDTH = 1600
const DEFAULT_QUALITY = 72
const MAX_BYTES = 12 * 1024 * 1024
const FETCH_TIMEOUT_MS = 4000

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

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const rawSrc = String(query.src || '').trim()
  if (!rawSrc) {
    throw createError({ statusCode: 400, statusMessage: 'Missing src' })
  }

  const width = clampNumber(Number(query.w), MIN_WIDTH, MAX_WIDTH, 640)
  const quality = clampNumber(Number(query.q), 40, 90, DEFAULT_QUALITY)
  const url = await assertPublicUrl(rawSrc)

  let response: Response
  try {
    response = await fetch(url, {
      headers: { accept: 'image/*' },
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    })
  } catch {
    // 缩放服务拿不到原图（网络受限/超时）时直接 302 回原图：
    // 浏览器自己还能通过代理/CDN 打开，比返回 504 让前端 onerror 再兜底少一次往返。
    return sendRedirect(event, url.toString(), 302)
  }
  if (!response.ok) {
    return sendRedirect(event, url.toString(), 302)
  }

  const contentType = (response.headers.get('content-type') || '').toLowerCase()
  if (!contentType.startsWith('image/')) {
    throw createError({ statusCode: 415, statusMessage: 'Not an image' })
  }

  // SVG 不做栅格化：sharp 会把它渲染成位图，反而失真；直接透传原文件
  if (contentType.includes('svg')) {
    const svg = Buffer.from(await response.arrayBuffer())
    if (svg.byteLength > MAX_BYTES) {
      throw createError({ statusCode: 413, statusMessage: 'Image too large' })
    }
    setHeader(event, 'content-type', 'image/svg+xml')
    setHeader(event, 'cache-control', 'public, max-age=31536000, immutable')
    return svg
  }

  const lengthHeader = Number(response.headers.get('content-length') || 0)
  if (lengthHeader > MAX_BYTES) {
    throw createError({ statusCode: 413, statusMessage: 'Image too large' })
  }

  const input = Buffer.from(await response.arrayBuffer())
  if (input.byteLength > MAX_BYTES) {
    throw createError({ statusCode: 413, statusMessage: 'Image too large' })
  }

  try {
    const output = await sharp(input)
      .rotate() // 按 EXIF 方向摆正
      .resize({ width, withoutEnlargement: true, fit: 'inside' })
      .webp({ quality })
      .toBuffer()

    setHeader(event, 'content-type', 'image/webp')
    setHeader(event, 'cache-control', 'public, max-age=31536000, immutable')
    return output
  } catch {
    // 解不出来（损坏/非位图格式）就退回原图，不能让图片位直接裂掉
    setHeader(event, 'content-type', contentType)
    setHeader(event, 'cache-control', 'public, max-age=3600')
    return input
  }
})
