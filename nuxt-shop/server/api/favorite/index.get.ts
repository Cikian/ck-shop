// server/api/favorite/index.get.ts
// GET /api/favorite?pageNo=1&pageSize=12&lang=zh —— 我的收藏（分页）
//
// 出参与商品列表**完全一致**（records / total / size / current / pages），
// 因此归一化直接复用 types/shop-product.ts 的 normalizePage，
// 前端也能用同一套卡片组件渲染 —— 收藏列表里的商品和商品列表里的商品长得一样，
// 不搞第二套卡片。
//
// 已下架 / 已删除的商品由后端在查询时直接过滤掉（不会出现在 records 里），
// 所以这里不需要"失效条目"的额外处理。

import type { StoreProductPage } from '~~/types/shop-product'
import { clampInt, normalizePage, toPositiveInt } from '~~/types/shop-product'
import { resolveStoreLang } from '~~/types/store-locale'

// 与商品列表、订单列表保持同一套口径：默认 12、上限 60。
// 这里此前只校验了「>= 1」，没有上限 —— 调用方传 pageSize=100000
// 就能让后端一次性把整张收藏表拉出来（DoS 与慢查询的入口）。
const DEFAULT_PAGE_SIZE = 12
const MAX_PAGE_SIZE = 60

export default defineEventHandler(async (event): Promise<StoreProductPage> => {
  const query = getQuery(event)
  const safePageNo = toPositiveInt(query.pageNo, 1)
  const safePageSize = clampInt(toPositiveInt(query.pageSize, DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE)

  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })
  const result = await api<unknown>('/shop/favorite/store/list', {
    method: 'GET',
    query: { pageNo: safePageNo, pageSize: safePageSize },
  })
  return normalizePage(result, safePageNo, safePageSize)
})
