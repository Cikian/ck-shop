// server/api/cart/index.get.ts
// GET /api/cart —— 当前登录用户在当前店铺的购物车（BFF）
//
// 数据来源：Java 后端 GET /shop/cart/store/list
//
// 这里刻意用 withAuth: true：
//   1) 未登录时直接在 BFF 层抛 401，不把请求打到 Java 后端，省一次无效往返；
//   2) token 从 httpOnly cookie 读取，浏览器 JS 全程接触不到 token。
//
// 语言处理：/api/* 路径没有语言前缀，i18n 中间件解析不出 event.context.locale，
// 所以显式把 ?lang= 解析成受支持的语言 code，并交给 createApiClient 透传
// （商品标题、规格文案都要按当前语言返回）。

import type { StoreCart } from '~~/types/shop-cart'
import { normalizeCart } from '~~/types/shop-cart'
import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<StoreCart> => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  const result = await api<unknown>('/shop/cart/store/list', {
    method: 'GET',
    query: { lang },
  })

  return normalizeCart(result)
})
