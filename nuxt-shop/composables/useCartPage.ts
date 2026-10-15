// composables/useCartPage.ts
//
// 购物车页的数据与操作层：购物车条目、勾选与金额、无效商品、凑单规则、猜你喜欢、删除/改数量/结算。
//
// 为什么抽出来：pages/cart.vue 原先 718 行，模板（四种状态 + 凑单/无效/推荐卡片 + 确认弹层）
// 与这些逻辑混在一起。模板留在页面，逻辑收拢到这里。
//
// 三条硬规则（与页面注释一致，勿改）：
//   1) 只有后端下发的字段参与判定（valid/selected 等），前端不自己重算"能不能买"；
//   2) 金额只做两位小数格式化与加减展示，最终应付金额由结算页调服务端重算；
//   3) 「失效商品」单独成区，不与正常条目混排。
//
// ⚠️ 本函数是 async（内部要 await useStoreProductList 拉推荐），
//    await 之后的代码已脱离 setup 同步上下文 —— 因此这里**不再调用任何 composable**，
//    生命周期（onMounted(reload)）留在页面注册。

import { extractCartErrorMessage, useCart } from '~~/composables/useCart'
import { writeCheckoutSnapshot } from '~~/composables/useCheckoutSnapshot'
import { useStorePrice, useStoreProductList } from '~~/composables/useProducts'
import { useLoginLink } from '~~/composables/useLoginLink'
import { toNumberOrNull } from '~~/types/shop-product'
import type { StoreCartItem } from '~~/types/shop-cart'

export const useCartPage = async () => {
  const { t, locale } = useI18n()
  const localePath = useLocalePath()
  const toast = useToast()
  const { formatCompact } = useStorePrice()

  const {
    items,
    summary,
    loading,
    mutating,
    unauthorized,
    validItems,
    invalidItems,
    validCount,
    selectedQuantity,
    selectedAmount,
    allSelected,
    hasInvalid,
    isSelected,
    toggleSelect,
    toggleSelectAll,
    fetchCart,
    updateQuantity,
    removeItem,
    batchRemove,
    clearCart,
    clearInvalid,
  } = useCart()

  /** SSR 首屏不拉购物车：服务端相对路径 $fetch 不会带 httpOnly cookie，必然 401（保持原逻辑） */
  const ready = ref(false)
  const loadError = ref('')
  const pendingIds = ref<string[]>([])

  /** 失效商品分组默认折叠（重新设计的要求：只占一行高） */
  const invalidOpen = ref(false)

  const confirmState = reactive({
    open: false,
    kind: 'clear' as 'clear' | 'remove' | 'invalid',
  })

  const loginLink = useLoginLink()

  /** 登录态下才显示"共 N 件商品"；未登录显示登录引导文案，已登录空车则交给空状态卡片说明 */
  const showSubtitle = computed(() => !unauthorized.value && items.value.length > 0)

  // ---------------------------------------------------------------------------
  // 凑单进度（真实促销数据）
  // ---------------------------------------------------------------------------

  /**
   * 进行中的促销规则（BFF：server/api/promotions/active.get.ts）。
   * - query/key 都跟着 locale 变：key 一变 Nuxt 就会重新取，切换语言时活动标题跟着变
   *   （取法与首页 index.vue 一致，只是 key 多了 store-cart- 前缀，避免两个调用点共享缓存时
   *    选项不一致）；
   * - 带 lang 是因为后端将来要按语言解析活动标题（接口注释里写明显式 ?lang= 优先）；
   * - server: false —— 与购物车一致，首屏不在服务端发这个请求；
   * - default: () => [] —— 接口不存在 / 请求失败 / 返回空数组都退化成"没有促销"，
   *   绝不会让购物车页崩掉。
   */
  const { data: activePromotions } = useFetch<unknown[]>('/api/promotions/active', {
    query: computed(() => ({ lang: locale.value })),
    key: computed(() => `store-cart-active-promotions-${locale.value}`),
    default: () => [],
    server: false,
  })

  interface BundleRule {
    /** 满减门槛，来自后端活动配置 */
    target: number
    /** 已凑单金额：后端给了就用后端的，否则用本地真实的"已选商品金额" */
    current: number
  }

  /**
   * 从活动列表里挑出「满减凑单」规则。
   *
   * 【重要】当前 BFF 的 ShopActivePromotion 只返回
   *   { id, type, title, startTime, endTime, linkUrl, productId }
   * ——**没有满减门槛字段**（threshold_amount 只在 docs/store-redesign/后端促销字段清单.md
   * §2.1 的表结构里，Java 接口尚未提供）。因此这里的判定是：
   *   读不到门槛（thresholdAmount / threshold）就返回 null → 凑单卡整块不渲染。
   * 宁可不显示，也绝不前端写死"满 300 减 30"。等后端补上门槛与已凑单金额后，
   * 这里不用改就能正确渲染。
   */
  const bundleRule = computed<BundleRule | null>(() => {
    const list = Array.isArray(activePromotions.value) ? activePromotions.value : []
    const now = Date.now()
    for (const raw of list) {
      const entry = (raw ?? {}) as Record<string, unknown>
      const type = String(entry.type ?? '').toUpperCase()
      // 只有满减类活动才有"凑单门槛"；直降/折扣/秒杀拿来做进度条是误导
      if (type && type !== 'FULL_CUT') continue

      // 活动时间窗：已结束 / 还没开始的活动都不该拿来引导凑单（时间字段与 BFF 契约一致）
      const startAt = Date.parse(String(entry.startTime ?? ''))
      const endAt = Date.parse(String(entry.endTime ?? ''))
      if (Number.isFinite(endAt) && endAt <= now) continue
      if (Number.isFinite(startAt) && startAt > now) continue

      const target = toNumberOrNull(entry.thresholdAmount) ?? toNumberOrNull(entry.threshold)
      if (target === null || target <= 0) continue
      const backendCurrent = toNumberOrNull(entry.currentAmount) ?? toNumberOrNull(entry.current)
      return { target, current: backendCurrent ?? selectedAmount.value }
    }
    return null
  })

  const bundlePercent = computed(() => {
    if (!bundleRule.value) return 0
    const ratio = bundleRule.value.current / bundleRule.value.target
    return Math.min(100, Math.max(0, Math.round(ratio * 100)))
  })

  const bundleText = computed(() => bundleRule.value
    ? t('shop.cartBundleProgress', {
        current: formatCompact(bundleRule.value.current),
        target: formatCompact(bundleRule.value.target),
      })
    : '')

  // ---------------------------------------------------------------------------
  // 结算明细的真实数据
  // ---------------------------------------------------------------------------

  /** 勾选的可结算条目（批量删除、运费判断共用同一份口径） */
  const selectedItems = computed(() => validItems.value.filter(item => isSelected(item.id)))

  /** 购物车明细当前还没有 freeShipping 字段（见 types/shop-cart.ts）；后端补上后这里自动生效 */
  type CartItemWithShipping = StoreCartItem & { freeShipping?: boolean }

  /** 只有"全部已选商品都确认包邮"才算免运费；拿不到依据时为 false → 卡上显示无数据占位 */
  const allSelectedFreeShipping = computed(() => (
    selectedItems.value.length > 0
    && selectedItems.value.every(item => (item as CartItemWithShipping).freeShipping === true)
  ))

  /**
   * 优惠券抵扣。
   * 后端没有「用户可用券」接口（见 docs/store-redesign/后端促销字段清单.md §2.3），
   * 所以这里恒为 null：优惠券行、"已为你节省"整块不渲染，也绝不会凭空减掉一个金额。
   * 接口上线后只需给这个 computed 赋真实值，模板不用改。
   */
  const couponDiscount = computed<number | null>(() => null)

  /** 应付总额 = 商品总额 - 真实优惠（当前没有优惠，所以等于商品总额） */
  const payable = computed(() => (
    Math.max(0, Math.round((selectedAmount.value - (couponDiscount.value ?? 0)) * 100) / 100)
  ))

  const savedAmount = computed(() => (
    couponDiscount.value !== null && couponDiscount.value > 0 ? couponDiscount.value : null
  ))

  // ---------------------------------------------------------------------------
  // 猜你喜欢
  // ---------------------------------------------------------------------------

  const likePageNo = ref(1)
  const { data: likePage } = await useStoreProductList({ pageNo: likePageNo, pageSize: 4 })
  const likeProducts = computed(() => likePage.value?.records ?? [])

  // ---------------------------------------------------------------------------
  // 交互（逻辑与重构前保持一致）
  // ---------------------------------------------------------------------------

  const confirmCopy = computed(() => {
    if (confirmState.kind === 'remove') {
      return {
        title: t('cart.confirmRemoveTitle'),
        description: t('cart.confirmRemoveDesc', { count: selectedQuantity.value }),
      }
    }
    if (confirmState.kind === 'invalid') {
      return {
        title: t('cart.clearInvalid'),
        description: t('cart.confirmInvalidDesc', { count: summary.value.invalidItems }),
      }
    }
    return {
      title: t('cart.confirmClearTitle'),
      description: t('cart.confirmClearDesc', { count: summary.value.totalItems }),
    }
  })

  function askConfirm(kind: 'clear' | 'remove' | 'invalid') {
    if (kind === 'remove' && !selectedItems.value.length) return
    confirmState.kind = kind
    confirmState.open = true
  }

  function notifyError(error: unknown) {
    toast.add({
      title: t('cart.actionFailed'),
      description: extractCartErrorMessage(error, t('cart.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  }

  async function runConfirmedAction() {
    confirmState.open = false
    // 先取快照：清空/清理之后 summary 会立刻归零，届时再读就只剩 0 了
    const invalidCount = summary.value.invalidItems
    try {
      if (confirmState.kind === 'remove') {
        const ids = items.value.filter(item => item.selectable && isSelected(item.id)).map(item => item.id)
        if (!ids.length) return
        await batchRemove(ids)
        toast.add({ title: t('cart.removedToast'), color: 'success', icon: 'lucide:check' })
      } else if (confirmState.kind === 'invalid') {
        await clearInvalid()
        toast.add({
          title: invalidCount
            ? t('cart.invalidClearedToast', { count: invalidCount })
            : t('cart.noInvalidToast'),
          color: 'success',
          icon: 'lucide:check',
        })
      } else {
        await clearCart()
        toast.add({ title: t('cart.clearedToast'), color: 'success', icon: 'lucide:check' })
      }
    } catch (error) {
      notifyError(error)
    }
  }

  /** 单条操作统一走"标记 pending → 调接口 → 清 pending"，防止连点重复提交 */
  async function withPending(itemId: string, action: () => Promise<unknown>) {
    if (pendingIds.value.includes(itemId)) return
    pendingIds.value = [...pendingIds.value, itemId]
    try {
      await action()
    } catch (error) {
      notifyError(error)
    } finally {
      pendingIds.value = pendingIds.value.filter(id => id !== itemId)
    }
  }

  async function handleRemove(itemId: string) {
    await withPending(itemId, async () => {
      await removeItem(itemId)
      toast.add({ title: t('cart.removedToast'), color: 'success', icon: 'lucide:check' })
    })
  }

  async function handleQuantity(itemId: string, quantity: number) {
    const item = items.value.find(entry => entry.id === itemId)
    if (!item || quantity < 1 || quantity === item.quantity) return
    await withPending(itemId, () => updateQuantity(itemId, quantity))
  }

  /**
   * 去结算（本次新增）。
   *
   * 只把「已勾选且可结算」的条目传给结算页：购物车明细 ID + 商品 + 规格 + 数量，
   * **不传任何金额** —— 结算页会调 /api/order/preview 由服务端重新算钱，
   * 这样"购物车看到的价格"与"实际下单的价格"必然同源。
   *
   * 用 sessionStorage 而不是内存状态：结算页刷新后不该把用户踢回购物车；
   * 用 sessionStorage 而不是 localStorage：关掉标签页这份"待下单清单"就该消失。
   * cartItemId 会随下单一起提交，后端据此**只清除这些条目**，
   * 购物车里没勾选的商品不会被顺手删掉。
   *
   * 快照的读写收敛在 composables/useCheckoutSnapshot.ts（购物车 / 立即购买 / 结算页三处共用）。
   */
  async function goCheckout() {
    const chosen = selectedItems.value
    if (!chosen.length) return
    const written = writeCheckoutSnapshot(chosen.map(item => ({
      cartItemId: item.id,
      productId: item.productId,
      skuId: item.skuId || '',
      quantity: item.quantity,
    })))
    if (!written) {
      // sessionStorage 不可用（隐私模式）时不能静默跳转：否则结算页会显示"没有待结算商品"，
      // 用户完全不知道发生了什么
      toast.add({
        title: t('cart.checkoutSnapshotFailed'),
        color: 'error',
        icon: 'lucide:circle-alert',
      })
      return
    }
    await navigateTo(localePath('/checkout'))
  }

  async function reload() {
    loadError.value = ''
    try {
      await fetchCart()
    } catch (error) {
      loadError.value = extractCartErrorMessage(error, t('cart.loadErrorDesc'))
    } finally {
      ready.value = true
    }
  }


  return {
    items,
    summary,
    loading,
    mutating,
    unauthorized,
    validItems,
    invalidItems,
    validCount,
    selectedQuantity,
    selectedAmount,
    allSelected,
    hasInvalid,
    isSelected,
    toggleSelect,
    toggleSelectAll,
    fetchCart,
    updateQuantity,
    removeItem,
    batchRemove,
    clearCart,
    clearInvalid,
    ready,
    loadError,
    pendingIds,
    invalidOpen,
    confirmState,
    loginLink,
    showSubtitle,
    bundleRule,
    bundlePercent,
    bundleText,
    selectedItems,
    allSelectedFreeShipping,
    couponDiscount,
    payable,
    savedAmount,
    likeProducts,
    confirmCopy,
    askConfirm,
    runConfirmedAction,
    handleRemove,
    handleQuantity,
    goCheckout,
    reload,
  }
}

