// server/api/order/cancel.post.ts
// POST /api/order/cancel —— 店铺端「取消订单」(BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/cancel
// 入参（OrderActionDTO）：{ orderNo, reason }
//
// 后端只允许**待支付**的订单被买家取消（其余状态会返回中文业务错误）。
// 前端不重复实现这套判断：按钮可见性直接用列表/详情返回的 canCancel 字段。

import { resolveStoreLang } from '~~/types/store-locale'

interface CancelBody {
  orderNo?: string
  reason?: string
  lang?: string
}

export default defineEventHandler(async (event): Promise<unknown> => {
  const body = await readBody<CancelBody>(event)
  const orderNo = String(body?.orderNo ?? '').trim()
  if (!orderNo) {
    throw createError({ statusCode: 400, statusMessage: '缺少订单号' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  try {
    return await api<unknown>('/shop/order/store/cancel', {
      method: 'POST',
      body: {
        orderNo,
        reason: body?.reason ? String(body.reason).trim() : '',
      },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '取消订单失败，请稍后重试',
    })
  }
})
