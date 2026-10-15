// server/utils/rateLimit.ts
//
// 进程内的固定窗口限流。
//
// 为什么需要：登录、注册、发验证码、重置密码这几条接口此前**完全没有限流**，
// 任何脚本都能以每秒上百次的速度做撞库 / 密码喷洒 / 刷邮件（图形验证码只
// 挡得住"发验证码"，挡不住"邮箱+密码"这条路径）。
//
// 局限（必须知道）：
//   · 计数器在进程内存里，多实例部署时每个实例各算一份，攻击者可以靠
//     轮询实例放大配额。真正的全局限流要落在 Redis 上（本项目已有 Redis，
//     可以后续把 store 换掉，调用方 API 不用变）。
//   · 目的是把"毫无成本地暴力尝试"变成"必须慢下来"，而不是完美的风控。
//
// 用法：在 handler 最前面调用
//   enforceRateLimit(event, { key: 'auth:login', limit: 10, windowMs: 60_000, extra: email })

import type { H3Event } from 'h3'

interface RateLimitBucket {
  count: number
  resetAt: number
}

const buckets = new Map<string, RateLimitBucket>()

/** 超过这个规模就顺手清理过期桶，避免 Map 被大量随机 IP 撑爆内存 */
const CLEANUP_THRESHOLD = 5000

/**
 * 取客户端 IP。
 *
 * `xForwardedFor: true` 表示采信反向代理注入的 X-Forwarded-For；如果服务
 * 直接暴露给公网，这个头是可以伪造的，此时限流会被绕过（伪造不同 IP 即可）。
 * 部署在 nginx/frp 之后是本项目的既定形态，所以这里打开；若将来直连公网，
 * 应改为只信任固定代理网段。
 */
function clientIp(event: H3Event): string {
  return getRequestIP(event, { xForwardedFor: true }) || 'unknown-ip'
}

export interface RateLimitOptions {
  /** 限流维度前缀，例如 'auth:login' */
  key: string
  /** 窗口内允许的次数 */
  limit: number
  /** 窗口长度（毫秒） */
  windowMs: number
  /** 附加维度（通常是 email），用于「同一账号」限流，避免只按 IP 被绕过 */
  extra?: string
  /** 自定义提示文案 */
  message?: string
}

/** 超出配额时抛 429（带 Retry-After），否则放行并计数 */
export function enforceRateLimit(event: H3Event, options: RateLimitOptions): void {
  const now = Date.now()
  const identity = `${options.key}|${clientIp(event)}|${(options.extra || '').toLowerCase()}`
  const bucket = buckets.get(identity)

  if (!bucket || bucket.resetAt <= now) {
    if (buckets.size > CLEANUP_THRESHOLD) {
      for (const [key, value] of buckets) {
        if (value.resetAt <= now) {
          buckets.delete(key)
        }
      }
    }
    buckets.set(identity, { count: 1, resetAt: now + options.windowMs })
    return
  }

  bucket.count += 1
  if (bucket.count > options.limit) {
    const retryAfterSeconds = Math.max(1, Math.ceil((bucket.resetAt - now) / 1000))
    setHeader(event, 'Retry-After', String(retryAfterSeconds))
    throw createError({
      statusCode: 429,
      statusMessage: options.message || `操作过于频繁，请 ${retryAfterSeconds} 秒后再试`,
    })
  }
}
