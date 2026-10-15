<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto max-w-5xl px-2 pb-16 sm:px-3 lg:px-4">
      <header class="mb-5 flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div>
          <h1 class="text-[26px] font-black leading-none text-[var(--color-ink)] sm:text-[32px]">
            {{ t('order.detailTitle') }}
          </h1>
          <p v-if="detail" class="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-[13px] text-[var(--color-muted)]">
            <span>{{ t('order.orderNo') }}：</span>
            <span class="font-bold tabular text-[var(--color-ink)]">{{ detail.orderNo }}</span>
            <OrderStatusTag :label="detail.orderStatusLabel || detail.orderStatus || ''" :status="detail.orderStatus" />
          </p>
        </div>
        <NuxtLinkLocale
          to="/orders"
          class="group inline-flex cursor-pointer items-center gap-1 text-[13px] font-medium text-[var(--color-muted)] transition-colors duration-200 hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
        >
          <UIcon name="lucide:chevron-left" class="h-3.5 w-3.5" aria-hidden="true" />
          {{ t('order.backToList') }}
        </NuxtLinkLocale>
      </header>

      <!-- 未登录 -->
      <section v-if="unauthorized" class="ui-card px-6 py-16 text-center">
        <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
          <UIcon name="lucide:lock" class="h-8 w-8 text-[var(--color-brand-500)]" aria-hidden="true" />
        </span>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('order.loginRequired') }}</h2>
        <p class="mx-auto mt-2 max-w-md text-sm text-[var(--color-muted)]">{{ t('order.loginRequiredDesc') }}</p>
        <NuxtLinkLocale :to="loginLink" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm">
          <UIcon name="lucide:log-in" class="h-4 w-4" aria-hidden="true" />
          {{ t('order.loginAction') }}
        </NuxtLinkLocale>
      </section>

      <!-- 加载失败 / 订单不存在 -->
      <section v-else-if="loadError" class="ui-card px-6 py-16 text-center">
        <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error)]/10">
          <UIcon name="lucide:package-x" class="h-8 w-8 text-[var(--color-error)]" aria-hidden="true" />
        </span>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('order.loadError') }}</h2>
        <p class="mt-2 text-sm text-[var(--color-muted)]">{{ loadError }}</p>
        <div class="mt-6 flex flex-wrap justify-center gap-3">
          <button type="button" class="ui-pill ui-pill-brand h-12 px-7 text-sm" @click="loadDetail()">
            <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
            {{ t('common.retry') }}
          </button>
          <NuxtLinkLocale to="/orders" class="ui-pill ui-pill-ghost h-12 px-7 text-sm">
            {{ t('order.backToList') }}
          </NuxtLinkLocale>
        </div>
      </section>

      <!-- 骨架屏 -->
      <section v-else-if="loading || !detail" class="space-y-4" aria-busy="true" :aria-label="t('common.loading')">
        <div v-for="index in 3" :key="index" class="ui-card space-y-3 p-5">
          <div class="h-4 w-1/4 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
          <div class="h-3 w-2/3 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
          <div class="h-3 w-1/2 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
        </div>
      </section>

      <div v-else class="space-y-5">
        <!-- 待支付：倒计时 + 去支付 -->
        <section v-if="detail.canPay" class="ui-card flex flex-wrap items-center justify-between gap-x-4 gap-y-3 p-5">
          <div>
            <p class="text-[13px] text-[var(--color-muted)]">{{ t('order.payRemain') }}</p>
            <p
              class="mt-1 text-[24px] font-black leading-none tabular"
              :class="expired ? 'text-[var(--color-error-600)]' : 'text-[var(--color-brand-600)]'"
            >
              {{ countdownText }}
            </p>
            <p class="mt-1.5 text-[12px] text-[var(--color-muted)]">
              {{ t('order.payDeadline') }}：{{ detail.payDeadline || '--' }}
            </p>
          </div>
          <NuxtLinkLocale :to="payLink" class="ui-pill ui-pill-brand h-12 px-7 text-sm">
            <UIcon name="lucide:credit-card" class="h-4 w-4" aria-hidden="true" />
            {{ t('order.actionPay') }}
          </NuxtLinkLocale>
        </section>

        <!-- 操作区：按钮可见性全部来自后端 canPay/canCancel/canReceive/canRefund -->
        <section class="ui-card flex flex-wrap items-center gap-3 p-5">
          <span class="text-[13px] font-bold text-[var(--color-ink-soft)]">{{ t('order.action') }}</span>
          <button
            v-if="detail.canCancel"
            type="button"
            class="inline-flex h-10 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-5 text-[13px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-error)] hover:text-[var(--color-error)] motion-reduce:transition-none"
            @click="cancelState.open = true"
          >
            {{ t('order.actionCancel') }}
          </button>
          <button
            v-if="detail.canRefund"
            type="button"
            class="inline-flex h-10 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-5 text-[13px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-brand-500)] hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
            @click="refundState.open = true"
          >
            {{ t('order.actionRefund') }}
          </button>
          <button
            v-if="detail.canReceive"
            type="button"
            class="inline-flex h-10 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-5 text-[13px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-success)] hover:text-[var(--color-success-600)] motion-reduce:transition-none"
            @click="receiveState.open = true"
          >
            {{ t('order.actionReceive') }}
          </button>
          <NuxtLinkLocale v-if="detail.canPay" :to="payLink" class="ui-pill ui-pill-brand h-10 px-5 text-[13px]">
            {{ t('order.actionPay') }}
          </NuxtLinkLocale>
          <!-- 没有任何可执行操作时如实说明，而不是给一片空白 -->
          <span v-if="!detail.canCancel && !detail.canRefund && !detail.canReceive && !detail.canPay" class="text-[13px] text-[var(--color-muted)]">
            {{ t('order.noAction') }}
          </span>
        </section>

        <!-- 商品明细 / 金额明细 / 收货信息 / 支付单 / 退款单 / 订单时间线
             组件见 components/order/DetailCards.vue：只读展示，数据由 props 传入；
             「撤销退款」只把事件抛回本页（弹层与提交状态在这里） -->
        <OrderDetailCards
          :detail="detail"
          :items="items"
          :payments="payments"
          :refunds="refunds"
          :events="events"
          @withdraw="askWithdraw"
        />
      </div>
    </div>

    <!-- 确认收货 -->
    <UModal
      v-model:open="receiveState.open"
      :title="t('order.receiveTitle')"
      :description="t('order.receiveDesc')"
      :ui="{ content: 'rounded-[var(--radius-card)]' }"
    >
      <template #footer>
        <div class="flex w-full justify-end gap-3">
          <button
            type="button"
            class="cursor-pointer rounded-full border border-[var(--color-line)] px-5 py-2.5 text-sm font-semibold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] motion-reduce:transition-none"
            @click="receiveState.open = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="button"
            class="cursor-pointer rounded-full bg-[var(--color-brand-500)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
            :disabled="receiveState.pending"
            @click="confirmReceive"
          >
            {{ t('order.receiveConfirm') }}
          </button>
        </div>
      </template>
    </UModal>

    <!-- 撤销退款 -->
    <UModal
      v-model:open="withdrawState.open"
      :title="t('order.withdrawTitle')"
      :description="t('order.withdrawDesc')"
      :ui="{ content: 'rounded-[var(--radius-card)]' }"
    >
      <template #footer>
        <div class="flex w-full justify-end gap-3">
          <button
            type="button"
            class="cursor-pointer rounded-full border border-[var(--color-line)] px-5 py-2.5 text-sm font-semibold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] motion-reduce:transition-none"
            @click="withdrawState.open = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="button"
            class="cursor-pointer rounded-full bg-[var(--color-error)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-error-600)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
            :disabled="withdrawState.pending"
            @click="confirmWithdraw"
          >
            {{ t('order.withdrawConfirm') }}
          </button>
        </div>
      </template>
    </UModal>

    <OrderCancelModal
      v-model:open="cancelState.open"
      :order-no="orderNo"
      @canceled="loadDetail()"
    />

    <RefundApplyModal
      v-model:open="refundState.open"
      :order-no="orderNo"
      :items="items"
      @applied="onRefundApplied"
    />

    <SiteFooter />
  </div>
</template>
<script setup lang="ts">
/** 订单详情是私有页面，未登录 / 登录过期由 auth 中间件重定向到登录页并回跳 */
definePageMeta({ middleware: 'auth' })

import { isPositive } from '~~/composables/useApiError'
import { useStorePrice } from '~~/composables/useProducts'
import { useOrderDetail } from '~~/composables/useOrderDetail'

/**
 * 订单详情（渲染层）。
 *
 * 展示：状态 / 金额明细 / 商品 / 收货信息 / 物流 / 支付单 / 退款单 / 时间线。
 * 操作：取消订单 / 去支付 / 确认收货 / 申请退款 / 撤销退款。
 *
 * 数据、派生与全部操作都在 composables/useOrderDetail.ts：
 *   1) 四个订单级操作按钮的可见性**只用后端下发的 canPay / canCancel / canReceive / canRefund**；
 *   2) 所有"中文标签"（订单状态、支付单状态、退款状态与类型、事件类型）都用后端字段；
 *   3) 金额只做两位小数格式化，不做任何运算。
 *
 * 本文件只保留模板直接依赖的两件事：i18n 文案（t）与金额格式化（format）、正数判断（isPositive）。
 */
const { t } = useI18n()
const { format } = useStorePrice()

const {
  orderNo,
  loading,
  loadError,
  unauthorized,
  detail,
  receiveState,
  cancelState,
  refundState,
  withdrawState,
  items,
  payments,
  refunds,
  events,
  expired,
  countdownText,
  receiverAddressText,
  loginLink,
  payLink,
  loadDetail,
  confirmReceive,
  askWithdraw,
  confirmWithdraw,
  onRefundApplied,
} = useOrderDetail()

useSeoMeta({
  title: () => t('order.detailTitle'),
  robots: 'noindex, nofollow',
})
</script>
