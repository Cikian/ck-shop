// composables/useAuthForm.ts
//
// 登录 / 注册 / 忘记密码三个页面的**共享表单逻辑**。
//
// 为什么抽出来：这三个页面有 4 段完全一样的逻辑，散在三份实现里迟早会改歪一处：
//   1. 图形验证码（useCaptcha 的 checkKey / refreshCaptcha 调用时机完全一致）
//   2. 发送邮箱验证码 + 60 秒倒计时（契约 4.4 明确要求）
//   3. 密码强度评分（弱/中/强轻提示，契约要求"轻量、不弹窗、不阻断"）
//   4. 前端字段校验 + 后端错误文案归位
//
// ⚠️ 这里只做**展示层**的校验与编排：
// 真实校验（邮箱是否已注册、验证码对不对、密码是否合规）一律以后端为准，
// 前端校验的价值是"少发一次注定失败的请求"，绝不能替代后端校验。

import { resolveAuthErrorMessage, useAuth, type EmailCodeScene } from '~~/composables/useAuth'
// ⚠️ 必须**显式** import：本项目的 composables/ 目录在 Nuxt 的 srcDir(app/) 之外，
// Nuxt 不会自动注册它们（同一个原因，useAuth 也必须显式 import）。
// 漏掉这一行会导致 SSR 直接抛 "useCaptcha is not defined"，
// 三个认证页面全部 500 —— 是实测踩过的坑，不要删。
import { useCaptcha } from '~~/composables/useCaptcha'

/** 密码强度档位：0=空 1=弱 2=中 3=强 */
export type PasswordStrength = 0 | 1 | 2 | 3

/** 契约 2.2 的密码规则：8-32 位，且至少含字母和数字 */
export const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)[\S]{8,32}$/

/**
 * 邮箱合法性。
 *
 * 刻意用宽松规则而不是严格 RFC 5322：邮箱的**唯一权威判定在后端**
 * （真正发信才知道存不存在），前端过严只会把 `a+b@x.co` 这类合法地址挡在门外，
 * 反而制造"我明明填对了却提交不了"的投诉。这里只拦明显不是邮箱的输入。
 */
export const EMAIL_RULE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

/**
 * 评分规则（与后端 PASSWORD_RULE 保持同向，但不作为提交阻断条件）。
 *
 * 注意后端硬规则只有"8-32 位 + 字母 + 数字"，所以这里**长度不计入加分项**，
 * 只用来判"弱"：短于 8 位或类型过少直接弱。避免出现"提示条满格却被后端拒绝"的矛盾。
 */
export const scorePassword = (value: string): PasswordStrength => {
  if (!value) return 0
  if (value.length < 8) return 1

  let kinds = 0
  if (/[a-z]/.test(value)) kinds++
  if (/[A-Z]/.test(value)) kinds++
  if (/\d/.test(value)) kinds++
  if (/[^A-Za-z0-9]/.test(value)) kinds++

  if (kinds <= 1) return 1
  if (kinds === 2) return 2
  return value.length >= 12 ? 3 : 2
}

export const useAuthForm = () => {
  const { t } = useI18n()
  const { sendEmailCode } = useAuth()
  const { imageBase64, checkKey, refreshCaptcha } = useCaptcha()

  // 图形验证码在三个页面上都要用，且都是"页面打开时拉一次"。
  // onMounted 只在浏览器端执行：验证码对 SSR / SEO 毫无意义，
  // 在服务端每次渲染都请求一张只会白白消耗后端资源。
  onMounted(() => {
    refreshCaptcha()
  })

  /** 图形验证码输错 / 过期后必须换一张，否则用户拿同一张失效的再试一次必然又失败 */
  const resetCaptcha = (clearField?: { captcha: string }) => {
    if (clearField) clearField.captcha = ''
    refreshCaptcha()
  }

  // ===== 邮箱验证码倒计时（契约 4.4：60s，禁用期间显示剩余秒数）=====
  const countdown = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  const stopCountdown = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  const startCountdown = (seconds = 60) => {
    stopCountdown()
    countdown.value = Math.max(1, Math.floor(seconds))
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) {
        countdown.value = 0
        stopCountdown()
      }
    }, 1000)
  }

  // 组件卸载时清掉定时器：否则用户发完验证码立刻跳走，回调还会对着已卸载的 ref 跑 60 秒
  onBeforeUnmount(stopCountdown)

  const canSendCode = (email: string, captcha: string) =>
    countdown.value <= 0 && !!email.trim() && !!captcha.trim()

  /**
   * 发送邮箱验证码。
   *
   * @returns 成功返回 true；失败返回 false 并把错误文案写进 `errorRef`
   * 失败时**一定刷新图形验证码**：后端校验图形验证码是一次性的，
   * 不管这次失败是图形码错、邮箱已注册还是触发了限流，旧的那张都已作废。
   */
  const requestEmailCode = async (
    payload: { email: string, scene: EmailCodeScene, captcha: string },
    errorRef: { value: string },
  ): Promise<boolean> => {
    errorRef.value = ''
    try {
      const data = await sendEmailCode({
        email: payload.email.trim(),
        scene: payload.scene,
        captcha: payload.captcha.trim(),
        checkKey: checkKey.value,
      })
      // 倒计时以**后端**给的 cooldownSeconds 为准：后端才是限流的事实来源，
      // 前端写死 60 会让两边规则各改各的。
      startCountdown(data?.cooldownSeconds || 60)
      return true
    } catch (e: any) {
      errorRef.value = resolveAuthErrorMessage(e) || t('auth.sendCodeFailed')
      resetCaptcha()
      return false
    }
  }

  /**
   * 密码一致性 + 强度校验（注册 / 重置密码共用）。
   *
   * @returns 错误文案；通过则返回空串
   */
  const validateNewPassword = (password: string, confirmPassword: string): string => {
    if (!password) return t('auth.passwordRequired')
    if (!PASSWORD_RULE.test(password)) return t('auth.passwordTooWeak')
    if (password !== confirmPassword) return t('auth.passwordMismatch')
    return ''
  }

  const validateEmail = (email: string): string => {
    if (!email.trim()) return t('auth.emailRequired')
    if (!EMAIL_RULE.test(email.trim())) return t('auth.emailInvalid')
    return ''
  }

  return {
    // 图形验证码
    imageBase64,
    refreshCaptcha,
    resetCaptcha,
    // 邮箱验证码
    countdown,
    canSendCode,
    requestEmailCode,
    startCountdown,
    // 校验
    validateEmail,
    validateNewPassword,
    scorePassword,
    validateEmailRule: EMAIL_RULE,
    validatePasswordRule: PASSWORD_RULE,
  }
}
