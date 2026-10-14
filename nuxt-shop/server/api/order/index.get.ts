// server/api/order/index.get.ts
// GET /api/order —— 店铺端「我的订单」列表 (BFF)
//
// 转发目标：Java 后端 GET /shop/order/store/list
//   query: pageNo / pageSize / orderStatus / payStatus / refundStatus / orderNo / includeArchived
// 出参：MyBatis-Plus 的 Page 对象，字段为 { records, total, size, current, pages }。
//
// 刻意**不接受** storeId：订单归属由后端从登录态 + X-Store-Code 推导，
// 让前端传 storeId 等于把「看别人店铺订单」的入口留在接口上。
// 同理 userId 也不接受。
//
// 订单状态中文一律用后端返回的 orderStatusLabel，前端不自建映射。

import { resolveStoreLang } from '~~/types/store-locale'

/** 订单状态白名单：与 OrderQuery 注释里的取值一致（前端传别的值没有意义） */
const ORDER_STATUSES = ['WAIT_PAY', 'WAIT_SHIP', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'CLOSED']
const PAY_STATUSES = ['UNPAID', 'PAID', 'PARTIAL_REFUND', 'REFUNDED']
const REFUND_STATUSES = ['NONE', 'APPLYING', 'REJECTED', 'PARTIAL', 'FULL']

/** 后端分页默认 10 条；这里再设一个上限，避免前端传个 100000 把库拖垮 */
const DEFAULT_PAGE_SIZE = 10
const MAX_PAGE_SIZE = 50

function positiveInt(value: unknown, fallback: number, max: number): number {
  const parsed = Number(Array.isArray(value) ? value[0] : value)
  if (!Number.isFinite(parsed) || parsed < 1) return fallback
  return Math.min(Math.trunc(parsed), max)
}

function pickEnum(value: unknown, allowed: string[]): string | undefined {
  const raw = String(Array.isArray(value) ? value[0] : (value ?? '')).trim().toUpperCase()
  return allowed.includes(raw) ? raw : undefined
}

export default defineEventHandler(async (event): Promise<unknown> => {
  const query = getQuery(event)

  const params: Record<string, unknown> = {
    pageNo: positiveInt(query.pageNo, 1, 100000),
    pageSize: positiveInt(query.pageSize, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE),
  }

  const orderStatus = pickEnum(query.orderStatus, ORDER_STATUSES)
  if (orderStatus) params.orderStatus = orderStatus

  const payStatus = pickEnum(query.payStatus, PAY_STATUSES)
  if (payStatus) params.payStatus = payStatus

  const refundStatus = pickEnum(query.refundStatus, REFUND_STATUSES)
  if (refundStatus) params.refundStatus = refundStatus

  const orderNo = String(query.orderNo ?? '').trim()
  if (orderNo) params.orderNo = orderNo

  // 归档订单默认不进列表（后端 OrderQuery 默认值也是 false），显式传一次保持口径一致
  params.includeArchived = String(query.includeArchived ?? '') === 'true'

  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  try {
    return await api<unknown>('/shop/order/store/list', {
      method: 'GET',
      query: params,
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '订单列表加载失败，请稍后重试',
    })
  }
})
