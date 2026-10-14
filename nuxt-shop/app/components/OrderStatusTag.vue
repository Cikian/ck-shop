<template>
  <!--
    订单 / 退款 / 支付状态标签。

    【硬规则】标签里的**文字**永远是后端返回的中文标签
    （orderStatusLabel / statusLabel / eventTypeLabel / refundTypeLabel），
    本组件不内置任何「状态码 -> 中文」的映射：一旦后端加了新状态，
    前端自建映射就会显示成英文码或空白，而直接用后端的标签永远不会。
  -->
  <span
    class="inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-[12px] font-bold whitespace-nowrap"
    :class="toneClass"
  >
    <span class="h-1.5 w-1.5 shrink-0 rounded-full bg-current" aria-hidden="true"></span>
    {{ label }}
  </span>
</template>

<script setup lang="ts">
/**
 * 状态标签（纯展示组件）。
 *
 * status 只用来决定**颜色**（纯视觉），并且对未知状态有兜底色 ——
 * 后端新增一个状态时，标签照常渲染，只是用默认色，不会白屏也不会漏字。
 */
const props = defineProps<{
  /** 后端返回的中文标签，直接展示，不做任何翻译或映射 */
  label: string
  /** 原始状态码；仅用于选择配色，不参与文案 */
  status?: string | null
}>()

/** 状态码 -> 配色（不是文案映射！） */
const TONES: Record<string, string> = {
  // 订单状态
  WAIT_PAY: 'bg-[var(--color-warning-50)] text-[var(--color-warning-600)]',
  WAIT_SHIP: 'bg-[var(--color-info-50)] text-[var(--color-info-600)]',
  SHIPPED: 'bg-[var(--color-info-50)] text-[var(--color-info-600)]',
  COMPLETED: 'bg-[var(--color-success-50)] text-[var(--color-success-600)]',
  CANCELLED: 'bg-[var(--color-line-soft)] text-[var(--color-muted)]',
  CLOSED: 'bg-[var(--color-line-soft)] text-[var(--color-muted)]',
  // 支付单 / 退款单状态
  INIT: 'bg-[var(--color-line-soft)] text-[var(--color-muted)]',
  PENDING: 'bg-[var(--color-warning-50)] text-[var(--color-warning-600)]',
  APPLYING: 'bg-[var(--color-warning-50)] text-[var(--color-warning-600)]',
  REFUNDING: 'bg-[var(--color-info-50)] text-[var(--color-info-600)]',
  SUCCESS: 'bg-[var(--color-success-50)] text-[var(--color-success-600)]',
  REJECTED: 'bg-[var(--color-error-50)] text-[var(--color-error-600)]',
  FAILED: 'bg-[var(--color-error-50)] text-[var(--color-error-600)]',
}

const toneClass = computed(() => {
  const key = String(props.status ?? '').trim().toUpperCase()
  return TONES[key] ?? 'bg-[var(--color-brand-50)] text-[var(--color-brand-600)]'
})
</script>
