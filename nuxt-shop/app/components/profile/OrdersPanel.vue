<!--
  我的订单面板

  从 pages/profile.vue 拆出（P1-4 巨型文件拆分第二步）。
  数据由 composables/useOrderList.ts 提供 —— 页面与面板共用同一份 useState 状态，
  所以这里翻页 / 确认收货后刷新，顶部「订单数」统计卡会同步变化。

  与本面板一起搬过来的还有三个订单操作弹层（确认收货 / 取消订单 / 申请退款）：
  它们只服务于订单列表，留在页面根节点会让人误以为与其它面板有关。
-->
<template>
  <div>
    <!-- 错误 / 骨架 / 列表 / 空态：四态 fade 切换，骨架↔内容不再硬切 -->
    <Transition name="fade" mode="out-in">
      <!-- 加载失败：把后端/网络的原话展示出来，用户才知道该重试还是先别管 -->
      <div v-if="ordersError" key="error" class="mt-5 flex items-start gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-4 py-3 text-[13px] font-medium text-[var(--color-error-600)]">
        <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        <span class="min-w-0 flex-1">
          {{ t('order.loadError') }}：{{ ordersError }}
          <button
            type="button"
            class="ml-2 cursor-pointer font-bold underline underline-offset-2 disabled:opacity-45"
            :disabled="ordersLoading"
            @click="loadOrders()"
          >
            {{ t('common.retry') }}
          </button>
        </span>
      </div>

      <!-- 加载中：骨架屏，固定 3 行（不猜每页条数） -->
      <ul v-else-if="ordersLoading && !orders.length" key="loading" class="mt-4 space-y-3" aria-busy="true" :aria-label="t('common.loading')">
        <li v-for="index in 3" :key="`order-skeleton-${index}`" class="space-y-2.5 rounded-[var(--radius-card-sm)] bg-[var(--color-line-soft)]/50 p-4">
          <span class="block h-4 w-1/3 animate-pulse rounded bg-[var(--color-line-soft)]"></span>
          <span class="block h-3 w-2/3 animate-pulse rounded bg-[var(--color-line-soft)]"></span>
        </li>
      </ul>

      <!-- 有订单 -->
      <div v-else-if="orders.length" key="list">
        <p class="mt-1.5 text-[13px] text-[var(--color-muted)]">
          {{ t('order.pagination') }} · {{ t('order.pageInfo', { current: currentOrderPage, total: orderTotalPages }) }}
        </p>

        <TransitionGroup name="list" tag="ul" class="relative mt-4 space-y-3">
        <li
          v-for="order in orders"
          :key="order.orderNo"
          class="rounded-[var(--radius-card-sm)] border border-[var(--color-line-soft)] p-4"
        >
          <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
            <div class="flex min-w-0 flex-wrap items-center gap-x-2.5 gap-y-1">
              <span class="text-[13px] font-bold tabular text-[var(--color-ink)]">{{ order.orderNo }}</span>
              <!-- 状态文字用后端 orderStatusLabel；status 只决定配色，前端不自建映射 -->
              <OrderStatusTag :label="order.orderStatusLabel || order.orderStatus || ''" :status="order.orderStatus" />
            </div>
            <span class="text-[12px] tabular text-[var(--color-muted)]">{{ formatDateTime(order.createTime, locale) }}</span>
          </div>

          <!-- 商品缩略图条：只展示后端返回的快照图，没有就留空位（不塞占位图） -->
          <div v-if="orderItems(order).length" class="mt-3 flex items-center gap-2">
            <span
              v-for="(item, index) in orderItems(order).slice(0, 4)"
              :key="`${order.orderNo}-thumb-${item.id || index}`"
              class="h-12 w-12 shrink-0 overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-line-soft)]"
            >
              <ShopImage
                :src="item.productImage"
                :alt="item.productName || ''"
                width="48"
                height="48"
                class="h-full w-full object-cover"
                icon-class="h-5 w-5"
              />
            </span>
            <!--
              这里只显示第一个商品的名称。**刻意不在这里再写一个数量/单位**：
                - 件数由下方 `order.totalQuantity` 统一表达（「共 N 件」），
                  这里再写一次就会出现两个含义不同的数字并列；
                - 曾经这里写过 t('shop.itemsUnit')，但 shop.itemsUnit 是**裸单位词**
                  （zh "件" / en "items" / fr "articles"，值里没有 {count} 占位符），
                  传进去的 count 会被 i18n 静默忽略，渲染成孤零零一个「件」。
                  本项目其它地方的正确用法都是「数字 + 空格 + 单位」
                  （见 CartLineItem.vue / CartSummaryCard.vue / cart.vue）。
                - 商品种类数（orderItems(order).length）也与「件」不是一个概念，
                  不能拿它当数量补上去。
              多条商品的存在感由左边那排缩略图表达，所以这里删掉不丢信息。
            -->
            <span class="min-w-0 flex-1 truncate text-[12px] text-[var(--color-muted)]">
              {{ orderItems(order)[0]?.productName || '' }}
            </span>
          </div>

          <div class="mt-3 flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-t border-[var(--color-line-soft)] pt-3">
            <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1 text-[12px] text-[var(--color-muted)]">
              <span>{{ t('order.totalQuantity', { count: order.totalQuantity ?? 0 }) }}</span>
              <span v-if="isPositive(order.refundAmount)" class="text-[var(--color-warning-600)]">
                {{ t('order.refundAmount') }} {{ format(order.refundAmount) }}
              </span>
              <span>
                {{ t('order.payAmount') }}
                <span class="price ml-1 text-[16px]">{{ format(order.payAmount) }}</span>
              </span>
            </div>

            <div class="flex flex-wrap items-center gap-2">
              <NuxtLinkLocale :to="orderDetailLink(order.orderNo)" class="ui-pill ui-pill-ghost h-9 px-4 text-[12px]">
                {{ t('order.viewDetail') }}
              </NuxtLinkLocale>
              <!-- 按钮可见性完全由后端 canXxx 决定，前端不重算状态机 -->
              <button
                v-if="order.canCancel"
                type="button"
                class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[12px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-error)] hover:text-[var(--color-error)] motion-reduce:transition-none"
                @click="openCancel(order.orderNo)"
              >
                {{ t('order.actionCancel') }}
              </button>
              <button
                v-if="order.canRefund"
                type="button"
                class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[12px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-brand-500)] hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
                @click="openRefund(order)"
              >
                {{ t('order.actionRefund') }}
              </button>
              <button
                v-if="order.canReceive"
                type="button"
                class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[12px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-success)] hover:text-[var(--color-success-600)] motion-reduce:transition-none"
                @click="openReceive(order.orderNo)"
              >
                {{ t('order.actionReceive') }}
              </button>
              <NuxtLinkLocale v-if="order.canPay" :to="payLink(order.orderNo)" class="ui-pill ui-pill-brand h-9 px-5 text-[12px]">
                {{ t('order.actionPay') }}
              </NuxtLinkLocale>
            </div>
          </div>
        </li>
        </TransitionGroup>

        <!-- 分页：只有一页时不渲染，避免噪音 -->
        <PaginationBar
          :current="currentOrderPage"
          :total="orderTotalPages"
          :disabled="ordersLoading"
          size="sm"
          margin="mt-6"
          :label="t('order.pagination')"
          :prev-label="t('order.prev')"
          :next-label="t('order.next')"
          :info-label="t('order.pageInfo', { current: currentOrderPage, total: orderTotalPages })"
          @change="goToOrderPage"
        />

        <div class="mt-5 flex justify-center">
          <!-- NuxtLinkLocale 自己会补 locale 前缀，这里不能再套 localePath（否则会变成 /zh/zh/orders） -->
          <NuxtLinkLocale to="/orders" class="ui-pill ui-pill-ghost h-10 px-6 text-[13px]">
            <UIcon name="lucide:arrow-right" class="h-4 w-4" aria-hidden="true" />
            <!--
              这里**不能用** shop.profileOrders：它的中文值是「订单数」，那是统计卡的**名词**口径，
              当成"跳转全部订单"的链接文案会读成一句没头没尾的统计项。
              用 shop.profileMyOrders（我的订单 / My orders / Mes commandes）——与目标页标题同义，
              用户一眼能看懂点了会去哪；且不新增 i18n key。
            -->
            {{ t('shop.profileMyOrders') }}
          </NuxtLinkLocale>
        </div>
      </div>

      <!-- 真的没有订单 -->
      <div v-else key="empty" class="flex flex-col items-center justify-center py-14 text-center">
        <span class="flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-brand-50)] text-[var(--color-brand-400)]">
          <UIcon name="lucide:package-open" class="h-9 w-9" aria-hidden="true" />
        </span>
        <p class="mt-5 text-sm font-bold text-[var(--color-ink)]">
          {{ t('order.empty') }}
        </p>
        <p class="mt-2 max-w-md text-[13px] text-[var(--color-muted)]">
          {{ t('order.emptyDesc') }}
        </p>
        <NuxtLinkLocale to="/products" class="ui-pill ui-pill-brand mt-5 h-10 px-6 text-[13px]">
          <UIcon name="lucide:arrow-right" class="h-4 w-4" aria-hidden="true" />
          {{ t('order.emptyAction') }}
        </NuxtLinkLocale>
      </div>
    </Transition>

    <!-- 订单操作：确认收货 / 取消订单 / 申请退款。全部复用 /orders 页同一批组件与接口，不再实现一遍 -->
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
          <AsyncButton
            type="button"
            class="cursor-pointer rounded-full bg-[var(--color-brand-cta)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-brand-cta-hover)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
            :pending="receiveState.pending"
            :pending-label="t('common.loading')"
            @click="confirmReceive"
          >
            {{ t('order.receiveConfirm') }}
          </AsyncButton>
        </div>
      </template>
    </UModal>

    <OrderCancelModal v-model:open="cancelState.open" :order-no="cancelState.orderNo" @canceled="loadOrders()" />

    <RefundApplyModal
      v-model:open="refundState.open"
      :order-no="refundState.orderNo"
      :items="refundState.items"
      @applied="loadOrders()"
    />
  </div>
</template>

<script setup lang="ts">
import type { OrderItemVO, OrderListVO } from '~~/types/shop-order'
import { isPositive, readErrorMessage } from '~~/composables/useApiError'
import { formatDateTime } from '~~/composables/useDateTimeFormat'
import { useOrderList } from '~~/composables/useOrderList'
import { useStorePrice } from '~~/composables/useProducts'

const { t, locale } = useI18n()
const toast = useToast()
const { format } = useStorePrice()

const {
  orders,
  loading: ordersLoading,
  error: ordersError,
  totalPages: orderTotalPages,
  currentPage: currentOrderPage,
  load: loadOrders,
  goToPage: goToOrderPage,
} = useOrderList()

// 弹层状态留在面板内：它们只服务于订单列表
const receiveState = reactive({ open: false, orderNo: '', pending: false })
const cancelState = reactive({ open: false, orderNo: '' })
const refundState = reactive<{ open: boolean, orderNo: string, items: OrderItemVO[] }>({
  open: false,
  orderNo: '',
  items: [],
})

// 注意：这些链接都直接喂给 NuxtLinkLocale，它自己会补 locale 前缀，
// 所以这里**只给无前缀路径**；再套一层 localePath 会得到 /zh/zh/orders 这种坏链接。
const orderDetailLink = (orderNo: string) => `/orders/${orderNo}`
const payLink = (orderNo: string) => ({ path: '/checkout/pay', query: { orderNo } })

/** 明细数组归一化：后端给 null 时不能当数组用，否则模板里 .slice() 会炸 */
function orderItems(order: OrderListVO): OrderItemVO[] {
  return Array.isArray(order.items) ? order.items : []
}

function openReceive(orderNo: string) {
  receiveState.orderNo = orderNo
  receiveState.open = true
}

function openCancel(orderNo: string) {
  cancelState.orderNo = orderNo
  cancelState.open = true
}

function openRefund(order: OrderListVO) {
  refundState.orderNo = order.orderNo
  refundState.items = orderItems(order)
  refundState.open = true
}

/** 确认收货：成功后就地刷新列表（订单状态会变成"已完成"） */
async function confirmReceive() {
  receiveState.pending = true
  try {
    await $fetch('/api/order/confirm-receive', {
      method: 'POST',
      body: { orderNo: receiveState.orderNo, lang: locale.value },
    })
    toast.add({ title: t('order.receivedToast'), color: 'success', icon: 'lucide:check' })
    receiveState.open = false
    await loadOrders()
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
</script>
