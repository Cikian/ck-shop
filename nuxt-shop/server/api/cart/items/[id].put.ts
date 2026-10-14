// server/api/cart/items/[id].put.ts
// PUT /api/cart/items/:id —— 修改购物车数量
//
// body: { quantity: number, source?: string }
// :id 是购物车明细 ID（不是商品 ID）。后端会校验该明细属于当前用户 + 当前店铺，
// 拿别人的明细 ID 来改会被拒绝。

import type { StoreCart } from '~~/types/shop-cart'
import { normalizeCart } from '~~/types/shop-cart'
import { resolveStoreLang } from '~~/types/store-locale'

interface QuantityBody {
  quantity?: number
  source?: string
  lang?: string
}

export default defineEventHandler(async (event): Promise<StoreCart> => {
  const itemId = getRouterParam(event, 'id')?.trim()
  if (!itemId) {
    throw createError({ statusCode: 400, statusMessage: '缺少购物车明细 ID' })
  }

  const body = await readBody<QuantityBody>(event)
  const quantity = Number(body?.quantity)
  if (!Number.isFinite(quantity) || quantity < 1) {
    throw createError({ statusCode: 400, statusMessage: '数量必须是大于 0 的整数' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  const result = await api<unknown>('/shop/cart/store/quantity', {
    method: 'POST',
    body: {
      itemId,
      quantity: Math.trunc(quantity),
      source: body?.source ?? 'cart',
    },
  })

  return normalizeCart(result)
})
