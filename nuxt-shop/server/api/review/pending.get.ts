// server/api/review/pending.get.ts
// GET /api/review/pending?orderNo=xxx —— 某笔订单里可以评价的明细
//
// 必须登录（评价资格是"买过的人"，匿名无从校验），未登录时 BFF 直接 401，
// 订单详情页据此静默隐藏「去评价」入口，而不是弹一个错误给游客看。

import type { StoreReviewableItem } from '~~/types/shop-review'
import { normalizeReviewableItems } from '~~/types/shop-review'
import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<StoreReviewableItem[]> => {
  const query = getQuery(event)
  const orderNo = String(query.orderNo ?? '').trim()
  if (!orderNo) {
    throw createError({ statusCode: 400, statusMessage: '缺少订单号' })
  }

  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })
  const result = await api<unknown>('/shop/review/store/pending', {
    method: 'GET',
    query: { orderNo },
  })
  return normalizeReviewableItems(result)
})
