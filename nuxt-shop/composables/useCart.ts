// composables/useCart.ts
//
// 店铺端购物车状态 + 动作（BFF 层 /api/cart/*）。
//
// 注意：Nuxt 4 的 srcDir 是 app/，本项目根目录下的 composables/ 不会被自动导入
// （useAuth / useProducts 也都是显式 import 的），所以要这样用：
//   import { useCart } from '~~/composables/useCart'
//
// 设计取舍：
//   1. **所有写操作都用后端返回的购物车整体替换本地状态**，不做乐观更新。
//      购物车的"能不能买"取决于实时库存与上下架，本地猜一个结果再回滚，
//      迟早会出现界面显示"已加入"而后端其实拒绝了的错觉。
//   2. **勾选状态只保存在前端**（selectedIds）：勾选是纯交互状态，
//      不必每次回后端；但每次刷新/变更后会剔除已不存在的 id，避免"幽灵勾选"。
//   3. 401 单独识别并暴露 `unauthorized`，调用方据此引导登录，
//      而不是把"请先登录"和"库存不足"混成一句通用错误。

import type { StoreCart, StoreCartCount, StoreCartItem } from '~~/types/shop-cart'
import { emptyCart, normalizeCart, normalizeCartCount, sumSelected } from '~~/types/shop-cart'
import { useAuth } from '~~/composables/useAuth'
import { isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'
import { useLatestRequest } from '~~/composables/useLatestRequest'

/**
 * 后端业务错误的可读文案（JeecgBoot 的中文 message 会透传到 statusMessage）。
 * 实现收敛到 composables/useApiError.ts —— 原来这份与页面里的 readErrorMessage 逐字相同。
 */
export function extractCartErrorMessage(error: unknown, fallback: string): string {
  return readErrorMessage(error, fallback)
}

/**
 * 购物车角标相关的 useState key。
 *
 * ⚠️ 对外导出是因为 **SSR 播种**（app/middleware/auth-seed.global.ts）要在
 * "没有组件实例、不方便调用整个 useCart()"的地方写同一份状态。
 * 改这里的字符串会同时让播种失效 —— 两处必须一起改。
 */
export const CART_COUNT_STATE_KEY = 'store-cart-count'
export const CART_UNAUTHORIZED_STATE_KEY = 'store-cart-unauthorized'

export const useCart = () => {
  const cart = useState<StoreCart>('store-cart', () => emptyCart())
  const count = useState<StoreCartCount>(CART_COUNT_STATE_KEY, () => ({ totalItems: 0, totalQuantity: 0 }))
  const loading = useState<boolean>('store-cart-loading', () => false)
  const mutating = useState<boolean>('store-cart-mutating', () => false)
  const unauthorized = useState<boolean>(CART_UNAUTHORIZED_STATE_KEY, () => false)
  const selectedIds = useState<string[]>('store-cart-selected', () => [])
  /**
   * 是否已经做过一次"默认全选"。
   *
   * 只做一次是有意的：用户手动取消勾选之后，任何一次数量修改/刷新都不该把勾选又加回来——
   * 那会让人以为"取消不了"。但首次打开购物车时不应该一件都没选，
   * 否则"已选 0 件 / 合计 ¥0.00"会让用户以为购物车坏了。
   */
  const selectionInitialized = useState<boolean>('store-cart-selection-initialized', () => false)

  const { isLoggedIn, restoreUser, refreshSession } = useAuth()
  // 在 setup 阶段取一次 locale：动作函数是"稍后被用户点击触发"的，
  // 那时再调用 useI18n() 属于脱离 setup 上下文，容易拿到不可靠的实例。
  const { locale } = useI18n()

  /**
   * SSR 也要能带上 httpOnly cookie 取购物车。
   *
   * 普通 $fetch 在服务端是"服务端 → 自己的 Nitro"，**不会**自动转发浏览器的 cookie，
   * 于是 SSR 阶段永远 401、购物车/角标只能等客户端再拉一次（首屏空窗 + 多一个 RTT）。
   * useRequestFetch() 在服务端会转发当前请求的 cookie，客户端则等价于 $fetch。
   *
   * 必须在 setup 阶段取一次并捕获下来（动作函数是 later 被点击触发的）。
   */
  const requestFetch = useRequestFetch()

  // latest-wins 守卫：购物车整体状态/角标只接受"最后一次请求"的结果
  const cartGuard = useLatestRequest()
  const countGuard = useLatestRequest()

  const items = computed(() => cart.value.items)
  const summary = computed(() => cart.value.summary)

  /** 可结算的明细（AVAILABLE / STOCK_INSUFFICIENT） */
  const validItems = computed(() => items.value.filter(item => item.selectable))
  /** 失效的明细（下架 / 无货 / 规格失效），界面上置灰并单独成组 */
  const invalidItems = computed(() => items.value.filter(item => !item.selectable))

  const selectedIdSet = computed(() => new Set(selectedIds.value))
  const selected = computed(() => sumSelected(items.value, selectedIdSet.value))
  const selectedCount = computed(() => selected.value.count)
  const selectedQuantity = computed(() => selected.value.quantity)
  const selectedAmount = computed(() => selected.value.amount)
  const validCount = computed(() => validItems.value.length)
  const allSelected = computed(() => validCount.value > 0 && selectedCount.value === validCount.value)
  const hasInvalid = computed(() => invalidItems.value.length > 0)

  /** 某条明细是否被勾选 */
  function isSelected(itemId: string): boolean {
    return selectedIdSet.value.has(itemId)
  }

  /** 剔除已不存在的 id：防止"刷新后仍显示已选 N 件"的幽灵勾选 */
  function pruneSelection(next: StoreCart) {
    const alive = new Set(next.items.filter(item => item.selectable).map(item => item.id))
    const kept = selectedIds.value.filter(id => alive.has(id))
    if (kept.length !== selectedIds.value.length) {
      selectedIds.value = kept
    }
  }

  function applyCart(next: StoreCart) {
    cart.value = next
    count.value = { totalItems: next.summary.totalItems, totalQuantity: next.summary.totalQuantity }
    pruneSelection(next)
  }

  /** 首次拿到购物车时默认勾选所有可结算条目（只做一次，之后尊重用户的手动勾选） */
  function ensureDefaultSelection() {
    if (selectionInitialized.value) return
    selectionInitialized.value = true
    const selectable = cart.value.items.filter(item => item.selectable).map(item => item.id)
    if (selectable.length) {
      selectedIds.value = Array.from(new Set([...selectedIds.value, ...selectable]))
    }
  }

  function toggleSelect(itemId: string) {
    if (selectedIdSet.value.has(itemId)) {
      selectedIds.value = selectedIds.value.filter(id => id !== itemId)
    } else {
      selectedIds.value = [...selectedIds.value, itemId]
    }
  }

  function toggleSelectAll() {
    selectedIds.value = allSelected.value ? [] : validItems.value.map(item => item.id)
  }

  /** 拉取购物车（需登录）。未登录时静默置 unauthorized，不抛异常——页面要能正常渲染登录引导 */
  async function fetchCart(): Promise<StoreCart> {
    restoreUser()
    if (!isLoggedIn.value) {
      unauthorized.value = true
      cart.value = emptyCart()
      selectedIds.value = []
      return cart.value
    }
    // latest-wins：连续调用 fetchCart（切语言/进购物车页 + Header 同时拉）时，
    // 只让最后一次的结果落到共享状态上，避免旧响应覆盖新响应。
    const isLatest = cartGuard.begin()
    loading.value = true
    try {
      const next = normalizeCart(await requestFetch('/api/cart', { query: { lang: locale.value } }))
      if (!isLatest()) return next
      unauthorized.value = false
      applyCart(next)
      ensureDefaultSelection()
      return next
    } catch (error) {
      // 过期请求的错误不能覆盖状态：更晚的请求还在路上（或已成功），
      // 这里静默返回当前状态，把"失败"的判定交给最后一次请求。
      if (!isLatest()) return cart.value
      if (isUnauthorizedError(error)) {
        unauthorized.value = true
        cart.value = emptyCart()
        selectedIds.value = []
        return cart.value
      }
      throw error
    } finally {
      // 旧请求不许把 loading 关掉：更新的请求可能还在路上
      if (isLatest()) loading.value = false
    }
  }

  /**
   * 页面级「重新拉购物车」。
   *
   * 页面会把它交给 useLazyAsyncData('cart-page', () => reload())：
   *   · 返回**购物车对象**（而非 undefined）—— useAsyncData 的 handler 必须返回 truthy，
   *     否则 Nuxt 会在客户端重复执行一次（见 Nuxt useAsyncData 文档）；
   *   · 出错时按 fetchCart 的语义抛给调用方（购物车页的 reload 会捕获成 loadError，
   *     页面需要错误态时用那个；这里不吞异常，避免"点了重试却没反应"）。
   */
  async function reload(): Promise<StoreCart> {
    return await fetchCart()
  }

  /** 只刷新角标（顶部导航用，失败静默：角标不该干扰页面主体） */
  async function refreshCount() {
    restoreUser()
    if (!isLoggedIn.value) {
      count.value = { totalItems: 0, totalQuantity: 0 }
      unauthorized.value = true
      return
    }
    const isLatest = countGuard.begin()
    try {
      const next = normalizeCartCount(await requestFetch('/api/cart/count'))
      if (!isLatest()) return
      count.value = next
      unauthorized.value = false
    } catch (error) {
      if (!isLatest()) return
      // ★ 401/403 说明 cookie 里的 token 已经失效（登录过期），
      // 但 localStorage 的 user-info 还在，isLoggedIn 仍是 true。
      // 这里必须把本地登录态一起清掉，否则顶部会一直显示头像 + 旧角标，
      // 而用户其实已经掉线 —— 这正是"登录过期了头像还在"的成因之一。
      // 清掉之后 refreshSession() 的语义在这里被复用（它内部会同步 localStorage）。
      if (isUnauthorizedError(error)) {
        count.value = { totalItems: 0, totalQuantity: 0 }
        unauthorized.value = true
        await refreshSession()
        return
      }
      // 其他错误（网络抖动等）保持上一次的数字：为一个小徽标弹错误提示只会打扰用户
    }
  }

  async function addItem(params: {
    productId: string
    skuId?: string | null
    quantity?: number
    source?: string
  }): Promise<StoreCart> {
    mutating.value = true
    try {
      const next = normalizeCart(await requestFetch('/api/cart/items', {
        method: 'POST',
        body: {
          productId: params.productId,
          skuId: params.skuId ?? '',
          quantity: params.quantity ?? 1,
          source: params.source ?? 'api',
          lang: locale.value,
        },
      }))
      applyCart(next)
      // 新加入的条目默认勾选：用户刚加的东西多半就是想买的
      const added = next.items.find(item => item.productId === params.productId
        && (item.skuId || '') === (params.skuId || ''))
      if (added && added.selectable && !selectedIdSet.value.has(added.id)) {
        selectedIds.value = [...selectedIds.value, added.id]
      }
      return next
    } finally {
      mutating.value = false
    }
  }

  async function updateQuantity(itemId: string, quantity: number): Promise<StoreCart> {
    mutating.value = true
    try {
      const next = normalizeCart(await requestFetch(`/api/cart/items/${encodeURIComponent(itemId)}`, {
        method: 'PUT',
        body: { quantity, source: 'cart', lang: locale.value },
      }))
      applyCart(next)
      return next
    } finally {
      mutating.value = false
    }
  }

  async function removeItem(itemId: string): Promise<StoreCart> {
    mutating.value = true
    try {
      const next = normalizeCart(await requestFetch(`/api/cart/items/${encodeURIComponent(itemId)}`, {
        method: 'DELETE',
        query: { lang: locale.value },
      }))
      applyCart(next)
      return next
    } finally {
      mutating.value = false
    }
  }

  async function batchRemove(ids: string[]): Promise<StoreCart> {
    mutating.value = true
    try {
      const next = normalizeCart(await requestFetch('/api/cart/batch-remove', {
        method: 'POST',
        body: { ids, source: 'cart', lang: locale.value },
      }))
      applyCart(next)
      return next
    } finally {
      mutating.value = false
    }
  }

  async function clearCart(): Promise<StoreCart> {
    mutating.value = true
    try {
      const next = normalizeCart(await requestFetch('/api/cart', {
        method: 'DELETE',
        query: { lang: locale.value },
      }))
      applyCart(next)
      return next
    } finally {
      mutating.value = false
    }
  }

  async function clearInvalid(): Promise<StoreCart> {
    mutating.value = true
    try {
      const next = normalizeCart(await requestFetch('/api/cart/invalid', {
        method: 'DELETE',
        query: { lang: locale.value },
      }))
      applyCart(next)
      return next
    } finally {
      mutating.value = false
    }
  }

  return {
    // state
    cart,
    items,
    summary,
    count,
    loading,
    mutating,
    unauthorized,
    selectedIds,
    // computed
    validItems,
    invalidItems,
    validCount,
    selectedCount,
    selectedQuantity,
    selectedAmount,
    allSelected,
    hasInvalid,
    // select helpers
    isSelected,
    toggleSelect,
    toggleSelectAll,
    // actions
    fetchCart,
    /** 页面级重新拉取（useLazyAsyncData('cart-page', () => reload()) 的入口） */
    reload,
    refreshCount,
    addItem,
    updateQuantity,
    removeItem,
    batchRemove,
    clearCart,
    clearInvalid,
  }
}

/** 类型再导出，方便页面只 import 一处 */
export type { StoreCart, StoreCartCount, StoreCartItem }
