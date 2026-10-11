// server/api/categories.get.ts
// 对应 GET /api/categories
//
// 店铺端分类树（Nuxt BFF）。
// 数据来源：Java 后端 /shop/store/category/tree
//   —— 后端已按**当前店铺**过滤好（全局共享节点 + 本店专属节点），
//      并且每个节点带 "含子分类的本店已上架商品数"。
// 前端不做也无法做店铺过滤：店铺身份由本项目服务端配置（STORE_CODE）
// 通过 X-Store-Code 请求头传给后端，浏览器不感知、也改不了。

import { resolveStoreLang } from '~~/types/shop-product'

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: false })

  try {
    const result = await api<unknown>('/shop/store/category/tree', {
      method: 'GET',
      query: { lang },
    })
    return Array.isArray(result) ? result : []
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 502,
      statusMessage: error?.statusMessage || '获取分类树失败',
    })
  }
})
