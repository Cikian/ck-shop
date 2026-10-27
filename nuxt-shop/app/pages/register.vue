<template>
  <div class="min-h-dvh bg-[var(--color-cream)]">
    <!--
      注册页：与登录页共用同一套品牌分栏 / 插画。
      表单保留「邮箱 + 邮箱验证码 + 密码 + 确认密码 + 密码强度」，
      不为了视觉一致而删掉任何校验或人机验证入口。
    -->
    <section class="relative lg:grid lg:h-dvh lg:grid-cols-2 lg:overflow-hidden">
      <AuthAside
        :is-typing="isTyping"
        :password-visible="asidePasswordVisible"
        :password-length="asidePasswordLength"
      />

      <div class="flex min-h-dvh items-start justify-center px-4 py-8 sm:px-8 lg:h-dvh lg:min-h-0 lg:overflow-y-auto lg:bg-[var(--color-surface)] lg:px-12 lg:py-10">
        <div class="my-auto w-full max-w-[420px] rounded-[var(--radius-card)] bg-[var(--color-surface)] p-6 shadow-[var(--shadow-card)] sm:p-8 lg:rounded-none lg:bg-transparent lg:p-0 lg:shadow-none">
          <!-- 移动端品牌 -->
          <div class="mb-8 flex items-center justify-center lg:hidden">
            <img
              src="/logo/lautira-lockup.svg"
              :alt="brandName"
              width="1006"
              height="312"
              class="h-9 w-auto"
            >
          </div>

          <div class="text-center">
            <h1 class="text-[28px] font-black leading-tight tracking-tight text-[var(--color-ink)] sm:text-3xl">
              {{ t('auth.register') }}
            </h1>
            <p class="mt-2 text-sm text-[var(--color-muted)]">
              {{ t('auth.registerSubtitle') }}
            </p>
          </div>

          <form
            class="mt-7 flex flex-col gap-4"
            @submit.prevent="handleRegister"
            @focusin="isTyping = true"
            @focusout="onFormFocusOut"
          >
            <AuthEmailCodeFields
              v-model:email="form.email"
              v-model:code="form.emailCode"
              :countdown="countdown"
              :sending="sendingCode"
              :send-disabled="!canSendCode(form.email)"
              :disabled="loading"
              :email-error="fieldErrors.email"
              :code-error="fieldErrors.emailCode"
              @email-blur="validateEmailField"
              @send-code="handleSendCode"
            />

            <AuthPasswordField
              v-model="form.password"
              v-model:revealed="passwordVisible"
              name="password"
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
              v-model:revealed="confirmPasswordVisible"
              name="confirmPassword"
              :label="t('auth.confirmPassword')"
              :placeholder="t('auth.confirmPasswordPlaceholder')"
              autocomplete="new-password"
              :error="fieldErrors.confirmPassword"
              :disabled="loading"
              @blur="validateConfirmField"
            />

            <!--
              注册说明：本系统**没有用户名输入**，登录标识就是邮箱。
              不要在这里再提"系统会生成用户名" —— 全站已不再展示 u_xxxxxxxx，
              提它只会让用户去找一个根本不存在的"账号名"。
            -->
            <p class="flex items-start gap-1.5 rounded-[var(--radius-input)] bg-[var(--color-brand-50)] px-3.5 py-2.5 text-xs text-[var(--color-ink-soft)]">
              <UIcon name="lucide:info" class="mt-0.5 h-3.5 w-3.5 shrink-0 text-[var(--color-brand-500)]" aria-hidden="true" />
              <span>{{ t('auth.emailOnlyHint') }}</span>
            </p>

            <p
              v-if="errorMsg"
              role="alert"
              class="flex items-start gap-2 rounded-[var(--radius-input)] bg-[var(--color-error-50)] px-3.5 py-2.5 text-[13px] font-medium text-[var(--color-error-600)]"
            >
              <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              <span>{{ errorMsg }}</span>
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
          </form>

          <p class="mt-6 text-center text-sm text-[var(--color-muted)]">
            {{ t('auth.haveAccount') }}
            <NuxtLinkLocale
              to="/login"
              class="cursor-pointer font-bold text-[var(--color-brand-700)] underline-offset-2 transition-colors duration-200 hover:text-[var(--color-brand-800)] hover:underline motion-reduce:transition-none"
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
// 认证类页面：全屏分栏，不渲染全局 Header（与 login.vue 一致）
definePageMeta({ layout: 'auth-split' })

import { resolveAuthErrorMessage, useAuth } from '~~/composables/useAuth'
import { useAuthForm } from '~~/composables/useAuthForm'
import { useCikianCaptcha } from '~~/composables/useCikianCaptcha'

/**
 * 注册页。
 *
 * 注册成功后后端会直接签发 token（契约 2.2："免二次登录"），
 * BFF 已把 cookie 写好，useAuth.register 会把 userInfo 落进前端状态，
 * 所以这里不需要再跳一次登录页 —— 直接进个人中心。
 */
const { t } = useI18n()
const { register } = useAuth()
const {
  countdown,
  canSendCode,
  requestEmailCode,
  validateEmail,
  validateNewPassword,
  scorePassword,
} = useAuthForm()
// CikVerify 弹窗：只能在点击回调（客户端）里 open()
const { open: openCaptcha } = useCikianCaptcha()
const localePath = useLocalePath()

// 移动端品牌名：与 Header 同源
const { storeName } = useStoreInfo()
const brandName = computed(() => storeName.value || t('shop.brandName'))

const form = reactive({
  email: '',
  emailCode: '',
  password: '',
  confirmPassword: '',
})

const fieldErrors = reactive<Record<string, string>>({})
const loading = ref(false)
const sendingCode = ref(false)
const errorMsg = ref('')

/** 密码明文状态：既控制输入框，也驱动左侧插画「捂眼睛」 */
const passwordVisible = ref(false)
const confirmPasswordVisible = ref(false)

/**
 * 两个密码框任意一个处于明文且有内容，插画就捂眼睛。
 * 这样用户点「确认密码」的眼睛按钮时，角色也有反馈。
 */
const asidePasswordVisible = computed(() => passwordVisible.value || confirmPasswordVisible.value)
const asidePasswordLength = computed(() => {
  // 两个框都可见时优先用有内容的那个，避免"确认密码有内容但角色不捂眼"
  if (passwordVisible.value && form.password.length > 0) return form.password.length
  if (confirmPasswordVisible.value) return form.confirmPassword.length
  return 0
})

/**
 * 表单内是否有焦点。
 * 用 focusin/focusout 包住整个 form，新增字段也不会漏掉「角色互相看」。
 */
const isTyping = ref(false)

const onFormFocusOut = (event: FocusEvent) => {
  const next = event.relatedTarget as Node | null
  const formElement = event.currentTarget as HTMLFormElement | null
  if (!next || !formElement?.contains(next)) {
    isTyping.value = false
  }
}

const passwordStrength = computed(() => scorePassword(form.password))

const clearFieldErrors = () => {
  fieldErrors.email = ''
  fieldErrors.emailCode = ''
  fieldErrors.password = ''
  fieldErrors.confirmPassword = ''
}

const validateEmailField = () => {
  fieldErrors.email = form.email ? validateEmail(form.email) : ''
}

/**
 * 密码字段失焦校验：只在已输入内容时提示规则不满足。
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

/**
 * 发送邮箱验证码（scene=register）。
 *
 * 流程：校验邮箱 → 打开 CikVerify 人机验证拿到一次性 ticket → 携带 ticket 发信。
 * 用户关闭弹窗（reason==='closed'）静默返回；加载/超时等失败写 errorMsg。
 */
const handleSendCode = async () => {
  fieldErrors.email = validateEmail(form.email)
  if (fieldErrors.email) return

  errorMsg.value = ''
  sendingCode.value = true
  try {
    let ticket: string
    try {
      ticket = await openCaptcha()
    } catch (error: any) {
      if (error?.reason === 'closed') return
      errorMsg.value = t('auth.captchaOpenFailed')
      return
    }

    // scene=register：后端会在此校验"该邮箱是否已被注册"
    const ok = await requestEmailCode(
      { email: form.email, scene: 'register', ticket },
      errorMsg,
    )
    if (ok) {
      form.emailCode = ''
      fieldErrors.emailCode = ''
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
    // 注册提交不做人机验证（ticket 只在发送邮箱验证码时用）
  } finally {
    loading.value = false
  }
}

useSeoMeta({
  title: () => t('auth.register'),
  robots: 'noindex, nofollow',
})
</script>
