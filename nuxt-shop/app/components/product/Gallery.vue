<!--
  商品图集（左栏）：主图 + 角标 + 分享/收藏浮动按钮 + 缩略图条

  从 pages/products/[slug].vue 拆出（商品详情页拆组件第一步）：
  这一块只依赖"当前商品展示相关的几个值"，与页面其它部分（价格/规格/购买栏/下半部区块）耦合最少，
  所以先拆它 —— 每次拆完都用 .probe/smoke-product-detail.ps1 做真实页面回归。

  组件是**受控**的：当前图下标由父组件持有（activeIndex + update:activeIndex），
  因为切换规格时页面要把它重置为 0；分享/收藏也交回父组件处理（涉及 toast 与后端关系）。
-->
<template>
  <div class="space-y-4">
    <div class="ui-card relative overflow-hidden">
      <img
        v-if="mainImage"
        :key="mainImage"
        :src="mainImage"
        :alt="title"
        width="600"
        height="600"
        class="aspect-square w-full object-cover"
        loading="eager"
        fetchpriority="high"
      >
      <div v-else class="flex aspect-square w-full items-center justify-center text-[var(--color-muted)]">
        <UIcon name="lucide:image-off" class="h-14 w-14" aria-hidden="true" />
      </div>

      <!-- 角标：只渲染真实存在的推荐标记与 HOT/SALE/NEW/PICK 标签 -->
      <div v-if="badges.length" class="absolute left-4 top-4 flex flex-wrap gap-1.5">
        <span v-if="isRecommend" class="badge badge-new">
          {{ t('products.recommended') }}
        </span>
        <span
          v-for="tag in badges"
          :key="tag.id"
          class="badge"
          :class="badgeToneClass(tag.code)"
        >
          {{ tag.name }}
        </span>
      </div>

      <!-- 分享（复制链接）/ 收藏：收藏是真实写入后端的关系，不是本地开关 -->
      <div class="absolute right-4 top-4 flex flex-col gap-2">
        <button
          type="button"
          class="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full bg-white/92 text-[var(--color-ink-soft)] shadow-[var(--shadow-md)] backdrop-blur-sm transition-colors duration-200 hover:bg-[var(--color-brand-500)] hover:text-white motion-reduce:transition-none"
          :aria-label="t('product.share')"
          @click="emit('share')"
        >
          <UIcon name="lucide:share-2" class="h-[18px] w-[18px]" aria-hidden="true" />
        </button>
        <button
          type="button"
          class="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full bg-white/92 shadow-[var(--shadow-md)] backdrop-blur-sm transition-colors duration-200 disabled:cursor-wait motion-reduce:transition-none"
          :class="favorited
            ? 'bg-[var(--color-brand-500)] text-white'
            : 'text-[var(--color-ink-soft)] hover:bg-[var(--color-brand-500)] hover:text-white'"
          :aria-label="favorited ? t('product.removeFromWishlist') : t('product.addToWishlist')"
          :aria-pressed="favorited"
          :aria-busy="favoritePending"
          :disabled="favoritePending"
          @click="emit('toggle-favorite')"
        >
          <UIcon name="lucide:heart" class="h-[18px] w-[18px]" :class="{ 'fill-current': favorited }" aria-hidden="true" />
        </button>
      </div>
    </div>

    <!-- 缩略图：选中带图 SKU 时该 SKU 图已在 gallery 首位 -->
    <div v-if="gallery.length > 1" class="grid grid-cols-5 gap-3">
      <button
        v-for="(img, idx) in gallery"
        :key="`${img}-${idx}`"
        type="button"
        class="cursor-pointer overflow-hidden rounded-[var(--radius-tile)] border-2 transition-all duration-200 motion-reduce:transition-none"
        :class="activeIndex === idx
          ? 'border-[var(--color-brand-500)]'
          : 'border-transparent opacity-60 hover:opacity-100'"
        :aria-label="t('product.galleryItem', { index: idx + 1 })"
        :aria-current="activeIndex === idx ? 'true' : undefined"
        @click="emit('update:activeIndex', idx)"
      >
        <img :src="img" :alt="`${title} ${idx + 1}`" width="600" height="600" class="aspect-square w-full object-cover" loading="lazy">
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { BadgeTone, ShopTagRef } from '~~/types/shop-product'
import { resolveTagTone } from '~~/types/shop-product'

const props = defineProps<{
  /** 当前主图（空串表示没有图，渲染占位图标） */
  mainImage: string
  /** 全部图（第一张为主图；多于一张才渲染缩略图条） */
  gallery: string[]
  /** 当前选中的图下标（受控：切换规格时父组件会重置为 0） */
  activeIndex: number
  /** 商品标题：用于 alt 文本 */
  title: string
  /** 是否「推荐」商品（后端 isRecommend === 'Y'） */
  isRecommend: boolean
  /** 图片角标：只包含 HOT / SALE / NEW / PICK 这些真实存在的标签 */
  badges: ShopTagRef[]
  favorited: boolean
  favoritePending: boolean
}>()

const emit = defineEmits<{
  (e: 'update:activeIndex', value: number): void
  (e: 'share'): void
  (e: 'toggle-favorite'): void
}>()

const { t } = useI18n()

/**
 * 角标配色：按标签 code 的真实语义取色（与商品卡片同一套 resolveTagTone）。
 * 原先在 useProductDetail 里，现在只有图集用得到，随组件一起搬过来。
 */
function badgeToneClass(code: string | null | undefined): string {
  const tone: BadgeTone = resolveTagTone(code)
  switch (tone) {
    case 'hot':
      return 'badge-hot'
    case 'sale':
      return 'badge-sale'
    case 'new':
      return 'badge-new'
    default:
      return 'badge-soft'
  }
}
</script>
