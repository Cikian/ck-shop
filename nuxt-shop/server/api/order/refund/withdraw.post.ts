// server/api/order/refund/withdraw.post.ts
// POST /api/order/refund/withdraw?refundNo=xxx —— 店铺端「撤销退款申请」(BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/refund/withdraw（String refundNo）
//
// 【注意参数位置】后端方法的签名是 `withdrawRefund(String refundNo, HttpServletRequest req)`，
// 没有 @RequestBody —— 所以 refundNo 必须走**查询参数**（?refundNo=xxx），
// 放进 body 后端读不到，会得到「退款单不存在」这类误导性错误。

import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<unknown> => {
  const query = getQuery(event)
  const refundNo = String(query.refundNo ?? '').trim()
  if (!refundNo) {
    throw createError({ statusCode: 400, statusMessage: '缺少退款单号' })
  }

  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  try {
    return await api<unknown>('/shop/order/store/refund/withdraw', {
      method: 'POST',
      query: { refundNo },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '撤销退款申请失败，请稍后重试',
    })
  }
})
