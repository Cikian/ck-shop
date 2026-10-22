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
  /*
   * 必须是**整数**：以前只判 >=1，1.9 会被 Math.trunc 成 1 静默接受，
   * 而提示语却写着"必须是整数"——用户看到的数量与提交的意图不一致。
   * 这里直接拒绝非整数，并加上限（后端单行数量上限 999，与 DB/业务口径一致）。
   */
  if (!Number.isFinite(quantity) || !Number.isInteger(quantity) || quantity < 1) {
    throw createError({ statusCode: 400, statusMessage: '数量必须是大于 0 的整数' })
  }
  if (quantity > 999) {
    throw createError({ statusCode: 400, statusMessage: '单次最多购买 999 件' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  const result = await api<unknown>('/shop/cart/store/quantity', {
    method: 'POST',
    body: {
      itemId,
      quantity,
      source: body?.source ?? 'cart',
    },
  })

  return normalizeCart(result)
})
