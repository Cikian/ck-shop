<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto max-w-7xl px-2 pb-4 sm:px-3 lg:px-4">
      <!--
        ===================== 首屏（设计稿第 3 行）=====================
        左：竖向分类菜单（桌面端）
        右：大轮播 Banner + 限时秒杀条
      -->
      <div class="grid gap-5 lg:grid-cols-[236px_minmax(0,1fr)]">
        <HomeCategoryMenu
          class="hidden lg:flex"
          :categories="categories"
          :pending="categoriesPending"
        />

        <div class="space-y-4">
          <!-- 移动端：分类菜单收起成横向药丸条（分类入口不能只在汉堡菜单里） -->
          <div
            v-if="categories.length"
            class="no-scrollbar -mx-1 flex items-center gap-2 overflow-x-auto px-1 lg:hidden"
          >
            <NuxtLinkLocale
              v-for="category in categories.slice(0, 8)"
              :key="category.id"
              :to="`/categories/${category.id}`"
              class="chip shrink-0"
            >
              {{ category.name }}
            </NuxtLinkLocale>
          </div>

          <HomeHeroCarousel :slides="slides" />

          <!--
            限时秒杀条：只有后端给出「进行中」的活动才渲染。
            多条活动时由组件内部轮流播放（倒计时完全由各活动的 end_time 驱动，
            首页没有任何一处写死时间）；全部过期时组件自身不渲染。
          -->
          <HomeFlashSaleBar v-if="promotionList.length" :promotions="promotionList" />
        </div>
      </div>

      <!-- ===================== 热销爆款横排（设计稿第 4 行）===================== -->
      <HomeHotRow :products="hotProducts" />

      <!-- ===================== 商品流（设计稿第 5 行）===================== -->
      <section class="mt-8" :aria-label="t('shop.recommendTitle')">
        <h2 class="mb-5 text-[22px] font-black tracking-tight text-[var(--color-ink)] sm:text-2xl">
          {{ t('shop.recommendTitle') }}
        </h2>

        <!--
          错误态：一件商品都没加载出来时给整块提示（可重试，不留白板）。
          已经有商品时不走这里 —— 那种情况在底部提示，免得"加载更多"失败一次
          就把用户已经逛到的商品全部抹掉。
        -->
        <div v-if="error && !visibleProducts.length" class="ui-card flex flex-col items-center gap-2 py-16 text-center">
          <UIcon name="lucide:wifi-off" class="h-10 w-10 text-[var(--color-error)]" aria-hidden="true" />
          <p class="text-base font-bold text-[var(--color-ink)]">{{ t('products.loadError') }}</p>
          <p class="max-w-md text-sm text-[var(--color-muted)]">{{ t('products.loadErrorDesc') }}</p>
          <button type="button" class="ui-pill ui-pill-brand mt-3 h-10 px-6 text-sm" @click="refresh()">
            <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
            {{ t('common.retry') }}
          </button>
        </div>

        <!-- 首次加载骨架：只在**一件商品都没有**时出现（加载更多时保留已有商品，不整页闪白） -->
        <div
          v-else-if="pending && !visibleProducts.length"
          class="product-grid"
          aria-hidden="true"
        >
          <div v-for="i in SKELETON_COUNT" :key="i" class="ui-card overflow-hidden">
            <div class="aspect-square animate-pulse bg-[var(--color-surface-secondary)]" />
            <div class="space-y-2 p-3.5">
              <div class="h-4 w-4/5 animate-pulse rounded bg-[var(--color-surface-secondary)]" />
              <div class="h-4 w-1/2 animate-pulse rounded bg-[var(--color-surface-secondary)]" />
              <div class="h-6 w-1/3 animate-pulse rounded bg-[var(--color-surface-secondary)]" />
            </div>
          </div>
        </div>

        <!--
          商品网格：直接复用 StoreProductCard，首页不另写一套卡片。

          列数按"卡片永远不窄于 ~240px"来排：
            lg  ⁴列 → 每张约 238px
            xl ⁵列 → 约 238px   （xl 从 1280px 开始，那时容器还没到 1920，
                                 若这里就上 6 列，卡片会被压回 196px）
            2xl⁶列 → 约 239px；在 1920px 容器下则是 301px，对齐设计稿的密度
        -->
        <div
          v-else-if="visibleProducts.length"
          class="product-grid"
        >
          <StoreProductCard v-for="product in visibleProducts" :key="product.id" :product="product" />
        </div>

        <!--
          空态：后端确实没有在售商品。
          注意条件是 `!hotProducts.length`：热销爆款横排的商品已从主网格剔除，
          若某个店铺商品总数 ≤ 6（全被横排展示完），主网格必然为空 ——
          那时页面并没有"没商品"，不能报「暂无商品」。
        -->
        <div v-else-if="!hotProducts.length" class="ui-card flex flex-col items-center gap-2 py-16 text-center">
          <UIcon name="lucide:package-search" class="h-12 w-12 text-[var(--color-muted)]" aria-hidden="true" />
          <p class="text-base font-bold text-[var(--color-ink)]">{{ t('products.noProducts') }}</p>
          <p class="max-w-md text-sm text-[var(--color-muted)]">{{ t('products.noProductsDesc') }}</p>
        </div>

        <!--
          底部：追加式「加载更多」而不是跳页。
          首页是浏览主战场，用户的行为是"一直往下逛"，跳页会把这份连续感打断。
        -->
        <div v-if="visibleProducts.length" class="mt-8 flex flex-col items-center gap-3">
          <template v-if="error">
            <p class="flex items-center gap-1.5 text-xs text-[var(--color-error)]">
              <UIcon name="lucide:circle-alert" class="h-3.5 w-3.5" aria-hidden="true" />
              {{ t('products.loadError') }}
            </p>
            <button type="button" class="ui-pill ui-pill-ghost h-11 px-8 text-sm" @click="refresh()">
              <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
              {{ t('common.retry') }}
            </button>
          </template>

          <template v-else>
            <p class="text-xs text-[var(--color-muted)]">
              {{ hasMore
                ? t('products.showingResults', { count: visibleProducts.length })
                : t('products.totalProducts', { count: totalCount }) }}
            </p>
            <button
              v-if="hasMore"
              type="button"
              class="ui-pill ui-pill-ghost h-11 px-8 text-sm disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="pending"
              :aria-busy="pending"
              @click="loadMore"
            >
              <UIcon
                :name="pending ? 'lucide:loader-circle' : 'lucide:chevron-down'"
                class="h-4 w-4 motion-reduce:animate-none"
                :class="pending ? 'animate-spin' : ''"
                aria-hidden="true"
              />
              {{ pending ? t('common.loading') : t('common.viewMore') }}
            </button>
          </template>
        </div>
      </section>
    </div>

    <!-- 页脚 -->
    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import type { StoreCategoryNode } from '~~/types/store-category'
import type { StoreProductCard } from '~~/types/shop-product'
import type { ShopActivePromotion } from '@@/server/api/promotions/active.get'
import type { SlideItem } from '@@/server/api/home/slide.get'
import { useStoreProductList } from '~~/composables/useProducts'

/**
 * 首页 = 浏览主战场，结构**完全对齐设计稿**：
 *
 *   1. 首屏：左侧竖向分类菜单 + 右侧大轮播 Banner + 限时秒杀条
 *   2. 热销爆款横排（按真实销量排序）
 *   3. 商品流（6 列，加载更多）
 *
 * 三处数据全部来自后端真实接口，没有任何一块是前端编的：
 *   分类   /api/categories       轮播 /api/home/slide（MongoDB 真实轮播图）
 *   秒杀   /api/promotions/active（进行中的活动，倒计时来自 end_time）
 *   商品   /api/products（热销排 sortBy=sales，主商品流按后端默认排序）
 *
 * 设计稿里首页**没有排序控件**，所以这里不再放排序 chips
 * （排序能力仍在搜索结果页 /products 上保留）。
 */
const { t, locale } = useI18n()

/** 主商品流每页条数：6 列 × 3 行，翻页粒度不至于碎 */
const HOME_PAGE_SIZE = 18
/** 热销爆款横排取 6 件 */
const HOT_PAGE_SIZE = 6
/** 骨架屏块数：比一页商品少，够撑住首屏高度即可 */
const SKELETON_COUNT = 12

// ===========================================================================
// 1. 分类（key 与 Header.vue 完全一致 → 整站分类树只请求一次）
// ===========================================================================
const { data: categoryTree, pending: categoriesPending } = await useFetch<StoreCategoryNode[]>('/api/categories', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `store-categories-${locale.value}`),
  default: () => [],
  watch: [locale],
})

const categories = computed(() => categoryTree.value || [])

// ===========================================================================
// 2. 轮播 Banner（后端真实轮播图；没有数据时组件内部给中性占位）
// ===========================================================================
const { data: slideData } = await useFetch<SlideItem[]>('/api/home/slide', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `home-slide-${locale.value}`),
  default: () => [],
  watch: [locale],
})

const slides = computed(() => slideData.value || [])

// ===========================================================================
// 3. 限时秒杀（进行中的活动，可能多条）
// ===========================================================================
const { data: promotionData } = await useFetch<ShopActivePromotion[]>('/api/promotions/active', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `store-active-promotions-${locale.value}`),
  default: () => [],
  watch: [locale],
})

/**
 * 把"哪些活动真的在进行中"的过滤交给 HomeFlashSaleBar：
 * 它内部有一秒一次的心跳，能随着时间推移把刚过期的活动剔掉；
 * 放在页面里做反而需要一个额外的定时器。首页只负责把数组传下去。
 */
const promotionList = computed(() => promotionData.value || [])

// ===========================================================================
// 4. 热销爆款横排（真实销量排序）
// ===========================================================================
const { data: hotPage } = await useStoreProductList({
  pageNo: ref(1),
  pageSize: HOT_PAGE_SIZE,
  sortBy: ref<'sales'>('sales'),
})

const hotProducts = computed(() => hotPage.value?.records ?? [])

// ===========================================================================
// 5. 主商品流
// ===========================================================================
const pageNo = ref(1)

/**
 * 换语言时先回到第 1 页。
 * 这份 watch 故意注册在 useStoreProductList **之前**并开 flush: 'sync'：
 * 语言变化时先同步改掉页码，请求 key 只变一次；否则会先按旧页码请求一页新语言的商品
 * （白跑一次），更糟的是会把两种语言的商品混进同一条流。
 */
watch(locale, () => {
  if (pageNo.value === 1) return
  pageNo.value = 1
}, { flush: 'sync' })

const { data: page, pending, error, refresh } = await useStoreProductList({
  pageNo,
  pageSize: HOME_PAGE_SIZE,
})

/**
 * 「加载更多」的累积层。
 *
 * useStoreProductList 每次只返回**一页**，而浏览型首页要的是连续的流，
 * 所以这里按页存下已请求的内容，渲染时再摊平：
 *   - 按页存而不是就地 concat：换语言后第 1 页的响应会整份替换掉累计负载，
 *     不会出现「旧语言的第 2 页 + 新语言的第 1 页」这种混合列表；
 *   - 空响应（key 切换的过渡帧、加载失败）直接返回，**不清空**已有列表，避免列表闪白。
 */
const loadedPages = ref<Array<{ page: number; records: StoreProductCard[] }>>([])
/** 最近一次真实响应里的总数：过渡帧里 page.value 可能被重置，用它避免「加载更多」按钮闪烁 */
const totalCount = ref(0)

watch(page, (current) => {
  const records = current?.records ?? []
  if (!records.length) return

  const pageIndex = current?.current || pageNo.value
  // total 缺失或为 0 时按已加载条数兜底，避免出现「共 0 件商品」却摆着一屏商品
  totalCount.value = Math.max(current?.total ?? 0, records.length)

  loadedPages.value = pageIndex <= 1
    ? [{ page: 1, records }]
    : [
        ...loadedPages.value.filter(entry => entry.page !== pageIndex),
        { page: pageIndex, records },
      ].sort((a, b) => a.page - b.page)
}, { immediate: true })

/**
 * 渲染用的商品列表：按页码顺序摊平，并按商品 id 去重。
 * 后端分页在两次请求之间发生数据变动时，同一件商品可能出现在相邻两页，
 * 这里保证它**只渲染一次**；没有 id 的记录无法构成商品链接（id 即 slug），直接跳过。
 *
 * 同时排除掉热销横排里已经出现过的商品，避免同一件商品在首屏出现两次。
 */
const visibleProducts = computed<StoreProductCard[]>(() => {
  const seen = new Set<string>(hotProducts.value.map(product => product.id))
  const list: StoreProductCard[] = []
  for (const entry of loadedPages.value) {
    for (const product of entry.records) {
      if (!product.id || seen.has(product.id)) continue
      seen.add(product.id)
      list.push(product)
    }
  }
  return list
})

const hasMore = computed(() => visibleProducts.value.length < totalCount.value)

function loadMore() {
  if (pending.value || !hasMore.value) return
  pageNo.value += 1
}

useSeoMeta({
  title: () => t('home.title'),
  description: () => t('home.description'),
})
</script>
