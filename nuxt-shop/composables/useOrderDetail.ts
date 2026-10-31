// composables/useOrderDetail.ts
//
// 订单详情页的数据与操作层：详情加载、四个订单级操作、倒计时、退款单可撤销判断。
//
// 为什么抽出来：pages/orders/[orderNo].vue 原先 824 行，模板（状态/金额/商品/收货/物流/时间线）
// 与这些逻辑混在一起。模板留在页面，逻辑收拢到这里 —— 页面只剩"渲染 + 把这些状态接上"。
//
// 三条硬规则的落点（与页面注释一致，勿改）：
//   1) 操作按钮可见性**只用后端下发的 canPay / canCancel / canReceive / canRefund**；
//   2) 所有中文标签（订单状态、支付单状态、退款状态与类型、事件类型）都用后端字段；
//   3) 金额只做两位小数格式化，不做任何运算。
// 例外：「撤销退款」按钮的可见性由退款单 status 判断（后端没下发标记），
//      但"是否真的允许撤销"仍由后端裁决，前端只是不发必然失败的请求。
//
// ⚠️ 生命周期在本 composable 内注册（onMounted 拉详情 / onBeforeUnmount 停倒计时）：
//    调用方必须在 setup 顶层**同步**调用它，不要放在 await 之后。

import { toNumberOrNull } from '~~/types/shop-product'
import type { OrderItemVO } from '~~/types/shop-order'
import { useStorePrice } from '~~/composables/useProducts'
import { isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'
import { useLoginLink } from '~~/composables/useLoginLink'
import { hasNumber, isWithdrawable, refundReasonKey } from '~~/utils/orderRefund'

/**
 * 类型收敛：OrderItemVO 的唯一来源是 types/shop-order.ts。
 * 这里再导出一次只是为了保持既有调用点（components/order/DetailCards.vue 从这里 import）
 * 完全不用改；新代码请直接从 types/shop-order 引入。
 */
export type { OrderItemVO }

/** 支付单（后端 OrderPaymentVO） */
export interface OrderPaymentVO {
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
export interface OrderRefundVO {
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
export interface OrderEventVO {
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
export interface OrderDetailVO {
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
  /**
   * 后端下发的整串收货地址（含国家段、国际顺序、相邻同名去重）。
   *
   * 两个字段的分工与地址簿 `AddressVO.fullAddress` / `fullAddressLocalized` 完全一致：
   *   · receiverFullAddress          = 当地语言整串（面单口径，给物流看）
   *   · receiverFullAddressLocalized = 访客语言整串（屏幕展示口径）
   * 拼接规则**只准由后端实现**（联调约定：前端不再自己拼、不再去重），
   * 否则订单详情与地址簿两处规则必然漂移。
   * 历史订单 / 后端尚未重启的中间态下这两个字段都是 null，所以前端保留旧拼法兜底。
   */
  receiverFullAddress?: string | null
  receiverFullAddressLocalized?: string | null
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

export const useOrderDetail = () => {

  const { t, locale } = useI18n()

  /** 退款原因 code → 界面文案（映射表在 utils/orderRefund.ts，与后端字典一致） */
  function refundReasonLabel(reason: string): string {
    const key = refundReasonKey(reason)
    return key ? t(key) : reason
  }
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

  /**
   * 收货地址整串（对外语义不变：永远是字符串，没有订单时为 ''）。
   *
   * 来源按三级回退，规则与地址簿一致 —— **能拿后端下发的就绝不自己拼**：
   *   1) receiverFullAddressLocalized —— 访客语言整串，屏幕展示的正解；
   *   2) receiverFullAddress          —— 当地语言整串（面单口径）；
   *   3) 旧拼法兜底：省 + 市 + 区 + 详细地址。
   *
   * 为什么兜底必须留着：历史订单没有新字段，且后端还没重启的中间态下两个新字段都是 null。
   * 但兜底那一段**刻意保持原样**（不含国家段）—— 它只为"不显示空白"服务；
   * 想补国家就得在前端复刻后端的国际顺序与去重规则，那正是本约定要避免的事。
   */
  const receiverAddressText = computed(() => {
    const source = detail.value
    if (!source) return ''
    const fromBackend = source.receiverFullAddressLocalized || source.receiverFullAddress
    if (fromBackend && fromBackend.trim()) return fromBackend
    const region = [source.receiverProvince, source.receiverCity, source.receiverDistrict].filter(Boolean).join(' ')
    return [region, source.receiverAddress].filter(Boolean).join(' ')
  })

  const loginLink = useLoginLink()
  const payLink = computed(() => localePath({ path: '/checkout/pay', query: { orderNo: orderNo.value } }))


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
      // 401/403 = 登录态已失效：交给全局会话守卫统一清理登录态、提示并跳登录页，
      // 这里再弹一条"退款单加载失败"只会让用户同时看到两条互相矛盾的提示。
      if (isUnauthorizedError(error)) return
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

  return {
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
    refundReasonLabel,
    hasNumber,
    isWithdrawable,
    loadDetail,
    confirmReceive,
    askWithdraw,
    confirmWithdraw,
    onRefundApplied,
  }
}

