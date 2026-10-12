// server/api/products/[slug].get.ts
// [slug] 是占位符，这个文件对应 GET /api/products/:slug
//
// 店铺端商品详情（Nuxt BFF）。恒返回商品详情对象（StoreProductDetail）。
//
// 契约：后端商品 id 同时作为店铺端 slug，所以这里直接把 slug 当作 id 透传。
// 数据来源：Java 后端（经 createApiClient）
//   GET /shop/product/store/detail?id=<slug>&lang=<zh|en|fr>
//
// 页面用法见 nuxt-shop/composables/useProducts.ts 的 useStoreProductDetail()。

import type { StoreProductDetail } from '~~/types/shop-product'
import { normalizeDetail } from '~~/types/shop-product'
import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<StoreProductDetail> => {
  const id = getRouterParam(event, 'slug')?.trim()

  if (!id) {
    throw createError({ statusCode: 400, statusMessage: '缺少商品 id' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: false })

  let result: unknown
  try {
    result = await api<unknown>('/shop/product/store/detail', {
      method: 'GET',
      query: { id, lang },
    })
  } catch (error: any) {
    // 后端对"商品不存在"通常以业务错误（code!=200 / Result.error）返回，
    // apiClient 会抛 400；这里统一收敛成 404，方便前端展示"商品不存在"。
    const statusCode = error?.statusCode === 400 || error?.statusCode === 404 ? 404 : error?.statusCode || 502
    throw createError({
      statusCode,
      statusMessage: statusCode === 404 ? 'Product not found' : error?.statusMessage || '获取商品详情失败',
    })
  }

  const detail = normalizeDetail(result)
  if (!detail.id) {
    throw createError({ statusCode: 404, statusMessage: 'Product not found' })
  }

  return detail
})
