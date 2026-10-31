// server/api/region/cities.get.ts
// GET /api/region/cities?country=TH&stateCode=TH-10&keyword=&limit=200 —— 城市列表 (BFF)
//
// 转发目标：Java 后端 GET /shop/region/cities
// 出参：RegionCityVO[]（id / name / nameLocal / population），已按 population DESC, name 排序
//
// 【为什么一定要有 limit + keyword】
// 菲律宾 4,374 个城市、泰国 1,012 个（契约 §5.2），一次铺开既渲染不动也没法用：
//   · 不带 keyword → 取人口前 200 条当首屏；
//   · 用户输入关键词 → 带 keyword 重新取，结果仍在排序后截 200 条（不做分页）。
// limit 的硬上限 500 是契约 §3.3 的约定，这里**必须**夹紧：
// 不夹的话前端（或任何一个裸 curl）传 limit=999999 就等于让后端把 15 万行吐出来。
//
// 缓存 60 秒而不是 1 天：城市表的索引和排序规则将来可能微调，
// 而且关键词组合极多，缓存太久只会把 CDN 塞满长尾 URL（契约 §4 指定 60s）。

import { clampInt, toPositiveInt } from '~~/types/shop-product'
import { resolveStoreLang } from '~~/types/store-locale'

/** 默认条数（契约 §3.3：默认 200） */
const DEFAULT_LIMIT = 200
/** 硬上限（契约 §3.3：硬上限 500） */
const MAX_LIMIT = 500
/** 关键词长度上限：模糊查询走 LIKE，超长输入纯属浪费索引扫描 */
const MAX_KEYWORD_LENGTH = 64

function toQueryString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

export default defineEventHandler(async (event): Promise<unknown> => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const country = toQueryString(query.country)
  const stateCode = toQueryString(query.stateCode)
  const keyword = toQueryString(query.keyword).slice(0, MAX_KEYWORD_LENGTH)
  const limit = clampInt(toPositiveInt(query.limit, DEFAULT_LIMIT), 1, MAX_LIMIT)

  setHeader(event, 'cache-control', 'public, max-age=60')

  const api = createApiClient(event, { withAuth: false, lang })

  try {
    return await api<unknown>('/shop/region/cities', {
      method: 'GET',
      query: {
        // country 为空时照样把空串交给后端（后端会回空数组），不在 BFF 造 400 ——
        // 与 states 一致，把"没选国家"表达成空结果比表达成错误更适合下拉框场景
        country,
        ...(stateCode ? { stateCode } : {}),
        ...(keyword ? { keyword } : {}),
        limit,
      },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '城市列表加载失败，请稍后重试',
    })
  }
})
