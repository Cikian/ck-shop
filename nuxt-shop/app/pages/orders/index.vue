<template>
  <div class="flex min-h-[calc(100vh-var(--nav-total-height))] flex-col bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto flex w-full max-w-7xl flex-1 flex-col px-2 pb-16 sm:px-3 lg:px-4">
      <header class="mb-5">
        <h1 class="text-[26px] font-black leading-none text-[var(--color-ink)] sm:text-[32px]">
          {{ t('order.title') }}
        </h1>
        <p class="mt-2 text-[13px] text-[var(--color-muted)]">{{ t('order.subtitle') }}</p>
      </header>

      <!-- 未登录 -->
      <section v-if="unauthorized" class="ui-card flex flex-1 flex-col items-center justify-center px-6 py-16 text-center">
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

      <template v-else>
        <!--
          状态筛选 tab。
          注意：这里的标签是**本页的固定筛选项文案**，不是"状态码 -> 中文"的映射 ——
          订单卡片上的状态文字一律来自后端返回的 orderStatusLabel。
          value 是后端 OrderQuery.orderStatus 的白名单取值。
        -->
        <div
          class="no-scrollbar -mx-2 mb-5 flex gap-2 overflow-x-auto px-2"
          role="tablist"
          :aria-label="t('order.statusFilterLabel')"
        >
          <button
            v-for="tab in STATUS_TABS"
            :key="tab.value || 'all'"
            type="button"
            role="tab"
            :aria-selected="activeStatus === tab.value"
            class="chip shrink-0"
            :class="activeStatus === tab.value ? 'chip-active' : ''"
            @click="changeStatus(tab.value)"
          >
            {{ t(tab.label) }}
          </button>
        </div>

        <!-- 已有列表时切换筛选/翻页：给一条轻量"更新中"反馈，避免点了没反应 -->
        <div
          v-if="loading && records.length"
          class="mb-3 flex items-center gap-2 text-[12px] text-[var(--color-muted)]"
          role="status"
        >
          <UIcon name="lucide:loader-circle" class="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" aria-hidden="true" />
          {{ t('common.loading') }}
        </div>

        <Transition name="fade" mode="out-in">
        <!-- 加载失败 -->
        <div v-if="loadError" key="error" class="ui-card flex flex-1 flex-col items-center justify-center px-6 py-16 text-center">
          <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error)]/10">
            <UIcon name="lucide:wifi-off" class="h-8 w-8 text-[var(--color-error)]" aria-hidden="true" />
          </span>
          <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('order.loadError') }}</h2>
          <p class="mt-2 text-sm text-[var(--color-muted)]">{{ loadError }}</p>
          <button type="button" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm" @click="loadOrders()">
            <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
            {{ t('common.retry') }}
          </button>
        </div>

        <!-- 骨架屏 -->
        <div v-else-if="ordersPending && !records.length" key="skeleton" class="space-y-4" aria-busy="true" :aria-label="t('common.loading')">
          <div v-for="index in 3" :key="index" class="ui-card space-y-3 p-5">
            <div class="h-4 w-1/3 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
            <div class="h-16 animate-pulse rounded-[var(--radius-card-sm)] bg-[var(--color-line-soft)]"></div>
          </div>
        </div>

        <!-- 空态 -->
        <div v-else-if="!records.length" key="empty" class="ui-card flex flex-1 flex-col items-center justify-center px-6 py-20 text-center">
          <span class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
            <UIcon name="lucide:package-open" class="h-9 w-9 text-[var(--color-brand-400)]" aria-hidden="true" />
          </span>
          <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('order.empty') }}</h2>
          <p class="mx-auto mt-2 max-w-md text-sm text-[var(--color-muted)]">{{ t('order.emptyDesc') }}</p>
          <NuxtLinkLocale to="/products" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm">
            <UIcon name="lucide:arrow-right" class="h-4 w-4" aria-hidden="true" />
            {{ t('order.emptyAction') }}
          </NuxtLinkLocale>
        </div>

        <!-- 订单列表 -->
        <div v-else key="content">
          <TransitionGroup name="list" tag="ul" class="relative space-y-4">
            <li v-for="order in records" :key="order.orderNo" class="ui-card overflow-hidden">
              <!-- 头：订单号 + 状态 + 时间 -->
              <header class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b border-[var(--color-line-soft)] px-4 py-3">
                <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span class="text-[13px] text-[var(--color-muted)]">{{ t('order.orderNo') }}</span>
                  <span class="text-[13px] font-bold tabular text-[var(--color-ink)]">{{ order.orderNo }}</span>
                  <!-- 状态文字用后端 orderStatusLabel；status 只决定配色 -->
                  <OrderStatusTag :label="order.orderStatusLabel || order.orderStatus || ''" :status="order.orderStatus" />
                </div>
                <span class="text-[12px] tabular text-[var(--color-muted)]">{{ formatDateTime(order.createTime, locale) }}</span>
              </header>

              <!-- 商品明细：只展示后端返回的快照，缩略图 + 名称 + 规格 + 单价×数量 -->
              <ul class="divide-y divide-[var(--color-line-soft)]">
                <li v-for="(item, index) in order.items" :key="`${order.orderNo}-${item.id || index}`" class="flex gap-3 px-4 py-3">
                  <span class="h-14 w-14 shrink-0 overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-line-soft)]">
                    <ShopImage
                      :src="item.productImage"
                      :alt="item.productName || ''"
                      :width="56"
                      :height="56"
                      class="h-full w-full object-cover"
                      icon="lucide:image"
                      icon-class="h-5 w-5 text-[var(--color-muted)]"
                    />
                  </span>
                  <span class="min-w-0 flex-1">
                    <span class="line-clamp-2 text-[13px] font-medium text-[var(--color-ink)]">{{ item.productName || '—' }}</span>
                    <span class="mt-1 block truncate text-[12px] text-[var(--color-muted)]">{{ item.skuName || t('cart.noSpec') }}</span>
                    <span v-if="item.refundQuantity" class="mt-1 block text-[12px] text-[var(--color-warning-600)]">
                      {{ t('order.refundedQuantity', { count: item.refundQuantity }) }}
                    </span>
                  </span>
                  <span class="shrink-0 text-right text-[12px] tabular text-[var(--color-muted)]">
                    <span class="block">{{ format(item.price) }}</span>
                    <span class="mt-0.5 block">× {{ item.quantity ?? 0 }}</span>
                  </span>
                </li>
              </ul>

              <!-- 汇总 + 操作 -->
              <footer class="flex flex-wrap items-center justify-between gap-x-4 gap-y-3 border-t border-[var(--color-line-soft)] bg-[var(--color-line-soft)]/40 px-4 py-3">
                <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] text-[var(--color-muted)]">
                  <span>{{ t('order.totalQuantity', { count: order.totalQuantity ?? 0 }) }}</span>
                  <span v-if="isPositive(order.refundAmount)" class="text-[var(--color-warning-600)]">
                    {{ t('order.refundAmount') }} {{ format(order.refundAmount) }}
                  </span>
                  <span>
                    {{ t('order.payAmount') }}
                    <span class="price ml-1 text-[18px]">{{ format(order.payAmount) }}</span>
                  </span>
                </div>

                <div class="flex flex-wrap items-center gap-2">
                  <NuxtLinkLocale :to="detailLink(order.orderNo)" class="ui-pill ui-pill-ghost h-9 px-4 text-[13px]">
                    {{ t('order.viewDetail') }}
                  </NuxtLinkLocale>

                  <!-- 按钮可见性完全由后端 canXxx 字段决定，前端不重算状态 -->
                  <button
                    v-if="order.canCancel"
                    type="button"
                    class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[13px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-error)] hover:text-[var(--color-error)] motion-reduce:transition-none"
                    @click="openCancel(order.orderNo)"
                  >
                    {{ t('order.actionCancel') }}
                  </button>
                  <button
                    v-if="order.canRefund"
                    type="button"
                    class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[13px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-brand-500)] hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
                    @click="openRefund(order)"
                  >
                    {{ t('order.actionRefund') }}
                  </button>
                  <button
                    v-if="order.canReceive"
                    type="button"
                    class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[13px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-success)] hover:text-[var(--color-success-600)] motion-reduce:transition-none"
                    @click="openReceive(order.orderNo)"
                  >
                    {{ t('order.actionReceive') }}
                  </button>
                  <NuxtLinkLocale v-if="order.canPay" :to="payLink(order.orderNo)" class="ui-pill ui-pill-brand h-9 px-5 text-[13px]">
                    {{ t('order.actionPay') }}
                  </NuxtLinkLocale>
                </div>
              </footer>
            </li>
          </TransitionGroup>
        </div>
        </Transition>

        <!-- 分页 -->
        <PaginationBar
          v-if="!loadError && records.length"
          :current="currentPage"
          :total="totalPages"
          :disabled="loading"
          margin="mt-8"
          :label="t('order.pagination')"
          :prev-label="t('order.prev')"
          :next-label="t('order.next')"
          :info-label="t('order.pageInfo', { current: currentPage, total: totalPages })"
          @change="goToPage"
        />
      </template>
    </div>

    <!-- 确认收货二次确认 -->
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
            class="cursor-pointer rounded-full border border-[var(--color-line)] px-5 py-2.5 text-sm font-semibold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
            :disabled="receiveState.pending"
            @click="receiveState.open = false"
          >
            {{ t('common.cancel') }}
          </button>
          <AsyncButton
            class="cursor-pointer rounded-full bg-[var(--color-brand-cta)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-brand-cta-hover)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
            :pending="receiveState.pending"
            :pending-label="t('order.receiveConfirming')"
            @click="confirmReceive"
          >
            {{ t('order.receiveConfirm') }}
          </AsyncButton>
        </div>
      </template>
    </UModal>

    <OrderCancelModal
      v-model:open="cancelState.open"
      :order-no="cancelState.orderNo"
      @canceled="loadOrders()"
    />

    <RefundApplyModal
      v-model:open="refundState.open"
      :order-no="refundState.orderNo"
      :items="refundState.items"
      @applied="loadOrders()"
    />
  </div>
</template>

<script setup lang="ts">
/** 订单列表是私有页面，未登录 / 登录过期由 auth 中间件重定向到登录页并回跳 */
definePageMeta({ middleware: 'auth' })

import { toNumberOrNull } from '~~/types/shop-product'
import type { OrderItemVO, OrderListVO, OrderPageResult } from '~~/types/shop-order'
import { useStorePrice } from '~~/composables/useProducts'
import { isPositive, isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'
import { formatDateTime } from '~~/composables/useDateTimeFormat'
import { useLoginLink } from '~~/composables/useLoginLink'

/**
 * 我的订单（列表 + 状态筛选 + 分页）。
 *
 * 两条硬规则：
 *   1) **操作按钮的可见性只用后端返回的 canPay / canCancel / canReceive / canRefund**，
 *      前端不重算「什么状态能干什么」—— 状态机只写在后端一处，
 *      否则后端改规则时前端会给出错误按钮（点了必然报错）。
 *   2) **订单状态文字一律用 orderStatusLabel**，退款/支付状态文字用各自的中文标签字段；
 *      顶部的筛选 tab 是本页的固定筛选项文案（不是状态码到中文的映射）。
 */



/** 筛选 tab：value 必须是后端 OrderQuery.orderStatus 的合法取值，空串表示不筛选 */
const STATUS_TABS: { value: string, label: string }[] = [
  { value: '', label: 'order.filterAll' },
  { value: 'WAIT_PAY', label: 'order.statusWaitPay' },
  { value: 'WAIT_SHIP', label: 'order.statusWaitShip' },
  { value: 'SHIPPED', label: 'order.statusShipped' },
  { value: 'COMPLETED', label: 'order.statusCompleted' },
  { value: 'CANCELLED', label: 'order.statusCancelled' },
  { value: 'CLOSED', label: 'order.statusClosed' },
]

const PAGE_SIZE = 10

const { t, locale } = useI18n()
const localePath = useLocalePath()
const toast = useToast()
const { format } = useStorePrice()
/** SSR 期也要带上 httpOnly cookie 的 fetch（loader 在 setup 阶段执行） */
const requestFetch = useRequestFetch()

const loading = ref(false)
const loadError = ref('')
const unauthorized = ref(false)

const records = ref<OrderListVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const activeStatus = ref('')

const receiveState = reactive({ open: false, orderNo: '', pending: false })
const cancelState = reactive({ open: false, orderNo: '' })
const refundState = reactive<{ open: boolean, orderNo: string, items: OrderItemVO[] }>({
  open: false,
  orderNo: '',
  items: [],
})

const totalPages = computed(() => {
  const size = PAGE_SIZE
  const pages = Math.ceil(total.value / size)
  return pages > 0 ? pages : 1
})
const currentPage = computed(() => pageNo.value)
const currentStatus = computed(() => activeStatus.value || undefined)

const loginLink = useLoginLink()
const detailLink = (orderNo: string) => localePath({ path: `/orders/${orderNo}` })
const payLink = (orderNo: string) => localePath({ path: '/checkout/pay', query: { orderNo } })


/**
 * 快速切换筛选/翻页时，只应用"最后一次"请求的响应。
 * 没有它时，慢请求后返回会把列表覆盖成上一个 tab 的数据（tab 高亮与内容不一致）。
 */
let loadSeq = 0

async function loadOrders() {
  const seq = ++loadSeq
  loading.value = true
  loadError.value = ''
  try {
    const page = await requestFetch<OrderPageResult<OrderListVO>>('/api/order', {
      query: {
        pageNo: pageNo.value,
        pageSize: PAGE_SIZE,
        // 空串会被 BFF 的枚举白名单过滤掉（不会下发 orderStatus 参数）
        orderStatus: currentStatus.value,
        lang: locale.value,
      },
    })
    if (seq !== loadSeq) return
    records.value = Array.isArray(page?.records) ? page.records : []
    total.value = toNumberOrNull(page?.total) ?? 0
    // 后端会回传 pageNo 的权威值（比如超出范围时被纠正），以它为准
    const serverPage = toNumberOrNull(page?.current)
    if (serverPage !== null && serverPage >= 1) pageNo.value = Math.trunc(serverPage)
    unauthorized.value = false
  } catch (error) {
    if (seq !== loadSeq) return
    if (isUnauthorizedError(error)) {
      unauthorized.value = true
      return
    }
    loadError.value = readErrorMessage(error, t('order.loadErrorDesc'))
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function changeStatus(status: string) {
  if (activeStatus.value === status) return
  activeStatus.value = status
  pageNo.value = 1
  loadOrders()
}

function goToPage(target: number) {
  const next = Math.min(Math.max(target, 1), totalPages.value)
  if (next === pageNo.value) return
  pageNo.value = next
  loadOrders()
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
  refundState.items = Array.isArray(order.items) ? order.items : []
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
    // 401/403 = 登录态已失效：交给全局会话守卫统一清理登录态、提示并跳登录页，
    // 这里再弹一条"操作失败"只会让用户同时看到两条互相矛盾的提示。
    if (isUnauthorizedError(error)) return
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

/**
 * SSR 直出订单列表：loader 在 setup 期执行（不再等 onMounted），
 * 配合 requestFetch 让 SSR 请求带上 httpOnly cookie。
 * lazy 只影响客户端导航：导航立即出骨架，不阻塞。
 *
 * 返回值是**可序列化快照**：列表/总数/失败与登录结论必须随 payload 回到客户端，
 * 否则水合首帧本地 refs 全是初始值，会与服务端已渲染的列表/空态不一致。
 */
const { data: ordersBoot, pending: ordersPending } = useLazyAsyncData('orders-index', async () => {
  await loadOrders()
  return {
    records: records.value,
    total: total.value,
    unauthorized: unauthorized.value,
    failed: !!loadError.value,
  }
}, { lazy: true })

if (ordersBoot.value) {
  records.value = ordersBoot.value.records
  total.value = ordersBoot.value.total
  if (ordersBoot.value.unauthorized) unauthorized.value = true
  if (ordersBoot.value.failed && !loadError.value) loadError.value = t('order.loadErrorDesc')
}

useSeoMeta({
  title: () => t('order.title'),
  robots: 'noindex, nofollow',
})
</script>
