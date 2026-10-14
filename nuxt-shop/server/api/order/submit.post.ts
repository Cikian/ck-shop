// server/api/order/submit.post.ts
// POST /api/order/submit —— 店铺端「提交订单」(BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/submit（ShopStoreOrderController#submit）
// 出参（OrderSubmitVO）：
//   { orderNo, orderStatus, orderStatusLabel, payAmount,
//     payDeadline, remainPaySeconds, createTime, idempotentReused }
//
// 两个必带项，缺一个都会被后端拒绝（后端刻意不提供兜底，因为兜底等于放弃幂等）：
//   1) **idempotentKey**：由前端在**进入结算页时**生成一次（crypto.randomUUID()），
//      用户连点、网络重试都用同一个值 -> 只会产生一笔订单。
//      前端每次点击都新生成的话幂等就失效了，所以 BFF 在这里做非空校验，防止误传。
//   2) **priceToken**：结算预览返回的价格令牌，服务端用它复核「价格是否变了」。
//
// 收货地址二选一（后端 OrderSubmitDTO 的设计）：
//   - addressId：从地址簿选一条（推荐，下单后后端会累计该地址的使用次数）；
//   - 内联 receiverName / receiverPhone / receiverAddress 等：直接下单不改地址簿。
//   两者都没传时 BFF 直接 400，省一次无效往返。

import { resolveStoreLang } from '~~/types/store-locale'

interface SubmitItemBody {
  productId?: string
  skuId?: string | null
  quantity?: number
}

interface SubmitBody {
  idempotentKey?: string
  priceToken?: string
  items?: SubmitItemBody[]
  addressId?: string | null
  receiverName?: string
  receiverPhone?: string
  receiverProvince?: string
  receiverCity?: string
  receiverDistrict?: string
  receiverAddress?: string
  receiverPostalCode?: string
  buyerRemark?: string
  orderSource?: string
  channelCode?: string
  utmSource?: string
  referrer?: string
  cartItemIds?: string[]
  lang?: string
}

function trimOrEmpty(value: unknown): string {
  return value === null || value === undefined ? '' : String(value).trim()
}

export default defineEventHandler(async (event): Promise<unknown> => {
  const body = await readBody<SubmitBody>(event)

  const idempotentKey = trimOrEmpty(body?.idempotentKey)
  if (!idempotentKey) {
    throw createError({ statusCode: 400, statusMessage: '缺少幂等键，请刷新结算页后重试' })
  }

  const priceToken = trimOrEmpty(body?.priceToken)
  if (!priceToken) {
    throw createError({ statusCode: 400, statusMessage: '价格信息已失效，请重新结算' })
  }

  const rawItems = Array.isArray(body?.items) ? body.items : []
  if (!rawItems.length) {
    throw createError({ statusCode: 400, statusMessage: '请选择要结算的商品' })
  }
  const items = rawItems.map((item, index) => {
    const productId = trimOrEmpty(item?.productId)
    if (!productId) {
      throw createError({ statusCode: 400, statusMessage: `第 ${index + 1} 行缺少商品 ID` })
    }
    const quantity = Number(item?.quantity)
    if (!Number.isFinite(quantity) || quantity < 1) {
      throw createError({ statusCode: 400, statusMessage: `第 ${index + 1} 行数量必须是大于 0 的整数` })
    }
    return {
      productId,
      // 无规格商品必须传空串（后端契约）
      skuId: trimOrEmpty(item?.skuId),
      quantity: Math.trunc(quantity),
    }
  })

  const addressId = trimOrEmpty(body?.addressId)
  const receiverName = trimOrEmpty(body?.receiverName)
  const receiverPhone = trimOrEmpty(body?.receiverPhone)
  const receiverAddress = trimOrEmpty(body?.receiverAddress)
  if (!addressId && !(receiverName && receiverPhone && receiverAddress)) {
    throw createError({ statusCode: 400, statusMessage: '请选择收货地址' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  // 只下发后端 DTO 里真实存在的字段，不臆造参数
  const payload: Record<string, unknown> = {
    idempotentKey,
    priceToken,
    items,
    buyerRemark: trimOrEmpty(body?.buyerRemark),
    orderSource: trimOrEmpty(body?.orderSource) || 'web',
    channelCode: trimOrEmpty(body?.channelCode),
    utmSource: trimOrEmpty(body?.utmSource),
    referrer: trimOrEmpty(body?.referrer),
    // 购物车条目 ID：下单后后端**只清除这些条目**，没勾选的商品不会被顺手删掉
    cartItemIds: Array.isArray(body?.cartItemIds)
      ? body.cartItemIds.map(id => String(id)).filter(Boolean)
      : [],
  }

  if (addressId) {
    payload.addressId = addressId
  } else {
    payload.receiverName = receiverName
    payload.receiverPhone = receiverPhone
    payload.receiverProvince = trimOrEmpty(body?.receiverProvince)
    payload.receiverCity = trimOrEmpty(body?.receiverCity)
    payload.receiverDistrict = trimOrEmpty(body?.receiverDistrict)
    payload.receiverAddress = receiverAddress
    payload.receiverPostalCode = trimOrEmpty(body?.receiverPostalCode)
  }

  try {
    return await api<unknown>('/shop/order/store/submit', {
      method: 'POST',
      body: payload,
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '下单失败，请稍后重试',
    })
  }
})
