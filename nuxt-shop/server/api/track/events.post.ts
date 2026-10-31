// server/api/track/events.post.ts
// POST /api/track/events —— 前端轻量 SDK 的事件入口（同源）。
//
// 职责：接收前端 sendBeacon / fetch 上报的事件，补全前端看不到的服务端字段
// （session_id / visitor_id / ip / UA / 语言 / referrer），并入同一内存队列，
// 由 server/utils/analytics.ts 异步批量转发到 Java /shop/analytics/track/events。
//
// body 兼容两种形状：单个事件对象，或事件数组（SDK 批量合并时）。
// 未知 event_name 静默丢弃，成功与否都立即返回 { ok: true }——采集是无感的。

import { enrichClientEvent, enqueueEvents } from '../../utils/analytics'

export default defineEventHandler(async (event) => {
  const body = await readBody<unknown>(event).catch(() => null)

  const rawEvents = Array.isArray(body) ? body : (body ? [body] : [])
  const events = rawEvents
    .map((item) => {
      if (!item || typeof item !== 'object' || Array.isArray(item)) return null
      return enrichClientEvent(event, item as Record<string, unknown>)
    })
    .filter((item): item is NonNullable<typeof item> => item !== null)

  enqueueEvents(events)
  return { ok: true }
})
