// server/api/home/products.get.ts
//
// 首页商品聚合接口（清单 S21）。
//
// 背景：首页原本要发两次 /api/products ——
//   · 热销横排：pageSize=6&sortBy=sales
//   · 主商品流：pageSize=18（后端默认排序）
// 两次请求在 SSR 里就是两次后端往返；更糟的是主列表还要在客户端剔除热销商品，
// 于是"18 件"里实际只剩 12 件（重叠 6 件），用户看到的第一屏比预期少。
//
// 现在：一次请求、服务端并行取两份数据，并且主列表**多取 hotSize 条**，
// 剔除重叠后仍然有 pageSize 条。第二页起仍走 /api/products（不需要热销）。
//
// 返回：{ hot: StoreProductPage, main: StoreProductPage }

import type { StoreProductPage } from '~~/types/shop-product'
import { clampInt, normalizePage, toOptionalQueryString, toPositiveInt } from '~~/types/shop-product'
import { resolveStoreLang } from '~~/types/store-locale'

const DEFAULT_PAGE_SIZE = 18
const DEFAULT_HOT_SIZE = 6
const MAX_PAGE_SIZE = 60
const MAX_HOT_SIZE = 12

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale

  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')
  const pageNo = toPositiveInt(query.pageNo, 1)
  const pageSize = clampInt(toPositiveInt(query.pageSize, DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE)
  const hotSize = clampInt(toPositiveInt(query.hotSize, DEFAULT_HOT_SIZE), 1, MAX_HOT_SIZE)
  const categoryId = toOptionalQueryString(query.categoryId)

  const api = createApiClient(event, { withAuth: false })

  const fetchPage = async (extra: Record<string, unknown>): Promise<StoreProductPage> => {
    const result = await api<unknown>('/shop/product/store/list', {
      method: 'GET',
      query: {
        lang,
        ...(categoryId ? { categoryId } : {}),
        ...extra,
      },
    })
    return normalizePage(result, Number(extra.pageNo) || 1, Number(extra.pageSize) || pageSize)
  }

  try {
    const [hot, main] = await Promise.all([
      fetchPage({ pageNo: 1, pageSize: hotSize, sortBy: 'sales' }),
      // 多取 hotSize 条：前端剔除热销重叠后仍是 pageSize 条
      fetchPage({ pageNo, pageSize: pageSize + hotSize }),
    ])

    return { hot, main }
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 502,
      statusMessage: error?.statusMessage || '获取首页商品失败',
    })
  }
})
