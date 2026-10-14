// server/api/products.get.ts
// 文件路径决定 URL：这个文件对应 GET /api/products
//
// 店铺端商品列表（Nuxt BFF）。恒返回**单一稳定结构**：
//   { records: StoreProductCard[], total, size, current, pages }
// 即 Java 后端 /shop/product/store/list 的 IPage 直出结果（字段已归一化），
// 不做"按调用方式切换返回结构"的双形态接口。
//
// 数据来源：Java 后端（经 createApiClient，自动签名/语言透传/解包 result）
//   GET /shop/product/store/list
//   query: lang, pageNo, pageSize, categoryId?, keyword?
//
// 页面用法见 nuxt-shop/composables/useProducts.ts 的 useStoreProductList()。

import type { StoreProductPage } from '~~/types/shop-product'
import { clampInt, normalizePage, toOptionalQueryString, toPositiveInt } from '~~/types/shop-product'
import { resolveStoreLang } from '~~/types/store-locale'

const DEFAULT_PAGE_NO = 1
const DEFAULT_PAGE_SIZE = 12
const MAX_PAGE_SIZE = 60

/**
 * 排序白名单。
 * 必须与 Java 后端 queryStorePageList 的 sortBy 取值一一对应；
 * 这里显式过滤，任何未知取值都会被丢弃（后端走默认排序），
 * 避免把用户输入原样透传到下游查询。
 */
const ALLOWED_SORT_BY = new Set(['sales', 'newest', 'price_asc', 'price_desc'])

export default defineEventHandler(async (event): Promise<StoreProductPage> => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale

  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')
  const pageNo = toPositiveInt(query.pageNo, DEFAULT_PAGE_NO)
  const pageSize = clampInt(toPositiveInt(query.pageSize, DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE)
  const categoryId = toOptionalQueryString(query.categoryId)
  const keyword = toOptionalQueryString(query.keyword)

  const rawSortBy = toOptionalQueryString(query.sortBy)
  const sortBy = rawSortBy && ALLOWED_SORT_BY.has(rawSortBy) ? rawSortBy : undefined

  const api = createApiClient(event, { withAuth: false })

  try {
    const result = await api<unknown>('/shop/product/store/list', {
      method: 'GET',
      query: {
        lang,
        pageNo,
        pageSize,
        ...(categoryId ? { categoryId } : {}),
        ...(keyword ? { keyword } : {}),
        ...(sortBy ? { sortBy } : {}),
      },
    })

    return normalizePage(result, pageNo, pageSize)
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 502,
      statusMessage: error?.statusMessage || '获取商品列表失败',
    })
  }
})
