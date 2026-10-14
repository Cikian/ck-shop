<template>
  <UModal
    :open="open"
    :title="t('order.cancelTitle')"
    :description="t('order.cancelDesc')"
    :ui="{ content: 'rounded-[var(--radius-card)]' }"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div>
        <label for="cancel-reason" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
          {{ t('order.cancelReasonLabel') }}
        </label>
        <textarea
          id="cancel-reason"
          v-model="reason"
          rows="3"
          maxlength="200"
          :placeholder="t('order.cancelReasonPlaceholder')"
          class="w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-3 py-2.5 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
        ></textarea>
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
          {{ t('order.cancelKeep') }}
        </button>
        <button
          type="button"
          class="cursor-pointer rounded-full bg-[var(--color-error)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-error-600)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
          :disabled="submitting"
          @click="submit"
        >
          {{ submitting ? t('order.cancelSubmitting') : t('order.cancelConfirm') }}
        </button>
      </div>
    </template>
  </UModal>
</template>

<script setup lang="ts">
/**
 * 取消订单确认弹层。
 *
 * 取消原因（OrderActionDTO.reason）是选填的自由文本：后端没有为"取消原因"提供字典，
 * 所以这里**不编造**一个下拉选项列表（那会让用户以为只能从这几项里选）。
 *
 * 「能不能取消」不由本组件判断：调用方只在列表/详情返回 canCancel === true 时
 * 才让用户打开它，后端也会再校验一次（只允许待支付订单被买家取消）。
 */
const props = defineProps<{
  open: boolean
  orderNo: string
}>()

const emit = defineEmits<{
  (e: 'update:open', open: boolean): void
  (e: 'canceled'): void
}>()

const { t } = useI18n()
const toast = useToast()

const reason = ref('')
const submitting = ref(false)

watch(() => props.open, (open) => {
  if (open) {
    reason.value = ''
    submitting.value = false
  }
})

/** 从 $fetch 抛出的错误里取出可读文案（后端业务异常 message 是中文，直接展示） */
function readErrorMessage(error: unknown): string {
  const err = error as any
  return err?.data?.statusMessage || err?.data?.message || err?.statusMessage || err?.message || t('order.actionFailed')
}

async function submit() {
  if (submitting.value) return
  submitting.value = true
  try {
    await $fetch('/api/order/cancel', {
      method: 'POST',
      body: { orderNo: props.orderNo, reason: reason.value.trim() },
    })
    toast.add({ title: t('order.canceledToast'), color: 'success', icon: 'lucide:check' })
    emit('update:open', false)
    emit('canceled')
  } catch (error) {
    toast.add({
      title: t('order.actionFailed'),
      description: readErrorMessage(error),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    submitting.value = false
  }
}
</script>
