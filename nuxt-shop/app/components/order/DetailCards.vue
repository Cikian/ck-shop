<!--
  订单详情正文卡片：商品明细 / 金额明细 / 收货信息 / 支付单 / 退款单 / 订单时间线

  从 pages/orders/[orderNo].vue 拆出（订单详情页拆组件第一步）。
  这些卡片是**只读展示**：数据全部由 props 传入，交互只有两处 ——「撤销退款」与
  商品行内的「去评价」—— 它们都只把事件抛回页面（页面持有弹层与提交状态），
  组件本身不发请求。

  商品行的「去评价 / 已评价」由 reviewItems（useOrderReviews().items）决定：
  按 orderItemId ↔ 订单行 id 匹配；匹配不到的行不渲染任何评价入口。

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
            <li v-for="(row, index) in rows" :key="row.item.id || index" class="flex gap-3 px-4 py-4">
              <span class="h-[68px] w-[68px] shrink-0 overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-line-soft)]">
                <ShopImage
                  :src="row.item.productImage"
                  :alt="row.item.productName || ''"
                  width="68"
                  height="68"
                  class="h-full w-full object-cover"
                  icon-class="h-5 w-5"
                />
              </span>
              <div class="min-w-0 flex-1">
                <p class="line-clamp-2 text-[14px] font-bold text-[var(--color-ink)]">{{ row.item.productName || '—' }}</p>
                <p class="mt-1 truncate text-[12px] text-[var(--color-muted)]">{{ row.item.skuName || t('cart.noSpec') }}</p>
                <p v-if="row.item.promotionTitle" class="mt-1 truncate text-[12px] text-[var(--color-brand-600)]">
                  {{ t('order.promotion') }}：{{ row.item.promotionTitle }}
                </p>
                <p v-if="row.item.refundQuantity" class="mt-1 text-[12px] text-[var(--color-warning-600)]">
                  {{ t('order.refundedQuantity', { count: row.item.refundQuantity }) }}
                  <span v-if="isPositive(row.item.refundAmount)">· {{ format(row.item.refundAmount) }}</span>
                </p>
              </div>
              <div class="shrink-0 text-right">
                <p class="text-[13px] tabular text-[var(--color-ink)]">{{ format(row.item.price) }}</p>
                <p class="mt-1 text-[12px] tabular text-[var(--color-muted)]">× {{ row.item.quantity ?? 0 }}</p>
                <p class="mt-1 text-[13px] font-bold tabular text-[var(--color-ink)]">{{ format(row.item.actualAmount ?? row.item.productAmount) }}</p>
                <!--
                  「去评价」入口：只在这一行出现在待评价明细里时才渲染。
                  整单没有可评价项 → reviewItems 为空 → 这里什么都没有（不显示任何入口）。
                  互斥：已评价的行只给不可点的「已评价」，绝不出现第二颗按钮。
                -->
                <button
                  v-if="row.reviewable && !row.reviewable.reviewed"
                  type="button"
                  class="mt-2 inline-flex h-8 cursor-pointer items-center rounded-full border border-[var(--color-brand-300)] px-3.5 text-[12px] font-medium text-[var(--color-brand-600)] transition-colors duration-200 hover:border-[var(--color-brand-500)] hover:bg-[var(--color-brand-50)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-500)] motion-reduce:transition-none"
                  :aria-label="reviewEntryLabel(row.item)"
                  @click="row.reviewable && emit('review', row.reviewable)"
                >
                  {{ t('review.pendingEntry') }}
                </button>
                <span
                  v-else-if="row.reviewable"
                  class="mt-2 inline-flex h-8 items-center gap-1 rounded-full bg-[var(--color-surface-secondary)] px-3.5 text-[12px] font-medium text-[var(--color-muted)]"
                >
                  <UIcon name="lucide:check" class="h-3.5 w-3.5" aria-hidden="true" />
                  {{ t('review.pendingDone') }}
                </span>
              </div>
            </li>
          </ul>
          <!-- 待评价明细拉取失败：如实说明，而不是让入口静静消失 -->
          <p v-if="reviewLoadFailed" class="border-t border-[var(--color-line-soft)] px-4 py-3 text-[12px] text-[var(--color-muted)]">
            {{ t('review.loadError') }}
          </p>
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
import type { StoreReviewableItem } from '~~/types/shop-review'
import { isPositive } from '~~/composables/useApiError'
import { useStorePrice } from '~~/composables/useProducts'
import { hasNumber, isWithdrawable, refundReasonKey } from '~~/utils/orderRefund'

const props = withDefaults(defineProps<{
  detail: OrderDetailVO
  items: OrderItemVO[]
  payments: OrderPaymentVO[]
  refunds: OrderRefundVO[]
  events: OrderEventVO[]
  /** 待评价明细（composables/useReviews.ts 的 items）：为空 = 整单没有可评价项 */
  reviewItems?: StoreReviewableItem[]
  /** 待评价明细拉取失败（页面传入；只用于如实提示，不影响商品行本身的展示） */
  reviewLoadFailed?: boolean
}>(), {
  reviewItems: () => [],
  reviewLoadFailed: false,
})

const emit = defineEmits<{
  /** 点「撤销退款」：弹层与提交由页面负责 */
  (e: 'withdraw', refund: OrderRefundVO): void
  /** 点「去评价」：把这一行对应的待评价明细交给页面（页面打开弹窗并提交） */
  (e: 'review', item: StoreReviewableItem): void
}>()

const { t } = useI18n()
const { format } = useStorePrice()

/**
 * 订单行 + 该行的评价状态。
 *
 * 匹配键是 orderItemId ↔ 订单行 id（后端两处是同一个主键）。
 * 用 computed 一次算好，避免在模板 v-for 里反复查表 —— 也避免模板里出现 `!` 断言。
 */
const rows = computed(() => {
  const byItemId = new Map<string, StoreReviewableItem>()
  for (const reviewable of props.reviewItems) {
    if (reviewable.orderItemId) byItemId.set(reviewable.orderItemId, reviewable)
  }
  return props.items.map(item => ({
    item,
    reviewable: item.id ? byItemId.get(String(item.id)) ?? null : null,
  }))
})

/** 退款原因 code → 界面文案（映射表在 utils/orderRefund.ts，与后端字典一致） */
function refundReasonLabel(reason: string): string {
  const key = refundReasonKey(reason)
  return key ? t(key) : reason
}

/**
 * 「去评价」按钮的 aria-label：多行明细时按钮文字全是"去评价"，
 * 读屏用户逐个 Tab 过去分不清是哪件商品，所以把商品名一起报出来。
 */
function reviewEntryLabel(item: OrderItemVO): string {
  const name = String(item.productName ?? '').trim()
  return name ? `${t('review.pendingEntry')} ${name}` : t('review.pendingEntry')
}
</script>
