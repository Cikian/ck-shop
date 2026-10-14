// server/api/favorite/status.get.ts
// GET /api/favorite/status?productId=xxx —— 查询"我收藏这件商品没有 + 商品收藏总数"
//
// 商品详情页进入时调一次，用来点亮心形按钮、显示真实收藏数。
// 必须登录（收藏是用户维度的数据），未登录时 BFF 直接 401，
// 不把请求打到后端 —— 前端据此静默降级为"未收藏"（不弹错误，详情页对游客也要能看）。

import type { StoreFavoriteStatus } from '~~/types/shop-favorite'
import { normalizeFavoriteStatus } from '~~/types/shop-favorite'
import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<StoreFavoriteStatus> => {
  const query = getQuery(event)
  const productId = String(query.productId ?? '').trim()
  if (!productId) {
    throw createError({ statusCode: 400, statusMessage: '缺少商品 ID' })
  }

  // /api/* 路径没有语言前缀，i18n 中间件解析不出 event.context.locale，
  // 所以显式把 ?lang= 解析成受支持的语言 code（与 cart 的取法一致）。
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })
  const result = await api<unknown>('/shop/favorite/store/status', {
    method: 'GET',
    query: { productId },
  })
  return normalizeFavoriteStatus(result)
})
