// server/api/order/confirm-receive.post.ts
// POST /api/order/confirm-receive —— 店铺端「确认收货」(BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/confirm-receive
// 入参（OrderActionDTO）：{ orderNo }
//
// 后端只接受「已发货」的订单，其余状态返回中文业务错误。
// 前端按钮可见性用列表/详情返回的 canReceive 字段，不自建状态判断。

import { resolveStoreLang } from '~~/types/store-locale'

interface ConfirmReceiveBody {
  orderNo?: string
  lang?: string
}

export default defineEventHandler(async (event): Promise<unknown> => {
  const body = await readBody<ConfirmReceiveBody>(event)
  const orderNo = String(body?.orderNo ?? '').trim()
  if (!orderNo) {
    throw createError({ statusCode: 400, statusMessage: '缺少订单号' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  try {
    return await api<unknown>('/shop/order/store/confirm-receive', {
      method: 'POST',
      body: { orderNo },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '确认收货失败，请稍后重试',
    })
  }
})
