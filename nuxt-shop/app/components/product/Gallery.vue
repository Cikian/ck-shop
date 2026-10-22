<!--
  商品图集（左栏）：桌面 = 左侧竖排缩略图 + 右侧主图；移动端 = 主图 + 下方缩略图行

  从 pages/products/[slug].vue 拆出（商品详情页拆组件第一步）：
  这一块只依赖"当前商品展示相关的几个值"，与页面其它部分（价格/规格/购买栏/下半部区块）耦合最少，
  所以先拆它 —— 每次拆完都用 .probe/smoke-product-detail.ps1 做真实页面回归。

  组件是**受控**的：当前图下标由父组件持有（activeIndex + update:activeIndex），
  因为切换规格时页面要把它重置为 0；分享交回父组件处理（涉及 toast）。
  收藏按钮已按 2026-10 布局改版移到页面底部固定动作条，这里不再渲染心形。

  第 2 轮布局（设计说明 八）：
    · 桌面 ≥1024px：缩略图竖排在左（宽 56px），主图在右；
      主图高度 = 100dvh - header - 56px（等价「底边落在 viewport-32px」），
      尺寸来自页面 useStickyAside 写入的 --gallery-main-height（首帧用 CSS calc 回退）。
      主图保持正方形（宽=高），底边与底部动作条底边同线；缩略图首屏完整可见，
      图多到放不下时只在缩略图竖条内部滚动，不截断主图。
    · 移动端 <1024px：保持「主图（1:1）+ 下方缩略图行」。

  主图切换：**不换 key、不重建 <img>**。两层 <img> 常驻，新图先加载到"背面"那层，
  加载完成后翻转 opacity（200ms 交叉淡入）—— 这样切换商品图不会出现"旧图先消失、
  新图还在下载"的白屏闪烁；加载失败则保留原图并显示占位。
-->
<template>
  <!-- 同一份 DOM：移动端纵向（主图 → 缩略图行），桌面横向（缩略图列 → 主图） -->
  <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-2 lg:overflow-hidden">
    <!-- 缩略图：移动端在主图下方一行；桌面在左侧竖排 -->
    <div
      v-if="gallery.length > 1"
      class="no-scrollbar order-2 grid grid-cols-5 gap-3 lg:order-1 lg:flex lg:w-14 lg:shrink-0 lg:flex-col lg:gap-2 lg:overflow-y-auto gallery-thumbs"
    >
      <button
        v-for="(img, idx) in gallery"
        :key="`${img}-${idx}`"
        type="button"
        class="aspect-square w-full shrink-0 cursor-pointer overflow-hidden rounded-[var(--radius-tile)] border-2 transition-all duration-200 motion-reduce:transition-none"
        :class="activeIndex === idx
          ? 'border-[var(--color-brand-500)]'
          : 'border-transparent opacity-60 hover:opacity-100'"
        :aria-label="t('product.galleryItem', { index: idx + 1 })"
        :aria-current="activeIndex === idx ? 'true' : undefined"
        @mouseenter="preload(img)"
        @focus="preload(img)"
        @click="emit('update:activeIndex', idx)"
      >
        <ShopImage
          :src="img"
          :alt="`${title} ${idx + 1}`"
          width="600"
          height="600"
          class="aspect-square w-full object-cover"
          icon-class="h-4 w-4 lg:h-5 lg:w-5"
        />
      </button>
    </div>

    <!-- 主图：桌面用 --gallery-main-height 定高定宽（正方形、底边对齐动作条）；移动端 1:1 -->
    <div
      class="gallery-main ui-card relative order-1 min-w-0 aspect-square w-full overflow-hidden lg:order-2 lg:aspect-auto"
      :class="{ 'has-thumbs': gallery.length > 1 }"
    >
      <div class="relative h-full w-full bg-[var(--color-surface-secondary)]">
        <!-- 第 0 层 / 第 1 层：始终只渲染有 src 的，切换时轮流做"背面"层 -->
        <img
          v-if="layerSrc[0]"
          :src="layerSrc[0]"
          :alt="title"
          width="600"
          height="600"
          class="absolute inset-0 h-full w-full object-cover transition-opacity duration-200 ease-out motion-reduce:transition-none"
          :class="activeLayer === 0 ? 'opacity-100' : 'opacity-0'"
          :aria-hidden="activeLayer === 0 ? undefined : 'true'"
          loading="eager"
          fetchpriority="high"
          @load="onLayerLoad(0)"
          @error="onLayerError(0)"
        >
        <img
          v-if="layerSrc[1]"
          :src="layerSrc[1]"
          :alt="title"
          width="600"
          height="600"
          class="absolute inset-0 h-full w-full object-cover transition-opacity duration-200 ease-out motion-reduce:transition-none"
          :class="activeLayer === 1 ? 'opacity-100' : 'opacity-0'"
          :aria-hidden="activeLayer === 1 ? undefined : 'true'"
          loading="eager"
          fetchpriority="high"
          @load="onLayerLoad(1)"
          @error="onLayerError(1)"
        >

        <!-- 没有图 / 当前图加载失败：中性占位（不是碎图 + alt 文本） -->
        <div v-if="!hasVisibleImage" class="absolute inset-0 flex items-center justify-center text-[var(--color-muted)]">
          <UIcon name="lucide:image-off" class="h-14 w-14" aria-hidden="true" />
        </div>
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

      <!-- 分享（复制链接）：收藏已移到页面底部固定动作条，图集右上角只保留分享 -->
      <div class="absolute right-4 top-4 flex flex-col gap-2">
        <button
          type="button"
          class="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full bg-white/92 text-[var(--color-ink-soft)] shadow-[var(--shadow-md)] backdrop-blur-sm transition-colors duration-200 hover:bg-[var(--color-brand-cta)] hover:text-white motion-reduce:transition-none"
          :aria-label="t('product.share')"
          @click="emit('share')"
        >
          <UIcon name="lucide:share-2" class="h-[18px] w-[18px]" aria-hidden="true" />
        </button>
      </div>
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
}>()

const emit = defineEmits<{
  (e: 'update:activeIndex', value: number): void
  (e: 'share'): void
}>()

const { t } = useI18n()

// ---------------------------------------------------------------------------
// 主图交叉淡入：两层常驻，新图加载完成后再翻转
// ---------------------------------------------------------------------------
type Layer = 0 | 1

const layerSrc = ref<[string, string]>(['', ''])
const layerFailed = ref<[boolean, boolean]>([false, false])
const activeLayer = ref<Layer>(0)

const hasVisibleImage = computed(() => {
  const src = layerSrc.value[activeLayer.value]
  return !!src && !layerFailed.value[activeLayer.value]
})

/** 新图放进"背面"层；等它 load 完再翻到前面（否则会先闪一下空白） */
watch(() => props.mainImage, (next) => {
  if (!next) {
    layerSrc.value = ['', ''] as [string, string]
    layerFailed.value = [false, false] as [boolean, boolean]
    return
  }
  if (layerSrc.value[activeLayer.value] === next) return
  const back: Layer = activeLayer.value === 0 ? 1 : 0
  if (layerFailed.value[back]) layerFailed.value[back] = false
  layerSrc.value[back] = next
  // 首次加载（两层都空）没有"原图"可保留，直接让背面层成为可见层，避免空白等待
  if (!layerSrc.value[activeLayer.value]) activeLayer.value = back
}, { immediate: true })

function onLayerLoad(layer: Layer) {
  // 只认"当前主图"的加载完成：快速连点时旧图的 load 事件必须被丢弃
  if (layerSrc.value[layer] !== props.mainImage) return
  if (layer !== activeLayer.value && !layerFailed.value[layer]) activeLayer.value = layer
}

function onLayerError(layer: Layer) {
  if (layerSrc.value[layer] !== props.mainImage) return
  layerFailed.value[layer] = true
  // 失败的新图不要顶掉上一张能看的图：保持当前层不变
}

/** 缩略图 hover/focus 时预加载，点下去时主图基本已经进缓存 */
function preload(src: string) {
  if (!import.meta.client || !src) return
  const image = new Image()
  image.src = src
}

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

<style scoped>
/*
  桌面图集尺寸：
    · 主图正方形（宽=高），高度由页面 useStickyAside 写入 --gallery-main-height（px）；
    · 首帧（水合前 / JS 未就绪）用 CSS calc 回退：100dvh - 导航总高 - 56px
      （56 = 图集顶部间距 24 + 动作条离底 32），保证底边仍落在 viewport-32px
      （与 StickyActionBar 的 lg:bottom-8、useStickyAside 的 BAR_BOTTOM_OFFSET 三处一致）；
    · 缩略图竖条与主图同高：图多时在竖条内部滚动，绝不截断主图。
*/
@media (min-width: 1024px) {
  .gallery-main {
    height: var(--gallery-main-height, calc(100dvh - var(--nav-total-height) - 56px));
    /*
     * 宽度取 min(视口高度算出的边长, 左列可用宽度)：
     * 之前写死成正方形边长 + flex: 0 0 auto 时，在「桌面宽度较窄但窗口很高」的场景下
     * （例如 1400×965），主图 805px + 缩略图 64px 会超过左列宽度，直接压到右侧信息栏上
     * ——用户实测截图里的"左右内容重叠"就是这个。
     * 主图右侧还有 56px 缩略图 + 8px 间距 = 4rem，所以有缩略图时要把它扣掉。
     */
    width: min(var(--gallery-main-height, calc(100dvh - var(--nav-total-height) - 56px)), 100%);
    max-width: 100%;
    flex: 0 0 auto;
  }

  .gallery-main.has-thumbs {
    width: min(var(--gallery-main-height, calc(100dvh - var(--nav-total-height) - 56px)), calc(100% - 4rem));
  }

  .gallery-thumbs {
    height: var(--gallery-main-height, calc(100dvh - var(--nav-total-height) - 56px));
  }
}
</style>
