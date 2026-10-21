// composables/useAuthForm.ts
//
// 登录 / 注册 / 忘记密码三个页面的**共享表单逻辑**。
//
// 为什么抽出来：这三个页面有 3 段完全一样的逻辑，散在三份实现里迟早会改歪一处：
//   1. 发送邮箱验证码 + 60 秒倒计时（契约 4.4 明确要求）
//   2. 密码强度评分（弱/中/强轻提示，契约要求"轻量、不弹窗、不阻断"）
//   3. 前端字段校验 + 后端错误文案归位
//
// 本次改造：旧的图形验证码（useCaptcha / imageBase64 / checkKey / captchaVerified）
// 已整体删除。人机验证改为 CikVerify：页面在点击「获取验证码」时调用
// useCikianCaptcha().open() 拿到一次性 ticket，再随 requestEmailCode 提交。
// 本 composable 只负责「拿着 ticket 发信 + 倒计时」，不碰验证码弹窗
// （弹窗只能在点击回调里由页面触发，且要单独处理用户关闭等错误）。
//
// ⚠️ 这里只做**展示层**的校验与编排：
// 真实校验（邮箱是否已注册、验证码对不对、密码是否合规）一律以后端为准，
// 前端校验的价值是"少发一次注定失败的请求"，绝不能替代后端校验。

import { resolveAuthErrorMessage, useAuth, type EmailCodeScene } from '~~/composables/useAuth'

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
  const { t, locale } = useI18n()
  const { sendEmailCode } = useAuth()

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

  /**
   * 「获取验证码」按钮是否可点。
   *
   * 只判断两件事：倒计时结束 + 邮箱非空。人机验证在点击之后才弹出，
   * 不需要用户预先输入任何图形码（旧版这里还要 `captcha.trim()`）。
   */
  const canSendCode = (email: string) => countdown.value <= 0 && !!email.trim()

  /**
   * 发送邮箱验证码。
   *
   * ticket 是页面先通过 CikVerify 弹窗拿到的一次性凭证，必须原样交给 BFF：
   * Java 端会消费它（一次性、绑定 IP+UA、120s 有效），失败即发信失败。
   *
   * @returns 成功返回 true；失败返回 false 并把错误文案写进 `errorRef`
   */
  const requestEmailCode = async (
    payload: { email: string, scene: EmailCodeScene, ticket: string },
    errorRef: { value: string },
  ): Promise<boolean> => {
    errorRef.value = ''
    try {
      const data = await sendEmailCode({
        email: payload.email.trim(),
        scene: payload.scene,
        ticket: payload.ticket,
        // 语言必须随请求一起传：BFF 用 resolveStoreLang(body.lang) 决定 Java 端
        // 业务错误文案（"该邮箱已注册/尚未注册/发送过于频繁"）的语言。
        // 不传的话中文/法语站点会收到英文错误提示（曾经的真实回归）。
        lang: locale.value,
      })
      // 倒计时以**后端**给的 cooldownSeconds 为准：后端才是限流的事实来源，
      // 前端写死 60 会让两边规则各改各的。
      startCountdown(data?.cooldownSeconds || 60)
      return true
    } catch (e: any) {
      errorRef.value = resolveAuthErrorMessage(e) || t('auth.sendCodeFailed')
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
