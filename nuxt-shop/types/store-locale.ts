// types/store-locale.ts
//
// 店铺端「支持哪些语言」的**唯一来源**（single source of truth）。
//
// 改造前同一份语言清单散落在 3 处、必须手工同步：
//   1) nuxt.config.ts 的 i18n.locales（code / language / 展示名 / 语言包文件 / 图标）
//   2) types/shop-product.ts 的 STORE_SUPPORTED_LANGS（BFF 收敛 ?lang= / x-language 用）
//   3) types/shop-product.ts 的 INTL_LOCALE_TAGS（zh -> zh-CN，Intl 格式化用）
// 现在统一由本文件的 STORE_LOCALES 派生：
//   - nuxt.config.ts：i18n.locales / defaultLocale
//   - types/shop-product.ts：resolveStoreLang 的合法值集合、toIntlLocale 的 BCP-47 映射
//   - server/middleware/locale.ts：判断 URL 前缀（/zh/... ）是不是受支持语言
// 新增一门语言时只需要在这里加一条。
//
// 约束：本文件**零依赖**（不 import 任何 Nuxt / Vue / 第三方模块），
// 因为 nuxt.config.ts 会在配置加载阶段（jiti）直接 import 它。

/** 一门受支持语言的完整定义 */
export interface StoreLocaleDefinition {
  /** i18n locale code：同时是 URL 前缀、?lang= 取值、后端 lang 参数取值 */
  code: string
  /** BCP-47 标签，交给 Intl.NumberFormat 等使用 */
  language: string
  /** 语言选择器里的展示名 */
  name: string
  /** i18n langDir（i18n/locales/）下的语言包文件名 */
  file: string
  /** 语言选择器里的旗帜 */
  icon: string
}

/** 支持的语言清单（顺序即语言选择器的展示顺序；第一条为默认语言） */
export const STORE_LOCALES: readonly StoreLocaleDefinition[] = [
  { code: 'en', language: 'en-US', name: 'English', file: 'en.json', icon: '🇺️🇸️' },
  { code: 'zh', language: 'zh-CN', name: '中文', file: 'zh.json', icon: '🇨️🇳️' },
  { code: 'fr', language: 'fr-FR', name: 'Français', file: 'fr.json', icon: '🇫️🇷️' },
]

/** 默认语言：解析不出语言时的兜底（与 nuxt.config.ts 的 i18n.defaultLocale 同源） */
export const STORE_DEFAULT_LANG = 'en'

/** 受支持语言的 code 列表 */
export const STORE_SUPPORTED_LANGS: readonly string[] = STORE_LOCALES.map(locale => locale.code)

/** locale code -> BCP-47 标签（Intl 用），由 STORE_LOCALES 派生 */
export const STORE_INTL_LOCALE_TAGS: Record<string, string> = Object.fromEntries(
  STORE_LOCALES.map(locale => [locale.code, locale.language]),
)

/**
 * 把任意来源（query.lang / cookie / URL 前缀 / 请求头）的语言值收敛成受支持的语言 code。
 * 行为与改造前 types/shop-product.ts 里的同名函数完全一致：
 * 命中则返回小写 code，否则回退 fallback，fallback 也不支持时再回退默认语言。
 */
export function resolveStoreLang(value: unknown, fallback: string = STORE_DEFAULT_LANG): string {
  const raw = Array.isArray(value) ? value[0] : value
  const lang = typeof raw === 'string' ? raw.trim().toLowerCase() : ''
  return STORE_SUPPORTED_LANGS.includes(lang)
    ? lang
    : STORE_SUPPORTED_LANGS.includes(fallback)
      ? fallback
      : STORE_DEFAULT_LANG
}
