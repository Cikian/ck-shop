// server/api/cart/invalid.delete.ts
// DELETE /api/cart/invalid —— 一键清理失效商品
//
// "失效"由后端判定：下架 / 删除 / 不属于本店 / SKU 停售或规格变更 / 无货。
// 注意"库存不足"不算失效（东西还在，只是买不了那么多），因此不会被这里清掉。

import type { StoreCart } from '~~/types/shop-cart'
import { normalizeCart } from '~~/types/shop-cart'
import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<StoreCart> => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  const result = await api<unknown>('/shop/cart/store/clear-invalid', { method: 'POST' })

  return normalizeCart(result)
})
