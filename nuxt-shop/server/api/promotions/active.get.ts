// server/api/promotions/active.get.ts
// 对应 GET /api/promotions/active
//
// 店铺端首页「进行中的促销 / 秒杀场次」（Nuxt BFF）。
// 数据来源：Java 后端 GET /shop/promotion/store/active（按店铺隔离，只返回进行中的活动）
//
// 路径为什么带 /store：管理端 CRUD 占用 /shop/promotion/**，
// 店铺端（免登录）统一放 /shop/promotion/store/**，
// 与既有约定 /shop/product/list（后台） vs /shop/product/store/list（店铺端）保持一致。
//
// 【设计原则】首页的秒杀条**只有**在后端真的给出进行中的活动时才渲染。
// 「限时秒杀 02:34:17」这种倒计时如果在前端写死，就是虚构活动时间 ——
// 既骗用户，也踩《价格法》《广告法》的合规红线（见 docs/store-redesign/后端促销字段清单.md 第 8 节）。
// 倒计时完全由活动自身的 end_time 驱动。
//
// 契约详见 docs/store-redesign/促销活动管理-接口契约.md 第 4 节。

import { toNumberOrNull } from '~~/types/shop-product'
import { resolveStoreLang } from '~~/types/store-locale'

/**
 * 店铺端「进行中的促销活动」。
 *
 * 字段与 ck_shop_promotion（+ 翻译表）对齐，**只保留前端渲染真正需要的部分**：
 * 写死一堆用不到的字段，只会让"后端返回了什么"和"页面显示了什么"越来越难对上。
 */
export interface ShopActivePromotion {
  id: string
  /** SECKILL-秒杀 / DIRECT-直降 / PERCENT-折扣 / FULL_CUT-满减（对应 ck_shop_promotion.type） */
  type: string
  /**
   * 活动名，由后端按 `?lang=` 解析（翻译表 → 回退 zh → 回退主表）；
   * 空串时前端回退到通用的「限时优惠」。
   */
  title: string
  /** 副标题 / 宣传语；可空 */
  subTitle?: string | null
  /**
   * 秒杀条背景图 URL（`ck_shop_promotion.bg_image`）。
   * 为空时前端用品牌色渐变兜底 —— 与「没有数据就不编内容」是同一条原则。
   */
  bgImage?: string | null
  /** 活动开始时间（ISO 字符串）。用于「还没开始的活动不渲染」；可空 */
  startTime?: string | null
  /** 活动结束时间（ISO 字符串）—— 倒计时的**唯一**依据，缺了就不渲染整条 */
  endTime: string
  /**
   * 点击「查看全部」的去向（`ck_shop_promotion.link_url`，不含语言前缀）。
   * 为空时前端**不渲染入口**，避免做出一个点不动或跳回首页的假按钮。
   */
  linkUrl?: string | null
  /** 活动主推商品 id（可选） */
  productId?: string | null
  /** 活动关联的商品总数（「查看更多」文案用）；没有则 0 */
  productCount?: number
  /**
   * 满减门槛（FULL_CUT 用，对应 ck_shop_promotion.threshold_amount）。
   * 这是购物车「凑单进度条」的**唯一**依据：读不到门槛就不渲染进度卡，
   * 绝不前端写死「满 300 减 30」这种规则。
   */
  thresholdAmount?: number | null
  /** 直降金额 / 折扣率（对应 discount_value） */
  discountValue?: number | null
}

function asRecord(value: unknown): Record<string, any> {
  return value && typeof value === 'object' ? (value as Record<string, any>) : {}
}

function asNullableString(value: unknown): string | null {
  if (value === null || value === undefined) return null
  const str = String(value).trim()
  return str.length ? str : null
}

/**
 * 归一化单条活动。
 *
 * 缺 id 或缺 endTime 的一律丢弃：这两样是「能渲染出什么」的硬前提
 * （id 用于 key，endTime 用于倒计时），宁可少渲染一条，也不渲染一条坏数据。
 */
function normalizePromotion(raw: unknown): ShopActivePromotion | null {
  const item = asRecord(raw)
  const id = asNullableString(item.id)
  const endTime = asNullableString(item.endTime)
  if (!id || !endTime) return null

  const productCount = toNumberOrNull(item.productCount)

  return {
    id,
    type: asNullableString(item.type) ?? '',
    title: asNullableString(item.title) ?? '',
    subTitle: asNullableString(item.subTitle),
    bgImage: asNullableString(item.bgImage),
    startTime: asNullableString(item.startTime),
    endTime,
    linkUrl: asNullableString(item.linkUrl),
    productId: asNullableString(item.productId),
    productCount: productCount === null ? 0 : Math.trunc(productCount),
    thresholdAmount: toNumberOrNull(item.thresholdAmount),
    discountValue: toNumberOrNull(item.discountValue),
  }
}

export default defineEventHandler(async (event): Promise<ShopActivePromotion[]> => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: false })

  try {
    const result = await api<unknown>('/shop/promotion/store/active', {
      method: 'GET',
      query: { lang },
    })
    return Array.isArray(result)
      ? result.map(normalizePromotion).filter((item): item is ShopActivePromotion => item !== null)
      : []
  } catch {
    // 促销是"锦上添花"的数据：接口还没上线或临时挂了，都不该让整个首页报错。
    // 返回空数组，首页自然退化成「分类 + 轮播 + 商品流」的干净版本。
    // （对比 /api/categories：分类是首页骨架，缺了就没法逛，所以那边是抛错的。）
    return []
  }
})
