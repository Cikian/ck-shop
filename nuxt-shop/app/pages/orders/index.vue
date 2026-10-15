<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto max-w-7xl px-2 pb-16 sm:px-3 lg:px-4">
      <header class="mb-5">
        <h1 class="text-[26px] font-black leading-none text-[var(--color-ink)] sm:text-[32px]">
          {{ t('order.title') }}
        </h1>
        <p class="mt-2 text-[13px] text-[var(--color-muted)]">{{ t('order.subtitle') }}</p>
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

        <!-- 加载失败 -->
        <section v-if="loadError" class="ui-card px-6 py-16 text-center">
          <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error)]/10">
            <UIcon name="lucide:wifi-off" class="h-8 w-8 text-[var(--color-error)]" aria-hidden="true" />
          </span>
          <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('order.loadError') }}</h2>
          <p class="mt-2 text-sm text-[var(--color-muted)]">{{ loadError }}</p>
          <button type="button" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm" @click="loadOrders()">
            <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
            {{ t('common.retry') }}
          </button>
        </section>

        <!-- 骨架屏 -->
        <section v-else-if="loading && !records.length" class="space-y-4" aria-busy="true" :aria-label="t('common.loading')">
          <div v-for="index in 3" :key="index" class="ui-card space-y-3 p-5">
            <div class="h-4 w-1/3 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
            <div class="h-16 animate-pulse rounded-[var(--radius-card-sm)] bg-[var(--color-line-soft)]"></div>
          </div>
        </section>

        <!-- 空态 -->
        <section v-else-if="!records.length" class="ui-card px-6 py-20 text-center">
          <span class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
            <UIcon name="lucide:package-open" class="h-9 w-9 text-[var(--color-brand-400)]" aria-hidden="true" />
          </span>
          <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('order.empty') }}</h2>
          <p class="mx-auto mt-2 max-w-md text-sm text-[var(--color-muted)]">{{ t('order.emptyDesc') }}</p>
          <NuxtLinkLocale to="/products" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm">
            <UIcon name="lucide:arrow-right" class="h-4 w-4" aria-hidden="true" />
            {{ t('order.emptyAction') }}
          </NuxtLinkLocale>
        </section>

        <!-- 订单列表 -->
        <template v-else>
          <ul class="space-y-4">
            <li v-for="order in records" :key="order.orderNo" class="ui-card overflow-hidden">
              <!-- 头：订单号 + 状态 + 时间 -->
              <header class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b border-[var(--color-line-soft)] px-4 py-3">
                <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span class="text-[13px] text-[var(--color-muted)]">{{ t('order.orderNo') }}</span>
                  <span class="text-[13px] font-bold tabular text-[var(--color-ink)]">{{ order.orderNo }}</span>
                  <!-- 状态文字用后端 orderStatusLabel；status 只决定配色 -->
                  <OrderStatusTag :label="order.orderStatusLabel || order.orderStatus || ''" :status="order.orderStatus" />
                </div>
                <span class="text-[12px] tabular text-[var(--color-muted)]">{{ order.createTime || '' }}</span>
              </header>

              <!-- 商品明细：只展示后端返回的快照，缩略图 + 名称 + 规格 + 单价×数量 -->
              <ul class="divide-y divide-[var(--color-line-soft)]">
                <li v-for="(item, index) in order.items" :key="`${order.orderNo}-${item.id || index}`" class="flex gap-3 px-4 py-3">
                  <span class="h-14 w-14 shrink-0 overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-line-soft)]">
                    <img
                      v-if="item.productImage"
                      :src="item.productImage"
                      :alt="item.productName || ''"
                      class="h-full w-full object-cover"
                      loading="lazy"
                    >
                    <span v-else class="flex h-full w-full items-center justify-center">
                      <UIcon name="lucide:image" class="h-5 w-5 text-[var(--color-muted)]" aria-hidden="true" />
                    </span>
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
          </ul>

          <!-- 分页 -->
          <nav
            v-if="totalPages > 1"
            class="mt-8 flex flex-wrap items-center justify-center gap-2"
            :aria-label="t('order.pagination')"
          >
            <button
              type="button"
              class="ui-pill ui-pill-ghost h-10 gap-1 px-4 text-[13px] disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="pageNo <= 1 || loading"
              @click="goToPage(pageNo - 1)"
            >
              <UIcon name="lucide:chevron-left" class="h-4 w-4" aria-hidden="true" />
              <span class="hidden sm:inline">{{ t('order.prev') }}</span>
            </button>
            <span class="px-2 text-[13px] tabular text-[var(--color-muted)]">
              {{ t('order.pageInfo', { current: currentPage, total: totalPages }) }}
            </span>
            <button
              type="button"
              class="ui-pill ui-pill-ghost h-10 gap-1 px-4 text-[13px] disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="pageNo >= totalPages || loading"
              @click="goToPage(pageNo + 1)"
            >
              <span class="hidden sm:inline">{{ t('order.next') }}</span>
              <UIcon name="lucide:chevron-right" class="h-4 w-4" aria-hidden="true" />
            </button>
          </nav>
        </template>
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

    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
/** 订单列表是私有页面，未登录 / 登录过期由 auth 中间件重定向到登录页并回跳 */
definePageMeta({ middleware: 'auth' })

import { toNumberOrNull } from '~~/types/shop-product'
import { useStorePrice } from '~~/composables/useProducts'

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

/** 订单明细（后端 StoreOrderItemVO 的子集） */
interface OrderItemVO {
  id?: string | null
  productId?: string | null
  productName?: string | null
  skuName?: string | null
  productImage?: string | null
  price?: number | null
  quantity?: number | null
  actualAmount?: number | null
  refundQuantity?: number | null
  refundAmount?: number | null
}

/** 订单列表项（后端 StoreOrderListVO） */
interface OrderListVO {
  orderNo: string
  orderStatus?: string | null
  orderStatusLabel?: string | null
  payStatus?: string | null
  refundStatus?: string | null
  payAmount?: number | null
  paidAmount?: number | null
  refundAmount?: number | null
  totalQuantity?: number | null
  itemCount?: number | null
  createTime?: string | null
  items?: OrderItemVO[] | null
  canPay?: boolean | null
  canCancel?: boolean | null
  canReceive?: boolean | null
  canRefund?: boolean | null
}

/** MyBatis-Plus Page 出参 */
interface PageResult<T> {
  records?: T[] | null
  total?: number | null
  size?: number | null
  current?: number | null
  pages?: number | null
}

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
const route = useRoute()
const toast = useToast()
const { format } = useStorePrice()

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

/** 严格大于 0：只有真的退过款才显示"已退金额" */
function isPositive(value: unknown): boolean {
  const num = toNumberOrNull(value)
  return num !== null && num > 0
}

const loginLink = computed(() => localePath({
  path: '/login',
  query: { redirect: route.fullPath },
}))
const detailLink = (orderNo: string) => localePath({ path: `/orders/${orderNo}` })
const payLink = (orderNo: string) => localePath({ path: '/checkout/pay', query: { orderNo } })

/** 从 $fetch 抛出的错误里取出可读文案（后端业务异常 message 是中文，直接展示） */
function readErrorMessage(error: unknown, fallback: string): string {
  const err = error as any
  return err?.data?.statusMessage || err?.data?.message || err?.statusMessage || err?.message || fallback
}

function isUnauthorizedError(error: unknown): boolean {
  const err = error as any
  const code = err?.statusCode ?? err?.status ?? err?.data?.statusCode
  return code === 401 || code === 403
}

async function loadOrders() {
  loading.value = true
  loadError.value = ''
  try {
    const page = await $fetch<PageResult<OrderListVO>>('/api/order', {
      query: {
        pageNo: pageNo.value,
        pageSize: PAGE_SIZE,
        // 空串会被 BFF 的枚举白名单过滤掉（不会下发 orderStatus 参数）
        orderStatus: currentStatus.value,
        lang: locale.value,
      },
    })
    records.value = Array.isArray(page?.records) ? page.records : []
    total.value = toNumberOrNull(page?.total) ?? 0
    // 后端会回传 pageNo 的权威值（比如超出范围时被纠正），以它为准
    const serverPage = toNumberOrNull(page?.current)
    if (serverPage !== null && serverPage >= 1) pageNo.value = Math.trunc(serverPage)
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

onMounted(loadOrders)

useSeoMeta({
  title: () => t('order.title'),
  robots: 'noindex, nofollow',
})
</script>
