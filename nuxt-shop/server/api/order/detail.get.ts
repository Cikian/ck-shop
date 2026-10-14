// server/api/order/detail.get.ts
// GET /api/order/detail?orderNo=xxx —— 店铺端「订单详情」(BFF)
//
// 转发目标：Java 后端 GET /shop/order/store/detail?orderNo=xxx
// 出参（StoreOrderDetailVO = StoreOrderListVO + 以下内容）：
//   - 完整收货地址（买家本人可见，不脱敏）；
//   - 金额明细（totalAmount / productAmount / promotionDiscount / freightAmount ...）；
//   - payments / refunds（支付单、退款单）；
//   - events（买家可见的订单节点时间线）。
//
// 归属校验在后端：不是自己的订单会返回中文业务错误「订单不存在或无权操作」，
// BFF 原样透出，不做二次改写。

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
    return await api<unknown>('/shop/order/store/detail', {
      method: 'GET',
      query: { orderNo },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '订单详情加载失败，请稍后重试',
    })
  }
})
