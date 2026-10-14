// server/api/cart/index.delete.ts
// DELETE /api/cart —— 清空当前登录用户在当前店铺的购物车
//
// 数据来源：Java 后端 POST /shop/cart/store/clear
// 返回：清空后的完整购物车（空列表 + 空汇总），前端据此直接刷新界面，无需再查一次。

import type { StoreCart } from '~~/types/shop-cart'
import { normalizeCart } from '~~/types/shop-cart'
import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<StoreCart> => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  const result = await api<unknown>('/shop/cart/store/clear', { method: 'POST' })

  return normalizeCart(result)
})
