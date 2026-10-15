// types/shop-address.ts
//
// 收货地址的后端出入参类型 —— 唯一来源。
// 此前 profile.vue 与 address/index.vue 各写了一份 AddressVO（字段已经开始漂移），
// 抽取 useAddressBook 时一并收敛到这里。

/** 地址列表项 / 详情（后端 AddressVO） */
export interface AddressVO {
  id: string
  receiverName?: string | null
  receiverPhone?: string | null
  province?: string | null
  city?: string | null
  district?: string | null
  detailAddress?: string | null
  postalCode?: string | null
  addressTag?: string | null
  isDefault?: boolean | null
  masked?: boolean | null
  useCount?: number | null
  fullAddress?: string | null
}

/**
 * 提交给后端的地址载荷（新增 / 编辑共用）。
 *
 * ⚠️ isDefault 的三态（见后端 AddressDTO 注释）：null = 不改动当前默认状态，true = 设为默认。
 * 所以"没勾选"必须传 null 而不是 false ——
 * 否则编辑一条非默认地址会把用户真正想保留的默认地址顺手取消掉。
 */
export interface AddressPayload {
  receiverName: string
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
  postalCode: string
  addressTag: string
  isDefault: true | null
  lang: string
}
