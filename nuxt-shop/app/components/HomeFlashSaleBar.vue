<template>
  <!--
    促销 / 秒杀条（可承载多条进行中的活动）。

    为什么是"轮播多条"而不是"只显示第一条"：
    运营同时开了秒杀 + 满减两个活动时，只显示排序第一的会让另一个活动彻底没有曝光。

    四条硬约束（都是"别让动效变成负担"）：
      1. 活动全部过期 / endTime 解析不出 -> **整条不渲染**，页面上不会挂死倒计时；
      2. 鼠标悬停 / 键盘聚焦 / 页面不可见 / 用户手动暂停时暂停轮播；
      3. prefers-reduced-motion 下**不自动轮播**，只保留手动点圆点；
      4. 手动切活动后重置轮播相位 —— 旧定时器残留会让用户点完 0.1s 又被切走。
  -->
  <section
    v-if="active"
    class="relative overflow-hidden rounded-[var(--radius-card)] shadow-[0_8px_24px_-10px_rgba(255,77,109,0.6)]"
    :class="{ 'brand-gradient': !hasBgImage }"
    :style="barStyle"
    :aria-label="title"
    @mouseenter="hoverPaused = true"
    @mouseleave="hoverPaused = false"
    @focusin="focusPaused = true"
    @focusout="onFocusOut"
    @keydown.left.stop="goPrev"
    @keydown.right.stop="goNext"
    @touchstart.passive="onTouchStart"
    @touchend.passive="onTouchEnd"
  >
    <!-- 背景图：叠一层暗色蒙层，否则浅色图片上的白字完全看不清 -->
    <div v-if="hasBgImage" class="absolute inset-0 bg-black/45" aria-hidden="true" />

    <!-- 纯装饰光斑：用品牌渐变时才有；prefers-reduced-motion 下不画 -->
    <span
      v-else
      class="pointer-events-none absolute -right-8 -top-16 h-40 w-40 animate-pulse rounded-full bg-white/15 blur-2xl motion-reduce:hidden"
      aria-hidden="true"
    />

    <div class="relative flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3.5 text-white sm:px-6">
      <!-- 左：活动名 + 多条时的圆点指示器 + 暂停 -->
      <div class="flex items-center gap-3">
        <p class="flex items-center gap-2 text-[15px] font-black sm:text-lg">
          <UIcon name="lucide:zap" class="h-5 w-5 shrink-0 text-[var(--color-accent-400)]" aria-hidden="true" />
          {{ title }}
        </p>

        <div v-if="liveList.length > 1" class="flex items-center gap-1.5">
          <!--
            圆点不用 role="tablist"/"tab"：这里没有 tabpanel、也没有方向键切换，
            误用 ARIA 会让读屏念成"选项卡"却切不出面板。按钮 + aria-current 即可
            （与首页大轮播保持同一套语义）。
          -->
          <button
            v-for="(item, index) in liveList"
            :key="item.id"
            type="button"
            class="h-1.5 rounded-full transition-all duration-300"
            :class="index === currentIndex ? 'w-4 bg-white' : 'w-1.5 bg-white/50 hover:bg-white/80'"
            :aria-label="item.title || t('home.limitedOffer')"
            :aria-current="index === currentIndex"
            @click="goTo(index); restartPhase()"
          />
        </div>

        <!-- 手动暂停/继续：触摸端没有 hover，需要一个明确的控制入口（WCAG 2.2.2） -->
        <button
          v-if="liveList.length > 1 && !reducedMotion"
          type="button"
          class="flex h-6 w-6 items-center justify-center rounded-full bg-white/20 text-white transition-colors hover:bg-white/30"
          :aria-label="userPaused ? t('home.playCarousel') : t('home.pauseCarousel')"
          :aria-pressed="userPaused"
          @click="userPaused = !userPaused"
        >
          <UIcon :name="userPaused ? 'lucide:play' : 'lucide:pause'" class="h-3 w-3" aria-hidden="true" />
        </button>
      </div>

      <!-- 中：倒计时（读当前轮到的活动） -->
      <div class="flex items-center gap-2">
        <span class="text-xs font-bold text-white/80">{{ t('shop.flashSaleEndsIn') }}</span>

        <!--
          数字只在客户端挂载后渲染：
          服务端和客户端各自用 Date.now() 算剩余秒数，跨网络必然差几十到几百毫秒，
          直接参与 SSR 会造成 hydration 文本不一致（Vue 会告警并可能回退重渲染）。
        -->
        <span
          v-if="isMounted"
          class="flex items-center gap-1"
          role="timer"
          :aria-label="`${t('shop.flashSaleEndsIn')} ${countdownText}`"
        >
          <template v-for="(unit, index) in countdownUnits" :key="unit.key">
            <span class="tabular flex h-8 min-w-8 items-center justify-center rounded-[8px] bg-[var(--color-ink)]/25 px-1.5 text-[15px] font-black leading-none">
              {{ unit.value }}
            </span>
            <span v-if="index < countdownUnits.length - 1" class="text-sm font-black text-white/70" aria-hidden="true">:</span>
          </template>
        </span>
        <span v-else class="tabular text-[15px] font-black text-white/70">--:--:--</span>
      </div>

      <!-- 右：只有后端给了落地页才渲染入口，避免做出一个点不动的假按钮 -->
      <NuxtLinkLocale
        v-if="active.linkUrl"
        :to="active.linkUrl"
        class="ui-pill ui-pill-accent h-8 shrink-0 px-4 text-[13px]"
      >
        {{ t('shop.viewAll') }}
        <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5" aria-hidden="true" />
      </NuxtLinkLocale>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ShopActivePromotion } from '@@/server/api/promotions/active.get'

/**
 * 首页促销条。
 *
 * 铁律：**没有任何前端写死的时间**。倒计时完全由活动 endTime 驱动；
 * 活动列表为空时页面（pages/index.vue）已经用 v-if 把整条拦掉了，
 * 这里再做一层"逐条过滤已过期"是为了兜住"接口返回了刚过期的活动"这种情况。
 *
 * 关于 prefers-reduced-motion：倒计时是**信息**不是动效，秒数该跳还得跳；
 * 被裁掉的只是自动轮播与装饰动画。
 */
const props = defineProps<{
  promotions: ShopActivePromotion[]
}>()

const { t } = useI18n()

/** 轮播间隔：比首屏大图轮播略长，让用户有时间读完活动名与倒计时 */
const ROTATE_MS = 6000

const isMounted = ref(false)
const currentIndex = ref(0)
/** 每秒 +1 的"心跳"：既驱动倒计时，也驱动过期活动的过滤（computed 依赖它才会重算） */
const now = ref(Date.now())

// 轮播暂停原因分开记：任一成立就不自动轮播
const hoverPaused = ref(false)
const focusPaused = ref(false)
const userPaused = ref(false)
const documentHidden = ref(false)
const reducedMotion = ref(false)

let tickTimer: ReturnType<typeof setInterval> | null = null
let rotateTimer: ReturnType<typeof setInterval> | null = null
let motionQuery: MediaQueryList | null = null

/** 真的在进行中的活动（接口已过滤，这里再兜一层） */
const liveList = computed(() => {
  const current = now.value
  return (props.promotions || []).filter((promotion) => {
    const end = Date.parse(promotion.endTime)
    if (!Number.isFinite(end) || end <= current) return false
    const start = promotion.startTime ? Date.parse(promotion.startTime) : Number.NaN
    return !Number.isFinite(start) || start <= current
  })
})

/** 当前展示的活动：索引越界时回落到第一条，避免某条活动过期后整条消失 */
const active = computed<ShopActivePromotion | null>(() => {
  const list = liveList.value
  if (!list.length) return null
  return list[Math.min(currentIndex.value, list.length - 1)] ?? null
})

const title = computed(() => active.value?.title?.trim() || t('home.limitedOffer'))

const hasBgImage = computed(() => !!active.value?.bgImage)
const barStyle = computed(() =>
  active.value?.bgImage
    ? {
        backgroundImage: `url(${active.value.bgImage})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }
    : {},
)

function computeRemainingSeconds(): number {
  const end = active.value ? Date.parse(active.value.endTime) : Number.NaN
  if (!Number.isFinite(end)) return 0
  return Math.max(0, Math.floor((end - Date.now()) / 1000))
}

const remainingSeconds = ref(0)

function tick() {
  now.value = Date.now()
  remainingSeconds.value = computeRemainingSeconds()
  // 一条都不剩时停表：留着每秒空转的定时器既浪费，也会让 v-if 反复抖
  if (!liveList.value.length) stopAll()
}

function stopTicking() {
  if (tickTimer === null) return
  clearInterval(tickTimer)
  tickTimer = null
}

function stopRotating() {
  if (rotateTimer === null) return
  clearInterval(rotateTimer)
  rotateTimer = null
}

function stopAll() {
  stopTicking()
  stopRotating()
}

/** 是否允许自动轮播（倒计时不受这里影响，它是信息） */
const canRotate = computed(() =>
  liveList.value.length > 1
  && !hoverPaused.value
  && !focusPaused.value
  && !userPaused.value
  && !documentHidden.value
  && !reducedMotion.value,
)

function startRotating() {
  stopRotating()
  if (!canRotate.value) return
  rotateTimer = setInterval(() => {
    const total = liveList.value.length
    if (!total) return
    currentIndex.value = (currentIndex.value + 1) % total
  }, ROTATE_MS)
}

function startTicking() {
  stopTicking()
  tickTimer = setInterval(tick, 1000)
}

/** 手动切活动后从 0 重新计时（旧相位可能只剩 0.1s，表现就是"点了又跳"） */
function restartPhase() {
  if (canRotate.value) startRotating()
}

function goTo(index: number) {
  const total = liveList.value.length
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

/** 焦点真正离开整条促销条才恢复（在圆点/按钮之间移动不算） */
function onFocusOut(event: FocusEvent) {
  const next = event.relatedTarget
  if (next instanceof Node && (event.currentTarget as HTMLElement | null)?.contains(next)) return
  focusPaused.value = false
}

function onVisibilityChange() {
  documentHidden.value = document.hidden
  // 页面隐藏时停掉两个定时器：既省电，也避免后台节流后倒计时乱跳
  if (documentHidden.value) {
    stopAll()
    return
  }
  tick()
  if (!liveList.value.length) return
  startTicking()
}

function onMotionChange(event: MediaQueryListEvent) {
  reducedMotion.value = event.matches
}

// 暂停条件变化即启停轮播定时器
watch(canRotate, (value) => {
  if (value) startRotating()
  else stopRotating()
})

// 活动条数变化（后端数据到达 / 语言切换 / 某条活动过期）后重新评估
watch(() => liveList.value.length, (total) => {
  if (currentIndex.value >= total) currentIndex.value = 0
  if (!total) {
    stopAll()
    return
  }
  startTicking()
  startRotating()
})

// ---------------------------------------------------------------------------
// 触摸滑动：左右切换活动（手写 touch，不引新依赖）
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
  if (!touch || liveList.value.length <= 1) return
  const dx = touch.clientX - touchStartX
  const dy = touch.clientY - touchStartY
  if (Math.abs(dx) < 40 || Math.abs(dx) <= Math.abs(dy)) return
  if (dx < 0) goNext()
  else goPrev()
}

onMounted(() => {
  isMounted.value = true
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.matches
  motionQuery.addEventListener('change', onMotionChange)
  document.addEventListener('visibilitychange', onVisibilityChange)
  documentHidden.value = document.hidden
  tick()
  // 全部过期时 tick() 内部已经 stopAll()，这里不要再把定时器加回来
  if (liveList.value.length) {
    startTicking()
    startRotating()
  }
})

onUnmounted(() => {
  stopAll()
  motionQuery?.removeEventListener('change', onMotionChange)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  motionQuery = null
})

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

const countdownUnits = computed(() => {
  const total = remainingSeconds.value
  return [
    { key: 'hours', value: pad(Math.floor(total / 3600)) },
    { key: 'minutes', value: pad(Math.floor((total % 3600) / 60)) },
    { key: 'seconds', value: pad(total % 60) },
  ]
})

const countdownText = computed(() => countdownUnits.value.map(unit => unit.value).join(':'))
</script>
