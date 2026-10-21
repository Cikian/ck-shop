<template>
  <div>
    <!--
      邮箱 + 邮箱验证码 复合字段组（注册 / 验证码登录 / 忘记密码三页共用）。

      旧版的图形验证码区块已整体删除：人机验证改为点击「获取验证码」后由
      @cikian/captcha-web 弹出 CikVerify 验证码，通过后拿到一次性 ticket，
      随 send-code 一起提交。这里只剩「邮箱 + 验证码 + 发送按钮」。
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

    <!-- 明确告知：点「获取验证码」不是直接发信，会先弹人机验证 -->
    <p class="mt-1.5 text-[11px] leading-relaxed text-[var(--color-muted)]">
      {{ t('auth.captchaHint') }}
    </p>

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
          pending（sending）期间同时显示 spinner，防止用户连点打出一串验证码请求。
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
 * 「邮箱 + 邮箱验证码」复合字段组。
 *
 * 三个页面（注册 / 验证码登录 / 忘记密码）的这段 UI 一字不差，
 * 差别只有 scene。复制三份的话，改一次倒计时文案要改三处、必然漏一处。
 *
 * 人机验证（CikVerify）不在这里：它是点击「获取验证码」时由页面调用
 * useCikianCaptcha().open() 弹出的全屏遮罩，和这段内联表单 UI 无关。
 */
withDefaults(defineProps<{
  countdown?: number
  sending?: boolean
  sendDisabled?: boolean
  disabled?: boolean
  emailError?: string
  codeError?: string
}>(), {
  countdown: 0,
  sending: false,
  sendDisabled: false,
  disabled: false,
  emailError: '',
  codeError: '',
})

const emit = defineEmits<{
  'email-blur': []
  'send-code': []
}>()

const email = defineModel<string>('email', { required: true })
const code = defineModel<string>('code', { required: true })

const { t } = useI18n()

// useId 保证同一页面上多组字段的 id 不冲突（label 的 for 必须精确命中自己的 input）
const uid = useId()
const emailInputId = `auth-email-${uid}`
const codeInputId = `auth-code-${uid}`
const emailErrorId = `${emailInputId}-error`
const codeErrorId = `${codeInputId}-error`
</script>
