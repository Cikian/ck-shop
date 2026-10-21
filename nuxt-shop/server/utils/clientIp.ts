// server/utils/clientIp.ts
//
// 统一解析「真实客户端 IP」，供限流与后端审计使用。
//
// 背景（线上安全）：
//   旧实现直接 `getRequestIP(event, { xForwardedFor: true })` —— 无条件信任
//   X-Forwarded-For。如果站点被直连（或攻击者绕过了前置代理），随手伪造这个头
//   就能让所有按 IP 的限流失效（每次换一个伪造 IP 即可）。
//
// 现在的规则：
//   1) 取 TCP 对端地址（remoteAddr）；
//   2) 只有对端是「本机/内网」时，才认为它可能是 Nginx / frp / 容器代理，
//      此时才采信它注入的 X-Client-IP / X-Forwarded-For / X-Real-IP；
//   3) 公网直连的请求一律只认 remoteAddr，代理头全部忽略。
//
// 与后端 ShopAccountSupport#resolveClientIp 保持同一套信任模型：
//   BFF 把算好的 IP 写进 `X-Client-IP` 转发给 Java；Java 只在请求来自内网时采信。

import type { H3Event } from 'h3'

/** 规范化 IP；明显不是 IP 的返回 null（同时过滤 "unknown"、引号、[::1] 写法） */
function normalizeIp(raw?: string | null): string | null {
  if (!raw) return null
  let ip = raw.trim()
  if (ip.startsWith('"') && ip.endsWith('"') && ip.length > 2) {
    ip = ip.slice(1, -1).trim()
  }
  if (!ip || ip.toLowerCase() === 'unknown') return null
  if (ip.includes(':')) {
    if (ip.startsWith('[') && ip.endsWith(']')) ip = ip.slice(1, -1)
    return ip.length <= 64 ? ip : null
  }
  const parts = ip.split('.')
  if (parts.length !== 4) return null
  for (const part of parts) {
    if (!/^\d{1,3}$/.test(part)) return null
    const value = Number(part)
    if (value < 0 || value > 255) return null
  }
  return ip
}

/** 本机回环 / 内网地址：公网攻击者无法把自己的 TCP 对端伪装成这些地址 */
function isTrustedProxy(ip: string): boolean {
  if (ip === '::1' || ip === '0:0:0:0:0:0:0:1') return true
  if (ip.includes(':')) {
    const lower = ip.toLowerCase()
    return lower.startsWith('fc') || lower.startsWith('fd')
      || lower.startsWith('fe8') || lower.startsWith('fe9')
      || lower.startsWith('fea') || lower.startsWith('feb')
  }
  const [a, b] = ip.split('.').map(Number)
  if (a === 127 || a === 10) return true
  if (a === 172 && b >= 16 && b <= 31) return true
  if (a === 192 && b === 168) return true
  return a === 169 && b === 254
}

/**
 * 取真实客户端 IP。
 *
 * @returns 规范化后的 IP；极端情况下（取不到）返回 'unknown'
 */
export function getClientIp(event: H3Event): string {
  const remote = normalizeIp(getRequestIP(event, { xForwardedFor: false }))
  if (!remote) return 'unknown'

  // 非可信代理直连：代理头一律不可信
  if (!isTrustedProxy(remote)) return remote

  // 1) BFF 之前的代理（如有）注入的 X-Client-IP
  const injected = normalizeIp(getRequestHeader(event, 'x-client-ip'))
  if (injected) return injected

  // 2) X-Forwarded-For：从右往左跳过可信代理，第一个不可信地址即客户端
  const xff = getRequestHeader(event, 'x-forwarded-for')
  if (xff) {
    const parts = xff.split(',').map(part => normalizeIp(part)).filter((ip): ip is string => !!ip)
    let fallback: string | undefined
    for (let i = parts.length - 1; i >= 0; i--) {
      fallback ??= parts[i]
      if (!isTrustedProxy(parts[i])) return parts[i]
    }
    if (fallback) return fallback
  }

  // 3) Nginx 常见写法
  const realIp = normalizeIp(getRequestHeader(event, 'x-real-ip'))
  if (realIp) return realIp

  // 4) 兜底：TCP 对端（即代理本机地址）
  return remote
}
