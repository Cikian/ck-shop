// composables/useIdempotencyKey.ts
//
// 幂等键（idempotency key / requestId）的唯一实现。
//
// 为什么必须有幂等键：用户连点、客户端重试、网络抖动重发都会让同一个"意图"
// 到达后端多次。键相同 → 后端只建一单/一张退款单/一条支付单。
// **一次页面访问只生成一次**，中途重试（例如价格令牌过期后重算金额）不换键 ——
// 换了就等于告诉后端"这是另一笔"，用户会被下两单。
//
// 历史：这段实现曾在 3 个文件里各写一份，前缀不同（order- / pay- / refund-），
// 逻辑逐字相同，已收敛到这里；前缀参数保留各自的语义。

/**
 * 生成一个**新的**幂等键。
 *
 * 优先 crypto.randomUUID()；非安全上下文（用 http + 局域网 IP 访问时
 * crypto.randomUUID 不存在）降级为 `${prefix}-${时间戳}-${随机串}`，保证功能可用。
 * 注意 `import.meta.client`：SSR 阶段没有 crypto，也不能用它生成会随请求变化的键。
 */
export function createIdempotencyKey(prefix = 'idem'): string {
  if (import.meta.client && typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}

/**
 * 「本次页面访问」的稳定幂等键：在 setup 顶层调用一次，之后所有提交/重试复用同一个值。
 *
 * ⚠️ 只该在页面组件 setup 顶层调用一次。放进 computed / watch / 事件处理里
 * 会让每次调用都换一个新键，幂等直接失效（那正是它要防的事）。
 */
export function useIdempotencyKey(prefix = 'idem') {
  return ref(createIdempotencyKey(prefix))
}
