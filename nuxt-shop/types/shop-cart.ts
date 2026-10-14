// types/shop-cart.ts
//
// 店铺端（nuxt-shop）购物车相关类型 + 纯函数工具。
//
// 与 types/shop-product.ts 同样的约定：本文件刻意不依赖任何 Nuxt / Vue API，
// 服务端路由用它做数据归一化，前端页面/组件用它做状态渲染与勾选计算。
//
// 数据来源（Java 后端，全部通过 server/utils/apiClient.ts 的 createApiClient 调用）：
//   GET  /shop/cart/store/list          -> StoreCartVO       { items, summary }
//   GET  /shop/cart/store/count         -> StoreCartCountVO  { totalItems, totalQuantity }
//   POST /shop/cart/store/add           -> StoreCartVO
//   POST /shop/cart/store/quantity      -> StoreCartVO
//   POST /shop/cart/store/remove        -> StoreCartVO
//   POST /shop/cart/store/batch-remove  -> StoreCartVO
//   POST /shop/cart/store/clear         -> StoreCartVO
//   POST /shop/cart/store/clear-invalid -> StoreCartVO
//
// 关键设计：**明细的可用性状态由后端直出**（见 CartItemStatus 注释）。
// 前端不做"下架/无货"的二次判断——规则只写在后端一处，就不会出现
// "详情页说有货、购物车说没货"这类自相矛盾。

import { toInteger, toNumberOrNull, toNullableString } from './shop-product'

/**
 * 购物车明细状态（后端直出）：
 * - AVAILABLE            可购买
 * - STOCK_INSUFFICIENT   库存不足（还有货，但数量超过剩余库存）
 * - OUT_OF_STOCK         无货
 * - OFFLINE              商品已下架 / 已删除 / 不属于当前店铺
 * - SKU_INVALID          该规格已停售，或商品规格已变更
 */
export type CartItemStatus =
  | 'AVAILABLE'
  | 'STOCK_INSUFFICIENT'
  | 'OUT_OF_STOCK'
  | 'OFFLINE'
  | 'SKU_INVALID'

/** 一条购物车明细（= 一个 SKU） */
export interface StoreCartItem {
  id: string
  productId: string
  /** SKU组合ID；无规格商品为空串 */
  skuId: string
  skuCode: string | null
  spuCode: string | null
  title: string
  coverUrl: string | null
  /** 规格文案，如 白色/8G/256G；无规格商品为 null */
  skuName: string | null
  unit: string | null
  price: number | null
  quantity: number
  /** 小计（失效条目恒为 0，避免"总价里混进一件已下架商品"） */
  lineTotal: number
  stock: number
  status: CartItemStatus
  /** 是否可勾选结算：AVAILABLE / STOCK_INSUFFICIENT 为 true */
  selectable: boolean
  productStatus: string | null
  skuSaleable: string | null
  createTime: string | null
  updateTime: string | null
}

/** 购物车汇总（后端直出，勾选合计由前端按勾选状态另算） */
export interface StoreCartSummary {
  totalItems: number
  totalQuantity: number
  validItems: number
  validQuantity: number
  validAmount: number
  invalidItems: number
  allInvalid: boolean
}

export interface StoreCart {
  items: StoreCartItem[]
  summary: StoreCartSummary
}

/** 购物车角标 */
export interface StoreCartCount {
  totalItems: number
  totalQuantity: number
}

// ---------------------------------------------------------------------------
// 归一化
// ---------------------------------------------------------------------------

const CART_STATUSES: CartItemStatus[] = [
  'AVAILABLE',
  'STOCK_INSUFFICIENT',
  'OUT_OF_STOCK',
  'OFFLINE',
  'SKU_INVALID',
]

function asRecord(value: unknown): Record<string, any> {
  return value && typeof value === 'object' ? (value as Record<string, any>) : {}
}

/** 未知状态一律按"失效"处理（fail-safe：宁可多提示，也不能把不可买的东西算进可结算） */
function normalizeStatus(value: unknown): CartItemStatus {
  const raw = toNullableString(value)?.toUpperCase() ?? ''
  return (CART_STATUSES as string[]).includes(raw) ? (raw as CartItemStatus) : 'OFFLINE'
}

export function normalizeCartItem(raw: unknown): StoreCartItem | null {
  const item = asRecord(raw)
  const id = toNullableString(item.id)
  if (!id) return null
  const status = normalizeStatus(item.status)
  return {
    id,
    productId: toNullableString(item.productId) ?? '',
    // 空串是"无规格商品"的合法取值，不能归一成 null
    skuId: toNullableString(item.skuId) ?? '',
    skuCode: toNullableString(item.skuCode),
    spuCode: toNullableString(item.spuCode),
    title: toNullableString(item.title) ?? '',
    coverUrl: toNullableString(item.coverUrl),
    skuName: toNullableString(item.skuName),
    unit: toNullableString(item.unit),
    price: toNumberOrNull(item.price),
    quantity: toInteger(item.quantity, 0),
    lineTotal: toNumberOrNull(item.lineTotal) ?? 0,
    stock: toInteger(item.stock, 0),
    status,
    // 后端没给 selectable 时按状态兜底推导，保证前端不会因此失去勾选能力
    selectable: item.selectable === undefined || item.selectable === null
      ? status === 'AVAILABLE' || status === 'STOCK_INSUFFICIENT'
      : Boolean(item.selectable),
    productStatus: toNullableString(item.productStatus),
    skuSaleable: toNullableString(item.skuSaleable),
    createTime: toNullableString(item.createTime),
    updateTime: toNullableString(item.updateTime),
  }
}

export function emptyCartSummary(): StoreCartSummary {
  return {
    totalItems: 0,
    totalQuantity: 0,
    validItems: 0,
    validQuantity: 0,
    validAmount: 0,
    invalidItems: 0,
    allInvalid: false,
  }
}

export function emptyCart(): StoreCart {
  return { items: [], summary: emptyCartSummary() }
}

export function normalizeCart(raw: unknown): StoreCart {
  const cart = asRecord(raw)
  const items = Array.isArray(cart.items)
    ? cart.items.map(normalizeCartItem).filter((item): item is StoreCartItem => item !== null)
    : []
  const summaryRaw = asRecord(cart.summary)
  const summary: StoreCartSummary = {
    totalItems: toInteger(summaryRaw.totalItems, items.length),
    totalQuantity: toInteger(summaryRaw.totalQuantity, 0),
    validItems: toInteger(summaryRaw.validItems, 0),
    validQuantity: toInteger(summaryRaw.validQuantity, 0),
    validAmount: toNumberOrNull(summaryRaw.validAmount) ?? 0,
    invalidItems: toInteger(summaryRaw.invalidItems, 0),
    allInvalid: Boolean(summaryRaw.allInvalid),
  }
  // 后端字段缺失（例如旧版本）时用明细兜底算出汇总，前端永远不会显示空汇总
  if (items.length && !summary.totalItems) {
    summary.totalItems = items.length
    summary.totalQuantity = items.reduce((sum, item) => sum + item.quantity, 0)
    const valid = items.filter(item => item.selectable)
    summary.validItems = valid.length
    summary.validQuantity = valid.reduce((sum, item) => sum + item.quantity, 0)
    summary.validAmount = valid.reduce((sum, item) => sum + item.lineTotal, 0)
    summary.invalidItems = items.length - valid.length
    summary.allInvalid = valid.length === 0
  }
  return { items, summary }
}

export function normalizeCartCount(raw: unknown): StoreCartCount {
  const count = asRecord(raw)
  return {
    totalItems: toInteger(count.totalItems, 0),
    totalQuantity: toInteger(count.totalQuantity, 0),
  }
}

// ---------------------------------------------------------------------------
// 展示辅助（纯函数，便于前端复用与自测）
// ---------------------------------------------------------------------------

/**
 * 明细语义词条 key 后缀（i18n：cart.status.<key>）。
 * 抽成函数而不是在模板里写一长串三元：模板里的三元判断最容易漏掉新增状态。
 */
export function cartItemStatusKey(status: CartItemStatus): string {
  switch (status) {
    case 'AVAILABLE':
      return 'available'
    case 'STOCK_INSUFFICIENT':
      return 'stockInsufficient'
    case 'OUT_OF_STOCK':
      return 'outOfStock'
    case 'SKU_INVALID':
      return 'skuInvalid'
    case 'OFFLINE':
    default:
      return 'offline'
  }
}

/** 状态对应的语义色调（供 Tailwind 类名映射，避免硬编码散落在模板里） */
export function cartItemStatusTone(status: CartItemStatus): 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'AVAILABLE':
      return 'success'
    case 'STOCK_INSUFFICIENT':
      return 'warning'
    default:
      return 'danger'
  }
}

/** 勾选合计：只累加"可勾选且被勾选"的条目 */
export function sumSelected(items: StoreCartItem[], selectedIds: Iterable<string>): {
  count: number
  quantity: number
  amount: number
} {
  const set = selectedIds instanceof Set ? selectedIds : new Set(selectedIds)
  let count = 0
  let quantity = 0
  let amount = 0
  for (const item of items) {
    if (!item.selectable || !set.has(item.id)) continue
    count += 1
    quantity += item.quantity
    amount += item.lineTotal
  }
  return { count, quantity, amount: Math.round(amount * 100) / 100 }
}
