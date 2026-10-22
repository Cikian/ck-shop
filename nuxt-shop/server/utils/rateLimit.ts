// server/utils/rateLimit.ts
//
// 固定窗口限流（边缘防护）。
//
// 为什么需要：登录、注册、发验证码、重置密码这几条接口此前**完全没有限流**，
// 任何脚本都能以每秒上百次的速度做撞库 / 密码喷洒 / 刷邮件（图形验证码只
// 挡得住"发验证码"，挡不住"邮箱+密码"这条路径）。
//
// 存储（清单 B19）：
//   · 计数器放在 Nitro storage 的 `rate` 挂载上（见 nuxt.config 的 nitro.storage）；
//   · 配置了 RATE_LIMIT_REDIS_URL 时用 Redis —— 多实例共用同一份计数，
//     攻击者无法靠"轮询实例"放大配额；
//   · 没配置时退回进程内存，并**在第一次使用时打一条 warn**，避免"以为有共享限流、
//     其实是每实例各算一份"的误解。
//   · 存储读写异常（Redis 抖动）时同样退回内存计数：可用性优先，
//     限流只是减速带，不能因为它把登录接口打挂。
//
// 已知局限：读-改-写不是原子操作，高并发下窗口内可能多放过几次请求。
// **权威限流在 Java 后端（Redis）**，这里是第一道减速带，目标是抬高成本。
//
// 客户端 IP 使用 server/utils/clientIp.ts：只有在请求来自可信代理时才采信
// X-Forwarded-For，避免直连场景伪造头绕过限流。
//
// 用法（handler 最前面，注意要 await）：
//   await enforceRateLimit(event, { key: 'auth:login', limit: 10, windowMs: 60_000, extra: email })

import type { H3Event } from 'h3'
import { getClientIp } from './clientIp'

interface RateLimitBucket {
  count: number
  resetAt: number
}

/** 内存兜底：仅在未配置 Redis 或存储异常时使用 */
const memoryBuckets = new Map<string, RateLimitBucket>()
/** 超过这个规模就顺手清理过期桶，避免 Map 被大量随机 IP 撑爆内存 */
const CLEANUP_THRESHOLD = 5000
/** 是否已经提示过"当前是进程内计数" */
let warnedAboutLocalCounter = false

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

function isSharedStorageConfigured(): boolean {
  return Boolean(process.env.RATE_LIMIT_REDIS_URL)
}

function rejectTooMany(event: H3Event, retryAfterSeconds: number, message?: string): never {
  setHeader(event, 'Retry-After', String(retryAfterSeconds))
  throw createError({
    statusCode: 429,
    statusMessage: message || `操作过于频繁，请 ${retryAfterSeconds} 秒后再试`,
  })
}

/** 进程内计数（单实例语义，或 Redis 不可用时的兜底） */
function enforceInMemory(event: H3Event, identity: string, options: RateLimitOptions): void {
  const now = Date.now()
  const bucket = memoryBuckets.get(identity)

  if (!bucket || bucket.resetAt <= now) {
    if (memoryBuckets.size > CLEANUP_THRESHOLD) {
      for (const [key, value] of memoryBuckets) {
        if (value.resetAt <= now) {
          memoryBuckets.delete(key)
        }
      }
    }
    memoryBuckets.set(identity, { count: 1, resetAt: now + options.windowMs })
    return
  }

  bucket.count += 1
  if (bucket.count > options.limit) {
    rejectTooMany(event, Math.max(1, Math.ceil((bucket.resetAt - now) / 1000)), options.message)
  }
}

/** 超出配额时抛 429（带 Retry-After），否则放行并计数 */
export async function enforceRateLimit(event: H3Event, options: RateLimitOptions): Promise<void> {
  const identity = `${options.key}|${getClientIp(event)}|${(options.extra || '').toLowerCase()}`

  if (!isSharedStorageConfigured()) {
    if (!warnedAboutLocalCounter) {
      warnedAboutLocalCounter = true
      console.warn(
        '[rateLimit] 未配置 RATE_LIMIT_REDIS_URL，限流计数为**进程内**实现：' +
          '多实例部署时每个实例各算一份，攻击者可轮询实例放大配额。',
      )
    }
    enforceInMemory(event, identity, options)
    return
  }

  const now = Date.now()
  const windowIndex = Math.floor(now / options.windowMs)
  const storageKey = `${identity}|${windowIndex}`

  try {
    const storage = useStorage('rate')
    const current = Number((await storage.getItem<number>(storageKey)) ?? 0)

    if (current >= options.limit) {
      const remainingMs = options.windowMs - (now % options.windowMs)
      rejectTooMany(event, Math.max(1, Math.ceil(remainingMs / 1000)), options.message)
    }

    const ttlSeconds = Math.max(1, Math.ceil((options.windowMs - (now % options.windowMs)) / 1000))
    await storage.setItem(storageKey, current + 1, { ttl: ttlSeconds })
  } catch (error) {
    // 存储不可用：退回内存计数，别让限流把正常请求打挂
    console.warn('[rateLimit] 共享存储不可用，本次退回进程内计数：', (error as Error)?.message)
    enforceInMemory(event, identity, options)
  }
}
