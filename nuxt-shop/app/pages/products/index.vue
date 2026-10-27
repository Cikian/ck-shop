<template>
  <div class="min-h-[calc(100vh-var(--nav-total-height))] bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto max-w-7xl px-2 pb-16 sm:px-3 lg:px-4">
      <!-- ===================== 搜索摘要栏 =====================
           首页承担「逛」的职责（有分类卡片），所以本页只做「搜索结果页」：
           顶部回答两个问题——在搜什么、一共有几件；右侧给排序。 -->
      <header class="flex flex-col gap-4 pt-6 sm:pt-8 lg:flex-row lg:items-end lg:justify-between">
        <div class="min-w-0">
          <h1 class="text-2xl font-black leading-tight text-[var(--color-ink)] sm:text-3xl">
            {{ pageTitle }}
          </h1>
          <p v-if="showMeta" class="mt-1.5 text-[13px] text-[var(--color-muted)]">
            {{ t('shop.searchResultMeta', { count: total }) }}
            <template v-if="totalPages > 1">
              · {{ t('products.pageInfo', { current: currentPage, total: totalPages }) }}
            </template>
          </p>
        </div>

        <!-- 排序下拉：选项与后端白名单一一对应 -->
        <UDropdownMenu :items="sortItems" :popper="{ placement: 'bottom-end' }" :modal="false">
          <button
            type="button"
            class="ui-pill ui-pill-ghost h-10 shrink-0 gap-2 self-start px-4 text-[13px] lg:self-auto"
            :aria-label="t('shop.sort')"
          >
            <UIcon name="lucide:arrow-up-down" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
            {{ t('shop.sortLabel', { label: currentSortLabel }) }}
            <UIcon name="lucide:chevron-down" class="h-3.5 w-3.5 text-[var(--color-muted)]" aria-hidden="true" />
          </button>
        </UDropdownMenu>
      </header>

      <!-- ===================== 分类筛选 chips =====================
           分类来自 /api/categories（后端按店铺+语言过滤好的真实分类），
           与 Header 共用同一个 useFetch key，因此这里不会重复请求。
           分类接口失败不再静默：给一行可重试提示，否则筛选区会凭空消失。 -->
      <div
        v-if="categoriesError"
        class="mt-4 flex flex-wrap items-center gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-3.5 py-2.5 text-[13px] font-medium text-[var(--color-error-600)]"
        role="status"
      >
        <UIcon name="lucide:wifi-off" class="h-4 w-4 shrink-0" aria-hidden="true" />
        <span>{{ t('shop.allCategories') }}</span>
        <button
          type="button"
          class="ml-auto inline-flex items-center gap-1 font-bold underline underline-offset-2"
          @click="refreshCategories()"
        >
          <UIcon name="lucide:rotate-ccw" class="h-3.5 w-3.5" aria-hidden="true" />
          {{ t('common.retry') }}
        </button>
      </div>

      <div v-if="rootCategories.length" class="mt-6 flex items-center gap-3">
        <div
          class="no-scrollbar flex min-w-0 flex-1 items-center gap-2 overflow-x-auto pb-0.5"
          role="group"
          :aria-label="t('shop.allCategories')"
        >
          <button
            type="button"
            class="chip shrink-0"
            :class="!categoryId ? 'chip-active' : ''"
            :aria-pressed="!categoryId"
            @click="selectCategory('')"
          >
            {{ t('products.all') }}
          </button>
          <button
            v-for="cat in rootCategories"
            :key="cat.id"
            type="button"
            class="chip shrink-0"
            :class="categoryId === cat.id ? 'chip-active' : ''"
            :aria-pressed="categoryId === cat.id"
            @click="selectCategory(cat.id)"
          >
            {{ cat.name }}
          </button>
        </div>

        <button
          v-if="hasActiveFilters"
          type="button"
          class="chip shrink-0 text-[var(--color-muted)]"
          @click="clearFilters"
        >
          <UIcon name="lucide:filter-x" class="h-3.5 w-3.5" aria-hidden="true" />
          {{ t('shop.clearFilters') }}
        </button>
      </div>

      <!--
        「相关搜索」一行已移除：它只是把同一批一级分类名再渲染一遍（点击等于用分类名做关键词搜索），
        与上面的 chips 完全重复 —— 1440px 下用户会连续看到三行几乎相同的分类入口。
        真正的搜索词建议需要后端提供搜索日志/热词接口，等有了再加。
      -->

      <!-- ===================== 商品区 ===================== -->
      <section class="mt-6" :aria-busy="pending">
        <Transition name="fade" mode="out-in">
          <!-- 错误态（例如后端未启动） -->
          <div v-if="error" key="error" class="ui-card flex flex-col items-center justify-center px-6 py-16 text-center">
            <span class="flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error-50)]">
              <UIcon name="lucide:wifi-off" class="h-8 w-8 text-[var(--color-error)]" aria-hidden="true" />
            </span>
            <h2 class="mt-5 text-lg font-bold text-[var(--color-ink)]">{{ t('products.loadError') }}</h2>
            <p class="mt-1.5 max-w-md text-sm text-[var(--color-muted)]">{{ t('products.loadErrorDesc') }}</p>
            <button type="button" class="ui-pill ui-pill-brand mt-6 h-11 px-6 text-sm" @click="refresh()">
              <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
              {{ t('common.retry') }}
            </button>
          </div>

          <!-- 加载骨架：补齐副标题行与加购按钮占位，与真实卡片高度对齐（减少 CLS） -->
          <div v-else-if="pending" key="skeleton" class="product-grid">
            <div v-for="i in skeletonCount" :key="`skeleton-${i}`" class="ui-card overflow-hidden">
              <div class="aspect-square animate-pulse bg-[var(--color-surface-secondary)]"></div>
              <div class="space-y-2.5 p-3.5">
                <div class="h-4 w-full animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
                <div class="h-4 w-2/3 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
                <div class="h-3 w-1/3 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
                <div class="h-7 w-1/2 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
                <div class="h-9 w-full animate-pulse rounded-full bg-[var(--color-surface-secondary)]"></div>
              </div>
            </div>
          </div>

          <!-- 商品网格 -->
          <div
            v-else-if="records.length"
            key="content"
            class="product-grid"
          >
            <StoreProductCard
              v-for="(product, idx) in records"
              :key="product.id"
              :product="product"
              :eager="idx < 6"
              :style="{ animationDelay: `${idx * 60}ms` }"
              class="animate-fade-in-up opacity-0 motion-reduce:animate-none motion-reduce:opacity-100"
            />
          </div>

          <!-- 空态 -->
          <div v-else key="empty" class="ui-card flex flex-col items-center justify-center px-6 py-16 text-center">
            <span class="flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
              <UIcon name="lucide:package-search" class="h-8 w-8 text-[var(--color-brand-500)]" aria-hidden="true" />
            </span>
            <h2 class="mt-5 text-lg font-bold text-[var(--color-ink)]">{{ t('shop.searchEmptyTitle') }}</h2>
            <p class="mt-1.5 max-w-md text-sm text-[var(--color-muted)]">{{ t('shop.searchEmptyDesc') }}</p>
            <button type="button" class="ui-pill ui-pill-ghost mt-6 h-10 px-5 text-[13px]" @click="clearFilters">
              <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
              {{ t('shop.clearFilters') }}
            </button>
          </div>
        </Transition>

        <!-- 分页：沿用原有的交互与 i18n key，只是换了视觉 -->
        <PaginationBar
          v-if="!pending && !error && records.length"
          :current="currentPage"
          :total="totalPages"
          :disabled="pending"
          variant="chip"
          margin="mt-10"
          :label="t('products.pagination')"
          :prev-label="t('products.prev')"
          :next-label="t('products.next')"
          @change="goToPage"
        />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { StoreCategoryNode } from '~~/types/store-category'
import { STORE_PRODUCT_PAGE_SIZE, useStoreProductList, type StoreProductSort } from '~~/composables/useProducts'

/**
 * 搜索结果页。
 *
 * 和旧版「商品列表页」的差别：
 *   1) 首页已经承担浏览职责，本页只做搜索结果 —— 标题是「"xx"的搜索结果」；
 *      没有 keyword 时退化为“全部商品”，所以 /zh/products 仍可直接访问。
 *   2) 全部筛选状态（keyword / categoryId / sortBy / pageNo）都写在 URL query 里：
 *      链接可分享、浏览器前进后退可用、刷新不丢状态。
 *      页面只把 route.query 当唯一数据源，本地不放第二份 state（避免两处不同步）。
 *   3) 分类 chips 只用后端真实分类名，零硬编码行业词。
 *
 * 取数约定：两个请求都不 await。客户端导航立即出骨架；SSR 由 useAsyncData
 * 内部的 onServerPrefetch 等待，HTML 仍是完整内容。
 */

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const localePath = useLocalePath()

const pageSize = STORE_PRODUCT_PAGE_SIZE

/** query 取值归一化：数组取第一个、去空白、非字符串一律当空 */
function readQuery(value: unknown): string {
  const raw = Array.isArray(value) ? value[0] : value
  return typeof raw === 'string' ? raw.trim() : ''
}

/** 排序取值必须与 composables/useProducts.ts 的 StoreProductSort 白名单一致 */
const SORT_OPTIONS: StoreProductSort[] = ['default', 'sales', 'newest', 'price_asc', 'price_desc']
const SORT_LABEL_KEYS: Record<StoreProductSort, string> = {
  default: 'shop.sortDefault',
  sales: 'shop.sortSales',
  newest: 'shop.sortNewest',
  price_asc: 'shop.sortPriceAsc',
  price_desc: 'shop.sortPriceDesc',
}

// ===== 唯一数据源：URL query =====
const keyword = computed(() => readQuery(route.query.keyword))
const categoryId = computed(() => readQuery(route.query.categoryId))
const sortBy = computed<StoreProductSort>(() => {
  const raw = readQuery(route.query.sortBy)
  return (SORT_OPTIONS as string[]).includes(raw) ? (raw as StoreProductSort) : 'default'
})
const pageNo = computed(() => {
  const parsed = Number(readQuery(route.query.pageNo))
  return Number.isFinite(parsed) && parsed >= 1 ? Math.trunc(parsed) : 1
})

const { data: page, pending, error, refresh } = useStoreProductList({
  pageNo,
  pageSize,
  keyword,
  categoryId,
  sortBy,
})

const records = computed(() => page.value?.records ?? [])
const total = computed(() => page.value?.total ?? 0)
const currentPage = computed(() => page.value?.current || pageNo.value)
const totalPages = computed(() => Math.max(1, page.value?.pages ?? 0))
/** 骨架块数：与商品网格一致的一整行倍数（宽屏 6 列 → 12 块正好两行，不会出现半排空块） */
const skeletonCount = computed(() => Math.min(pageSize, 12))

/** 结果条数：加载中/出错/0 件时都不渲染，避免闪一下「共 0 件商品」 */
const showMeta = computed(() => !pending.value && !error.value && total.value > 0)

const pageTitle = computed(() =>
  keyword.value ? t('shop.searchResultFor', { keyword: keyword.value }) : t('products.all'),
)
const currentSortLabel = computed(() => t(SORT_LABEL_KEYS[sortBy.value]))

const sortItems = computed<DropdownMenuItem[]>(() =>
  SORT_OPTIONS.map(option => ({
    label: t(SORT_LABEL_KEYS[option]),
    // 用 icon 表示“当前选中”，比 DropdownMenu 的 checkbox 更可靠（无需额外 type）
    icon: sortBy.value === option ? 'lucide:check' : undefined,
    onSelect: () => pushQuery({ sortBy: option }),
  })),
)

// ===== 真实分类（与 Header / 分类页共用 key，因此不会重复请求） =====
// key 里已经带 locale：切语言时 key 变化会触发 useFetch 重新请求，
// 再写 watch: [locale] 只会让同一次切换多打一遍接口（重复请求）。
const {
  data: categoryTree,
  error: categoriesError,
  refresh: refreshCategories,
} = useFetch<StoreCategoryNode[]>('/api/categories', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `store-categories-${locale.value}`),
  default: () => [],
})

/** /api/categories 返回的就是分类树的根节点数组 */
const rootCategories = computed(() => categoryTree.value || [])

/** 是否有任何筛选/排序/翻页条件：没有条件时不显示「清空筛选」 */
const hasActiveFilters = computed(() =>
  !!keyword.value || !!categoryId.value || sortBy.value !== 'default' || pageNo.value > 1,
)

/**
 * 改写 URL query（唯一的写入口）。
 * 空值直接不进 query，保证 /zh/products 永远是“干净”的默认态；
 * 任何筛选变化都回到第 1 页（否则会停在旧页码上看到空列表）。
 */
function pushQuery(patch: {
  keyword?: string
  categoryId?: string
  sortBy?: StoreProductSort
  pageNo?: number
}) {
  const nextKeyword = patch.keyword ?? keyword.value
  const nextCategory = patch.categoryId ?? categoryId.value
  const nextSort = patch.sortBy ?? sortBy.value
  const nextPage = patch.pageNo ?? 1

  const query: Record<string, string> = {}
  if (nextKeyword) query.keyword = nextKeyword
  if (nextCategory) query.categoryId = nextCategory
  if (nextSort && nextSort !== 'default') query.sortBy = nextSort
  if (nextPage > 1) query.pageNo = String(nextPage)

  return router.push(localePath({ path: '/products', query }))
}

/** 分类 chips：再点一次已选分类 = 取消该分类 */
function selectCategory(id: string) {
  pushQuery({ categoryId: categoryId.value === id ? '' : id })
}

/** 清空筛选：回到不带任何 query 的 /products（默认排序 + 全部商品） */
function clearFilters() {
  return router.push(localePath('/products'))
}

/** 翻页：同样落到 URL 上，前进后退可用 */
function goToPage(target: number) {
  const next = Math.min(Math.max(target, 1), totalPages.value)
  if (next === currentPage.value) return
  pushQuery({ pageNo: next })
  if (import.meta.client) {
    window.scrollTo({
      top: 0,
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
    })
  }
}

/**
 * SEO：筛选/排序/分页都是**独立可索引页面**。
 *
 * - canonical 参数白名单在 nuxt.config.ts 的 seo.canonicalQueryWhitelist 里维护，
 *   否则这些变体都会被 canonical 到 /zh/products；
 * - 关键词搜索结果页没有独特价值，按惯例 noindex（但 follow，保留站内链接权重）；
 * - 分页页输出 prev/next，帮助爬虫理解序列。
 */
useSeoMeta({
  title: () => pageTitle.value,
  // 搜索页的 description 必须是"这次搜索是什么"，而不是分类总览的通用介绍
  description: () => keyword.value
    ? t('shop.searchResultFor', { keyword: keyword.value })
    : t('categories.description'),
  robots: () => keyword.value ? 'noindex, follow' : 'index, follow',
})

const siteConfig = useSiteConfig()

/** 生成某一页的绝对 URL（保留当前筛选条件；分页用 pageNo） */
function absolutePageUrl(targetPage: number): string {
  const query: Record<string, string> = {}
  if (keyword.value) query.keyword = keyword.value
  if (categoryId.value) query.categoryId = categoryId.value
  if (sortBy.value !== 'default') query.sortBy = sortBy.value
  if (targetPage > 1) query.pageNo = String(targetPage)
  const base = String(siteConfig.url || '').replace(/\/+$/, '')
  return `${base}${localePath({ path: '/products', query })}`
}

useHead(() => {
  const link: Array<Record<string, string>> = []
  if (currentPage.value > 1) {
    link.push({ rel: 'prev', href: absolutePageUrl(currentPage.value - 1) })
  }
  if (currentPage.value < totalPages.value) {
    link.push({ rel: 'next', href: absolutePageUrl(currentPage.value + 1) })
  }
  return { link }
})
</script>
