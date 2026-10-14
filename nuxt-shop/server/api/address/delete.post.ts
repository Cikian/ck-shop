// server/api/address/delete.post.ts
// POST /api/address/delete?id=xxx —— 删除收货地址（软删除）(BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/address/delete（String id）
//
// 【注意参数位置】后端 delete(String id, HttpServletRequest req) 没有 @RequestBody，
// id 必须走**查询参数**。放进 body 后端读不到，会变成"缺少地址 ID"。
//
// 后端是软删除（del_flag），并且只删自己的地址。

import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<unknown> => {
  const query = getQuery(event)
  const id = String(query.id ?? '').trim()
  if (!id) {
    throw createError({ statusCode: 400, statusMessage: '缺少地址 ID' })
  }

  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  try {
    return await api<unknown>('/shop/order/store/address/delete', {
      method: 'POST',
      query: { id },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '地址删除失败，请稍后重试',
    })
  }
})
