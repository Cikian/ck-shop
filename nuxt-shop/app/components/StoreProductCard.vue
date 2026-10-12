<template>
  <NuxtLinkLocale
    :to="`/products/${product.id}`"
    class="group relative flex flex-col overflow-hidden rounded-[var(--radius-card)] bg-[var(--color-surface)] transition-all duration-500 hover:-translate-y-2 hover:shadow-2xl hover:shadow-[var(--color-primary)]/10 motion-reduce:transition-none motion-reduce:hover:translate-y-0"
  >
    <!-- 封面 -->
    <div class="relative overflow-hidden rounded-t-[var(--radius-card)] bg-[var(--color-surface-secondary)]">
      <div class="aspect-square">
        <img
          v-if="coverUrl"
          :src="coverUrl"
          :alt="product.title"
          class="h-full w-full object-cover transition-transform duration-700 ease-out group-hover:scale-110 motion-reduce:transition-none motion-reduce:group-hover:scale-100"
          loading="lazy"
        />
        <div v-else class="flex h-full w-full items-center justify-center text-[var(--color-text-muted)]">
          <UIcon name="lucide:image-off" class="h-10 w-10" />
        </div>
      </div>

      <!-- 推荐 / 标签 -->
      <div class="absolute left-3 top-3 flex flex-wrap gap-2">
        <span
          v-if="product.isRecommend === 'Y'"
          class="inline-flex items-center gap-1 rounded-full bg-[var(--color-primary)] px-3 py-1 text-xs font-bold text-white shadow-lg"
        >
          <UIcon name="lucide:thumbs-up" class="h-3 w-3" />
          {{ t('products.recommended') }}
        </span>
        <span
          v-for="tag in visibleTags"
          :key="tag.id"
          class="inline-flex items-center gap-1 rounded-full bg-[var(--color-accent)] px-3 py-1 text-xs font-bold text-white shadow-lg shadow-[var(--color-accent)]/30"
        >
          {{ tag.name }}
        </span>
      </div>
    </div>

    <!-- 信息 -->
    <div class="flex flex-1 flex-col p-4 sm:p-5">
      <span
        v-if="categoryLine"
        class="text-xs font-medium uppercase tracking-wider text-[var(--color-text-muted)] transition-colors duration-300 group-hover:text-[var(--color-accent)]"
      >
        {{ categoryLine }}
      </span>

      <h3 class="mt-1 line-clamp-2 text-base font-semibold text-[var(--color-primary)] transition-colors duration-300 group-hover:text-[var(--color-accent)] sm:text-lg">
        {{ product.title }}
      </h3>

      <p v-if="product.subTitle" class="mt-1 line-clamp-2 text-sm text-[var(--color-text-muted)]">
        {{ product.subTitle }}
      </p>

      <div class="mt-auto flex items-end justify-between gap-2 border-t border-[var(--color-border-light)] pt-3">
        <div class="flex flex-col">
          <span class="text-xl font-bold text-[var(--color-primary)]">{{ priceText }}</span>
          <span v-if="product.unit" class="text-xs text-[var(--color-text-muted)]">/ {{ product.unit }}</span>
        </div>
        <span
          class="inline-flex items-center gap-1 text-xs font-medium"
          :class="inStock ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'"
        >
          <UIcon :name="inStock ? 'lucide:package-check' : 'lucide:package-x'" class="h-3.5 w-3.5" />
          {{ inStock ? t('products.stock.inStock') : t('products.stock.outOfStock') }}
        </span>
      </div>
    </div>
  </NuxtLinkLocale>
</template>

<script setup lang="ts">
import type { StoreProductCard } from '~~/types/shop-product'
import { resolveCoverUrl } from '~~/types/shop-product'
import { useStorePrice } from '~~/composables/useProducts'

/**
 * 店铺端商品卡片：只展示后端真实字段（title / subTitle / coverUrl / mainImage /
 * minPrice / maxPrice / price / stock / tags / categoryName / isRecommend）。
 * 后端没有 rating / reviewCount / 折扣 等概念，这里不做任何伪造。
 */
const props = defineProps<{
  product: StoreProductCard
}>()

const { t } = useI18n()
const { formatRange } = useStorePrice()

const coverUrl = computed(() => resolveCoverUrl(props.product))
const inStock = computed(() => props.product.stock > 0)
const priceText = computed(() => formatRange(props.product.minPrice, props.product.maxPrice, props.product.price))
const categoryLine = computed(() =>
  [props.product.categoryName].filter(Boolean).join(' · '),
)
const visibleTags = computed(() => {
  const max = props.product.isRecommend === 'Y' ? 2 : 3
  return (props.product.tags || []).slice(0, max)
})
</script>
