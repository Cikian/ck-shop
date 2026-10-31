// server/api/region/countries.get.ts
// GET /api/region/countries —— 国家/地区全量列表 (BFF)
//
// 转发目标：Java 后端 GET /shop/region/countries
// 出参：RegionCountryVO[]（code / name / nameLocal / phoneCode / currency /
//        postalCodeFormat / postalCodeRegex），字段口径见 types/shop-region.ts
//
// 三个 region 路由（countries / states / cities）与 address/* 的差别只有三条，
// 写法和错误处理刻意与 server/api/address/list.get.ts 保持一致：
//   1) **不需要登录**（不传 withAuth）—— 国家/地区是公开参考数据，
//      未登录的用户也要能填地址（契约 §4 明确要求；验收标准 §6.5 会用裸 curl 打）；
//   2) 语言照旧走 lang —— BFF 统一发 x-language / Accept-Language，地名译文由后端现算；
//   3) 响应带 Cache-Control —— 这类数据几乎不变，别让每次打开地址弹层都回源。
//
// 关于缓存与语言的配合：响应内容随语言变化，但前端**永远**带 ?lang=（见
// composables/useRegionOptions.ts），语言已经在 URL 上，所以"按 URL 缓存"是安全的；
// 不带 ?lang 的请求一律按 event.context.locale 或 en 处理，同一 URL 结论恒定，也不会串语言。

import { resolveStoreLang } from '~~/types/store-locale'

export default defineEventHandler(async (event): Promise<unknown> => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  // 1 天：250 条国家级数据一年也变不了几次（契约 §4 的缓存表）
  setHeader(event, 'cache-control', 'public, max-age=86400')

  const api = createApiClient(event, { withAuth: false, lang })

  try {
    return await api<unknown>('/shop/region/countries', { method: 'GET' })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '国家/地区列表加载失败，请稍后重试',
    })
  }
})
