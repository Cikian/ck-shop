// types/shop-region.ts
//
// 国家 / 州省 / 城市（region）三级数据的类型与语言映射 —— 唯一来源。
// 契约：docs/地址国家地区级联-实施契约-2026-10-28.md（§2 语言规则、§3 接口出参、§5 前端）。
//
// 三个 VO 与后端 org.jeecg.modules.shop.region 的出参一一对应（camelCase 照抄，不要改名）。
//
// name 与 nameLocal 的分工（契约 §1 的三条使用纪律里最容易被搞混的一条）：
//   · name      = 后端按**访客语言**解析好的显示名，已经走过 §2 的
//                 「译文 → 拉丁名(name) → 当地文字(native)」回退链；
//   · nameLocal = 当地文字原名（XX.native）。
// 前端只负责显示 name、缺失才退 nameLocal，**不要**再自己查一次译文 ——
// 译文是展示期现算的，落库永远只用当地语言（契约 §7 禁止事项）。

/** 国家 / 地区（后端 /shop/region/countries 的列表项） */
export interface RegionCountryVO {
  /** ISO 3166-1 alpha-2，权威码；落库就是它（`country_code`） */
  code: string
  /** 访客语言下的国名 */
  name: string
  /** countries.native：当地文字国名 */
  nameLocal?: string | null
  /** 国际电话区号（不含 +），如泰国 "66" */
  phoneCode?: string | null
  /** ISO 4217 币种，如 "THB" */
  currency?: string | null
  /** 邮编格式提示，如 "#####" */
  postalCodeFormat?: string | null
  /**
   * 邮编正则（后端 countries.postal_code_regex 原样透传，如 `^(\d{5})$`）。
   * 前端只用它做**提示性**预校验：不匹配只提醒、不阻断（契约 §5.2，后端才是权威）。
   * 可能为空 —— 有些国家（如阿联酋）本来就没有邮编，"没有正则"不等于"格式不对"。
   */
  postalCodeRegex?: string | null
}

/** 一级行政区：省 / 州 / 府 / 大区（后端 /shop/region/states 的列表项） */
export interface RegionStateVO {
  /** states.id */
  id: number
  /**
   * states.iso3166_2（如 "TH-10"）。
   * 该列为空时后端已经回退成 "S"+id（契约 §3.2），所以前端一定能拿到可用标识，
   * 不需要再写一层兜底。
   */
  code: string
  /** 访客语言下的省名 */
  name: string
  /** states.native：当地文字省名 */
  nameLocal?: string | null
  /** province / state / region …（只作展示辅助，不参与逻辑） */
  type?: string | null
}

/** 城市（后端 /shop/region/cities 的列表项） */
export interface RegionCityVO {
  /** cities.id；落库就是它（`city_id`） */
  id: number
  /** 访客语言下的城市名 */
  name: string
  /** cities.native：当地文字城市名 */
  nameLocal?: string | null
  /** 人口，后端已按 population DESC 排序，前端不再重排 */
  population?: number | null
}

/**
 * 店铺 locale → region 译文查表语言（契约 §2 的表格，前后端必须一致）。
 *
 * 正常取数路径上语言由 BFF 从 `?lang=` / `x-language` 透传，前端用不到这张表；
 * 导出它是为了两件事：
 *   1) 让"语言映射"这件事有一处可查的实现，而不是散落在注释里；
 *   2) 将来要做"按语言预热 CDN 缓存"这类需要自己拼查询参数的活儿时不用再猜。
 *
 * `en` 映射成 null 而不是 'en'：en 译文覆盖率只有 0.6%，查了只会更差 —— 直接用 name。
 */
const REGION_TRANSLATION_LANG_ALIASES: Record<string, string> = {
  zh: 'zh-CN',
  'zh-tw': 'zh-TW',
}

/** locale → 译文表语言；返回 null 表示"这门语言不查译文表，直接用 name" */
export function resolveRegionTranslationLang(locale: unknown): string | null {
  const raw = typeof locale === 'string' ? locale.trim().toLowerCase() : ''
  if (!raw) return null
  // en / en-US / en-GB 一律不查：英文名本来就在 name 上
  if (raw === 'en' || raw.startsWith('en-') || raw.startsWith('en_')) return null
  return REGION_TRANSLATION_LANG_ALIASES[raw] ?? raw
}

/**
 * 一条地名该显示的文案（契约 §2 显示回退链在**前端**只剩最后一跳）。
 *
 * 译文那一步后端已经算完并放进 name 了，所以这里只需要 name → nameLocal。
 * 两个都空时返回空串（调用方自己决定用占位符还是 `—`），绝不留 `undefined`。
 */
export function regionLabel(item: { name?: string | null, nameLocal?: string | null } | null | undefined): string {
  if (!item) return ''
  return (item.name || '').trim() || (item.nameLocal || '').trim()
}

/**
 * 关键词是否命中该地名（本地过滤用）。
 *
 * 为什么要同时匹配 name 与 nameLocal（契约 §5.2 的硬要求）：
 * 中文用户搜"泰"、英文用户搜"Thai"、泰国用户搜"ไทย"都该命中同一个国家 ——
 * 只匹配 name 会让非拉丁文字的用户彻底搜不到自己的国家/城市。
 * 顺带匹配 code（"TH" / "TH-10"），运维/客服按编码核对地址时很省事。
 */
export function regionMatchesKeyword(
  item: { name?: string | null, nameLocal?: string | null, code?: string | null },
  keyword: string,
): boolean {
  const kw = (keyword || '').trim().toLowerCase()
  if (!kw) return true
  return [item.name, item.nameLocal, item.code]
    .some(field => (field || '').toLowerCase().includes(kw))
}

/**
 * 收货地址表单里的「地区」v-model 契约（AddressRegionCascader 的出入参）。
 *
 * 为什么用一个对象而不是三个独立 v-model：
 * 换国家必须把省/市**一起**清空（旧的 provinceCode 属于旧国家，后端会判"不属于该国家"），
 * 拆成三个 emit 会让父组件有中间态可用（例如"国家已换、城市还是旧国家的"），
 * 一个对象原子替换则不可能出现这种脏组合。
 */
export interface AddressRegionSelection {
  /** ISO alpha-2；空串 = 用户走了"手动填写"兜底（等价于老数据/老客户端） */
  countryCode: string
  /** stateCode（iso3166_2）；空串 = 该国无州级，或用户手动填写了省/州 */
  provinceCode: string
  /** cities.id；null = 用户手动填写了城市（后端此时才采信 city 文字，契约 §3.4） */
  cityId: number | null
  /** 省/州文字：选中时取选项名，手填时取用户输入 */
  province: string
  /** 城市文字：同上 */
  city: string
  /**
   * 手填国家时的国名。
   *
   * ⚠️ 契约里的 AddressDTO **没有**国名字段（只有 countryCode），手填国名暂时无处落库，
   * BFF 会把它作为 countryName 带下去（后端 Jackson 忽略未知字段，不会报错）。
   * 见回报里的"与契约不一致"一节 —— 需要后端确认是否补字段。
   */
  countryText: string
}

/** 空的地区选择（新增地址时的初始值；所有字段都要显式给，避免 undefined 传到 payload） */
export function createEmptyRegionSelection(): AddressRegionSelection {
  return { countryCode: '', provinceCode: '', cityId: null, province: '', city: '', countryText: '' }
}

/** 两个地区选择是否完全一致（级联组件用它区分"自己发出去的"和"父组件重新填表的"） */
export function isSameRegionSelection(
  a: AddressRegionSelection | null | undefined,
  b: AddressRegionSelection | null | undefined,
): boolean {
  if (!a || !b) return a === b
  return a.countryCode === b.countryCode
    && a.provinceCode === b.provinceCode
    && a.cityId === b.cityId
    && (a.province || '') === (b.province || '')
    && (a.city || '') === (b.city || '')
    && (a.countryText || '') === (b.countryText || '')
}
