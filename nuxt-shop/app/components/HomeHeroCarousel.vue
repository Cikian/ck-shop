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
    @mouseenter="pause" @mouseleave="resume" @focusin="pause" @focusout="resume"
  >
    <div class="relative h-[220px] sm:h-[280px] lg:h-[320px]">
      <!-- 真实轮播图：多个 slide 时横向平移切换，只有一个时不渲染切换动画 -->
      <div
        v-if="slides.length"
        class="flex h-full transition-transform duration-500 ease-out motion-reduce:transition-none"
        :style="{ transform: `translateX(-${currentIndex * 100}%)` }"
      >
        <component
          :is="slide.targetUrl ? 'a' : 'div'"
          v-for="(slide, index) in slides"
          :key="slide._id || index"
          :href="slide.targetUrl || undefined"
          :target="slide.targetUrl ? '_blank' : undefined"
          :rel="slide.targetUrl ? 'noopener noreferrer' : undefined"
          class="relative h-full w-full shrink-0"
        >
          <img
            :src="slide.pic_url"
            :alt="slide.title || ''"
            width="1200"
            height="500"
            class="h-full w-full object-cover"
            :loading="index === 0 ? 'eager' : 'lazy'"
            :fetchpriority="index === 0 ? 'high' : undefined"
          >
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
          @click="goTo(index)"
        />
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import type { SlideItem } from '@@/server/api/home/slide.get'

/**
 * 首屏轮播。
 *
 * 自动播放的三条约束（都是"别让动效变成负担"）：
 *   1. 鼠标悬停 / 键盘聚焦时暂停 —— 用户正在看的时候图不该自己跑掉；
 *   2. prefers-reduced-motion 下**完全不自动播放**，只保留手动切换；
 *   3. 组件卸载必须清定时器，否则首页切走之后定时器还在跑并写已卸载组件的状态。
 */
const props = defineProps<{
  slides: SlideItem[]
}>()

const { t } = useI18n()

const AUTOPLAY_MS = 5000

const currentIndex = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const slides = computed(() => props.slides || [])

function goTo(index: number) {
  const total = slides.value.length
  if (!total) return
  currentIndex.value = (index + total) % total
}

/**
 * 手动「上一张 / 下一张」。
 *
 * 切完之后**必须重新计时**：定时器是按"上一次自动切换"起算的，
 * 用户在第 4.9 秒点了「下一张」，旧的定时器会在 0.1 秒后再切一张 ——
 * 表现为"点一下跳两张"，像按钮坏了。
 */
function goPrev() {
  goTo(currentIndex.value - 1)
  restartAutoplay()
}

function goNext() {
  goTo(currentIndex.value + 1)
  restartAutoplay()
}

/**
 * 重新开始自动播放计时（只在**正在自动播放**时生效）。
 *
 * 为什么加 timer === null 的判断：悬停 / 聚焦时 pause() 已经把定时器停掉了，
 * 此时手动切换如果无条件 startTimer()，就等于在用户正看着的时候偷偷恢复自动播放，
 * 直接违反本组件第 1 条约束（"用户正在看的时候图不该自己跑掉"）。
 */
function restartAutoplay() {
  if (timer === null) return
  startTimer()
}

function prefersReducedMotion(): boolean {
  if (!import.meta.client) return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function stopTimer() {
  if (timer === null) return
  clearInterval(timer)
  timer = null
}

function startTimer() {
  stopTimer()
  if (slides.value.length <= 1 || prefersReducedMotion()) return
  // 定时器只做"自动前进"，不走 goNext()：goNext 还要负责重新计时，
  // 若定时器复用它会变成每 5 秒重建一次定时器（无意义的重建）。
  timer = setInterval(() => goTo(currentIndex.value + 1), AUTOPLAY_MS)
}

function pause() {
  stopTimer()
}

function resume() {
  startTimer()
}

onMounted(startTimer)
onUnmounted(stopTimer)

// 后端数据到达后（或语言切换导致 slide 数量变化）重新评估要不要自动播放
watch(() => slides.value.length, () => startTimer())
</script>
