<template>
  <!-- 注册页：邮箱 + 邮箱验证码 + 密码 + 确认密码（契约 4.2） -->
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <section class="relative flex justify-center px-4 pb-20 pt-6 sm:px-6 sm:pt-10">
      <div class="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
        <div class="brand-gradient-soft absolute inset-0"></div>
        <div class="absolute -left-24 top-0 h-72 w-72 rounded-full bg-[var(--color-brand-200)]/45 blur-3xl"></div>
        <div class="absolute -right-20 top-24 h-80 w-80 rounded-full bg-[var(--color-accent-200)]/40 blur-3xl"></div>
      </div>

      <div class="relative w-full max-w-[440px]">
        <div class="ui-card p-6 sm:p-9">
          <div class="flex flex-col items-center text-center">
            <span class="brand-gradient flex h-14 w-14 items-center justify-center rounded-full text-white shadow-[var(--shadow-pop)]">
              <UIcon name="lucide:user-plus" class="h-6 w-6" aria-hidden="true" />
            </span>
            <h1 class="mt-4 text-2xl font-black tracking-tight text-[var(--color-ink)]">
              {{ t('auth.register') }}
            </h1>
            <p class="mt-1.5 text-sm text-[var(--color-muted)]">
              {{ t('auth.registerSubtitle') }}
            </p>
          </div>

          <form class="mt-7 flex flex-col gap-4" @submit.prevent="handleRegister">
            <AuthEmailCodeFields
              v-model:email="form.email"
              v-model:captcha="form.captcha"
              v-model:code="form.emailCode"
              :captcha-image="imageBase64"
              :countdown="countdown"
              :sending="sendingCode"
              :send-disabled="!canSendCode(form.email, form.captcha)"
              :disabled="loading"
              :email-error="fieldErrors.email"
              :captcha-error="fieldErrors.captcha"
              :code-error="fieldErrors.emailCode"
              @email-blur="validateEmailField"
              @refresh-captcha="refreshCaptcha"
              @send-code="handleSendCode"
            />

            <AuthPasswordField
              v-model="form.password"
              :label="t('auth.password')"
              :placeholder="t('auth.passwordPlaceholder')"
              autocomplete="new-password"
              show-strength
              :strength="passwordStrength"
              :error="fieldErrors.password"
              :disabled="loading"
              @blur="validatePasswordField"
            />

            <AuthPasswordField
              v-model="form.confirmPassword"
              :label="t('auth.confirmPassword')"
              :placeholder="t('auth.confirmPasswordPlaceholder')"
              autocomplete="new-password"
              :error="fieldErrors.confirmPassword"
              :disabled="loading"
              @blur="validateConfirmField"
            />

            <!-- 用户名说明：username 由系统生成（契约 2.2），必须提前讲清楚，
                 否则用户注册完看到 u_xxxx 会以为填错了 -->
            <p class="flex items-start gap-1.5 rounded-[var(--radius-input)] bg-[var(--color-brand-50)] px-3.5 py-2.5 text-xs text-[var(--color-ink-soft)]">
              <UIcon name="lucide:info" class="mt-0.5 h-3.5 w-3.5 shrink-0 text-[var(--color-brand-500)]" aria-hidden="true" />
              <span>{{ t('auth.usernameAutoHint') }}</span>
            </p>

            <button
              type="submit"
              class="ui-pill ui-pill-brand h-12 w-full text-[15px] disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="loading"
              :aria-busy="loading"
            >
              <UIcon
                v-if="loading"
                name="lucide:loader-circle"
                class="h-4 w-4 animate-spin motion-reduce:animate-none"
                aria-hidden="true"
              />
              {{ loading ? t('auth.registering') : t('auth.register') }}
            </button>

            <p
              v-if="errorMsg"
              role="alert"
              class="flex items-start gap-2 rounded-[var(--radius-input)] bg-[var(--color-error-50)] px-3.5 py-2.5 text-[13px] font-medium text-[var(--color-error-600)]"
            >
              <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              <span>{{ errorMsg }}</span>
            </p>
          </form>

          <p class="mt-6 text-center text-sm text-[var(--color-muted)]">
            {{ t('auth.haveAccount') }}
            <NuxtLinkLocale
              to="/login"
              class="cursor-pointer font-bold text-[var(--color-brand-600)] underline-offset-2 transition-colors duration-200 hover:text-[var(--color-brand-700)] hover:underline motion-reduce:transition-none"
            >
              {{ t('auth.goLogin') }}
            </NuxtLinkLocale>
          </p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { resolveAuthErrorMessage, useAuth } from '~~/composables/useAuth'
import { useAuthForm } from '~~/composables/useAuthForm'

/**
 * 注册页。
 *
 * 注册成功后后端会**直接签发 token**（契约 2.2："免二次登录"），
 * BFF 已把 cookie 写好，useAuth.register 会把 userInfo 落进前端状态，
 * 所以这里不需要再跳一次登录页 —— 直接进个人中心。
 */
const { t } = useI18n()
const { register } = useAuth()
const {
  imageBase64,
  refreshCaptcha,
  countdown,
  canSendCode,
  requestEmailCode,
  validateEmail,
  validateNewPassword,
  scorePassword,
} = useAuthForm()
const localePath = useLocalePath()

const form = reactive({
  email: '',
  emailCode: '',
  captcha: '',
  password: '',
  confirmPassword: '',
})

const fieldErrors = reactive<Record<string, string>>({})
const loading = ref(false)
const sendingCode = ref(false)
const errorMsg = ref('')

const passwordStrength = computed(() => scorePassword(form.password))

const clearFieldErrors = () => {
  fieldErrors.email = ''
  fieldErrors.emailCode = ''
  fieldErrors.captcha = ''
  fieldErrors.password = ''
  fieldErrors.confirmPassword = ''
}

const validateEmailField = () => {
  fieldErrors.email = form.email ? validateEmail(form.email) : ''
}

/**
 * 密码字段失焦校验。
 *
 * 只在**已输入内容**时提示规则不满足 —— 用户刚点进空字段就跳红字是很烦人的
 * （ui-ux-pro-max 的 inline-validation 也建议 blur 校验而非 submit-only）。
 */
const validatePasswordField = () => {
  if (!form.password) {
    fieldErrors.password = ''
    return
  }
  const message = validateNewPassword(form.password, form.password)
  fieldErrors.password = message
}

const validateConfirmField = () => {
  if (!form.confirmPassword) {
    fieldErrors.confirmPassword = ''
    return
  }
  fieldErrors.confirmPassword = form.confirmPassword === form.password
    ? ''
    : t('auth.passwordMismatch')
}

const handleSendCode = async () => {
  fieldErrors.email = validateEmail(form.email)
  fieldErrors.captcha = form.captcha.trim() ? '' : t('auth.captchaRequired')
  if (fieldErrors.email || fieldErrors.captcha) return

  sendingCode.value = true
  try {
    // scene=register：后端会在此校验"该邮箱是否已被注册"，并把验证码存到 register 命名空间下
    const ok = await requestEmailCode(
      { email: form.email, scene: 'register', captcha: form.captcha },
      errorMsg,
    )
    form.captcha = ''
    refreshCaptcha()
    if (ok) {
      // 发送成功后清掉上一次的邮箱验证码，避免用户把旧码填进新流程里
      form.emailCode = ''
    }
  } finally {
    sendingCode.value = false
  }
}

const validateForm = (): boolean => {
  clearFieldErrors()
  fieldErrors.email = validateEmail(form.email)
  fieldErrors.emailCode = form.emailCode.trim() ? '' : t('auth.emailCodeRequired')
  fieldErrors.password = validateNewPassword(form.password, form.confirmPassword)
  // 一致性单独给 confirmPassword 字段级错误，用户才知道该改哪个框
  fieldErrors.confirmPassword = form.confirmPassword === form.password
    ? ''
    : t('auth.passwordMismatch')
  return !Object.values(fieldErrors).some(Boolean)
}

const handleRegister = async () => {
  if (loading.value) return
  errorMsg.value = ''

  if (!validateForm()) return

  loading.value = true
  try {
    await register({
      email: form.email.trim(),
      emailCode: form.emailCode.trim(),
      password: form.password,
      confirmPassword: form.confirmPassword,
    })
    await navigateTo(localePath('/profile'))
  } catch (e: any) {
    errorMsg.value = resolveAuthErrorMessage(e) || t('auth.registerFailed')
    // 验证码是一次性的：无论失败原因是什么（验证码错、已过期、邮箱已注册），
    // 这张图形验证码都已经用掉了，必须换新的
    form.captcha = ''
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

useSeoMeta({
  title: () => t('auth.register'),
  robots: 'noindex, nofollow',
})
</script>
