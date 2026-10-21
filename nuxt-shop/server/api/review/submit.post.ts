// server/api/review/submit.post.ts
// POST /api/review/submit { orderItemId, rating, content, anonymous }
//   → { review, summary }
//
// 入参只允许这四项：**哪一行订单明细**、几分、写什么、是否匿名。
// productId / storeId / userId 一律由后端从订单明细反查 —— 前端传了也不认，
// 否则任何人都能给别店商品刷 5 星。
//
// 校验（1-5 星、500 字上限）在 BFF 与 Java 两侧各做一遍：BFF 这一遍是为了
// 用户少等一个 RTT 就能看到"请选择星级"，Java 那一遍才是真正的守门人（接口可被直接调用）。

import type { StoreReviewSubmitResult } from '~~/types/shop-review'
import { normalizeReviewSubmitResult } from '~~/types/shop-review'
import { resolveStoreLang } from '~~/types/store-locale'

const MAX_CONTENT_LENGTH = 500

export default defineEventHandler(async (event): Promise<StoreReviewSubmitResult> => {
  const body = await readBody(event).catch(() => null) as Record<string, unknown> | null
  const orderItemId = String(body?.orderItemId ?? '').trim()
  if (!orderItemId) {
    throw createError({ statusCode: 400, statusMessage: '缺少订单明细ID' })
  }
  const rating = Number(body?.rating)
  if (!Number.isInteger(rating) || rating < 1 || rating > 5) {
    throw createError({ statusCode: 400, statusMessage: '请选择 1-5 星评分' })
  }
  const content = String(body?.content ?? '').trim()
  if (content.length > MAX_CONTENT_LENGTH) {
    throw createError({ statusCode: 400, statusMessage: `评价内容不能超过 ${MAX_CONTENT_LENGTH} 字` })
  }

  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })
  const result = await api<unknown>('/shop/review/store/submit', {
    method: 'POST',
    body: {
      orderItemId,
      rating,
      // 空内容传 null 而不是空串：后端用 trimToNull 判断"只打分不写字"
      content: content.length > 0 ? content : null,
      anonymous: body?.anonymous === true,
    },
  })
  return normalizeReviewSubmitResult(result)
})
