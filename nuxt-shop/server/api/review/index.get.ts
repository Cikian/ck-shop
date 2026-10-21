// server/api/review/index.get.ts
// GET /api/review?productId=xxx&pageNo=1&pageSize=10&lang=zh
//   → { summary, page }
//
// 为什么把"汇总"和"首页列表"并成一个路由：
// 商品详情页一进来就要同时显示「4.5 分 · 12 条评价」和下面 10 条评价，
// 拆成两个请求只会让首屏多一次 RTT，还可能出现"评分已经更新、列表还是旧的"的闪动。
// 两个后端接口在这里**并行**发出，取回后各自归一化（任一失败都按空数据降级，
// 评价区不渲染总比整页 500 好）。

import type { StoreReviewOverview } from '~~/types/shop-review'
import { clampInt, toPositiveInt } from '~~/types/shop-product'
import { normalizeReviewPage, normalizeReviewSummary } from '~~/types/shop-review'
import { resolveStoreLang } from '~~/types/store-locale'

/** 与项目其它分页接口保持同一套口径：默认 10 条，上限 50（与后端 Math.min 对齐） */
const DEFAULT_PAGE_SIZE = 10
const MAX_PAGE_SIZE = 50

export default defineEventHandler(async (event): Promise<StoreReviewOverview> => {
  const query = getQuery(event)
  const productId = String(query.productId ?? '').trim()
  if (!productId) {
    throw createError({ statusCode: 400, statusMessage: '缺少商品 ID' })
  }
  const safePageNo = toPositiveInt(query.pageNo, 1)
  const safePageSize = clampInt(toPositiveInt(query.pageSize, DEFAULT_PAGE_SIZE), 1, MAX_PAGE_SIZE)

  // /api/* 没有语言前缀，i18n 中间件解析不出 event.context.locale，这里显式解析 ?lang=
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  // 游客也要能看评价：不带 withAuth，避免未登录时 BFF 侧就 401
  const api = createApiClient(event, { lang })

  const [summaryRaw, pageRaw] = await Promise.all([
    api<unknown>('/shop/review/store/summary', { method: 'GET', query: { productId } })
      .catch(() => null),
    api<unknown>('/shop/review/store/list', {
      method: 'GET',
      query: { productId, pageNo: safePageNo, pageSize: safePageSize },
    }).catch(() => null),
  ])

  return {
    summary: normalizeReviewSummary(summaryRaw),
    page: normalizeReviewPage(pageRaw, safePageNo, safePageSize),
  }
})
