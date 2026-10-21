// app/utils/locale-flag.ts
//
// 语言 code -> 旗帜 code 的**纯映射模块**（无副作用、无第三方依赖）。
//
// 为什么不在 types/store-locale.ts 里直接存旗帜：
//   1) 那个文件是语言清单的唯一来源，但 nuxt.config.ts 会在 jiti 阶段直接 import 它，
//      必须保持零依赖、只描述「有哪些语言」；旗帜属于展示层细节，不该混进配置数据。
//   2) 旗帜以前以 emoji「区域指示符 + U+FE0F」序列存在清单里，会一路序列化进 __NUXT__ payload。
//
// 为什么旗帜最终用**内联 SVG** 而不是 emoji：
//   Windows 没有系统国旗字体，emoji 国旗必然退化成两个方框字母（这就是用户截图里
//   「US / CN / FR」显示成方块的原因）。SVG 不依赖系统字体，跨平台渲染一致，
//   还能用小程序 token 化的 class 控制尺寸与描边。
//
// 映射表的 key 用宽松的 `Record<string, FlagCode>` 而不是 `Record<StoreLocaleCode, ...>`：
// 避免与 store-locale.ts 产生类型层面的强耦合（那边新增语言时这里只是「缺项」，
// 由下面的 STORE_LOCALE_FLAG_MISSING / 自检脚本卡住，而不是让整个项目类型报错）。
//
// 这里用**相对路径**而不是 `~~/types/store-locale`：本模块是纯数据 + 纯函数，
// 用相对路径后可以被裸 Node 直接 import 做自检（`~~` 是 Nuxt 专属别名，
// 只有经过 Nuxt/vite 才能解析，裸 Node 会抛 ERR_MODULE_NOT_FOUND）。

import { STORE_LOCALES } from '../../types/store-locale.ts'

/** 本仓库目前支持的三面旗帜 */
export type FlagCode = 'us' | 'cn' | 'fr'

/**
 * 旗帜 code -> 无障碍用的国家/地区英文名。
 * 只用于 title 属性（鼠标悬停可见），不进 aria-label —— 旗帜旁边永远有可见的语言名，
 * 再播报一遍国家名是重复噪音。
 */
export const FLAG_TITLES: Record<FlagCode, string> = {
  us: 'United States',
  cn: 'China',
  fr: 'France',
}

/** i18n locale code -> 旗帜 code */
const FLAG_BY_LOCALE: Record<string, FlagCode> = {
  en: 'us',
  zh: 'cn',
  fr: 'fr',
}

/**
 * 语言 code -> 旗帜 code，**从 STORE_LOCALES 派生**，保持「语言清单唯一来源」不破。
 *
 * 语言清单新增一门语言而这里没跟上时，该项会被直接跳过（渲染成空白而不是崩掉）；
 * 同时下面的 STORE_LOCALE_FLAG_MISSING 会列出缺项，便于自检脚本 / 评审发现。
 */
export const STORE_LOCALE_FLAG: Record<string, FlagCode> = Object.fromEntries(
  STORE_LOCALES.flatMap((locale) => {
    const flag = FLAG_BY_LOCALE[locale.code]
    return flag ? [[locale.code, flag] as const] : []
  }),
) as Record<string, FlagCode>

/** 语言清单里有、但还没配旗帜的语言 code（正常应为空数组） */
export const STORE_LOCALE_FLAG_MISSING: readonly string[] = STORE_LOCALES
  .map(locale => locale.code)
  .filter(code => !STORE_LOCALE_FLAG[code])

/** 把任意输入收敛成旗帜 code；未知 / 空值返回 ''（调用方据此渲染占位或整块不渲染） */
export function resolveLocaleFlag(code?: string | null): FlagCode | '' {
  if (typeof code !== 'string') return ''
  return STORE_LOCALE_FLAG[code.trim().toLowerCase()] ?? ''
}

/** 旗帜的无障碍英文名（title 属性用）；未知 code 返回空串 */
export function flagLabel(code?: string | null): string {
  const flag = resolveLocaleFlag(code)
  return flag ? FLAG_TITLES[flag] : ''
}
