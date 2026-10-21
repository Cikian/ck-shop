// types/shop-review.ts
//
// 店铺端（nuxt-shop）商品评价相关类型 + 纯函数工具。
//
// 与 types/shop-product.ts / shop-favorite.ts / shop-cart.ts 同样的约定：
// 本文件刻意不依赖任何 Nuxt / Vue API —— 服务端路由用它做数据归一化，前端组件用它渲染。
//
// 数据来源（Java 后端，全部经 server/utils/apiClient.ts 的 createApiClient 调用）：
//   GET  /shop/review/store/summary?productId -> StoreReviewSummaryVO
//   GET  /shop/review/store/list?productId&pageNo&pageSize -> IPage<StoreReviewVO>
//   GET  /shop/review/store/pending?orderNo -> StoreReviewableItemVO[]
//   POST /shop/review/store/submit -> { review, summary }
//
// 三条硬规则（与后端契约 docs/真实统计与评价模块-接口契约.md 一致）：
//   1. **0 值不渲染**：reviewCount === 0 → 评价区整块不出现，绝不显示"0 分 0 条评价"；
//   2. **昵称由后端打码**，前端不再做二次处理；匿名评价 username 为 null，
//      显示哪个词由 i18n 决定（三语站点不能把中文"匿名用户"写死在接口里）；
//   3. **汇总与列表同源**：统计行的评分与评价区标题旁的条数都取自 summary，不各算一套。

import { clampInt, toInteger, toNullableString, toNumberOrNull } from './shop-product'

/** 单条评价（列表项） */
export interface StoreReview {
  id: string
  /** 1-5 星 */
  rating: number
  /** 文字内容；只打分没写字时为空串 */
  content: string
  /** 已打码的昵称；匿名评价为 null（由 i18n 显示「匿名用户」） */
  username: string | null
  anonymous: boolean
  /** 下单时的规格文案，可能为空串 */
  skuName: string
  /** yyyy-MM-dd HH:mm:ss（后端统一格式化），解析失败时为空串 */
  createTime: string
}

/** 星级分布项（1-5 星各自的条数） */
export interface StoreReviewBucket {
  rating: number
  count: number
}

/** 评价汇总：评分 + 条数 + 星级分布 */
export interface StoreReviewSummary {
  productId: string
  /** 平均评分 0.0-5.0；reviewCount === 0 时无意义（前端不渲染） */
  ratingAvg: number
  reviewCount: number
  /** 恒定 5 项（5→1），无评价的档位 count 为 0 */
  distribution: StoreReviewBucket[]
}

/** 分页结构：与项目其它列表接口保持一致（records / total / size / current / pages） */
export interface StoreReviewPage {
  records: StoreReview[]
  total: number
  size: number
  current: number
  pages: number
}

/** 详情页一次请求拿到的"汇总 + 首页列表" */
export interface StoreReviewOverview {
  summary: StoreReviewSummary
  page: StoreReviewPage
}

/** 待评价明细（订单详情页「去评价」入口） */
export interface StoreReviewableItem {
  orderItemId: string
  productId: string
  productName: string
  skuName: string
  productImage: string
  quantity: number
  /** true = 已经评价过（前端置灰「已评价」） */
  reviewed: boolean
}

/** 提交评价的返回：新建评价 + 商品最新汇总 */
export interface StoreReviewSubmitResult {
  review: StoreReview | null
  summary: StoreReviewSummary
}

const MAX_RATING = 5

/** 布尔归一化：Java 的 Boolean 经 JSON 可能是 true / 'true' / 'Y' / 1，只认这几种真值 */
function toBoolean(value: unknown): boolean {
  if (value === true || value === 1) return true
  return typeof value === 'string' && ['true', 'y', 'yes', '1'].includes(value.trim().toLowerCase())
}

/** 单条评价归一化（后端字段缺失/NULL 都不该让页面崩） */
export function normalizeReview(raw: unknown): StoreReview {
  const item = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
  return {
    id: toNullableString(item.id) ?? '',
    rating: clampInt(toInteger(item.rating, 0), 0, MAX_RATING),
    content: toNullableString(item.content) ?? '',
    username: toNullableString(item.username),
    anonymous: toBoolean(item.anonymous),
    skuName: toNullableString(item.skuName) ?? '',
    createTime: toNullableString(item.createTime) ?? '',
  }
}

/**
 * 汇总归一化。
 *
 * `distribution` 一律补成 5 项（5→1），并把条数夹到 >= 0：
 * 页面要画 5 行进度条，缺项就得在前端各处补位，补漏一次就是一条歪掉的长条。
 * 后端已经返回 5 项时这里只是原样通过。
 */
export function normalizeReviewSummary(raw: unknown): StoreReviewSummary {
  const item = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
  const rawBuckets = Array.isArray(item.distribution) ? item.distribution : []
  const byRating = new Map<number, number>()
  for (const bucket of rawBuckets) {
    const row = (bucket && typeof bucket === 'object' ? bucket : {}) as Record<string, unknown>
    const rating = toInteger(row.rating, 0)
    if (rating >= 1 && rating <= MAX_RATING) {
      byRating.set(rating, Math.max(toInteger(row.count, 0), 0))
    }
  }
  const distribution: StoreReviewBucket[] = []
  for (let star = MAX_RATING; star >= 1; star--) {
    distribution.push({ rating: star, count: byRating.get(star) ?? 0 })
  }
  const reviewCount = Math.max(toInteger(item.reviewCount, 0), 0)
  return {
    productId: toNullableString(item.productId) ?? '',
    // 没有评价时后端给 0；这里再挡一次 null/NaN，避免模板里出现 "null 分"
    ratingAvg: reviewCount > 0 ? (toNumberOrNull(item.ratingAvg) ?? 0) : 0,
    reviewCount,
    distribution,
  }
}

/** 评价分页归一化（MyBatis-Plus 的 IPage 结构） */
export function normalizeReviewPage(
  raw: unknown,
  fallbackPageNo = 1,
  fallbackPageSize = 10,
): StoreReviewPage {
  const item = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
  const records = Array.isArray(item.records) ? item.records.map(normalizeReview) : []
  return {
    records,
    total: Math.max(toInteger(item.total, records.length), 0),
    size: Math.max(toInteger(item.size, fallbackPageSize), 1),
    current: Math.max(toInteger(item.current, fallbackPageNo), 1),
    pages: Math.max(toInteger(item.pages, records.length > 0 ? 1 : 0), 0),
  }
}

/** 待评价明细列表归一化 */
export function normalizeReviewableItems(raw: unknown): StoreReviewableItem[] {
  if (!Array.isArray(raw)) return []
  return raw.map((entry) => {
    const item = (entry && typeof entry === 'object' ? entry : {}) as Record<string, unknown>
    return {
      orderItemId: toNullableString(item.orderItemId) ?? '',
      productId: toNullableString(item.productId) ?? '',
      productName: toNullableString(item.productName) ?? '',
      skuName: toNullableString(item.skuName) ?? '',
      productImage: toNullableString(item.productImage) ?? '',
      quantity: Math.max(toInteger(item.quantity, 1), 1),
      reviewed: toBoolean(item.reviewed),
    }
  })
}

/** 提交结果归一化（review 可能为 null：后端只保证 summary 一定有） */
export function normalizeReviewSubmitResult(raw: unknown): StoreReviewSubmitResult {
  const item = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
  const review = item.review ? normalizeReview(item.review) : null
  return {
    review,
    summary: normalizeReviewSummary(item.summary),
  }
}

/**
 * 星级渲染用的整数星级：四舍五入到最近的整数（4.5 → 5 颗实心星）。
 * 与 composables/useProductDetail.ts 里 ratingStars 的口径保持一致。
 */
export function reviewStars(ratingAvg: number | null | undefined): number {
  const value = toNumberOrNull(ratingAvg)
  if (value === null) return 0
  return clampInt(Math.round(value), 0, MAX_RATING)
}

/** 某星级占总数的百分比（用于分布进度条宽度）；总数为 0 时返回 0 而不是 NaN */
export function reviewBucketPercent(count: number, total: number): number {
  if (!total || total <= 0) return 0
  return clampInt(Math.round((count / total) * 100), 0, 100)
}
