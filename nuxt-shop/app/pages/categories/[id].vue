<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto max-w-7xl px-2 pb-16 sm:px-3 lg:px-4">
      <!-- 面包屑：首页 → 全部分类 → 当前分类 -->
      <nav
        class="flex flex-wrap items-center gap-2 pt-6 text-sm text-[var(--color-muted)] sm:pt-8"
        :aria-label="t('cart.breadcrumbLabel')"
      >
        <NuxtLinkLocale
          to="/"
          class="cursor-pointer transition-colors hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
        >
          {{ t('common.home') }}
        </NuxtLinkLocale>
        <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <NuxtLinkLocale
          to="/categories"
          class="cursor-pointer transition-colors hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
        >
          {{ t('categories.title') }}
        </NuxtLinkLocale>
        <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span class="font-bold text-[var(--color-ink)]">{{ currentCategory?.name || categoryId }}</span>
      </nav>

      <header class="mb-7 mt-4 sm:mb-9">
        <h1 class="text-3xl font-black tracking-tight text-[var(--color-ink)] sm:text-4xl">
          {{ currentCategory?.name || t('categories.title') }}
        </h1>
        <p v-if="!pending && !error" class="mt-2 text-sm text-[var(--color-muted)]">
          {{ t('products.totalProducts', { count: total }) }}
        </p>
      </header>

      <!-- 加载骨架 -->
      <div
        v-if="pending"
        class="product-grid"
        aria-busy="true"
      >
        <span class="sr-only">{{ t('common.loading') }}</span>
        <div v-for="i in 12" :key="i" class="ui-card animate-pulse overflow-hidden">
          <div class="aspect-square bg-[var(--color-line-soft)]"></div>
          <div class="space-y-2 p-3.5">
            <div class="h-4 w-full rounded bg-[var(--color-line-soft)]"></div>
            <div class="h-4 w-2/3 rounded bg-[var(--color-line-soft)]"></div>
            <div class="h-6 w-1/3 rounded bg-[var(--color-line-soft)]"></div>
          </div>
        </div>
      </div>

      <!-- 错误态 -->
      <div v-else-if="error" class="ui-card flex flex-col items-center px-6 py-16 text-center">
        <span class="flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error-50)] text-[var(--color-error-600)]">
          <UIcon name="lucide:wifi-off" class="h-7 w-7" aria-hidden="true" />
        </span>
        <h2 class="mt-5 text-lg font-black text-[var(--color-ink)]">{{ t('products.loadError') }}</h2>
        <p class="mt-2 text-sm text-[var(--color-muted)]">{{ t('products.loadErrorDesc') }}</p>
        <button type="button" class="ui-pill ui-pill-brand mt-6 h-11 px-6 text-sm" @click="refresh()">
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
          {{ t('common.retry') }}
        </button>
      </div>

      <!-- 空态：该分类下确实没有在售商品 -->
      <div v-else-if="!records.length" class="ui-card flex flex-col items-center px-6 py-16 text-center">
        <span class="flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-brand-50)] text-[var(--color-brand-500)]">
          <UIcon name="lucide:package-search" class="h-7 w-7" aria-hidden="true" />
        </span>
        <p class="mt-5 text-sm font-bold text-[var(--color-ink)]">{{ t('products.noProducts') }}</p>
        <NuxtLinkLocale to="/products" class="ui-pill ui-pill-ghost mt-6 h-11 px-6 text-sm">
          <UIcon name="lucide:layout-grid" class="h-4 w-4" aria-hidden="true" />
          {{ t('products.all') }}
        </NuxtLinkLocale>
      </div>

      <!-- 商品网格：统一用新的 StoreProductCard（价格/角标/统计的渲染契约都在组件里） -->
      <template v-else>
        <div class="product-grid">
          <StoreProductCard v-for="product in records" :key="product.id" :product="product" />
        </div>

        <!-- 分页（后端分页：pageNo / pageSize） -->
        <PaginationBar
          :current="currentPage"
          :total="totalPages"
          variant="outline"
          margin="mt-10"
          :label="t('products.pagination')"
          :prev-label="t('products.prev')"
          :next-label="t('products.next')"
          @change="goToPage"
        />
      </template>
    </div>

    <!-- Footer：与首页 / 商品列表 / 个人中心保持一致（登录页是唯一不加页脚的页面） -->
    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
// 某个分类下的商品列表（含子分类的商品——后端按"分类及其后代"匹配）。
//
// 注意：这里查询的是**本店**的商品。店铺身份由 BFF 注入 X-Store-Code，
// 页面 URL 里不含任何店铺参数（商品链接要分享到社交平台，必须短）。
import { STORE_PRODUCT_PAGE_SIZE, useStoreProductList } from '~~/composables/useProducts'
import type { StoreCategoryNode } from '~~/types/store-category'

const { t, locale } = useI18n()
const route = useRoute()

const categoryId = computed(() => String(route.params.id || ''))

// 分类名从分类树里取（一次请求，用于标题与面包屑）。
// 语言：query 带 lang，key 带 locale + 当前分类 id —— 切语言/切分类都要重新请求，
// 否则标题与面包屑会停留在上一个语言/上一个分类的缓存。
const { data: categories } = await useFetch<StoreCategoryNode[]>('/api/categories', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `store-categories-${categoryId.value}-${locale.value}`),
  default: () => [],
})

/** 在整棵树里找到当前分类节点（分类最多 3 级，遍历成本可忽略） */
const currentCategory = computed(() => {
  const stack = [...(categories.value || [])]
  while (stack.length) {
    const node = stack.shift()
    if (node?.id === categoryId.value) return node
    if (node?.children?.length) stack.push(...node.children)
  }
  return null
})

const pageSize = STORE_PRODUCT_PAGE_SIZE
const pageNo = ref(1)

const {
  data: page,
  pending,
  error,
  refresh,
} = await useStoreProductList({
  pageNo,
  pageSize,
  categoryId,
})

const records = computed(() => page.value?.records ?? [])
const total = computed(() => page.value?.total ?? 0)
const currentPage = computed(() => page.value?.current || pageNo.value)
const totalPages = computed(() => Math.max(1, page.value?.pages ?? 0))

// 切换分类时回到第 1 页，否则会停留在上一个分类的页码上
watch(categoryId, () => {
  pageNo.value = 1
})

function goToPage(target: number) {
  const next = Math.min(Math.max(target, 1), totalPages.value)
  if (next === currentPage.value) return
  pageNo.value = next
  if (import.meta.client) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

useSeoMeta({
  title: () => currentCategory.value?.name || t('categories.title'),
  description: () => t('categories.description'),
})
</script>
