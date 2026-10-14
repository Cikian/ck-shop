// composables/useCheckoutSnapshot.ts
//
// 结算快照（sessionStorage）的**唯一**读写出口。
//
// 这份快照是"待下单清单"：购物车页 / 商品详情页的「立即购买」把它写进去，
// 结算页读出来调 /api/order/preview 由服务端重新算钱。
//
// 为什么要抽成一个文件（原先 cart.vue 与 checkout/index.vue 各写一份同名常量）：
//   1. 加入「立即购买」后，写快照的地方从 1 处变成 3 处（购物车 / 立即购买 / 结算页 reads）。
//      同一个 sessionStorage key 散落在 3 个文件里，改一处漏一处就是"点了结算却提示没有待结算商品"；
//   2. 读取侧的归一化（空值收敛、非法行过滤）只应该有一份实现，
//      否则"购物车写入 2 件、结算页只认 1 件"这类问题会非常难查。
//
// 注意：Nuxt 4 的 srcDir 是 app/，本项目根目录下的 composables/ 不会被自动导入
// （useAuth / useProducts 也都是显式 import 的），所以要这样用：
//   import { writeCheckoutSnapshot } from '~~/composables/useCheckoutSnapshot'

/** sessionStorage key（改动等于让所有已打开的页面失去快照，非必要不要动） */
export const CHECKOUT_SNAPSHOT_KEY = 'store-checkout-items'

/** 结算快照里的一条 */
export interface CheckoutSnapshotItem {
  /**
   * 购物车明细 ID：下单后后端**只清除这些条目**。
   * 「立即购买」的商品没有经过购物车，这里固定为空串 ——
   * 后端 submit 对空串的处理是"不清任何购物车条目"（cartItemIds 过滤掉空值），
   * 因此不会出现"直接买一件却把购物车里的同款删掉"的副作用。
   */
  cartItemId: string
  productId: string
  skuId: string
  quantity: number
}

/** 归一化一条快照：数字收敛成整数、字符串去空格，非法行返回 null */
function normalizeItem(raw: unknown): CheckoutSnapshotItem | null {
  const entry = (raw ?? {}) as Record<string, unknown>
  const productId = String(entry.productId ?? '').trim()
  const quantity = Math.trunc(Number(entry.quantity ?? 0))
  if (!productId || !Number.isFinite(quantity) || quantity < 1) return null
  return {
    cartItemId: String(entry.cartItemId ?? '').trim(),
    productId,
    skuId: entry.skuId ? String(entry.skuId).trim() : '',
    quantity,
  }
}

/**
 * 写入待下单清单（购物车「去结算」/ 详情页「立即购买」都走这里）。
 *
 * @returns 是否真的写进去了（SSR 或 sessionStorage 不可用时返回 false）
 */
export function writeCheckoutSnapshot(items: CheckoutSnapshotItem[]): boolean {
  if (!import.meta.client) return false
  const normalized = items.map(normalizeItem).filter((item): item is CheckoutSnapshotItem => item !== null)
  if (!normalized.length) return false
  try {
    sessionStorage.setItem(CHECKOUT_SNAPSHOT_KEY, JSON.stringify(normalized))
    return true
  } catch {
    // 隐私模式 / 存储配额满：当作没写成，由调用方提示用户，而不是抛异常炸掉页面
    return false
  }
}

/**
 * 读取待下单清单（结算页用）。
 * 快照损坏时返回空数组 —— 退化成"请回购物车重新勾选"，而不是让结算页崩掉。
 */
export function readCheckoutSnapshot(): CheckoutSnapshotItem[] {
  if (!import.meta.client) return []
  try {
    const raw = sessionStorage.getItem(CHECKOUT_SNAPSHOT_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.map(normalizeItem).filter((item): item is CheckoutSnapshotItem => item !== null)
  } catch {
    return []
  }
}

/** 下单成功后清掉快照：用户从支付页返回时不该再看到一份待下单清单 */
export function clearCheckoutSnapshot(): void {
  if (!import.meta.client) return
  try {
    sessionStorage.removeItem(CHECKOUT_SNAPSHOT_KEY)
  } catch {
    // 清不掉也不影响下单结果，静默降级
  }
}
