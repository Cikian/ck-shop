// server/api/cart/items.post.ts
// POST /api/cart/items —— 加入购物车
//
// body: { productId: string, skuId?: string, quantity?: number, source?: string }
//
// 契约要点（与后端一致，前端不必重复实现）：
//   - **主体是 SKU**：商品有规格时 skuId 必填，缺失会被后端明确拒绝；
//   - 无规格商品必须不传 skuId（传了同样会被拒绝）；
//   - 数量超过真实库存时后端返回可读的业务错误（"最多还能加 N 件"），
//     BFF 原样抛出，前端直接展示，不做二次改写。

import type { StoreCart } from '~~/types/shop-cart'
import { normalizeCart } from '~~/types/shop-cart'
import { resolveStoreLang } from '~~/types/store-locale'

interface AddBody {
  productId?: string
  skuId?: string | null
  quantity?: number
  source?: string
  lang?: string
}

export default defineEventHandler(async (event): Promise<StoreCart> => {
  const body = await readBody<AddBody>(event)
  const productId = body?.productId?.trim()
  if (!productId) {
    throw createError({ statusCode: 400, statusMessage: '缺少商品 ID' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  // 与修改数量接口同一口径：必须是 1..999 的整数（否则后端会静默截断或报错）
  const quantity = Number(body?.quantity ?? 1)
  if (!Number.isFinite(quantity) || !Number.isInteger(quantity) || quantity < 1) {
    throw createError({ statusCode: 400, statusMessage: '数量必须是大于 0 的整数' })
  }
  if (quantity > 999) {
    throw createError({ statusCode: 400, statusMessage: '单次最多购买 999 件' })
  }

  const api = createApiClient(event, { withAuth: true, lang })

  const result = await api<unknown>('/shop/cart/store/add', {
    method: 'POST',
    body: {
      productId,
      // 无规格商品必须传空串（后端契约）；null / undefined 统一收敛成空串
      skuId: body?.skuId ? String(body.skuId).trim() : '',
      quantity,
      source: body?.source ?? 'api',
    },
  })

  return normalizeCart(result)
})
