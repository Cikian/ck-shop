// composables/useApiError.ts
//
// $fetch（ofetch）抛出的错误 → 可读文案 / 状态判定的**唯一实现**。
//
// 优先级（不可调换）：data.statusMessage > data.message > statusMessage > message > fallback
//
// 为什么 statusMessage 必须排在 message 前面：
//   ofetch 会把非 2xx 响应的 `message` 固定成 "400 Bad Request" 这类 HTTP 短语，
//   真正的业务文案在后端 body 里 —— BFF（Nitro createError）放在 data.statusMessage，
//   老接口放在 data.message。一旦改成 message 优先，用户看到的就是 "500 Server Error"
//   而不是"库存不足"这种真正的原因。
//
// 历史：这段逻辑曾在 8 个文件里各抄了一份（orders/index、profile、address/index、
// checkout/index、checkout/pay、orders/[orderNo]、OrderCancelModal、RefundApplyModal），
// 8 份的优先级完全一致，只有 fallback 传值不同，所以这里收敛成一处。
import { toNumberOrNull } from '~~/types/shop-product'

/**
 * 错误 → 展示文案。
 * @param fallback 取不到任何后端文案时的兜底（调用方传各自语境下的 i18n 文案）
 */
export function readErrorMessage(error: unknown, fallback = ''): string {
  const err = error as any
  return err?.data?.statusMessage || err?.data?.message || err?.statusMessage || err?.message || fallback
}

/**
 * 401/403 视为「未登录 / 无权限」。
 * 页面据此走登录引导分支，而不是弹一条红色的失败 toast。
 * statusCode / status / data.statusCode 三个位置都要看：$fetch 与 SSR 直抛的错误形状不同。
 */
export function isUnauthorizedError(error: unknown): boolean {
  const err = error as any
  const code = err?.statusCode ?? err?.status ?? err?.data?.statusCode
  return code === 401 || code === 403
}

/**
 * 严格大于 0。
 * 用于"这一行该不该渲染"：0 元优惠行、0 次使用次数都是噪音，不是信息。
 * 非数字（null / 空串 / 'abc'）一律 false —— toNumberOrNull 已归一化。
 */
export function isPositive(value: unknown): boolean {
  const num = toNumberOrNull(value)
  return num !== null && num > 0
}
