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
//   GET /shop/product/store/list?lang&pageNo&pageSize&categoryId&brandId&keyword
//        -> JeecgBoot 信封 .result = { records, total, size, current, pages }
//   GET /shop/product/store/detail?id&lang
//        -> JeecgBoot 信封 .result = StoreProductDetail
// 字段以后端 VO 为准：StoreProductCardVO / StoreProductDetailVO / StoreSpecVO /
// StoreSpecValueVO / StoreSkuVO / TagRefVO + ShopProduct / ShopProductImage。

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
  brandId: string | null
  /** 当前语言品牌名 */
  brandName: string | null
  tags: ShopTagRef[]
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
  brandId: string | null
  brandName: string | null
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
    brandId: toNullableString(item.brandId),
    brandName: toNullableString(item.brandName),
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

/** 店铺端支持的语言（与 nuxt.config.ts i18n.locales 一致） */
export const STORE_SUPPORTED_LANGS = ['zh', 'en', 'fr'] as const

/** 把 query.lang 收敛成 zh|en|fr 之一 */
export function resolveStoreLang(value: unknown, fallback: string = 'en'): string {
  const raw = Array.isArray(value) ? value[0] : value
  const lang = typeof raw === 'string' ? raw.trim().toLowerCase() : ''
  return (STORE_SUPPORTED_LANGS as readonly string[]).includes(lang)
    ? lang
    : (STORE_SUPPORTED_LANGS as readonly string[]).includes(fallback)
      ? fallback
      : 'en'
}

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
// 判定规则（与任务契约 + Lead 用 SQL 复算的期望值一致）：
//   规格值 V 可选 ⇔ 存在至少一个 combination 满足：
//       1. saleable === 'Y' && stock > 0            （组合可售）
//       2. combination.specValueIds 包含 V 的 id
//       3. combination.specValueIds 包含"当前已选的所有其它规格值"的 id
//          —— 注意这里是"所有其它已选值"，**不排除 V 自己所在的规格**：
//             一个组合每个规格只会有一个值，所以当同规格里已选了别的值时，
//             V 必然无法与之共存 => 置灰（例：已选 白色，则 红色 / 黑色 置灰；
//             想换颜色需先点一下已选值取消）。
//             V 自己就是被选中的那个值时，规则退化为"包含其它已选值"，仍然可选。
//
// 已选中规格值的处理策略（两种标准做法里选一种，这里选 **保持选中**）：
//   - 用户只能点击"当前可选"的值，所以状态永远来自合法路径；
//   - 已选中的值不参与置灰（选中态优先），再次点击即可取消，用户不会卡在非法状态。
//
// 自测样例（含不存在组合 / saleable='N' / stock=0 三种边界 + Lead 给的演示商品
// 期望值）见：nuxt-shop/temp/spec-availability.selfcheck.ts

/** 组合是否可售：商家标记可售 且 库存 > 0 */
export function isCombinationSellable(combination: StoreSkuCombination): boolean {
  return combination.saleable === 'Y' && toInteger(combination.stock, 0) > 0
}

/**
 * 单个规格值是否可选。
 * @param specId 该规格值所属规格的 id
 * @param selection 当前已选状态 specId -> valueId
 */
export function isSpecValueAvailable(
  specId: string,
  valueId: string,
  selection: SpecSelection,
  combinations: StoreSkuCombination[],
): boolean {
  const requiredOtherValueIds: string[] = []
  for (const [, otherValueId] of Object.entries(selection || {})) {
    // 排除 V 自己（V 已被选中时，规则要求的是"其它"已选值）
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
 * 已选中的规格值不置灰（选中态优先，见上方策略说明）。
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

const INTL_LOCALE_TAGS: Record<string, string> = {
  zh: 'zh-CN',
  en: 'en-US',
  fr: 'fr-FR',
}

/** 把 i18n 的 locale code（zh / en / fr）转成 Intl 用的 BCP-47 tag */
export function toIntlLocale(locale?: string | null): string {
  if (!locale) return 'en-US'
  return INTL_LOCALE_TAGS[locale] ?? locale
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
