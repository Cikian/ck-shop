// server/api/favorite/toggle.post.ts
// POST /api/favorite/toggle —— 收藏 / 取消收藏（取反）
//
// body: { productId: string, lang?: string }
//
// 返回**操作后**的最终状态（favorited + favoriteCount），前端直接照着渲染，
// 不需要自己取反、也不需要自己加减收藏数 —— 那样在并发或重复点击时必然显示错。
//
// 幂等性由后端保证（重复收藏同一商品不会写出两条关系），BFF 只做入参校验与语言透传。

import type { StoreFavoriteStatus } from '~~/types/shop-favorite'
import { normalizeFavoriteStatus } from '~~/types/shop-favorite'
import { resolveStoreLang } from '~~/types/store-locale'

interface ToggleBody {
  productId?: string
  lang?: string
}

export default defineEventHandler(async (event): Promise<StoreFavoriteStatus> => {
  const body = await readBody<ToggleBody>(event)
  const productId = body?.productId?.trim()
  if (!productId) {
    throw createError({ statusCode: 400, statusMessage: '缺少商品 ID' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })
  const result = await api<unknown>('/shop/favorite/store/toggle', {
    method: 'POST',
    body: { productId },
  })
  return normalizeFavoriteStatus(result)
})
