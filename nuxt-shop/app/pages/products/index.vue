<template>
  <div class="min-h-screen bg-[var(--color-background)] pt-navbar">
    <!-- Page Header -->
    <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
      <div class="mb-8 flex flex-col gap-5 sm:mb-10 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <span class="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
            <UIcon name="lucide:sparkles" class="h-4 w-4" />
            {{ t('products.title') }}
          </span>
          <h1 class="mt-3 text-3xl font-bold text-[var(--color-primary)] sm:text-4xl text-gradient-primary">
            {{ t('products.title') }}
          </h1>
          <p class="mt-2 text-sm text-[var(--color-text-muted)]">
            {{ t('products.totalProducts', { count: total }) }}
            <template v-if="totalPages > 1">
              · {{ t('products.pageInfo', { current: currentPage, total: totalPages }) }}
            </template>
          </p>
        </div>

        <!-- 关键词搜索（后端 keyword 模糊匹配商品名） -->
        <form class="flex w-full gap-2 lg:max-w-md" @submit.prevent="submitSearch">
          <label for="products-search" class="sr-only">{{ t('products.search') }}</label>
          <div class="relative flex-1">
            <UIcon
              name="lucide:search"
              class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-text-muted)]"
            />
            <input
              id="products-search"
              v-model="keywordInput"
              type="search"
              :placeholder="t('products.searchPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white/70 pl-10 pr-3 text-sm text-[var(--color-text-secondary)] backdrop-blur-sm transition-all duration-200 focus:border-[var(--color-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]/10"
            />
          </div>
          <button
            type="submit"
            class="inline-flex h-11 items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-5 text-sm font-semibold text-white transition-all duration-200 hover:bg-[var(--color-primary-700)] motion-reduce:transition-none"
          >
            <UIcon name="lucide:search" class="h-4 w-4" />
            {{ t('products.search') }}
          </button>
          <button
            v-if="keyword"
            type="button"
            class="inline-flex h-11 items-center gap-2 rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white/60 px-4 text-sm font-medium text-[var(--color-text-secondary)] transition-all duration-200 hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] motion-reduce:transition-none"
            @click="clearSearch"
          >
            <UIcon name="lucide:x" class="h-4 w-4" />
            {{ t('products.clearSearch') }}
          </button>
        </form>
      </div>
    </div>

    <!-- Products -->
    <section class="mx-auto max-w-7xl px-4 pb-20 sm:px-6 lg:px-8">
      <!-- 错误态（例如后端未启动） -->
      <div v-if="error" class="flex flex-col items-center justify-center py-20 text-center">
        <div class="flex h-20 w-20 items-center justify-center rounded-full border border-[var(--color-border)] bg-[var(--color-surface-secondary)]">
          <UIcon name="lucide:wifi-off" class="h-10 w-10 text-[var(--color-error)]" />
        </div>
        <h3 class="mt-6 text-xl font-bold text-[var(--color-primary)]">
          {{ t('products.loadError') }}
        </h3>
        <p class="mt-2 max-w-md text-sm text-[var(--color-text-muted)]">
          {{ t('products.loadErrorDesc') }}
        </p>
        <button
          type="button"
          class="mt-6 inline-flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-white transition-all duration-200 hover:bg-[var(--color-primary-700)] motion-reduce:transition-none"
          @click="refresh()"
        >
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" />
          {{ t('common.retry') }}
        </button>
      </div>

      <!-- 骨架 -->
      <div
        v-else-if="pending"
        class="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3 xl:grid-cols-4"
      >
        <div v-for="i in pageSize" :key="i" class="animate-pulse">
          <div class="aspect-square rounded-[var(--radius-card)] bg-[var(--color-surface-secondary)]"></div>
          <div class="mt-4 space-y-2 px-1">
            <div class="h-3 w-1/3 rounded bg-[var(--color-surface-secondary)]"></div>
            <div class="h-4 w-full rounded bg-[var(--color-surface-secondary)]"></div>
            <div class="h-4 w-2/3 rounded bg-[var(--color-surface-secondary)]"></div>
            <div class="h-6 w-1/3 rounded bg-[var(--color-surface-secondary)]"></div>
          </div>
        </div>
      </div>

      <!-- 商品网格 -->
      <div
        v-else-if="records.length"
        class="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3 xl:grid-cols-4"
      >
        <StoreProductCard
          v-for="(product, idx) in records"
          :key="product.id"
          :product="product"
          :style="{ animationDelay: `${idx * 60}ms` }"
          class="animate-fade-in-up opacity-0"
        />
      </div>

      <!-- 空态 -->
      <div v-else class="flex flex-col items-center justify-center py-20">
        <div class="relative">
          <div class="absolute inset-0 animate-pulse rounded-full bg-[var(--color-accent)]/10 blur-2xl"></div>
          <div class="relative flex h-24 w-24 items-center justify-center rounded-full border border-[var(--color-border)] bg-[var(--color-surface-secondary)] glass">
            <UIcon name="lucide:package-search" class="h-12 w-12 text-[var(--color-text-muted)]" />
          </div>
        </div>
        <h3 class="mt-8 text-xl font-bold text-[var(--color-primary)]">
          {{ t('products.noProducts') }}
        </h3>
        <p class="mt-2 max-w-md text-center text-sm text-[var(--color-text-muted)]">
          {{ t('products.noProductsDesc') }}
        </p>
        <button
          v-if="keyword"
          type="button"
          class="mt-6 inline-flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-white transition-all duration-200 hover:bg-[var(--color-primary-700)] motion-reduce:transition-none"
          @click="clearSearch"
        >
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" />
          {{ t('products.resetFilters') }}
        </button>
      </div>

      <!-- 分页（后端分页：pageNo / pageSize） -->
      <nav
        v-if="!pending && !error && records.length && totalPages > 1"
        class="mt-12 flex flex-wrap items-center justify-center gap-2"
        :aria-label="t('products.pagination')"
      >
        <button
          type="button"
          class="inline-flex h-11 min-w-[44px] items-center justify-center gap-1 rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white/60 px-3 text-sm font-medium text-[var(--color-text-secondary)] transition-all duration-200 hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
          :disabled="currentPage <= 1"
          @click="goToPage(currentPage - 1)"
        >
          <UIcon name="lucide:chevron-left" class="h-4 w-4" />
          <span class="hidden sm:inline">{{ t('products.prev') }}</span>
        </button>

        <button
          v-for="pageNumber in pageWindow"
          :key="pageNumber"
          type="button"
          class="inline-flex h-11 min-w-[44px] items-center justify-center rounded-[var(--radius-button)] border text-sm font-semibold transition-all duration-200 motion-reduce:transition-none"
          :class="pageNumber === currentPage
            ? 'border-[var(--color-primary)] bg-[var(--color-primary)] text-white'
            : 'border-[var(--color-border)] bg-white/60 text-[var(--color-text-secondary)] hover:border-[var(--color-primary)] hover:text-[var(--color-primary)]'"
          :aria-current="pageNumber === currentPage ? 'page' : undefined"
          @click="goToPage(pageNumber)"
        >
          {{ pageNumber }}
        </button>

        <button
          type="button"
          class="inline-flex h-11 min-w-[44px] items-center justify-center gap-1 rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white/60 px-3 text-sm font-medium text-[var(--color-text-secondary)] transition-all duration-200 hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
          :disabled="currentPage >= totalPages"
          @click="goToPage(currentPage + 1)"
        >
          <span class="hidden sm:inline">{{ t('products.next') }}</span>
          <UIcon name="lucide:chevron-right" class="h-4 w-4" />
        </button>
      </nav>
    </section>

    <!-- Footer -->
    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import { STORE_PRODUCT_PAGE_SIZE, useStoreProductList } from '~~/composables/useProducts'

const { t } = useI18n()

const pageSize = STORE_PRODUCT_PAGE_SIZE
const pageNo = ref(1)
const keywordInput = ref('')
/** 已提交的搜索词；只有它参与请求 key，输入过程不会打后端 */
const keyword = ref('')

const { data: page, pending, error, refresh } = await useStoreProductList({
  pageNo,
  pageSize,
  keyword,
})

const records = computed(() => page.value?.records ?? [])
const total = computed(() => page.value?.total ?? 0)
const currentPage = computed(() => page.value?.current || pageNo.value)
const totalPages = computed(() => Math.max(1, page.value?.pages ?? 0))

/** 页码窗口：最多 5 个，围绕当前页展开 */
const pageWindow = computed(() => {
  const pages = totalPages.value
  const current = currentPage.value
  const size = Math.min(5, pages)
  let start = Math.max(1, current - Math.floor(size / 2))
  const end = Math.min(pages, start + size - 1)
  start = Math.max(1, end - size + 1)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

function submitSearch() {
  keyword.value = keywordInput.value.trim()
  pageNo.value = 1
}

function clearSearch() {
  keywordInput.value = ''
  keyword.value = ''
  pageNo.value = 1
}

function goToPage(target: number) {
  const next = Math.min(Math.max(target, 1), totalPages.value)
  if (next === currentPage.value) return
  pageNo.value = next
  if (import.meta.client) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

useSeoMeta({
  title: () => t('products.title'),
  description: () => t('products.noProductsDesc'),
})
</script>
