// server/api/order/preview.post.ts
// POST /api/order/preview —— 店铺端「结算预览」(BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/preview（ShopStoreOrderController#preview）
// 出参（OrderPreviewVO，字段名照后端 camelCase 原样透传，不做任何改写）：
//   { items: StoreOrderItemVO[], amount: OrderAmountVO,
//     priceToken, priceTokenExpireAt, hasInvalidItem, allAvailable }
//
// 为什么必须有这一步：
//   1) **金额由服务端算**。请求体里没有金额字段（OrderItemDTO 只有 商品/规格/数量），
//      所以「客户端改价」在接口层面就不存在；
//   2) 返回的 priceToken 是提交订单的必带项，服务端提交时会用它复核价格没变，
//      于是「页面上看到 100 元、实际扣 120 元」不可能发生。
//
// 不可结算的明细**不抛异常**：后端返回 200 + available=false + invalidReason，
// 让用户看到「哪一件没货了」，由结算页决定置灰哪一行、是否禁用提交。
//
// 登录态：订单必须知道「是谁的单」，所以统一 withAuth: true ——
// 未登录时 BFF 直接 401，不把请求打到后端。

import { resolveStoreLang } from '~~/types/store-locale'

/** 结算明细入参（与后端 OrderItemDTO 一一对应：只有商品/规格/数量，没有金额） */
interface PreviewItemBody {
  productId?: string
  skuId?: string | null
  quantity?: number
}

interface PreviewBody {
  items?: PreviewItemBody[]
  lang?: string
}

/**
 * 归一化结算明细。
 * 无规格商品的 skuId 必须是**空串**（后端契约），null / undefined 一律收敛成 ''，
 * 否则后端会把「没有规格」当成「规格不存在」而拒绝。
 */
function normalizeItems(rawItems: PreviewItemBody[] | undefined) {
  if (!Array.isArray(rawItems) || rawItems.length === 0) {
    throw createError({ statusCode: 400, statusMessage: '请选择要结算的商品' })
  }

  return rawItems.map((item, index) => {
    const productId = item?.productId ? String(item.productId).trim() : ''
    if (!productId) {
      throw createError({ statusCode: 400, statusMessage: `第 ${index + 1} 行缺少商品 ID` })
    }
    const quantity = Number(item?.quantity)
    if (!Number.isFinite(quantity) || quantity < 1) {
      throw createError({ statusCode: 400, statusMessage: `第 ${index + 1} 行数量必须是大于 0 的整数` })
    }
    return {
      productId,
      skuId: item?.skuId ? String(item.skuId).trim() : '',
      quantity: Math.trunc(quantity),
    }
  })
}

export default defineEventHandler(async (event): Promise<unknown> => {
  const body = await readBody<PreviewBody>(event)
  const items = normalizeItems(body?.items)

  // /api/* 路径没有语言前缀，i18n 中间件解析不出 event.context.locale，
  // 所以显式把 ?lang= 解析成受支持的语言 code（与 cart 的取法一致）。
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  try {
    return await api<unknown>('/shop/order/store/preview', {
      method: 'POST',
      body: { items },
    })
  } catch (error: any) {
    // 后端业务异常 message 是中文，原样透出给前端展示，不做二次改写
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '结算预览失败，请稍后重试',
    })
  }
})
