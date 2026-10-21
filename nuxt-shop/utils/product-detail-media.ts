// utils/product-detail-media.ts
//
// 商品详情「视频模块」的地址解析 —— 纯函数，SSR / 客户端 / Node 自测都能直接跑。
//
// 契约：docs/商品详情-设计契约.md 4.3 视频
//   · bilibili：从 URL（或裸 BV 号）解析出 BV 号 → player.bilibili.com iframe
//   · youtube：解析 v= / youtu.be / shorts / embed / live → youtube-nocookie embed
//   · file：直链 → 原生 <video>
//   · 解析不出 id 时返回 null，调用方**整块不渲染**（绝不输出坏 iframe）
//
// 为什么单独抽成工具而不是写在组件里：
//   1) 这些正则边界多（短链 / 查询参数 / 多路径形态），集中一处才好自测；
//   2) 组件只负责"有没有值 → 渲染哪种节点"，不做字符串猜测。

export type DetailVideoProvider = 'bilibili' | 'youtube' | 'file'

export interface DetailVideoSource {
  provider: DetailVideoProvider
  /** iframe = 需要嵌入播放器；file = 原生 video */
  kind: 'iframe' | 'file'
  src: string
}

/** 需要剔除的控制字符（协议混淆 / 复制粘贴带入的空白） */
// eslint-disable-next-line no-control-regex
const CONTROL_CHARS = /[\u0000-\u0020\u007f-\u009f]/g

function normalizeForProtocolCheck(value: string): string {
  return value.replace(CONTROL_CHARS, '').toLowerCase()
}

/** 直链视频：只允许 http(s):// 或 `/` 开头（契约 3.3 对 file 的同一条约束） */
export function isSafeMediaFileUrl(value: string | null | undefined): boolean {
  if (typeof value !== 'string') return false
  const trimmed = value.trim()
  if (!trimmed) return false
  const probe = normalizeForProtocolCheck(trimmed)
  if (!probe) return false
  if (probe.startsWith('data:') || probe.startsWith('javascript:') || probe.startsWith('vbscript:')) {
    return false
  }
  if (probe.startsWith('/')) return true
  return /^https?:\/\//.test(probe)
}

// ---------------------------------------------------------------------------
// Bilibili
// ---------------------------------------------------------------------------

export interface BilibiliVideoId {
  bvid?: string
  aid?: string
}

/**
 * 从各种输入里解析 B 站视频标识：
 *   · 裸 BV 号：BV1GJ411x7h7
 *   · 完整地址：https://www.bilibili.com/video/BV1GJ411x7h7/?p=2
 *   · 短链：https://b23.tv/xxxx（短链本身不含 id，解析不出就整块不渲染 —— 不猜）
 *   · av 号（可选兼容）：av170001 / ?aid=170001
 */
export function parseBilibiliVideoId(input: string | null | undefined): BilibiliVideoId | null {
  if (typeof input !== 'string') return null
  const raw = input.trim()
  if (!raw) return null

  // 裸 BV 号 / URL 里的 BV 号。BV 号固定 12 位（BV + 10 位），后缀大小写敏感。
  const bv = /bv([0-9a-z]{10})/i.exec(raw)
  if (bv && bv[1]) return { bvid: `BV${bv[1]}` }

  // av 号：/video/av170001、?aid=170001、裸 av170001（= 兼容旧管理端写死的历史数据）
  const av = /(?:^|[^0-9a-z])(?:av|aid)[=/]?(\d{1,12})(?![0-9])/i.exec(raw)
  if (av && av[1]) return { aid: av[1] }

  return null
}

/** B 站 iframe 地址（参数按契约 4.3，autoplay=0 不在打开页面时自动播放） */
export function resolveBilibiliEmbedUrl(id: BilibiliVideoId): string | null {
  const page = 'page=1&high_quality=1&danmaku=0&autoplay=0'
  if (id.bvid) return `https://player.bilibili.com/player.html?bvid=${encodeURIComponent(id.bvid)}&${page}`
  if (id.aid) return `https://player.bilibili.com/player.html?aid=${encodeURIComponent(id.aid)}&${page}`
  return null
}

// ---------------------------------------------------------------------------
// YouTube
// ---------------------------------------------------------------------------

/**
 * 从各种 YouTube 输入里解析 11 位视频 id：
 *   · 裸 id：dQw4w9WgXcQ
 *   · https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s
 *   · https://youtu.be/dQw4w9WgXcQ
 *   · https://www.youtube.com/shorts/dQw4w9WgXcQ
 *   · https://www.youtube.com/embed/dQw4w9WgXcQ
 *   · https://www.youtube.com/live/dQw4w9WgXcQ
 */
export function parseYoutubeVideoId(input: string | null | undefined): string | null {
  if (typeof input !== 'string') return null
  const raw = input.trim()
  if (!raw) return null

  const ID = '[a-z0-9_-]{11}'
  const patterns = [
    new RegExp(`youtu\\.be/(${ID})`, 'i'),
    new RegExp(`[?&]v=(${ID})`, 'i'),
    new RegExp(`youtube(?:-nocookie)?\\.com/(?:embed|shorts|live|v)/(${ID})`, 'i'),
  ]
  for (const pattern of patterns) {
    const match = pattern.exec(raw)
    if (match && match[1]) return match[1]
  }

  // 裸 id（11 位、只含合法字符）
  if (new RegExp(`^${ID}$`, 'i').test(raw)) return raw
  return null
}

/** YouTube 嵌入地址（youtube-nocookie 域名；契约明确不加 autoplay） */
export function resolveYoutubeEmbedUrl(videoId: string): string {
  return `https://www.youtube-nocookie.com/embed/${encodeURIComponent(videoId)}`
}

// ---------------------------------------------------------------------------
// 统一出口
// ---------------------------------------------------------------------------

/**
 * 把后端 VIDEO 模块的 `{ provider, url }` 解析成可直接渲染的源。
 * 任何解析不出的情况都返回 null（调用方整块不渲染）。
 */
export function resolveDetailVideoSource(
  provider: string | null | undefined,
  url: string | null | undefined,
): DetailVideoSource | null {
  const providerName = typeof provider === 'string' ? provider.trim().toLowerCase() : ''
  const rawUrl = typeof url === 'string' ? url.trim() : ''
  if (!providerName || !rawUrl) return null

  if (providerName === 'bilibili') {
    const id = parseBilibiliVideoId(rawUrl)
    if (!id) return null
    const src = resolveBilibiliEmbedUrl(id)
    return src ? { provider: 'bilibili', kind: 'iframe', src } : null
  }

  if (providerName === 'youtube') {
    const id = parseYoutubeVideoId(rawUrl)
    if (!id) return null
    return { provider: 'youtube', kind: 'iframe', src: resolveYoutubeEmbedUrl(id) }
  }

  if (providerName === 'file') {
    if (!isSafeMediaFileUrl(rawUrl)) return null
    return { provider: 'file', kind: 'file', src: rawUrl }
  }

  return null
}
