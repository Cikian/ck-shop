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
    用户评价 = 真实汇总（分数 + 星级分布）+ 正文预览 3 条 + 左侧抽屉浏览全部（滚动续页），
               条数为 0 时整块不渲染；
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

      <!--
        2. 用户评价（真实数据：汇总 + 星级分布 + 预览列表 + 全量抽屉）

        「0 值不渲染」：是否出现整块（以及锚点里的「用户评价」）只由 reviewTotal 决定 ——
        reviewTotal === 0 时这块彻底不渲染，绝不会出现"0 分 / 0 条评价"的空壳。

        布局：汇总卡（左）+ 星级分布（右）在桌面并排、移动端上下堆叠；
        下方只预览 2~3 条评价，更多评价通过「查看全部评价」在**左侧抽屉**里滚动浏览
        （抽屉内滚动到底自动续页，见 ProductReviewDrawer）。
        锚点 id 仍为 review（页面内 #review 直达），滚动/高亮仍由上面的 ref 驱动。
      -->
      <section
        v-if="hasReviewSection"
        id="review"
        ref="reviewSection"
        class="border-t border-[var(--color-line-soft)] py-10"
      >
        <h2 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
          <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
          {{ t('shop.anchorReview') }}
        </h2>

        <!-- 加载失败只留一行提示：不弹窗、不阻塞下方的商品详情等区块 -->
        <p v-if="reviewError" class="mt-3 inline-flex items-center gap-1.5 text-[13px] text-[var(--color-error-600)]">
          <UIcon name="lucide:circle-alert" class="h-4 w-4" aria-hidden="true" />
          {{ t('review.loadError') }}
        </p>

        <!-- 汇总 + 星级分布 -->
        <div
          v-if="reviewSummary"
          class="mt-5 flex flex-col gap-5 rounded-2xl bg-[var(--color-surface-secondary)] p-4 sm:p-5 lg:flex-row lg:items-center lg:gap-10"
        >
          <!-- 左：大分数 + 实心/空心星 + 条数（条数与列表同源，都来自 summary） -->
          <div class="flex shrink-0 flex-wrap items-center gap-x-4 gap-y-2 lg:w-40 lg:flex-col lg:items-start lg:gap-y-1.5">
            <div class="flex items-baseline gap-1">
              <span class="tabular text-4xl font-black leading-none text-[var(--color-brand-600)]">{{ ratingAvgText }}</span>
              <span class="text-[13px] text-[var(--color-muted)]">/ 5</span>
            </div>
            <div class="space-y-1.5">
              <div class="flex items-center gap-0.5" aria-hidden="true">
                <UIcon
                  v-for="star in 5"
                  :key="`summary-star-${star}`"
                  name="lucide:star"
                  class="h-4 w-4"
                  :class="star <= summaryStars ? 'fill-current text-[var(--color-accent-500)]' : 'text-[var(--color-line)]'"
                />
              </div>
              <p class="text-[13px] text-[var(--color-muted)]">
                {{ t('shop.reviewCount', { count: reviewTotalCount }) }}
              </p>
            </div>
          </div>

          <!-- 右：5→1 五行分布，进度条宽度按占比（reviewBucketPercent，总数为 0 时是 0 而不是 NaN） -->
          <div class="min-w-0 flex-1">
            <p class="text-[13px] font-bold text-[var(--color-ink)]">{{ t('review.distributionTitle') }}</p>
            <div class="mt-2 space-y-1.5">
              <div
                v-for="row in distributionRows"
                :key="`distribution-${row.rating}`"
                class="flex items-center gap-2.5"
              >
                <span class="inline-flex w-8 shrink-0 items-center justify-end gap-0.5 text-[13px] tabular text-[var(--color-muted)]">
                  {{ row.rating }}
                  <UIcon name="lucide:star" class="h-3 w-3 fill-current text-[var(--color-accent-500)]" aria-hidden="true" />
                </span>
                <span class="h-2 min-w-0 flex-1 overflow-hidden rounded-full bg-[var(--color-line)]">
                  <span
                    class="block h-full rounded-full bg-[var(--color-accent-500)]"
                    :style="{ width: `${row.percent}%` }"
                  ></span>
                </span>
                <span class="w-9 shrink-0 text-right text-[13px] tabular text-[var(--color-muted)]">{{ row.count }}</span>
              </div>
            </div>
          </div>
        </div>

        <!--
          评价列表：正文只预览**前 PREVIEW_REVIEW_COUNT 条**（用户 2026-10 本轮要求：2~3 条即可），
          更多评价点「查看全部评价」进左侧抽屉滚动浏览（见 ProductReviewDrawer）。

          每条：昵称（匿名走 i18n）/ 星级 / 日期 / 规格（空则整行不渲染）/ 内容（空则只剩星级）。
          预览里的正文做 4 行截断：详情页不该被三条长评撑开，
          完整内容在抽屉里逐条完整展示。
        -->
        <ul v-if="previewReviews.length" class="mt-5 divide-y divide-[var(--color-line-soft)]">
          <li
            v-for="(review, index) in previewReviews"
            :key="`${review.id || 'review'}-${index}`"
            class="py-5 first:pt-1 last:pb-0"
          >
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1.5">
              <span class="text-[13px] font-bold text-[var(--color-ink)]">{{ reviewDisplayName(review, t('review.anonymous')) }}</span>
              <span class="flex items-center gap-0.5" aria-hidden="true">
                <UIcon
                  v-for="star in 5"
                  :key="`${review.id || index}-star-${star}`"
                  name="lucide:star"
                  class="h-3.5 w-3.5"
                  :class="star <= review.rating ? 'fill-current text-[var(--color-accent-500)]' : 'text-[var(--color-line)]'"
                />
              </span>
              <span class="sr-only">{{ review.rating }} / 5</span>
              <span v-if="review.createTime" class="tabular text-[13px] text-[var(--color-muted)]">{{ reviewDateText(review.createTime) }}</span>
            </div>
            <p v-if="review.skuName" class="mt-1 break-words text-[13px] text-[var(--color-muted)]">{{ review.skuName }}</p>
            <p
              v-if="review.content"
              class="mt-2 line-clamp-4 whitespace-pre-line break-words text-[15px] leading-relaxed text-[var(--color-ink-soft)]"
            >{{ review.content }}</p>

            <!-- 买家秀：预览里最多露 3 张（多的折成 +N），点击放大，完整列表在抽屉里 -->
            <div v-if="review.images?.length" class="mt-2.5">
              <ReviewPhotos
                :images="review.images"
                :title="productTitle"
                variant="preview"
                :preview-limit="3"
              />
            </div>
          </li>
        </ul>

        <!--
          查看全部评价：总条数多于预览条数时才出现
          （总数本身就是 1~3 条时，抽屉里不会有新内容，就不该给一个点了没增量收益的入口）。
        -->
        <div v-if="hasMoreThanPreview" class="mt-6 flex justify-center">
          <button
            type="button"
            class="ui-pill ui-pill-ghost h-11 cursor-pointer px-6 text-sm"
            aria-haspopup="dialog"
            :aria-expanded="reviewDrawerOpen ? 'true' : 'false'"
            @click="reviewDrawerOpen = true"
          >
            {{ t('review.viewAll') }}
            <span class="tabular text-[13px] font-bold text-[var(--color-muted)]">· {{ reviewTotalCount }}</span>
            <UIcon name="lucide:chevron-right" class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <!--
          左侧抽屉：抽屉内滚动到底部自动续页（用户无感加载）。
          取数仍由页面负责（本组件只把 load-more 原样转发），
          与旧版「加载更多」按钮走的是同一条 loadMoreReviews 链路。
        -->
        <ProductReviewDrawer
          v-model:open="reviewDrawerOpen"
          :summary="reviewSummary"
          :reviews="reviewList"
          :total="reviewTotalCount"
          :has-more="reviewHasMore"
          :loading-more="reviewLoadingMore"
          :error="reviewError"
          :product-title="productTitle"
          @load-more="emit('load-more')"
        />
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
import type { StoreReview, StoreReviewSummary } from '~~/types/shop-review'
import {
  reviewBucketPercent,
  reviewDateText,
  reviewDisplayName,
  reviewStars,
} from '~~/types/shop-review'
import { sanitizeHtml } from '~~/utils/sanitize-html'
import { registerScrollTask } from '~/utils/scrollScheduler'
import ProductDetailBlocks from './ProductDetailBlocks.vue'
import ProductReviewDrawer from './ProductReviewDrawer.vue'
import ReviewPhotos from './ReviewPhotos.vue'

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
  /** 评价汇总（null = 还没有数据/加载失败）：只负责画分数与分布，不决定区块是否出现 */
  reviewSummary?: StoreReviewSummary | null
  /** 已加载的评价列表（第一页 + 追加的页） */
  reviews?: StoreReview[]
  /**
   * 真实评价总条数 —— **唯一**决定评价区（含锚点「用户评价」）是否出现的依据。
   * 0 条时整块不渲染（项目铁律：0 值不渲染）。
   */
  reviewTotal: number
  /** 还有下一页 → 抽屉滚到底部会继续取（正文不再有「加载更多」按钮） */
  reviewHasMore?: boolean
  /** 正在追加下一页（抽屉底部换骨架屏） */
  reviewLoadingMore?: boolean
  /** 评价请求失败的可读文案；非空时只渲染一行提示，不弹窗、不阻塞其它区块 */
  reviewError?: string
  /** 是否有推荐商品（决定是否生成「为你推荐」锚点；区块本身在页面里全宽渲染） */
  hasRelated: boolean
}>(), {
  description: null,
  detailBlocks: () => [],
  params: () => [],
  productTitle: '',
  reviewSummary: null,
  reviews: () => [],
  reviewHasMore: false,
  reviewLoadingMore: false,
  reviewError: '',
})

/** 「加载更多」只发事件：取数（loadMore）属于页面的职责 */
const emit = defineEmits<{ 'load-more': [] }>()

const { t } = useI18n()

// ===== 区块数据 =====
const blocks = computed(() => props.detailBlocks ?? [])
const paramRows = computed(() => props.params ?? [])
/** 规格参数区块是否有内容：新 KV 优先，历史自动行兜底；两者都空时整块不渲染（老商品不变） */
const hasSpecSection = computed(() => paramRows.value.length > 0 || props.specRows.length > 0)
/** 简介与富文本同一条清洗管线（契约第五节：渲染端再过一遍白名单） */
const sanitizedDescription = computed(() => sanitizeHtml(props.description))
const hasDescription = computed(() => sanitizedDescription.value.length > 0)

// ===== 用户评价（真实数据）=====
/** 只有真实存在评价（条数 > 0）才渲染评价区；0 条 → 区块与锚点都不出现 */
const hasReviewSection = computed(() => props.reviewTotal > 0 && !!props.reviewSummary)
const reviewList = computed(() => props.reviews ?? [])

/**
 * 正文预览条数（用户 2026-10 本轮要求：只展示 2~3 条，多的进抽屉）。
 * 3 条既能让用户看出"这商品确实有人买/有人评"，又不至于把下面的商品详情推到很远。
 */
const PREVIEW_REVIEW_COUNT = 3
/** 正文只画前 3 条；抽屉里画全部已加载项（含抽屉内续页追加的页） */
const previewReviews = computed(() => reviewList.value.slice(0, PREVIEW_REVIEW_COUNT))
/** 总条数多于预览条数 → 才给「查看全部评价」入口（1~3 条时抽屉没有增量内容） */
const hasMoreThanPreview = computed(() => reviewTotalCount.value > PREVIEW_REVIEW_COUNT)
/** 全量评价抽屉的开关：只有正文列表这一个入口，所以状态放在本组件里 */
const reviewDrawerOpen = ref(false)

/** 大分数：summary 已归一化（reviewCount > 0 时才可能是非 0），模板里不再对可空值做运算 */
const ratingAvgText = computed(() => (props.reviewSummary ? props.reviewSummary.ratingAvg.toFixed(1) : ''))
const summaryStars = computed(() => reviewStars(props.reviewSummary?.ratingAvg ?? null))
/** 条数与列表同源：都取 summary.reviewCount，不各算一套 */
const reviewTotalCount = computed(() => props.reviewSummary?.reviewCount ?? 0)
/** 5→1 五行分布 + 进度条宽度；总数为 0 时百分比是 0（reviewBucketPercent 已挡 NaN） */
const distributionRows = computed(() => {
  const total = props.reviewSummary?.reviewCount ?? 0
  return (props.reviewSummary?.distribution ?? []).map(bucket => ({
    rating: bucket.rating,
    count: bucket.count,
    percent: reviewBucketPercent(bucket.count, total),
  }))
})

/*
 * 昵称 / 日期格式化收敛在 types/shop-review.ts（reviewDisplayName / reviewDateText）：
 * 正文预览与抽屉两处渲染同一条评价，格式化必须同源，否则会出现
 * 一边"匿名用户"一边空字符串、一边带时间一边只带日期的不一致。
 */

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
  // 评价锚点只在真的有评价（reviewTotal > 0 且拿到汇总）时出现：点了没反应的死锚点不该存在
  if (hasReviewSection.value) items.push({ id: 'review', labelKey: 'shop.anchorReview', el: reviewSection.value })
  items.push({ id: 'detail', labelKey: 'shop.anchorDetail', el: detailSection.value })
  if (props.hasRelated) items.push({ id: 'recommend', labelKey: 'shop.anchorRecommend', el: null })
  return items
})

/**
 * 推荐区在页面（不在本组件内）：按 id 解析；其余锚点用组件内 ref。
 *
 * 缓存：滚动高亮是**每帧**都要解析的，以前每帧都 getElementById（清单 S23）。
 * 这里按 id 缓存一次，元素被替换（isConnected 为 false）时自动重查。
 */
const anchorElCache = new Map<AnchorId, HTMLElement>()

function resolveAnchorEl(item: { id: AnchorId; el: HTMLElement | null }): HTMLElement | null {
  if (item.el) return item.el
  if (item.id !== 'recommend') return null
  const cached = anchorElCache.get(item.id)
  if (cached && cached.isConnected) return cached
  const el = document.getElementById('product-recommend')
  if (el) anchorElCache.set(item.id, el)
  return el
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

/** 共享滚动调度器的注销函数（清单 S23） */
let stopScrollTask: (() => void) | null = null

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

function onResize() {
  // 尺寸变化后缓存的元素可能已被替换，清空让下一次解析重新查询
  anchorElCache.clear()
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
  /*
   * 滚动交给全站共享调度器（清单 S23）：
   * 和 useStickyAside 共用一个 passive 监听 + 一帧 rAF，不再各建一套。
   */
  stopScrollTask = registerScrollTask(updateActiveAnchor)
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  // 只清理浏览器侧监听：SSR 不执行 unmount，但加个判断更保险
  if (!import.meta.client) return
  stopScrollTask?.()
  window.removeEventListener('resize', onResize)
})
</script>
