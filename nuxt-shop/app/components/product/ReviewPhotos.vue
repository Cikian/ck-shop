<!--
  app/components/product/ReviewPhotos.vue

  评价里的「买家秀」图片：缩略图宫格 + 点击放大（复用商品详情的灯箱）。

  为什么单独拆一个组件：
    · 评价图片在**两个地方**出现 —— 正文预览的 3 条评价、左侧全量抽屉里的每一条；
      两处必须同一套尺寸/圆角/alt/放大行为，否则同一条评价在正文和抽屉里长得不一样；
    · 灯箱（ProductImageLightbox）是"受控 + Teleport 到 body"的浮层，
      由本组件持有开关状态，调用方不必各自维护一份。

  两条约束：
    1) **图片区只在有图时渲染**（images 空数组 → 整个 div 不存在，不留空容器）；
    2) 预览最多露 3 张，多出来的用最后一张上的 "+N" 角标提示，点击仍然放大**全部**图片。
-->
<template>
  <div v-if="images.length" ref="rootEl" class="flex flex-wrap gap-2">
    <button
      v-for="(src, index) in visibleImages"
      :key="`${src}-${index}`"
      type="button"
      class="group relative cursor-pointer overflow-hidden rounded-[var(--radius-card-sm)] bg-[var(--color-surface-secondary)] ring-1 ring-[var(--color-line)] transition-shadow duration-200 hover:shadow-[var(--shadow-md)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-400)] motion-reduce:transition-none"
      :class="variant === 'drawer'
        ? 'aspect-square w-[calc((100%-1rem)/3)] sm:aspect-auto sm:h-[132px] sm:w-[132px]'
        : 'h-[76px] w-[76px]'"
      :aria-label="thumbLabel(index)"
      @click="openAt(index)"
    >
      <!-- 加载中：骨架铺满方块（图片 10KB 级别，一般一闪而过；但慢网下不能是"空气泡"） -->
      <span
        v-if="!loadedMap[`${src}-${index}`]"
        class="skeleton absolute inset-0"
        aria-hidden="true"
      ></span>

      <!--
        只有"接近视口"时才真的挂 <img>（见 script 的 inRange）：
        评价区在详情页很靠下，一屏 8~10 张图如果和首屏 JS 一起抢连接，
        会把水合拖到好几秒（dev 实测 8.0s → 1.6s）。
      -->
      <ShopImage
        v-if="inRange"
        :src="src"
        :alt="t('review.photoAlt', { index: index + 1 })"
        :width="variant === 'drawer' ? 180 : 152"
        fetchpriority="low"
        sizes="(max-width: 640px) 30vw, 180px"
        class="relative h-full w-full object-cover transition-[transform,opacity] duration-300 group-hover:scale-[1.04] motion-reduce:transition-none"
        :class="loadedMap[`${src}-${index}`] ? 'opacity-100' : 'opacity-0'"
        @load="markLoaded(`${src}-${index}`)"
        @error="markLoaded(`${src}-${index}`)"
      />

      <!-- 预览态：被折叠的图片数量（"还有 N 张"），点击进入灯箱可逐张翻 -->
      <span
        v-if="hiddenCount > 0 && index === visibleImages.length - 1"
        class="absolute inset-0 grid place-items-center bg-[var(--color-ink)]/50 text-sm font-bold text-white"
        aria-hidden="true"
      >
        +{{ hiddenCount }}
      </span>
    </button>

    <!-- 灯箱：序列是**该条评价的全部图片**，不是只放大点中的那一张 -->
    <ProductImageLightbox
      :open="lightboxOpen"
      :images="lightboxImages"
      :start-index="lightboxIndex"
      :title="title"
      @close="closeLightbox"
    />
  </div>
</template>

<script setup lang="ts">
import type { StoreDetailImage } from '~~/types/shop-product'
import ProductImageLightbox from './ProductImageLightbox.vue'
import ShopImage from '../ShopImage.vue'

const props = withDefaults(defineProps<{
  /** 该条评价的图片（已归一化：http(s) / 站内路径，最多 9 张） */
  images: string[]
  /** 商品标题：灯箱里图片的 alt 兜底 */
  title: string
  /** 展示形态：drawer = 抽屉里的 3 列宫格；preview = 正文预览的固定小方块 */
  variant?: 'preview' | 'drawer'
  /** 预览态最多露几张（抽屉里恒为全部） */
  previewLimit?: number
}>(), {
  variant: 'drawer',
  previewLimit: 3,
})

const emit = defineEmits<{ open: []; close: [] }>()

const { t } = useI18n()

/**
 * 图片列表的兜底：`images` 在类型上是必填数组，但**旧缓存的 payload**（SWR 30s、
 * 或升级前生成的页面快照）里可能没有这个字段。少一个字段不该让整条评价渲染崩掉，
 * 这里统一按空数组处理——没有图就不渲染图片区。
 */
const images = computed(() => props.images ?? [])

/** 预览态只露前 N 张（其余折叠成 +N 角标）；抽屉里全部展示 */
const visibleImages = computed(() => {
  if (props.variant !== 'preview') return images.value
  return images.value.slice(0, props.previewLimit)
})

const hiddenCount = computed(() => Math.max(images.value.length - visibleImages.value.length, 0))

/**
 * 放大序列 = 该条评价的全部图片。
 * alt 用 i18n 拼（"买家秀图片 N"），不带商品名：灯箱打开的是某条评价的配图，
 * 商品名已经在上一层的标题里了，重复读一遍反而吵。
 */
const lightboxImages = computed<StoreDetailImage[]>(() =>
  images.value.map((url, index) => ({ url, alt: t('review.photoAlt', { index: index + 1 }) })),
)

const lightboxOpen = ref(false)
const lightboxIndex = ref(0)

/**
 * 每张图是否已经解码完成（key = 图片地址 + 下标）。
 *
 * 为什么要这层状态：
 *   · 加载中显示 `.skeleton` 骨架，而不是一个空白方块；
 *   · 图片本身 `opacity-0` 直到 load 完成，避免"半张图 + 原生裂图图标"这种脏状态
 *     （浏览器在 src 还没就绪时会把 alt 文字画出来，看起来就像图片坏了）。
 */
const loadedMap = ref<Record<string, boolean>>({})

function markLoaded(key: string) {
  loadedMap.value = { ...loadedMap.value, [key]: true }
}

function thumbLabel(index: number): string {
  if (hiddenCount.value > 0 && index === visibleImages.value.length - 1) {
    return t('review.photoMore', { count: hiddenCount.value })
  }
  return t('review.photoOpen', { index: index + 1 })
}

function openAt(index: number) {
  // 折叠的图片从"被点的那张"开始看，随后可以左右翻到全部
  lightboxIndex.value = index
  lightboxOpen.value = true
  emit('open')
}

function closeLightbox() {
  lightboxOpen.value = false
  emit('close')
}

// ===== 视口内才挂 <img>（首屏不抢连接） =====
/**
 * 评价区在详情页很靠下（首屏之后 3000px 以上）。如果这些图在页面加载时就发请求，
 * 它们会和首屏 JS 抢同一个域名的连接，把水合推到好几秒 ——
 * 实测（dev，1440×900）：图片全开 hydration 8.0s / 屏蔽图片 1.4s。
 *
 * 所以这里用 IntersectionObserver 做一个"接近视口（下方 600px 内）才挂 <img>"的闸门：
 *   · SSR 与首屏：只有骨架，不发任何图片请求；
 *   · 用户往下滚到评价区前 600px：一次性放行（之后不再断开，避免来回抖动）。
 * 抽屉里的图同理：抽屉打开时才挂载，用户滚动时自然进入视口。
 */
const rootEl = ref<HTMLElement | null>(null)
const inRange = ref(false)
let io: IntersectionObserver | null = null

onMounted(() => {
  if (!import.meta.client) return
  // 没有 IO 的旧环境直接放行，别把图片永远藏起来
  if (typeof IntersectionObserver === 'undefined' || !rootEl.value) {
    inRange.value = true
    return
  }
  io = new IntersectionObserver((entries) => {
    if (entries.some(entry => entry.isIntersecting)) {
      inRange.value = true
      io?.disconnect()
      io = null
    }
  }, { rootMargin: '600px 0px' })
  io.observe(rootEl.value)
})

onBeforeUnmount(() => {
  io?.disconnect()
  io = null
})
</script>
