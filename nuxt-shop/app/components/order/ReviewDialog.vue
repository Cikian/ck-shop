<!--
  评价提交弹窗（订单详情页「去评价」入口的弹层）

  职责边界（与 DetailCards.vue 一致：组件只负责「渲染 + 收集输入」，不发请求）：
    1) 本地只做**一件事**校验：没选星级就不提交，显示 review.ratingRequired；
    2) 真正的提交由页面调用 composables/useReviews.ts 的 submit()：
       成功 → 页面关弹窗 + toast；失败 → 页面把后端可读中文（extractReviewErrorMessage）
       通过 errorMessage 回传到这里显示；
    3) **失败不丢用户输入**：星级 / 内容 / 匿名只存在本组件的 ref 里，
       提交失败时页面只更新 errorMessage，不清空任何输入；
       唯一会重置的时机是「弹窗重新打开」（watch(open === true)）。

  无障碍：role="dialog" + aria-modal="true"、ESC 关闭、点遮罩关闭、
  打开时锁定 body 滚动并把焦点移入弹层、关闭后把焦点还给触发按钮；
  星级是一组 role="radio"（←/→/↑/↓/Home/End 可操作，每个都有具体 aria-label）。
  移动端全宽（底部抽屉式），桌面居中且最大 max-w-lg。

  为什么要 Teleport + v-if="open"：open 只在用户点击后变 true，
  SSR 阶段恒为 false —— 服务端不会有任何遮罩节点（与 ProductImageLightbox 同一套理由）。
-->
<template>
  <Teleport v-if="open" to="body">
    <div
      class="fixed inset-0 z-[100] flex items-end justify-center sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
      :aria-label="t('review.submitTitle')"
    >
      <!-- 遮罩：单独一层，点击关闭（提交中忽略，避免请求还在飞就把上下文关了） -->
      <div class="absolute inset-0 bg-black/50" aria-hidden="true" @click="requestClose"></div>

      <!-- 内容层：移动端底部全宽，桌面居中最大 max-w-lg -->
      <div
        ref="panel"
        tabindex="-1"
        class="relative flex max-h-[92dvh] w-full flex-col overflow-hidden rounded-t-2xl bg-white shadow-[var(--shadow-2xl)] outline-none sm:max-w-lg sm:rounded-2xl"
      >
        <header class="flex items-start justify-between gap-3 border-b border-[var(--color-line-soft)] px-5 py-4">
          <h2 class="text-[16px] font-black text-[var(--color-ink)]">{{ t('review.submitTitle') }}</h2>
          <button
            type="button"
            class="-mr-1 -mt-1 flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-full text-[var(--color-muted)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-ink)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-500)] motion-reduce:transition-none"
            :aria-label="t('review.close')"
            @click="requestClose"
          >
            <UIcon name="lucide:x" class="h-5 w-5" aria-hidden="true" />
          </button>
        </header>

        <div class="min-h-0 flex-1 space-y-5 overflow-y-auto px-5 py-4">
          <!-- 商品信息：全部来自 StoreReviewableItem，不额外请求 -->
          <div v-if="item" class="flex items-center gap-3">
            <span class="h-14 w-14 shrink-0 overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-line-soft)]">
              <ShopImage
                :src="item.productImage"
                :alt="item.productName || ''"
                width="56"
                height="56"
                class="h-full w-full object-cover"
                icon-class="h-4 w-4"
              />
            </span>
            <div class="min-w-0 flex-1">
              <p class="line-clamp-2 text-[14px] font-bold text-[var(--color-ink)]">{{ item.productName || '—' }}</p>
              <p class="mt-1 truncate text-[12px] text-[var(--color-muted)]">{{ item.skuName || t('cart.noSpec') }}</p>
            </div>
            <span class="shrink-0 text-[12px] tabular text-[var(--color-muted)]">× {{ item.quantity }}</span>
          </div>

          <!-- 星级：1-5，role=radio 组，方向键可切换 -->
          <div>
            <span id="review-rating-label" class="mb-1 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('review.ratingLabel') }}
            </span>
            <div
              role="radiogroup"
              aria-labelledby="review-rating-label"
              class="flex items-center gap-1"
              @keydown="onRatingKeydown"
            >
              <button
                v-for="star in RATING_OPTIONS"
                :key="star"
                :ref="(el) => setStarRef(star, el)"
                type="button"
                role="radio"
                :aria-checked="rating === star"
                :aria-label="ratingOptionLabel(star)"
                :tabindex="star === tabStop ? 0 : -1"
                class="flex h-10 w-10 cursor-pointer items-center justify-center rounded-full transition-colors duration-200 hover:bg-[var(--color-brand-50)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-500)] motion-reduce:transition-none"
                @click="selectRating(star)"
              >
                <UIcon
                  name="lucide:star"
                  class="h-7 w-7"
                  :class="star <= rating ? 'fill-current text-[var(--color-accent-500)]' : 'text-[var(--color-line)]'"
                  aria-hidden="true"
                />
              </button>
            </div>
            <p
              v-if="ratingRequired"
              class="mt-1.5 flex items-start gap-1.5 text-[12px] font-medium text-[var(--color-error-600)]"
              role="alert"
            >
              <UIcon name="lucide:circle-alert" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              {{ t('review.ratingRequired') }}
            </p>
          </div>

          <!-- 评价内容：maxlength 500 + 实时剩余字数；超长时给明确提示并挡住提交 -->
          <div>
            <div class="mb-1.5 flex items-baseline justify-between gap-3">
              <label for="review-content" class="text-[13px] font-bold text-[var(--color-ink-soft)]">
                {{ t('review.contentLabel') }}
              </label>
              <span
                class="text-[12px] tabular"
                :class="tooLong ? 'text-[var(--color-error-600)]' : 'text-[var(--color-muted)]'"
              >
                {{ remaining }}
              </span>
            </div>
            <textarea
              id="review-content"
              v-model="content"
              rows="4"
              :maxlength="CONTENT_MAX"
              :placeholder="t('review.contentPlaceholder')"
              class="w-full resize-none rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-3 py-2.5 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            ></textarea>
            <p
              v-if="tooLong"
              class="mt-1.5 flex items-start gap-1.5 text-[12px] font-medium text-[var(--color-error-600)]"
              role="alert"
            >
              <UIcon name="lucide:circle-alert" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              {{ t('review.contentTooLong') }}
            </p>
          </div>

          <!-- 匿名 -->
          <label class="flex cursor-pointer items-start gap-2.5 rounded-[var(--radius-input)] border border-[var(--color-line-soft)] px-3.5 py-3">
            <input
              v-model="anonymous"
              type="checkbox"
              class="mt-0.5 h-4 w-4 shrink-0 cursor-pointer accent-[var(--color-brand-500)]"
            >
            <span class="min-w-0 flex-1">
              <span class="block text-[13px] font-semibold text-[var(--color-ink)]">{{ t('review.anonymousLabel') }}</span>
              <span class="mt-0.5 block text-[12px] leading-relaxed text-[var(--color-muted)]">{{ t('review.anonymousHint') }}</span>
            </span>
          </label>

          <!-- 后端返回的可读错误（失败时保留用户已填内容，只多这一条提示） -->
          <p
            v-if="errorMessage"
            class="flex items-start gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-3.5 py-2.5 text-[12px] font-medium leading-relaxed text-[var(--color-error-600)]"
            role="alert"
          >
            <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <span class="min-w-0 flex-1">{{ errorMessage }}</span>
          </p>
        </div>

        <footer class="flex flex-wrap justify-end gap-3 border-t border-[var(--color-line-soft)] px-5 py-4">
          <button
            type="button"
            class="cursor-pointer rounded-full border border-[var(--color-line)] px-5 py-2.5 text-sm font-semibold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
            :disabled="submitting"
            @click="requestClose"
          >
            {{ t('review.close') }}
          </button>
          <AsyncButton
            type="button"
            class="cursor-pointer rounded-full bg-[var(--color-brand-cta)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-brand-cta-hover)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
            :pending="submitting"
            :pending-label="t('review.submitSubmitting')"
            @click="requestSubmit"
          >
            {{ t('review.submitAction') }}
          </AsyncButton>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import type { StoreReviewableItem } from '~~/types/shop-review'

/** 与后端校验一致：内容上限 500 字（超长只在前端挡一次，后端仍会校验） */
const CONTENT_MAX = 500
/** 星级档位：1-5（这是评价刻度，不是任何"统计数字"） */
const RATING_OPTIONS = [1, 2, 3, 4, 5] as const
const MAX_RATING = 5

const props = defineProps<{
  /** 是否打开（受控：页面决定开关） */
  open: boolean
  /** 正在评价的行（来自 useOrderReviews().items） */
  item: StoreReviewableItem | null
  /** 提交中（来自 composable 的 submitting）：禁用按钮并显示 review.submitSubmitting */
  submitting: boolean
  /** 提交失败时后端返回的可读文案（页面用 extractReviewErrorMessage 生成） */
  errorMessage: string
}>()

const emit = defineEmits<{
  /** 请求关闭（ESC / 遮罩 / 关闭按钮 / 页脚「关闭」都走这里） */
  (e: 'close'): void
  /** 本地校验通过、请求提交：真正的网络请求由页面负责 */
  (e: 'submit', payload: { orderItemId: string; rating: number; content: string; anonymous: boolean }): void
}>()

const { t } = useI18n()

const panel = ref<HTMLElement | null>(null)
const rating = ref(0)
const content = ref('')
const anonymous = ref(false)
/** 是否已经点过「提交评价」：只有点过才显示 review.ratingRequired，避免一打开就报错 */
const ratingRequired = ref(false)

/** 星级按钮引用：方向键切换后要把焦点移到新选中项 */
const starButtons: Record<number, HTMLButtonElement | null> = {}

const remaining = computed(() => Math.max(0, CONTENT_MAX - content.value.length))
const tooLong = computed(() => content.value.length > CONTENT_MAX)
/** roving tabindex：整组只占一个 Tab 位（已选星级，否则第 1 颗） */
const tabStop = computed(() => (rating.value >= 1 ? rating.value : 1))

/** 读屏需要的具体标签（i18n 只有"评分"总标签，这里补上档位） */
function ratingOptionLabel(star: number): string {
  return `${t('review.ratingLabel')} ${star} / ${MAX_RATING}`
}

function selectRating(star: number) {
  const next = Math.min(Math.max(Math.trunc(star), 1), MAX_RATING)
  rating.value = next
  ratingRequired.value = false
}

function setStarRef(star: number, el: unknown) {
  starButtons[star] = (el as HTMLButtonElement | null) ?? null
}

/** 方向键 / Home / End：radiogroup 的标准键盘行为 */
function onRatingKeydown(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  let next = rating.value >= 1 ? rating.value : 1
  if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') next = Math.max(1, next - 1)
  else if (event.key === 'ArrowRight' || event.key === 'ArrowUp') next = Math.min(MAX_RATING, next + 1)
  else if (event.key === 'Home') next = 1
  else if (event.key === 'End') next = MAX_RATING
  selectRating(next)
  starButtons[next]?.focus()
}

function requestSubmit() {
  if (props.submitting) return
  const target = props.item
  if (!target) return
  // 未选星级：只提示，不发请求
  if (rating.value < 1) {
    ratingRequired.value = true
    return
  }
  if (tooLong.value) return
  emit('submit', {
    orderItemId: target.orderItemId,
    rating: rating.value,
    // 只去掉首尾空白：中间内容原样提交，不做任何"美化"
    content: content.value.trim(),
    anonymous: anonymous.value,
  })
}

function requestClose() {
  if (props.submitting) return
  emit('close')
}

// ===== ESC 关闭 =====
function onKeydown(event: KeyboardEvent) {
  if (!props.open || event.key !== 'Escape') return
  event.preventDefault()
  requestClose()
}

// ===== 锁定 body 滚动（关闭后恢复原值，不破坏页面原有的 overflow / padding） =====
let bodyLocked = false
let previousOverflow = ''
let previousPaddingRight = ''

function lockBody() {
  if (!import.meta.client || bodyLocked) return
  const body = document.body
  previousOverflow = body.style.overflow
  previousPaddingRight = body.style.paddingRight
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

/** 打开前的焦点位置：关闭后还回去，键盘/读屏用户不会掉回文档开头 */
let lastFocused: HTMLElement | null = null

watch(
  () => props.open,
  (open) => {
    if (!import.meta.client) return
    if (open) {
      // 重新打开 = 一次新的评价：清空上一轮的输入与提示
      rating.value = 0
      content.value = ''
      anonymous.value = false
      ratingRequired.value = false
      lastFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
      lockBody()
      nextTick(() => panel.value?.focus())
    } else {
      // 提交失败时代码路径**不会**走到这里（页面只有成功才关弹窗），
      // 所以用户已输入的星级 / 内容不会因为一次失败而丢。
      unlockBody()
      if (lastFocused?.isConnected) lastFocused.focus()
      lastFocused = null
    }
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  unlockBody()
})
</script>
