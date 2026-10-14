// server/api/address/list.get.ts
// GET /api/address/list —— 当前登录用户的收货地址簿 (BFF)
//
// 转发目标：Java 后端 GET /shop/order/store/address/list
// 出参：AddressVO[]（id / receiverName / receiverPhone / province / city / district /
//        detailAddress / postalCode / addressTag / isDefault / masked / useCount /
//        lastUsedTime / fullAddress）
//
// 两点与订单接口不同：
//   1) 地址**不按店铺隔离**（同一个人在不同店铺前台共用一份地址簿），
//      但依然必须有登录态，所有操作都带 userId 过滤；
//   2) 手机号**不脱敏** —— 这是买家自己的地址簿，脱敏只会让他无法核对收货信息。

import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<unknown> => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  try {
    return await api<unknown>('/shop/order/store/address/list', { method: 'GET' })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '地址列表加载失败，请稍后重试',
    })
  }
})
