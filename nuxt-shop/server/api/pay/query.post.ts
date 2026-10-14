// server/api/pay/query.post.ts
// POST /api/pay/query —— 店铺端「主动向渠道查询支付结果」(BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/pay/query
// 入参（OrderPayDTO）：{ orderNo }
// 出参：{ settled, message }
//
// 用户点「我已支付，刷新一下」时调用：后端会真的去渠道查一次并尝试结算。
// 未查到时返回 settled = false + 一句中文说明（"系统也会自动补单…"），
// 前端直接展示这句话即可，不要自己编造结论。

import { resolveStoreLang } from '~~/types/store-locale'

interface PayQueryBody {
  orderNo?: string
  lang?: string
}

export default defineEventHandler(async (event): Promise<unknown> => {
  const body = await readBody<PayQueryBody>(event)
  const orderNo = String(body?.orderNo ?? '').trim()
  if (!orderNo) {
    throw createError({ statusCode: 400, statusMessage: '缺少订单号' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  try {
    return await api<unknown>('/shop/order/store/pay/query', {
      method: 'POST',
      body: { orderNo },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '支付结果确认失败，请稍后重试',
    })
  }
})
