<!--
  商品详情下半部：通栏吸顶锚点导航 + 单列四区块（商品详情 → 规格参数 → 用户评价 → 为你推荐）

  从 pages/products/[slug].vue 拆出（商品详情页拆组件第二步）。
  选它一起拆的原因：锚点导航与四个区块是**同一个滚动整体** ——
  锚点要高亮哪个模块、点一下滚到哪，都依赖这四块的 DOM 引用与"是否渲染"。
  把它们放在同一个组件里，滚动逻辑就不必把 DOM 引用传回页面再传下来。

  布局契约（用户明确要求）：下半部恒为**单列竖向堆叠**，绝不并排多栏。
-->
<template>
  <div>
    <!--
      ===================== 通栏吸顶锚点导航 =====================
      top 用实测的 Header 高度（Header 是 fixed，导航是两行，比单行 token 高），
      SSR 首帧回退到 --nav-total-height（导航总高），这样桌面/移动端都不会被导航盖住。

      这里的负 margin 是为了"在容器内做通栏"：把容器的横向内边距抵消掉，
      再自己补一份，使吸顶条的背景铺满内容区宽度。
      ⚠️ 必须与容器内边距**成对**：容器是 px-2/sm:px-3/lg:px-4，
         这里就只能是 -mx-2/sm:-mx-3/lg:-mx-4 —— 负 margin 比容器内边距大
         会把元素捅出容器边界，直接撑出横向滚动条。 -->
    <nav
      ref="anchorBar"
      class="sticky z-30 -mx-2 bg-[var(--color-cream)] px-2 pb-3 pt-6 sm:-mx-3 sm:px-3 lg:-mx-4 lg:px-4"
      :style="{ top: stickyTop }"
    >
      <div class="no-scrollbar flex items-center gap-2 overflow-x-auto rounded-full bg-white px-2 py-1.5 shadow-[var(--shadow-card)]">
        <button
          v-for="item in anchorItems"
          :key="item.id"
          type="button"
          class="chip shrink-0"
          :class="activeAnchor === item.id ? 'chip-active' : ''"
          :aria-current="activeAnchor === item.id ? 'true' : undefined"
          @click="scrollToAnchor(item.id)"
        >
          {{ t(item.labelKey) }}
        </button>
      </div>
    </nav>

    <!-- ===================== 下半部：单列竖向堆叠 =====================
         用户明确要求：详情下方必须竖向滚动，绝不能并排多栏。
         因此这里恒为单列（没有任何 lg:grid-cols-*），
         模块之间用极浅分隔线 + 大间距区隔。 -->
    <div class="pb-16">
      <!-- 1. 商品详情 -->
      <section ref="detailSection" class="border-t border-[var(--color-line-soft)] py-10">
        <h2 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
          <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
          {{ t('shop.anchorDetail') }}
        </h2>
        <!-- description 是管理端录入的富文本（后端契约：富文本 HTML） -->
        <div
          v-if="description"
          class="mt-5 text-[15px] leading-relaxed text-[var(--color-ink-soft)] [&_a]:text-[var(--color-brand-600)] [&_h2]:mt-5 [&_h2]:text-base [&_h2]:font-bold [&_h3]:mt-5 [&_h3]:font-bold [&_img]:my-4 [&_img]:h-auto [&_img]:w-full [&_img]:rounded-[var(--radius-tile)] [&_li]:ml-5 [&_li]:list-disc [&_p]:mb-4"
          v-html="description"
        ></div>
        <p v-else class="mt-5 text-sm text-[var(--color-muted)]">{{ t('product.noDescription') }}</p>
      </section>

      <!-- 2. 规格参数：后端没有参数表，只用真实字段拼；没有一行数据则整块不渲染 -->
      <section v-if="specRows.length" ref="specSection" class="border-t border-[var(--color-line-soft)] py-10">
        <h2 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
          <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
          {{ t('shop.anchorSpec') }}
        </h2>
        <dl class="mt-5 divide-y divide-[var(--color-line-soft)] overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)] px-5">
          <div v-for="row in specRows" :key="row.labelKey" class="flex flex-wrap gap-x-4 gap-y-1 py-3.5">
            <dt class="w-24 shrink-0 text-[13px] text-[var(--color-muted)]">{{ t(row.labelKey) }}</dt>
            <dd class="min-w-0 flex-1 text-[13px] font-medium text-[var(--color-ink)]">{{ row.value }}</dd>
          </div>
        </dl>
      </section>

      <!-- 3. 用户评价：后端没有评价接口，只展示真实汇总（评分 + 评价数）；无数据整块不渲染 -->
      <section v-if="hasReview" ref="reviewSection" class="border-t border-[var(--color-line-soft)] py-10">
        <h2 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
          <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
          {{ t('shop.anchorReview') }}
        </h2>
        <div class="mt-5 flex flex-wrap items-center gap-x-6 gap-y-4">
          <div v-if="rating !== null" class="flex items-baseline gap-1">
            <span class="text-4xl font-black leading-none tabular text-[var(--color-brand-600)]">{{ ratingText }}</span>
          </div>
          <div class="space-y-2">
            <div v-if="rating !== null" class="flex items-center gap-0.5" aria-hidden="true">
              <UIcon
                v-for="star in 5"
                :key="`star-${star}`"
                name="lucide:star"
                class="h-4 w-4"
                :class="star <= ratingStars ? 'fill-current text-[var(--color-accent-500)]' : 'text-[var(--color-line)]'"
              />
            </div>
            <p v-if="reviewCount > 0" class="text-[13px] text-[var(--color-muted)]">
              {{ t('shop.reviewCount', { count: reviewCount }) }}
            </p>
          </div>
        </div>
      </section>

      <!-- 4. 为你推荐：真实同分类商品（排除当前商品），最多 4 张；无数据整块不渲染 -->
      <section v-if="related.length" ref="recommendSection" class="border-t border-[var(--color-line-soft)] py-10">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
            <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
            {{ t('shop.anchorRecommend') }}
          </h2>
          <NuxtLinkLocale
            to="/products"
            class="inline-flex items-center gap-1 text-[13px] font-bold text-[var(--color-brand-600)] transition-colors hover:text-[var(--color-brand-700)]"
          >
            {{ t('common.viewMore') }}
            <UIcon name="lucide:arrow-right" class="h-3.5 w-3.5" aria-hidden="true" />
          </NuxtLinkLocale>
        </div>
        <div class="mt-5 grid grid-cols-2 gap-4 sm:gap-5 lg:grid-cols-4 lg:gap-6">
          <StoreProductCard
            v-for="item in related"
            :key="item.id"
            :product="item"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { StoreProductCard as StoreProductCardType } from '~~/types/shop-product'

const props = defineProps<{
  /** 富文本 HTML（管理端录入）；空则不渲染正文 */
  description?: string | null
  /** 规格参数行（后端真实字段拼出，可能为空 → 整块不渲染） */
  specRows: { labelKey: string; value: string }[]
  /** 是否有评价数据（评分或评价数）→ 决定区块与锚点是否出现 */
  hasReview: boolean
  rating: number | null
  ratingText: string
  ratingStars: number
  reviewCount: number
  /** 推荐商品（同分类、已排除当前商品，最多 4 条） */
  related: StoreProductCardType[]
}>()

const { t } = useI18n()

// ===== 吸顶锚点导航 =====
type AnchorId = 'detail' | 'spec' | 'review' | 'recommend'

const anchorBar = ref<HTMLElement | null>(null)
const detailSection = ref<HTMLElement | null>(null)
const specSection = ref<HTMLElement | null>(null)
const reviewSection = ref<HTMLElement | null>(null)
const recommendSection = ref<HTMLElement | null>(null)

const activeAnchor = ref<AnchorId>('detail')
/** 吸顶位置：SSR 先用设计 token（导航总高，移动端两行 128px / 桌面 116px），挂载后用实测的 Header 高度修正（Header 是 fixed） */
const stickyTop = ref('var(--nav-total-height)')

/**
 * 只给「真的渲染出来的模块」生成锚点：
 * 规格参数 / 用户评价 / 为你推荐 在没数据时整块不渲染，
 * 此时不应该出现点了没反应的死锚点。
 */
const anchorItems = computed<{ id: AnchorId; labelKey: string; el: HTMLElement | null }[]>(() => {
  const items: { id: AnchorId; labelKey: string; el: HTMLElement | null }[] = [
    { id: 'detail', labelKey: 'shop.anchorDetail', el: detailSection.value },
  ]
  if (props.specRows.length) items.push({ id: 'spec', labelKey: 'shop.anchorSpec', el: specSection.value })
  if (props.hasReview) items.push({ id: 'review', labelKey: 'shop.anchorReview', el: reviewSection.value })
  if (props.related.length) items.push({ id: 'recommend', labelKey: 'shop.anchorRecommend', el: recommendSection.value })
  return items
})

/** Header 是 fixed 的全局导航，高度随时可能变（移动端多一行搜索框），实测最稳 */
function headerHeight(): number {
  const header = document.querySelector('header')
  return header instanceof HTMLElement ? header.getBoundingClientRect().height : 0
}

/** 锚点滚动/高亮都要避开 fixed Header + 吸顶锚点栏本身 */
function scrollOffset(): number {
  return headerHeight() + (anchorBar.value?.getBoundingClientRect().height ?? 0)
}

let ticking = false
let rafId = 0

function syncStickyTop() {
  const height = headerHeight()
  if (height > 0) stickyTop.value = `${Math.round(height)}px`
}

/** 当前高亮：取最后一个「顶部已越过 Header + 锚点栏」的模块 */
function updateActiveAnchor() {
  const probe = scrollOffset() + 24
  let current: AnchorId = 'detail'
  for (const item of anchorItems.value) {
    if (!item.el) continue
    if (item.el.getBoundingClientRect().top - probe <= 0) current = item.id
  }
  activeAnchor.value = current
}

function onScroll() {
  if (ticking) return
  ticking = true
  rafId = requestAnimationFrame(() => {
    ticking = false
    updateActiveAnchor()
  })
}

function onResize() {
  syncStickyTop()
  updateActiveAnchor()
}

function scrollToAnchor(id: AnchorId) {
  if (!import.meta.client) return
  const item = anchorItems.value.find(entry => entry.id === id)
  if (!item?.el) return
  const top = item.el.getBoundingClientRect().top + window.scrollY - scrollOffset() - 16
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: Math.max(top, 0), behavior: reduced ? 'auto' : 'smooth' })
  // 立即高亮，不等平滑滚动结束（否则中间态会闪回上一个模块）
  activeAnchor.value = id
}

onMounted(() => {
  syncStickyTop()
  updateActiveAnchor()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  // 只清理浏览器侧监听：SSR 不执行 unmount，但加个判断更保险
  if (!import.meta.client) return
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onResize)
  if (rafId) cancelAnimationFrame(rafId)
  ticking = false
})
</script>
