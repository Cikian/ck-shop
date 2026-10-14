// server/api/pay/status.get.ts
// GET /api/pay/status?orderNo=xxx —— 店铺端「支付状态查询」(BFF)，支付页轮询用
//
// 转发目标：Java 后端 GET /shop/order/store/pay/status?orderNo=xxx
// 出参：{ payments: [{ paymentNo, channel, status, amount, paidAmount, payTime, channelTradeNo }],
//         paid }
//
// 渠道回调有延迟，所以前端不能"跳转回来就当已支付"——付完款必须靠这个接口确认。
// 支付单状态（status）是后端直出的英文枚举，前端只把它当作判断依据，
// 界面文案使用后端给的中文标签（详情页的 statusLabel）。

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
    return await api<unknown>('/shop/order/store/pay/status', {
      method: 'GET',
      query: { orderNo },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '支付状态查询失败，请稍后重试',
    })
  }
})
