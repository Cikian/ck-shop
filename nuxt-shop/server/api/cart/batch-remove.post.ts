// server/api/cart/batch-remove.post.ts
// POST /api/cart/batch-remove —— 批量删除
//
// body: { ids: string[] }
//
// 空数组会被后端明确拒绝，而不是被解释成"删除全部"——
// 前端状态错乱时静默清空整个购物车是不可接受的破坏性行为。

import type { StoreCart } from '~~/types/shop-cart'
import { normalizeCart } from '~~/types/shop-cart'
import { resolveStoreLang } from '~~/types/store-locale'

interface BatchBody {
  ids?: string[]
  source?: string
  lang?: string
}

/**
 * 单次批量删除的条目上限。
 *
 * 不设上限时，请求体里塞几万个 id 会被原样转发给后端：拼接成超长 IN / foreach，
 * 轻则一次慢查询，重则打满数据库连接。正常用户一次最多也就选几十条，
 * 200 已经非常宽松。
 */
const MAX_BATCH_IDS = 200

export default defineEventHandler(async (event): Promise<StoreCart> => {
  const body = await readBody<BatchBody>(event)
  const ids = Array.isArray(body?.ids)
    ? body.ids.map(id => String(id).trim()).filter(Boolean)
    : []
  if (!ids.length) {
    throw createError({ statusCode: 400, statusMessage: '请先选择要删除的商品' })
  }
  if (ids.length > MAX_BATCH_IDS) {
    throw createError({
      statusCode: 400,
      statusMessage: `一次最多删除 ${MAX_BATCH_IDS} 项，请分批操作`,
    })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  const result = await api<unknown>('/shop/cart/store/batch-remove', {
    method: 'POST',
    body: { ids, source: body?.source ?? 'cart' },
  })

  return normalizeCart(result)
})
