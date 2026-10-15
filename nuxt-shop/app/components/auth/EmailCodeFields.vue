<template>
  <div>
    <!--
      邮箱 + 邮箱验证码 + 图形验证码 三件套。
      注册 / 登录 / 忘记密码三个页面的这一段结构完全相同，
      只有"发送验证码时用哪个 scene"不同，所以抽成一个组件。
    -->
    <label :for="emailInputId" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
      {{ t('auth.email') }}
      <span class="text-[var(--color-brand-600)]" aria-hidden="true">*</span>
    </label>
    <div class="relative">
      <UIcon
        name="lucide:mail"
        class="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-muted)]"
        aria-hidden="true"
      />
      <input
        :id="emailInputId"
        v-model="email"
        name="email"
        type="email"
        inputmode="email"
        autocomplete="email"
        :placeholder="t('auth.emailPlaceholder')"
        required
        :disabled="disabled"
        :aria-invalid="emailError ? true : undefined"
        :aria-describedby="emailError ? emailErrorId : undefined"
        class="h-12 w-full rounded-[var(--radius-input)] border bg-white pl-10 pr-4 text-sm text-[var(--color-ink)] transition-colors duration-200 placeholder:text-[var(--color-muted)] focus:outline-none focus:ring-2 disabled:cursor-not-allowed disabled:bg-[var(--color-line-soft)] motion-reduce:transition-none"
        :class="emailError
          ? 'border-[var(--color-error-600)] focus:border-[var(--color-error-600)] focus:ring-[var(--color-error-50)]'
          : 'border-[var(--color-line)] focus:border-[var(--color-brand-400)] focus:ring-[var(--color-brand-100)]'"
        @blur="emit('email-blur')"
      >
    </div>
    <p
      v-if="emailError"
      :id="emailErrorId"
      role="alert"
      class="mt-1.5 flex items-start gap-1.5 text-[12px] font-medium text-[var(--color-error-600)]"
    >
      <UIcon name="lucide:circle-alert" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>{{ emailError }}</span>
    </p>

    <!-- ===== 图形验证码（契约决策 8：只在发送邮箱验证码时校验） ===== -->
    <div class="mt-4">
      <label :for="captchaInputId" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
        {{ t('login.captcha') }}
        <span class="text-[var(--color-brand-600)]" aria-hidden="true">*</span>
      </label>
      <div class="flex items-stretch gap-2 sm:gap-3">
        <input
          :id="captchaInputId"
          v-model="captcha"
          name="captcha"
          type="text"
          inputmode="text"
          autocomplete="off"
          :placeholder="t('shop.captchaPlaceholder')"
          required
          :disabled="disabled"
          :aria-invalid="captchaError ? true : undefined"
          :aria-describedby="captchaError ? captchaErrorId : undefined"
          class="h-12 min-w-0 flex-1 rounded-[var(--radius-input)] border bg-white px-4 text-sm tracking-widest text-[var(--color-ink)] transition-colors duration-200 placeholder:tracking-normal placeholder:text-[var(--color-muted)] focus:outline-none focus:ring-2 disabled:cursor-not-allowed disabled:bg-[var(--color-line-soft)] motion-reduce:transition-none"
          :class="captchaError
            ? 'border-[var(--color-error-600)] focus:border-[var(--color-error-600)] focus:ring-[var(--color-error-50)]'
            : 'border-[var(--color-line)] focus:border-[var(--color-brand-400)] focus:ring-[var(--color-brand-100)]'"
        >
        <button
          type="button"
          class="h-12 w-[104px] shrink-0 cursor-pointer overflow-hidden rounded-[var(--radius-input)] border border-[var(--color-line)] bg-[var(--color-brand-50)] transition-colors duration-200 hover:border-[var(--color-brand-300)] sm:w-28 motion-reduce:transition-none"
          :aria-label="t('login.refreshCaptcha')"
          :title="t('login.refreshCaptcha')"
          @click="emit('refresh-captcha')"
        >
          <img
            v-if="captchaImage"
            :src="captchaImage"
            :alt="t('login.captcha')"
            width="104"
            height="48"
            class="h-full w-full object-cover"
            loading="lazy"
          >
          <span v-else class="flex h-full w-full items-center justify-center text-[var(--color-muted)]">
            <UIcon
              name="lucide:refresh-cw"
              class="h-4 w-4 animate-spin motion-reduce:animate-none"
              aria-hidden="true"
            />
          </span>
        </button>
      </div>
      <p
        v-if="captchaError"
        :id="captchaErrorId"
        role="alert"
        class="mt-1.5 flex items-start gap-1.5 text-[12px] font-medium text-[var(--color-error-600)]"
      >
        <UIcon name="lucide:circle-alert" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span>{{ captchaError }}</span>
      </p>
    </div>

    <!-- ===== 邮箱验证码 + 发送按钮（60s 倒计时） ===== -->
    <div class="mt-4">
      <label :for="codeInputId" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
        {{ t('auth.emailCode') }}
        <span class="text-[var(--color-brand-600)]" aria-hidden="true">*</span>
      </label>
      <div class="flex items-stretch gap-2 sm:gap-3">
        <input
          :id="codeInputId"
          v-model="code"
          name="emailCode"
          type="text"
          inputmode="numeric"
          autocomplete="one-time-code"
          maxlength="6"
          :placeholder="t('auth.emailCodePlaceholder')"
          required
          :disabled="disabled"
          :aria-invalid="codeError ? true : undefined"
          :aria-describedby="codeError ? codeErrorId : undefined"
          class="h-12 min-w-0 flex-1 rounded-[var(--radius-input)] border bg-white px-4 text-sm tracking-[0.3em] text-[var(--color-ink)] transition-colors duration-200 placeholder:tracking-normal placeholder:text-[var(--color-muted)] focus:outline-none focus:ring-2 disabled:cursor-not-allowed disabled:bg-[var(--color-line-soft)] motion-reduce:transition-none"
          :class="codeError
            ? 'border-[var(--color-error-600)] focus:border-[var(--color-error-600)] focus:ring-[var(--color-error-50)]'
            : 'border-[var(--color-line)] focus:border-[var(--color-brand-400)] focus:ring-[var(--color-brand-100)]'"
        >
        <!--
          倒计时期间禁用并显示剩余秒数。
          用 tabular-nums 让数字宽度固定，秒数跳动时按钮宽度纹丝不动（避免布局抖动）。
        -->
        <button
          type="button"
          class="tabular h-12 w-[112px] shrink-0 cursor-pointer whitespace-nowrap rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-2 text-[13px] font-bold text-[var(--color-brand-600)] transition-colors duration-200 hover:border-[var(--color-brand-300)] hover:bg-[var(--color-brand-50)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-500)] disabled:cursor-not-allowed disabled:border-[var(--color-line)] disabled:bg-[var(--color-line-soft)] disabled:text-[var(--color-muted)] disabled:hover:bg-[var(--color-line-soft)] sm:w-32 motion-reduce:transition-none"
          :disabled="sendDisabled || sending"
          :aria-busy="sending"
          @click="emit('send-code')"
        >
          <span v-if="sending" class="flex items-center justify-center gap-1.5">
            <UIcon
              name="lucide:loader-circle"
              class="h-3.5 w-3.5 animate-spin motion-reduce:animate-none"
              aria-hidden="true"
            />
          </span>
          <span v-else-if="countdown > 0">{{ t('auth.resendIn', { seconds: countdown }) }}</span>
          <span v-else>{{ t('auth.sendCode') }}</span>
        </button>
      </div>
      <p
        v-if="codeError"
        :id="codeErrorId"
        role="alert"
        class="mt-1.5 flex items-start gap-1.5 text-[12px] font-medium text-[var(--color-error-600)]"
      >
        <UIcon name="lucide:circle-alert" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span>{{ codeError }}</span>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 「邮箱 + 图形验证码 + 邮箱验证码」复合字段组。
 *
 * 三个页面（注册 / 验证码登录 / 忘记密码）的这段 UI 一字不差，
 * 差别只有 scene。复制三份的话，改一次倒计时文案要改三处、必然漏一处。
 */
// 不接住返回值：模板里直接按名字使用 props（captchaImage / countdown / sending ...），
// 声明成 const props 却不读它在 lint 下是 unused-vars 报错。
withDefaults(defineProps<{
  captchaImage?: string
  countdown?: number
  sending?: boolean
  sendDisabled?: boolean
  disabled?: boolean
  emailError?: string
  captchaError?: string
  codeError?: string
}>(), {
  captchaImage: '',
  countdown: 0,
  sending: false,
  sendDisabled: false,
  disabled: false,
  emailError: '',
  captchaError: '',
  codeError: '',
})

const emit = defineEmits<{
  'email-blur': []
  'refresh-captcha': []
  'send-code': []
}>()

const email = defineModel<string>('email', { required: true })
const captcha = defineModel<string>('captcha', { required: true })
const code = defineModel<string>('code', { required: true })

const { t } = useI18n()

// useId 保证同一页面上多组字段的 id 不冲突（label 的 for 必须精确命中自己的 input）
const uid = useId()
const emailInputId = `auth-email-${uid}`
const captchaInputId = `auth-captcha-${uid}`
const codeInputId = `auth-code-${uid}`
const emailErrorId = `${emailInputId}-error`
const captchaErrorId = `${captchaInputId}-error`
const codeErrorId = `${codeInputId}-error`
</script>
