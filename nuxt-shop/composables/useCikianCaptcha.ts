// composables/useCikianCaptcha.ts
//
// CikVerify 浏览器 SDK（npm 包 @cikian/captcha-web@1.0.0）在本项目里的唯一入口。
//
// 为什么集中在这里，而不是每个页面各写一遍 createCaptcha()：
//   · SDK 是「单例 + 弹窗」模型：同一个实例在已打开时会复用同一个 Promise（防重复弹窗），
//     页面级各自 new 会让用户快速切换页面后出现两个遮罩；
//   · challenge/verify 的路径、类型、主题色、三语文案都要保持一致，散出去必然改歪；
//   · 资源（tac.js / tac.css）由 SDK 注入并缓存在模块级，单例才能避免重复注入。
//
// 关键设计：
//   1. 客户端单例：instance / loading 都是模块级变量，SSR 期间不会初始化。
//   2. 动态 import：`open()` 被点击时才 import('@cikian/captcha-web')，
//      SSR 的构建产物里不会因为静态 import 而把浏览器 SDK 拉进服务端执行路径。
//   3. SSR 明确抛错：只能在 click 回调（客户端）调用 open()，服务端调用直接给出
//      可读的错误，而不是在 SDK 里炸出 "window is not defined"。
//   4. 不打印 ticket：ticket 是一次性凭证，进日志等于把"人机验证"白送出去。
//   5. locale 变化时 setLocale：只影响**下一次** open() 的 lang/aria-label 与文案，
//      已打开的弹窗不受影响（SDK 的既有语义）。
//
// 文案来源：i18n/locales/*.json 的 `captcha.*`（15 个 key，中文按 CikVerify
// 用户接入手册 §6.1）。用 vue-i18n 的 te() 判存在、t() 取值；某个 key 缺失时
// 直接不传，SDK 会回退它的内置中文 —— 这正是手册 §6.2 推荐的映射方式。
//
// 类型：默认 SLIDER；联调时可用环境变量 NUXT_PUBLIC_CAPTCHA_TYPE=POW 覆盖
// （nuxt.config.ts 的 runtimeConfig.public.captchaType）。

import type { CaptchaInstance, CaptchaMessages } from '@cikian/captcha-web'
import { STORE_INTL_LOCALE_TAGS } from '~~/types/store-locale'

/** 与 CikVerify 用户接入手册 §6.1 的 15 个 key 一一对应。 */
const CAPTCHA_MESSAGE_KEYS = [
  'tipsSuccess',
  'tipsError',
  'tipsExpired',
  'tipsNetworkError',
  'sliderTitle',
  'concatTitle',
  'rotateTitle',
  'imageClickTitle',
  'imageClickConfirm',
  'powTitle',
  'powLoading',
  'disabledTitle',
  'closeTitle',
  'refreshTitle',
  'overlayLabel',
] as const satisfies readonly (keyof CaptchaMessages)[]

const DEFAULT_CAPTCHA_TYPE = 'SLIDER'

/**
 * 浏览器端单例。刻意放在模块作用域而不是 useState：
 * SDK 实例持有 DOM 节点/定时器，把它序列化进 Nuxt payload 毫无意义且有害。
 */
let instance: CaptchaInstance | null = null
let loading: Promise<CaptchaInstance> | null = null

/** 把项目 locale code（zh/en/fr）映射成 SDK 期望的 BCP-47（zh-CN / en-US / fr-FR）。 */
function toCaptchaLocale(locale: string): string {
  return STORE_INTL_LOCALE_TAGS[locale] || 'zh-CN'
}

/** 按 i18n 的 15 个 caption.* key 组装 SDK messages；缺失的 key 不传，交给 SDK 回退中文。 */
function buildMessages(
  te: (key: string) => boolean,
  t: (key: string) => string,
): Partial<CaptchaMessages> {
  const messages: Partial<CaptchaMessages> = {}
  for (const key of CAPTCHA_MESSAGE_KEYS) {
    const path = `captcha.${key}`
    if (!te(path)) continue
    const value = t(path)
    // vue-i18n 在 key 缺失时会返回 key 本身：再判一次，避免把 "captcha.xxx" 当文案传下去
    if (typeof value === 'string' && value && value !== path) {
      const target = messages as Record<string, string>
      target[key] = value
    }
  }
  return messages
}

export const useCikianCaptcha = () => {
  const config = useRuntimeConfig()
  const { t, te, locale } = useI18n()

  /** 运行时配置只读一次即可：NUXT_PUBLIC_CAPTCHA_TYPE 在构建/启动时固定。 */
  const resolveType = (): string => {
    const raw = String(config.public.captchaType || DEFAULT_CAPTCHA_TYPE).trim().toUpperCase()
    return raw || DEFAULT_CAPTCHA_TYPE
  }

  /** 首次调用时动态加载并创建实例；并发调用复用同一个 Promise。 */
  const ensureInstance = async (): Promise<CaptchaInstance> => {
    if (import.meta.server) {
      throw new Error(
        '[useCikianCaptcha] 验证码只能在浏览器端通过点击回调打开；SSR 期间不允许调用 open()',
      )
    }

    if (instance) return instance

    if (!loading) {
      loading = import('@cikian/captcha-web')
        .then(({ createCaptcha }) => {
          instance = createCaptcha({
            // 同源 BFF 路径（server/api/captcha/*），不直接把 Java 地址暴露给浏览器
            challengeUrl: '/api/captcha/challenge',
            verifyUrl: '/api/captcha/verify',
            type: resolveType(),
            locale: toCaptchaLocale(locale.value),
            messages: buildMessages(te, t),
            // 品牌色：与 tailwind.css 的 --color-brand-500/600 对齐，滑轨不显得突兀
            theme: {
              moveTrackMaskBgColor: '#FF4D6D',
              moveTrackMaskBorderColor: '#E63E5C',
            },
          })
          return instance
        })
        .finally(() => {
          loading = null
        })
    }

    return await loading
  }

  // 语言切换后同步下一次弹窗的语言/文案。
  // 只在当前组件存活期间生效（watch 绑定组件作用域）；下一次 open 前 SDK 已更新。
  watch(locale, (value) => {
    instance?.setLocale(toCaptchaLocale(value), buildMessages(te, t))
  })

  /**
   * 打开验证码弹窗，用户通过后 resolve 一次性 ticket。
   *
   * 调用方（三个认证页的 handleSendCode）必须处理这些 reject reason：
   *   · closed        用户主动关闭 —— 静默返回，不报错；
   *   · resource-load tac CDN/CSP 加载失败 —— 提示稍后重试；
   *   · timeout       操作超时 —— 提示稍后重试；
   *   · ticket-missing 后端 verify 没返回 ticket —— 部署/契约问题，提示重试；
   *   · challenge-failed / init-failed / unsupported —— 同上。
   * ⚠️ 不要打印 resolve 出来的 ticket。
   */
  const open = async (): Promise<string> => {
    if (import.meta.server) {
      throw new Error(
        '[useCikianCaptcha] 验证码只能在浏览器端通过点击回调打开；SSR 期间不允许调用 open()',
      )
    }
    const captcha = await ensureInstance()
    // SPA 切换语言时，旧组件的 locale watcher 会随卸载销毁；新页面的 watcher
    // 只在“语言再次变化”时触发，不会补一次初始同步。这里在每次打开前显式
    // setLocale，保证弹窗文案与当前页面语言一致（SDK 语义：下一次 open 生效）。
    captcha.setLocale(toCaptchaLocale(locale.value), buildMessages(te, t))
    return await captcha.open()
  }

  return { open }
}
