// utils/orderRefund.ts
//
// 退款/金额展示用的**纯函数**（不含 i18n 调用，故 composable 与组件都能直接用）。
//
// 为什么单独放一处：订单详情页拆组件后，这些判断同时被
// composables/useOrderDetail.ts（页面逻辑）与 components/order/DetailCards.vue（卡片渲染）需要。
// 与其复制两份（口径会漂移），不如放一个纯模块，各自 import。

import { toNumberOrNull } from '~~/types/shop-product'

/**
 * 退款原因 code → 界面文案 i18n key。
 *
 * 后端字典 shop_refund_reason 只有英文 code，没有随退款单下发中文标签
 * （退款单出参里 applyReason 是 code，statusLabel / refundTypeLabel 才是中文）。
 * 所以必须做一次 code → 文案的映射，取值集合与后端字典严格一致。
 */
export const REFUND_REASON_LABELS: Record<string, string> = {
  no_need: 'order.reasonNoNeed',
  duplicate: 'order.reasonDuplicate',
  mismatch: 'order.reasonMismatch',
  quality: 'order.reasonQuality',
  missing: 'order.reasonMissing',
  logistics: 'order.reasonLogistics',
  other: 'order.reasonOther',
}

/** 拿到 i18n key；未知 code 返回 null（调用方决定要不要原样展示 code） */
export function refundReasonKey(reason: string): string | null {
  return REFUND_REASON_LABELS[reason] ?? null
}

/** 有值（含 0）即 true —— 用于"这一行该不该渲染" */
export function hasNumber(value: unknown): boolean {
  return toNumberOrNull(value) !== null
}

/**
 * 退款单是否可撤销：只有"申请中"可以。
 * 后端没有下发对应标记，所以前端用退款单自身 status 判断是否存在按钮；
 * 是否真的允许撤销仍由后端裁决，前端只是不发必然失败的请求。
 */
export function isWithdrawable(refund: { status?: string | null }): boolean {
  return String(refund.status ?? '').toUpperCase() === 'APPLYING'
}
