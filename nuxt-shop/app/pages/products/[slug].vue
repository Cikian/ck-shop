<template>
  <div class="min-h-screen bg-[var(--color-background)] pt-navbar">
    <!-- Loading State -->
    <div v-if="pending" class="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
      <div class="grid gap-10 lg:grid-cols-2">
        <div class="space-y-4">
          <div class="aspect-square animate-pulse rounded-[var(--radius-card)] bg-[var(--color-surface-secondary)]"></div>
          <div class="grid grid-cols-4 gap-3">
            <div v-for="i in 4" :key="i" class="aspect-square animate-pulse rounded-[var(--radius-button)] bg-[var(--color-surface-secondary)]"></div>
          </div>
        </div>
        <div class="space-y-6">
          <div class="h-8 w-1/3 animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
          <div class="h-10 w-2/3 animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
          <div class="h-4 w-1/4 animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
          <div class="h-12 w-1/3 animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
          <div class="space-y-2">
            <div class="h-4 w-full animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
            <div class="h-4 w-full animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
            <div class="h-4 w-2/3 animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 商品不存在（404） -->
    <div v-else-if="isNotFound" class="mx-auto max-w-7xl px-4 py-20 text-center sm:px-6 lg:px-8">
      <div class="relative mx-auto">
        <div class="absolute inset-0 animate-pulse rounded-full bg-[var(--color-error)]/10 blur-2xl"></div>
        <div class="relative flex h-20 w-20 items-center justify-center rounded-full border border-[var(--color-border)] bg-[var(--color-surface-secondary)] glass">
          <UIcon name="lucide:package-x" class="h-10 w-10 text-[var(--color-error)]" />
        </div>
      </div>
      <h2 class="mt-8 text-2xl font-bold text-[var(--color-primary)]">
        {{ t('product.notFound') }}
      </h2>
      <p class="mt-2 text-sm text-[var(--color-text-muted)]">
        {{ t('product.notFoundDesc') }}
      </p>
      <NuxtLinkLocale
        to="/products"
        class="mt-6 inline-flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-white transition-all duration-200 hover:bg-[var(--color-primary-700)] motion-reduce:transition-none btn-shine"
      >
        <UIcon name="lucide:arrow-left" class="h-4 w-4" />
        {{ t('product.backToProducts') }}
      </NuxtLinkLocale>
    </div>

    <!-- 其它错误（例如后端未启动） -->
    <div v-else-if="error || !product" class="mx-auto max-w-7xl px-4 py-20 text-center sm:px-6 lg:px-8">
      <div class="mx-auto flex h-20 w-20 items-center justify-center rounded-full border border-[var(--color-border)] bg-[var(--color-surface-secondary)]">
        <UIcon name="lucide:wifi-off" class="h-10 w-10 text-[var(--color-error)]" />
      </div>
      <h2 class="mt-8 text-2xl font-bold text-[var(--color-primary)]">
        {{ t('product.loadError') }}
      </h2>
      <p class="mt-2 text-sm text-[var(--color-text-muted)]">
        {{ t('product.loadErrorDesc') }}
      </p>
      <div class="mt-6 flex items-center justify-center gap-3">
        <button
          type="button"
          class="inline-flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-white transition-all duration-200 hover:bg-[var(--color-primary-700)] motion-reduce:transition-none"
          @click="refresh()"
        >
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" />
          {{ t('common.retry') }}
        </button>
        <NuxtLinkLocale
          to="/products"
          class="inline-flex items-center gap-2 rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white/60 px-6 py-3 text-sm font-semibold text-[var(--color-text-secondary)] transition-all duration-200 hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] motion-reduce:transition-none"
        >
          {{ t('product.backToProducts') }}
        </NuxtLinkLocale>
      </div>
    </div>

    <!-- Product Detail -->
    <div v-else class="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <!-- Breadcrumb -->
      <nav class="mb-8 flex items-center gap-2 text-sm">
        <NuxtLinkLocale
          to="/"
          class="flex items-center gap-1 text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-primary)]"
        >
          <UIcon name="lucide:home" class="h-3.5 w-3.5" />
          {{ t('common.home') }}
        </NuxtLinkLocale>
        <span class="text-[var(--color-border)]">
          <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5" />
        </span>
        <NuxtLinkLocale
          to="/products"
          class="text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-primary)]"
        >
          {{ t('products.title') }}
        </NuxtLinkLocale>
        <span class="text-[var(--color-border)]">
          <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5" />
        </span>
        <span class="line-clamp-1 font-medium text-[var(--color-text-secondary)]">{{ product.title }}</span>
      </nav>

      <div class="grid gap-10 lg:grid-cols-2 lg:gap-16">
        <!-- 图集：主图 + 缩略图切换 -->
        <div class="space-y-4">
          <div class="relative overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)] bg-[var(--color-surface-secondary)] glass">
            <img
              v-if="mainImage"
              :key="mainImage"
              :src="mainImage"
              :alt="product.title"
              class="aspect-square w-full animate-scale-in object-cover motion-reduce:animate-none"
            />
            <div v-else class="flex aspect-square w-full items-center justify-center text-[var(--color-text-muted)]">
              <UIcon name="lucide:image-off" class="h-16 w-16" />
            </div>

            <!-- 标签 + 推荐 -->
            <div v-if="product.tags.length || product.isRecommend === 'Y'" class="absolute left-4 top-4 flex flex-wrap gap-2">
              <span
                v-if="product.isRecommend === 'Y'"
                class="inline-flex items-center gap-1 rounded-full bg-[var(--color-primary)] px-3 py-1 text-xs font-bold text-white shadow-lg"
              >
                <UIcon name="lucide:thumbs-up" class="h-3 w-3" />
                {{ t('products.recommended') }}
              </span>
              <span
                v-for="tag in product.tags"
                :key="tag.id"
                class="inline-flex items-center gap-1 rounded-full bg-[var(--color-accent)] px-3 py-1 text-xs font-bold text-white shadow-lg shadow-[var(--color-accent)]/30"
              >
                {{ tag.name }}
              </span>
            </div>

            <!-- 收藏 / 分享 -->
            <div class="absolute right-4 top-4 flex flex-col gap-2">
              <button
                type="button"
                class="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full bg-white/90 text-[var(--color-text-secondary)] shadow-lg backdrop-blur-sm transition-all duration-300 hover:bg-[var(--color-primary)] hover:text-white motion-reduce:transition-none"
                :aria-label="t('product.share')"
                @click="handleShare"
              >
                <UIcon name="lucide:share-2" class="h-4 w-4" />
              </button>
              <button
                type="button"
                class="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full bg-white/90 shadow-lg backdrop-blur-sm transition-all duration-300 motion-reduce:transition-none"
                :class="isWishlisted ? 'bg-[var(--color-error)] text-white' : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-error)] hover:text-white'"
                :aria-label="t('product.addToWishlist')"
                :aria-pressed="isWishlisted"
                @click="isWishlisted = !isWishlisted"
              >
                <UIcon name="lucide:heart" class="h-4 w-4" :class="{ 'fill-current': isWishlisted }" />
              </button>
            </div>
          </div>

          <div v-if="gallery.length > 1" class="grid grid-cols-4 gap-3">
            <button
              v-for="(img, idx) in gallery"
              :key="`${img}-${idx}`"
              type="button"
              class="cursor-pointer overflow-hidden rounded-[var(--radius-button)] border-2 transition-all duration-300 motion-reduce:transition-none"
              :class="activeImageIndex === idx
                ? 'border-[var(--color-accent)] shadow-lg shadow-[var(--color-accent)]/20'
                : 'border-transparent opacity-60 hover:border-[var(--color-border)] hover:opacity-100'"
              :aria-label="t('product.galleryItem', { index: idx + 1 })"
              :aria-current="activeImageIndex === idx ? 'true' : undefined"
              @click="activeImageIndex = idx"
            >
              <img :src="img" :alt="`${product.title} ${idx + 1}`" class="aspect-square w-full object-cover" loading="lazy" />
            </button>
          </div>
        </div>

        <!-- 信息 -->
        <div class="flex flex-col">
          <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
            <span v-if="product.categoryName" class="inline-flex items-center gap-1 font-semibold text-[var(--color-accent)]">
              <UIcon name="lucide:bookmark" class="h-3.5 w-3.5" />
              {{ product.categoryName }}
            </span>
            <span v-if="product.spuCode" class="text-[var(--color-text-muted)]">
              {{ t('product.spuCode') }}: {{ product.spuCode }}
            </span>
          </div>

          <h1 class="mt-3 text-3xl font-bold leading-tight text-[var(--color-primary)] sm:text-4xl">
            {{ product.title }}
          </h1>
          <p v-if="product.subTitle" class="mt-2 text-base text-[var(--color-text-muted)]">
            {{ product.subTitle }}
          </p>

          <!-- 价格（SKU 价优先） -->
          <div class="mt-6 flex flex-wrap items-baseline gap-3">
            <span class="text-4xl font-bold text-[var(--color-primary)]">{{ displayPrice }}</span>
            <span v-if="product.unit" class="text-sm text-[var(--color-text-muted)]">/ {{ product.unit }}</span>
            <span
              v-if="!combination && product.minPrice !== null && product.maxPrice !== null && product.minPrice !== product.maxPrice"
              class="text-sm text-[var(--color-text-muted)]"
            >
              {{ t('products.priceRangeHint') }}
            </span>
          </div>

          <!-- 库存 -->
          <div class="mt-4 flex flex-wrap items-center gap-2">
            <span
              class="inline-flex h-2.5 w-2.5 rounded-full"
              :class="displayStock > 0 ? 'bg-[var(--color-success)]' : 'bg-[var(--color-error)]'"
            ></span>
            <span class="text-sm font-medium" :class="displayStock > 0 ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'">
              {{ displayStock > 0 ? t('products.stock.inStock') : t('products.stock.outOfStock') }}
            </span>
            <span v-if="displayStock > 0" class="text-sm text-[var(--color-text-muted)]">
              ({{ t('products.stock.remaining', { count: displayStock }) }})
            </span>
          </div>

          <!-- 规格选择器（核心交互：不存在 / 不可售组合置灰） -->
          <div v-if="hasSpecs" class="mt-8">
            <h2 class="text-sm font-bold uppercase tracking-wide text-[var(--color-text-secondary)]">
              {{ t('products.spec.title') }}
            </h2>
            <div class="mt-4">
              <ProductSpecPicker
                :specs="specs"
                :combinations="combinations"
                :selected="selected"
                @update:selected="onSelectionChange"
              />
            </div>

            <!-- 组合状态提示 -->
            <p v-if="unavailableNotice" class="mt-4 inline-flex items-center gap-2 text-sm" :class="unavailableNotice.error ? 'text-[var(--color-error)]' : 'text-[var(--color-text-muted)]'">
              <UIcon :name="unavailableNotice.error ? 'lucide:ban' : 'lucide:info'" class="h-4 w-4" />
              {{ unavailableNotice.text }}
            </p>
            <p v-else-if="combination" class="mt-4 inline-flex items-center gap-2 text-sm text-[var(--color-text-secondary)]">
              <UIcon name="lucide:package-check" class="h-4 w-4 text-[var(--color-success)]" />
              {{ combination.combinationName || combination.combinationKey }}
            </p>
          </div>

          <!-- 数量 + 加购 -->
          <div class="mt-8">
            <div class="flex flex-col gap-4 sm:flex-row sm:items-center">
              <div class="flex items-center rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white shadow-sm">
                <button
                  type="button"
                  class="flex h-12 w-12 cursor-pointer items-center justify-center rounded-l-[var(--radius-button)] text-[var(--color-text-secondary)] transition-all duration-200 hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-primary)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
                  :disabled="quantity <= 1"
                  :aria-label="t('product.decreaseQuantity')"
                  @click="changeQuantity(-1)"
                >
                  <UIcon name="lucide:minus" class="h-4 w-4" />
                </button>
                <span class="w-12 text-center text-base font-bold text-[var(--color-primary)]">{{ quantity }}</span>
                <button
                  type="button"
                  class="flex h-12 w-12 cursor-pointer items-center justify-center rounded-r-[var(--radius-button)] text-[var(--color-text-secondary)] transition-all duration-200 hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-primary)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
                  :disabled="quantity >= maxQuantity"
                  :aria-label="t('product.increaseQuantity')"
                  @click="changeQuantity(1)"
                >
                  <UIcon name="lucide:plus" class="h-4 w-4" />
                </button>
              </div>

              <!-- 购物车/结算还没有实现：按钮保持禁用并如实说明，不再假装加购成功 -->
              <button
                type="button"
                disabled
                aria-disabled="true"
                class="flex flex-1 items-center justify-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-accent)] px-8 py-3.5 text-sm font-bold text-white transition-all duration-300 disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
              >
                <UIcon name="lucide:shopping-bag" class="h-5 w-5" />
                {{ t('product.cartComingSoon') }}
              </button>
            </div>

            <p class="mt-3 text-xs text-[var(--color-text-muted)]">
              {{ t('product.cartComingSoonHint') }}
            </p>

            <div v-if="buyable" class="mt-4 flex items-center justify-between rounded-[var(--radius-button)] bg-[var(--color-surface-secondary)] px-4 py-3">
              <span class="text-sm text-[var(--color-text-muted)]">{{ t('product.totalItems', { count: quantity }) }}</span>
              <span class="text-xl font-bold text-[var(--color-primary)]">{{ totalPrice }}</span>
            </div>
          </div>

          <!-- 描述 -->
          <div class="mt-8">
            <h2 class="text-sm font-bold uppercase tracking-wide text-[var(--color-text-secondary)]">
              {{ t('product.description') }}
            </h2>
            <!-- description 是管理端录入的富文本（后端契约：富文本 HTML） -->
            <div
              v-if="product.description"
              class="mt-3 text-base leading-relaxed text-[var(--color-text-secondary)] [&_a]:text-[var(--color-accent)] [&_h2]:mt-4 [&_h2]:font-bold [&_h3]:mt-4 [&_h3]:font-bold [&_img]:rounded-[var(--radius-button)] [&_li]:ml-5 [&_li]:list-disc [&_p]:mb-3"
              v-html="product.description"
            ></div>
            <p v-else class="mt-3 text-sm text-[var(--color-text-muted)]">{{ t('product.noDescription') }}</p>
          </div>

          <!-- 原「服务承诺」三卡（满 $99 免运费 / 安全支付 / 30 天无忧退换）已删除：
               后台没有运费、支付、退换货这些概念，属于没有数据支撑的承诺。 -->
        </div>
      </div>

      <!-- 相关商品（真实接口数据，同分类） -->
      <section v-if="related.length" class="mt-20">
        <div class="mb-10 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <span class="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
              <UIcon name="lucide:sparkles" class="h-4 w-4" />
              {{ t('product.relatedSubtitle') }}
            </span>
            <h2 class="mt-3 text-2xl font-bold text-[var(--color-primary)] sm:text-3xl">
              {{ t('product.relatedTitle') }}
            </h2>
          </div>
          <NuxtLinkLocale
            to="/products"
            class="group inline-flex items-center gap-2 text-sm font-semibold text-[var(--color-primary)] transition-colors duration-200 hover:text-[var(--color-accent)]"
          >
            {{ t('common.viewMore') }}
            <UIcon name="lucide:arrow-right" class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-1 motion-reduce:transition-none motion-reduce:group-hover:translate-x-0" />
          </NuxtLinkLocale>
        </div>

        <div class="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-4">
          <StoreProductCard
            v-for="(item, idx) in related"
            :key="item.id"
            :product="item"
            :style="{ animationDelay: `${idx * 80}ms` }"
            class="animate-fade-in-up opacity-0"
          />
        </div>
      </section>
    </div>

    <!-- Footer -->
    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import type { SpecSelection, StoreSkuCombination } from '~~/types/shop-product'
import {
  findSelectedCombination,
  isCombinationSellable,
  isSelectionComplete,
  resolveGallery,
  resolveSkuPrice,
} from '~~/types/shop-product'
import { useStorePrice, useStoreProductDetail, useStoreProductList } from '~~/composables/useProducts'

const route = useRoute()
const { t } = useI18n()

const slug = computed(() => String(route.params.slug ?? ''))

const { data: product, pending, error, refresh } = await useStoreProductDetail(slug)
const { format, formatRange } = useStorePrice()

const quantity = ref(1)
const activeImageIndex = ref(0)
const isWishlisted = ref(false)
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

/** 画廊：选中 SKU 带配图时，把它放到主图位置 */
const gallery = computed(() => {
  const base = product.value ? resolveGallery(product.value) : []
  const skuImage = combination.value?.imageUrl
  if (!skuImage) return base
  return [skuImage, ...base.filter(url => url !== skuImage)]
})

const mainImage = computed(() => gallery.value[activeImageIndex.value] ?? gallery.value[0] ?? '')

/** 展示价：命中 SKU 用 SKU 价，否则用商品价格区间 */
const displayPrice = computed(() => {
  if (!product.value) return '--'
  if (combination.value) return format(resolveSkuPrice(combination.value, product.value))
  return formatRange(product.value.minPrice, product.value.maxPrice, product.value.price)
})

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

const relatedPageNo = ref(1)
const { data: relatedPage } = await useStoreProductList({
  pageNo: relatedPageNo,
  pageSize: 5,
  categoryId: computed(() => product.value?.categoryId ?? undefined),
})
const related = computed(() =>
  (relatedPage.value?.records ?? []).filter(item => item.id !== product.value?.id).slice(0, 4),
)

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
  },
)

async function handleShare() {
  if (!import.meta.client) return
  try {
    await navigator.clipboard.writeText(window.location.href)
  } catch {
    // 剪贴板不可用时静默降级（当前无 toast 体系）
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
