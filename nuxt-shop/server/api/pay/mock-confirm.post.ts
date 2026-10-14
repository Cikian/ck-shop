// server/api/pay/mock-confirm.post.ts
// POST /api/pay/mock-confirm —— 店铺端「模拟支付确认」(BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/pay/mock-confirm
// 入参（OrderPayDTO）：{ orderNo }（后端会用它换出该订单当前待支付的支付单号）
// 出参：{ paymentNo, paid }
//
// 【本期的支付闭环就是它】点击「立即支付」= prepay + mock-confirm，即视为付款成功。
// 它并不做任何"假业务处理"：后端让模拟渠道生成一条**已签名**的回调，
// 再走与真实渠道完全相同的链路（验签 → 去重 → 状态机 → 资金流水 → 库存实扣），
// 所以接入真实渠道时这条路径不需要重写。
//
// 仅当订单存在「待支付」的支付单时才可用；已完成支付或已关闭的订单会返回中文业务错误。

import { resolveStoreLang } from '~~/types/store-locale'

interface MockConfirmBody {
  orderNo?: string
  lang?: string
}

export default defineEventHandler(async (event): Promise<unknown> => {
  const body = await readBody<MockConfirmBody>(event)
  const orderNo = String(body?.orderNo ?? '').trim()
  if (!orderNo) {
    throw createError({ statusCode: 400, statusMessage: '缺少订单号' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  try {
    return await api<unknown>('/shop/order/store/pay/mock-confirm', {
      method: 'POST',
      body: { orderNo },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '模拟支付确认失败，请稍后重试',
    })
  }
})
