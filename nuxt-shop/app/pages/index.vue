<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto max-w-7xl px-2 pb-4 sm:px-3 lg:px-4">
      <!--
        ===================== 首屏（设计稿第 3 行）=====================
        左：竖向分类菜单（桌面端）
        右：大轮播 Banner + 限时秒杀条
      -->
      <!--
        次级请求（分类 / 轮播 / 促销）失败时不要静默：
        给一条轻量可重试提示，避免左栏留下一张空白的分类卡。
      -->
      <div
        v-if="secondaryError"
        class="mb-4 flex flex-wrap items-center gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-3.5 py-2.5 text-[13px] font-medium text-[var(--color-error-600)]"
        role="status"
      >
        <UIcon name="lucide:wifi-off" class="h-4 w-4 shrink-0" aria-hidden="true" />
        <span>{{ t('products.loadError') }}</span>
        <button
          type="button"
          class="ml-auto inline-flex items-center gap-1 font-bold underline underline-offset-2"
          @click="refreshSecondary"
        >
          <UIcon name="lucide:rotate-ccw" class="h-3.5 w-3.5" aria-hidden="true" />
          {{ t('common.retry') }}
        </button>
      </div>

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

        <Transition name="fade" mode="out-in">
          <!--
            错误态：一件商品都没加载出来时给整块提示（可重试，不留白板）。
            已经有商品时不走这里 —— 那种情况在底部提示，免得"加载更多"失败一次
            就把用户已经逛到的商品全部抹掉。
          -->
          <div
            v-if="error && !visibleProducts.length"
            key="error"
            class="ui-card flex flex-col items-center gap-2 py-16 text-center"
          >
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
            key="skeleton"
            class="product-grid"
            aria-hidden="true"
          >
            <div v-for="i in SKELETON_COUNT" :key="i" class="ui-card overflow-hidden">
              <div class="aspect-square animate-pulse bg-[var(--color-surface-secondary)]" />
              <div class="space-y-2 p-3.5">
                <div class="h-4 w-4/5 animate-pulse rounded bg-[var(--color-surface-secondary)]" />
                <div class="h-4 w-1/2 animate-pulse rounded bg-[var(--color-surface-secondary)]" />
                <div class="h-6 w-1/3 animate-pulse rounded bg-[var(--color-surface-secondary)]" />
                <!-- 加购按钮占位：与真实卡片高度对齐，避免骨架→内容时 CLS -->
                <div class="h-9 w-full animate-pulse rounded-full bg-[var(--color-surface-secondary)]" />
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
            key="content"
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
          <div
            v-else-if="!hotProducts.length"
            key="empty"
            class="ui-card flex flex-col items-center gap-2 py-16 text-center"
          >
            <UIcon name="lucide:package-search" class="h-12 w-12 text-[var(--color-muted)]" aria-hidden="true" />
            <p class="text-base font-bold text-[var(--color-ink)]">{{ t('products.noProducts') }}</p>
            <p class="max-w-md text-sm text-[var(--color-muted)]">{{ t('products.noProductsDesc') }}</p>
          </div>
        </Transition>

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
                : t('products.allLoaded') }}
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
 *
 * 取数约定（2026-10 重构）：**所有请求都不 await**。
 *   页面 setup 同步返回 → 客户端导航立即渲染骨架；SSR 时 Nuxt 通过
 *   useAsyncData 内部的 onServerPrefetch 等待数据，HTML 仍是完整内容。
 *   5 个请求互不依赖，因此并行发起（以前是 5 个串行 await，回首页要等 5 个 RTT）。
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
// key 里已经带 locale：切语言时 key 变化会触发 useFetch 重新请求，
// 再写 watch: [locale] 只是让同一次切换多打一遍接口（重复请求）。
const {
  data: categoryTree,
  pending: categoriesPending,
  error: categoriesError,
  refresh: refreshCategories,
} = useFetch<StoreCategoryNode[]>('/api/categories', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `store-categories-${locale.value}`),
  default: () => [],
})

const categories = computed(() => categoryTree.value || [])

// ===========================================================================
// 2. 轮播 Banner（后端真实轮播图；没有数据时组件内部给中性占位）
// ===========================================================================
const { data: slideData, error: slideError, refresh: refreshSlides } = useFetch<SlideItem[]>('/api/home/slide', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `home-slide-${locale.value}`),
  default: () => [],
})

const slides = computed(() => slideData.value || [])

// ===========================================================================
// 3. 限时秒杀（进行中的活动，可能多条）
// ===========================================================================
const {
  data: promotionData,
  error: promotionError,
  refresh: refreshPromotions,
} = useFetch<ShopActivePromotion[]>('/api/promotions/active', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `store-active-promotions-${locale.value}`),
  default: () => [],
})

/**
 * 把"哪些活动真的在进行中"的过滤交给 HomeFlashSaleBar：
 * 它内部有一秒一次的心跳，能随着时间推移把刚过期的活动剔掉；
 * 放在页面里做反而需要一个额外的定时器。首页只负责把数组传下去。
 */
const promotionList = computed(() => promotionData.value || [])

/** 次级请求失败：不再静默（以前分类挂了只会留下一个空白分类卡） */
const secondaryError = computed(() => !!(categoriesError.value || slideError.value || promotionError.value || hotError.value))

function refreshSecondary() {
  void refreshCategories()
  void refreshSlides()
  void refreshPromotions()
  void refreshHot()
}

// ===========================================================================
// 4. 热销爆款横排（真实销量排序）
// ===========================================================================
const { data: hotPage, error: hotError, refresh: refreshHot } = useStoreProductList({
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
 * 「加载更多」的累积层：点击加载更多时把"当前页"快照压栈，渲染时由 computed 摊平。
 *
 * ⚠️ 刻意**不用 `watch(page)` 做累积**：首页已改成不 await 取数，SSR 是在 setup 之后
 *    才 resolve 分页数据的；依赖 watcher 更新的渲染数据在 SSR 下时序不可靠。
 *    现在首屏只读 page.value（渲染前已 resolve），SSR HTML 天然是完整商品流。
 */
const loadedPages = ref<Array<{ page: number; records: StoreProductCard[] }>>([])

/**
 * 换语言时回到第 1 页，并丢掉已累积的旧语言页面。
 * 这份 watch 故意注册在 useStoreProductList **之前**并开 flush: 'sync'：
 * 语言变化时先同步改掉页码，请求 key 只变一次；否则会先按旧页码请求一页新语言的商品
 * （白跑一次），更糟的是会把两种语言的商品混进同一条流。
 */
watch(locale, () => {
  loadedPages.value = []
  if (pageNo.value === 1) return
  pageNo.value = 1
}, { flush: 'sync' })

const { data: page, pending, error, refresh } = useStoreProductList({
  pageNo,
  pageSize: HOME_PAGE_SIZE,
})

/** 当前正在展示的页码（后端回传的权威值优先，超出范围时后端会纠正） */
const currentPageNo = computed(() => page.value?.current || pageNo.value)

/**
 * 渲染用的商品列表：已加载页按页码摊平，并按商品 id 去重。
 * 后端分页在两次请求之间发生数据变动时，同一件商品可能出现在相邻两页，
 * 这里保证它**只渲染一次**；没有 id 的记录无法构成商品链接（id 即 slug），直接跳过。
 * 同时排除掉热销横排里已经出现过的商品，避免同一件商品在首屏出现两次。
 */
const visibleProducts = computed<StoreProductCard[]>(() => {
  const current = page.value?.records ?? []
  // 回到第 1 页（换语言 / 重试）时忽略旧快照，避免两页不同批次的数据混在一起
  const entries = currentPageNo.value <= 1
    ? [{ page: 1, records: current }]
    : [...loadedPages.value, { page: currentPageNo.value, records: current }].sort((a, b) => a.page - b.page)

  const seen = new Set<string>(hotProducts.value.map(product => product.id))
  const list: StoreProductCard[] = []
  for (const entry of entries) {
    for (const product of entry.records) {
      if (!product.id || seen.has(product.id)) continue
      seen.add(product.id)
      list.push(product)
    }
  }
  return list
})

/**
 * 是否还有下一页：直接以后端分页元数据为准（当前页 < 总页数）。
 *   - 空页（数据变动导致）records 为空 → 视为到底，按钮不会无限留在页面上；
 *   - 不能用"可见数 < 后端总数"判断：可见数剔除了热销横排并去重，两者口径不同，
 *     小目录店铺会在翻到空页后永远显示「加载更多」。
 */
const hasMore = computed(() => {
  // 加载中保持"还有更多"的判定，避免按钮/文案在 pending 的过渡帧里闪成"已经到底了"
  if (pending.value) return true
  const records = page.value?.records ?? []
  if (!records.length) return false
  const pages = page.value?.pages ?? 0
  return pages > 0 && currentPageNo.value < pages
})

function loadMore() {
  if (pending.value || !hasMore.value) return
  const records = page.value?.records ?? []
  if (records.length) {
    const currentPage = currentPageNo.value
    loadedPages.value = [
      ...loadedPages.value.filter(entry => entry.page !== currentPage),
      { page: currentPage, records },
    ]
  }
  pageNo.value += 1
}

// 首页标题带上真实店铺名（旧版是占位符「欢迎来到 Nuxt Shop」）
const { storeName } = useStoreInfo()
const siteTitle = computed(() => t('home.title', { name: storeName.value || t('shop.brandName') }))

useSeoMeta({
  title: () => siteTitle.value,
  description: () => t('home.description'),
})
</script>
