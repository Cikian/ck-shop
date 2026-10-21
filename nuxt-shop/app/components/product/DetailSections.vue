<!--
  商品详情左列下半部：左列内吸顶锚点导航 + 单列三区块
  （规格参数 → 用户评价 → 商品详情；「为你推荐」自 2026-10 第 2 轮起移到页面 sticky zone 之后全宽渲染）

  从 pages/products/[slug].vue 拆出（商品详情页拆组件第二步）。
  选它一起拆的原因：锚点导航与四个区块是**同一个滚动整体** ——
  锚点要高亮哪个模块、点一下滚到哪，都依赖这四块的 DOM 引用与"是否渲染"。
  把它们放在同一个组件里，滚动逻辑就不必把 DOM 引用传回页面再传下来。

  布局契约（用户明确要求）：下半部恒为**单列竖向堆叠**，绝不并排多栏。

  区块数据来源（契约 4.2）：
    规格参数 = 新字段 params（KV）优先，为空时回退历史自动行 specRows；
    用户评价 = 真实汇总（0 值不渲染）；
    商品详情 = description（简介，清洗后）+ detailBlocks（模块渲染），都空显示「暂无商品描述」；
    类似推荐 = 同分类真实商品。
-->
<template>
  <div>
    <!--
      ===================== 左列内吸顶锚点导航 =====================
      2026-10 布局改版：锚点从"页面通栏"改为**只在左列内吸顶**（宽度 = 左列宽度）。
      本组件整个渲染在页面左列里，因此这里不再需要负 margin 抵消页面容器内边距：
      负 margin 会把导航捅出左列，既会超过左列宽度，也可能撑出横向滚动条。

      top 用实测的 Header 高度（Header 是 fixed，移动端导航两行更高），
      SSR 首帧回退到 --nav-total-height，避免首屏被 Header 盖住。
    -->
    <nav
      ref="anchorBar"
      data-anchor-nav
      class="sticky z-30 bg-[var(--color-cream)] pb-3 pt-6"
      :style="{ top: stickyTop }"
    >
      <div class="no-scrollbar flex items-center gap-2 overflow-x-auto rounded-full bg-white px-2 py-1.5 shadow-[var(--shadow-card)]">
        <button
          v-for="item in anchorItems"
          :key="item.id"
          type="button"
          class="chip shrink-0"
          :class="currentAnchor === item.id ? 'chip-active' : ''"
          :aria-current="currentAnchor === item.id ? 'true' : undefined"
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
      <!-- 1. 规格参数（契约 4.2）：params 非空 → KV；为空且历史自动行存在 → 旧渲染 -->
      <section v-if="hasSpecSection" ref="specSection" class="border-t border-[var(--color-line-soft)] py-10">
        <h2 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
          <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
          {{ t('shop.anchorSpec') }}
        </h2>

        <!-- 新字段：管理端自由录入的 Key-Value；两列自适应、长值换行、移动端不溢出 -->
        <dl
          v-if="paramRows.length"
          class="mt-5 divide-y divide-[var(--color-line-soft)] overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)] px-4 sm:px-5"
        >
          <div
            v-for="(row, index) in paramRows"
            :key="`${row.name}-${index}`"
            class="flex flex-wrap gap-x-4 gap-y-1 py-3.5"
          >
            <dt class="w-24 shrink-0 break-words text-[13px] text-[var(--color-muted)]">{{ row.name }}</dt>
            <dd class="min-w-0 flex-1 break-words text-[13px] font-medium text-[var(--color-ink)] [overflow-wrap:anywhere]">{{ row.value }}</dd>
          </div>
        </dl>

        <!-- 老商品兼容：后端没有参数表，只用真实字段拼；没有一行数据则整块不渲染 -->
        <dl
          v-else
          class="mt-5 divide-y divide-[var(--color-line-soft)] overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)] px-4 sm:px-5"
        >
          <div v-for="row in specRows" :key="row.labelKey" class="flex flex-wrap gap-x-4 gap-y-1 py-3.5">
            <dt class="w-24 shrink-0 text-[13px] text-[var(--color-muted)]">{{ t(row.labelKey) }}</dt>
            <dd class="min-w-0 flex-1 break-words text-[13px] font-medium text-[var(--color-ink)]">{{ row.value }}</dd>
          </div>
        </dl>
      </section>

      <!-- 2. 用户评价：后端没有评价接口，只展示真实汇总（评分 + 评价数）；无数据整块不渲染 -->
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

      <!-- 3. 商品详情（契约 4.2）：顶部简介 description（清洗后），下方结构化模块 -->
      <section ref="detailSection" class="border-t border-[var(--color-line-soft)] py-10">
        <h2 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
          <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
          {{ t('shop.anchorDetail') }}
        </h2>

        <!-- description 是管理端录入的富文本（契约：商品简介）→ 渲染前必须清洗 -->
        <div
          v-if="hasDescription"
          class="mt-5 text-[15px] leading-relaxed text-[var(--color-ink-soft)] [&_a]:text-[var(--color-brand-600)] [&_a]:underline-offset-2 [&_blockquote]:border-l-2 [&_blockquote]:border-[var(--color-brand-300)] [&_blockquote]:pl-3 [&_blockquote]:text-[var(--color-muted)] [&_h2]:mt-5 [&_h2]:text-base [&_h2]:font-bold [&_h3]:mt-5 [&_h3]:font-bold [&_h4]:mt-4 [&_h4]:font-bold [&_hr]:my-4 [&_hr]:border-[var(--color-line-soft)] [&_img]:my-4 [&_img]:h-auto [&_img]:w-full [&_li]:ml-5 [&_li]:list-disc [&_ol]:list-decimal [&_p]:mb-4 [&_table]:my-4 [&_table]:block [&_table]:w-full [&_table]:overflow-x-auto [&_td]:border [&_td]:border-[var(--color-line)] [&_td]:px-2 [&_td]:py-1 [&_th]:border [&_th]:border-[var(--color-line)] [&_th]:px-2 [&_th]:py-1"
          v-html="sanitizedDescription"
        ></div>

        <!-- 结构化详情模块：8 种 blockType 的渲染在 ProductDetailBlocks 内。
             ⚠️ 这里**不能**给组件加 mt-5 / space-y 之类的统一间距（契约 1.4 / 4.3）：
             模块间距全部由每个模块自己的内联 margin 决定（默认 0），否则长图切割块会露出缝。 -->
        <ProductDetailBlocks
          v-if="blocks.length"
          :blocks="blocks"
          :title="productTitle"
        />

        <!-- 无模块且无简介：显示空态（老商品空 description 的既有表现） -->
        <p v-if="!hasDescription && !blocks.length" class="mt-5 text-sm text-[var(--color-muted)]">
          {{ t('product.noDescription') }}
        </p>
      </section>
      <!--
        注意：第 4 块「为你推荐」自 2026-10 第 2 轮起**不再在左列里渲染**，
        而是由页面在 sticky zone 之后渲染成横跨页面容器的全宽区块（桌面 6 列）。
        本组件只负责在它有数据时生成锚点，并把点击/高亮目标解析到页面里的 #product-recommend。
      -->
    </div>
  </div>
</template>

<script setup lang="ts">
import type {
  StoreDetailBlock,
  StoreProductParam,
} from '~~/types/shop-product'
import { sanitizeHtml } from '~~/utils/sanitize-html'
import ProductDetailBlocks from './ProductDetailBlocks.vue'

const props = withDefaults(defineProps<{
  /** 商品简介 HTML（管理端录入）；空则不渲染正文 */
  description?: string | null
  /** 结构化详情模块（新接口字段 detailBlocks，已按 sortOrder 排好） */
  detailBlocks?: StoreDetailBlock[]
  /** 规格参数 KV（新接口字段 params；为空时回退旧自动行） */
  params?: StoreProductParam[]
  /** 商品标题：详情模块图片的 alt 兜底 */
  productTitle?: string
  /** 规格参数行（后端真实字段拼出，可能为空 → 整块不渲染） */
  specRows: { labelKey: string; value: string }[]
  /** 是否有评价数据（评分或评价数）→ 决定区块与锚点是否出现 */
  hasReview: boolean
  rating: number | null
  ratingText: string
  ratingStars: number
  reviewCount: number
  /** 是否有推荐商品（决定是否生成「为你推荐」锚点；区块本身在页面里全宽渲染） */
  hasRelated: boolean
}>(), {
  description: null,
  detailBlocks: () => [],
  params: () => [],
  productTitle: '',
})

const { t } = useI18n()

// ===== 区块数据 =====
const blocks = computed(() => props.detailBlocks ?? [])
const paramRows = computed(() => props.params ?? [])
/** 规格参数区块是否有内容：新 KV 优先，历史自动行兜底；两者都空时整块不渲染（老商品不变） */
const hasSpecSection = computed(() => paramRows.value.length > 0 || props.specRows.length > 0)
/** 简介与富文本同一条清洗管线（契约第五节：渲染端再过一遍白名单） */
const sanitizedDescription = computed(() => sanitizeHtml(props.description))
const hasDescription = computed(() => sanitizedDescription.value.length > 0)

// ===== 吸顶锚点导航 =====
// 顺序与模板一致：规格参数 → 用户评价 → 商品详情 → 类似推荐（契约 4.1）
type AnchorId = 'spec' | 'review' | 'detail' | 'recommend'

const anchorBar = ref<HTMLElement | null>(null)
const specSection = ref<HTMLElement | null>(null)
const reviewSection = ref<HTMLElement | null>(null)
const detailSection = ref<HTMLElement | null>(null)

/** null = 还没滚动过；渲染时回退到第一个可用锚点，SSR 首帧也不会高亮错位置 */
const activeAnchor = ref<AnchorId | null>(null)
/** 吸顶位置：SSR 先用设计 token（导航总高，移动端两行 128px / 桌面 116px），挂载后用实测的 Header 高度修正（Header 是 fixed） */
const stickyTop = ref('var(--nav-total-height)')

/**
 * 只给「真的渲染出来的模块」生成锚点：
 * 规格参数（无 params 且无历史行）/ 用户评价 在没数据时整块不渲染，
 * 此时不应该出现点了没反应的死锚点。
 * 「为你推荐」区块在页面里（sticky zone 之后的全宽区），这里只判断有没有数据，
 * 真实 DOM 在点击/高亮时按 id 解析（见 resolveAnchorEl）。
 */
const anchorItems = computed<{ id: AnchorId; labelKey: string; el: HTMLElement | null }[]>(() => {
  const items: { id: AnchorId; labelKey: string; el: HTMLElement | null }[] = []
  if (hasSpecSection.value) items.push({ id: 'spec', labelKey: 'shop.anchorSpec', el: specSection.value })
  if (props.hasReview) items.push({ id: 'review', labelKey: 'shop.anchorReview', el: reviewSection.value })
  items.push({ id: 'detail', labelKey: 'shop.anchorDetail', el: detailSection.value })
  if (props.hasRelated) items.push({ id: 'recommend', labelKey: 'shop.anchorRecommend', el: null })
  return items
})

/** 推荐区在页面（不在本组件内）：按 id 解析；其余锚点用组件内 ref */
function resolveAnchorEl(item: { id: AnchorId; el: HTMLElement | null }): HTMLElement | null {
  if (item.el) return item.el
  if (item.id !== 'recommend') return null
  return document.getElementById('product-recommend')
}

/** 当前高亮：优先用户滚动/点击得到的值；首帧回退到第一个可用锚点 */
const currentAnchor = computed<AnchorId | null>(() => activeAnchor.value ?? anchorItems.value[0]?.id ?? null)

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
  const items = anchorItems.value
  if (!items.length) return
  const probe = scrollOffset() + 24
  let current: AnchorId = items[0]!.id
  for (const item of items) {
    const el = resolveAnchorEl(item)
    if (!el) continue
    if (el.getBoundingClientRect().top - probe <= 0) current = item.id
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
  if (!item) return
  const el = resolveAnchorEl(item)
  if (!el) return
  const top = el.getBoundingClientRect().top + window.scrollY - scrollOffset() - 16
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
