<template>
  <div class="min-h-screen bg-[var(--color-background)] pt-navbar">
    <!-- Page Header -->
    <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
      <div class="mb-8 flex flex-col gap-4 sm:mb-10 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <span class="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
            <UIcon name="lucide:sparkles" class="h-4 w-4" />
            {{ t('products.title') }}
          </span>
          <h1 class="mt-3 text-3xl font-bold text-[var(--color-primary)] sm:text-4xl text-gradient-primary">
            {{ t('products.title') }}
          </h1>
          <p class="mt-2 text-sm text-[var(--color-text-muted)]">
            {{ t('products.showingResults', { count: sortedProducts.length }) }}
          </p>
        </div>
      </div>

      <!-- Filters & Sort - Glass Card -->
      <div class="glass mb-8 rounded-[var(--radius-card)] p-4 sm:p-5">
        <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <!-- Filter Buttons -->
          <div class="flex flex-wrap items-center gap-2">
            <button
              v-for="filter in filterOptions"
              :key="filter.value"
              @click="activeFilter = filter.value"
              class="inline-flex items-center gap-2 rounded-[var(--radius-button)] px-4 py-2.5 text-sm font-medium transition-all duration-200 cursor-pointer"
              :class="activeFilter === filter.value
                ? 'bg-[var(--color-primary)] text-white shadow-lg shadow-[var(--color-primary)]/20'
                : 'bg-white/60 text-[var(--color-text-secondary)] hover:bg-white hover:text-[var(--color-primary)] border border-[var(--color-border)]'"
            >
              <UIcon :name="filter.icon" class="h-4 w-4" />
              {{ t(filter.label) }}
            </button>
          </div>

          <!-- Sort Select -->
          <div class="flex items-center gap-3">
            <div class="relative">
              <select
                v-model="sortBy"
                class="appearance-none rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white/60 px-4 py-2.5 pr-10 text-sm font-medium text-[var(--color-text-secondary)] transition-all duration-200 hover:border-[var(--color-primary)] cursor-pointer focus:border-[var(--color-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]/10 backdrop-blur-sm"
              >
                <option value="popular">{{ t('products.sortByPopular') }}</option>
                <option value="newest">{{ t('products.sortByNewest') }}</option>
                <option value="price-asc">{{ t('products.sortByPriceAsc') }}</option>
                <option value="price-desc">{{ t('products.sortByPriceDesc') }}</option>
              </select>
              <span class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)]">
                <UIcon name="lucide:chevron-down" class="h-4 w-4" />
              </span>
            </div>

            <button
              class="inline-flex h-10 w-10 items-center justify-center rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white/60 text-[var(--color-text-secondary)] transition-all duration-200 hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] cursor-pointer backdrop-blur-sm"
              aria-label="Grid view"
            >
              <UIcon name="lucide:layout-grid" class="h-5 w-5" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Products Grid -->
    <section class="mx-auto max-w-7xl px-4 pb-20 sm:px-6 lg:px-8">
      <!-- Loading State -->
      <div
        v-if="pending"
        class="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3 xl:grid-cols-4"
      >
        <div
          v-for="i in 8"
          :key="i"
          class="animate-pulse"
        >
          <div class="aspect-square rounded-[var(--radius-card)] bg-[var(--color-surface-secondary)]"></div>
          <div class="mt-4 space-y-2 p-4">
            <div class="h-3 w-1/3 rounded bg-[var(--color-surface-secondary)]"></div>
            <div class="h-4 w-full rounded bg-[var(--color-surface-secondary)]"></div>
            <div class="h-4 w-2/3 rounded bg-[var(--color-surface-secondary)]"></div>
            <div class="h-6 w-1/3 rounded bg-[var(--color-surface-secondary)]"></div>
          </div>
        </div>
      </div>

      <!-- Products Grid -->
      <div
        v-else-if="sortedProducts.length"
        class="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3 xl:grid-cols-4"
      >
        <ProductCard
          v-for="(product, idx) in sortedProducts"
          :key="product.id"
          :product="product"
          :style="{ animationDelay: `${idx * 60}ms` }"
          class="animate-fade-in-up opacity-0"
        />
      </div>

      <!-- Empty State -->
      <div
        v-else
        class="flex flex-col items-center justify-center py-20"
      >
        <div class="relative">
          <div class="absolute inset-0 rounded-full bg-[var(--color-accent)]/10 blur-2xl animate-pulse"></div>
          <div class="relative flex h-24 w-24 items-center justify-center rounded-full bg-[var(--color-surface-secondary)] glass border border-[var(--color-border)]">
            <UIcon name="lucide:package-search" class="h-12 w-12 text-[var(--color-text-muted)]" />
          </div>
        </div>
        <h3 class="mt-8 text-xl font-bold text-[var(--color-primary)]">
          {{ t('products.noProducts') }}
        </h3>
        <p class="mt-2 max-w-md text-center text-sm text-[var(--color-text-muted)]">
          {{ t('products.noProductsDesc') || 'Try adjusting your filters or search terms to find what you\'re looking for.' }}
        </p>
        <button
          class="mt-6 inline-flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-white transition-all duration-200 hover:bg-[var(--color-primary-700)] hover:shadow-lg hover:shadow-[var(--color-primary)]/20 cursor-pointer btn-shine"
          @click="activeFilter = 'all'; sortBy = 'popular'"
        >
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" />
          {{ t('products.resetFilters') || 'Reset Filters' }}
        </button>
      </div>
    </section>

    <!-- Footer -->
    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
interface Product {
  id: number
  slug: string
  name: string
  description: string
  price: number
  originalPrice?: number
  image: string
  rating: number
  reviewCount: number
  isNew?: boolean
  isBestseller?: boolean
  discount?: number
  category: string
  stock: number
}

const { t, locale } = useI18n()
const sortBy = ref('popular')
const activeFilter = ref('all')

const filterOptions = [
  { value: 'all', label: 'products.all', icon: 'lucide:layers' },
  { value: 'new', label: 'products.newArrivals', icon: 'lucide:sparkles' },
  { value: 'bestseller', label: 'products.bestsellers', icon: 'lucide:zap' },
  { value: 'sale', label: 'products.onSale', icon: 'lucide:tag' },
]

const { data: products, pending } = await useFetch<Product[]>('/api/products', {
  query: { lang: locale.value },
})

const sortedProducts = computed(() => {
  let list = products.value || []

  if (activeFilter.value === 'new') {
    list = list.filter(p => p.isNew)
  } else if (activeFilter.value === 'bestseller') {
    list = list.filter(p => p.isBestseller)
  } else if (activeFilter.value === 'sale') {
    list = list.filter(p => p.discount && p.discount > 0)
  }

  const sorted = [...list]

  switch (sortBy.value) {
    case 'price-asc':
      sorted.sort((a, b) => a.price - b.price)
      break
    case 'price-desc':
      sorted.sort((a, b) => b.price - a.price)
      break
    case 'newest':
      sorted.sort((a, b) => (b.isNew ? 1 : 0) - (a.isNew ? 1 : 0))
      break
    case 'popular':
    default:
      sorted.sort((a, b) => b.reviewCount - a.reviewCount)
      break
  }

  return sorted
})

useSeoMeta({
  title: () => t('products.title'),
})
</script>
