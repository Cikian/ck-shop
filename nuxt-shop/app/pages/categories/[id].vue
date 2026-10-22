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
        <span class="font-bold text-[var(--color-ink)]">{{ currentCategory?.name || t('categories.title') }}</span>
      </nav>

      <header class="mb-7 mt-4 sm:mb-9">
        <h1 class="text-3xl font-black tracking-tight text-[var(--color-ink)] sm:text-4xl">
          {{ currentCategory?.name || t('categories.title') }}
        </h1>
        <p v-if="!pending && !error && !categoryMissing" class="mt-2 text-sm text-[var(--color-muted)]">
          {{ t('products.totalProducts', { count: total }) }}
        </p>
      </header>

      <Transition name="fade" mode="out-in">
      <!-- 加载骨架 -->
      <div
        v-if="pending"
        key="skeleton"
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
            <!-- 加购按钮占位：与真实卡片高度对齐，减少骨架→内容时的 CLS -->
            <div class="h-9 w-full rounded-full bg-[var(--color-line-soft)]"></div>
          </div>
        </div>
      </div>

      <!-- 错误态 -->
      <div v-else-if="error" key="error" class="ui-card flex flex-col items-center px-6 py-16 text-center">
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

      <!--
        分类不存在（链接写错 / 分类被删）：不能落到"共 0 件商品"的空态——
        那会让用户以为分类只是没货，也会被搜索引擎收录成有效页。
        SSR 同时返回 404 + noindex（见 script 里的 setResponseStatus）。
      -->
      <div v-else-if="categoryMissing" key="category-missing" class="ui-card flex flex-col items-center px-6 py-16 text-center">
        <span class="flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error-50)] text-[var(--color-error-600)]">
          <UIcon name="lucide:folder-x" class="h-7 w-7" aria-hidden="true" />
        </span>
        <h2 class="mt-5 text-lg font-black text-[var(--color-ink)]">{{ t('categories.notFoundTitle') }}</h2>
        <p class="mt-2 max-w-md text-sm text-[var(--color-muted)]">{{ t('categories.notFoundDesc') }}</p>
        <NuxtLinkLocale to="/categories" class="ui-pill ui-pill-brand mt-6 h-11 px-6 text-sm">
          <UIcon name="lucide:layout-grid" class="h-4 w-4" aria-hidden="true" />
          {{ t('categories.backToAll') }}
        </NuxtLinkLocale>
      </div>

      <!-- 空态：该分类下确实没有在售商品 -->
      <div v-else-if="!records.length" key="empty" class="ui-card flex flex-col items-center px-6 py-16 text-center">
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
      <div v-else key="content" class="product-grid">
        <StoreProductCard v-for="(product, idx) in records" :key="product.id" :product="product" :eager="idx < 6" />
      </div>
      </Transition>

      <!-- 分页（后端分页：pageNo / pageSize） -->
      <PaginationBar
        v-if="!pending && !error && records.length"
        :current="currentPage"
        :total="totalPages"
        :disabled="pending"
        variant="outline"
        margin="mt-10"
        :label="t('products.pagination')"
        :prev-label="t('products.prev')"
        :next-label="t('products.next')"
        @change="goToPage"
      />
    </div>
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
const { data: categories, pending: categoriesPending } = useFetch<StoreCategoryNode[]>('/api/categories', {
  query: computed(() => ({ lang: locale.value })),
  // 与 Header / 首页共用同一个 key：分类树与 categoryId 无关，
  // 以前 key 里带 categoryId 会让每个分类页都重新请求一遍分类树
  key: computed(() => `store-categories-${locale.value}`),
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

/** 分类树已加载完但找不到该分类 → 页面语义是 404，不是"该分类没货" */
const categoryMissing = computed(() => !categoriesPending.value && !currentCategory.value)

if (import.meta.server) {
  const requestEvent = useRequestEvent()
  // flush: 'sync'：分类树一落地就写响应状态，赶在响应发出之前
  watch(categoryMissing, (missing) => {
    if (missing && requestEvent) {
      setResponseStatus(requestEvent, 404, 'Not Found')
    }
  }, { immediate: true, flush: 'sync' })
}

const pageSize = STORE_PRODUCT_PAGE_SIZE
/**
 * 页码以 URL 为唯一数据源：第 2 页刷新/分享/浏览器后退都能回到第 2 页。
 * 旧实现是本地 ref，刷新即回第 1 页。
 */
const pageNo = computed(() => {
  const parsed = Number(route.query.pageNo)
  return Number.isFinite(parsed) && parsed >= 1 ? Math.trunc(parsed) : 1
})

const {
  data: page,
  pending,
  error,
  refresh,
} = useStoreProductList({
  pageNo,
  pageSize,
  categoryId,
})

const records = computed(() => page.value?.records ?? [])
const total = computed(() => page.value?.total ?? 0)
const currentPage = computed(() => page.value?.current || pageNo.value)
const totalPages = computed(() => Math.max(1, page.value?.pages ?? 0))

const router = useRouter()
const localePath = useLocalePath()

function goToPage(target: number) {
  const next = Math.min(Math.max(target, 1), totalPages.value)
  if (next === currentPage.value) return
  // 写回 query（第 1 页不保留 pageNo，保持 URL 干净）
  void router.push(localePath({
    path: `/categories/${categoryId.value}`,
    query: next > 1 ? { pageNo: String(next) } : {},
  }))
  if (import.meta.client) {
    window.scrollTo({
      top: 0,
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
    })
  }
}

useSeoMeta({
  title: () => categoryMissing.value
    ? t('categories.notFoundTitle')
    : (currentCategory.value?.name || t('categories.title')),
  // description 带上当前分类名，避免所有分类页共用同一段通用介绍
  description: () => categoryMissing.value
    ? t('categories.notFoundDesc')
    : (currentCategory.value?.name
      ? `${currentCategory.value.name} - ${t('categories.description')}`
      : t('categories.description')),
  // 分类不存在时不要被搜索引擎收录
  robots: () => categoryMissing.value ? 'noindex, nofollow' : 'index, follow',
})
</script>
