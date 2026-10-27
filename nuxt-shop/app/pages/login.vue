<template>
  <div class="min-h-dvh bg-[var(--color-cream)]">
    <!--
      登录页：左右分栏（品牌插画 + 表单）。
      左侧插画只做装饰；右侧保留邮箱密码 / 邮箱验证码 / Google 三种入口。

      与改造前的差异：
        · 布局从「导航下方的居中卡片」改成「全屏 1:1 分栏」，因此使用 auth-split 布局，
          不再渲染固定 Header（语言切换 / 购物车入口移到左栏底部与移动端品牌区）；
        · 新增 AuthAside 插画分栏，角色会跟随鼠标、眨眼、输入时互相看、密码可见时捂眼睛；
        · 原稿的 Remember for 30 days 没有实现 —— 本项目没有"记住登录"的后端契约，
          加一个不生效的勾选框比不加更糟。
    -->
    <section class="relative lg:grid lg:h-dvh lg:grid-cols-2 lg:overflow-hidden">
      <!-- 只有密码 Tab 才把密码状态传给插画；切到验证码 Tab 时角色应恢复自然姿态 -->
      <AuthAside
        :is-typing="isTyping"
        :password-visible="activeTab === 'password' && passwordVisible"
        :password-length="activeTab === 'password' ? form.password.length : 0"
      />

      <!-- 右侧表单：桌面端独立滚动，移动端整页滚动 -->
      <div class="flex min-h-dvh items-start justify-center px-4 py-8 sm:px-8 lg:h-dvh lg:min-h-0 lg:overflow-y-auto lg:bg-[var(--color-surface)] lg:px-12 lg:py-10">
        <div class="my-auto w-full max-w-[420px] rounded-[var(--radius-card)] bg-[var(--color-surface)] p-6 shadow-[var(--shadow-card)] sm:p-8 lg:rounded-none lg:bg-transparent lg:p-0 lg:shadow-none">
          <!-- 移动端品牌：桌面端由左栏承担，移动端左栏隐藏 -->
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
              {{ t('auth.welcomeBack') }}
            </h1>
            <p class="mt-2 text-sm text-[var(--color-muted)]">
              {{ t('shop.loginSubtitle') }}
            </p>
          </div>

          <!-- ===== Tab：药丸分段控件 ===== -->
          <div class="mt-7">
            <div
              class="grid grid-cols-2 gap-1 rounded-full bg-[var(--color-surface-secondary)] p-1"
              role="tablist"
              :aria-label="t('auth.tabsLabel')"
            >
              <button
                v-for="(tab, index) in tabs"
                :id="`tab-${tab.key}`"
                :key="tab.key"
                type="button"
                role="tab"
                class="flex cursor-pointer items-center justify-center gap-1.5 rounded-full px-3 py-2.5 text-[13px] font-bold transition-colors duration-200 motion-reduce:transition-none"
                :class="activeTab === tab.key
                  ? 'bg-white text-[var(--color-brand-700)] shadow-[var(--shadow-sm)]'
                  : 'text-[var(--color-muted)] hover:text-[var(--color-ink)]'"
                :aria-selected="activeTab === tab.key"
                :aria-controls="`panel-${tab.key}`"
                :tabindex="activeTab === tab.key ? 0 : -1"
                @click="selectTab(tab.key)"
                @keydown="onTabKeydown($event, index)"
              >
                <UIcon :name="tab.icon" class="h-4 w-4" aria-hidden="true" />
                {{ t(tab.label) }}
              </button>
            </div>
          </div>

          <!--
            ⚠️ 两个 Tab 面板共用一个 <form>，且都用 v-show 常驻 DOM。
            非当前 Tab 的输入框必须 disabled：disabled 的控件不参与原生约束校验，
            否则隐藏面板里的 required 空值会让 submit 事件永远不触发。
          -->
          <form
            class="mt-6 flex flex-col gap-4"
            @submit.prevent="handleSubmit"
            @focusin="isTyping = true"
            @focusout="onFormFocusOut"
          >
            <!-- ===== 面板一：邮箱 + 密码 ===== -->
            <div
              v-show="activeTab === 'password'"
              id="panel-password"
              role="tabpanel"
              aria-labelledby="tab-password"
              tabindex="0"
              class="flex flex-col gap-4 focus-visible:outline-none"
            >
              <div>
                <label for="login-email" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
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
                    id="login-email"
                    v-model="form.email"
                    name="email"
                    type="email"
                    inputmode="email"
                    autocomplete="email"
                    :placeholder="t('auth.emailPlaceholder')"
                    required
                    :disabled="loading || activeTab !== 'password'"
                    :aria-invalid="fieldErrors.email ? true : undefined"
                    :aria-describedby="fieldErrors.email ? 'login-email-error' : undefined"
                    class="h-12 w-full rounded-[var(--radius-input)] border bg-white pl-10 pr-4 text-sm text-[var(--color-ink)] transition-colors duration-200 placeholder:text-[var(--color-muted)] focus:outline-none focus:ring-2 disabled:cursor-not-allowed disabled:bg-[var(--color-line-soft)] motion-reduce:transition-none"
                    :class="fieldErrors.email
                      ? 'border-[var(--color-error-600)] focus:border-[var(--color-error-600)] focus:ring-[var(--color-error-50)]'
                      : 'border-[var(--color-line)] focus:border-[var(--color-brand-400)] focus:ring-[var(--color-brand-100)]'"
                    @blur="validateEmailField"
                  >
                </div>
                <p
                  v-if="fieldErrors.email"
                  id="login-email-error"
                  role="alert"
                  class="mt-1.5 flex items-start gap-1.5 text-[12px] font-medium text-[var(--color-error-600)]"
                >
                  <UIcon name="lucide:circle-alert" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  <span>{{ fieldErrors.email }}</span>
                </p>
              </div>

              <div>
                <div class="mb-1.5 flex items-baseline justify-between gap-3">
                  <label for="login-password" class="block text-[13px] font-bold text-[var(--color-ink-soft)]">
                    {{ t('auth.password') }}
                    <span class="text-[var(--color-brand-600)]" aria-hidden="true">*</span>
                  </label>
                  <NuxtLinkLocale
                    to="/forgot-password"
                    class="cursor-pointer text-xs font-bold text-[var(--color-brand-700)] underline-offset-2 transition-colors duration-200 hover:text-[var(--color-brand-800)] hover:underline motion-reduce:transition-none"
                  >
                    {{ t('auth.forgotPassword') }}
                  </NuxtLinkLocale>
                </div>
                <div class="relative">
                  <UIcon
                    name="lucide:key-round"
                    class="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-muted)]"
                    aria-hidden="true"
                  />
                  <input
                    id="login-password"
                    v-model="form.password"
                    name="password"
                    :type="passwordVisible ? 'text' : 'password'"
                    autocomplete="current-password"
                    :placeholder="t('auth.passwordPlaceholder')"
                    required
                    :disabled="loading || activeTab !== 'password'"
                    :aria-invalid="fieldErrors.password ? true : undefined"
                    :aria-describedby="fieldErrors.password ? 'login-password-error' : undefined"
                    class="h-12 w-full rounded-[var(--radius-input)] border bg-white pl-10 pr-12 text-sm text-[var(--color-ink)] transition-colors duration-200 placeholder:text-[var(--color-muted)] focus:outline-none focus:ring-2 disabled:cursor-not-allowed disabled:bg-[var(--color-line-soft)] motion-reduce:transition-none"
                    :class="fieldErrors.password
                      ? 'border-[var(--color-error-600)] focus:border-[var(--color-error-600)] focus:ring-[var(--color-error-50)]'
                      : 'border-[var(--color-line)] focus:border-[var(--color-brand-400)] focus:ring-[var(--color-brand-100)]'"
                  >
                  <button
                    type="button"
                    class="absolute right-1.5 top-1/2 flex h-9 w-9 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full text-[var(--color-muted)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-500)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
                    :disabled="loading"
                    :aria-label="passwordVisible ? t('auth.hidePassword') : t('auth.showPassword')"
                    :aria-pressed="passwordVisible"
                    :title="passwordVisible ? t('auth.hidePassword') : t('auth.showPassword')"
                    @click="passwordVisible = !passwordVisible"
                  >
                    <UIcon :name="passwordVisible ? 'lucide:eye-off' : 'lucide:eye'" class="h-4 w-4" aria-hidden="true" />
                  </button>
                </div>
                <p
                  v-if="fieldErrors.password"
                  id="login-password-error"
                  role="alert"
                  class="mt-1.5 flex items-start gap-1.5 text-[12px] font-medium text-[var(--color-error-600)]"
                >
                  <UIcon name="lucide:circle-alert" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  <span>{{ fieldErrors.password }}</span>
                </p>
              </div>
            </div>

            <!-- ===== 面板二：邮箱 + 邮箱验证码 ===== -->
            <div
              v-show="activeTab === 'emailCode'"
              id="panel-emailCode"
              role="tabpanel"
              aria-labelledby="tab-emailCode"
              tabindex="0"
              class="focus-visible:outline-none"
            >
              <AuthEmailCodeFields
                v-model:email="form.email"
                v-model:code="form.emailCode"
                :countdown="countdown"
                :sending="sendingCode"
                :send-disabled="!canSendCode(form.email)"
                :disabled="loading || activeTab !== 'emailCode'"
                :email-error="fieldErrors.email"
                :code-error="fieldErrors.emailCode"
                @email-blur="validateEmailField"
                @send-code="handleSendCode"
              />
              <p class="mt-3 flex items-start gap-1.5 text-xs text-[var(--color-muted)]">
                <UIcon name="lucide:info" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span>{{ t('auth.emailCodeLoginHint') }}</span>
              </p>
            </div>

            <!-- 表单级错误：字段级错误各自在字段下方，这里只放"不属于任何字段"的错误 -->
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
              {{ loading ? t('auth.loggingIn') : t('auth.login') }}
            </button>
          </form>

          <!-- ===== 分隔线 ===== -->
          <div class="my-6 flex items-center gap-3" aria-hidden="true">
            <span class="h-px flex-1 bg-[var(--color-line)]"></span>
            <span class="text-[11px] font-bold uppercase tracking-wider text-[var(--color-muted)]">
              {{ t('auth.orUseEmail') }}
            </span>
            <span class="h-px flex-1 bg-[var(--color-line)]"></span>
          </div>

          <AuthGoogleAuthButton
            :label="t('auth.googleLogin')"
            :loading="googleLoading"
            :disabled="googleDisabled"
            :disabled-reason="googleDisabledReason"
            @click="handleGoogleLogin"
          />

          <p class="mt-6 text-center text-sm text-[var(--color-muted)]">
            {{ t('login.noAccount') }}
            <NuxtLinkLocale
              to="/register"
              class="cursor-pointer font-bold text-[var(--color-brand-700)] underline-offset-2 transition-colors duration-200 hover:text-[var(--color-brand-800)] hover:underline motion-reduce:transition-none"
            >
              {{ t('login.register') }}
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
import { useCikianCaptcha } from '~~/composables/useCikianCaptcha'

/** 认证类页面：全屏分栏，不渲染全局 Header（见 app/layouts/auth-split.vue） */
definePageMeta({ layout: 'auth-split' })

/**
 * 登录页。
 *
 * 行为全部走 BFF（/api/auth/*），token 由 BFF 写进 httpOnly cookie，
 * 前端只拿到 userInfo —— 详见 composables/useAuth.ts 顶部注释。
 */
const { t, locale } = useI18n()
const { loginByPassword, loginByEmailCode, fetchGoogleAuthUrl } = useAuth()
const { countdown, canSendCode, requestEmailCode, validateEmail } = useAuthForm()
// CikVerify 弹窗：只能在点击回调（客户端）里 open()，这里先取到单例的 open 方法
const { open: openCaptcha } = useCikianCaptcha()
const route = useRoute()
const localePath = useLocalePath()

// 移动端品牌名：与 Header 同源，取不到店铺信息时回退到 i18n 品牌名
const { storeName } = useStoreInfo()
const brandName = computed(() => storeName.value || t('shop.brandName'))

/**
 * 登录后回到用户原本想去的页面。
 *
 * 只接受"站内绝对路径"（以单个 / 开头）：
 *   - 拒绝 '//evil.com' 这种协议相对地址，否则会变成开放重定向；
 *   - 拒绝外部 http(s) 链接，理由同上。
 */
const redirectTarget = computed(() => {
  const raw = route.query.redirect
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value === 'string' && /^\/(?!\/)/.test(value)) {
    return value
  }
  return localePath('/profile')
})

type TabKey = 'password' | 'emailCode'

const tabs = [
  { key: 'password' as const, label: 'auth.tabPassword', icon: 'lucide:key-round' },
  { key: 'emailCode' as const, label: 'auth.tabEmailCode', icon: 'lucide:mail' },
]

const activeTab = ref<TabKey>('password')
// 两个 Tab 各有一份错误状态：共用一个 errorMsg 会让用户切到另一个 Tab
// 还看到上一个 Tab 的报错，误以为当前表单有问题。
const errorMsg = ref('')

const form = reactive({
  email: '',
  password: '',
  emailCode: '',
})

const fieldErrors = reactive<Record<string, string>>({})
const loading = ref(false)
const sendingCode = ref(false)
const passwordVisible = ref(false)

/**
 * 表单内是否有焦点。
 *
 * 用 focusin/focusout 包住整个 form，而不是给每个输入框单独写 focus 事件：
 * 这样以后加字段也不会漏掉「角色互相看」的触发条件。
 */
const isTyping = ref(false)

const onFormFocusOut = (event: FocusEvent) => {
  const next = event.relatedTarget as Node | null
  const formElement = event.currentTarget as HTMLFormElement | null
  // 焦点仍在表单内部（例如从邮箱切到密码）时不要结束"输入中"
  if (!next || !formElement?.contains(next)) {
    isTyping.value = false
  }
}

const clearFieldErrors = () => {
  fieldErrors.email = ''
  fieldErrors.password = ''
  fieldErrors.emailCode = ''
}

const validateEmailField = () => {
  fieldErrors.email = form.email ? validateEmail(form.email) : ''
}

/**
 * Tab 切换：同时清掉错误与密码明文显示状态。
 *
 * 清明文是刻意的：切走再切回来时密码还显示着，是很常见的"肩上偷看"风险点。
 */
const selectTab = (key: TabKey) => {
  activeTab.value = key
  errorMsg.value = ''
  clearFieldErrors()
  passwordVisible.value = false
}

/**
 * Tab 键盘导航（WAI-ARIA tabs 模式）。
 */
const onTabKeydown = (event: KeyboardEvent, index: number) => {
  const keys = ['ArrowLeft', 'ArrowRight', 'Home', 'End']
  if (!keys.includes(event.key)) return
  event.preventDefault()

  let next = index
  if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length
  if (event.key === 'ArrowRight') next = (index + 1) % tabs.length
  if (event.key === 'Home') next = 0
  if (event.key === 'End') next = tabs.length - 1

  selectTab(tabs[next]!.key)
  nextTick(() => {
    document.getElementById(`tab-${tabs[next]!.key}`)?.focus()
  })
}

/**
 * 发送邮箱验证码（scene=login）。
 *
 * 流程：校验邮箱 → 打开 CikVerify 人机验证拿到一次性 ticket → 携带 ticket 发信。
 * ticket 是敏感的一次性凭证，禁止打印到 console。
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

    const ok = await requestEmailCode(
      { email: form.email, scene: 'login', ticket },
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

/**
 * 客户端预校验：只拦"注定失败"的情况（邮箱格式、空字段）。
 * 密码长度等规则不在这里拦：老账号可能不符合新规则。
 */
const validateForm = (): boolean => {
  clearFieldErrors()
  fieldErrors.email = validateEmail(form.email)

  if (activeTab.value === 'password') {
    if (!form.password) fieldErrors.password = t('auth.passwordRequired')
  } else if (!form.emailCode.trim()) {
    fieldErrors.emailCode = t('auth.emailCodeRequired')
  }

  return !fieldErrors.email && !fieldErrors.password && !fieldErrors.emailCode
}

const handleSubmit = async () => {
  if (loading.value) return
  errorMsg.value = ''

  if (!validateForm()) return

  loading.value = true
  try {
    if (activeTab.value === 'password') {
      await loginByPassword({ email: form.email.trim(), password: form.password })
    } else {
      await loginByEmailCode({ email: form.email.trim(), emailCode: form.emailCode.trim() })
    }
    await navigateTo(redirectTarget.value)
  } catch (e: any) {
    errorMsg.value = resolveAuthErrorMessage(e) || t('auth.loginFailed')
    // 登录提交不做人机验证（ticket 只在发送邮箱验证码时用）
  } finally {
    loading.value = false
  }
}

// ===== Google =====

const googleLoading = ref(false)
// Google 是否可用要在页面打开时问一次：未配置时按钮禁用并给提示。
// 三态：'checking'（SSR + 探活中）/ 'ready' / 'disabled'。
const googleState = ref<'checking' | 'ready' | 'disabled'>('checking')

const googleDisabled = computed(() => googleState.value !== 'ready')
const googleDisabledReason = computed(() =>
  googleState.value === 'disabled' ? t('auth.googleUnavailable') : '',
)

onMounted(async () => {
  try {
    // 传相对路径而不是绝对 URL：BFF 的 google-auth-url.get.ts 会做开放重定向防护
    await fetchGoogleAuthUrl(localePath('/auth/google/callback'), locale.value)
    googleState.value = 'ready'
  } catch {
    googleState.value = 'disabled'
  }
})

const handleGoogleLogin = async () => {
  if (googleDisabled.value || googleLoading.value) return
  googleLoading.value = true
  errorMsg.value = ''
  try {
    const data = await fetchGoogleAuthUrl(localePath('/auth/google/callback'), locale.value)
    if (!data?.authUrl) {
      throw new Error(t('auth.googleUnavailable'))
    }
    // Google 会回到固定回调路径，跳转前把"原本想去哪"暂存到 sessionStorage
    sessionStorage.setItem('auth-redirect-after-oauth', redirectTarget.value)
    window.location.href = data.authUrl
  } catch (e: any) {
    googleState.value = 'disabled'
    errorMsg.value = resolveAuthErrorMessage(e) || t('auth.googleUnavailable')
    googleLoading.value = false
  }
}

// 登录页不该被搜索引擎收录
useSeoMeta({
  title: () => t('login.title'),
  robots: 'noindex, nofollow',
})
</script>
