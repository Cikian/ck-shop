// server/api/address/[id].get.ts
// GET /api/address/:id —— 收货地址详情 (BFF)
//
// 转发目标：Java 后端 GET /shop/order/store/address/detail?id=xxx
//
// 【注意参数位置】后端 detail(String id, HttpServletRequest req) 没有 @RequestBody，
// id 必须走查询参数；同时 detail 只能查**自己的**地址（服务层带 userId 过滤），
// 拿别人的地址 ID 会得到「地址不存在或无权查看」。

import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<unknown> => {
  const id = getRouterParam(event, 'id')?.trim()
  if (!id) {
    throw createError({ statusCode: 400, statusMessage: '缺少地址 ID' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  try {
    return await api<unknown>('/shop/order/store/address/detail', {
      method: 'GET',
      query: { id },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '地址详情加载失败，请稍后重试',
    })
  }
})
