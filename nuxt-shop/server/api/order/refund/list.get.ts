// server/api/order/refund/list.get.ts
// GET /api/order/refund/list?orderNo=xxx —— 某订单的退款单列表 (BFF)
//
// 转发目标：Java 后端 GET /shop/order/store/refund/list?orderNo=xxx
// 出参：OrderRefundVO[]（含 statusLabel / refundTypeLabel / items 退款明细）
//
// 退款状态中文一律用后端返回的 statusLabel 展示，前端不做映射。

import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<unknown> => {
  const query = getQuery(event)
  const orderNo = String(query.orderNo ?? '').trim()
  if (!orderNo) {
    throw createError({ statusCode: 400, statusMessage: '缺少订单号' })
  }

  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  try {
    return await api<unknown>('/shop/order/store/refund/list', {
      method: 'GET',
      query: { orderNo },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '退款单加载失败，请稍后重试',
    })
  }
})
