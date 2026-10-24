<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar" :style="pageStyle">
    <Transition name="fade" mode="out-in">
    <!-- ===================== 加载态 ===================== -->
    <div v-if="pending" key="skeleton" class="mx-auto max-w-[1600px] px-4 py-10">
      <div class="grid gap-10 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)] lg:gap-x-8">
        <div class="space-y-4">
          <div class="ui-card aspect-square animate-pulse bg-[var(--color-surface-secondary)]"></div>
          <div class="grid grid-cols-5 gap-3">
            <div v-for="i in 5" :key="`thumb-skeleton-${i}`" class="aspect-square animate-pulse rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)]"></div>
          </div>
        </div>
        <div class="space-y-5">
          <div class="h-5 w-1/3 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
          <div class="h-9 w-3/4 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
          <div class="h-4 w-1/2 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
          <div class="h-24 w-full animate-pulse rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)]"></div>
          <div class="h-12 w-2/3 animate-pulse rounded-full bg-[var(--color-surface-secondary)]"></div>
          <div class="h-12 w-full animate-pulse rounded-full bg-[var(--color-surface-secondary)]"></div>
        </div>
      </div>
    </div>

    <!-- ===================== 404 ===================== -->
    <div v-else-if="isNotFound" key="not-found" class="mx-auto max-w-[1600px] px-4 py-20 text-center">
      <span class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-surface-secondary)]">
        <UIcon name="lucide:package-x" class="h-10 w-10 text-[var(--color-brand-500)]" aria-hidden="true" />
      </span>
      <h1 class="mt-6 text-2xl font-black text-[var(--color-ink)]">{{ t('product.notFound') }}</h1>
      <p class="mt-2 text-sm text-[var(--color-muted)]">{{ t('product.notFoundDesc') }}</p>
      <NuxtLinkLocale
        to="/products"
        class="ui-pill ui-pill-brand mt-6 h-11 px-6 text-sm"
      >
        <UIcon name="lucide:arrow-left" class="h-4 w-4" aria-hidden="true" />
        {{ t('product.backToProducts') }}
      </NuxtLinkLocale>
    </div>

    <!-- ===================== 其它错误 ===================== -->
    <div v-else-if="error || !product" key="error" class="mx-auto max-w-[1600px] px-4 py-20 text-center">
      <span class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-error-50)]">
        <UIcon name="lucide:wifi-off" class="h-10 w-10 text-[var(--color-error)]" aria-hidden="true" />
      </span>
      <h1 class="mt-6 text-2xl font-black text-[var(--color-ink)]">{{ t('product.loadError') }}</h1>
      <p class="mt-2 text-sm text-[var(--color-muted)]">{{ t('product.loadErrorDesc') }}</p>
      <div class="mt-6 flex flex-wrap items-center justify-center gap-3">
        <button type="button" class="ui-pill ui-pill-brand h-11 px-6 text-sm" @click="refresh()">
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
          {{ t('common.retry') }}
        </button>
        <NuxtLinkLocale to="/products" class="ui-pill ui-pill-ghost h-11 px-6 text-sm">
          {{ t('product.backToProducts') }}
        </NuxtLinkLocale>
      </div>
    </div>

    <!-- =================================================================
         商品详情（2026-10 布局改版）

         桌面（≥1024px）：grid 两列 3fr / 2fr
           左列 = 图集（row1）+ 锚点与四区块（row2），随页面滚动；
           右列 = 信息面板，跨两行（align-self: stretch），内部 position: sticky 吸顶。
           面板的 top 由 useStickyAside 按「内容高度 vs 可用高度」动态算：
           内容不高 → 钉在 header 下方；内容超长 → 先随页面滚到全部露出再吸底。

         移动端（<1024px）：同一份 DOM 按自然顺序单列流动
           图集 → 信息面板 → 锚点 → 四区块；动作条固定底部通栏。

         ⚠️ 单一滚动条：右列及任何祖先都**不能**出现 overflow:auto/scroll，
            否则会出现第二个滚动条；滚轮必须直接落到 window 上。

         DOM 顺序刻意是「图集 → 面板 → 区块」：移动端直接是正确阅读顺序；
         桌面端再用 grid 的显式行列把它摆成左列两段 + 右列一段（不能用 DOM 顺序拆成左右列）。
         ================================================================= -->
    <div v-else key="content">
      <!-- ===== sticky zone：两栏 grid + 动作条 =====
           推荐区移出本 zone（见下方全宽 section），于是：
             · 右列 sticky 的容纳高度 = 本 zone 的 grid 高度，滚到推荐区前自然释放；
             · 动作条由 useStickyAside 监听滚动：zone 结束前固定，结束后随页面上移、不遮挡推荐。 -->
      <div ref="zoneRef" data-sticky-zone class="relative mx-auto max-w-[1600px] px-4">
        <div data-product-grid class="lg:-mt-6 lg:grid lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
          <!-- ===== 左列 row1：图集（无面包屑、无心形收藏） ===== -->
          <div ref="galleryRef" data-product-gallery class="min-w-0 pt-4 sm:pt-5 lg:col-start-1 lg:row-start-1 lg:pt-6 lg:pr-8">
            <ProductGallery
              v-model:active-index="activeImageIndex"
              :main-image="mainImage"
              :gallery="gallery"
              :title="product.title"
              :is-recommend="product.isRecommend === 'Y'"
              :badges="imageBadges"
              @share="handleShare"
            />
          </div>

          <!-- ===== 右列：信息面板（桌面跨两行 + 内部 sticky） =====
               asideRef 是 grid 子项（高度随整行 stretch），panelRef 是真正 sticky 的面板。
               两者分开：sticky 只能作用在"比父级矮"的元素上，父级必须是整行高度。 -->
          <aside
            ref="asideRef" data-product-aside
            class="mt-6 min-w-0 lg:relative lg:z-10 lg:col-start-2 lg:row-span-2 lg:row-start-1 lg:mt-0 lg:bg-[var(--color-cream)] lg:pt-6"
            :style="asideStyle"
          >
          <div ref="panelRef" data-product-panel class="lg:sticky" :style="panelStyle">
            <!-- 分类 + 商品编码 -->
            <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px]">
              <span v-if="product.categoryName" class="inline-flex items-center gap-1 font-bold text-[var(--color-brand-600)]">
                <UIcon name="lucide:bookmark" class="h-3.5 w-3.5" aria-hidden="true" />
                {{ product.categoryName }}
              </span>
              <span v-if="product.spuCode" class="text-[var(--color-muted)]">
                {{ t('product.spuCode') }}: {{ product.spuCode }}
              </span>
            </div>

            <h1 class="mt-2.5 text-2xl font-black leading-tight text-[var(--color-ink)] sm:text-3xl">
              {{ product.title }}
            </h1>
            <p v-if="product.subTitle" class="mt-2 text-sm text-[var(--color-muted)]">
              {{ product.subTitle }}
            </p>

            <!-- 价格块：超大现价 + 划线价 + 直降价签 -->
            <div class="price-block mt-5 px-5 py-4">
              <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span class="price text-[38px] leading-none sm:text-[42px]">{{ priceText }}</span>
                <span v-if="product.unit" class="text-sm text-[var(--color-muted)]">/ {{ product.unit }}</span>
              </div>
              <div v-if="priceView.showOriginal || priceView.discountAmount !== null" class="mt-2.5 flex flex-wrap items-center gap-2">
                <span v-if="priceView.showOriginal" class="price-strike text-sm">
                  {{ formatCompact(priceView.originalPrice) }}
                </span>
                <span v-if="priceView.discountAmount !== null" class="badge badge-hot">
                  {{ t('shop.discountOff', { amount: formatCompact(priceView.discountAmount) }) }}
                </span>
              </div>
              <!--
                价格区间提示：**必须先占位再隐藏，不能用 v-if 移除**。
                选中具体规格后这句提示就不该再出现，但用 v-if 直接把它从 DOM 删掉会让价格块矮一行，
                它下面的所有内容（统计行、规格、按钮…）整体上移 —— 用户点规格时页面会"跳"一下。
                改用 visibility:hidden 保留占位：它不参与绘制、不进入无障碍树，但**仍然占高度**，
                于是价格块高度在"未选规格 ↔ 已选规格"之间恒定不变。
                外层还用 hasPriceRange 兜一道：不跨价格区间的商品（本来就不会出现这句提示）
                不预留这一行，避免白白多出一段空白。
              -->
              <p
                v-if="hasPriceRange"
                class="mt-2 text-xs text-[var(--color-muted)]"
                :class="{ invisible: !showRangeHint }"
              >
                {{ t('products.priceRangeHint') }}
              </p>
            </div>

            <!-- 统计行：销量 / 评分 / 收藏，各自为 0 时该项不渲染 -->
            <div v-if="hasAnyStat" class="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-[13px] text-[var(--color-muted)]">
              <span v-if="product.salesCount > 0" class="inline-flex items-center gap-1.5">
                <UIcon name="lucide:flame" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
                {{ t('shop.sold', { count: formatCompactCount(product.salesCount) }) }}
              </span>
              <span v-if="rating !== null" class="inline-flex items-center gap-1.5">
                <UIcon name="lucide:star" class="h-4 w-4 fill-current text-[var(--color-accent-500)]" aria-hidden="true" />
                {{ ratingText }}
              </span>
              <span v-if="displayFavoriteCount > 0" class="inline-flex items-center gap-1.5">
                <UIcon name="lucide:heart" class="h-4 w-4 text-[var(--color-brand-400)]" aria-hidden="true" />
                {{ t('shop.profileFavorites') }} {{ formatCompactCount(displayFavoriteCount) }}
              </span>
            </div>

            <!-- 库存 -->
            <div class="mt-4 flex flex-wrap items-center gap-2 text-[13px]">
              <span
                class="h-2.5 w-2.5 rounded-full"
                :class="displayStock > 0 ? 'bg-[var(--color-success)]' : 'bg-[var(--color-error)]'"
                aria-hidden="true"
              ></span>
              <span class="font-bold" :class="displayStock > 0 ? 'text-[var(--color-success-600)]' : 'text-[var(--color-error-600)]'">
                {{ displayStock > 0 ? t('products.stock.inStock') : t('products.stock.outOfStock') }}
              </span>
              <span v-if="displayStock > 0" class="text-[var(--color-muted)]">
                {{ t('products.stock.remaining', { count: displayStock }) }}
              </span>
            </div>

            <!-- 规格选择器（核心交互：不存在 / 不可售组合置灰） -->
            <div v-if="hasSpecs" class="mt-7">
              <h2 class="text-sm font-bold text-[var(--color-ink)]">{{ t('products.spec.title') }}</h2>
              <div class="mt-3">
                <ProductSpecPicker
                  :specs="specs"
                  :combinations="combinations"
                  :selected="selected"
                  @update:selected="onSelectionChange"
                />
              </div>

              <!-- 组合状态提示 -->
              <p
                v-if="unavailableNotice"
                class="mt-3 inline-flex items-center gap-2 text-sm"
                :class="unavailableNotice.error ? 'text-[var(--color-error-600)]' : 'text-[var(--color-muted)]'"
              >
                <UIcon :name="unavailableNotice.error ? 'lucide:ban' : 'lucide:info'" class="h-4 w-4" aria-hidden="true" />
                {{ unavailableNotice.text }}
              </p>
              <p v-else-if="combination" class="mt-3 inline-flex items-center gap-2 text-sm text-[var(--color-ink-soft)]">
                <UIcon name="lucide:package-check" class="h-4 w-4 text-[var(--color-success)]" aria-hidden="true" />
                {{ combination.combinationName || combination.combinationKey }}
              </p>
            </div>

            <!-- 面板底部：数量 / 合计 / 提示 / 服务标签（三个动作按钮已移到固定动作条） -->
            <ProductPurchaseBar
              :quantity="quantity"
              :max-quantity="maxQuantity"
              :total-price="totalPrice"
              :buyable="buyable"
              :has-fast-tag="hasFastTag"
              :has-top-tag="hasTopTag"
              :free-shipping="!!product.freeShipping"
              @change-quantity="changeQuantity"
            />
          </div>
        </aside>

          <!-- ===== 左列 row2：锚点导航 + 三区块（锚点只在左列内吸顶） ===== -->
          <div data-product-sections class="mt-2 min-w-0 lg:col-start-1 lg:row-start-2 lg:mt-0 lg:pr-8">
            <ProductDetailSections
              :description="product.description"
              :detail-blocks="product.detailBlocks"
              :params="product.params"
              :product-title="product.title"
              :spec-rows="specRows"
              :review-summary="reviewSummary"
              :reviews="reviewRecords"
              :review-total="reviewTotal"
              :review-has-more="reviewHasMore"
              :review-loading-more="reviewLoadingMore"
              :review-error="reviewError"
              :has-related="related.length > 0"
              @load-more="loadMoreReviews"
            />
          </div>
        </div>

        <!-- ===== 底部动作条（在 sticky zone 内）=====
             桌面：先固定在视口右下（宽度=右列），滚到 zone 底部（= 推荐区上方）后随页面上移、不遮挡推荐；
             移动端：固定底部通栏，同样在「为你推荐」前释放（第 4 轮）——否则推荐区/页脚会被压住。
             left/width/transform 由 useStickyAside 注入（:style 透传到组件根节点）。 -->
        <ProductStickyActionBar
          v-if="!pending && !isNotFound && !error && product"
          :adding="adding"
          :add-to-cart-label="addToCartLabel"
          :can-add-to-cart="canAddToCart"
          :buying="buying"
          :buy-now-label="buyNowLabel"
          :can-buy-now="canBuyNow"
          :favorited="favorited"
          :favorite-pending="favoritePending"
          :style="barStyle"
          @add-to-cart="handleAddToCart"
          @buy-now="handleBuyNow"
          @toggle-favorite="handleToggleFavorite"
        />
      </div>

      <!-- ===== 为你推荐：横跨页面容器的全宽区块（第 2 轮移出左列）=====
           桌面 6 列、移动 2 列，复用首页同款 StoreProductCard；数据取 7 条排除当前后展示 6 条。
           锚点「为你推荐」由 DetailSections 按 #product-recommend 解析并滚动到这里。 -->
      <section
        id="product-recommend"
        data-product-recommend
        class="mx-auto mt-8 max-w-[1600px] border-t border-[var(--color-line-soft)] px-4 pb-16 pt-10"
      >
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
        <div v-if="related.length" class="mt-5 grid grid-cols-2 gap-4 sm:gap-5 lg:grid-cols-6 lg:gap-6">
          <!--
            推荐区在首屏之外（详情页往下 3000px+），**不要** eager：
            eager 会把这 6 张图提到 high 优先级、和首屏 JS 抢连接，
            实测会明显拖慢水合（用户反馈的"图片加载时按钮点不动"）。
          -->
          <StoreProductCard
            v-for="item in related"
            :key="item.id"
            :product="item"
          />
        </div>
        <p v-else class="mt-5 text-sm text-[var(--color-muted)]">{{ t('shop.noData') }}</p>
      </section>
    </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
// 商品详情用 View Transitions 做页面级过场（nuxt.config 的 experimental.viewTransition 由基建开启）
definePageMeta({ viewTransition: true })

import type { SpecSelection, StoreProductCard, StoreProductDetail, StoreProductPage } from '~~/types/shop-product'
import { formatCompactCount } from '~~/types/shop-product'
import { useStorePrice } from '~~/composables/useProducts'
import { useProductDetail } from '~~/composables/useProductDetail'
import { useProductReviews } from '~~/composables/useReviews'
import { useStickyAside } from '~~/composables/useStickyAside'
import { extractCartErrorMessage, useCart } from '~~/composables/useCart'
import { extractFavoriteErrorMessage } from '~~/composables/useFavorites'
import { writeCheckoutSnapshot } from '~~/composables/useCheckoutSnapshot'
import { useAuth } from '~~/composables/useAuth'

/**
 * 商品详情页（2026-10 第 4 轮布局调整）。
 *
 * 布局契约（docs/商品页布局与底部动作条-设计说明.md，含「八、第 2 轮调整」）：
 *   · 桌面 ≥1024px：左列 60%（图集 + 锚点 + 规格参数/评价/详情，随页面滚动）
 *                  + 右列 40% 商品信息面板（吸顶）；
 *   · 图集：竖排缩略图在左、主图在右；主图高度 = 100dvh - header - 56px，
 *     底边与动作条浮层底底边同在 viewport-32px（高度由 useStickyAside 测量，首帧 CSS 回退）；
 *   · 右列吸顶由 useStickyAside 动态计算 top（内容不高→header 下；超长→滚到全部露出再吸底），
 *     右列任何祖先都没有 overflow：**全程只有浏览器级滚动条**；
 *   · 底部动作条：桌面固定右下、宽度=右列；移动 fixed 通栏 + 安全区；
 *     桌面/移动端都在滚到「为你推荐」前（sticky zone 结束）后随页面上移，不遮挡推荐，回滚恢复；
 *   · 「为你推荐」移出左列，作为 zone 之后横跨页面容器的全宽区块（桌面 6 列 / 移动 2 列）；
 *   · 移动端单列顺序：图集 → 信息面板 → 锚点 → 三区块 → 全宽推荐。
 *
 * 购买闭环（2026-10-14 扩充，本轮只搬家不改语义）：
 *   加入购物车 / **立即购买** / **收藏** 三个动作现在都在底部动作条，
 *   口径完全一致——未登录先引导登录、有规格必须选全、规格组合必须可售；
 *   三个动作都不做本地乐观更新，全部以服务端返回的状态为准：
 *     · 加购后购物车整体由 useCart 用后端返回值替换；
 *     · 立即购买把这一件写进结算快照，金额由结算页调服务端重算（前端不碰钱）；
 *     · 收藏由 useProductFavorite 回填 favorited 与收藏数。
 *
 * 数据契约：所有模块都遵守「零值不渲染」——
 *   评分 / 评价数 / 销量 / 收藏数为 0 时不渲染对应元素；
 *   规格参数优先用后端新字段 params（KV），为空时才回退真实字段（spuCode / categoryName）拼；
 *   商品详情 = description（简介）+ detailBlocks（结构化模块），两者都空显示「暂无商品描述」；
 *   评价区（真实数据，2026-10 本轮）：汇总分数 / 条数 / 星级分布与评价列表都来自 /api/review，
 *     汇总与列表同源；`reviewTotal === 0` 时整块（含锚点「用户评价」）不渲染，绝不留空壳。
 */
const route = useRoute()
const { t, locale } = useI18n()
const toast = useToast()
const localePath = useLocalePath()
const { isLoggedIn } = useAuth()
const { addItem } = useCart()
const { formatCompact } = useStorePrice()
/** SSR/客户端都能带上会话头的 fetch（推荐位/后续客户端补取都用它） */
const requestFetch = useRequestFetch()

// 数据与派生状态全部来自 composables/useProductDetail.ts ——
// 该页面曾近 1060 行，先把数据层收拢，模板拆组件时才有清晰边界。
const {
  slug,
  product,
  pending,
  error,
  refresh,
  quantity,
  adding,
  buying,
  activeImageIndex,
  selected,
  favorited,
  favoritePending,
  refreshFavorite,
  toggleFavorite,
  specs,
  combinations,
  hasSpecs,
  combination,
  selectionComplete,
  gallery,
  mainImage,
  priceView,
  priceText,
  showRangeHint,
  hasPriceRange,
  rating,
  ratingText,
  displayFavoriteCount,
  hasAnyStat,
  specRows,
  displayStock,
  maxQuantity,
  totalPrice,
  buyable,
  unavailableNotice,
  canAddToCart,
  addToCartLabel,
  canBuyNow,
  buyNowLabel,
  imageBadges,
  hasFastTag,
  hasTopTag,
} = useProductDetail()

// ===== 用户评价（真实数据，见 composables/useReviews.ts）=====
// 评价首屏走 useAsyncData（SSR 取数），key 用路由 slug（spu_code）——
// 后端评价接口同时支持主键与 spu_code，用 slug 才能在 setup 阶段确定 key，
// 让服务端渲染直接带出评价区，避免客户端补拉时把下面的详情整块推下去（CLS）。
const reviewProductKey = computed(() => String(route.params.slug || ''))

const {
  summary: reviewSummary,
  records: reviewRecords,
  total: reviewTotal,
  hasMore: reviewHasMore,
  loadingMore: reviewLoadingMore,
  error: reviewError,
  loadMore: loadMoreReviews,
} = useProductReviews(reviewProductKey)

// ===== 桌面两栏吸顶 + 底部动作条对齐 + 图集高度 + 动作条释放 =====
// asideRef 是右列 grid 子项（整行高度），panelRef 是内部 sticky 面板；
// galleryRef 用来算主图高度（底边对齐动作条底边）；zoneRef 用来在推荐区前释放动作条。
// composable 只在 resize / ResizeObserver / 语言切换时重算面板 top，滚动时只算动作条释放。
const asideRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const galleryRef = ref<HTMLElement | null>(null)
const zoneRef = ref<HTMLElement | null>(null)
const { panelStyle, asideStyle, pageStyle, barStyle } = useStickyAside({ asideRef, panelRef, galleryRef, zoneRef })

// ===== 为你推荐：同分类真实商品，排除当前商品后展示 6 条 =====
// 详情页不再 await 取推荐：详情就绪后再补一次请求即可（推荐位在页面底部，不需要进 SSR）。
//
// 旧实现是「同分类请求 → 不足 6 条再发一次全站兜底」，两次请求串行：分类里商品少时
// 用户要多等一个 RTT，而且第二次请求可能把推荐位整体换掉（视觉跳动）。
// 现在只发**一次**请求：有分类就取该分类（pageSize=13，足够挑出 6 条），不补位；
// 分类下确实不足 6 条时就展示实际数量 —— 推荐位不是导航，少几条不影响可用性。
const related = ref<StoreProductCard[]>([])

watch(
  () => [product.value?.id, product.value?.categoryId] as const,
  async ([id, categoryId]) => {
    if (!id) return
    try {
      const result = await requestFetch<StoreProductPage>('/api/products', {
        query: {
          lang: locale.value,
          pageNo: 1,
          pageSize: 13,
          ...(categoryId ? { categoryId } : {}),
        },
      })
      const seen = new Set<string>([id])
      const items: StoreProductCard[] = []
      for (const item of result?.records ?? []) {
        if (seen.has(item.id)) continue
        seen.add(item.id)
        items.push(item)
        if (items.length >= 6) break
      }
      related.value = items
    } catch {
      // 推荐位失败不影响主信息：保持为空即可，不弹错误
      related.value = []
    }
  },
  { immediate: true },
)

// 锚点导航（吸顶 + 滚动高亮 + 点击滚动）随下半部区块一起搬进 components/product/DetailSections.vue：
// 它依赖左列三块的 DOM 引用与"是否渲染"；「为你推荐」锚点由它按 #product-recommend 解析到页面全宽区块。

onMounted(() => {
  // 收藏态只能客户端查（需要 access-token cookie；SSR 期间还没恢复登录态）
  void refreshFavorite()
  // 评价**不在这里**拉：首屏由 useAsyncData 在 SSR 阶段取好，商品切换由 key 变化自动重取，
  // 这里再调会打出重复请求。
})

const isNotFound = computed(() => {
  const err = error.value as { statusCode?: number } | null
  // ① 接口明确 404；② 没有错误但也没有数据（后端返回了空对象/空响应）——都按"不存在"处理
  return err?.statusCode === 404 || (!pending.value && !err && !product.value)
})

/** 切换选择时重置数量与主图 */
function onSelectionChange(next: SpecSelection) {
  selected.value = next
  quantity.value = 1
  activeImageIndex.value = 0
}

function changeQuantity(delta: number) {
  quantity.value = Math.min(Math.max(1, quantity.value + delta), maxQuantity.value)
}

// 商品切换（同为详情路由参数变化）时重置本地状态
watch(
  () => product.value?.id,
  () => {
    selected.value = {}
    quantity.value = 1
    activeImageIndex.value = 0
    // 收藏态同样要跟着换：否则会显示"上一件商品已收藏"的心形
    void refreshFavorite()
  },
)

async function handleShare() {
  if (!import.meta.client) return
  try {
    await navigator.clipboard.writeText(window.location.href)
    toast.add({ title: t('product.shareCopied'), color: 'success', icon: 'lucide:link' })
  } catch {
    // 剪贴板不可用（非 HTTPS / 权限被拒）时也要如实告知，不能静默
    toast.add({ title: t('product.shareFailed'), color: 'error', icon: 'lucide:circle-alert' })
  }
}

/**
 * 加入购物车。
 *
 * 失败一律把后端的原话展示出来（例如"库存不足：最多可购买 3 件…"），
 * 不在这里替换成"操作失败"这种没有信息量的兜底文案——
 * 后端已经算清楚了原因，前端再包一层只会让用户猜。
 */
async function handleAddToCart() {
  if (!product.value || adding.value) return

  if (!isLoggedIn.value) {
    await navigateTo(localePath({ path: '/login', query: { redirect: route.fullPath } }))
    return
  }
  if (hasSpecs.value && !selectionComplete.value) {
    toast.add({ title: t('product.selectSpecFirst'), color: 'warning', icon: 'lucide:info' })
    return
  }

  adding.value = true
  try {
    await addItem({
      productId: product.value.id,
      // 无规格商品必须传空串，后端据此区分"无规格"与"未选规格"
      skuId: combination.value?.id ?? '',
      quantity: quantity.value,
      source: 'detail',
    })
    toast.add({ title: t('product.addedToCart'), color: 'success', icon: 'lucide:check' })
    quantity.value = 1
  } catch (error) {
    toast.add({
      title: t('cart.actionFailed'),
      description: extractCartErrorMessage(error, t('cart.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    adding.value = false
  }
}

/**
 * 立即购买。
 *
 * 与"加入购物车"的**唯一区别**：不写购物车，而是把"这一件 + 已选规格 + 数量"直接写进结算快照，
 * 跳到 /checkout 由服务端算钱。三个前置校验与加购同源（登录态 / 规格选全 / 可售），
 * 避免出现"能加购却买不了"或者反过来的自相矛盾。
 *
 * 快照写入失败（隐私模式禁用 sessionStorage）时必须显式提示：
 * 否则用户会被送到结算页看到"没有待结算商品"，完全不知道是自己浏览器的问题。
 */
async function handleBuyNow() {
  if (!product.value || buying.value) return

  if (!isLoggedIn.value) {
    await navigateTo(localePath({ path: '/login', query: { redirect: route.fullPath } }))
    return
  }
  if (hasSpecs.value && !selectionComplete.value) {
    toast.add({ title: t('product.selectSpecFirst'), color: 'warning', icon: 'lucide:info' })
    return
  }
  if (!buyable.value) return

  const written = writeCheckoutSnapshot([{
    // 立即购买不经过购物车：cartItemId 空串 -> 下单成功后后端不清任何购物车条目
    cartItemId: '',
    productId: product.value.id,
    // 无规格商品必须传空串（后端据此区分"无规格"与"未选规格"）
    skuId: combination.value?.id ?? '',
    quantity: quantity.value,
  }])
  if (!written) {
    toast.add({ title: t('product.buyNowFailed'), color: 'error', icon: 'lucide:circle-alert' })
    return
  }

  buying.value = true
  try {
    await navigateTo(localePath('/checkout'))
  } finally {
    buying.value = false
  }
}

/**
 * 收藏 / 取消收藏。
 *
 * 未登录时先引导登录（与加购同一套交互）：收藏是用户维度的数据，没有"游客收藏"这回事。
 * 状态一律取接口返回值，不做本地取反 —— 已收藏/未收藏的实时状态只有后端知道。
 */
async function handleToggleFavorite() {
  if (!product.value || favoritePending.value) return

  if (!isLoggedIn.value) {
    await navigateTo(localePath({ path: '/login', query: { redirect: route.fullPath } }))
    return
  }

  try {
    const status = await toggleFavorite()
    toast.add({
      title: status.favorited ? t('product.addedToWishlist') : t('product.removedFromWishlist'),
      color: 'success',
      icon: 'lucide:heart',
    })
  } catch (error) {
    toast.add({
      title: t('product.wishlistFailed'),
      description: extractFavoriteErrorMessage(error, t('cart.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  }
}

/**
 * SEO / 404。
 *
 * 旧实现用 `useSeoMeta` 无条件注册 `ogTitle / ogImage` 等标签：商品不存在时
 * 这些 getter 没有值，unhead 在 `tags:afterResolve` 里对非字符串的 content 调
 * `replaceAll` 直接抛 `TypeError`，整页从"404 友好提示"变成 **HTTP 500**。
 *
 * 现在改成 `useHead` + 显式构造 meta：
 *  - 所有 content 都保证是字符串（标题永远有回退）；
 *  - 商品不存在时只输出 noindex，**不注册任何商品 SEO**；
 *  - SSR 命中"不存在"时显式 `setResponseStatus(404)`，搜索引擎才会把它当 404，
 *    而不是可收录的空页面。
 */
const notFoundTitle = computed(() => t('product.notFound') || 'Product not found')

// ===== 结构化数据（schema.org Product + BreadcrumbList） =====
// Google 的商品富结果依赖这些字段：name/image/offers 必填，aggregateRating 有评价时才输出。
// 只在该商品真实存在时输出；404 页不输出任何商品结构化数据（与 noindex 一致）。
const { storeName } = useStoreInfo()
const siteConfig = useSiteConfig()
const priceCurrency = String(useRuntimeConfig().public.storeCurrency || 'CNY')

function buildJsonLd(current: StoreProductDetail, canonicalUrl: string) {
  const base = String(siteConfig.url || '').replace(/\/+$/, '')
  const images = (current.images || [])
    .map(image => image.url)
    .filter((src): src is string => !!src)
  const productNode: Record<string, unknown> = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: current.seoTitle || current.title,
    sku: current.spuCode || current.id,
    url: canonicalUrl,
    image: images.length ? images : [current.coverUrl, current.mainImage].filter(Boolean),
    brand: { '@type': 'Brand', name: storeName.value || t('shop.brandName') },
    offers: {
      '@type': 'Offer',
      url: canonicalUrl,
      price: current.price ?? current.minPrice ?? 0,
      priceCurrency,
      availability: current.stock > 0
        ? 'https://schema.org/InStock'
        : 'https://schema.org/OutOfStock',
    },
  }
  const description = current.seoDescription || current.subTitle
  if (description) {
    productNode.description = description
  }
  if (reviewSummary.value && reviewSummary.value.reviewCount > 0) {
    productNode.aggregateRating = {
      '@type': 'AggregateRating',
      ratingValue: reviewSummary.value.ratingAvg,
      reviewCount: reviewSummary.value.reviewCount,
      bestRating: 5,
      worstRating: 1,
    }
  }

  const crumbs: Array<Record<string, unknown>> = [
    { '@type': 'ListItem', position: 1, name: t('common.home'), item: `${base}${localePath('/')}` },
    { '@type': 'ListItem', position: 2, name: t('products.all'), item: `${base}${localePath('/products')}` },
  ]
  if (current.categoryId && current.categoryName) {
    crumbs.push({
      '@type': 'ListItem',
      position: 3,
      name: current.categoryName,
      item: `${base}${localePath(`/categories/${current.categoryId}`)}`,
    })
  }
  crumbs.push({
    '@type': 'ListItem',
    position: crumbs.length + 1,
    name: current.title,
    item: canonicalUrl,
  })

  return [
    productNode,
    {
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: crumbs,
    },
  ]
}

useHead(() => {
  const current = product.value
  const missing = isNotFound.value
  const title = missing
    ? notFoundTitle.value
    : (current?.seoTitle || current?.title || notFoundTitle.value)
  const description = missing ? '' : (current?.seoDescription || current?.subTitle || '')
  const image = missing ? '' : (current?.coverUrl || current?.mainImage || '')

  const meta: Array<Record<string, string>> = [
    { name: 'robots', content: missing ? 'noindex, nofollow' : 'index, follow' },
    { property: 'og:title', content: title },
  ]
  if (description) {
    meta.push(
      { name: 'description', content: description },
      { property: 'og:description', content: description },
    )
  }
  if (image) {
    meta.push({ property: 'og:image', content: image })
  }

  const script: Array<Record<string, string>> = []
  if (!missing && current) {
    const base = String(siteConfig.url || '').replace(/\/+$/, '')
    const canonicalUrl = `${base}${localePath(`/products/${current.spuCode || current.id}`)}`
    script.push({
      type: 'application/ld+json',
      innerHTML: JSON.stringify(buildJsonLd(current, canonicalUrl)),
    })
  }

  return { title, meta, script }
})

if (import.meta.server) {
  const requestEvent = useRequestEvent()
  // flush: 'sync' 保证 useAsyncData 的 error 一落地就写响应状态，赶在响应发出之前
  watch(isNotFound, (missing) => {
    if (missing && requestEvent) {
      setResponseStatus(requestEvent, 404, 'Not Found')
    }
  }, { immediate: true, flush: 'sync' })
}
</script>
