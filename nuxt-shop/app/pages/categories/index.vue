<template>
  <div class="min-h-[calc(100vh-var(--nav-total-height))] bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto max-w-7xl px-2 pb-16 sm:px-3 lg:px-4">
      <!-- 面包屑：首页 → 全部分类 -->
      <nav
        class="flex items-center gap-2 pt-6 text-sm text-[var(--color-muted)] sm:pt-8"
        :aria-label="t('cart.breadcrumbLabel')"
      >
        <NuxtLinkLocale
          to="/"
          class="cursor-pointer transition-colors hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
        >
          {{ t('common.home') }}
        </NuxtLinkLocale>
        <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span class="font-bold text-[var(--color-ink)]">{{ t('categories.title') }}</span>
      </nav>

      <header class="mb-7 mt-4 sm:mb-9">
        <h1 class="text-3xl font-black tracking-tight text-[var(--color-ink)] sm:text-4xl">
          {{ t('categories.title') }}
        </h1>
        <p class="mt-2 text-sm text-[var(--color-muted)]">
          {{ t('categories.description') }}
        </p>
      </header>

      <Transition name="fade" mode="out-in">
      <!-- 加载骨架：分类是整页主内容，用骨架代替一行"加载中"更稳 -->
      <div
        v-if="pending"
        key="skeleton"
        class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
        aria-busy="true"
      >
        <span class="sr-only">{{ t('common.loading') }}</span>
        <div v-for="i in 6" :key="i" class="ui-card animate-pulse overflow-hidden">
          <div class="aspect-[16/9] bg-[var(--color-line-soft)]"></div>
          <div class="space-y-2 p-4">
            <div class="h-4 w-1/2 rounded bg-[var(--color-line-soft)]"></div>
            <div class="h-3 w-1/3 rounded bg-[var(--color-line-soft)]"></div>
            <!-- 二级分类药丸占位：真实卡片带子分类，骨架不能只有两行（否则切换时 CLS 明显） -->
            <div class="h-6 w-2/3 rounded-full bg-[var(--color-line-soft)]"></div>
          </div>
        </div>
      </div>

      <!-- 错误态：/api/categories 失败时给出可重试的真实出口 -->
      <div v-else-if="error" key="error" class="ui-card flex flex-col items-center px-6 py-16 text-center">
        <span class="flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error-50)] text-[var(--color-error-600)]">
          <UIcon name="lucide:wifi-off" class="h-7 w-7" aria-hidden="true" />
        </span>
        <h2 class="mt-5 text-lg font-black text-[var(--color-ink)]">{{ t('products.loadError') }}</h2>
        <p class="mt-2 text-sm text-[var(--color-muted)]">{{ t('products.loadErrorDesc') }}</p>
        <button
          type="button"
          class="ui-pill ui-pill-brand mt-6 h-11 px-6 text-sm"
          @click="refresh()"
        >
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
          {{ t('common.retry') }}
        </button>
      </div>

      <!-- 空态 -->
      <div v-else-if="!categories.length" key="empty" class="ui-card flex flex-col items-center px-6 py-16 text-center">
        <span class="flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-brand-50)] text-[var(--color-brand-500)]">
          <UIcon name="lucide:folder-open" class="h-7 w-7" aria-hidden="true" />
        </span>
        <p class="mt-5 text-sm font-bold text-[var(--color-ink)]">{{ t('categories.empty') }}</p>
      </div>

      <!-- 分类树：一级分类卡片，卡片内的药丸是它的二级分类（各自可点进商品列表） -->
      <div v-else key="content" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <article v-for="top in categories" :key="top.id" class="ui-card ui-card-hover overflow-hidden">
          <NuxtLinkLocale :to="`/categories/${top.id}`" class="group block">
            <div class="relative aspect-[16/9] overflow-hidden bg-[var(--color-surface-secondary)]">
              <ShopImage
                :src="categoryCover(top)"
                :alt="top.name"
                :width="1600"
                :height="900"
                loading="lazy"
                class="h-full w-full object-cover transition-transform duration-500 ease-out group-hover:scale-105 motion-reduce:transition-none motion-reduce:group-hover:scale-100"
                icon="lucide:folder-open"
                icon-class="h-10 w-10 text-[var(--color-brand-400)]"
              />
              <!-- 商品数来自后端 productCount（含子分类）；后端没给就不渲染，不拿 0 冒充 -->
              <span v-if="top.productCount != null" class="badge badge-soft absolute left-3 top-3">
                {{ t('shop.categoryItems', { count: top.productCount }) }}
              </span>
            </div>

            <div class="p-4">
              <h2 class="text-[15px] font-bold text-[var(--color-ink)] transition-colors group-hover:text-[var(--color-brand-600)] motion-reduce:transition-none">
                {{ top.name }}
              </h2>
            </div>
          </NuxtLinkLocale>

          <!-- 二级 / 三级分类：二级是药丸，三级跟在它下面用更小的文字链（整棵树都可点） -->
          <div
            v-if="top.children?.length"
            class="space-y-2.5 border-t border-[var(--color-line-soft)] px-4 pb-4 pt-3"
          >
            <div v-for="child in top.children" :key="child.id">
              <NuxtLinkLocale
                :to="`/categories/${child.id}`"
                class="inline-flex cursor-pointer items-center gap-1.5 rounded-full bg-[var(--color-line-soft)] px-3 py-1.5 text-xs font-bold text-[var(--color-ink-soft)] transition-colors hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
              >
                {{ child.name }}
                <span v-if="child.productCount != null" class="tabular font-medium text-[var(--color-muted)]">
                  {{ t('shop.categoryItems', { count: child.productCount }) }}
                </span>
              </NuxtLinkLocale>

              <div v-if="child.children?.length" class="mt-2 flex flex-wrap gap-x-3 gap-y-1 pl-1">
                <NuxtLinkLocale
                  v-for="grand in child.children"
                  :key="grand.id"
                  :to="`/categories/${grand.id}`"
                  class="cursor-pointer text-[11px] text-[var(--color-muted)] transition-colors hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
                >
                  {{ grand.name }}
                </NuxtLinkLocale>
              </div>
            </div>
          </div>
        </article>
      </div>
      </Transition>
    </div>
  </div>
</template>

<script setup lang="ts">
// 分类总览：只展示**本店可见**的分类。
//
// 后端已按店铺过滤好（全局共享节点 + 本店专属节点），前端不做也无法做店铺过滤——
// 店铺身份由本站服务端配置（STORE_CODE）通过 X-Store-Code 请求头带给后端，
// 浏览器不感知也改不了，因此 A 店的分类不可能出现在 B 店。
//
// 每个节点带的 productCount 是"含子分类的本店已上架商品数"（后端汇总，前端不再二次计算）。
//
// 语言：query 带 lang，key 带 locale —— 切语言时必须重新请求，否则分类名会是旧语言的缓存。
import type { StoreCategoryNode } from '~~/types/store-category'

const { t, locale } = useI18n()

const { data: categories, pending, error, refresh } = useFetch<StoreCategoryNode[]>('/api/categories', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `store-categories-${locale.value}`),
  default: () => [],
})

/** 封面优先，没有封面就退回图标；都没有则渲染品牌色占位（不做假图片） */
function categoryCover(node: StoreCategoryNode): string {
  return node.coverUrl || node.iconUrl || ''
}

useSeoMeta({
  title: () => t('categories.title'),
  description: () => t('categories.description'),
})
</script>
