<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <!-- ===================== 加载态 ===================== -->
    <div v-if="pending" class="mx-auto max-w-7xl px-2 py-10 sm:px-3 lg:px-4">
      <div class="grid gap-10 lg:grid-cols-2 lg:gap-12">
        <div class="space-y-4">
          <div class="ui-card aspect-square animate-pulse bg-[var(--color-surface-secondary)]"></div>
          <div class="grid grid-cols-5 gap-3">
            <div v-for="i in 5" :key="`thumb-skeleton-${i}`" class="aspect-square animate-pulse rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)]"></div>
          </div>
        </div>
        <div class="space-y-5">
          <div class="h-5 w-1/3 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
          <div class="h-9 w-3/4 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
          <div class="h-4 w-1/2 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
          <div class="h-24 w-full animate-pulse rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)]"></div>
          <div class="h-12 w-2/3 animate-pulse rounded-full bg-[var(--color-surface-secondary)]"></div>
          <div class="h-12 w-full animate-pulse rounded-full bg-[var(--color-surface-secondary)]"></div>
        </div>
      </div>
    </div>

    <!-- ===================== 404 ===================== -->
    <div v-else-if="isNotFound" class="mx-auto max-w-7xl px-2 py-20 text-center sm:px-3 lg:px-4">
      <span class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-surface-secondary)]">
        <UIcon name="lucide:package-x" class="h-10 w-10 text-[var(--color-brand-500)]" aria-hidden="true" />
      </span>
      <h1 class="mt-6 text-2xl font-black text-[var(--color-ink)]">{{ t('product.notFound') }}</h1>
      <p class="mt-2 text-sm text-[var(--color-muted)]">{{ t('product.notFoundDesc') }}</p>
      <NuxtLinkLocale
        to="/products"
        class="ui-pill ui-pill-brand mt-6 h-11 px-6 text-sm"
      >
        <UIcon name="lucide:arrow-left" class="h-4 w-4" aria-hidden="true" />
        {{ t('product.backToProducts') }}
      </NuxtLinkLocale>
    </div>

    <!-- ===================== 其它错误 ===================== -->
    <div v-else-if="error || !product" class="mx-auto max-w-7xl px-2 py-20 text-center sm:px-3 lg:px-4">
      <span class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-error-50)]">
        <UIcon name="lucide:wifi-off" class="h-10 w-10 text-[var(--color-error)]" aria-hidden="true" />
      </span>
      <h1 class="mt-6 text-2xl font-black text-[var(--color-ink)]">{{ t('product.loadError') }}</h1>
      <p class="mt-2 text-sm text-[var(--color-muted)]">{{ t('product.loadErrorDesc') }}</p>
      <div class="mt-6 flex flex-wrap items-center justify-center gap-3">
        <button type="button" class="ui-pill ui-pill-brand h-11 px-6 text-sm" @click="refresh()">
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
          {{ t('common.retry') }}
        </button>
        <NuxtLinkLocale to="/products" class="ui-pill ui-pill-ghost h-11 px-6 text-sm">
          {{ t('product.backToProducts') }}
        </NuxtLinkLocale>
      </div>
    </div>

    <!-- ===================== 商品详情 ===================== -->
    <div v-else class="mx-auto max-w-7xl px-2 sm:px-3 lg:px-4">
      <!-- 面包屑 -->
      <nav class="no-scrollbar flex items-center gap-2 overflow-x-auto py-5 text-[13px] text-[var(--color-muted)]" :aria-label="t('cart.breadcrumbLabel')">
        <NuxtLinkLocale to="/" class="flex shrink-0 items-center gap-1 transition-colors hover:text-[var(--color-brand-600)]">
          <UIcon name="lucide:home" class="h-3.5 w-3.5" aria-hidden="true" />
          {{ t('common.home') }}
        </NuxtLinkLocale>
        <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5 shrink-0 text-[var(--color-line)]" aria-hidden="true" />
        <NuxtLinkLocale to="/products" class="shrink-0 transition-colors hover:text-[var(--color-brand-600)]">
          {{ t('products.all') }}
        </NuxtLinkLocale>
        <template v-if="product.categoryName">
          <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5 shrink-0 text-[var(--color-line)]" aria-hidden="true" />
          <NuxtLinkLocale
            v-if="product.categoryId"
            :to="`/categories/${product.categoryId}`"
            class="shrink-0 transition-colors hover:text-[var(--color-brand-600)]"
          >
            {{ product.categoryName }}
          </NuxtLinkLocale>
          <span v-else class="shrink-0">{{ product.categoryName }}</span>
        </template>
        <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5 shrink-0 text-[var(--color-line)]" aria-hidden="true" />
        <span class="line-clamp-1 font-medium text-[var(--color-ink-soft)]">{{ product.title }}</span>
      </nav>

      <!-- ===================== 上半部：左图右信息 ===================== -->
      <div class="grid gap-8 lg:grid-cols-2 lg:gap-12">
        <!-- 左：图集（组件见 components/product/Gallery.vue；当前图下标仍由本页持有，切规格时会重置） -->
        <ProductGallery
          v-model:active-index="activeImageIndex"
          :main-image="mainImage"
          :gallery="gallery"
          :title="product.title"
          :is-recommend="product.isRecommend === 'Y'"
          :badges="imageBadges"
          :favorited="favorited"
          :favorite-pending="favoritePending"
          @share="handleShare"
          @toggle-favorite="handleToggleFavorite"
        />
        <!-- 右：商品信息（保持原有全部功能） -->
        <div class="flex flex-col">
          <!-- 分类 + 商品编码 -->
          <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px]">
            <span v-if="product.categoryName" class="inline-flex items-center gap-1 font-bold text-[var(--color-brand-600)]">
              <UIcon name="lucide:bookmark" class="h-3.5 w-3.5" aria-hidden="true" />
              {{ product.categoryName }}
            </span>
            <span v-if="product.spuCode" class="text-[var(--color-muted)]">
              {{ t('product.spuCode') }}: {{ product.spuCode }}
            </span>
          </div>

          <h1 class="mt-2.5 text-2xl font-black leading-tight text-[var(--color-ink)] sm:text-3xl">
            {{ product.title }}
          </h1>
          <p v-if="product.subTitle" class="mt-2 text-sm text-[var(--color-muted)]">
            {{ product.subTitle }}
          </p>

          <!-- 价格块：超大现价 + 划线价 + 直降价签 -->
          <div class="price-block mt-5 px-5 py-4">
            <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <span class="price text-[38px] leading-none sm:text-[42px]">{{ priceText }}</span>
              <span v-if="product.unit" class="text-sm text-[var(--color-muted)]">/ {{ product.unit }}</span>
            </div>
            <div v-if="priceView.showOriginal || priceView.discountAmount !== null" class="mt-2.5 flex flex-wrap items-center gap-2">
              <span v-if="priceView.showOriginal" class="price-strike text-sm">
                {{ formatCompact(priceView.originalPrice) }}
              </span>
              <span v-if="priceView.discountAmount !== null" class="badge badge-hot">
                {{ t('shop.discountOff', { amount: formatCompact(priceView.discountAmount) }) }}
              </span>
            </div>
            <!--
              价格区间提示：**必须先占位再隐藏，不能用 v-if 移除**。
              选中具体规格后这句提示就不该再出现，但用 v-if 直接把它从 DOM 删掉会让价格块矮一行，
              它下面的所有内容（统计行、规格、按钮…）整体上移 —— 用户点规格时页面会"跳"一下。
              改用 visibility:hidden 保留占位：它不参与绘制、不进入无障碍树，但**仍然占高度**，
              于是价格块高度在"未选规格 ↔ 已选规格"之间恒定不变。
              外层还用 hasPriceRange 兜一道：不跨价格区间的商品（本来就不会出现这句提示）
              不预留这一行，避免白白多出一段空白。
            -->
            <p
              v-if="hasPriceRange"
              class="mt-2 text-xs text-[var(--color-muted)]"
              :class="{ invisible: !showRangeHint }"
            >
              {{ t('products.priceRangeHint') }}
            </p>
          </div>

          <!-- 统计行：销量 / 评分 / 收藏，各自为 0 时该项不渲染 -->
          <div v-if="hasAnyStat" class="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-[13px] text-[var(--color-muted)]">
            <span v-if="product.salesCount > 0" class="inline-flex items-center gap-1.5">
              <UIcon name="lucide:flame" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
              {{ t('shop.sold', { count: formatCompactCount(product.salesCount) }) }}
            </span>
            <span v-if="rating !== null" class="inline-flex items-center gap-1.5">
              <UIcon name="lucide:star" class="h-4 w-4 fill-current text-[var(--color-accent-500)]" aria-hidden="true" />
              {{ ratingText }}
            </span>
            <span v-if="displayFavoriteCount > 0" class="inline-flex items-center gap-1.5">
              <UIcon name="lucide:heart" class="h-4 w-4 text-[var(--color-brand-400)]" aria-hidden="true" />
              {{ t('shop.profileFavorites') }} {{ formatCompactCount(displayFavoriteCount) }}
            </span>
          </div>

          <!-- 库存 -->
          <div class="mt-4 flex flex-wrap items-center gap-2 text-[13px]">
            <span
              class="h-2.5 w-2.5 rounded-full"
              :class="displayStock > 0 ? 'bg-[var(--color-success)]' : 'bg-[var(--color-error)]'"
              aria-hidden="true"
            ></span>
            <span class="font-bold" :class="displayStock > 0 ? 'text-[var(--color-success-600)]' : 'text-[var(--color-error-600)]'">
              {{ displayStock > 0 ? t('products.stock.inStock') : t('products.stock.outOfStock') }}
            </span>
            <span v-if="displayStock > 0" class="text-[var(--color-muted)]">
              {{ t('products.stock.remaining', { count: displayStock }) }}
            </span>
          </div>

          <!-- 规格选择器（核心交互：不存在 / 不可售组合置灰） -->
          <div v-if="hasSpecs" class="mt-7">
            <h2 class="text-sm font-bold text-[var(--color-ink)]">{{ t('products.spec.title') }}</h2>
            <div class="mt-3">
              <ProductSpecPicker
                :specs="specs"
                :combinations="combinations"
                :selected="selected"
                @update:selected="onSelectionChange"
              />
            </div>

            <!-- 组合状态提示 -->
            <p
              v-if="unavailableNotice"
              class="mt-3 inline-flex items-center gap-2 text-sm"
              :class="unavailableNotice.error ? 'text-[var(--color-error-600)]' : 'text-[var(--color-muted)]'"
            >
              <UIcon :name="unavailableNotice.error ? 'lucide:ban' : 'lucide:info'" class="h-4 w-4" aria-hidden="true" />
              {{ unavailableNotice.text }}
            </p>
            <p v-else-if="combination" class="mt-3 inline-flex items-center gap-2 text-sm text-[var(--color-ink-soft)]">
              <UIcon name="lucide:package-check" class="h-4 w-4 text-[var(--color-success)]" aria-hidden="true" />
              {{ combination.combinationName || combination.combinationKey }}
            </p>
          </div>

          <!-- 操作区（两行两列）+ 合计 + 服务标签：组件见 components/product/PurchaseBar.vue
               数量/收藏态/加载态由本页持有（与规格选择、后端关系耦合），组件只渲染并抛回动作 -->
          <ProductPurchaseBar
            :quantity="quantity"
            :max-quantity="maxQuantity"
            :can-buy-now="canBuyNow"
            :buying="buying"
            :buy-now-label="buyNowLabel"
            :favorited="favorited"
            :favorite-pending="favoritePending"
            :can-add-to-cart="canAddToCart"
            :adding="adding"
            :add-to-cart-label="addToCartLabel"
            :total-price="totalPrice"
            :buyable="buyable"
            :has-fast-tag="hasFastTag"
            :has-top-tag="hasTopTag"
            :free-shipping="!!product.freeShipping"
            @change-quantity="changeQuantity"
            @buy-now="handleBuyNow"
            @add-to-cart="handleAddToCart"
            @toggle-favorite="handleToggleFavorite"
          />
        </div>
      </div>

      <!-- 下半部：吸顶锚点导航 + 单列四区块（商品详情 → 规格参数 → 用户评价 → 为你推荐）
           组件见 components/product/DetailSections.vue：锚点高亮/滚动依赖这四块的 DOM，
           放在同一组件里就不必把 DOM 引用传回页面再传下来。 -->
      <ProductDetailSections
        :description="product.description"
        :spec-rows="specRows"
        :has-review="hasReview"
        :rating="rating"
        :rating-text="ratingText"
        :rating-stars="ratingStars"
        :review-count="product.reviewCount ?? 0"
        :related="related"
      />
    </div>

    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import type { SpecSelection } from '~~/types/shop-product'
import { formatCompactCount } from '~~/types/shop-product'
import { useStorePrice, useStoreProductList } from '~~/composables/useProducts'
import { useProductDetail } from '~~/composables/useProductDetail'
import { extractCartErrorMessage, useCart } from '~~/composables/useCart'
import { extractFavoriteErrorMessage } from '~~/composables/useFavorites'
import { writeCheckoutSnapshot } from '~~/composables/useCheckoutSnapshot'
import { useAuth } from '~~/composables/useAuth'

/**
 * 商品详情页。
 *
 * 布局契约（用户明确要求）：
 *   上半部 = 左图集 / 右信息 两栏；
 *   下半部 = **单列竖向堆叠**，四个模块依次为 商品详情 → 规格参数 → 用户评价 → 为你推荐，
 *           模块之间只有极浅分隔线，绝不并排成多栏。
 *
 * 购买闭环（2026-10-14 扩充）：
 *   加入购物车 / **立即购买** / **收藏** 三个动作都在右侧信息区，
 *   口径完全一致——未登录先引导登录、有规格必须选全、规格组合必须可售；
 *   三个动作都不做本地乐观更新，全部以服务端返回的状态为准：
 *     · 加购后购物车整体由 useCart 用后端返回值替换；
 *     · 立即购买把这一件写进结算快照，金额由结算页调服务端重算（前端不碰钱）；
 *     · 收藏由 useProductFavorite 回填 favorited 与收藏数。
 *
 * 数据契约：所有模块都遵守「零值不渲染」——
 *   评分 / 评价数 / 销量 / 收藏数为 0 时不渲染对应元素；
 *   规格参数只用后端真实字段（spuCode / categoryName）拼，没有数据的行不出现；
 *   评价模块只展示真实汇总（ratingAvg + reviewCount），后端没有评价接口就**不编造评价列表**。
 */
const route = useRoute()
const { t } = useI18n()
const toast = useToast()
const localePath = useLocalePath()
const { isLoggedIn } = useAuth()
const { addItem } = useCart()
const { format, formatCompact } = useStorePrice()

// 数据与派生状态全部来自 composables/useProductDetail.ts ——
// 该页面曾近 1060 行，先把数据层收拢，模板拆组件时才有清晰边界。
const {
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
  ratingStars,
  displayFavoriteCount,
  hasAnyStat,
  specRows,
  hasReview,
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
  tagCodes,
  hasFastTag,
  hasTopTag,
} = await useProductDetail()

// ===== 为你推荐：同分类真实商品，排除当前商品，最多 4 张 =====
// 这段留在页面而不是 composable：它要按「详情里的 categoryId」过滤，必须等详情拿到后再发起；
// 而 async composable 在自身 await 之后已脱离 setup 上下文，不能再调 useStoreProductList。
const relatedPageNo = ref(1)
const { data: relatedPage } = await useStoreProductList({
  pageNo: relatedPageNo,
  pageSize: 5,
  categoryId: computed(() => product.value?.categoryId ?? undefined),
})
const related = computed(() =>
  (relatedPage.value?.records ?? []).filter(item => item.id !== product.value?.id).slice(0, 4),
)

// 锚点导航（吸顶 + 滚动高亮 + 点击滚动）随下半部区块一起搬进 components/product/DetailSections.vue：
// 它依赖那四块的 DOM 引用与"是否渲染"，放在同一组件里比分传 DOM 更清晰。

onMounted(() => {
  // 收藏态只能客户端查（需要 access-token cookie；SSR 期间还没恢复登录态）
  void refreshFavorite()
})

const isNotFound = computed(() => (error.value as any)?.statusCode === 404)

/** 切换选择时重置数量与主图 */
function onSelectionChange(next: SpecSelection) {
  selected.value = next
  quantity.value = 1
  activeImageIndex.value = 0
}

function changeQuantity(delta: number) {
  quantity.value = Math.min(Math.max(1, quantity.value + delta), maxQuantity.value)
}

// 商品切换（同为详情路由参数变化）时重置本地状态
watch(
  () => product.value?.id,
  () => {
    selected.value = {}
    quantity.value = 1
    activeImageIndex.value = 0
    // 收藏态同样要跟着换：否则会显示"上一件商品已收藏"的心形
    void refreshFavorite()
  },
)

async function handleShare() {
  if (!import.meta.client) return
  try {
    await navigator.clipboard.writeText(window.location.href)
  } catch {
    // 剪贴板不可用时静默降级（与重构前一致的行为）
  }
}

/**
 * 加入购物车。
 *
 * 失败一律把后端的原话展示出来（例如"库存不足：最多可购买 3 件…"），
 * 不在这里替换成"操作失败"这种没有信息量的兜底文案——
 * 后端已经算清楚了原因，前端再包一层只会让用户猜。
 */
async function handleAddToCart() {
  if (!product.value || adding.value) return

  if (!isLoggedIn.value) {
    await navigateTo(localePath({ path: '/login', query: { redirect: route.fullPath } }))
    return
  }
  if (hasSpecs.value && !selectionComplete.value) {
    toast.add({ title: t('product.selectSpecFirst'), color: 'warning', icon: 'lucide:info' })
    return
  }

  adding.value = true
  try {
    await addItem({
      productId: product.value.id,
      // 无规格商品必须传空串，后端据此区分"无规格"与"未选规格"
      skuId: combination.value?.id ?? '',
      quantity: quantity.value,
      source: 'detail',
    })
    toast.add({ title: t('product.addedToCart'), color: 'success', icon: 'lucide:check' })
    quantity.value = 1
  } catch (error) {
    toast.add({
      title: t('cart.actionFailed'),
      description: extractCartErrorMessage(error, t('cart.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    adding.value = false
  }
}

/**
 * 立即购买。
 *
 * 与"加入购物车"的**唯一区别**：不写购物车，而是把"这一件 + 已选规格 + 数量"直接写进结算快照，
 * 跳到 /checkout 由服务端算钱。三个前置校验与加购同源（登录态 / 规格选全 / 可售），
 * 避免出现"能加购却买不了"或者反过来的自相矛盾。
 *
 * 快照写入失败（隐私模式禁用 sessionStorage）时必须显式提示：
 * 否则用户会被送到结算页看到"没有待结算商品"，完全不知道是自己浏览器的问题。
 */
async function handleBuyNow() {
  if (!product.value || buying.value) return

  if (!isLoggedIn.value) {
    await navigateTo(localePath({ path: '/login', query: { redirect: route.fullPath } }))
    return
  }
  if (hasSpecs.value && !selectionComplete.value) {
    toast.add({ title: t('product.selectSpecFirst'), color: 'warning', icon: 'lucide:info' })
    return
  }
  if (!buyable.value) return

  const written = writeCheckoutSnapshot([{
    // 立即购买不经过购物车：cartItemId 空串 -> 下单成功后后端不清任何购物车条目
    cartItemId: '',
    productId: product.value.id,
    // 无规格商品必须传空串（后端据此区分"无规格"与"未选规格"）
    skuId: combination.value?.id ?? '',
    quantity: quantity.value,
  }])
  if (!written) {
    toast.add({ title: t('product.buyNowFailed'), color: 'error', icon: 'lucide:circle-alert' })
    return
  }

  buying.value = true
  try {
    await navigateTo(localePath('/checkout'))
  } finally {
    buying.value = false
  }
}

/**
 * 收藏 / 取消收藏。
 *
 * 未登录时先引导登录（与加购同一套交互）：收藏是用户维度的数据，没有"游客收藏"这回事。
 * 状态一律取接口返回值，不做本地取反 —— 已收藏/未收藏的实时状态只有后端知道。
 */
async function handleToggleFavorite() {
  if (!product.value || favoritePending.value) return

  if (!isLoggedIn.value) {
    await navigateTo(localePath({ path: '/login', query: { redirect: route.fullPath } }))
    return
  }

  try {
    const status = await toggleFavorite()
    toast.add({
      title: status.favorited ? t('product.addedToWishlist') : t('product.removedFromWishlist'),
      color: 'success',
      icon: 'lucide:heart',
    })
  } catch (error) {
    toast.add({
      title: t('product.wishlistFailed'),
      description: extractFavoriteErrorMessage(error, t('cart.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  }
}

useSeoMeta({
  title: () => product.value?.seoTitle || product.value?.title || t('product.notFound'),
  description: () => product.value?.seoDescription || product.value?.subTitle || '',
  ogTitle: () => product.value?.seoTitle || product.value?.title || '',
  ogDescription: () => product.value?.seoDescription || product.value?.subTitle || '',
  ogImage: () => product.value?.coverUrl || product.value?.mainImage || '',
})
</script>
