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

        <!-- 商品明细 -->
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
                    @click="askWithdraw(refund)"
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

import { toNumberOrNull } from '~~/types/shop-product'
import { useStorePrice } from '~~/composables/useProducts'
import { isPositive, isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'
import { useLoginLink } from '~~/composables/useLoginLink'

/**
 * 订单详情。
 *
 * 展示：状态 / 金额明细 / 商品 / 收货信息 / 物流 / 支付单 / 退款单 / 时间线。
 * 操作：取消订单 / 去支付 / 确认收货 / 申请退款 / 撤销退款。
 *
 * 三条硬规则：
 *   1) 四个订单级操作按钮的可见性**只用后端下发的 canPay / canCancel / canReceive / canRefund**；
 *   2) 所有"中文标签"（订单状态、支付单状态、退款状态与类型、事件类型）都用后端字段；
 *   3) 金额只做两位小数格式化，不做任何运算。
 *
 * 唯一的例外是「撤销退款」按钮的可见性：后端没有下发对应标记，
 * 而可撤销的只有"申请中"的退款单，所以这里用退款单自身的 status 判断是否存在按钮
 * （是否真的允许撤销仍由后端裁决，前端只是不发必然失败的请求）。
 */

/** 订单明细（后端 StoreOrderItemVO） */
interface OrderItemVO {
  id?: string | null
  productId?: string | null
  skuName?: string | null
  productName?: string | null
  productImage?: string | null
  price?: number | null
  quantity?: number | null
  productAmount?: number | null
  actualAmount?: number | null
  promotionTitle?: string | null
  refundQuantity?: number | null
  refundAmount?: number | null
  refundStatus?: string | null
}

/** 支付单（后端 OrderPaymentVO） */
interface OrderPaymentVO {
  id?: string | null
  paymentNo?: string | null
  channel?: string | null
  payType?: string | null
  status?: string | null
  statusLabel?: string | null
  amount?: number | null
  paidAmount?: number | null
  refundableAmount?: number | null
  payTime?: string | null
  channelTradeNo?: string | null
}

/** 退款单（后端 OrderRefundVO） */
interface OrderRefundVO {
  id?: string | null
  refundNo?: string | null
  status?: string | null
  statusLabel?: string | null
  refundType?: string | null
  refundTypeLabel?: string | null
  refundAmount?: number | null
  totalQuantity?: number | null
  applyReason?: string | null
  applyDesc?: string | null
  applyTime?: string | null
  auditRemark?: string | null
  rejectReason?: string | null
  refundTime?: string | null
}

/** 订单节点（后端 OrderEventVO） */
interface OrderEventVO {
  id?: string | null
  eventType?: string | null
  eventTypeLabel?: string | null
  operatorTypeLabel?: string | null
  amount?: number | null
  remark?: string | null
  occurredTime?: string | null
  createTime?: string | null
}

/** 订单详情（后端 StoreOrderDetailVO） */
interface OrderDetailVO {
  orderNo: string
  orderStatus?: string | null
  orderStatusLabel?: string | null
  payStatus?: string | null
  refundStatus?: string | null
  storeName?: string | null
  payAmount?: number | null
  paidAmount?: number | null
  refundAmount?: number | null
  discountAmount?: number | null
  freightAmount?: number | null
  totalAmount?: number | null
  productAmount?: number | null
  promotionDiscount?: number | null
  totalQuantity?: number | null
  createTime?: string | null
  payDeadline?: string | null
  remainPaySeconds?: number | null
  logisticsCompany?: string | null
  logisticsNo?: string | null
  cancelReason?: string | null
  receiverName?: string | null
  receiverPhone?: string | null
  receiverProvince?: string | null
  receiverCity?: string | null
  receiverDistrict?: string | null
  receiverAddress?: string | null
  receiverPostalCode?: string | null
  receiverTag?: string | null
  buyerRemark?: string | null
  items?: OrderItemVO[] | null
  payments?: OrderPaymentVO[] | null
  refunds?: OrderRefundVO[] | null
  events?: OrderEventVO[] | null
  canPay?: boolean | null
  canCancel?: boolean | null
  canReceive?: boolean | null
  canRefund?: boolean | null
}

const { t, locale } = useI18n()
const localePath = useLocalePath()
const route = useRoute()
const toast = useToast()
const { format } = useStorePrice()

const orderNo = computed(() => String(route.params.orderNo ?? '').trim())

const loading = ref(false)
const loadError = ref('')
const unauthorized = ref(false)
const detail = ref<OrderDetailVO | null>(null)

const receiveState = reactive({ open: false, pending: false })
const cancelState = reactive({ open: false })
const refundState = reactive({ open: false })
const withdrawState = reactive({ open: false, pending: false, refundNo: '' })

/** 倒计时（与支付页同口径：用 remainPaySeconds 本地锚定，避免时区解析问题） */
const deadlineAt = ref<number | null>(null)
const remainSeconds = ref(0)
const nowTick = ref(Date.now())
let tickTimer: ReturnType<typeof setInterval> | null = null

const items = computed(() => (Array.isArray(detail.value?.items) ? detail.value!.items! : []))
const payments = computed(() => (Array.isArray(detail.value?.payments) ? detail.value!.payments! : []))
const refunds = computed(() => (Array.isArray(detail.value?.refunds) ? detail.value!.refunds! : []))
const events = computed(() => (Array.isArray(detail.value?.events) ? detail.value!.events! : []))

const expired = computed(() => remainSeconds.value <= 0 && deadlineAt.value !== null)

const countdownText = computed(() => {
  const total = Math.max(0, remainSeconds.value)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  return hours > 0 ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`
})

/** 完整收货地址：后端把省市区与详细地址分开给，这里只做拼接展示 */
const receiverAddressText = computed(() => {
  const source = detail.value
  if (!source) return ''
  const region = [source.receiverProvince, source.receiverCity, source.receiverDistrict].filter(Boolean).join(' ')
  return [region, source.receiverAddress].filter(Boolean).join(' ')
})

const loginLink = useLoginLink()
const payLink = computed(() => localePath({ path: '/checkout/pay', query: { orderNo: orderNo.value } }))

/**
 * 退款原因的中文文案。
 * 后端字典 shop_refund_reason 只有英文 code，没有随退款单下发中文标签
 * （退款单出参里 applyReason 是 code，statusLabel / refundTypeLabel 才是中文）。
 * 所以这里必须做一次 code -> 界面文案的映射，取值集合与后端字典严格一致。
 */
const REFUND_REASON_LABELS: Record<string, string> = {
  no_need: 'order.reasonNoNeed',
  duplicate: 'order.reasonDuplicate',
  mismatch: 'order.reasonMismatch',
  quality: 'order.reasonQuality',
  missing: 'order.reasonMissing',
  logistics: 'order.reasonLogistics',
  other: 'order.reasonOther',
}

function refundReasonLabel(reason: string): string {
  const key = REFUND_REASON_LABELS[reason]
  return key ? t(key) : reason
}

/** 有值（含 0）即 true —— 用于"这一行该不该渲染" */
function hasNumber(value: unknown): boolean {
  const num = toNumberOrNull(value)
  return num !== null
}

function isWithdrawable(refund: OrderRefundVO): boolean {
  return String(refund.status ?? '').toUpperCase() === 'APPLYING'
}

function startTicking() {
  stopTicking()
  if (deadlineAt.value === null) return
  tickTimer = setInterval(() => {
    nowTick.value = Date.now()
    const remain = Math.round((deadlineAt.value! - nowTick.value) / 1000)
    remainSeconds.value = remain > 0 ? remain : 0
  }, 1000)
}

function stopTicking() {
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = null
  }
}

function anchorDeadline(order: OrderDetailVO) {
  const remain = toNumberOrNull(order.remainPaySeconds)
  if (remain !== null && remain >= 0) {
    remainSeconds.value = Math.trunc(remain)
    deadlineAt.value = Date.now() + remainSeconds.value * 1000
    return
  }
  const raw = order.payDeadline ? String(order.payDeadline).replace(' ', 'T') : ''
  const parsed = raw ? new Date(raw) : null
  if (parsed && !Number.isNaN(parsed.getTime())) {
    deadlineAt.value = parsed.getTime()
    remainSeconds.value = Math.max(0, Math.round((deadlineAt.value - Date.now()) / 1000))
    return
  }
  deadlineAt.value = null
  remainSeconds.value = 0
}

async function loadDetail() {
  if (!orderNo.value) {
    loadError.value = t('order.notFoundDesc')
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const result = await $fetch<OrderDetailVO>('/api/order/detail', {
      query: { orderNo: orderNo.value, lang: locale.value },
    })
    detail.value = result
    // 只有待支付订单需要倒计时
    if (result?.canPay) {
      anchorDeadline(result)
      startTicking()
    } else {
      stopTicking()
      deadlineAt.value = null
      remainSeconds.value = 0
    }
    unauthorized.value = false
  } catch (error) {
    if (isUnauthorizedError(error)) {
      unauthorized.value = true
      return
    }
    loadError.value = readErrorMessage(error, t('order.loadErrorDesc'))
  } finally {
    loading.value = false
  }
}

async function confirmReceive() {
  receiveState.pending = true
  try {
    await $fetch('/api/order/confirm-receive', {
      method: 'POST',
      body: { orderNo: orderNo.value, lang: locale.value },
    })
    toast.add({ title: t('order.receivedToast'), color: 'success', icon: 'lucide:check' })
    receiveState.open = false
    await loadDetail()
  } catch (error) {
    toast.add({
      title: t('order.actionFailed'),
      description: readErrorMessage(error, t('order.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    receiveState.pending = false
  }
}

function askWithdraw(refund: OrderRefundVO) {
  withdrawState.refundNo = String(refund.refundNo ?? '')
  withdrawState.open = true
}

async function confirmWithdraw() {
  if (!withdrawState.refundNo) return
  withdrawState.pending = true
  try {
    // refundNo 走查询参数（后端方法签名没有 @RequestBody），BFF 已按这个位置转发
    await $fetch('/api/order/refund/withdraw', {
      method: 'POST',
      query: { refundNo: withdrawState.refundNo, lang: locale.value },
    })
    toast.add({ title: t('order.withdrawnToast'), color: 'success', icon: 'lucide:check' })
    withdrawState.open = false
    await loadDetail()
  } catch (error) {
    toast.add({
      title: t('order.actionFailed'),
      description: readErrorMessage(error, t('order.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    withdrawState.pending = false
  }
}

/**
 * 退款申请成功后的刷新。
 *
 * 这里走**独立的退款单接口**（GET /api/order/refund/list?orderNo=）而不是重新拉整个详情：
 * 退款审核状态是变化最快的数据，单独刷新它更省带宽，也让"刚提交的退款单"立刻可见。
 * 详情里的其它字段（状态、金额）也顺手刷新一次，避免订单状态已变而页面还停在旧值。
 */
async function onRefundApplied() {
  // 顺序很重要：先刷新订单主体（订单状态 / refundStatus / canRefund 都会变），
  // 再用独立的退款单接口覆盖退款单列表 —— 审核状态是变化最快的数据，
  // 它拿到的必然不比详情旧，放在后面就不会被详情里的旧列表盖回去。
  await loadDetail()
  try {
    const list = await $fetch<OrderRefundVO[]>('/api/order/refund/list', {
      query: { orderNo: orderNo.value, lang: locale.value },
    })
    if (detail.value && Array.isArray(list)) {
      detail.value = { ...detail.value, refunds: list }
    }
  } catch (error) {
    toast.add({
      title: t('order.refundsTitle'),
      description: readErrorMessage(error, t('order.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  }
}

onMounted(loadDetail)
onBeforeUnmount(stopTicking)

useSeoMeta({
  title: () => t('order.detailTitle'),
  robots: 'noindex, nofollow',
})
</script>
