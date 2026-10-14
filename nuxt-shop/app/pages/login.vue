<template>
  <!--
    登录页（珊瑚玫红 × 奶油白）。
    刻意保持极简：不加页脚、不加"忘记密码/注册/第三方登录/记住我"这些
    后端并不支持的功能（login.* 语言包里虽然有这些 key，但模板里绝不渲染它们，
    渲染了就等于给用户一个假入口）。
  -->
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <section class="relative flex justify-center px-4 pb-20 pt-6 sm:px-6 sm:pt-10">
      <!-- 品牌柔光背景：纯 CSS 光晕，不引入任何图片资源，也不影响对比度 -->
      <div class="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
        <div class="brand-gradient-soft absolute inset-0"></div>
        <div class="absolute -left-24 top-0 h-72 w-72 rounded-full bg-[var(--color-brand-200)]/45 blur-3xl"></div>
        <div class="absolute -right-20 top-24 h-80 w-80 rounded-full bg-[var(--color-accent-200)]/40 blur-3xl"></div>
      </div>

      <!-- 白色卡片：圆角 + 柔和阴影（ui-card 已统一处理，页面里不再手写 border + shadow） -->
      <div class="ui-card relative w-full max-w-[420px] p-6 sm:p-9">
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

        <form class="mt-7 flex flex-col gap-4" @submit.prevent="handleLogin">
          <!-- 用户名 -->
          <div>
            <label for="login-username" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('login.username') }}
            </label>
            <div class="relative">
              <UIcon
                name="lucide:user"
                class="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-muted)]"
                aria-hidden="true"
              />
              <input
                id="login-username"
                v-model="form.username"
                name="username"
                type="text"
                autocomplete="username"
                required
                :placeholder="t('shop.usernamePlaceholder')"
                class="h-12 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white pl-10 pr-4 text-sm text-[var(--color-ink)] transition-colors placeholder:text-[var(--color-muted)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
              >
            </div>
          </div>

          <!-- 密码 -->
          <div>
            <label for="login-password" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('login.password') }}
            </label>
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
                type="password"
                autocomplete="current-password"
                required
                :placeholder="t('shop.passwordPlaceholder')"
                class="h-12 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white pl-10 pr-4 text-sm text-[var(--color-ink)] transition-colors placeholder:text-[var(--color-muted)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
              >
            </div>
          </div>

          <!-- 验证码：输入框 + 图片，点图片即可刷新（保留原有交互） -->
          <div>
            <label for="login-captcha" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('login.captcha') }}
            </label>
            <div class="flex items-stretch gap-3">
              <input
                id="login-captcha"
                v-model="form.captcha"
                name="captcha"
                type="text"
                autocomplete="off"
                required
                :placeholder="t('shop.captchaPlaceholder')"
                class="h-12 min-w-0 flex-1 rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors placeholder:text-[var(--color-muted)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
              >
              <button
                type="button"
                class="h-12 w-28 shrink-0 cursor-pointer overflow-hidden rounded-[var(--radius-input)] border border-[var(--color-line)] bg-[var(--color-brand-50)]"
                :aria-label="t('login.refreshCaptcha')"
                :title="t('login.refreshCaptcha')"
                @click="refreshCaptcha"
              >
                <img
                  v-if="imageBase64"
                  :src="imageBase64"
                  :alt="t('login.captcha')"
                  class="h-full w-full object-cover"
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
            <button
              type="button"
              class="mt-1.5 cursor-pointer text-xs text-[var(--color-muted)] transition-colors hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
              @click="refreshCaptcha"
            >
              {{ t('login.refreshCaptcha') }}
            </button>
          </div>

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
            {{ loading ? t('login.loading') : t('login.submit') }}
          </button>

          <!-- 失败提示：后端 statusMessage 优先（验证码错、密码错都能给出具体原因） -->
          <p
            v-if="errorMsg"
            role="alert"
            class="flex items-start gap-2 rounded-[var(--radius-input)] bg-[var(--color-error-50)] px-3.5 py-2.5 text-[13px] font-medium text-[var(--color-error-600)]"
          >
            <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{{ errorMsg }}</span>
          </p>
        </form>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useAuth } from '~~/composables/useAuth'
import { useCaptcha } from '~~/composables/useCaptcha'

/**
 * 登录页。
 *
 * 这里**只做展示层的重构**，登录/验证码的全部行为保持不变：
 *   useAuth.login 的入参、useCaptcha 的 checkKey / refreshCaptcha 调用时机
 *   都与重构前逐行一致（见下面 handleLogin / onMounted）。
 */
const { t } = useI18n()
const { login } = useAuth()
const { imageBase64, checkKey, refreshCaptcha } = useCaptcha()
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

const form = reactive({
  username: '',
  password: '',
  captcha: '',
})

const loading = ref(false)
const errorMsg = ref('')

// 页面打开时自动拉一次验证码
// onMounted 只在浏览器端执行，验证码这种东西没必要在 SSR 阶段就请求
// （对 SEO 完全没有意义，而且每次 SSR 渲染都请求一次验证码反而浪费后端资源）
onMounted(() => {
  refreshCaptcha()
})

async function handleLogin() {
  loading.value = true
  errorMsg.value = ''
  try {
    await login({
      username: form.username,
      password: form.password,
      captcha: form.captcha,
      checkKey: checkKey.value,
    })
    await navigateTo(redirectTarget.value)
  } catch (e: any) {
    // 后端 statusMessage 优先：验证码错误 / 密码错误这类信息只有后端知道
    errorMsg.value = e?.data?.statusMessage || e?.message || t('login.failed')
    // 登录失败常见原因是验证码错了或者过期了，直接刷新一张新的，
    // 免得用户重复用一张已经失效的验证码再试一次
    refreshCaptcha()
    form.captcha = ''
  } finally {
    loading.value = false
  }
}
</script>
