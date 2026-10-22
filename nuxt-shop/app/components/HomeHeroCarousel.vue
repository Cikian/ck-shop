<template>
  <!--
    首页首屏大轮播（设计稿首屏右栏）。
    图片全部来自后端真实轮播图（MongoDB，/api/home/slide），没有数据时给中性占位，
    不会出现"编一张 banner 出来"的情况。
  -->
  <section
    class="group relative overflow-hidden rounded-[var(--radius-card)] bg-[var(--color-surface-secondary)] shadow-[var(--shadow-card)]"
    :aria-label="t('home.newCollection')"
    aria-roledescription="carousel"
    @mouseenter="hoverPaused = true"
    @mouseleave="hoverPaused = false"
    @focusin="focusPaused = true"
    @focusout="onFocusOut"
    @keydown.left.stop="goPrev"
    @keydown.right.stop="goNext"
  >
    <div
      class="relative h-[220px] sm:h-[280px] lg:h-[320px]"
      @touchstart.passive="onTouchStart"
      @touchend.passive="onTouchEnd"
    >
      <!-- 真实轮播图：多个 slide 时横向平移切换，只有一个时不渲染切换动画 -->
      <div
        v-if="slides.length"
        class="flex h-full transition-transform duration-500 ease-out motion-reduce:transition-none"
        :style="{ transform: `translateX(-${currentIndex * 100}%)` }"
      >
        <!--
          非当前 slide 用 inert + aria-hidden 移出可交互树：
          它们只是被 translateX 移出可视区，DOM 仍在，否则键盘 Tab 与读屏会
          命中看不见的横幅链接（这正是 tabs/carousel 最常见的一类 a11y bug）。
        -->
        <component
          :is="slide.targetUrl ? 'a' : 'div'"
          v-for="(slide, index) in slides"
          :key="slide._id || index"
          :href="slide.targetUrl || undefined"
          :target="slide.targetUrl ? '_blank' : undefined"
          :rel="slide.targetUrl ? 'noopener noreferrer' : undefined"
          class="relative h-full w-full shrink-0"
          :inert="index === currentIndex ? undefined : true"
          :aria-hidden="index === currentIndex ? undefined : 'true'"
        >
          <ShopImage
            :src="slide.pic_url"
            :alt="slide.title || `${t('home.newCollection')} ${index + 1}`"
            width="1200"
            height="500"
            :loading="index === 0 ? 'eager' : 'lazy'"
            :fetchpriority="index === 0 ? 'high' : 'auto'"
            class="h-full w-full object-cover"
            icon-class="h-10 w-10"
          />
          <!--
            底部渐变 + 文案：图片本身是纯照片，标题来自后端真实 title。
            title 为空时不叠任何文字，避免出现一条没有内容的黑色渐变带。
          -->
          <div
            v-if="slide.title"
            class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/55 via-black/20 to-transparent p-5 sm:p-7"
          >
            <p class="text-xl font-black leading-tight text-white drop-shadow-sm sm:text-3xl">
              {{ slide.title }}
            </p>
          </div>
        </component>
      </div>

      <!-- 没有轮播数据：中性占位（品牌浅色底 + 图标），不留一块空白 -->
      <div v-else class="brand-gradient-soft flex h-full flex-col items-center justify-center gap-3 text-center">
        <UIcon name="lucide:image-off" class="h-10 w-10 text-[var(--color-brand-300)]" aria-hidden="true" />
        <p class="text-sm font-medium text-[var(--color-muted)]">{{ t('home.newCollection') }}</p>
      </div>
    </div>

    <!-- 左右切换：只有一张图时不渲染箭头（点了也没变化，就是假按钮） -->
    <template v-if="slides.length > 1">
      <button
        type="button"
        class="absolute left-3 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-white/85 text-[var(--color-ink)] shadow-[var(--shadow-card)] transition-colors hover:bg-white"
        :aria-label="t('products.prev')"
        @click="goPrev"
      >
        <UIcon name="lucide:chevron-left" class="h-5 w-5" aria-hidden="true" />
      </button>
      <button
        type="button"
        class="absolute right-3 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-white/85 text-[var(--color-ink)] shadow-[var(--shadow-card)] transition-colors hover:bg-white"
        :aria-label="t('products.next')"
        @click="goNext"
      >
        <UIcon name="lucide:chevron-right" class="h-5 w-5" aria-hidden="true" />
      </button>

      <!-- 圆点指示器 -->
      <div class="absolute bottom-3 left-1/2 flex -translate-x-1/2 items-center gap-1.5">
        <button
          v-for="(slide, index) in slides"
          :key="`dot-${slide._id || index}`"
          type="button"
          class="h-1.5 rounded-full transition-all duration-300"
          :class="index === currentIndex ? 'w-5 bg-[var(--color-brand-500)]' : 'w-1.5 bg-white/75 hover:bg-white'"
          :aria-label="`${index + 1} / ${slides.length}`"
          :aria-current="index === currentIndex"
          @click="goTo(index); restartPhase()"
        />
      </div>

      <!--
        手动暂停/继续：WCAG 2.2.2 要求 >5s 的自动更新内容可暂停。
        触摸设备没有 hover，读屏/键盘用户也需要一个明确入口，所以做成常驻按钮。
      -->
      <button
        v-if="!reducedMotion"
        type="button"
        class="absolute bottom-3 right-3 flex h-7 w-7 items-center justify-center rounded-full bg-white/85 text-[var(--color-ink)] shadow-[var(--shadow-card)] transition-colors hover:bg-white"
        :aria-label="userPaused ? t('home.playCarousel') : t('home.pauseCarousel')"
        :aria-pressed="userPaused"
        @click="userPaused = !userPaused"
      >
        <UIcon :name="userPaused ? 'lucide:play' : 'lucide:pause'" class="h-3.5 w-3.5" aria-hidden="true" />
      </button>
    </template>
  </section>
</template>

<script setup lang="ts">
import type { SlideItem } from '@@/server/api/home/slide.get'

/**
 * 首屏轮播。
 *
 * 自动播放的约束（"别让动效变成负担"）：
 *   1. 鼠标悬停 / 键盘聚焦 / **页面不可见** / 用户手动暂停时都停表 ——
 *      只处理 hover 的话，切到后台标签页定时器还在跑，回来看就是另一张图了；
 *   2. prefers-reduced-motion 下**完全不自动播放**，只保留手动切换；
 *   3. 组件卸载必须清定时器与全局监听；
 *   4. 手动切图（箭头/圆点/滑动/方向键）后**重置相位**：
 *      旧定时器的相位残留会在 0.1s 后再切一张，表现为"点一下跳两张"。
 */
const props = defineProps<{
  slides: SlideItem[]
}>()

const { t } = useI18n()

const AUTOPLAY_MS = 5000

const currentIndex = ref(0)
/** 暂停原因分开记：任一成立就不自动播，避免 hover 暂停被 focusout 意外恢复 */
const hoverPaused = ref(false)
const focusPaused = ref(false)
const userPaused = ref(false)
const documentHidden = ref(false)
const reducedMotion = ref(false)

let timer: ReturnType<typeof setInterval> | null = null
let motionQuery: MediaQueryList | null = null

const slides = computed(() => props.slides || [])

/**
 * 首图 preload：hero 是 LCP 元素，等 HTML 解析到 <img> 才开始下载就晚了。
 * imagesrcset/imagesizes 必须与 ShopImage 生成的候选完全一致（width=1200 → 1200w/1600w），
 * 否则浏览器会当成两张图，preload 白做还多下一次。
 */
useHead(() => {
  const first = slides.value[0]
  const pic = first?.pic_url
  if (!pic || !/^https?:\/\//i.test(pic)) {
    return {}
  }
  const src = encodeURIComponent(pic)
  return {
    link: [
      {
        rel: 'preload',
        as: 'image',
        href: `/api/img?src=${src}&w=1200`,
        imagesrcset: `/api/img?src=${src}&w=1200 1200w, /api/img?src=${src}&w=1600 1600w`,
        imagesizes: '100vw',
        fetchpriority: 'high',
      },
    ],
  }
})

/** 全部暂停条件都放这里：watch 它即可自动启停定时器 */
const canAutoplay = computed(() =>
  slides.value.length > 1
  && !hoverPaused.value
  && !focusPaused.value
  && !userPaused.value
  && !documentHidden.value
  && !reducedMotion.value,
)

function stopTimer() {
  if (timer === null) return
  clearInterval(timer)
  timer = null
}

function startTimer() {
  stopTimer()
  if (!canAutoplay.value) return
  // 定时器只做"自动前进"：复用 goNext 会让每次自动切换都重启定时器（无意义的重建）
  timer = setInterval(() => {
    const total = slides.value.length
    if (!total) return
    currentIndex.value = (currentIndex.value + 1) % total
  }, AUTOPLAY_MS)
}

/** 手动切换后从 0 重新计时（只在允许自动播时生效） */
function restartPhase() {
  if (canAutoplay.value) startTimer()
}

function goTo(index: number) {
  const total = slides.value.length
  if (!total) return
  currentIndex.value = (index + total) % total
}

function goPrev() {
  goTo(currentIndex.value - 1)
  restartPhase()
}

function goNext() {
  goTo(currentIndex.value + 1)
  restartPhase()
}

// 暂停条件任何一项变化都会自动启停；数据到达后长度变化也在 canAutoplay 里
watch(canAutoplay, (value) => {
  if (value) startTimer()
  else stopTimer()
})

// 某张 slide 被后端移除后索引可能越界
watch(() => slides.value.length, () => {
  if (currentIndex.value >= slides.value.length) currentIndex.value = 0
})

/** 焦点真正离开整个轮播才恢复自动播（在圆点之间移动焦点不算离开） */
function onFocusOut(event: FocusEvent) {
  const next = event.relatedTarget
  if (next instanceof Node && (event.currentTarget as HTMLElement | null)?.contains(next)) return
  focusPaused.value = false
}

function onVisibilityChange() {
  documentHidden.value = document.hidden
}

function onMotionChange(event: MediaQueryListEvent) {
  reducedMotion.value = event.matches
}

// ---------------------------------------------------------------------------
// 触摸滑动（手写 touch，不引新依赖）：横向位移 > 40px 且明显大于纵向才算翻页
// ---------------------------------------------------------------------------
let touchStartX = 0
let touchStartY = 0

function onTouchStart(event: TouchEvent) {
  const touch = event.touches[0]
  if (!touch) return
  touchStartX = touch.clientX
  touchStartY = touch.clientY
}

function onTouchEnd(event: TouchEvent) {
  const touch = event.changedTouches[0]
  if (!touch || slides.value.length <= 1) return
  const dx = touch.clientX - touchStartX
  const dy = touch.clientY - touchStartY
  if (Math.abs(dx) < 40 || Math.abs(dx) <= Math.abs(dy)) return
  if (dx < 0) goNext()
  else goPrev()
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.matches
  motionQuery.addEventListener('change', onMotionChange)
  document.addEventListener('visibilitychange', onVisibilityChange)
  documentHidden.value = document.hidden
  startTimer()
})

onUnmounted(() => {
  stopTimer()
  motionQuery?.removeEventListener('change', onMotionChange)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  motionQuery = null
})
</script>
