<template>
  <UModal
    :open="open"
    :title="t('order.refundModalTitle')"
    :description="t('order.refundModalDesc')"
    :ui="{ content: 'rounded-[var(--radius-card)]' }"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div class="space-y-5">
        <!-- 退款原因：取值来自后端字典 shop_refund_reason，前端只提供这几项 -->
        <div>
          <label for="refund-reason" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
            {{ t('order.refundReasonLabel') }}
          </label>
          <select
            id="refund-reason"
            v-model="reason"
            class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-3 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
          >
            <option value="">{{ t('order.refundReasonPlaceholder') }}</option>
            <option v-for="option in REASON_OPTIONS" :key="option.value" :value="option.value">
              {{ t(option.label) }}
            </option>
          </select>
        </div>

        <!-- 说明 -->
        <div>
          <label for="refund-desc" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
            {{ t('order.refundDescLabel') }}
          </label>
          <textarea
            id="refund-desc"
            v-model="description"
            rows="3"
            maxlength="200"
            :placeholder="t('order.refundDescPlaceholder')"
            class="w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-3 py-2.5 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
          ></textarea>
        </div>

        <!-- 按行退数量：全部为 0 = 整单全退（后端约定：不传 items 就是整单退） -->
        <div>
          <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
            <span class="text-[13px] font-bold text-[var(--color-ink-soft)]">{{ t('order.refundItemsLabel') }}</span>
            <span class="text-[12px] text-[var(--color-muted)]">
              {{ refundAll ? t('order.refundFull') : t('order.refundPartial') }}
            </span>
          </div>

          <ul v-if="lines.length" class="mt-2 divide-y divide-[var(--color-line-soft)] rounded-[var(--radius-card-sm)] border border-[var(--color-line-soft)]">
            <li v-for="line in lines" :key="line.id" class="flex items-center gap-3 px-3 py-2.5">
              <span class="min-w-0 flex-1">
                <span class="block truncate text-[13px] font-medium text-[var(--color-ink)]">{{ line.productName || '—' }}</span>
                <span class="mt-0.5 block truncate text-[12px] text-[var(--color-muted)]">
                  {{ line.skuName || t('cart.noSpec') }}
                  <!-- maxRefundQuantity 由「下单数量 − 已退数量」得出；
                       这里算的是**件数**不是金额，退款金额一律由服务端计算 -->
                  · {{ t('order.refundableQuantity', { count: maxRefundQuantity(line) }) }}
                </span>
              </span>
              <input
                v-model.number="quantities[String(line.id)]"
                type="number"
                min="0"
                :max="maxRefundQuantity(line)"
                step="1"
                inputmode="numeric"
                :aria-label="line.productName || String(line.id)"
                class="h-9 w-20 shrink-0 rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-2 text-center text-sm tabular text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
              >
            </li>
          </ul>
          <p v-else class="mt-2 text-[13px] text-[var(--color-muted)]">{{ t('order.refundNoItems') }}</p>

          <p class="mt-2 text-[12px] text-[var(--color-muted)]">{{ t('order.refundAllHint') }}</p>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex w-full justify-end gap-3">
        <button
          type="button"
          class="cursor-pointer rounded-full border border-[var(--color-line)] px-5 py-2.5 text-sm font-semibold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] motion-reduce:transition-none"
          :disabled="submitting"
          @click="emit('update:open', false)"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="cursor-pointer rounded-full bg-[var(--color-brand-500)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
          :disabled="submitting"
          @click="submit"
        >
          {{ submitting ? t('order.refundSubmitting') : t('order.refundSubmit') }}
        </button>
      </div>
    </template>
  </UModal>
</template>

<script setup lang="ts">
/**
 * 申请退款弹层。
 *
 * 三条不容妥协的契约（与后端 RefundApplyDTO 对齐）：
 *   1) **请求体里没有退款金额**。金额由服务端按「订单行成交价 × 退款数量 − 已分摊优惠」
 *      计算，并受「累计退款 ≤ 实收金额」约束。前端只提交件数。
 *   2) **requestId 是幂等键**，在弹层打开时用 crypto.randomUUID() 生成一次并复用：
 *      用户连点「提交申请」或网络重试都只会产生一张退款单。
 *      每次点击都新生成的话幂等就失效了。
 *   3) **items 不传 = 整单全退**。所以全部数量为 0 时不下发 items 字段。
 */
import { readErrorMessage } from '~~/composables/useApiError'
import { createIdempotencyKey } from '~~/composables/useIdempotencyKey'

/** 退款可选行（直接使用后端 StoreOrderItemVO 的字段名，页面不必再转换一次） */
interface RefundLine {
  /** 订单明细 ID（后端字段名就是 id，提交时映射成 RefundItemDTO.orderItemId） */
  id?: string | null
  productName?: string | null
  skuName?: string | null
  /** 下单数量 */
  quantity?: number | null
  /** 已退数量 */
  refundQuantity?: number | null
}

const props = defineProps<{
  open: boolean
  orderNo: string
  items: RefundLine[]
}>()

const emit = defineEmits<{
  (e: 'update:open', open: boolean): void
  (e: 'applied'): void
}>()

const { t } = useI18n()
const toast = useToast()

/**
 * 退款原因选项：value 必须与后端字典 shop_refund_reason 的取值完全一致，
 * label 是界面文案（走 i18n）。前端不发明新的原因值。
 */
const REASON_OPTIONS: { value: string, label: string }[] = [
  { value: 'no_need', label: 'order.reasonNoNeed' },
  { value: 'duplicate', label: 'order.reasonDuplicate' },
  { value: 'mismatch', label: 'order.reasonMismatch' },
  { value: 'quality', label: 'order.reasonQuality' },
  { value: 'missing', label: 'order.reasonMissing' },
  { value: 'logistics', label: 'order.reasonLogistics' },
  { value: 'other', label: 'order.reasonOther' },
]

const reason = ref('')
const description = ref('')
const submitting = ref(false)
const quantities = ref<Record<string, number>>({})
/** 幂等键：弹层每次打开生成一次，提交重试复用同一个值 */
const requestId = ref('')

/** 可退款行：只保留拿得到明细 ID 的行（拿不到就没法按行退） */
const lines = computed(() => props.items.filter(item => !!item?.id))

/** 某行最多能退多少件 = 下单数量 − 已退数量（件数，不是金额） */
function maxRefundQuantity(line: RefundLine): number {
  const quantity = Number(line.quantity ?? 0)
  const refunded = Number(line.refundQuantity ?? 0)
  const remain = Math.trunc(quantity) - Math.trunc(refunded)
  return remain > 0 ? remain : 0
}

/** 全部数量为 0 时按「整单全退」提交 */
const refundAll = computed(() => (
  lines.value.length === 0
  || lines.value.every(line => (quantities.value[String(line.id)] ?? 0) <= 0)
))

/**
 * 每次打开弹层都生成一个**新的**幂等键：同一张退款单的重试复用同一个键，
 * 但用户重新打开（= 新的一次申请意图）必须换键。
 */
function resetForm() {
  reason.value = ''
  description.value = ''
  submitting.value = false
  requestId.value = createIdempotencyKey('refund')
  const next: Record<string, number> = {}
  for (const line of lines.value) next[String(line.id)] = 0
  quantities.value = next
}

// 每次打开都重置：复用上一次的原因/数量会让人误提交
watch(() => props.open, (open) => {
  if (open) resetForm()
})

async function submit() {
  if (submitting.value) return
  if (!props.orderNo) {
    toast.add({ title: t('order.actionFailed'), description: t('order.payMissingOrderNo'), color: 'error', icon: 'lucide:circle-alert' })
    return
  }

  submitting.value = true
  try {
    // 只提交数量 > 0 的行；一行都没有就**不带 items**（= 整单全退）
    const items = lines.value
      .map(line => ({
        // 后端 RefundItemDTO 的字段名是 orderItemId（订单明细 ID）
        orderItemId: String(line.id),
        quantity: Math.trunc(Number(quantities.value[String(line.id)] ?? 0)),
      }))
      .filter(item => item.quantity > 0)

    await $fetch('/api/order/refund/apply', {
      method: 'POST',
      body: {
        orderNo: props.orderNo,
        requestId: requestId.value,
        reason: reason.value,
        description: description.value.trim(),
        items: items.length ? items : undefined,
      },
    })

    toast.add({ title: t('order.refundAppliedToast'), color: 'success', icon: 'lucide:check' })
    emit('update:open', false)
    emit('applied')
  } catch (error) {
    toast.add({
      title: t('order.actionFailed'),
      description: readErrorMessage(error, t('order.actionFailed')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    submitting.value = false
  }
}
</script>
