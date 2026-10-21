<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto max-w-3xl px-2 pb-32 sm:px-3 lg:pb-16 lg:px-4">
      <header class="mb-5">
        <h1 class="text-[26px] font-black leading-none text-[var(--color-ink)] sm:text-[32px]">
          {{ t('order.payTitle') }}
        </h1>
        <p v-if="orderNo" class="mt-2 text-[13px] tabular text-[var(--color-muted)]">
          {{ t('order.payOrderNo') }}: {{ orderNo }}
        </p>
      </header>

      <Transition name="fade" mode="out-in">
      <!-- 缺少订单号（直接打开 /checkout/pay） -->
      <section v-if="!orderNo" key="missing" class="ui-card px-6 py-16 text-center">
        <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error)]/10">
          <UIcon name="lucide:file-question" class="h-8 w-8 text-[var(--color-error)]" aria-hidden="true" />
        </span>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('order.payMissingOrderNo') }}</h2>
        <NuxtLinkLocale to="/orders" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm">
          {{ t('order.title') }}
        </NuxtLinkLocale>
      </section>

      <!-- 未登录 -->
      <section v-else-if="unauthorized" key="unauthorized" class="ui-card px-6 py-16 text-center">
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

      <!-- 加载失败 -->
      <section v-else-if="loadError" key="error" class="ui-card px-6 py-16 text-center">
        <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error)]/10">
          <UIcon name="lucide:wifi-off" class="h-8 w-8 text-[var(--color-error)]" aria-hidden="true" />
        </span>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('order.payLoadError') }}</h2>
        <p class="mt-2 text-sm text-[var(--color-muted)]">{{ loadError }}</p>
        <button type="button" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm" @click="loadOrder()">
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
          {{ t('common.retry') }}
        </button>
      </section>

      <!-- 骨架屏 -->
      <section v-else-if="loading || !detail" key="skeleton" class="ui-card space-y-4 p-6" aria-busy="true" :aria-label="t('common.loading')">
        <div class="h-4 w-1/3 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
        <div class="h-10 w-1/2 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
        <div class="h-12 w-full animate-pulse rounded-full bg-[var(--color-line-soft)]"></div>
      </section>

      <!-- 正文 -->
      <div v-else key="content">
        <!-- 已支付：不再显示支付按钮，只给"去看订单" -->
        <section v-if="paid" class="ui-card px-6 py-14 text-center">
          <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-success-50)]">
            <UIcon name="lucide:circle-check" class="h-8 w-8 text-[var(--color-success-600)]" aria-hidden="true" />
          </span>
          <h2 class="mt-5 text-xl font-black text-[var(--color-ink)]">{{ t('order.payPaidTitle') }}</h2>
          <!-- 订单状态文字永远用后端返回的 orderStatusLabel -->
          <p class="mt-2 text-sm text-[var(--color-muted)]">{{ detail.orderStatusLabel }}</p>
          <NuxtLinkLocale :to="detailLink" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm">
            {{ t('order.payGoDetail') }}
          </NuxtLinkLocale>
        </section>

        <template v-else>
          <!-- 金额 + 倒计时 -->
          <section class="ui-card p-6">
            <div class="flex flex-wrap items-end justify-between gap-x-4 gap-y-3">
              <div>
                <p class="text-[13px] text-[var(--color-muted)]">{{ t('order.payAmount') }}</p>
                <p class="price mt-1 text-[34px] leading-none sm:text-[40px]">{{ format(detail.payAmount) }}</p>
              </div>
              <div class="text-right">
                <p class="text-[13px] text-[var(--color-muted)]">{{ t('order.payRemain') }}</p>
                <!-- 倒计时用后端给的 remainPaySeconds 本地锚定，见脚本注释 -->
                <p
                  class="mt-1 text-[26px] font-black leading-none tabular"
                  :class="expired ? 'text-[var(--color-error-600)]' : 'text-[var(--color-brand-600)]'"
                >
                  {{ countdownText }}
                </p>
                <p v-if="payDeadlineText" class="mt-1.5 text-[12px] text-[var(--color-muted)]">
                  {{ t('order.payDeadline') }}: {{ payDeadlineText }}
                </p>
              </div>
            </div>

            <p
              v-if="expired"
              class="mt-4 flex items-start gap-1.5 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-3 py-2.5 text-[12px] font-medium text-[var(--color-error-600)]"
            >
              <UIcon name="lucide:clock-alert" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>{{ t('order.payExpired') }}</span>
            </p>

            <!-- 模拟支付声明：必须让用户（和联调同事）一眼看到这不是真支付 -->
            <p class="mt-4 flex items-start gap-2 rounded-[var(--radius-card-sm)] border border-[var(--color-warning)]/30 bg-[var(--color-warning-50)] px-3.5 py-3 text-[12px] leading-relaxed text-[var(--color-warning-600)]">
              <UIcon name="lucide:flask-conical" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              <span><strong>{{ t('order.payMockBadge') }}</strong>: {{ t('order.payMockNotice') }}</span>
            </p>

            <div class="mt-5 flex flex-col gap-3 sm:flex-row">
              <button
                type="button"
                class="hidden h-12 flex-1 items-center justify-center gap-2 rounded-full bg-[var(--color-brand-500)] text-[15px] font-bold text-white transition-colors duration-200 hover:bg-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:bg-[var(--color-ink)]/20 motion-reduce:transition-none lg:flex"
                :disabled="paying || expired"
                @click="payNow"
              >
                <UIcon v-if="paying" name="lucide:loader-2" class="h-4 w-4 animate-spin" aria-hidden="true" />
                {{ paying ? t('order.paying') : t('order.payNow') }}
              </button>

              <button
                type="button"
                class="flex h-12 flex-1 items-center justify-center gap-2 rounded-full border border-[var(--color-line)] text-[15px] font-bold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
                :disabled="querying"
                @click="refreshPaidStatus"
              >
                <UIcon v-if="querying" name="lucide:loader-2" class="h-4 w-4 animate-spin" aria-hidden="true" />
                <UIcon v-else name="lucide:refresh-cw" class="h-4 w-4" aria-hidden="true" />
                {{ querying ? t('order.payRefreshing') : t('order.payRefresh') }}
              </button>
            </div>

            <!-- 轮询状态提示：最多 20 次后停止并如实说明 -->
            <p v-if="pollGaveUp" class="mt-3 flex items-start justify-center gap-1.5 text-center text-[12px] text-[var(--color-warning-600)]">
              <UIcon name="lucide:info" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>{{ t('order.payPollingGaveUp') }}</span>
            </p>
            <p v-else-if="polling" class="mt-3 text-center text-[12px] text-[var(--color-muted)]">
              {{ t('order.payPollingHint') }}
            </p>

            <!-- 后端返回的最后一句查询结论（中文），原样展示，不自己下结论 -->
            <p v-if="queryMessage" class="mt-3 text-center text-[12px] text-[var(--color-ink-soft)]">
              {{ queryMessage }}
            </p>
          </section>

          <!-- 渠道调起参数：本期是模拟渠道，参数由后端支付网关填充，前端只做透传展示 -->
          <section class="ui-card mt-5 overflow-hidden">
            <header class="border-b border-[var(--color-line-soft)] px-4 py-3">
              <h2 class="text-[15px] font-black text-[var(--color-ink)]">{{ t('order.payParamsTitle') }}</h2>
            </header>
            <dl v-if="payParams.length" class="divide-y divide-[var(--color-line-soft)]">
              <div v-for="param in payParams" :key="param.key" class="flex items-start gap-3 px-4 py-2.5">
                <dt class="w-40 shrink-0 truncate text-[12px] font-bold text-[var(--color-muted)]">{{ param.key }}</dt>
                <dd class="min-w-0 flex-1 break-all text-[12px] tabular text-[var(--color-ink)]">{{ param.value }}</dd>
              </div>
            </dl>
            <p v-else class="px-4 py-4 text-[13px] text-[var(--color-muted)]">
              {{ prepayResult ? t('order.payParamsEmpty') : t('order.payParamsNotYet') }}
            </p>
          </section>

          <div class="mt-5 text-center">
            <NuxtLinkLocale :to="detailLink" class="text-[13px] font-medium text-[var(--color-muted)] hover:text-[var(--color-brand-600)]">
              {{ t('order.payBackToOrder') }}
            </NuxtLinkLocale>
          </div>

          <!--
            移动端常驻支付条：支付是本页唯一目标，不能让用户滚回上面的金额卡。
            桌面端仍用卡内按钮；这里 lg:hidden，fixed 不占文档流（容器已留 pb-32）。
          -->
          <div
            class="fixed inset-x-0 bottom-0 z-40 border-t border-[var(--color-line-soft)] bg-white/95 px-3 pb-[calc(env(safe-area-inset-bottom)+0.5rem)] pt-2.5 shadow-[0_-4px_16px_rgba(0,0,0,0.06)] backdrop-blur lg:hidden"
          >
            <div class="flex items-center gap-3">
              <div class="min-w-0 flex-1">
                <p class="text-[11px] leading-none text-[var(--color-muted)]">{{ t('order.payAmount') }}</p>
                <p class="price mt-1 text-[22px] leading-none">{{ format(detail.payAmount) }}</p>
              </div>
              <button
                type="button"
                class="flex h-12 min-w-[150px] items-center justify-center gap-2 rounded-full bg-[var(--color-brand-500)] px-6 text-[15px] font-bold text-white transition-colors disabled:cursor-not-allowed disabled:bg-[var(--color-ink)]/20"
                :disabled="paying || expired"
                @click="payNow"
              >
                <UIcon v-if="paying" name="lucide:loader-2" class="h-4 w-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
                {{ paying ? t('order.paying') : t('order.payNow') }}
              </button>
            </div>
          </div>
        </template>
        </div>
      </Transition>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 支付页是私有页面，未登录 / 登录过期由 auth 中间件重定向到登录页并回跳 */
definePageMeta({ middleware: 'auth' })

import { toNumberOrNull } from '~~/types/shop-product'
import { useStorePrice } from '~~/composables/useProducts'
import { isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'
import { createIdempotencyKey } from '~~/composables/useIdempotencyKey'
import { useLoginLink } from '~~/composables/useLoginLink'

/**
 * 支付页（下单闭环第二步）。
 *
 * 【本期是模拟支付】「立即支付」= POST /api/pay/prepay（拿 payParams）
 *                              + POST /api/pay/mock-confirm（点击即视为付款成功）。
 * 界面上必须明确标注这是模拟支付 —— 否则联调同事、试用用户会以为真的扣了钱。
 * 后端走的是与真实渠道**完全相同**的回调链路（验签/去重/状态机/资金流水/库存实扣），
 * 所以接入真渠道时这一页只需要把 mock-confirm 换成渠道 SDK 调用。
 *
 * 倒计时：后端明明给了两个字段，为什么要用 remainPaySeconds 而不是直接解析 payDeadline？
 *   payDeadline 是 "yyyy-MM-dd HH:mm:ss" 字符串（GMT+8），浏览器会按**本地时区**解析，
 *   用户在东八区之外时倒计时会整体偏几个小时。
 *   remainPaySeconds 是相对秒数，不存在时区问题；这里在拿到响应的那一刻
 *   把它锚定成本地时间戳，之后每秒自减。payDeadline 仍然原样显示出来
 *   （它是"权威的截止时间"），只是不参与计算。
 *
 * 支付结果确认：渠道回调有延迟，所以不能"点完就当已支付"。
 *   1) GET /api/pay/status 每 3 秒轮询一次，最多 20 次；20 次后停止并提示
 *      "支付结果确认中，系统会自动补单"（后端确实有自动补单，不是安慰话）；
 *   2) 用户也可以点「我已支付，刷新一下」-> POST /api/pay/query 主动向渠道查一次，
 *      界面文案直接用后端返回的 message。
 */

/** 订单详情里本页用到的字段（后端 StoreOrderDetailVO 的子集） */
interface OrderDetail {
  orderNo: string
  orderStatus?: string | null
  orderStatusLabel?: string | null
  payStatus?: string | null
  payAmount?: number | null
  payDeadline?: string | null
  remainPaySeconds?: number | null
  createTime?: string | null
}

/** 支付状态查询出参（后端 /pay/status 的 Map） */
interface PayStatusResult {
  payments?: unknown[]
  paid?: boolean | null
}

/** 主动查询出参（后端 /pay/query 的 Map） */
interface PayQueryResult {
  settled?: boolean | null
  message?: string | null
}

/** 发起支付出参（后端 OrderPrepayVO） */
interface PrepayResult {
  paymentNo?: string | null
  channel?: string | null
  payType?: string | null
  amount?: number | null
  payParams?: Record<string, string> | null
}

/** 模拟支付确认出参（后端 /pay/mock-confirm 的 Map） */
interface MockConfirmResult {
  paymentNo?: string | null
  paid?: boolean | null
}

const POLL_INTERVAL_MS = 3000
/** 最多轮询 20 次（约 1 分钟）：再久就是在浪费用户的流量与电量，改为如实提示 */
const MAX_POLLS = 20

const { t, locale } = useI18n()
const localePath = useLocalePath()
const route = useRoute()
const toast = useToast()
const { format } = useStorePrice()
/** SSR 期也要带上 httpOnly cookie 的 fetch（订单详情 loader 在 setup 阶段执行） */
const requestFetch = useRequestFetch()

const orderNo = computed(() => String(route.query.orderNo ?? '').trim())

const loading = ref(false)
const loadError = ref('')
const unauthorized = ref(false)
const detail = ref<OrderDetail | null>(null)

const paid = ref(false)
const paying = ref(false)
const querying = ref(false)
const queryMessage = ref('')
const prepayResult = ref<PrepayResult | null>(null)

const polling = ref(false)
const pollCount = ref(0)
const pollGaveUp = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

/** 截止时刻（本地锚定，毫秒）；null = 后端没给可用依据 */
const deadlineAt = ref<number | null>(null)
const remainSeconds = ref(0)
const nowTick = ref(Date.now())
let tickTimer: ReturnType<typeof setInterval> | null = null

const loginLink = useLoginLink()
const detailLink = computed(() => localePath({
  path: `/orders/${orderNo.value}`,
}))

/**
 * 已支付判定：payStatus 的三种终态都算"付过钱"（部分退款/已退款仍不该再支付）。
 * 抽成函数：加载路径与水合回填路径必须用同一套判定。
 */
function isPaidStatus(order: OrderDetail | null): boolean {
  const status = String(order?.payStatus ?? '')
  return status === 'PAID' || status === 'PARTIAL_REFUND' || status === 'REFUNDED'
}

/**
 * 是否已过期。
 *
 * 除了"本地倒计时归零"，还必须在订单已经不可支付（已关闭/已取消/已完成）时成立：
 * 否则后端两个时间字段都缺失时 deadlineAt 为 null，页面会把一张早已关闭的订单
 * 当成"还能支付"（按钮可点，只能靠后端报错兜底）。
 */
const CLOSED_ORDER_STATUS = new Set(['CANCELLED', 'CLOSED', 'COMPLETED', 'REFUNDED'])
const expired = computed(() => (
  !paid.value
  && (
    CLOSED_ORDER_STATUS.has(String(detail.value?.orderStatus ?? '').toUpperCase())
    || (deadlineAt.value !== null && remainSeconds.value <= 0)
  )
))

const countdownText = computed(() => {
  const total = Math.max(0, remainSeconds.value)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  return hours > 0 ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`
})

/** 支付截止时间：后端给的字符串原样展示（它是权威值），不参与倒计时计算 */
const payDeadlineText = computed(() => detail.value?.payDeadline ? String(detail.value.payDeadline) : '')

/** payParams 展开成有序数组，模板里好渲染 */
const payParams = computed(() => {
  const params = prepayResult.value?.payParams
  if (!params || typeof params !== 'object') return []
  return Object.entries(params).map(([key, value]) => ({ key, value: String(value) }))
})

// ---------------------------------------------------------------------------
// 倒计时
// ---------------------------------------------------------------------------

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

/**
 * 用后端给的 remainPaySeconds 锚定本地截止时刻。
 * 后端没给 remainPaySeconds 时（理论上不会）才退化为解析 payDeadline 字符串，
 * 并按本项目的既有约定把 "yyyy-MM-dd HH:mm:ss" 当本地时间处理。
 */
function anchorDeadline(order: OrderDetail) {
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
  // 两个字段都没有：不显示倒计时，也不假装"还剩很久"
  deadlineAt.value = null
  remainSeconds.value = 0
}

// ---------------------------------------------------------------------------
// 轮询
// ---------------------------------------------------------------------------

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  polling.value = false
}

function startPolling() {
  stopPolling()
  polling.value = true
  pollGaveUp.value = false
  pollCount.value = 0
  pollTimer = setInterval(async () => {
    if (pollCount.value >= MAX_POLLS) {
      // 到次数就停：与其无限轮询，不如告诉用户"系统会自动补单"，这是后端的真实行为
      stopPolling()
      pollGaveUp.value = true
      return
    }
    pollCount.value += 1
    try {
      const status = await $fetch<PayStatusResult>('/api/pay/status', {
        query: { orderNo: orderNo.value, lang: locale.value },
      })
      if (status?.paid) {
        stopPolling()
        stopTicking()
        onPaid()
      }
    } catch (error) {
      // 轮询失败不该弹错误打断用户（网络抖一下很正常），下一轮再试
      if (isUnauthorizedError(error)) {
        stopPolling()
        unauthorized.value = true
      }
    }
  }, POLL_INTERVAL_MS)
}

function onPaid() {
  paid.value = true
  toast.add({ title: t('order.payPaidToast'), color: 'success', icon: 'lucide:check' })
  // 支付成功后跳订单详情：那条页面有支付单、金额、时间线，是用户真正想看的东西
  navigateTo(detailLink.value)
}

// ---------------------------------------------------------------------------
// 取数与动作
// ---------------------------------------------------------------------------

async function loadOrder() {
  if (!orderNo.value) return
  loading.value = true
  loadError.value = ''
  try {
    const result = await requestFetch<OrderDetail>('/api/order/detail', {
      query: { orderNo: orderNo.value, lang: locale.value },
    })
    detail.value = result
    paid.value = isPaidStatus(result)
    anchorDeadline(result)
    // 定时器只在客户端启动：SSR 阶段 setInterval 会挂在 Node 进程里，每个请求漏一个
    if (import.meta.client) {
      startTicking()
      if (!paid.value) startPolling()
    }
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

/** 立即支付：先 prepay 拿渠道参数，本期再调 mock-confirm 完成"付款" */
async function payNow() {
  if (paying.value || !orderNo.value) return
  paying.value = true
  queryMessage.value = ''
  try {
    // ① 发起支付（创建支付单 + 调渠道预支付）。requestId 用本次会话的幂等键，
    //    连点不会产生多笔有效支付单（后端也有"订单号 + 待支付支付单"兜底）。
    prepayResult.value = await $fetch<PrepayResult>('/api/pay/prepay', {
      method: 'POST',
      body: {
        orderNo: orderNo.value,
        // 本期只有模拟渠道；接入真渠道时这里换成 WECHAT / ALIPAY，页面其余部分不用改
        channel: 'MOCK',
        requestId: prepayRequestId,
        lang: locale.value,
      },
    })

    // ② 模拟确认：后端让模拟渠道生成**已签名**的回调，走与真实渠道一致的结算链路
    const confirmed = await $fetch<MockConfirmResult>('/api/pay/mock-confirm', {
      method: 'POST',
      body: { orderNo: orderNo.value, lang: locale.value },
    })

    if (confirmed?.paid) {
      stopPolling()
      stopTicking()
      onPaid()
      return
    }

    toast.add({
      title: t('order.payPaidFailed'),
      color: 'warning',
      icon: 'lucide:circle-alert',
    })
    // 没成功就继续轮询，等渠道回调把它补上
    startPolling()
  } catch (error) {
    if (isUnauthorizedError(error)) {
      unauthorized.value = true
      return
    }
    toast.add({
      title: t('order.actionFailed'),
      description: readErrorMessage(error, t('order.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    paying.value = false
  }
}

/** 我已支付，刷新一下：主动向渠道查询一次，文案直接用后端返回的 message */
async function refreshPaidStatus() {
  if (querying.value || !orderNo.value) return
  querying.value = true
  try {
    const result = await $fetch<PayQueryResult>('/api/pay/query', {
      method: 'POST',
      body: { orderNo: orderNo.value, lang: locale.value },
    })
    queryMessage.value = result?.message ?? ''
    if (result?.settled) {
      stopPolling()
      stopTicking()
      onPaid()
    } else {
      toast.add({
        title: t('order.payPollingGaveUp'),
        description: queryMessage.value || undefined,
        color: 'info',
        icon: 'lucide:info',
      })
    }
  } catch (error) {
    if (isUnauthorizedError(error)) {
      unauthorized.value = true
      return
    }
    toast.add({
      title: t('order.actionFailed'),
      description: readErrorMessage(error, t('order.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    querying.value = false
  }
}

/**
 * 发起支付的幂等键：进页面时生成一次（setup 顶层），所有重试复用同一个值。
 * 不是 crypto.randomUUID() 会怎样？用户连点「立即支付」时后端可能建多条支付单；
 * 后端虽然用"订单号 + 当前待支付支付单"兜底，但把幂等键交准是调用方的责任。
 * 生成/降级逻辑在 composables/useIdempotencyKey.ts，prefix 'pay' 用于非安全上下文兜底。
 */
const prepayRequestId = createIdempotencyKey('pay')

/**
 * SSR 直出支付页：loader 在 setup 期执行（不再等 onMounted），
 * 配合 requestFetch 让 SSR 请求带上 httpOnly cookie；lazy 只影响客户端导航。
 *
 * 返回值是**可序列化快照**：详情/失败/未登录结论随 payload 回到客户端，
 * 水合首帧直接回填 detail / paid / 倒计时，避免"服务端渲染了正文、客户端却闪骨架"
 * 这种 hydration 不一致。
 */
const { data: payBoot } = useLazyAsyncData('checkout-pay', async () => {
  await loadOrder()
  return {
    detail: detail.value,
    unauthorized: unauthorized.value,
    failed: !!loadError.value,
  }
}, { lazy: true })

if (payBoot.value) {
  if (payBoot.value.unauthorized) {
    unauthorized.value = true
  } else if (payBoot.value.failed) {
    if (!loadError.value) loadError.value = t('order.loadErrorDesc')
  } else if (payBoot.value.detail) {
    detail.value = payBoot.value.detail
    paid.value = isPaidStatus(payBoot.value.detail)
    anchorDeadline(payBoot.value.detail)
    if (import.meta.client) {
      startTicking()
      if (!paid.value) startPolling()
    }
  }
}
onBeforeUnmount(() => {
  stopPolling()
  stopTicking()
})

useSeoMeta({
  title: () => t('order.payTitle'),
  robots: 'noindex, nofollow',
})
</script>
