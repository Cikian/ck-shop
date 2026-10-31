// composables/useRegionOptions.ts
//
// 国家 / 州省 / 城市 三级数据的取数层（缓存 + 请求去重 + 邮编预校验）。
//
// 为什么抽出来：
//   · 个人中心的地址面板与 /address 整页是**两份表单**（历史原因，见 types/shop-address.ts 注释），
//     级联的取数逻辑只能有一份，否则两边的缓存与失败处理会各走一套；
//   · 国家 250 条、州 5 千、城市 15 万 —— 每次展开弹层都重打后端不可接受，
//     用 useState 挂到全局 SSR 状态，同一语言下整站只取一次。
//
// 与 useAddressBook 的差别：
//   · region 是**公开参考数据**（契约 §3 / §4：不登录也能取），所以不走 useRequestFetch
//     （不需要 cookie），也没有 401 概念 —— 失败只有"网络不通/后端没起"这一种解释；
//   · 数据与访客身份无关，所以缓存可以跨请求复用（见下面的 in-flight 去重表）。
//
// 语言：地名译文是展示期现算的，**语言换了缓存必须作废**，所以每个缓存 key 里都带 lang。

import type { RegionCityVO, RegionCountryVO, RegionStateVO } from '~~/types/shop-region'

/** 城市一次取多少条：后端硬上限 500，这里取 200 —— 再多用户在长列表里也翻不动（契约 §3.3） */
const CITY_LIMIT = 200

/** 关键词结果缓存最多留多少条：一个表单会话的输入量级，超了整体清空，避免无限膨胀 */
const CITY_CACHE_MAX_KEYS = 60

/**
 * 请求去重表（in-flight）。
 *
 * 放在模块级而不是 composable 内部：同一个国家在"编辑回显 + 用户展开下拉"两个路径上
 * 会并发发起同一个请求，只有跨组件实例共享这张表才压得住。
 * region 数据是公开参考数据、语言走查询参数，跨请求复用同一个 promise 是安全的。
 */
const inflight = new Map<string, Promise<unknown>>()

function dedupe<T>(key: string, factory: () => Promise<T>): Promise<T> {
  const running = inflight.get(key)
  if (running) return running as Promise<T>

  const task = factory()
  inflight.set(key, task as Promise<unknown>)
  // 成功/失败都要摘掉在途标记：否则一次网络抖动会把这条 key 永久钉在"进行中"，
  // 之后的重试全部拿到同一个已 reject 的 promise。
  void Promise.resolve(task)
    .catch(() => {})
    .then(() => {
      if (inflight.get(key) === task) inflight.delete(key)
    })
  return task
}

/** 邮箱里的 code 都是大写；空值统一成空串，便于做缓存 key */
function normalizeCode(value: unknown): string {
  return value === null || value === undefined ? '' : String(value).trim().toUpperCase()
}

export const useRegionOptions = () => {
  const { locale } = useI18n()

  /** 当前语言：所有缓存 key 里都带上它（地名译文随语言变化） */
  const lang = computed(() => locale.value)

  const countries = useState<RegionCountryVO[]>('region-countries', () => [])
  /** 这份国家列表是按哪门语言取的；与 lang 不一致就必须重取 */
  const countriesLang = useState<string>('region-countries-lang', () => '')
  const countriesLoading = useState<boolean>('region-countries-loading', () => false)

  /**
   * 州/省：key = `${lang}|${countryCode}`。
   * ⚠️ **空数组是有效值**（该国确实没有州级数据，契约 §3.2 明确要求回空数组），
   * 所以判存在用 `if (cached)` 而不是 `if (cached?.length)`，否则每次展开都会重打后端。
   */
  const statesByKey = useState<Record<string, RegionStateVO[]>>('region-states', () => ({}))
  const statesLoading = useState<boolean>('region-states-loading', () => false)

  /** 城市：key = `${lang}|${countryCode}|${stateCode}|${keyword}`；key 里空关键词那条就是"首屏列表" */
  const citiesByKey = useState<Record<string, RegionCityVO[]>>('region-cities', () => ({}))
  const citiesLoading = useState<boolean>('region-cities-loading', () => false)

  function stateCacheKey(countryCode: string): string {
    return `${lang.value}|${countryCode}`
  }

  function cityCacheKey(countryCode: string, stateCode: string, keyword: string): string {
    return `${lang.value}|${countryCode}|${stateCode}|${(keyword || '').trim().toLowerCase()}`
  }

  /**
   * 取国家全量（250 条，一次取完 —— 契约 §5.2 要求国家级可搜索而不是分页）。
   * 已按当前语言加载过就直接用缓存；force 用于"加载失败后重试"。
   */
  async function loadCountries(force = false): Promise<RegionCountryVO[]> {
    if (!force && countriesLang.value === lang.value && countries.value.length) {
      return countries.value
    }

    countriesLoading.value = true
    try {
      const list = await dedupe(`countries|${lang.value}`, () =>
        $fetch<RegionCountryVO[]>('/api/region/countries', { query: { lang: lang.value } }))

      // 没有 code 的记录无法回写 countryCode，直接丢掉（同地址簿过滤无 id 记录的道理）
      const rows = (Array.isArray(list) ? list : []).filter(item => !!item?.code)
      countries.value = rows
      countriesLang.value = lang.value
      return rows
    } finally {
      countriesLoading.value = false
    }
  }

  /** 取某国的州/省；返回空数组 = 该国没有州级，调用方据此**跳过州省这一级** */
  async function loadStates(countryCode: string, force = false): Promise<RegionStateVO[]> {
    const code = normalizeCode(countryCode)
    if (!code) return []

    const key = stateCacheKey(code)
    const cached = statesByKey.value[key]
    if (!force && cached) return cached

    statesLoading.value = true
    try {
      const list = await dedupe(`states|${key}`, () =>
        $fetch<RegionStateVO[]>('/api/region/states', {
          query: { country: code, lang: lang.value },
        }))

      const rows = (Array.isArray(list) ? list : []).filter(item => !!item?.code)
      statesByKey.value = { ...statesByKey.value, [key]: rows }
      return rows
    } finally {
      statesLoading.value = false
    }
  }

  /**
   * 取城市。
   *
   * stateCode / keyword 都可选（契约 §3.3）：
   *   · 不传 stateCode → 全国范围（该国无州级，或用户手动填写了省/州）；
   *   · 不传 keyword   → 全国/全省人口前 200 条（首屏）；传了就按键模糊匹配。
   * 关键词结果也进缓存：用户删字重搜是常态，没必要再打一次后端。
   */
  async function loadCities(countryCode: string, stateCode = '', keyword = ''): Promise<RegionCityVO[]> {
    const code = normalizeCode(countryCode)
    if (!code) return []

    const state = (stateCode || '').trim()
    const kw = (keyword || '').trim()
    const key = cityCacheKey(code, state, kw)
    const cached = citiesByKey.value[key]
    if (cached) return cached

    citiesLoading.value = true
    try {
      const list = await dedupe(`cities|${key}`, () =>
        $fetch<RegionCityVO[]>('/api/region/cities', {
          query: {
            country: code,
            ...(state ? { stateCode: state } : {}),
            ...(kw ? { keyword: kw } : {}),
            limit: CITY_LIMIT,
            lang: lang.value,
          },
        }))

      const rows = (Array.isArray(list) ? list : []).filter(item => typeof item?.id === 'number')
      const next = { ...citiesByKey.value, [key]: rows }
      // 超上限就整体清空而不是逐条淘汰：表单场景里旧关键词结果已经没用了，
      // 逐条 LRU 只是把复杂度搬进一个不值得的地方。
      citiesByKey.value = Object.keys(next).length > CITY_CACHE_MAX_KEYS ? { [key]: rows } : next
      return rows
    } finally {
      citiesLoading.value = false
    }
  }

  /** 按 code 找国家（编辑回显：用 countryCode 反查国名） */
  function findCountry(countryCode: string | null | undefined): RegionCountryVO | undefined {
    const code = normalizeCode(countryCode)
    if (!code) return undefined
    return countries.value.find(item => item.code === code)
  }

  /** 按 code 找省（编辑回显：用 provinceCode 反查省名；州列表没加载时返回 undefined） */
  function findState(countryCode: string | null | undefined, stateCode: string | null | undefined): RegionStateVO | undefined {
    const code = normalizeCode(countryCode)
    const state = (stateCode || '').trim()
    if (!code || !state) return undefined
    return statesByKey.value[`${lang.value}|${code}`]?.find(item => item.code === state)
  }

  /**
   * 邮编前端预校验（契约 §5.2：不匹配只提示、不阻断，后端才是权威）。
   *
   * 返回：
   *   · 'empty'   —— 用户还没填，什么都不提示；
   *   · 'ok'      —— 与国家的 postalCodeRegex 匹配；
   *   · 'invalid' —— 不匹配（提示一行灰字，但**允许提交**）；
   *   · 'unknown' —— 国家没加载到 / 该国没有 postal_code_regex / 正则 JS 编译不了。
   *                 这种一定要与 invalid 区分开：把"阿联酋没有邮编"说成"格式不对"是纯噪音。
   */
  function checkPostalCode(
    countryCode: string | null | undefined,
    postalCode: string | null | undefined,
  ): 'empty' | 'ok' | 'invalid' | 'unknown' {
    const value = (postalCode || '').trim()
    if (!value) return 'empty'

    const source = (findCountry(countryCode)?.postalCodeRegex || '').trim()
    if (!source) return 'unknown'

    try {
      return new RegExp(source).test(value) ? 'ok' : 'invalid'
    } catch {
      // 后端正则里如果混进了 Java 专属语法，JS 编译会失败；这时不做判定，放行
      return 'unknown'
    }
  }

  return {
    /** 城市单次取数上限（组件用它判断"结果被截断了，请让用户输入关键词"） */
    cityLimit: CITY_LIMIT,
    countries,
    countriesLoading,
    statesLoading,
    citiesLoading,
    loadCountries,
    loadStates,
    loadCities,
    findCountry,
    findState,
    checkPostalCode,
  }
}
