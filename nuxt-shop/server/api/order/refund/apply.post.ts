// server/api/order/refund/apply.post.ts
// POST /api/order/refund/apply —— 店铺端「申请退款」(BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/refund/apply
// 入参（RefundApplyDTO）：
//   { orderNo, requestId, items?: [{ orderItemId, quantity }], reason, description, images? }
//
// 三个关键契约：
//   1) **请求体里没有退款金额**。退款额由服务端按「订单行成交价 × 退款数量 − 已分摊优惠」
//      计算，并受「累计退款 ≤ 实收金额」约束。前端只传数量，绝不传金额。
//   2) **requestId 必填**（幂等键）：用户在「申请退款」上连点两次只产生一张退款单。
//      由 RefundApplyModal 在弹层打开时生成一次并复用。
//   3) **items 不传 = 整单全退**。所以只有真的按行退时才下发 items 数组。
//
// reason 取值来自后端字典 shop_refund_reason：
//   no_need / duplicate / mismatch / quality / missing / logistics / other

import { resolveStoreLang } from '~~/types/store-locale'

/** 后端字典 shop_refund_reason 的合法取值（前端下拉只提供这几项，不自由发挥） */
const REFUND_REASONS = ['no_need', 'duplicate', 'mismatch', 'quality', 'missing', 'logistics', 'other']

/**
 * 单次退款申请的明细行上限与凭证图片上限。
 *
 * 不设上限时，超大数组会被原样转发给后端（逐行写退款明细 + 占额度），
 * 属于"一个请求放大成上万次写库"的入口。正常订单不会有几十行明细。
 */
const MAX_REFUND_ITEMS = 50
const MAX_REFUND_IMAGES = 9

interface RefundItemBody {
  orderItemId?: string
  quantity?: number
}

interface RefundApplyBody {
  orderNo?: string
  requestId?: string
  items?: RefundItemBody[] | null
  reason?: string
  description?: string
  images?: string[]
  lang?: string
}

export default defineEventHandler(async (event): Promise<unknown> => {
  const body = await readBody<RefundApplyBody>(event)

  const orderNo = String(body?.orderNo ?? '').trim()
  if (!orderNo) {
    throw createError({ statusCode: 400, statusMessage: '缺少订单号' })
  }

  const requestId = String(body?.requestId ?? '').trim()
  if (!requestId) {
    throw createError({ statusCode: 400, statusMessage: '缺少幂等键，请关闭弹层后重试' })
  }

  // items 缺省 / 空数组都表示「整单全退」：此时**不带** items 字段，
  // 因为后端把「不传」当作整单退款的信号。
  const rawItems = Array.isArray(body?.items) ? body.items : []
  if (rawItems.length > MAX_REFUND_ITEMS) {
    throw createError({
      statusCode: 400,
      statusMessage: `一次最多提交 ${MAX_REFUND_ITEMS} 行退款明细，请分批申请`,
    })
  }
  const items = rawItems
    .map(item => ({
      orderItemId: String(item?.orderItemId ?? '').trim(),
      quantity: Math.trunc(Number(item?.quantity)),
    }))
    .filter(item => item.orderItemId && Number.isFinite(item.quantity) && item.quantity > 0)

  // 凭证图片同样要有上限：数组越长后端要存的 URL 越多，也是放大攻击的入口
  const rawImages = Array.isArray(body?.images) ? body.images : []
  if (rawImages.length > MAX_REFUND_IMAGES) {
    throw createError({
      statusCode: 400,
      statusMessage: `最多上传 ${MAX_REFUND_IMAGES} 张凭证图片`,
    })
  }

  const reason = String(body?.reason ?? '').trim()
  if (reason && !REFUND_REASONS.includes(reason)) {
    throw createError({ statusCode: 400, statusMessage: '退款原因不在可选范围内' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  const payload: Record<string, unknown> = {
    orderNo,
    requestId,
    reason,
    description: String(body?.description ?? '').trim(),
  }
  if (items.length) {
    payload.items = items
  }
  if (rawImages.length) {
    payload.images = rawImages.map(url => String(url)).filter(Boolean)
  }

  try {
    return await api<unknown>('/shop/order/store/refund/apply', {
      method: 'POST',
      body: payload,
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '退款申请提交失败，请稍后重试',
    })
  }
})
