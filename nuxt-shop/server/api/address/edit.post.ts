// server/api/address/edit.post.ts
// POST /api/address/edit —— 编辑收货地址 (BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/address/edit
// 入参（AddressDTO，**必须有 id**）：{ id, receiverName, receiverPhone, ... , isDefault }
// 出参：AddressVO（更新后的完整地址）
//
// 服务端会校验该地址归属当前用户；isDefault 传 null 表示不改动默认状态
// （编辑一个非默认地址时，不要把用户真正想保留的默认地址顺手取消掉）。

import { resolveStoreLang } from '~~/types/store-locale'

interface AddressBody {
  id?: string
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

  const id = trimOrEmpty(body?.id)
  if (!id) {
    throw createError({ statusCode: 400, statusMessage: '缺少地址 ID' })
  }

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
    id,
    receiverName,
    receiverPhone,
    province: trimOrEmpty(body?.province),
    city: trimOrEmpty(body?.city),
    district: trimOrEmpty(body?.district),
    detailAddress,
    postalCode: trimOrEmpty(body?.postalCode),
    addressTag: trimOrEmpty(body?.addressTag),
    isDefault: body?.isDefault === undefined ? null : body.isDefault,
  }

  try {
    return await api<unknown>('/shop/order/store/address/edit', {
      method: 'POST',
      body: payload,
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '地址更新失败，请稍后重试',
    })
  }
})
