// server/api/cart/count.get.ts
// GET /api/cart/count —— 购物车角标（条目数 + 件数）
//
// 顶部导航每个页面都要显示这个数字，因此单独走一条只做聚合的轻量接口，
// 不加载购物车明细、不做商品 JOIN。

import type { StoreCartCount } from '~~/types/shop-cart'
import { normalizeCartCount } from '~~/types/shop-cart'

export default defineEventHandler(async (event): Promise<StoreCartCount> => {
  const api = createApiClient(event, { withAuth: true })
  const result = await api<unknown>('/shop/cart/store/count', { method: 'GET' })
  return normalizeCartCount(result)
})
