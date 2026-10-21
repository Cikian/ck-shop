<!--
  商品详情模块渲染器（契约 4.3：8 种 blockType）

  数据来源：StoreProductDetail.detailBlocks（types/shop-product.ts 已归一化并按 sortOrder 排序）。
  本组件只负责"数据 → 视觉结构"，不做任何请求、不依赖后端新接口是否就绪：
  后端还没返回 detailBlocks 时，父组件不会渲染本组件，老商品表现不变。

  布局契约（2026-10 第 2 轮，契约 1.4 / 4.3）：
    · 每个模块最外层 div 用**内联 margin** 渲染四方向间距（默认 0px），
      容器上**不允许**任何统一间距（space-y / gap / my-* / mt-*），否则默认 0 会被撑开，
      超长图切割后的多张 IMAGE 模块就拼不严（会露出缝）。
    · 图片圆角只来自 content.radius（默认 0 = 直角），不再用组件默认圆角类；
      圆角 0 时不输出 border-radius 样式，保证"默认就是直角"。
    · 宫格用 repeat(columns, minmax(0, 1fr))（1–6 列），每格内联 itemMargin，
      去掉 aspect-square / object-cover 强制裁切 —— 长图切割块必须按原比例完整显示。

  渲染细则（与契约逐条对应）：
    IMAGE       整幅图片，width:100% / height:auto / 圆角取 radius / lazy / async，点击开灯箱
    IMAGE_GRID  1–6 列宫格，每格按 itemMargin 留白，图片按原比例；整块图片共用一条灯箱序列
    IMAGE_TEXT  ≥768px 左右两栏（图 45%），<768px 上下堆叠（图在上）；layout 只影响桌面顺序
    TEXT        \n 分段，标题 h3 级别样式
    RICH_TEXT   渲染前必须过一次 sanitizeHtml（历史脏数据防护在渲染端兜底）
    VIDEO       bilibili / youtube / file；解析不出 id 时整块不渲染
    TITLE       小节标题 + 可选副标题
    DIVIDER     分隔线
-->
<template>
  <div v-if="blocks.length">
    <template v-for="(block, blockIndex) in blocks" :key="`${block.type}-${blockIndex}`">
      <!-- ===================== 整幅图片 ===================== -->
      <div v-if="block.type === 'IMAGE'" :style="spacingStyle(block)">
        <button
          type="button"
          class="block w-full cursor-zoom-in"
          :aria-label="t('product.imageEnlarge')"
          @click="openLightbox([{ url: block.url, alt: block.alt }], 0, $event)"
        >
          <ShopImage
            :src="block.url"
            :alt="detailImageAlt(block.alt, blockIndex)"
            class="block h-auto w-full bg-[var(--color-surface-secondary)]"
            v-bind="radiusBind(block.radius)"
            loading="lazy"
            decoding="async"
          />
        </button>
      </div>

      <!-- ===================== 多图宫格 ===================== -->
      <div
        v-else-if="block.type === 'IMAGE_GRID'"
        class="grid w-full"
        :style="gridStyle(block)"
      >
        <button
          v-for="(image, imageIndex) in block.images"
          :key="`${image.url}-${imageIndex}`"
          type="button"
          class="block cursor-zoom-in"
          :style="itemMarginStyle(block.itemMargin)"
          :aria-label="t('product.imageEnlarge')"
          @click="openLightbox(block.images, imageIndex, $event)"
        >
          <ShopImage
            :src="image.url"
            :alt="detailImageAlt(image.alt, imageIndex)"
            class="block h-auto w-full"
            v-bind="radiusBind(block.radius)"
            loading="lazy"
            decoding="async"
          />
        </button>
      </div>

      <!-- ===================== 图文 ===================== -->
      <div
        v-else-if="block.type === 'IMAGE_TEXT'"
        class="flex flex-col gap-4 md:flex-row md:items-start md:gap-6"
        :class="block.layout === 'right' ? 'md:flex-row-reverse' : ''"
        :style="spacingStyle(block)"
      >
        <div class="w-full md:w-[45%] md:shrink-0">
          <button
            type="button"
            class="block w-full cursor-zoom-in"
            :aria-label="t('product.imageEnlarge')"
            @click="openLightbox([{ url: block.url, alt: block.alt }], 0, $event)"
          >
            <ShopImage
              :src="block.url"
              :alt="detailImageAlt(block.alt, blockIndex)"
              class="block h-auto w-full bg-[var(--color-surface-secondary)]"
              v-bind="radiusBind(block.radius)"
              loading="lazy"
              decoding="async"
            />
          </button>
        </div>
        <div class="min-w-0 flex-1">
          <h3 v-if="block.title" class="text-base font-bold text-[var(--color-ink)]">
            {{ block.title }}
          </h3>
          <p
            v-for="(line, lineIndex) in textLines(block.text)"
            :key="`${blockIndex}-line-${lineIndex}`"
            class="mt-2 text-[15px] leading-relaxed text-[var(--color-ink-soft)] [overflow-wrap:anywhere]"
          >
            {{ line }}
          </p>
        </div>
      </div>

      <!-- ===================== 文本段落 ===================== -->
      <div v-else-if="block.type === 'TEXT'" class="space-y-2" :style="spacingStyle(block)">
        <h3 v-if="block.title" class="text-base font-bold text-[var(--color-ink)]">
          {{ block.title }}
        </h3>
        <p
          v-for="(line, lineIndex) in textLines(block.text)"
          :key="`${blockIndex}-line-${lineIndex}`"
          class="text-[15px] leading-relaxed text-[var(--color-ink-soft)] [overflow-wrap:anywhere]"
        >
          {{ line }}
        </p>
      </div>

      <!-- ===================== 富文本（渲染前清洗） ===================== -->
      <div
        v-else-if="block.type === 'RICH_TEXT'"
        class="text-[15px] leading-relaxed text-[var(--color-ink-soft)] [&_a]:text-[var(--color-brand-600)] [&_a]:underline-offset-2 [&_blockquote]:border-l-2 [&_blockquote]:border-[var(--color-brand-300)] [&_blockquote]:pl-3 [&_blockquote]:text-[var(--color-muted)] [&_h2]:mt-5 [&_h2]:text-base [&_h2]:font-bold [&_h3]:mt-5 [&_h3]:font-bold [&_h4]:mt-4 [&_h4]:font-bold [&_hr]:my-4 [&_hr]:border-[var(--color-line-soft)] [&_img]:my-4 [&_img]:h-auto [&_img]:w-full [&_li]:ml-5 [&_li]:list-disc [&_ol]:list-decimal [&_p]:mb-4 [&_table]:my-4 [&_table]:block [&_table]:w-full [&_table]:overflow-x-auto [&_td]:border [&_td]:border-[var(--color-line)] [&_td]:px-2 [&_td]:py-1 [&_th]:border [&_th]:border-[var(--color-line)] [&_th]:px-2 [&_th]:py-1"
        :style="spacingStyle(block)"
        v-html="sanitizeHtml(block.html)"
      ></div>

      <!-- ===================== 视频 ===================== -->
      <div
        v-else-if="block.type === 'VIDEO' && (videoFrameSrc(block) || videoFileSrc(block))"
        :style="spacingStyle(block)"
      >
        <div v-if="videoFrameSrc(block)" class="overflow-hidden rounded-[var(--radius-tile)] bg-black">
          <div class="relative aspect-video w-full">
            <iframe
              :src="videoFrameSrc(block) ?? undefined"
              :title="videoTitle(block.provider)"
              class="absolute inset-0 h-full w-full border-0"
              loading="lazy"
              referrerpolicy="no-referrer"
              allowfullscreen
              allow="accelerometer; encrypted-media; gyroscope; picture-in-picture; fullscreen"
            ></iframe>
          </div>
        </div>
        <video
          v-else
          :src="videoFileSrc(block) ?? undefined"
          :poster="block.poster || undefined"
          class="h-auto w-full rounded-[var(--radius-tile)] bg-black"
          controls
          preload="metadata"
          playsinline
        >
          {{ t('product.videoUnsupported') }}
        </video>
      </div>

      <!-- ===================== 小节标题 ===================== -->
      <div v-else-if="block.type === 'TITLE'" :style="spacingStyle(block)">
        <h3 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
          <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
          {{ block.text }}
        </h3>
        <p v-if="block.sub" class="mt-1.5 text-sm text-[var(--color-muted)]">
          {{ block.sub }}
        </p>
      </div>

      <!-- ===================== 分隔线 ===================== -->
      <hr
        v-else-if="block.type === 'DIVIDER'"
        class="border-t border-[var(--color-line-soft)]"
        :style="spacingStyle(block)"
      >
    </template>

    <!-- 灯箱：每个模块的图片集合是一条独立的浏览序列（多图模块视为一个序列） -->
    <ProductImageLightbox
      :open="lightboxOpen"
      :images="lightboxImages"
      :start-index="lightboxStartIndex"
      :title="title"
      @close="closeLightbox"
    />
  </div>
</template>

<script setup lang="ts">
import type {
  StoreDetailBlock,
  StoreDetailBlockImageGrid,
  StoreDetailBlockVideo,
  StoreDetailGridItemMargin,
  StoreDetailImage,
} from '~~/types/shop-product'
import { resolveDetailVideoSource } from '~~/utils/product-detail-media'
import { sanitizeHtml } from '~~/utils/sanitize-html'
import ProductImageLightbox from './ProductImageLightbox.vue'

const props = defineProps<{
  /** 已归一化、已排序的详情模块 */
  blocks: StoreDetailBlock[]
  /** 商品标题：图片 alt 兜底（契约 4.4） */
  title: string
}>()

const { t } = useI18n()

// ===== 布局：全部内联输出（契约 1.4 / 4.3） =====
//
// 注意：这里刻意不用 Tailwind 的间距/圆角类 —— 类名是"统一默认值"，
// 而本轮需求的默认就是 0，必须让每个模块的 4 个 margin 与图片圆角都能独立为 0 且不残留留白。

/** 模块最外层内联 margin：`<top>px <right>px <bottom>px <left>px`（默认 0px 也显式输出，便于排查） */
function spacingStyle(block: StoreDetailBlock): Record<string, string> {
  return {
    margin: `${block.marginTop}px ${block.marginRight}px ${block.marginBottom}px ${block.marginLeft}px`,
  }
}

/**
 * 图片圆角：0（默认）时**什么都不绑定** → 元素上不出现 style / border-radius（直角）；
 * >0 时才输出 `style="border-radius:Npx"`。
 * 用 v-bind 的对象透传给 ShopImage（它会把属性放到根 img/占位元素上），
 * 这样 0 时连空的 `style=""` 都不会留下。
 */
function radiusBind(radius: number): Record<string, string> {
  return radius > 0 ? { style: `border-radius:${radius}px` } : {}
}

/** 宫格列数（1–6）：契约要求 repeat(columns, minmax(0, 1fr))，移动端不横向溢出 */
function gridTemplateColumns(columns: number): string {
  const safeColumns = Math.min(Math.max(Math.trunc(columns) || 1, 1), 6)
  return `repeat(${safeColumns}, minmax(0, 1fr))`
}

/** 宫格容器：模块间距 + 列模板（两者都走内联，容器本身不加 gap） */
function gridStyle(block: StoreDetailBlockImageGrid): Record<string, string> {
  return {
    ...spacingStyle(block),
    gridTemplateColumns: gridTemplateColumns(block.columns),
  }
}

/** 宫格每格内联 margin：相邻两图水平间距 = 左格 right + 右格 left（缺省 0 时上下/左右紧贴） */
function itemMarginStyle(itemMargin: StoreDetailGridItemMargin): Record<string, string> {
  return {
    margin: `${itemMargin.top}px ${itemMargin.right}px ${itemMargin.bottom}px ${itemMargin.left}px`,
  }
}

// ===== 灯箱状态（父组件持有，关闭后把焦点还给触发图片） =====
const lightboxOpen = ref(false)
const lightboxImages = ref<StoreDetailImage[]>([])
const lightboxStartIndex = ref(0)
/** 触发灯箱的按钮：关闭后焦点回到它（键盘用户不会"丢失位置"） */
const lightboxTrigger = ref<HTMLElement | null>(null)

function openLightbox(images: StoreDetailImage[], startIndex: number, event: MouseEvent) {
  if (!images.length) return
  lightboxImages.value = images
  lightboxStartIndex.value = startIndex
  lightboxTrigger.value = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  lightboxOpen.value = true
}

function closeLightbox() {
  lightboxOpen.value = false
  const trigger = lightboxTrigger.value
  lightboxTrigger.value = null
  nextTick(() => trigger?.focus())
}

// ===== 文案 / 取值辅助 =====
/** 图片 alt 兜底：模块没写 alt 时用「商品标题 + 序号」 */
function detailImageAlt(alt: string | null, imageIndex: number): string {
  return alt || t('product.detailImageAlt', { title: props.title, index: imageIndex + 1 })
}

/** TEXT 按 \n 分段；空行不产生空段落 */
function textLines(text: string | null | undefined): string[] {
  if (!text) return []
  return text
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line.length > 0)
}

/** iframe 地址：解析不出 id 返回 null（模板据此整块不渲染） */
function videoFrameSrc(block: StoreDetailBlockVideo): string | null {
  const source = resolveDetailVideoSource(block.provider, block.url)
  return source?.kind === 'iframe' ? source.src : null
}

/** 直链视频地址：非法/解析不出返回 null */
function videoFileSrc(block: StoreDetailBlockVideo): string | null {
  const source = resolveDetailVideoSource(block.provider, block.url)
  return source?.kind === 'file' ? source.src : null
}

function videoTitle(provider: StoreDetailBlockVideo['provider']): string {
  if (provider === 'bilibili') return t('product.videoBilibiliTitle')
  if (provider === 'youtube') return t('product.videoYoutubeTitle')
  return t('product.videoFileTitle')
}
</script>
