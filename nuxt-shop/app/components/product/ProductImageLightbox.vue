<!--
  商品详情图片灯箱（契约 4.3 灯箱 / 4.4 无障碍）

  职责：遮罩 + 图片居中 + 左右切换 + 关闭；Esc / ← / → 键、锁定 body 滚动、
  移动端点遮罩关闭、按钮 aria-label；关闭后的焦点回到触发图片由父组件负责
  （触发按钮在父组件里，本组件只管发 close 事件）。

  为什么要 Teleport + v-if="open"：
    open 只在用户点击后变 true，SSR 阶段恒为 false —— 服务端不会有任何遮罩节点，
    既不会影响首屏 HTML，也不会出现"水合前后 DOM 不一致"。

  受控程度：open 受控（父组件决定开关），当前下标在打开时由 startIndex 初始化，
  切换由本组件内部维护（父组件不需要知道翻到了第几张）。
-->
<template>
  <Teleport v-if="open" to="body">
    <div
      class="fixed inset-0 z-[100] flex flex-col"
      role="dialog"
      aria-modal="true"
      :aria-label="t('product.imageLightboxLabel')"
    >
      <!-- 遮罩：点击关闭。单独一层 absolute，避免被内容层挡住点击 -->
      <div class="absolute inset-0 bg-black/85" aria-hidden="true" @click="requestClose"></div>

      <!-- 内容层 pointer-events-none：空白处点击穿透到遮罩；图片/按钮单独打开事件 -->
      <div class="relative flex h-full flex-col pointer-events-none">
        <div class="relative flex min-h-0 flex-1 items-center justify-center px-3 pb-4 pt-16 sm:px-6">
          <img
            v-if="currentImage"
            :src="currentImage.url"
            :alt="currentAlt"
            class="pointer-events-auto max-h-[76dvh] max-w-full rounded-[var(--radius-tile)] object-contain shadow-[var(--shadow-2xl)] select-none sm:max-h-[82dvh]"
            decoding="async"
            @click.stop
          >

          <!-- 关闭 -->
          <button
            ref="closeButton"
            type="button"
            class="pointer-events-auto absolute right-2 top-3 z-10 flex h-11 w-11 cursor-pointer items-center justify-center rounded-full bg-white/12 text-white transition-colors hover:bg-white/24 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white motion-reduce:transition-none sm:right-5"
            :aria-label="t('product.lightboxClose')"
            @click.stop="requestClose"
          >
            <UIcon name="lucide:x" class="h-6 w-6" aria-hidden="true" />
          </button>

          <!-- 上一张 / 下一张：只有多图时才出现 -->
          <template v-if="hasMultiple">
            <button
              type="button"
              class="pointer-events-auto absolute left-1 top-1/2 z-10 flex h-11 w-11 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full bg-white/12 text-white transition-colors hover:bg-white/24 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white motion-reduce:transition-none sm:left-4"
              :aria-label="t('product.lightboxPrev')"
              @click.stop="prev"
            >
              <UIcon name="lucide:chevron-left" class="h-6 w-6" aria-hidden="true" />
            </button>
            <button
              type="button"
              class="pointer-events-auto absolute right-1 top-1/2 z-10 flex h-11 w-11 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full bg-white/12 text-white transition-colors hover:bg-white/24 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white motion-reduce:transition-none sm:right-4"
              :aria-label="t('product.lightboxNext')"
              @click.stop="next"
            >
              <UIcon name="lucide:chevron-right" class="h-6 w-6" aria-hidden="true" />
            </button>
          </template>
        </div>

        <!-- 计数：多图时给"现在第几张"一个可读的反馈 -->
        <p v-if="hasMultiple" class="pointer-events-none pb-5 text-center text-xs font-medium tabular text-white/80" aria-live="polite">
          {{ t('product.lightboxCounter', { current: index + 1, total: images.length }) }}
        </p>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import type { StoreDetailImage } from '~~/types/shop-product'

const props = defineProps<{
  /** 是否打开（受控） */
  open: boolean
  /** 当前浏览序列（单图模块长度 1；多图宫格 = 该模块全部图片） */
  images: StoreDetailImage[]
  /** 打开时定位到第几张（0 基） */
  startIndex: number
  /** 商品标题：图片缺 alt 时的兜底文案 */
  title: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { t } = useI18n()

const closeButton = ref<HTMLElement | null>(null)
const index = ref(0)

const hasMultiple = computed(() => props.images.length > 1)
const currentImage = computed<StoreDetailImage | null>(() => props.images[index.value] ?? null)

/** alt 兜底：契约 4.4「图片必须有 alt（缺省用商品标题 + 序号）」 */
const currentAlt = computed(() => {
  const image = currentImage.value
  if (!image) return props.title
  return image.alt || t('product.detailImageAlt', { title: props.title, index: index.value + 1 })
})

function clampIndex(value: number): number {
  if (!props.images.length) return 0
  if (!Number.isFinite(value)) return 0
  return Math.min(Math.max(Math.trunc(value), 0), props.images.length - 1)
}

function prev() {
  if (!hasMultiple.value) return
  index.value = (index.value - 1 + props.images.length) % props.images.length
}

function next() {
  if (!hasMultiple.value) return
  index.value = (index.value + 1) % props.images.length
}

function requestClose() {
  emit('close')
}

// ===== 键盘：Esc 关闭 / ← → 切换（契约 4.3） =====
function onKeydown(event: KeyboardEvent) {
  if (!props.open) return
  if (event.key === 'Escape') {
    event.preventDefault()
    requestClose()
    return
  }
  if (!hasMultiple.value) return
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    prev()
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    next()
  }
}

// ===== 锁定 body 滚动（关闭后恢复原值，避免把页面原本的 overflow 改坏） =====
let bodyLocked = false
let previousOverflow = ''
let previousPaddingRight = ''

function lockBody() {
  if (!import.meta.client || bodyLocked) return
  const body = document.body
  previousOverflow = body.style.overflow
  previousPaddingRight = body.style.paddingRight
  // 滚动条消失会让页面横向跳一下：用 padding 反向补上宽度
  const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth
  body.style.overflow = 'hidden'
  if (scrollbarWidth > 0) body.style.paddingRight = `${scrollbarWidth}px`
  bodyLocked = true
}

function unlockBody() {
  if (!import.meta.client || !bodyLocked) return
  const body = document.body
  body.style.overflow = previousOverflow
  body.style.paddingRight = previousPaddingRight
  bodyLocked = false
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      index.value = clampIndex(props.startIndex)
      lockBody()
      // 把焦点移进对话框：否则读屏/键盘用户还停在背后的页面上
      nextTick(() => closeButton.value?.focus())
    } else {
      unlockBody()
    }
  },
  { immediate: true },
)

// 打开状态下数据变了（例如图片被异步替换）：保持下标合法；序列清空则直接关闭
watch(
  () => props.images.length,
  () => {
    if (!props.open) return
    if (!props.images.length) {
      requestClose()
      return
    }
    index.value = clampIndex(index.value)
  },
)

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  unlockBody()
})
</script>
