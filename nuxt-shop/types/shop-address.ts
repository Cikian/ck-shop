// types/shop-address.ts
//
// 收货地址的后端出入参类型 —— 唯一来源。
// 此前 profile.vue 与 address/index.vue 各写了一份 AddressVO（字段已经开始漂移），
// 抽取 useAddressBook 时一并收敛到这里。
//
// 2026-10-28 随「国家—州省—城市」级联一起补了国家/州/城市的编码与译名字段
// （契约 docs/地址国家地区级联-实施契约-2026-10-28.md §3.4）。

/** 地址列表项 / 详情（后端 AddressVO） */
export interface AddressVO {
  id: string
  receiverName?: string | null
  receiverPhone?: string | null
  /** 省/州：**当地语言**的落库值（如 กรุงเทพมหานคร），不是译名 */
  province?: string | null
  /** 城市：**当地语言**的落库值 */
  city?: string | null
  district?: string | null
  detailAddress?: string | null
  postalCode?: string | null
  addressTag?: string | null
  isDefault?: boolean | null
  masked?: boolean | null
  useCount?: number | null
  /** 面单格式的整串（当地语言），面单/物流对接只能用它，不要用 fullAddressLocalized */
  fullAddress?: string | null

  /* ── 国家/地区级联（契约 §3.4）─────────────────────────────────────
     ⚠️ 老数据这几个字段全为空（库里 country_code IS NULL 的存量地址）。
     凡是用它们的地方都必须按"可空"处理，回退到 province/city/fullAddress 的老语义。 */
  /** ISO 3166-1 alpha-2 */
  countryCode?: string | null
  /** 落库的国名快照（当地语言） */
  countryName?: string | null
  /** states.iso3166_2 */
  provinceCode?: string | null
  /** cities.id */
  cityId?: number | null
  /** 当前访客语言的国名（展示用，不落库） */
  countryDisplay?: string | null
  /** 当前访客语言的省名（展示用） */
  provinceDisplay?: string | null
  /** 当前访客语言的城市名（展示用） */
  cityDisplay?: string | null
  /** 当前访客语言的整串地址（屏幕展示用；拼法与 fullAddress 只差名称语言） */
  fullAddressLocalized?: string | null
}

/**
 * 提交给后端的地址载荷（新增 / 编辑共用）。
 *
 * ⚠️ isDefault 的三态（见后端 AddressDTO 注释）：null = 不改动当前默认状态，true = 设为默认。
 * 所以"没勾选"必须传 null 而不是 false ——
 * 否则编辑一条非默认地址会把用户真正想保留的默认地址顺手取消掉。
 *
 * 国家/州/城市三个编码的语义（契约 §3.4）：
 *   · countryCode 为空 → 后端**完全按老逻辑**走（老客户端 / 未改造的入口），不校验也不覆盖文字；
 *   · provinceCode / cityId 有值 → 后端以服务端解析出的当地语言名覆盖 province / city 文字；
 *   · 为空（用户走了"手动填写"）→ 后端采信前端传来的 province / city 文字。
 * 也就是说手动填写的兜底路径靠的是"编码留空 + 文字照传"，这里是它唯一成立的前提。
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
  /** ISO 3166-1 alpha-2；空串 = 手动填写了国家（走老逻辑） */
  countryCode: string
  /** states.iso3166_2；空串 = 该国无州级，或手动填写了省/州 */
  provinceCode: string
  /** cities.id；null = 手动填写了城市 */
  cityId: number | null
  /**
   * 手动填写国家时的国名。
   *
   * 契约的 AddressDTO 没有国名字段（只有 countryCode），所以这个值目前后端不会落库；
   * 仍然带上是因为：① 后端 Jackson 已配置忽略未知字段（WebMvcConfiguration 里
   * FAIL_ON_UNKNOWN_PROPERTIES=false），不会因此报错；② 后端一旦补上字段，
   * 前端不需要再改。只在 countryCode 为空时才会传，正常路径下为空串。
   */
  countryName?: string
}
