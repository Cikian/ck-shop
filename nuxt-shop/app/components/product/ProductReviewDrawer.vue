<!--
  app/components/product/ProductReviewDrawer.vue

  商品详情「查看全部评价」左侧抽屉（参考图二：从左侧滑出，抽屉内滚动浏览更多评论）。

  为什么单独拆一个组件（而不是塞进 DetailSections 的模板里）：
    1) 抽屉是**独立的一层浮层**（teleport 到 body、自己管滚动与焦点），
       和详情页正文的锚点/吸顶逻辑没有任何关系，混在一起会让 DetailSections 越写越厚；
    2) 抽屉的"滚动到接近底部就自动续页"的状态（IntersectionObserver、预取余量、
       防重复请求）需要独立的生命周期（打开时挂、关闭时摘），组件化后这套逻辑能自洽。

  三条硬约束（对应用户 2026-10 本轮的要求）：
    1) **正文只预览 2~3 条**，更多评价点「查看全部评价」进抽屉 —— 见 DetailSections 的 PREVIEW_REVIEW_COUNT；
    2) **抽屉内滚动翻页、用户无感加载**：不用「加载更多」按钮，
       距底部 PREFETCH_PX 就开始拉下一页（IntersectionObserver 的 rootMargin + 距底判断双保险），
       底部用骨架屏占位，内容接上时列表不跳动；
    3) **汇总与列表同源**：分数 / 条数 / 星级分布仍然只来自 useProductReviews 的 summary，
       抽屉不另算一套。

  数据职责：本组件**不发请求**，只把 `load-more` 事件抛给页面（与 DetailSections 一致），
  取数、页码、hasMore 都由 composables/useReviews.ts 的 useProductReviews 负责。
-->
<template>
  <USlideover
    v-model:open="open"
    side="left"
    :title="t('shop.anchorReview')"
    :dismissible="!photoLightboxOpen"
    :ui="{
      overlay: 'bg-[var(--color-ink)]/45',
      // 宽度：窄屏整宽；平板 640px；桌面最宽 840px，但不超过视口的 60%（1024 这类窄桌面别把页面挤没）
      content: 'w-full sm:max-w-[640px] lg:max-w-[min(840px,60vw)]',
    }"
  >
    <!-- 整块自定义内容：头部固定、列表区自己滚（不改动 USlideover 的 dialog 语义，
         DialogTitle 仍由组件内部的 VisuallyHidden 提供，读屏能听到抽屉标题） -->
    <template #content>
      <div class="flex min-h-0 flex-1 flex-col bg-[var(--color-cream)]">
        <!-- ===== 头部：标题 / 条数 / 关闭 / 汇总条 ===== -->
        <header class="shrink-0 border-b border-[var(--color-line-soft)] bg-white/92 px-4 pb-4 pt-4 backdrop-blur sm:px-6">
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <h2 class="flex flex-wrap items-baseline gap-x-2 gap-y-1 text-base font-black text-[var(--color-ink)]">
                <span class="flex items-center gap-2">
                  <span class="h-4 w-1 shrink-0 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
                  {{ t('shop.anchorReview') }}
                </span>
                <span v-if="totalCount > 0" class="tabular text-[13px] font-bold text-[var(--color-muted)]">· {{ totalCount }}</span>
              </h2>
              <p class="mt-1 text-[12px] leading-relaxed text-[var(--color-muted)]">{{ t('review.drawerSubtitle') }}</p>
            </div>

            <button
              type="button"
              class="grid h-9 w-9 shrink-0 cursor-pointer place-items-center rounded-full text-[var(--color-muted)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-ink)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-400)] motion-reduce:transition-none"
              :aria-label="t('review.close')"
              @click="open = false"
            >
              <UIcon name="lucide:x" class="h-4 w-4" aria-hidden="true" />
            </button>
          </div>

          <!-- 汇总条：大分数 + 星级 + 5→1 分布（与正文里的汇总卡同源，只是压缩成一行放下） -->
          <div
            v-if="summary"
            class="mt-3 flex items-center gap-4 rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)] px-3.5 py-3"
          >
            <div class="shrink-0">
              <div class="flex items-baseline gap-1">
                <span class="tabular text-2xl font-black leading-none text-[var(--color-brand-600)]">{{ ratingAvgText }}</span>
                <span class="text-[11px] text-[var(--color-muted)]">/ 5</span>
              </div>
              <div class="mt-1 flex items-center gap-0.5" aria-hidden="true">
                <UIcon
                  v-for="star in 5"
                  :key="`drawer-summary-star-${star}`"
                  name="lucide:star"
                  class="h-3 w-3"
                  :class="star <= summaryStars ? 'fill-current text-[var(--color-accent-500)]' : 'text-[var(--color-line)]'"
                />
              </div>
            </div>

            <div class="min-w-0 flex-1 space-y-1">
              <div
                v-for="row in distributionRows"
                :key="`drawer-distribution-${row.rating}`"
                class="flex items-center gap-2"
              >
                <span class="tabular w-4 shrink-0 text-right text-[11px] text-[var(--color-muted)]">{{ row.rating }}</span>
                <span class="h-1.5 min-w-0 flex-1 overflow-hidden rounded-full bg-[var(--color-line)]">
                  <span class="block h-full rounded-full bg-[var(--color-accent-500)]" :style="{ width: `${row.percent}%` }"></span>
                </span>
                <span class="tabular w-7 shrink-0 text-right text-[11px] text-[var(--color-muted)]">{{ row.count }}</span>
              </div>
            </div>
          </div>
        </header>

        <!-- ===== 列表：自己滚动，滚到底部自动续页 ===== -->
        <div
          ref="scrollEl"
          class="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-4 sm:px-6"
        >
          <ul v-if="list.length" class="divide-y divide-[var(--color-line-soft)]">
            <li v-for="(review, index) in list" :key="`${review.id || 'review'}-${index}`" class="py-4 first:pt-0">
              <div class="flex items-start gap-3">
                <!-- 头像：匿名/无昵称走图标，其余取昵称首字符（纯装饰，昵称已可读） -->
                <span
                  class="mt-0.5 grid h-9 w-9 shrink-0 place-items-center rounded-full bg-white text-[13px] font-bold text-[var(--color-brand-600)] ring-1 ring-[var(--color-line)]"
                  aria-hidden="true"
                >
                  <!--
                    内联 SVG 而不是 UIcon：图标集合没被打进生产包时，UIcon 会去
                    api.iconify.design 拉图标，被本站 CSP（connect-src 'self'）拦掉，
                    头像位置就空一块。静态路径永远不依赖网络。
                  -->
                  <svg
                    v-if="review.anonymous || !review.username"
                    viewBox="0 0 24 24"
                    class="h-4 w-4 text-[var(--color-muted)]"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
                    <circle cx="12" cy="7" r="4" />
                  </svg>
                  <template v-else>{{ initialOf(review) }}</template>
                </span>

                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <span class="text-[13px] font-bold text-[var(--color-ink)]">{{ reviewDisplayName(review, t('review.anonymous')) }}</span>
                    <span class="flex items-center gap-0.5" aria-hidden="true">
                      <UIcon
                        v-for="star in 5"
                        :key="`${review.id || index}-drawer-star-${star}`"
                        name="lucide:star"
                        class="h-3 w-3"
                        :class="star <= review.rating ? 'fill-current text-[var(--color-accent-500)]' : 'text-[var(--color-line)]'"
                      />
                    </span>
                    <span class="sr-only">{{ review.rating }} / 5</span>
                    <span v-if="review.createTime" class="tabular text-[12px] text-[var(--color-muted)]">{{ reviewDateText(review.createTime) }}</span>
                  </div>

                  <p v-if="review.skuName" class="mt-1 break-words text-[12px] leading-relaxed text-[var(--color-muted)]">{{ review.skuName }}</p>
                  <p
                    v-if="review.content"
                    class="mt-1.5 whitespace-pre-line break-words text-[14px] leading-relaxed text-[var(--color-ink-soft)]"
                  >{{ review.content }}</p>

                  <!-- 买家秀：滚动列表里不放大图，点了才开灯箱（否则一屏图会把滚动续页打断） -->
                  <div v-if="review.images?.length" class="mt-2.5">
                    <ReviewPhotos
                      :images="review.images"
                      :title="productTitle"
                      variant="drawer"
                      @open="photoLightboxOpen = true"
                      @close="photoLightboxOpen = false"
                    />
                  </div>
                </div>
              </div>
            </li>
          </ul>

          <!-- 续页哨兵：进入预取余量（rootMargin 600px）就自动拉下一页 -->
          <div ref="sentinelEl" class="h-px w-full" aria-hidden="true"></div>

          <!-- 加载中：骨架占位（高度接近真实一条，接上内容时列表不跳动） -->
          <div v-if="loadingMore" class="space-y-4 pt-3" aria-hidden="true">
            <div v-for="row in 2" :key="`drawer-skeleton-${row}`" class="flex gap-3">
              <span class="skeleton h-9 w-9 shrink-0 rounded-full"></span>
              <div class="min-w-0 flex-1 space-y-2">
                <span class="skeleton block h-3 w-1/3 rounded-full"></span>
                <span class="skeleton block h-3 w-full rounded-full"></span>
                <span class="skeleton block h-3 w-2/3 rounded-full"></span>
              </div>
            </div>
          </div>

          <!-- 失败：只留一行提示 + 重试，不打断已经看到的评价 -->
          <p v-if="error" class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px] text-[var(--color-error-600)]" role="alert">
            <UIcon name="lucide:circle-alert" class="h-4 w-4" aria-hidden="true" />
            {{ t('review.loadError') }}
            <button
              type="button"
              class="cursor-pointer rounded-full border border-[var(--color-line)] px-3 py-1 text-[12px] font-bold text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-brand-300)] hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
              @click="retryNow"
            >
              {{ t('common.retry') }}
            </button>
          </p>

          <!-- 到底了：给一个明确的终止信号，避免用户继续往下拖却什么都没有 -->
          <p v-else-if="!hasMore && list.length" class="py-4 text-center text-[12px] text-[var(--color-muted)]">
            {{ t('review.noMore') }}
          </p>

          <!-- 读屏播报：翻页状态只念一次，不逐条朗读 -->
          <p class="sr-only" aria-live="polite">
            {{ loadingMore ? t('review.loadingMore') : '' }}
          </p>
        </div>
      </div>
    </template>
  </USlideover>
</template>

<script setup lang="ts">
import type { StoreReview, StoreReviewSummary } from '~~/types/shop-review'
import {
  reviewBucketPercent,
  reviewDateText,
  reviewDisplayName,
  reviewStars,
} from '~~/types/shop-review'
import ReviewPhotos from './ReviewPhotos.vue'

const props = withDefaults(defineProps<{
  /** 评价汇总（null = 还没有数据）：只画分数与分布，不决定抽屉是否可开 */
  summary?: StoreReviewSummary | null
  /** 已加载的评价（第一页 + 抽屉里续页追加的页）；抽屉直接渲染全部已加载项 */
  reviews?: StoreReview[]
  /** 真实评价总条数（0 时不渲染入口，见 DetailSections） */
  total?: number
  /** 还有下一页 → 滚到底部会自动续页 */
  hasMore?: boolean
  /** 正在追加下一页：底部换成骨架屏 */
  loadingMore?: boolean
  /** 评价请求失败的可读文案；非空时显示重试入口，并暂停自动续页 */
  error?: string
  /** 商品标题：买家秀灯箱里图片 alt 的兜底 */
  productTitle?: string
}>(), {
  summary: null,
  reviews: () => [],
  total: 0,
  hasMore: false,
  loadingMore: false,
  error: '',
  productTitle: '',
})

/** 抽屉开关由调用方掌握（v-model:open），组件内部只管"打开后怎么滚" */
const open = defineModel<boolean>('open', { default: false })

/** 取数不在这里：只把"该续页了"抛给页面（与 DetailSections 的 load-more 同一条链路） */
const emit = defineEmits<{ 'load-more': [] }>()

const { t } = useI18n()

// ===== 展示数据 =====
const list = computed(() => props.reviews ?? [])
const totalCount = computed(() => props.total ?? 0)
const ratingAvgText = computed(() => (props.summary ? props.summary.ratingAvg.toFixed(1) : ''))
const summaryStars = computed(() => reviewStars(props.summary?.ratingAvg ?? null))
const distributionRows = computed(() => {
  const total = props.summary?.reviewCount ?? 0
  return (props.summary?.distribution ?? []).map(bucket => ({
    rating: bucket.rating,
    count: bucket.count,
    percent: reviewBucketPercent(bucket.count, total),
  }))
})

/** 昵称首字符：只做头像里的装饰字符（读屏读的是旁边完整的昵称文本） */
function initialOf(review: StoreReview): string {
  const name = review.username?.trim() ?? ''
  return name ? name.slice(0, 1).toUpperCase() : ''
}

// ===== 买家秀灯箱 =====
/**
 * 灯箱开着时把抽屉设成**不可关闭**（:dismissible）。
 *
 * 灯箱自己是一个 Teleport 到 body 的浮层，Esc 会同时被它和抽屉（reka-ui 的 Dialog）收到：
 * 不拦住的话"关图片"会顺带把整个评价抽屉一起关掉，用户刚滑到的位置全丢。
 * dismissible=false 时 reka 会 preventDefault 掉 Esc 与遮罩点击，只留灯箱自己处理。
 */
const photoLightboxOpen = ref(false)

// ===== 滚动续页（用户无感加载） =====
/** 距底部多少像素就开始拉下一页：一屏左右，用户滚到底时新内容已经渲染好 */
const PREFETCH_PX = 600

const scrollEl = ref<HTMLElement | null>(null)
const sentinelEl = ref<HTMLElement | null>(null)

let observer: IntersectionObserver | null = null
/** 已发出请求但父层还没回状态：防抖用，避免同一帧里连发多次 */
let inflight = false
let inflightTimer: ReturnType<typeof setTimeout> | null = null
/** 上一页请求发出时的条数：用来判断"请求回来了但没新数据"，避免无限重试 */
let countAtRequest = 0
/** 服务端返回了空页（hasMore 与数据不一致）→ 暂停自动续页，只留手动重试 */
let stalled = false

/** 距滚动区底部还有多少像素（没有滚动区时视为"很远"，不触发续页） */
function distanceToBottom(): number {
  const el = scrollEl.value
  if (!el) return Number.POSITIVE_INFINITY
  return el.scrollHeight - el.scrollTop - el.clientHeight
}

function releaseInflight() {
  inflight = false
  if (inflightTimer) {
    clearTimeout(inflightTimer)
    inflightTimer = null
  }
}

/**
 * 请求下一页。
 * `force` 供"重试"按钮使用：失败后仍允许用户手动再试一次，
 * 而自动续页遇到 error / stalled 时保持安静（不反复打后端）。
 */
function requestMore(force = false) {
  if (!open.value || !props.hasMore || props.loadingMore || inflight) return
  if (!force && (props.error || stalled)) return
  if (force) stalled = false
  inflight = true
  countAtRequest = list.value.length
  emit('load-more')
  /*
   * 兜底解锁：父层若因为并发请求/守卫（例如同时有一次刷新）忽略了这次 load-more，
   * inflight 会永久为 true，之后用户滚到底再也不会续页 —— 这里 10s 后强行放开。
   */
  if (inflightTimer) clearTimeout(inflightTimer)
  inflightTimer = setTimeout(() => { inflight = false }, 10000)
}

function retryNow() {
  requestMore(true)
}

/** 打开抽屉 / 滚动到预取余量时调用：距底足够近就续页 */
function prefetchIfNeeded() {
  if (distanceToBottom() < PREFETCH_PX) requestMore()
}

function connectObserver() {
  disconnectObserver()
  if (!import.meta.client || typeof IntersectionObserver === 'undefined') return
  const root = scrollEl.value
  const target = sentinelEl.value
  if (!root || !target) return
  observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        requestMore()
        return
      }
    }
  }, {
    root,
    // 底部预留一屏：哨兵还没进视野就开始取数，滚到底时内容已经在了
    rootMargin: `0px 0px ${PREFETCH_PX}px 0px`,
    threshold: 0,
  })
  observer.observe(target)
}

function disconnectObserver() {
  observer?.disconnect()
  observer = null
}

/*
 * 打开/关闭：抽屉内容由 USlideover 按需挂载，ref 只有在打开后才存在，
 * 所以观察器必须在 nextTick（DOM 就位）之后再连。
 */
watch(open, async (isOpen) => {
  if (isOpen) {
    stalled = false
    await nextTick()
    connectObserver()
    prefetchIfNeeded()
  } else {
    disconnectObserver()
    releaseInflight()
    // 抽屉被关掉时，灯箱状态也归零：下次打开不该残留"不可关闭"的抽屉
    photoLightboxOpen.value = false
  }
})

/*
 * 每次续页结束（loadingMore → false）都重新量一次距底距离：
 *   · 新数据接上后哨兵可能仍在预取余量内（一页不满一屏），要再拉一次；
 *   · 若这次没有拿到新数据（条数没变），标记 stalled，避免空转请求；
 *   · 若这次是失败（error 非空），requestMore 自己会拒绝，不会打扰后端。
 */
watch(() => props.loadingMore, (loading) => {
  if (loading) return
  releaseInflight()
  nextTick(() => {
    if (list.value.length > countAtRequest) {
      stalled = false
      prefetchIfNeeded()
    } else if (props.hasMore && !props.error) {
      // 请求回来了但没有新增：后端页码/条数可能不一致，停掉自动续页
      stalled = true
    }
  })
})

onBeforeUnmount(() => {
  disconnectObserver()
  releaseInflight()
})
</script>
