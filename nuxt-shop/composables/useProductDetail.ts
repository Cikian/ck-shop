// composables/useProductDetail.ts
//
// 商品详情页的**数据与派生状态**（详情 + 推荐 + 图集 + 规格组合 + 价格/库存/可购判断）。
//
// 为什么抽出来：pages/products/[slug].vue 原先约 1060 行，数据、派生、交互、模板全堆在一起。
// 拆成组件之前先把数据层收拢到一处，模板各块才能只依赖明确的输入 ——
// 否则每个新组件都要从页面 props 里掏一堆彼此纠缠的 computed。
//
// 边界：这里只做"取数与派生"，不含交互副作用（加购/立即购买/收藏/复制链接仍由页面处理）。
// 必须是**同步**函数：页面在 setup 顶层直接调用、不 await（详见 useStoreProductDetail 处说明）。
// 数据是 useAsyncData 的 ref，SSR 同样会等它 resolve 后再输出 HTML；
// pending / error / default 与改造前一致。
// 目前唯一消费者是该页面，因此用普通 ref（非 useState）即可；若将来拆出的子组件也需要
// 这些状态，再按需把共享的那几个提升为 useState 或改为 props 传递。

import type { ShopTagRef, SpecSelection, StoreSkuCombination } from '~~/types/shop-product'
import {
  findSelectedCombination,
  formatCompactCount,
  isCombinationSellable,
  isPriceRange,
  isSelectionComplete,
  resolveGallery,
  resolvePriceView,
  resolveRating,
  resolveSkuPrice,
} from '~~/types/shop-product'
import { useStorePrice, useStoreProductDetail } from '~~/composables/useProducts'
import { useProductFavorite } from '~~/composables/useFavorites'
import { useAuth } from '~~/composables/useAuth'

export const useProductDetail = () => {
  const route = useRoute()
  const { t } = useI18n()
  const { format, formatCompact } = useStorePrice()
  // 加购/立即购买要先判断登录态（未登录时这两个按钮的文案与行为不同）
  const { isLoggedIn } = useAuth()

  const slug = computed(() => String(route.params.slug ?? ''))

  /**
   * 商品详情数据（useAsyncData）。
   *
   * 为什么**不在内部 await**：
   *   1) 页面现在直接 `const {...} = useProductDetail()`（不 await），
   *      让 setup 保持同步 —— 之前那个顶层 await 会把整页导航阻塞一个 RTT；
   *   2) Nuxt 的 useAsyncData 即使不被 await，SSR 也会等数据 resolve 后再渲染
   *      （见 Nuxt 文档「Even if you do not await the return value, during SSR Nuxt
   *      will wait for the request to finish」），所以首屏 HTML 仍然有商品数据；
   *      pending / error / default 的语义完全不变，模板照旧按 pending / error 分支。
   *   3) 不再有"await 之后脱离 setup 上下文"的约束，下面所有 composable 都是同步调用。
   * ⚠️ 唯一区别：**setup 体内**不能同步读到 product.value（此时还是 default 的 null）；
   *    需要派生值请一律用 computed（本文件里的派生值都是 computed），不要在 setup
   *    顶部用 `const title = product.value?.title` 这种一次性读取。
   */
  const { data: product, pending, error, refresh } = useStoreProductDetail(slug)

  /**
   * 收藏态（favorited）/ 商品收藏总数 / 请求中。
   * 收藏是**后端真实关系**（ck_shop_favorite），任何一次切换都以接口返回的状态为准。
   */
  const { favorited, favoriteCount, pending: favoritePending, refresh: refreshFavorite, toggle: toggleFavorite } = useProductFavorite(slug)

  // 交互状态（数量 / 提交中 / 图集下标 / 已选规格）
  const quantity = ref(1)
  const adding = ref(false)
  const buying = ref(false)
  const activeImageIndex = ref(0)
  const selected = ref<SpecSelection>({})

  const specs = computed(() => product.value?.specs ?? [])
  const combinations = computed(() => product.value?.combinations ?? [])
  const hasSpecs = computed(() => specs.value.length > 0)

  /** 当前选择解析出的 SKU 组合（可能为 null：没选全 / 组合不存在） */
  const combination = computed<StoreSkuCombination | null>(() =>
    product.value ? findSelectedCombination(specs.value, combinations.value, selected.value) : null,
  )

  const selectionComplete = computed(() => isSelectionComplete(specs.value, selected.value))
  const isSkuSellable = computed(() => (combination.value ? isCombinationSellable(combination.value) : false))

  /**
   * 画廊：选中 SKU 带配图时，把它顶到主图位（原有行为）。
   * 用 activeImageIndex 记录缩略图选中项，gallery 变化时索引 0 即 SKU 图。
   */
  const gallery = computed(() => {
    const base = product.value ? resolveGallery(product.value) : []
    const skuImage = combination.value?.imageUrl
    if (!skuImage) return base
    return [skuImage, ...base.filter(url => url !== skuImage)]
  })

  const mainImage = computed(() => gallery.value[activeImageIndex.value] ?? gallery.value[0] ?? '')

  /**
   * 价格视图：命中 SKU 时按 SKU 价算「划线价 / 直降金额」，否则用商品价格区间最低值。
   * 计算仍走 types 层唯一的 resolvePriceView（把 SKU 价当成 price 传进去），
   * 不在页面里重写一套「原价是否高于现价」的判断。
   */
  const priceView = computed(() => {
    const current = product.value
    if (!current) {
      return { price: null, originalPrice: null, showOriginal: false, discountAmount: null }
    }
    const skuPrice = combination.value ? resolveSkuPrice(combination.value, current) : null
    return resolvePriceView(
      skuPrice === null
        ? current
        : { price: skuPrice, minPrice: null, maxPrice: null, originalPrice: current.originalPrice },
    )
  })

  /** 展示价：命中 SKU 用 SKU 价；跨价格区间且未选规格时展示区间（避免用最低价误导） */
  const priceText = computed(() => {
    const current = product.value
    if (!current) return '--'
    if (!combination.value && isPriceRange(current)) {
      return `${formatCompact(current.minPrice)} - ${formatCompact(current.maxPrice)}`
    }
    return priceView.value.price === null ? '--' : formatCompact(priceView.value.price)
  })

  /** 只有跨价格区间且尚未命中 SKU 时才提示「价格可能随规格不同而变化」 */
  const showRangeHint = computed(() => !!product.value && !combination.value && isPriceRange(product.value))

  /**
   * 本商品是否**可能**出现价格区间提示（与当前选择无关）。
   *
   * 它决定价格块要不要为提示行预留高度：留着占位才不会在"选中规格 → 提示消失"时让页面跳动；
   * 而本来就不跨区间的商品不会出现这句提示，也就没必要预留（否则价格块会一直多一段空白）。
   */
  const hasPriceRange = computed(() => (!!product.value && isPriceRange(product.value)))

  /**
   * 评分：无评价时 resolveRating 返回 null → 统计行里这一项不渲染。
   *
   * ⚠️ 这里的 rating / reviewCount 都来自商品接口（后端已改为**实时聚合评价表**的真实值），
   * 只用于**统计行**的一个数字；评价区（分数 + 星级分布 + 列表）走 composables/useReviews.ts，
   * 那边才是权威来源。两处口径由同一份聚合 SQL 保证一致（见 docs/真实统计与评价模块-接口契约.md）。
   */
  const rating = computed(() => (product.value ? resolveRating(product.value) : null))
  /** 评分的展示值在脚本里算好，模板只做渲染（避免模板里对可空值做运算） */
  const ratingText = computed(() => (rating.value === null ? '' : rating.value.toFixed(1)))

  /** 统计行：三项全无数据时整行不渲染（收藏数优先用收藏接口回读的最新值，那也是真实 COUNT） */
  const displayFavoriteCount = computed(() => favoriteCount.value ?? product.value?.favoriteCount ?? 0)
  const hasAnyStat = computed(() =>
    !!product.value
    && (product.value.salesCount > 0 || displayFavoriteCount.value > 0 || rating.value !== null),
  )

  /**
   * 规格参数行：后端**没有**参数表，所以只能用商品自带的真实字段拼，
   * 一行没有数据就不渲染整块（绝不编造「品牌 / 屏幕 / 电池」这类字段）。
   * 单位（unit）没有可用的 i18n key，因此不在这里成行展示，而是跟在价格后面（`/ 个`）。
   */
  const specRows = computed<{ labelKey: string; value: string }[]>(() => {
    const current = product.value
    if (!current) return []
    const rows: { labelKey: string; value: string }[] = []
    if (current.spuCode) rows.push({ labelKey: 'product.spuCode', value: current.spuCode })
    if (current.categoryName) rows.push({ labelKey: 'nav.categories', value: current.categoryName })
    return rows
  })

  /** 评价区（分数/分布/列表）**不在这里**：见 composables/useReviews.ts 的 useProductReviews，
   *  它按商品主键实时拉评价接口，0 条时整块不渲染。 */

  /** 展示库存：命中 SKU 用 SKU 库存，否则用商品总库存 */
  const displayStock = computed(() => combination.value?.stock ?? product.value?.stock ?? 0)

  const maxQuantity = computed(() => Math.max(1, displayStock.value))
  /** 单价：命中 SKU 用 SKU 价，否则回退商品基准价 */
  const unitPrice = computed(() => (product.value ? resolveSkuPrice(combination.value, product.value) : null))
  const totalPrice = computed(() => format((unitPrice.value ?? 0) * quantity.value))

  /** 可加购：有规格时必须命中且可售；无规格时看商品库存 */
  const buyable = computed(() => {
    if (!product.value) return false
    if (hasSpecs.value) return !!combination.value && isSkuSellable.value
    return displayStock.value > 0
  })

  const unavailableNotice = computed<{ text: string; error: boolean } | null>(() => {
    if (!hasSpecs.value) {
      return displayStock.value > 0 ? null : { text: t('product.outOfStock'), error: true }
    }
    if (!selectionComplete.value) return { text: t('products.spec.choose'), error: false }
    if (!combination.value) return { text: t('products.spec.notSaleable'), error: true }
    if (!isSkuSellable.value) {
      return combination.value.saleable === 'N'
        ? { text: t('products.spec.notSaleable'), error: true }
        : { text: t('products.spec.outOfStock'), error: true }
    }
    return null
  })

  /**
   * 能否点"加入购物车"。
   * - 未登录：**保持可点**，点击后引导登录（直接置灰会让用户不知道为什么要登录）；
   * - 有规格但没选全：不可点（上方已有"请选择完整规格"的提示）；
   * - 其余情况取决于是否命中可售组合 / 是否有货。
   */
  const canAddToCart = computed(() => {
    if (!product.value) return false
    if (!isLoggedIn.value) return true
    if (hasSpecs.value && !selectionComplete.value) return false
    return buyable.value
  })

  /*
   * 动作条按钮文案：**永远只显示动作名**（加入购物车 / 立即购买），不再因为"未登录/未选规格"
   * 替换成长句（第 5 轮，需求方反馈）。
   * 原因：动作条按钮宽度固定，收藏按钮还要竖着放图标 + 小字；"登录后加入购物车"这类长文案
   * 在中文/英文/法文下都会溢出或截断。引导本来就有别的出口：
   *   · 未登录 → 点按钮直接跳登录页（见页面 handleAddToCart / handleBuyNow）；
   *   · 未选规格 → 按钮置灰 + 面板里已有「请选择完整规格」提示（unavailableNotice）。
   */
  const addToCartLabel = computed(() => t('product.addToCart'))

  /**
   * 立即购买可用性：与加购**同一口径**（同一个 canAddToCart），
   * 不做第二套判断 —— 两个按钮对同一件商品的"能不能买"必须给出一致的结论。
   */
  const canBuyNow = computed(() => canAddToCart.value)

  // 与加购同一口径：只显示动作名（未登录/未选规格的引导见 addToCartLabel 上方注释）
  const buyNowLabel = computed(() => t('product.buyNow'))

  /** 图片角标：只渲染真实存在的 HOT / SALE / NEW / PICK（与 StoreProductCard 同一套判断） */
  const IMAGE_BADGE_CODES = new Set(['HOT', 'SALE', 'NEW', 'PICK'])
  const imageBadges = computed<ShopTagRef[]>(() => {
    const limit = product.value?.isRecommend === 'Y' ? 2 : 3
    return (product.value?.tags || [])
      .filter(tag => IMAGE_BADGE_CODES.has((tag.code || '').toUpperCase()))
      .slice(0, limit)
  })

  // 「为你推荐」列表**不在这里**：它要按详情里的 categoryId 过滤，
  // 放在页面里 watch product 的 id/categoryId 再请求更直接
  // （见 pages/products/[slug].vue；推荐位在页面底部，不需要进 SSR）。
  return {
    slug,
    product,
    pending,
    error,
    refresh,
    quantity,
    adding,
    buying,
    activeImageIndex,
    selected,
    favorited,
    favoriteCount,
    favoritePending,
    refreshFavorite,
    toggleFavorite,
    specs,
    combinations,
    hasSpecs,
    combination,
    selectionComplete,
    isSkuSellable,
    gallery,
    mainImage,
    priceView,
    priceText,
    showRangeHint,
    hasPriceRange,
    rating,
    ratingText,
    displayFavoriteCount,
    hasAnyStat,
    specRows,
    displayStock,
    maxQuantity,
    unitPrice,
    totalPrice,
    buyable,
    unavailableNotice,
    canAddToCart,
    addToCartLabel,
    canBuyNow,
    buyNowLabel,
    imageBadges,
  }
}
