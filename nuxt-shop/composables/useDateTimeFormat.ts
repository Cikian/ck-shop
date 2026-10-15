// composables/useDateTimeFormat.ts
//
// 时间显示的**唯一实现**。
//
// 之前页面里各写各的：
//   · profile.vue 用 Intl.DateTimeFormat(locale.value, ...) 格式化注册时间（但 locale 没走 BCP-47 映射）；
//   · orders/index.vue 直接把后端的 "2026-10-15 17:22:17" 原样渲染出来；
//   · checkout/index.vue 又用了 toIntlLocale(...)。
// 结果是同一个概念在三个页面三种样子。
//
// 统一口径：
//   · 后端给的是 "yyyy-MM-dd HH:mm:ss"（也可能是 ISO），两种都要能解析；
//   · locale 一律经 toIntlLocale 映射成 BCP-47（zh -> zh-CN、fr -> fr-FR），
//     否则 'zh' 这种裸语言码在 Intl 里虽然能用，但格式与其它页面不一致；
//   · 解析不出来就原样返回（绝不显示 "Invalid Date"），空值返回空串由调用方决定隐藏整块。

import { STORE_DEFAULT_LANG, STORE_INTL_LOCALE_TAGS } from '~~/types/store-locale'

/** locale code -> BCP-47 标签；未知值时回退默认语言 */
function toTag(locale?: string | null): string {
  const key = (locale || '').trim()
  return STORE_INTL_LOCALE_TAGS[key]
    || STORE_INTL_LOCALE_TAGS[STORE_DEFAULT_LANG]
    || 'en-US'
}

/**
 * 格式化日期时间（默认「中等日期 + 短时间」，如 2026年10月15日 17:22）。
 *
 * @param value  后端返回的时间字符串
 * @param locale 当前 i18n locale code（如 'zh'）
 * @param options 自定义 Intl 选项，例如只要日期：{ dateStyle: 'medium' }
 */
export function formatDateTime(
  value: unknown,
  locale?: string | null,
  options?: Intl.DateTimeFormatOptions,
): string {
  if (value === null || value === undefined || value === '') return ''
  const raw = String(value)
  // 后端常见格式是 "yyyy-MM-dd HH:mm:ss"，Safari/Node 对该格式解析不一致，先归一化成 ISO 风格
  const parsed = new Date(raw.includes(' ') && !raw.includes('T') ? raw.replace(' ', 'T') : raw)
  if (Number.isNaN(parsed.getTime())) return raw
  return new Intl.DateTimeFormat(
    toTag(locale),
    options ?? { dateStyle: 'medium', timeStyle: 'short' },
  ).format(parsed)
}

/** 只显示日期（年/月/日），用于"注册时间""下单日期"这类不含时刻的场景 */
export function formatDate(value: unknown, locale?: string | null): string {
  return formatDateTime(value, locale, { dateStyle: 'medium' })
}
