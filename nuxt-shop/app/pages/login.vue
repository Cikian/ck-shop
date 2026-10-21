<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <!--
      登录页（珊瑚玫红 × 奶油白）。
      三个入口：邮箱密码 / 邮箱验证码 / Google。
      刻意不再渲染用户名输入框 —— 店铺端已废弃用户名登录（契约 4.1）。

      注意：注释必须放在单一根元素内部。放在 <template> 顶层会让 Vue 把
      "注释 + div" 当成两个根节点，dev 下报 "does not have a single root node"，
      路由过渡（pageTransition out-in）也会因此拿不到根节点。
    -->
    <section class="relative flex justify-center px-4 pb-20 pt-6 sm:px-6 sm:pt-10">
      <!-- 品牌柔光背景：纯 CSS 光晕，不引入任何图片资源，也不影响对比度 -->
      <div class="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
        <div class="brand-gradient-soft absolute inset-0"></div>
        <div class="absolute -left-24 top-0 h-72 w-72 rounded-full bg-[var(--color-brand-200)]/45 blur-3xl"></div>
        <div class="absolute -right-20 top-24 h-80 w-80 rounded-full bg-[var(--color-accent-200)]/40 blur-3xl"></div>
      </div>

      <div class="relative w-full max-w-[440px]">
        <!-- 白色卡片：圆角 + 柔和阴影（ui-card 已统一处理，页面里不再手写 border + shadow） -->
        <div class="ui-card p-6 sm:p-9">
          <div class="flex flex-col items-center text-center">
            <span class="brand-gradient flex h-14 w-14 items-center justify-center rounded-full text-white shadow-[var(--shadow-pop)]">
              <UIcon name="lucide:lock" class="h-6 w-6" aria-hidden="true" />
            </span>
            <h1 class="mt-4 text-2xl font-black tracking-tight text-[var(--color-ink)]">
              {{ t('login.title') }}
            </h1>
            <p class="mt-1.5 text-sm text-[var(--color-muted)]">
              {{ t('shop.loginSubtitle') }}
            </p>
          </div>

          <!-- ===== Tab：下划线滑动指示器 ===== -->
          <div class="relative mt-6">
            <!--
              用 grid-cols-2 让两个按钮各占一半宽度，指示器宽度才能用 50% 表达，
              从而做出"滑块"效果而不是"宽度跳变"。
            -->
            <div
              class="grid grid-cols-2 border-b border-[var(--color-line)]"
              role="tablist"
              :aria-label="t('auth.tabsLabel')"
            >
              <button
                v-for="(tab, index) in tabs"
                :id="`tab-${tab.key}`"
                :key="tab.key"
                type="button"
                role="tab"
                class="relative flex cursor-pointer items-center justify-center gap-1.5 pb-3 pt-1 text-sm font-bold transition-colors duration-200 motion-reduce:transition-none"
                :class="activeTab === tab.key ? 'text-[var(--color-brand-600)]' : 'text-[var(--color-muted)] hover:text-[var(--color-ink-soft)]'"
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

            <!--
              滑动指示器：用一个绝对定位的 2px 横条 + translateX 实现。
              translateX 不改变文档流，所以不会引起布局位移；
              motion-reduce 下由全局 CSS 把过渡时长压到 0.01ms（tailwind.css 已有）。
            -->
            <span
              class="pointer-events-none absolute bottom-0 left-0 h-0.5 w-1/2 bg-[var(--color-brand-500)] transition-transform duration-300 ease-out motion-reduce:transition-none"
              :style="{ transform: activeTab === 'password' ? 'translateX(0)' : 'translateX(100%)' }"
              aria-hidden="true"
            />
          </div>

          <!--
            ⚠️ 两个 Tab 面板共用一个 <form>，且都用 v-show 常驻 DOM。
            浏览器原生约束校验（required）会检查**隐藏面板里**的输入框：
            密码 Tab 下隐藏的 emailCode 是空值 → 点「登录」时 submit 事件
            根本不会触发（控制台报 "An invalid form control ... is not focusable"），
            handleSubmit 一次都执行不到，表现为「登录按钮点了没反应」。
            所以非当前 Tab 的输入框必须 disabled：disabled 的控件不参与约束校验，
            同时也不会被键盘 Tab 到（隐藏面板本来就不该能聚焦）。
          -->
          <form class="mt-6 flex flex-col gap-4" @submit.prevent="handleSubmit">
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
                    class="cursor-pointer text-xs font-bold text-[var(--color-brand-600)] underline-offset-2 transition-colors duration-200 hover:text-[var(--color-brand-700)] hover:underline motion-reduce:transition-none"
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
                    class="absolute right-1.5 top-1/2 flex h-9 w-9 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full text-[var(--color-muted)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-500)] motion-reduce:transition-none"
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
              <!-- 新用户自动注册：契约 2.4 明确"邮箱未注册则自动创建账号并登录"，
                   这句话必须让用户看见，否则用户会以为验证码登录只对老账号有效 -->
              <p class="mt-3 flex items-start gap-1.5 text-xs text-[var(--color-muted)]">
                <UIcon name="lucide:info" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span>{{ t('auth.emailCodeLoginHint') }}</span>
              </p>
            </div>

            <!-- 提交按钮：loading 时转圈 + 禁用，防重复提交 -->
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

            <!-- 表单级错误：字段级错误各自在字段下方，这里只放"不属于任何字段"的错误 -->
            <p
              v-if="errorMsg"
              role="alert"
              class="flex items-start gap-2 rounded-[var(--radius-input)] bg-[var(--color-error-50)] px-3.5 py-2.5 text-[13px] font-medium text-[var(--color-error-600)]"
            >
              <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              <span>{{ errorMsg }}</span>
            </p>
          </form>

          <!-- ===== 分隔线 ===== -->
          <div class="my-6 flex items-center gap-3" aria-hidden="true">
            <span class="h-px flex-1 bg-[var(--color-line)]"></span>
            <span class="text-[11px] font-bold uppercase tracking-wider text-[var(--color-muted)]">
              {{ t('auth.orUseEmail') }}
            </span>
            <span class="h-px flex-1 bg-[var(--color-line)]"></span>
          </div>

          <!-- ===== Google ===== -->
          <!--
            disabled 在"探活中"也为 true（防止用户在结果回来前点到按钮），
            但 disabledReason 此时为空 —— 探活中不该断言"本站未开通"，
            那句话只在校验确实失败（googleState === 'disabled'）时才说。
          -->
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
              class="cursor-pointer font-bold text-[var(--color-brand-600)] underline-offset-2 transition-colors duration-200 hover:text-[var(--color-brand-700)] hover:underline motion-reduce:transition-none"
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

/** 认证类页面统一使用无页脚的 auth 布局（见 app/layouts/auth.vue） */
definePageMeta({ layout: 'auth' })

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

/**
 * 登录后回到用户原本想去的页面。
 *
 * 只接受"站内绝对路径"（以单个 / 开头）：
 *   - 拒绝 '//evil.com' 这种协议相对地址，否则会变成开放重定向，
 *     攻击者可以把 /login?redirect=//evil.com 发给用户，登录后被带到钓鱼站；
 *   - 拒绝外部 http(s) 链接，理由同上。
 * 校验不通过就退回个人中心，不影响正常登录流程。
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
 * 清明文是刻意的：切走再切回来时密码还显示着，是很常见的"肩上偷看"风险点
 * （用户在咖啡厅切 Tab 去复制验证码，屏幕上的密码就一直亮着）。
 */
const selectTab = (key: TabKey) => {
  activeTab.value = key
  errorMsg.value = ''
  clearFieldErrors()
  passwordVisible.value = false
}

/**
 * Tab 键盘导航（WAI-ARIA tabs 模式）。
 *
 * 只有 Tab 键能进出的"选项卡"是不完整的：ARIA 规范要求
 * ←/→ 在选项卡之间移动焦点并激活。缺了它，键盘用户必须
 * 先 Tab 出去再 Shift+Tab 回来才能换 Tab，体验明显割裂。
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
  // 焦点也要跟着走，否则用户按了方向键却"看不见焦点在哪"
  nextTick(() => {
    document.getElementById(`tab-${tabs[next]!.key}`)?.focus()
  })
}

/**
 * 发送邮箱验证码（scene=login）。
 *
 * 流程：校验邮箱 → 打开 CikVerify 人机验证拿到一次性 ticket → 携带 ticket 发信。
 *
 * 错误处理约定：
 *   · 用户主动关闭弹窗（reason === 'closed'）→ 静默返回，不制造"报错"噪音；
 *   · CDN/网络/超时/后端未签发 ticket 等 → 写入 errorMsg（"安全验证加载失败，请重试"）；
 *   · 发信失败沿用后端 msg（如"该邮箱尚未注册" / "发送过于频繁"）。
 * 注意：ticket 是敏感的一次性凭证，禁止打印到 console。
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
    // 发信成功后清空验证码输入框：刚发出的那封才是有效验证码。
    // 失败时保留用户已输入内容，避免"手滑输错一个字就全没了"。
    if (ok) {
      form.emailCode = ''
      fieldErrors.emailCode = ''
    }
  } finally {
    sendingCode.value = false
  }
}

/**
 * 客户端预校验。
 *
 * 只拦"注定失败"的情况（邮箱格式、空字段）。
 * 密码长度等规则**不在这里拦**：登录的密码是已有账号的密码，
 * 老账号可能不符合新规则，前端按新规则拦会让老用户永远登不进来。
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
    // 注意：登录提交**不做人机验证**（ticket 只在发送邮箱验证码时用），
    // 这里不要打开验证码弹窗，否则用户会以为"提交还得再验证一次"。
  } finally {
    loading.value = false
  }
}

// ===== Google =====

const googleLoading = ref(false)
// Google 是否可用要在**页面打开时**就问一次（而不是等用户点了才报错）：
// 契约 2.5 要求未配置时按钮禁用并给提示。点了才知道不可用，等于给用户一个假入口。
//
// 三态（不能只用 boolean，见下）：
//   'checking' —— SSR 首屏 + 客户端探活进行中
//   'ready'    —— 探活成功，可点
//   'disabled' —— 探活失败（未配置 / 网络异常），显示原因
//
// ⚠️ 为什么必须有 'checking'：探活只能在 onMounted（客户端）跑，SSR 阶段拿不到结果。
//    若初始值直接是「不可用」，SSR 会把"本站暂未开通 Google 登录"渲染进 HTML，
//    用户会先看到一句**错误的**提示再被 hydrate 纠正 —— 探活失败时更是永远显示错误提示，
//    明明功能是好的却告诉用户没开通。
const googleState = ref<'checking' | 'ready' | 'disabled'>('checking')

/** 按钮是否禁用：探活中与失败都不可点，只有 ready 可点 */
const googleDisabled = computed(() => googleState.value !== 'ready')

/** 禁用原因文案：探活中不显示任何提示（避免误导），只有确认不可用才说 */
const googleDisabledReason = computed(() =>
  googleState.value === 'disabled' ? t('auth.googleUnavailable') : '',
)

onMounted(async () => {
  try {
    // ⚠️ 传**相对路径**而不是绝对 URL：BFF 的 google-auth-url.get.ts 会做开放重定向防护
    // （toSafeRelativePath），只接受以单个 `/` 开头的站内路径，且 origin 由服务端自己推导。
    // 传 `https://host/...` 会被它判定为绝对地址 → 丢弃并回退到默认回调路径。
    // lang 必须一起传：BFF 用它补语言前缀，漏传会回退 'en' 导致 redirect_uri 与授权阶段不一致。
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
    // Google 会把用户送回**固定回调路径**（BFF 拼的，不带任何 query），
    // 所以在整页跳转前把"原本想去哪"暂存到 sessionStorage，回调页再取出来。
    // 不这么做的话，从 /cart 点 Google 登录会被丢到 /profile，用户丢失上下文。
    // 用 sessionStorage 而不是 localStorage：这是单次跳转的临时状态，
    // 关掉标签页就该消失（localStorage 会一直留着，下次误用上一次的目标页）。
    sessionStorage.setItem('auth-redirect-after-oauth', redirectTarget.value)
    // 交给 Google：整页跳转（不是 window.open）—— 移动端弹窗拦截率高，
    // 而且整页跳转能保证用户按"返回"时回到登录页而不是空白弹窗
    window.location.href = data.authUrl
  } catch (e: any) {
    // 走到这里说明 auth-url 拿不到（未配置 / 网络异常），把按钮切成不可用，
    // 避免用户反复点一个注定失败的按钮
    googleState.value = 'disabled'
    errorMsg.value = resolveAuthErrorMessage(e) || t('auth.googleUnavailable')
    googleLoading.value = false
  }
}

// 页面级 SEO：登录页不该被搜索引擎收录（没有内容价值，且可能被当作低质页面）
useSeoMeta({
  title: () => t('login.title'),
  robots: 'noindex, nofollow',
})
</script>
