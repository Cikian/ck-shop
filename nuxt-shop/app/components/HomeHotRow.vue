<template>
  <!--
    「热销爆款」横排（设计稿第 4 行）。
    商品是**按真实销量排序**取回来的（sortBy=sales → 后端 month_sales_count DESC），
    没有销量数据时这一整块不渲染 —— 不会出现一排"0 件已售"的假热销。
  -->
  <section v-if="items.length" class="ui-card mt-5 p-3 sm:p-4" :aria-label="t('shop.hotTitle')">
    <div class="relative">
      <!--
        移动端横排（清单 S18）：加 snap 对齐 + 右侧渐变与箭头，明确告诉用户"还能往右滑"。
        以前只有一个 overflow-x-auto：390px 下第一张卡只露出约 190px，看起来像被截断。
      -->
      <div
        ref="scrollerRef"
        class="no-scrollbar flex snap-x snap-mandatory gap-3 overflow-x-auto scroll-smooth lg:grid lg:grid-cols-[minmax(160px,1fr)_repeat(6,minmax(0,1fr))] lg:snap-none lg:overflow-visible"
        @scroll.passive="updateHint"
      >
        <!-- 标题块 -->
        <div class="flex w-[168px] shrink-0 snap-start flex-col justify-center rounded-[var(--radius-tile)] bg-[var(--color-brand-50)] p-4 lg:w-auto">
          <h2 class="flex items-center gap-1.5 text-lg font-black leading-tight text-[var(--color-ink)]">
            <UIcon name="lucide:flame" class="h-5 w-5 shrink-0 text-[var(--color-brand-500)]" aria-hidden="true" />
            {{ t('shop.hotTitle') }}
          </h2>
          <p class="mt-1 text-xs leading-snug text-[var(--color-muted)]">{{ t('shop.hotSubtitle') }}</p>
        </div>

        <!-- 紧凑商品项：图左 + 信息右 -->
        <NuxtLinkLocale
          v-for="product in items"
          :key="product.id"
          :to="`/products/${product.id}`"
          class="group flex w-[196px] shrink-0 snap-start items-start gap-2.5 rounded-[var(--radius-tile)] p-2 transition-colors hover:bg-[var(--color-brand-50)] lg:w-auto"
        >
          <div class="h-16 w-16 shrink-0 overflow-hidden rounded-[12px] bg-[var(--color-surface-secondary)]">
            <ShopImage
              :src="coverOf(product)"
              :alt="product.title"
              width="64"
              height="64"
              class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105 motion-reduce:transition-none motion-reduce:group-hover:scale-100"
              icon-class="h-5 w-5"
            />
          </div>

          <div class="min-w-0 flex-1">
            <p class="line-clamp-2 text-[13px] font-medium leading-snug text-[var(--color-ink)] transition-colors group-hover:text-[var(--color-brand-600)]">
              {{ product.title }}
            </p>

            <p class="mt-1.5 flex flex-wrap items-baseline gap-x-1.5">
              <span class="price text-[17px] leading-none">{{ priceOf(product) }}</span>
              <span v-if="strikeOf(product)" class="price-strike text-[11px]">{{ strikeOf(product) }}</span>
            </p>

            <!-- 折扣百分比由真实的 originalPrice / price 算出，不是写死的营销数字 -->
            <span v-if="discountPercentOf(product) !== null" class="badge badge-sale mt-1">
              -{{ discountPercentOf(product) }}%
            </span>
          </div>
        </NuxtLinkLocale>
      </div>

      <!-- 右侧渐变遮罩 + 箭头：仅在"还能继续右滑"时出现，滚到末尾自动消失 -->
      <div
        v-if="canScrollRight"
        class="pointer-events-none absolute inset-y-0 right-0 w-12 bg-gradient-to-l from-white via-white/80 to-transparent lg:hidden"
        aria-hidden="true"
      />
      <button
        v-if="canScrollRight"
        type="button"
        class="absolute right-1 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full bg-white/95 text-[var(--color-brand-600)] shadow-[var(--shadow-md)] lg:hidden"
        :aria-label="t('shop.hotScrollHint')"
        :title="t('shop.hotScrollHint')"
        @click="scrollRight"
      >
        <UIcon name="lucide:chevron-right" class="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { StoreProductCard } from '~~/types/shop-product'
import { resolveCoverUrl, resolvePriceView } from '~~/types/shop-product'
import { useStorePrice } from '~~/composables/useProducts'

/**
 * 「热销爆款」横排。
 *
 * 为什么单独做一个紧凑卡片而不是复用 StoreProductCard：
 * 设计稿里这一排是"一屏扫过去"的密度（图小、信息一行），
 * 直接塞 6 张完整卡片会把它变成第二排商品网格，失去"爆款横条"的节奏。
 * 但价格/划线/角标的判断仍然**全部复用 types 层的同一个函数**，不重写第二套逻辑。
 */
const props = defineProps<{
  products: StoreProductCard[]
}>()

const { t } = useI18n()
const { formatCompact } = useStorePrice()

const items = computed(() => (props.products || []).slice(0, 6))

/** 横向滚动容器：用来判断"右侧还有内容"（移动端才需要提示） */
const scrollerRef = ref<HTMLElement | null>(null)
const canScrollRight = ref(false)

function updateHint() {
  const el = scrollerRef.value
  if (!el) {
    canScrollRight.value = false
    return
  }
  // 桌面端是 grid（无滚动），scrollWidth === clientWidth，自然为 false
  canScrollRight.value = el.scrollWidth - el.clientWidth - el.scrollLeft > 8
}

function scrollRight() {
  const el = scrollerRef.value
  if (!el) return
  el.scrollBy({ left: Math.round(el.clientWidth * 0.8), behavior: 'smooth' })
}

onMounted(() => {
  updateHint()
  window.addEventListener('resize', updateHint, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateHint)
})

function coverOf(product: StoreProductCard): string {
  return resolveCoverUrl(product)
}

function priceOf(product: StoreProductCard): string {
  const view = resolvePriceView(product)
  return view.price === null ? '--' : formatCompact(view.price)
}

function strikeOf(product: StoreProductCard): string {
  const view = resolvePriceView(product)
  return view.originalPrice === null ? '' : formatCompact(view.originalPrice)
}

function discountPercentOf(product: StoreProductCard): number | null {
  const view = resolvePriceView(product)
  if (!view.showOriginal || view.price === null || !view.originalPrice) return null
  const percent = Math.round((1 - view.price / view.originalPrice) * 100)
  return percent > 0 ? percent : null
}
</script>
