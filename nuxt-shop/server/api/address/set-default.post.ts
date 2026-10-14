// server/api/address/set-default.post.ts
// POST /api/address/set-default?id=xxx —— 设为默认收货地址 (BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/address/set-default（String id）
//
// 【注意参数位置】后端 setDefault(String id, HttpServletRequest req) 没有 @RequestBody，
// id 必须走**查询参数**。
//
// 服务端会把该用户其它地址的默认标记清掉，保证"有且只有一个默认地址"。

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
    return await api<unknown>('/shop/order/store/address/set-default', {
      method: 'POST',
      query: { id },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '设置默认地址失败，请稍后重试',
    })
  }
})
