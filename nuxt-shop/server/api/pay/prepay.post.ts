// server/api/pay/prepay.post.ts
// POST /api/pay/prepay —— 店铺端「发起支付」(BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/pay/prepay
// 入参（OrderPayDTO）：{ orderNo, channel?, payType?, requestId? }
// 出参（OrderPrepayVO）：
//   { paymentNo, orderNo, channel, payType, amount, status,
//     expireTime, remainPaySeconds, payParams }
//
// **没有金额入参**：支付金额永远取自订单的 pay_amount，
// 不是客户端传什么就收什么（「1 分钱买手机」的事故都源于此）。
//
// payParams 是 Map<String,String>，内容由支付网关实现决定
// （微信要 prepay_id + paySign，支付宝要 orderStr，H5 要跳转 URL）。
// BFF 只做透传，不解释其中任何字段 —— 新增渠道时前后端都不用改。
//
// 幂等：requestId 可选，不传时服务端用「订单号 + 当前待支付支付单」兜底，
// 保证连点不会产生多笔有效支付单。

import { resolveStoreLang } from '~~/types/store-locale'

interface PrepayBody {
  orderNo?: string
  channel?: string
  payType?: string
  requestId?: string
  lang?: string
}

export default defineEventHandler(async (event): Promise<unknown> => {
  const body = await readBody<PrepayBody>(event)
  const orderNo = String(body?.orderNo ?? '').trim()
  if (!orderNo) {
    throw createError({ statusCode: 400, statusMessage: '缺少订单号' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  const payload: Record<string, unknown> = { orderNo }
  // channel / payType / requestId 都交给调用方决定，BFF 不擅自替换渠道
  const channel = String(body?.channel ?? '').trim()
  if (channel) payload.channel = channel
  const payType = String(body?.payType ?? '').trim()
  if (payType) payload.payType = payType
  const requestId = String(body?.requestId ?? '').trim()
  if (requestId) payload.requestId = requestId

  try {
    return await api<unknown>('/shop/order/store/pay/prepay', {
      method: 'POST',
      body: payload,
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '发起支付失败，请稍后重试',
    })
  }
})
