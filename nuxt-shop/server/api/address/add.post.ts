// server/api/address/add.post.ts
// POST /api/address/add —— 新增收货地址 (BFF)
//
// 转发目标：Java 后端 POST /shop/order/store/address/add
// 入参（AddressDTO）：{ receiverName, receiverPhone, province, city, district,
//                     detailAddress, postalCode, addressTag, isDefault,
//                     countryCode, provinceCode, cityId }
// 出参：AddressVO（新增后的完整地址）
//
// 新增时**不带 id**（带 id 就是编辑语义了，走 /api/address/edit）。
// isDefault 用 null 表示"不改动当前默认状态"，true 表示设为默认
// （服务端会把该用户其它地址的默认标记清掉，保证有且只有一个默认地址）。
//
// 【国家/地区级联（契约 §3.4）】三个编码字段一律透传，空值也照发：
//   · countryCode 为空 = 老客户端 / 用户走了"手动填写"兜底 → 后端按旧逻辑走，不校验也不覆盖文字；
//   · provinceCode / cityId 有值 → 后端以服务端解析的当地语言名覆盖 province / city。
// 这里**不要**把空值过滤掉（"没传"和"传了空"在后端是同一个意思，但少一条分支更不容易出错）。

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
  countryCode?: string
  provinceCode?: string
  cityId?: number | string | null
  /** 手填国家时的国名（契约里 DTO 还没有这个字段，后端会忽略；见 types/shop-address.ts） */
  countryName?: string
  lang?: string
}

function trimOrEmpty(value: unknown): string {
  return value === null || value === undefined ? '' : String(value).trim()
}

/** cityId 允许数字或数字字符串（表单 v-model 常见两种形态），非法值一律当"没选" */
function toOptionalInt(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null
  const num = Number(value)
  return Number.isFinite(num) && Number.isInteger(num) && num > 0 ? num : null
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

  const countryCode = trimOrEmpty(body?.countryCode).toUpperCase()
  const countryName = trimOrEmpty(body?.countryName)

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
    // 国家/地区级联：ISO 大写（库里 iso2 是大写，大小写混用会让后端查不到）
    countryCode,
    provinceCode: trimOrEmpty(body?.provinceCode),
    cityId: toOptionalInt(body?.cityId),
    // 只在"手填国家"时才带国名，正常路径不带，避免给后端塞一个契约外字段
    ...(countryCode ? {} : countryName ? { countryName } : {}),
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
