<template>
  <!-- 忘记密码页：邮箱 + 邮箱验证码 + 新密码 + 确认新密码（契约 2.6 / 4.2） -->
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <section class="relative flex justify-center px-4 pb-20 pt-6 sm:px-6 sm:pt-10">
      <div class="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
        <div class="brand-gradient-soft absolute inset-0"></div>
        <div class="absolute -left-24 top-0 h-72 w-72 rounded-full bg-[var(--color-brand-200)]/45 blur-3xl"></div>
        <div class="absolute -right-20 top-24 h-80 w-80 rounded-full bg-[var(--color-accent-200)]/40 blur-3xl"></div>
      </div>

      <div class="relative w-full max-w-[440px]">
        <div class="ui-card p-6 sm:p-9">
          <!-- 成功后整卡切成结果态：不再渲染表单，避免用户在已改成功的页面上
               继续点"重置密码"（第二次必然失败，因为验证码已被消费） -->
          <template v-if="succeeded">
            <div class="flex flex-col items-center py-4 text-center">
              <span class="flex h-14 w-14 items-center justify-center rounded-full bg-[var(--color-success-50)] text-[var(--color-success-600)]">
                <UIcon name="lucide:circle-check" class="h-7 w-7" aria-hidden="true" />
              </span>
              <h1 class="mt-4 text-2xl font-black tracking-tight text-[var(--color-ink)]">
                {{ t('auth.resetSuccess') }}
              </h1>
              <p class="mt-1.5 text-sm text-[var(--color-muted)]">
                {{ t('auth.resetSuccessDesc') }}
              </p>
              <NuxtLinkLocale
                to="/login"
                class="ui-pill ui-pill-brand mt-7 h-12 w-full text-[15px]"
              >
                <UIcon name="lucide:log-in" class="h-4 w-4" aria-hidden="true" />
                {{ t('auth.goLogin') }}
              </NuxtLinkLocale>
            </div>
          </template>

          <template v-else>
            <div class="flex flex-col items-center text-center">
              <span class="brand-gradient flex h-14 w-14 items-center justify-center rounded-full text-white shadow-[var(--shadow-pop)]">
                <UIcon name="lucide:key-round" class="h-6 w-6" aria-hidden="true" />
              </span>
              <h1 class="mt-4 text-2xl font-black tracking-tight text-[var(--color-ink)]">
                {{ t('auth.forgotPassword') }}
              </h1>
              <p class="mt-1.5 text-sm text-[var(--color-muted)]">
                {{ t('auth.forgotPasswordSubtitle') }}
              </p>
            </div>

            <form class="mt-7 flex flex-col gap-4" @submit.prevent="handleReset">
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
                v-model="form.newPassword"
                :label="t('auth.newPassword')"
                :placeholder="t('auth.passwordPlaceholder')"
                autocomplete="new-password"
                show-strength
                :strength="passwordStrength"
                :error="fieldErrors.newPassword"
                :disabled="loading"
                @blur="validateNewPasswordField"
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
                {{ loading ? t('auth.resetting') : t('auth.resetPassword') }}
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
              <NuxtLinkLocale
                to="/login"
                class="cursor-pointer font-bold text-[var(--color-brand-600)] underline-offset-2 transition-colors duration-200 hover:text-[var(--color-brand-700)] hover:underline motion-reduce:transition-none"
              >
                {{ t('auth.backToLogin') }}
              </NuxtLinkLocale>
            </p>
          </template>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { resolveAuthErrorMessage, useAuth } from '~~/composables/useAuth'
import { useAuthForm } from '~~/composables/useAuthForm'

/**
 * 忘记密码 / 重置密码。
 *
 * ⚠️ 成功后**不自动登录**：契约 2.6 明确要"清除该用户所有登录态"
 * （清 Redis 里该用户的 token / shiro 缓存），所以这里只回登录页引导用户重新登录。
 */
const { t } = useI18n()
const { resetPassword } = useAuth()
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

const form = reactive({
  email: '',
  emailCode: '',
  captcha: '',
  newPassword: '',
  confirmPassword: '',
})

const fieldErrors = reactive<Record<string, string>>({})
const loading = ref(false)
const sendingCode = ref(false)
const errorMsg = ref('')
const succeeded = ref(false)

const passwordStrength = computed(() => scorePassword(form.newPassword))

const clearFieldErrors = () => {
  fieldErrors.email = ''
  fieldErrors.emailCode = ''
  fieldErrors.captcha = ''
  fieldErrors.newPassword = ''
  fieldErrors.confirmPassword = ''
}

const validateEmailField = () => {
  fieldErrors.email = form.email ? validateEmail(form.email) : ''
}

const validateNewPasswordField = () => {
  fieldErrors.newPassword = form.newPassword
    ? validateNewPassword(form.newPassword, form.newPassword)
    : ''
}

const validateConfirmField = () => {
  if (!form.confirmPassword) {
    fieldErrors.confirmPassword = ''
    return
  }
  fieldErrors.confirmPassword = form.confirmPassword === form.newPassword
    ? ''
    : t('auth.passwordMismatch')
}

const handleSendCode = async () => {
  fieldErrors.email = validateEmail(form.email)
  fieldErrors.captcha = form.captcha.trim() ? '' : t('auth.captchaRequired')
  if (fieldErrors.email || fieldErrors.captcha) return

  sendingCode.value = true
  try {
    // scene=reset：后端在此校验"该邮箱是否已注册"，并把验证码存到 reset 命名空间下。
    // 与 login 场景分开存是关键 —— 否则注册流程发的码能拿来重置密码。
    const ok = await requestEmailCode(
      { email: form.email, scene: 'reset', captcha: form.captcha },
      errorMsg,
    )
    form.captcha = ''
    refreshCaptcha()
    if (ok) {
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
  fieldErrors.newPassword = validateNewPassword(form.newPassword, form.confirmPassword)
  fieldErrors.confirmPassword = form.confirmPassword === form.newPassword
    ? ''
    : t('auth.passwordMismatch')
  return !Object.values(fieldErrors).some(Boolean)
}

const handleReset = async () => {
  if (loading.value) return
  errorMsg.value = ''

  if (!validateForm()) return

  loading.value = true
  try {
    await resetPassword({
      email: form.email.trim(),
      emailCode: form.emailCode.trim(),
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword,
    })
    succeeded.value = true
  } catch (e: any) {
    errorMsg.value = resolveAuthErrorMessage(e) || t('auth.resetFailed')
    form.captcha = ''
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

useSeoMeta({
  title: () => t('auth.forgotPassword'),
  robots: 'noindex, nofollow',
})
</script>
