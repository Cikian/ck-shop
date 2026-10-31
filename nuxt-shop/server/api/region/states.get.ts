// server/api/region/states.get.ts
// GET /api/region/states?country=TH —— 某国的一级行政区（省/州/府）(BFF)
//
// 转发目标：Java 后端 GET /shop/region/states
// 出参：RegionStateVO[]（id / code / name / nameLocal / type）
//
// 【空数组是正常结果，不是错误】契约 §3.2：country 为空或非法时后端回空数组
// （前端首次进页面时还不知道选哪个国家）；"该国确实没有州级"也回空数组 ——
// 前端据此**跳过州省这一级**直接选城市（§5.2）。所以这里不要把空数组改造成错误。
//
// 缓存与语言的处理同 countries.get.ts，不重复解释。states 5,327 行，按国家切片后
// 一个国家的数据更不可能变，仍然给 1 天。

import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<unknown> => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  // 国家码只做"非空才传"：留空就让后端走它自己的空结果分支，前端不必在这里拦截
  const country = typeof query.country === 'string' ? query.country.trim() : ''

  setHeader(event, 'cache-control', 'public, max-age=86400')

  const api = createApiClient(event, { withAuth: false, lang })

  try {
    return await api<unknown>('/shop/region/states', {
      method: 'GET',
      query: country ? { country } : {},
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '省/州列表加载失败，请稍后重试',
    })
  }
})
