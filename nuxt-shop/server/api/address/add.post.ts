// server/api/address/add.post.ts
// POST /api/address/add —— 新增收货地址 (BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/address/add
// 入参（AddressDTO）：{ receiverName, receiverPhone, province, city, district,
//                     detailAddress, postalCode, addressTag, isDefault }
// 出参：AddressVO（新增后的完整地址）
//
// 新增时**不带 id**（带 id 就是编辑语义了，走 /api/address/edit）。
// isDefault 用 null 表示"不改动当前默认状态"，true 表示设为默认
// （服务端会把该用户其它地址的默认标记清掉，保证有且只有一个默认地址）。

import { resolveStoreLang } from '~~/types/store-locale'

/** 地址表单入参（与后端 AddressDTO 一一对应） */
interface AddressBody {
  receiverName?: string
  receiverPhone?: string
  province?: string
  city?: string
  district?: string
  detailAddress?: string
  postalCode?: string
  addressTag?: string
  isDefault?: boolean | null
  lang?: string
}

function trimOrEmpty(value: unknown): string {
  return value === null || value === undefined ? '' : String(value).trim()
}

export default defineEventHandler(async (event): Promise<unknown> => {
  const body = await readBody<AddressBody>(event)

  const receiverName = trimOrEmpty(body?.receiverName)
  const receiverPhone = trimOrEmpty(body?.receiverPhone)
  const detailAddress = trimOrEmpty(body?.detailAddress)

  if (!receiverName) {
    throw createError({ statusCode: 400, statusMessage: '请填写收货人姓名' })
  }
  if (!receiverPhone) {
    throw createError({ statusCode: 400, statusMessage: '请填写收货人手机号' })
  }
  if (!detailAddress) {
    throw createError({ statusCode: 400, statusMessage: '请填写详细地址' })
  }

  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale
  const lang = resolveStoreLang(body?.lang ?? query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: true, lang })

  const payload: Record<string, unknown> = {
    receiverName,
    receiverPhone,
    province: trimOrEmpty(body?.province),
    city: trimOrEmpty(body?.city),
    district: trimOrEmpty(body?.district),
    detailAddress,
    postalCode: trimOrEmpty(body?.postalCode),
    addressTag: trimOrEmpty(body?.addressTag),
    // null = 不改动默认状态；true = 设为默认。刻意保留 null/true/false 三态
    isDefault: body?.isDefault === undefined ? null : body.isDefault,
  }

  try {
    return await api<unknown>('/shop/order/store/address/add', {
      method: 'POST',
      body: payload,
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '地址保存失败，请稍后重试',
    })
  }
})
