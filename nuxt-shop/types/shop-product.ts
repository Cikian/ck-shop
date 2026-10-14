// types/shop-product.ts
//
// 店铺端（nuxt-shop）商品相关类型 + 纯函数工具。
//
// 这个文件刻意不依赖任何 Nuxt / Vue API：
//   1) 服务端路由（server/api/products*.ts）用它做数据归一化；
//   2) 前端页面 / 组件用它做规格组合置灰计算；
//   3) Node 可以直接 import 它跑本地自测：
//        nuxt-shop/temp/spec-availability.selfcheck.ts
//
// 数据来源（Java 后端，全部通过 server/utils/apiClient.ts 的 createApiClient 调用）：
//   GET /shop/product/store/list?lang&pageNo&pageSize&categoryId&keyword
//        -> JeecgBoot 信封 .result = { records, total, size, current, pages }
//   GET /shop/product/store/detail?id&lang
//        -> JeecgBoot 信封 .result = StoreProductDetail
// 字段以后端 VO 为准：StoreProductCardVO / StoreProductDetailVO / StoreSpecVO /
// StoreSpecValueVO / StoreSkuVO / TagRefVO + ShopProduct / ShopProductImage。

// 语言清单不再写在本文件里（曾经 3 处手工同步），统一从 types/store-locale.ts 派生。
// 这里 import 时带 .ts 后缀，是为了让 Node 的 TS 类型擦除模式也能解析
// （temp/spec-availability.selfcheck.ts 直接 `node xxx.ts` 跑，Node 的 ESM 不做后缀补全）。
import { STORE_DEFAULT_LANG, STORE_INTL_LOCALE_TAGS } from './store-locale.ts'

/** 后端统一的 Y/N 标记 */
export type YesNo = 'Y' | 'N'

/** 标签简要信息（TagRefVO） */
export interface ShopTagRef {
  id: string
  code: string
  name: string
}

/** 商品卡片（列表页 StoreProductCardVO） */
export interface StoreProductCard {
  /** 商品ID，同时作为店铺端 slug */
  id: string
  spuCode: string | null
  /** 当前语言标题（缺失时后端回退内部名） */
  title: string
  subTitle: string | null
  /** 当前语言封面（缺失时后端回退商品主图） */
  coverUrl: string | null
  mainImage: string | null
  /** 展示价：优先 minPrice，其次商品基准价 */
  price: number | null
  minPrice: number | null
  maxPrice: number | null
  unit: string | null
  /** 可售库存合计 */
  stock: number
  status: YesNo | string | null
  isRecommend: YesNo | string | null
  sortOrder: number | null
  categoryId: string | null
  /** 当前语言分类名 */
  categoryName: string | null
  tags: ShopTagRef[]

  // ===== P0 电商统计字段 =====
  //
  // 数据来源：ck_shop_product 的 original_price / sales_count / month_sales_count /
  // review_count / rating_avg / favorite_count / free_shipping / listed_at。
  //
  // 【渲染契约】这些字段为 0 / null / false 时表示「没有数据」，对应 UI 元素
  // **整块不渲染**，不得显示 0 值或兜底假数据（延续项目既有约定）。
  // 判断逻辑统一收敛在 resolvePriceView / hasSalesStats，不要在组件里各写一遍。

  /** 划线原价；null 或 <= price 时不展示划线 */
  originalPrice: number | null
  /** 累计销量；0 = 无数据 */
  salesCount: number
  /** 近 30 天销量；0 = 无数据 */
  monthSalesCount: number
  /** 评价总数；0 = 无数据 */
  reviewCount: number
  /** 平均评分 0.0-5.0；0 = 无数据 */
  ratingAvg: number
  /** 收藏数；0 = 无数据 */
  favoriteCount: number
  /** 是否包邮 */
  freeShipping: boolean
  /** 上架时间（后端可能返回 ISO 字符串）；做「新品」排序用 */
  listedAt: string | null
}

/** 商品图集（ShopProductImage） */
export interface StoreProductImage {
  id: string | null
  url: string
  isMain: YesNo | string | null
  sortOrder: number | null
}

/** 规格值（StoreSpecValueVO） */
export interface StoreSpecValue {
  id: string
  value: string
  imageUrl: string | null
  sortOrder: number | null
}

/** 规格定义（StoreSpecVO），values 已按 sortOrder 升序 */
export interface StoreSpec {
  id: string
  name: string
  sortOrder: number | null
  isRequired: YesNo | string | null
  values: StoreSpecValue[]
}

/** SKU 组合（StoreSkuVO）。只有"存在"的组合才会被后端返回 */
export interface StoreSkuCombination {
  id: string
  skuCode: string | null
  /** 规格值ID按 specs 顺序用 "-" 连接 */
  combinationKey: string
  /** 与 combinationKey / specs 顺序一致的规格值ID数组 */
  specValueIds: string[]
  combinationName: string | null
  /** SKU 售价，null 表示回退商品基准价 */
  price: number | null
  /** 已回退计算好的最终价 */
  effectivePrice: number | null
  stock: number
  imageUrl: string | null
  saleable: YesNo | string | null
}

/** 商品详情（StoreProductDetailVO） */
export interface StoreProductDetail {
  id: string
  spuCode: string | null
  title: string
  subTitle: string | null
  description: string | null
  seoTitle: string | null
  seoKeywords: string | null
  seoDescription: string | null
  coverUrl: string | null
  mainImage: string | null
  images: StoreProductImage[]
  price: number | null
  minPrice: number | null
  maxPrice: number | null
  unit: string | null
  stock: number
  status: YesNo | string | null
  isRecommend: YesNo | string | null
  sortOrder: number | null
  categoryId: string | null
  categoryName: string | null
  tags: ShopTagRef[]
  /** 已按 sortOrder 升序 */
  specs: StoreSpec[]
  /** 笛卡尔积中"存在"的组合；不存在的不返回 */
  combinations: StoreSkuCombination[]
}

/** 分页信封（由后端 IPage 直出，Nuxt 侧原样透传） */
export interface StoreProductPage {
  records: StoreProductCard[]
  total: number
  size: number
  current: number
  pages: number
}

/** 规格选择状态：specId -> 选中的规格值ID（null 表示未选） */
export type SpecSelection = Record<string, string | null | undefined>

// ---------------------------------------------------------------------------
// 通用取值 / 归一化
// ---------------------------------------------------------------------------

function asRecord(value: unknown): Record<string, any> {
  return value && typeof value === 'object' ? (value as Record<string, any>) : {}
}

/** 后端用 BigDecimal 返回价格；这里统一收敛成 number | null，避免出现字符串价格 */
export function toNumberOrNull(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

/** 整数转换，非法值回退 fallback */
export function toInteger(value: unknown, fallback = 0): number {
  const num = toNumberOrNull(value)
  return num === null ? fallback : Math.trunc(num)
}

/** 字符串转换，空串 / null 都返回 null */
export function toNullableString(value: unknown): string | null {
  if (value === null || value === undefined) return null
  const str = String(value).trim()
  return str.length ? str : null
}

/**
 * 布尔归一化。
 * Java 后端可能用 Boolean / 1-0 / 'Y'-'N' 三种方式返回布尔字段，
 * 这里统一收敛，避免出现 'false' 字符串被判为真值的经典 bug。
 */
export function asBoolean(value: unknown): boolean {
  if (value === true || value === 1) return true
  if (value === false || value === 0 || value === null || value === undefined) return false
  const str = String(value).trim().toLowerCase()
  return str === 'true' || str === '1' || str === 'y' || str === 'yes'
}

export function normalizeTag(raw: unknown): ShopTagRef | null {
  const item = asRecord(raw)
  const id = toNullableString(item.id)
  if (!id) return null
  return {
    id,
    code: toNullableString(item.code) ?? '',
    name: toNullableString(item.name) ?? toNullableString(item.code) ?? '',
  }
}

export function normalizeTags(raw: unknown): ShopTagRef[] {
  if (!Array.isArray(raw)) return []
  return raw.map(normalizeTag).filter((tag): tag is ShopTagRef => tag !== null)
}

export function normalizeCard(raw: unknown): StoreProductCard {
  const item = asRecord(raw)
  return {
    id: String(item.id ?? ''),
    spuCode: toNullableString(item.spuCode),
    title: toNullableString(item.title) ?? toNullableString(item.name) ?? '',
    subTitle: toNullableString(item.subTitle),
    coverUrl: toNullableString(item.coverUrl) ?? toNullableString(item.mainImage),
    mainImage: toNullableString(item.mainImage),
    price: toNumberOrNull(item.price),
    minPrice: toNumberOrNull(item.minPrice),
    maxPrice: toNumberOrNull(item.maxPrice),
    unit: toNullableString(item.unit),
    stock: toInteger(item.stock, 0),
    status: toNullableString(item.status),
    isRecommend: toNullableString(item.isRecommend),
    sortOrder: toNullableString(item.sortOrder) === null ? null : toInteger(item.sortOrder, 0),
    categoryId: toNullableString(item.categoryId),
    categoryName: toNullableString(item.categoryName),
    originalPrice: toNumberOrNull(item.originalPrice),
    salesCount: toInteger(item.salesCount, 0),
    monthSalesCount: toInteger(item.monthSalesCount, 0),
    reviewCount: toInteger(item.reviewCount, 0),
    ratingAvg: toNumberOrNull(item.ratingAvg) ?? 0,
    favoriteCount: toInteger(item.favoriteCount, 0),
    freeShipping: asBoolean(item.freeShipping),
    listedAt: toNullableString(item.listedAt),
    tags: normalizeTags(item.tags),
  }
}

export function normalizeSpecValue(raw: unknown): StoreSpecValue | null {
  const item = asRecord(raw)
  const id = toNullableString(item.id)
  if (!id) return null
  return {
    id,
    value: toNullableString(item.value) ?? '',
    imageUrl: toNullableString(item.imageUrl),
    sortOrder: toNullableString(item.sortOrder) === null ? null : toInteger(item.sortOrder, 0),
  }
}

export function normalizeSpec(raw: unknown): StoreSpec | null {
  const item = asRecord(raw)
  const id = toNullableString(item.id)
  if (!id) return null
  const values = Array.isArray(item.values)
    ? item.values.map(normalizeSpecValue).filter((v): v is StoreSpecValue => v !== null)
    : []
  return {
    id,
    name: toNullableString(item.name) ?? '',
    sortOrder: toNullableString(item.sortOrder) === null ? null : toInteger(item.sortOrder, 0),
    isRequired: toNullableString(item.isRequired) ?? 'Y',
    values,
  }
}

export function normalizeCombination(raw: unknown): StoreSkuCombination | null {
  const item = asRecord(raw)
  const id = toNullableString(item.id)
  if (!id) return null
  const specValueIds = Array.isArray(item.specValueIds)
    ? item.specValueIds.map((v: unknown) => String(v)).filter((v: string) => v.length > 0)
    : []
  return {
    id,
    skuCode: toNullableString(item.skuCode),
    combinationKey: toNullableString(item.combinationKey) ?? specValueIds.join('-'),
    specValueIds,
    combinationName: toNullableString(item.combinationName),
    price: toNumberOrNull(item.price),
    effectivePrice: toNumberOrNull(item.effectivePrice),
    stock: toInteger(item.stock, 0),
    imageUrl: toNullableString(item.imageUrl),
    saleable: toNullableString(item.saleable) ?? 'Y',
  }
}

export function normalizeImage(raw: unknown): StoreProductImage | null {
  const item = asRecord(raw)
  const url = toNullableString(item.url)
  if (!url) return null
  return {
    id: toNullableString(item.id),
    url,
    isMain: toNullableString(item.isMain),
    sortOrder: toNullableString(item.sortOrder) === null ? null : toInteger(item.sortOrder, 0),
  }
}

export function normalizeDetail(raw: unknown): StoreProductDetail {
  const item = asRecord(raw)
  const card = normalizeCard(item)
  const images = Array.isArray(item.images)
    ? item.images.map(normalizeImage).filter((img): img is StoreProductImage => img !== null)
    : []
  const specs = Array.isArray(item.specs)
    ? item.specs.map(normalizeSpec).filter((spec): spec is StoreSpec => spec !== null)
    : []
  const combinations = Array.isArray(item.combinations)
    ? item.combinations
        .map(normalizeCombination)
        .filter((combo): combo is StoreSkuCombination => combo !== null)
    : []

  return {
    ...card,
    description: toNullableString(item.description),
    seoTitle: toNullableString(item.seoTitle),
    seoKeywords: toNullableString(item.seoKeywords),
    seoDescription: toNullableString(item.seoDescription),
    images,
    specs,
    combinations,
  }
}

export function normalizePage(raw: unknown, fallbackPageNo: number, fallbackPageSize: number): StoreProductPage {
  const page = asRecord(raw)
  const records = Array.isArray(page.records) ? page.records.map(normalizeCard) : []
  const total = toInteger(page.total, records.length)
  const size = toInteger(page.size, fallbackPageSize)
  const current = toInteger(page.current, fallbackPageNo)
  const pages = toInteger(page.pages, size > 0 ? Math.max(1, Math.ceil(total / size)) : 0)
  return { records, total, size, current, pages }
}

// ---------------------------------------------------------------------------
// 接口查询参数归一化（server/api/products*.ts 复用）
// ---------------------------------------------------------------------------
//
// 语言解析（STORE_SUPPORTED_LANGS / resolveStoreLang）已收敛到 types/store-locale.ts，
// 那里是店铺端语言清单的唯一来源，本文件不再重复维护。

/** 正整数转换（页码 / 每页条数），非法值回退 fallback */
export function toPositiveInt(value: unknown, fallback: number): number {
  const raw = Array.isArray(value) ? value[0] : value
  const num = Number(raw)
  if (!Number.isFinite(num) || num < 1) return fallback
  return Math.trunc(num)
}

export function clampInt(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

/** 可选查询参数：空串 / 空白 / 数组第一个值 都归一成 string | undefined */
export function toOptionalQueryString(value: unknown): string | undefined {
  const raw = Array.isArray(value) ? value[0] : value
  if (raw === null || raw === undefined) return undefined
  const str = String(raw).trim()
  return str.length ? str : undefined
}

// ---------------------------------------------------------------------------
// 规格组合置灰算法（本任务核心）
// ---------------------------------------------------------------------------
//
// 判定规则（2026-10-14 改版，交互从"先取消再换"改为"同规格直接切换"）：
//   规格值 V 可选 ⇔ 存在至少一个 combination 满足：
//       1. saleable === 'Y' && stock > 0            （组合可售）
//       2. combination.specValueIds 包含 V 的 id
//       3. combination.specValueIds 包含"当前已选的所有**其它规格**的值"的 id
//          —— 注意这里按 **specId** 排除整个"V 所属的规格"，而不是只排除 V 自己：
//             一个组合在每个规格上只会取一个值，所以"本规格还选着别的值"这件事
//             与 V 能不能选**无关**——用户点了 V 就是要换掉那个值。
//             （例：已选「39 码 + 棕褐」，此时「40 码」仍然可选：只要存在
//               40码+棕褐的可售组合即可，点击后 39 自动被 40 替换。）
//
// 为什么必须按 specId 排除，而不是"把同规格的旧值当成一个必须满足的条件"：
//   旧实现要求组合同时包含"已选的全部值"，而同规格的两个值天然互斥，
//   于是选完 39 之后 40/41/42/43 全部置灰，用户必须先点一下 39 把它取消才能选 40。
//   那既不直观（看着像"这件商品只有 39 码"），也违背主流电商的规格切换习惯。
//
// 仍被置灰的情形（这是需求的硬约束）：**该值与其它规格已选值没有任何可售组合**。
//   例：已选「棕褐」时，若「43 码 + 棕褐」组合不存在，43 码依旧灰色不可选。
//
// 已选中规格值的处理策略（保持选中，不参与置灰）：
//   - 已选中的值永远可点，作用变成"取消选择"；
//   - 同规格的其它值走"直接切换"，不做二次确认。
//
// 自测样例（含不存在组合 / saleable='N' / stock=0 三种边界 + 同规格切换矩阵
// + Lead 给的演示商品期望值）见：nuxt-shop/temp/spec-availability.selfcheck.ts

/** 组合是否可售：商家标记可售 且 库存 > 0 */
export function isCombinationSellable(combination: StoreSkuCombination): boolean {
  return combination.saleable === 'Y' && toInteger(combination.stock, 0) > 0
}

/**
 * 单个规格值是否可选。
 *
 * @param specId 该规格值所属规格的 id（用于把"本规格已选的旧值"从约束里排除）
 * @param selection 当前已选状态 specId -> valueId
 */
export function isSpecValueAvailable(
  specId: string,
  valueId: string,
  selection: SpecSelection,
  combinations: StoreSkuCombination[],
): boolean {
  const requiredOtherValueIds: string[] = []
  for (const [otherSpecId, otherValueId] of Object.entries(selection || {})) {
    // 排除「V 自己所属规格」的选择：同规格换值是直接替换，不是"两个值共存"
    if (otherSpecId === specId) continue
    // 再排除 V 自己（防御性：数据异常导致同一 valueId 出现在别的规格下时也按"自己"处理）
    if (otherValueId && otherValueId !== valueId) requiredOtherValueIds.push(otherValueId)
  }

  return (combinations || []).some(combination => {
    if (!isCombinationSellable(combination)) return false
    const ids = combination.specValueIds || []
    if (!ids.includes(valueId)) return false
    return requiredOtherValueIds.every(id => ids.includes(id))
  })
}

/**
 * 计算当前选择下需要置灰的规格值 id 集合。
 *
 * 两层排除：
 *   1. 本规格已选中的那个值不置灰（选中态优先：点它 = 取消选择）；
 *   2. 置灰判定按 specId 排除本规格的旧值（见 isSpecValueAvailable），
 *      所以"换规格"这件事本身不会再被旧值挡住。
 */
export function computeUnavailableValueIds(
  specs: StoreSpec[],
  combinations: StoreSkuCombination[],
  selection: SpecSelection,
): Set<string> {
  const unavailable = new Set<string>()
  for (const spec of specs || []) {
    for (const value of spec.values || []) {
      if (selection?.[spec.id] === value.id) continue
      if (!isSpecValueAvailable(spec.id, value.id, selection, combinations)) {
        unavailable.add(value.id)
      }
    }
  }
  return unavailable
}

/**
 * 根据当前选择解析出唯一命中的 SKU 组合；无法确定时返回 null。
 * - 必选规格（isRequired === 'Y'）没选全 -> null
 * - 命中的组合不唯一（本商品存在可选规格且未选） -> null
 */
export function findSelectedCombination(
  specs: StoreSpec[],
  combinations: StoreSkuCombination[],
  selection: SpecSelection,
): StoreSkuCombination | null {
  const specList = specs || []
  if (!specList.length) return null

  const selectedValueIds: string[] = []
  for (const spec of specList) {
    const valueId = selection?.[spec.id]
    if (valueId) selectedValueIds.push(valueId)
  }
  if (!selectedValueIds.length) return null
  if (specList.some(spec => spec.isRequired === 'Y' && !selection?.[spec.id])) return null

  const candidates = (combinations || []).filter(combination =>
    selectedValueIds.every(id => (combination.specValueIds || []).includes(id)),
  )
  if (!candidates.length) return null

  const exact = candidates.find(combination => (combination.specValueIds || []).length === selectedValueIds.length)
  if (exact) return exact

  const only = candidates[0]
  return candidates.length === 1 && only ? only : null
}

/** 是否所有规格都已选择（用于显示"请选择完整规格"） */
export function isSelectionComplete(specs: StoreSpec[], selection: SpecSelection): boolean {
  const specList = specs || []
  if (!specList.length) return true
  return specList.every(spec => !!selection?.[spec.id])
}

// ---------------------------------------------------------------------------
// 展示辅助
// ---------------------------------------------------------------------------

/** 把 i18n 的 locale code（zh / en / fr）转成 Intl 用的 BCP-47 tag（映射来自语言清单唯一来源） */
export function toIntlLocale(locale?: string | null): string {
  if (!locale) return STORE_INTL_LOCALE_TAGS[STORE_DEFAULT_LANG] ?? 'en-US'
  return STORE_INTL_LOCALE_TAGS[locale] ?? locale
}

/** 按当前 locale 用 Intl.NumberFormat 格式化价格数字（货币符号由 i18n 的 common.currency 提供） */
export function formatPriceNumber(value: number | null | undefined, locale?: string | null): string {
  const num = toNumberOrNull(value)
  if (num === null) return '--'
  return new Intl.NumberFormat(toIntlLocale(locale), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(num)
}

/** 卡片封面：优先当前语言封面，其次商品主图 */
export function resolveCoverUrl(product: Pick<StoreProductCard, 'coverUrl' | 'mainImage'>): string {
  return product.coverUrl || product.mainImage || ''
}

/**
 * 详情图集：images 按 sortOrder 升序 + isMain 优先；
 * images 为空时回退 coverUrl / mainImage，保证左侧大图始终有内容。
 */
export function resolveGallery(detail: Pick<StoreProductDetail, 'images' | 'coverUrl' | 'mainImage'>): string[] {
  const sorted = [...(detail.images || [])].sort((a, b) => {
    if (a.isMain === 'Y' && b.isMain !== 'Y') return -1
    if (b.isMain === 'Y' && a.isMain !== 'Y') return 1
    return toInteger(a.sortOrder, 0) - toInteger(b.sortOrder, 0)
  })
  const urls = sorted.map(image => image.url).filter((url): url is string => !!url)
  if (urls.length) return urls
  const fallback = detail.coverUrl || detail.mainImage
  return fallback ? [fallback] : []
}

/** SKU 展示价：优先 SKU 最终价，其次组合价，最后商品展示价 */
export function resolveSkuPrice(
  combination: StoreSkuCombination | null,
  product: Pick<StoreProductDetail, 'price' | 'minPrice' | 'maxPrice'>,
): number | null {
  if (combination) {
    return toNumberOrNull(combination.effectivePrice) ?? toNumberOrNull(combination.price) ?? toNumberOrNull(product.price)
  }
  return toNumberOrNull(product.minPrice) ?? toNumberOrNull(product.price)
}

// ---------------------------------------------------------------------------
// 展示辅助 · 电商统计字段（P0）
// ---------------------------------------------------------------------------
//
// 「有没有数据 → 显不显示」的判断全部收敛到这里，组件只负责渲染，
// 避免在 12 个组件里各写一遍 `salesCount > 0 ? ... : ''` 还写得不一致。

/** 商城价格展示：整数不带小数（¥4,299），有角分才带两位（¥211.68） */
export function formatShopPrice(value: number | null | undefined, locale?: string | null): string {
  const num = toNumberOrNull(value)
  if (num === null) return '--'
  return new Intl.NumberFormat(toIntlLocale(locale), {
    minimumFractionDigits: Number.isInteger(num) ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(num)
}

/** 大数字压缩：12345 → 1.2万；999 → 999 */
export function formatCompactCount(value: number | null | undefined): string {
  const num = toNumberOrNull(value)
  if (num === null || num <= 0) return '0'
  if (num < 10000) return String(Math.trunc(num))
  const wan = num / 10000
  return `${wan >= 100 ? Math.trunc(wan) : wan.toFixed(1).replace(/\.0$/, '')}万`
}

/** 是否有可展示的销量/评价统计 */
export function hasSalesStats(
  product: Pick<StoreProductCard, 'salesCount' | 'reviewCount'>,
): boolean {
  return toInteger(product.salesCount, 0) > 0 || toInteger(product.reviewCount, 0) > 0
}

/** 是否展示划线价：必须有值，且严格高于现价（否则不渲染） */
export function hasOriginalPrice(
  product: Pick<StoreProductCard, 'price' | 'minPrice' | 'originalPrice'>,
): boolean {
  const price = toNumberOrNull(product.minPrice) ?? toNumberOrNull(product.price)
  const original = toNumberOrNull(product.originalPrice)
  return original !== null && price !== null && original > price
}

/** 价格视图：把「现价 / 划线价 / 是否划线 / 优惠金额」一次算清 */
export interface StorePriceView {
  /** 展示价 */
  price: number | null
  /** 划线原价；不展示时为 null */
  originalPrice: number | null
  /** 是否渲染划线价 */
  showOriginal: boolean
  /**
   * 优惠金额（原价 - 现价）；无优惠时为 null。
   * 这里只给数字，文案由组件用 i18n 渲染（types 层不出现任何中文），
   * 例如 t('shop.discountOff', { amount: formatShopPrice(view.discountAmount) })。
   */
  discountAmount: number | null
}

export function resolvePriceView(
  product: Pick<StoreProductCard, 'price' | 'minPrice' | 'maxPrice' | 'originalPrice'>,
): StorePriceView {
  const price = toNumberOrNull(product.minPrice) ?? toNumberOrNull(product.price)
  const original = toNumberOrNull(product.originalPrice)
  const showOriginal = price !== null && original !== null && original > price
  return {
    price,
    originalPrice: showOriginal ? original : null,
    showOriginal,
    discountAmount: showOriginal && price !== null && original !== null ? original - price : null,
  }
}

/** 评分展示：无评价时返回 null（不渲染） */
export function resolveRating(
  product: Pick<StoreProductCard, 'ratingAvg' | 'reviewCount'>,
): number | null {
  if (toInteger(product.reviewCount, 0) <= 0) return null
  const rating = toNumberOrNull(product.ratingAvg)
  return rating !== null && rating > 0 ? rating : null
}

/** 是否跨价格区间（用于「¥899 起」与「价格随规格不同而变化」提示） */
export function isPriceRange(
  product: Pick<StoreProductCard, 'minPrice' | 'maxPrice'>,
): boolean {
  const min = toNumberOrNull(product.minPrice)
  const max = toNumberOrNull(product.maxPrice)
  return min !== null && max !== null && max > min
}

/**
 * 商品角标：从真实 tag code 映射到角标样式。
 * 标签体系见 ck_shop_tag（HOT / SALE / NEW / PICK / FAST / TOP），
 * 未识别的一律走中性样式，不做猜测。
 */
export type BadgeTone = 'hot' | 'sale' | 'new' | 'neutral'

export function resolveTagTone(code: string | null | undefined): BadgeTone {
  switch ((code || '').toUpperCase()) {
    case 'HOT':
      return 'hot'
    case 'SALE':
      return 'sale'
    case 'NEW':
      return 'new'
    default:
      return 'neutral'
  }
}

/** 服务标签（包邮 / 现货速发 / 品质优选 / 七天无理由）：同样只认已知 code */
export function resolveServiceTagCodes(product: Pick<StoreProductCard, 'tags'>): string[] {
  const known = new Set(['FAST', 'TOP'])
  return (product.tags || [])
    .map(tag => (tag.code || '').toUpperCase())
    .filter(code => known.has(code))
}
