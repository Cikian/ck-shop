// server/api/cart/items/[id].delete.ts
// DELETE /api/cart/items/:id —— 删除一条购物车明细
//
// 幂等：条目已被删除时后端返回当前购物车而不是报错，
// 前端重复点击、多标签页并发删除都不会看到莫名其妙的失败提示。

import type { StoreCart } from '~~/types/shop-cart'
import { normalizeCart } from '~~/types/shop-cart'
import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<StoreCart> => {
  const itemId = getRouterParam(event, 'id')?.trim()
  if (!itemId) {
    throw createError({ statusCode: 400, statusMessage: '缺少购物车明细 ID' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  const result = await api<unknown>('/shop/cart/store/remove', {
    method: 'POST',
    body: { itemId },
  })

  return normalizeCart(result)
})
