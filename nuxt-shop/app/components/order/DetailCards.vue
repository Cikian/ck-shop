<!--
  订单详情正文卡片：商品明细 / 金额明细 / 收货信息 / 支付单 / 退款单 / 订单时间线

  从 pages/orders/[orderNo].vue 拆出（订单详情页拆组件第一步）。
  这些卡片是**只读展示**：数据全部由 props 传入，唯一交互是「撤销退款」——
  它只把事件抛回页面（页面持有弹层与提交状态），组件本身不发请求。

  金额只做两位小数格式化、不做任何运算；标签（订单/支付/退款状态）一律用后端字段；
  "这一行该不该渲染"统一走 utils/orderRefund.ts 的 hasNumber（有值含 0 即渲染）。
-->
<template>
        <section class="ui-card overflow-hidden">
          <header class="border-b border-[var(--color-line-soft)] px-4 py-3">
            <h2 class="flex items-center gap-2 text-[15px] font-black text-[var(--color-ink)]">
              <UIcon name="lucide:package" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
              {{ t('order.itemsTitle') }}
            </h2>
          </header>
          <ul class="divide-y divide-[var(--color-line-soft)]">
            <li v-for="(item, index) in items" :key="item.id || index" class="flex gap-3 px-4 py-4">
              <span class="h-[68px] w-[68px] shrink-0 overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-line-soft)]">
                <img
                  v-if="item.productImage"
                  :src="item.productImage"
                  :alt="item.productName || ''"
                  width="68"
                  height="68"
                  class="h-full w-full object-cover"
                  loading="lazy"
                >
                <span v-else class="flex h-full w-full items-center justify-center">
                  <UIcon name="lucide:image" class="h-5 w-5 text-[var(--color-muted)]" aria-hidden="true" />
                </span>
              </span>
              <div class="min-w-0 flex-1">
                <p class="line-clamp-2 text-[14px] font-bold text-[var(--color-ink)]">{{ item.productName || '—' }}</p>
                <p class="mt-1 truncate text-[12px] text-[var(--color-muted)]">{{ item.skuName || t('cart.noSpec') }}</p>
                <p v-if="item.promotionTitle" class="mt-1 truncate text-[12px] text-[var(--color-brand-600)]">
                  {{ t('order.promotion') }}：{{ item.promotionTitle }}
                </p>
                <p v-if="item.refundQuantity" class="mt-1 text-[12px] text-[var(--color-warning-600)]">
                  {{ t('order.refundedQuantity', { count: item.refundQuantity }) }}
                  <span v-if="isPositive(item.refundAmount)">· {{ format(item.refundAmount) }}</span>
                </p>
              </div>
              <div class="shrink-0 text-right">
                <p class="text-[13px] tabular text-[var(--color-ink)]">{{ format(item.price) }}</p>
                <p class="mt-1 text-[12px] tabular text-[var(--color-muted)]">× {{ item.quantity ?? 0 }}</p>
                <p class="mt-1 text-[13px] font-bold tabular text-[var(--color-ink)]">{{ format(item.actualAmount ?? item.productAmount) }}</p>
              </div>
            </li>
          </ul>
        </section>

        <!-- 金额明细：全部来自后端 StoreOrderDetailVO，前端只做两位小数格式化 -->
        <section class="ui-card p-5">
          <h2 class="text-[15px] font-black text-[var(--color-ink)]">{{ t('order.amountDetail') }}</h2>
          <dl class="mt-4 grid gap-3 sm:grid-cols-2">
            <div v-if="hasNumber(detail.totalAmount)" class="flex items-center justify-between gap-3 text-[13px]">
              <dt class="text-[var(--color-muted)]">{{ t('order.amountTotal') }}</dt>
              <dd class="tabular text-[var(--color-muted)] line-through">{{ format(detail.totalAmount) }}</dd>
            </div>
            <div class="flex items-center justify-between gap-3 text-[13px]">
              <dt class="text-[var(--color-muted)]">{{ t('order.amountProduct') }}</dt>
              <dd class="font-bold tabular text-[var(--color-ink)]">{{ format(detail.productAmount) }}</dd>
            </div>
            <div v-if="isPositive(detail.promotionDiscount)" class="flex items-center justify-between gap-3 text-[13px]">
              <dt class="text-[var(--color-muted)]">{{ t('order.amountPromotion') }}</dt>
              <dd class="font-bold tabular text-[var(--color-brand-600)]">-{{ format(detail.promotionDiscount) }}</dd>
            </div>
            <div v-if="isPositive(detail.discountAmount)" class="flex items-center justify-between gap-3 text-[13px]">
              <dt class="text-[var(--color-muted)]">{{ t('order.amountDiscount') }}</dt>
              <dd class="font-bold tabular text-[var(--color-brand-600)]">-{{ format(detail.discountAmount) }}</dd>
            </div>
            <div class="flex items-center justify-between gap-3 text-[13px]">
              <dt class="text-[var(--color-muted)]">{{ t('order.amountFreight') }}</dt>
              <dd class="font-bold tabular text-[var(--color-ink)]">{{ format(detail.freightAmount) }}</dd>
            </div>
            <div v-if="isPositive(detail.paidAmount)" class="flex items-center justify-between gap-3 text-[13px]">
              <dt class="text-[var(--color-muted)]">{{ t('order.paidAmount') }}</dt>
              <dd class="font-bold tabular text-[var(--color-ink)]">{{ format(detail.paidAmount) }}</dd>
            </div>
            <div v-if="isPositive(detail.refundAmount)" class="flex items-center justify-between gap-3 text-[13px]">
              <dt class="text-[var(--color-muted)]">{{ t('order.refundAmount') }}</dt>
              <dd class="font-bold tabular text-[var(--color-warning-600)]">{{ format(detail.refundAmount) }}</dd>
            </div>
          </dl>
          <div class="mt-4 flex items-center justify-between gap-3 border-t border-[var(--color-line)] pt-4">
            <span class="text-[15px] font-black text-[var(--color-ink)]">{{ t('order.amountPay') }}</span>
            <span class="price text-[24px] leading-none whitespace-nowrap">{{ format(detail.payAmount) }}</span>
          </div>
        </section>

        <!-- 收货信息 + 物流 -->
        <section class="ui-card p-5">
          <h2 class="flex items-center gap-2 text-[15px] font-black text-[var(--color-ink)]">
            <UIcon name="lucide:truck" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
            {{ t('order.receiver') }}
          </h2>
          <dl class="mt-4 space-y-3 text-[13px]">
            <div class="flex gap-3">
              <dt class="w-24 shrink-0 text-[var(--color-muted)]">{{ t('address.receiverName') }}</dt>
              <dd class="min-w-0 flex-1 font-medium text-[var(--color-ink)]">
                {{ detail.receiverName || '--' }}
                <span v-if="detail.receiverPhone" class="ml-2 tabular text-[var(--color-ink-soft)]">{{ detail.receiverPhone }}</span>
              </dd>
            </div>
            <div class="flex gap-3">
              <dt class="w-24 shrink-0 text-[var(--color-muted)]">{{ t('order.receiverAddress') }}</dt>
              <dd class="min-w-0 flex-1 font-medium text-[var(--color-ink)]">
                {{ receiverAddressText || '--' }}
              </dd>
            </div>
            <div v-if="detail.receiverPostalCode" class="flex gap-3">
              <dt class="w-24 shrink-0 text-[var(--color-muted)]">{{ t('address.postalCode') }}</dt>
              <dd class="min-w-0 flex-1 font-medium text-[var(--color-ink)]">{{ detail.receiverPostalCode }}</dd>
            </div>
            <div class="flex gap-3">
              <dt class="w-24 shrink-0 text-[var(--color-muted)]">{{ t('order.logisticsTitle') }}</dt>
              <!-- 没有物流信息就说没有，绝不编一个快递单号 -->
              <dd v-if="detail.logisticsNo || detail.logisticsCompany" class="min-w-0 flex-1 font-medium text-[var(--color-ink)]">
                <span v-if="detail.logisticsCompany">{{ detail.logisticsCompany }}</span>
                <span v-if="detail.logisticsNo" class="ml-2 tabular">{{ detail.logisticsNo }}</span>
              </dd>
              <dd v-else class="min-w-0 flex-1 text-[var(--color-muted)]">{{ t('order.noLogistics') }}</dd>
            </div>
            <div v-if="detail.buyerRemark" class="flex gap-3">
              <dt class="w-24 shrink-0 text-[var(--color-muted)]">{{ t('order.buyerRemark') }}</dt>
              <dd class="min-w-0 flex-1 font-medium text-[var(--color-ink)]">{{ detail.buyerRemark }}</dd>
            </div>
            <div v-if="detail.cancelReason" class="flex gap-3">
              <dt class="w-24 shrink-0 text-[var(--color-muted)]">{{ t('order.cancelReasonLabel') }}</dt>
              <dd class="min-w-0 flex-1 font-medium text-[var(--color-error-600)]">{{ detail.cancelReason }}</dd>
            </div>
          </dl>
        </section>

        <!-- 支付单 -->
        <section class="ui-card overflow-hidden">
          <header class="border-b border-[var(--color-line-soft)] px-4 py-3">
            <h2 class="flex items-center gap-2 text-[15px] font-black text-[var(--color-ink)]">
              <UIcon name="lucide:credit-card" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
              {{ t('order.paymentsTitle') }}
            </h2>
          </header>
          <ul v-if="payments.length" class="divide-y divide-[var(--color-line-soft)]">
            <li v-for="payment in payments" :key="payment.paymentNo || payment.id" class="px-4 py-3.5">
              <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                <span class="flex flex-wrap items-center gap-x-2 gap-y-1">
                  <span class="text-[13px] font-bold tabular text-[var(--color-ink)]">{{ payment.paymentNo }}</span>
                  <!-- 支付单状态文字用后端 statusLabel -->
                  <OrderStatusTag :label="payment.statusLabel || payment.status || ''" :status="payment.status" />
                </span>
                <span class="text-[13px] font-bold tabular text-[var(--color-ink)]">{{ format(payment.paidAmount ?? payment.amount) }}</span>
              </div>
              <dl class="mt-2 grid gap-x-4 gap-y-1 text-[12px] text-[var(--color-muted)] sm:grid-cols-2">
                <div class="flex gap-2">
                  <dt>{{ t('order.paymentChannel') }}</dt>
                  <dd class="tabular">{{ payment.channel || '--' }}<span v-if="payment.payType"> / {{ payment.payType }}</span></dd>
                </div>
                <div class="flex gap-2">
                  <dt>{{ t('order.paymentPaidTime') }}</dt>
                  <dd class="tabular">{{ payment.payTime || '--' }}</dd>
                </div>
                <div v-if="payment.channelTradeNo" class="flex gap-2">
                  <dt>{{ t('order.paymentChannelTradeNo') }}</dt>
                  <dd class="min-w-0 break-all tabular">{{ payment.channelTradeNo }}</dd>
                </div>
                <div v-if="hasNumber(payment.refundableAmount)" class="flex gap-2">
                  <dt>{{ t('order.paymentRefundable') }}</dt>
                  <dd class="tabular">{{ format(payment.refundableAmount) }}</dd>
                </div>
              </dl>
            </li>
          </ul>
          <p v-else class="px-4 py-4 text-[13px] text-[var(--color-muted)]">{{ t('order.noPayments') }}</p>
        </section>

        <!-- 退款单 -->
        <section class="ui-card overflow-hidden">
          <header class="border-b border-[var(--color-line-soft)] px-4 py-3">
            <h2 class="flex items-center gap-2 text-[15px] font-black text-[var(--color-ink)]">
              <UIcon name="lucide:rotate-ccw" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
              {{ t('order.refundsTitle') }}
            </h2>
          </header>
          <ul v-if="refunds.length" class="divide-y divide-[var(--color-line-soft)]">
            <li v-for="refund in refunds" :key="refund.refundNo || refund.id" class="px-4 py-3.5">
              <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                <span class="flex flex-wrap items-center gap-x-2 gap-y-1">
                  <span class="text-[13px] font-bold tabular text-[var(--color-ink)]">{{ refund.refundNo }}</span>
                  <OrderStatusTag :label="refund.statusLabel || refund.status || ''" :status="refund.status" />
                  <span v-if="refund.refundTypeLabel" class="text-[12px] text-[var(--color-muted)]">{{ refund.refundTypeLabel }}</span>
                </span>
                <span class="flex items-center gap-3">
                  <span class="text-[13px] font-bold tabular text-[var(--color-ink)]">{{ format(refund.refundAmount) }}</span>
                  <!--
                    撤销退款的可见性判断是本页**唯一**一处前端状态判断：
                    后端没有下发 canWithdraw 之类的字段，而"申请中"才可撤销。
                    真正的拦截仍在后端（非申请中会返回中文业务错误），这里只是不发无效请求。
                  -->
                  <button
                    v-if="isWithdrawable(refund)"
                    type="button"
                    class="inline-flex cursor-pointer items-center rounded-full border border-[var(--color-line)] px-3.5 py-1.5 text-[12px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-error)] hover:text-[var(--color-error)] motion-reduce:transition-none"
                    @click="emit('withdraw', refund)"
                  >
                    {{ t('order.actionWithdraw') }}
                  </button>
                </span>
              </div>
              <dl class="mt-2 grid gap-x-4 gap-y-1 text-[12px] text-[var(--color-muted)] sm:grid-cols-2">
                <div class="flex gap-2">
                  <dt>{{ t('order.refundApplyTime') }}</dt>
                  <dd class="tabular">{{ refund.applyTime || '--' }}</dd>
                </div>
                <div class="flex gap-2">
                  <dt>{{ t('order.refundQuantityLabel') }}</dt>
                  <dd class="tabular">{{ refund.totalQuantity ?? 0 }}</dd>
                </div>
                <div v-if="refund.applyReason" class="flex gap-2">
                  <dt>{{ t('order.refundReason') }}</dt>
                  <dd>{{ refundReasonLabel(refund.applyReason) }}</dd>
                </div>
                <div v-if="refund.applyDesc" class="flex gap-2">
                  <dt>{{ t('order.refundDesc') }}</dt>
                  <dd class="min-w-0 flex-1">{{ refund.applyDesc }}</dd>
                </div>
                <div v-if="refund.auditRemark" class="flex gap-2">
                  <dt>{{ t('order.refundAuditRemark') }}</dt>
                  <dd class="min-w-0 flex-1">{{ refund.auditRemark }}</dd>
                </div>
                <div v-if="refund.rejectReason" class="flex gap-2">
                  <dt>{{ t('order.refundRejectReason') }}</dt>
                  <dd class="min-w-0 flex-1 text-[var(--color-error-600)]">{{ refund.rejectReason }}</dd>
                </div>
                <div v-if="refund.refundTime" class="flex gap-2">
                  <dt>{{ t('order.refundTime') }}</dt>
                  <dd class="tabular">{{ refund.refundTime }}</dd>
                </div>
              </dl>
            </li>
          </ul>
          <p v-else class="px-4 py-4 text-[13px] text-[var(--color-muted)]">{{ t('order.noRefunds') }}</p>
        </section>

        <!-- 时间线 -->
        <section class="ui-card p-5">
          <h2 class="flex items-center gap-2 text-[15px] font-black text-[var(--color-ink)]">
            <UIcon name="lucide:history" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
            {{ t('order.timelineTitle') }}
          </h2>
          <ol v-if="events.length" class="mt-4 space-y-4">
            <li v-for="(event, index) in events" :key="event.id || index" class="relative flex gap-3 pl-1">
              <span class="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-[var(--color-brand-400)]" aria-hidden="true"></span>
              <span class="min-w-0 flex-1">
                <!-- 事件类型文字用后端 eventTypeLabel -->
                <span class="flex flex-wrap items-center gap-x-2 gap-y-1">
                  <span class="text-[13px] font-bold text-[var(--color-ink)]">{{ event.eventTypeLabel || event.eventType || '--' }}</span>
                  <span v-if="event.operatorTypeLabel" class="text-[12px] text-[var(--color-muted)]">{{ event.operatorTypeLabel }}</span>
                  <span class="text-[12px] tabular text-[var(--color-muted)]">{{ event.occurredTime || event.createTime || '' }}</span>
                </span>
                <span v-if="event.remark" class="mt-0.5 block text-[12px] leading-relaxed text-[var(--color-muted)]">{{ event.remark }}</span>
                <span v-if="isPositive(event.amount)" class="mt-0.5 block text-[12px] tabular text-[var(--color-ink-soft)]">
                  {{ format(event.amount) }}
                </span>
              </span>
            </li>
          </ol>
          <p v-else class="mt-4 text-[13px] text-[var(--color-muted)]">{{ t('order.noEvents') }}</p>
        </section>
</template>

<script setup lang="ts">
import type {
  OrderDetailVO,
  OrderEventVO,
  OrderItemVO,
  OrderPaymentVO,
  OrderRefundVO,
} from '~~/composables/useOrderDetail'
import { isPositive } from '~~/composables/useApiError'
import { useStorePrice } from '~~/composables/useProducts'
import { hasNumber, isWithdrawable, refundReasonKey } from '~~/utils/orderRefund'

defineProps<{
  detail: OrderDetailVO
  items: OrderItemVO[]
  payments: OrderPaymentVO[]
  refunds: OrderRefundVO[]
  events: OrderEventVO[]
}>()

const emit = defineEmits<{
  /** 点「撤销退款」：弹层与提交由页面负责 */
  (e: 'withdraw', refund: OrderRefundVO): void
}>()

const { t } = useI18n()
const { format } = useStorePrice()

/** 退款原因 code → 界面文案（映射表在 utils/orderRefund.ts，与后端字典一致） */
function refundReasonLabel(reason: string): string {
  const key = refundReasonKey(reason)
  return key ? t(key) : reason
}
</script>
