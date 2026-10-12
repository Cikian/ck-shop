<template>
  <div class="min-h-screen bg-[var(--color-background)]">
    <!-- Hero Section -->
    <HomeHeroBanner />

    <!-- Featured Products Section -->
    <section class="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <div class="mb-12 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <span class="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
            <UIcon name="lucide:sparkles" class="h-4 w-4" />
            {{ t('home.featured') }}
          </span>
          <h2 class="mt-3 text-3xl font-bold text-[var(--color-primary)] sm:text-4xl">
            {{ t('home.recommended') }}
          </h2>
          <p class="mt-2 text-[var(--color-text-muted)]">
            {{ t('home.recommendedDesc') }}
          </p>
        </div>
        <NuxtLinkLocale
          to="/products"
          class="group inline-flex items-center gap-2 text-sm font-semibold text-[var(--color-primary)] transition-colors duration-200 hover:text-[var(--color-accent)] cursor-pointer"
        >
          {{ t('common.viewMore') }}
          <UIcon name="lucide:arrow-right" class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-1" />
        </NuxtLinkLocale>
      </div>

      <div class="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3 xl:grid-cols-4">
        <StoreProductCard
          v-for="(product, idx) in recommendedProducts"
          :key="product.id"
          :product="product"
          :style="{ animationDelay: `${idx * 80}ms` }"
          class="animate-fade-in-up opacity-0"
        />
      </div>
    </section>

    <!-- Categories Bento Grid -->
    <section class="bg-[var(--color-surface-secondary)] py-20">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mb-12 text-center">
          <span class="text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
            {{ t('home.categories') }}
          </span>
          <h2 class="mt-3 text-3xl font-bold text-[var(--color-primary)] sm:text-4xl">
            {{ t('home.shopByCategory') }}
          </h2>
          <p class="mx-auto mt-2 max-w-2xl text-[var(--color-text-muted)]">
            {{ t('home.shopByCategoryDesc') }}
          </p>
        </div>

        <!-- 分类区：只渲染后端 /api/categories 返回的**本店真实分类**，空数据给空态 -->
        <p
          v-if="!topCategories.length"
          class="rounded-2xl border border-dashed border-[var(--color-border)] p-12 text-center text-sm text-[var(--color-text-muted)]"
        >
          {{ t('categories.empty') }}
        </p>

        <div v-else class="grid grid-cols-2 gap-4 sm:gap-6 md:grid-cols-4">
          <NuxtLinkLocale
            v-for="(cat, idx) in topCategories"
            :key="cat.id"
            :to="`/categories/${cat.id}`"
            class="group relative aspect-square overflow-hidden rounded-[var(--radius-card)] bg-[var(--color-primary)] shadow-md transition-all duration-500 hover:-translate-y-2 hover:shadow-xl animate-fade-in-up opacity-0"
            :style="{ animationDelay: `${idx * 100}ms` }"
          >
            <img
              v-if="categoryCover(cat)"
              :src="categoryCover(cat)"
              :alt="cat.name"
              class="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110"
            />
            <div class="absolute inset-0 bg-gradient-to-t from-[var(--color-primary)]/85 via-[var(--color-primary)]/30 to-transparent transition-all duration-500 group-hover:from-[var(--color-primary)]/95 group-hover:via-[var(--color-accent)]/20"></div>
            <div class="absolute bottom-0 left-0 right-0 p-5 sm:p-6">
              <h3 class="text-lg font-bold text-white sm:text-xl">{{ cat.name }}</h3>
              <p class="mt-1 text-sm text-white/70">
                {{ cat.productCount || 0 }} {{ t('home.items') }}
              </p>
              <div class="mt-3 inline-flex items-center gap-1 text-sm font-medium text-white transition-all duration-300 group-hover:text-[var(--color-accent-300)]">
                {{ t('common.viewMore') }}
                <UIcon name="lucide:arrow-right" class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-1" />
              </div>
            </div>
          </NuxtLinkLocale>
        </div>
      </div>
    </section>

    <!-- New Arrivals Section -->
    <section class="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <div class="mb-12 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <span class="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
            <UIcon name="lucide:package" class="h-4 w-4" />
            {{ t('home.featured') }}
          </span>
          <h2 class="mt-3 text-3xl font-bold text-[var(--color-primary)] sm:text-4xl">
            {{ t('home.moreProducts') }}
          </h2>
          <p class="mt-2 text-[var(--color-text-muted)]">
            {{ t('home.moreProductsDesc') }}
          </p>
        </div>
      </div>

      <div class="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3 xl:grid-cols-4">
        <StoreProductCard
          v-for="(product, idx) in moreProducts"
          :key="product.id"
          :product="product"
          :style="{ animationDelay: `${idx * 80}ms` }"
          class="animate-fade-in-up opacity-0"
        />
      </div>
    </section>

    <!-- 原「Promo Banner」（Up to 50% OFF / 2 Days Left / 12:45:30 倒计时）已删除：
         后台没有促销、折扣、活动截止时间这些概念，属于编造内容，不做替换式兜底。
         原「Testimonials」（Sarah Johnson 等编造买家评价）同样删除。 -->

    <!-- Newsletter / CTA Section -->
    <section class="relative overflow-hidden bg-primary-gradient py-20 sm:py-24">
      <!-- Background decoration -->
      <div class="pointer-events-none absolute inset-0">
        <div class="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-[var(--color-accent)]/15 blur-3xl animate-blob"></div>
        <div class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-[var(--color-info)]/10 blur-3xl animate-blob animation-delay-300"></div>
      </div>

      <!-- Grid pattern -->
      <div class="pointer-events-none absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.03)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.03)_1px,transparent_1px)] bg-[size:40px_40px]"></div>

      <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <div class="mx-auto mb-6 flex h-14 w-14 items-center justify-center rounded-2xl bg-white/10 backdrop-blur-sm border border-white/10">
            <UIcon name="lucide:mail" class="h-7 w-7 text-white" />
          </div>
          <h2 class="text-3xl font-bold text-white sm:text-4xl">
            {{ t('footer.newsletter') }}
          </h2>
          <p class="mx-auto mt-4 max-w-lg text-base text-white/70">
            {{ t('footer.newsletterDesc') }}
          </p>
          <form class="mt-8 flex flex-col gap-3 sm:flex-row" @submit.prevent="handleSubscribe">
            <div class="relative flex-1">
              <span class="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-white/40">
                <UIcon name="lucide:mail" class="h-5 w-5" />
              </span>
              <input
                v-model="email"
                type="email"
                :placeholder="t('footer.emailPlaceholder')"
                class="w-full rounded-2xl border border-white/10 bg-white/10 py-3.5 pl-12 pr-4 text-sm text-white placeholder:text-white/40 backdrop-blur-sm transition-all duration-200 focus:border-[var(--color-accent)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]/20 focus:bg-white/15"
              />
            </div>
            <button
              type="submit"
              class="inline-flex h-14 items-center justify-center gap-2 rounded-2xl bg-[var(--color-accent)] px-8 text-sm font-semibold text-white transition-all duration-300 hover:bg-[var(--color-accent-light)] hover:shadow-xl hover:shadow-[var(--color-accent)]/30 cursor-pointer btn-shine"
            >
              {{ t('footer.subscribe') }}
              <UIcon name="lucide:arrow-right" class="h-4 w-4" />
            </button>
          </form>
          <div class="mt-6 flex items-center justify-center gap-6 text-xs text-white/40">
            <span class="flex items-center gap-1.5">
              <UIcon name="lucide:shield-check" class="h-3.5 w-3.5" />
              {{ t('home.noSpam') }}
            </span>
            <span class="flex items-center gap-1.5">
              <UIcon name="lucide:unlock" class="h-3.5 w-3.5" />
              {{ t('home.unsubscribeAnytime') }}
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import { useStoreProductList } from '~~/composables/useProducts'
import type { StoreCategoryNode } from '~~/types/store-category'

const { t, locale } = useI18n()
const email = ref('')

// 首页商品区块：真实数据来自后端 /shop/product/store/list（恒返回 { records, total, ... }）。
// 后端没有 rating / reviewCount / isNew / isBestseller 概念，这里不伪造：
//   - "推荐商品" 用真实字段 isRecommend === 'Y'
//   - "更多商品" 用后端默认排序（is_recommend DESC, sort_order ASC, create_time DESC）里的其它商品
const homePageNo = ref(1)
const { data: homeProductPage } = await useStoreProductList({ pageNo: homePageNo, pageSize: 12 })

const homeRecords = computed(() => homeProductPage.value?.records ?? [])

const recommendedProducts = computed(() =>
  homeRecords.value.filter(product => product.isRecommend === 'Y').slice(0, 4),
)

const moreProducts = computed(() => {
  const shown = new Set(recommendedProducts.value.map(product => product.id))
  const rest = homeRecords.value.filter(product => !shown.has(product.id))
  return (rest.length ? rest : homeRecords.value).slice(0, 4)
})

// 分类区：真实数据来自后端 /api/categories（本店可见分类，name 已按语言解析、productCount 已汇总）。
// query 带 lang + key 带 locale：切语言后分类名必须重新请求，否则会显示旧语言缓存。
const { data: categoryTree } = await useFetch<StoreCategoryNode[]>('/api/categories', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `home-categories-${locale.value}`),
  default: () => [],
  watch: [locale],
})

/** 首页只展示前 8 个一级分类，空数据时由模板给空态（不回退假数据） */
const topCategories = computed(() => (categoryTree.value || []).slice(0, 8))

/** 分类封面：优先后端返回的封面，其次图标；都没有时留空，由模板的渐变背景兜底 */
function categoryCover(category: StoreCategoryNode): string {
  return category.coverUrl || category.iconUrl || ''
}

function handleSubscribe() {
  console.log('Subscribe email:', email.value)
  email.value = ''
}

useSeoMeta({
  title: () => t('home.title'),
  description: () => t('home.description'),
})
</script>
