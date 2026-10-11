<script setup lang="ts">
// 某个分类下的商品列表（含子分类的商品——后端按"分类及其后代"匹配）。
//
// 注意：这里查询的是**本店**的商品。店铺身份由 BFF 注入 X-Store-Code，
// 页面 URL 里不含任何店铺参数（商品链接要分享到社交平台，必须短）。
import { STORE_PRODUCT_PAGE_SIZE, useStoreProductList } from '~~/composables/useProducts'

const { t } = useI18n()
const route = useRoute()

const categoryId = computed(() => String(route.params.id || ''))

// 分类名从分类树里取（一次请求，用于标题与面包屑）
const { data: categories } = await useFetch<any[]>('/api/categories', {
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

<template>
  <div class="min-h-screen bg-[var(--color-background)] pt-navbar">
    <div class="mx-auto max-w-7xl px-4 pb-16 sm:px-6 lg:px-8">
      <header class="mb-8 pt-8 sm:pt-10">
        <!-- 面包屑：分类总览 → 当前分类 -->
        <nav class="mb-3 flex items-center gap-2 text-sm text-[var(--color-text-muted)]">
          <NuxtLink to="/categories" class="transition-colors hover:text-[var(--color-accent)]">
            {{ t('categories.title') }}
          </NuxtLink>
          <span>/</span>
          <span class="text-[var(--color-text)]">{{ currentCategory?.name || categoryId }}</span>
        </nav>
        <h1 class="text-3xl font-bold text-[var(--color-primary)] sm:text-4xl">
          {{ currentCategory?.name || t('categories.title') }}
        </h1>
        <p class="mt-2 text-sm text-[var(--color-text-muted)]">
          {{ t('products.totalProducts', { count: total }) }}
        </p>
      </header>

      <p v-if="pending" class="py-16 text-center text-sm text-[var(--color-text-muted)]">
        {{ t('common.loading') }}
      </p>

      <p
        v-else-if="error"
        class="rounded-xl border border-dashed border-[var(--color-border)] p-12 text-center text-sm text-[var(--color-text-muted)]"
      >
        {{ t('products.noProductsDesc') }}
      </p>

      <p
        v-else-if="!records.length"
        class="rounded-xl border border-dashed border-[var(--color-border)] p-12 text-center text-sm text-[var(--color-text-muted)]"
      >
        {{ t('products.noProducts') }}
      </p>

      <template v-else>
        <div class="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3 xl:grid-cols-4">
          <StoreProductCard v-for="product in records" :key="product.id" :product="product" />
        </div>

        <!-- 分页 -->
        <nav v-if="totalPages > 1" class="mt-10 flex items-center justify-center gap-2">
          <button
            type="button"
            class="rounded-lg border border-[var(--color-border)] px-3 py-1.5 text-sm disabled:opacity-40"
            :disabled="currentPage <= 1"
            @click="goToPage(currentPage - 1)"
          >
            {{ t('products.previous') }}
          </button>
          <span class="px-2 text-sm text-[var(--color-text-muted)]">
            {{ currentPage }} / {{ totalPages }}
          </span>
          <button
            type="button"
            class="rounded-lg border border-[var(--color-border)] px-3 py-1.5 text-sm disabled:opacity-40"
            :disabled="currentPage >= totalPages"
            @click="goToPage(currentPage + 1)"
          >
            {{ t('products.next') }}
          </button>
        </nav>
      </template>
    </div>
  </div>
</template>
