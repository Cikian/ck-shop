<template>
  <div class="min-h-[calc(100vh-var(--nav-total-height))] bg-[var(--color-cream)] pt-navbar">
    <!--
      Google OAuth 回调落地页。
      这个页面**没有表单**：用户是被 Google 整页跳转过来的，正常情况下只会看到 1-2 秒的
      过渡态。所以它的核心是"状态要诚实"——加载中 / 成功 / 失败三种状态各有明确观感，
      绝不允许出现"转圈转到天荒地老"或白屏。
    -->
    <section class="relative flex justify-center px-4 pb-20 pt-6 sm:px-6 sm:pt-10">
      <div class="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
        <div class="brand-gradient-soft absolute inset-0"></div>
        <div class="absolute -left-24 top-0 h-72 w-72 rounded-full bg-[var(--color-brand-200)]/45 blur-3xl"></div>
      </div>

      <div class="relative w-full max-w-[440px]">
        <!-- aria-live=polite：状态从"正在登录"变成"登录失败"时，
             屏幕阅读器要能把新状态念出来，否则用户以为还在加载 -->
        <div class="ui-card p-8 text-center sm:p-10" aria-live="polite">
          <!-- ===== 加载中 ===== -->
          <template v-if="status === 'pending'">
            <span class="brand-gradient mx-auto flex h-14 w-14 items-center justify-center rounded-full text-white shadow-[var(--shadow-pop)]">
              <UIcon
                name="lucide:loader-circle"
                class="h-6 w-6 animate-spin motion-reduce:animate-none"
                aria-hidden="true"
              />
            </span>
            <h1 class="mt-4 text-xl font-black tracking-tight text-[var(--color-ink)]">
              {{ t('auth.googleCallbackLoading') }}
            </h1>
            <p class="mt-1.5 text-sm text-[var(--color-muted)]">
              {{ t('auth.googleCallbackWait') }}
            </p>
          </template>

          <!-- ===== 成功 ===== -->
          <template v-else-if="status === 'success'">
            <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-[var(--color-success-50)] text-[var(--color-success-600)]">
              <UIcon name="lucide:circle-check" class="h-7 w-7" aria-hidden="true" />
            </span>
            <h1 class="mt-4 text-xl font-black tracking-tight text-[var(--color-ink)]">
              {{ t('auth.loginSuccess') }}
            </h1>
            <p class="mt-1.5 text-sm text-[var(--color-muted)]">
              {{ t('auth.redirecting') }}
            </p>
          </template>

          <!-- ===== 失败 ===== -->
          <template v-else>
            <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-[var(--color-error-50)] text-[var(--color-error-600)]">
              <UIcon name="lucide:circle-alert" class="h-7 w-7" aria-hidden="true" />
            </span>
            <h1 class="mt-4 text-xl font-black tracking-tight text-[var(--color-ink)]">
              {{ t('auth.googleCallbackFailed') }}
            </h1>
            <p role="alert" class="mt-2 text-sm text-[var(--color-error-600)]">
              {{ errorMsg }}
            </p>
            <!-- 错误必须给出恢复路径（ui-ux-pro-max: error-recovery），
                 只显示一句报错、用户除了点返回无事可做是很糟的终点 -->
            <div class="mt-7 flex flex-col gap-3 sm:flex-row">
              <NuxtLinkLocale to="/login" class="ui-pill ui-pill-brand h-11 flex-1 text-sm">
                <UIcon name="lucide:log-in" class="h-4 w-4" aria-hidden="true" />
                {{ t('auth.backToLogin') }}
              </NuxtLinkLocale>
              <button
                type="button"
                class="ui-pill ui-pill-ghost h-11 flex-1 text-sm"
                @click="retry"
              >
                <UIcon name="lucide:refresh-cw" class="h-4 w-4" aria-hidden="true" />
                {{ t('common.retry') }}
              </button>
            </div>
          </template>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
// 认证类页面：只有导航、没有页脚（与 login.vue 一致，走 layouts/auth.vue）
definePageMeta({ layout: 'auth' })

import { resolveAuthErrorMessage, useAuth } from '~~/composables/useAuth'

/**
 * Google 回调页：/auth/google/callback
 *
 * 职责链（契约 3.3）：
 *   Google 带 ?code=...&state=... 回到这里
 *   → 本页把 { code, state } POST 给 BFF 的 /api/auth/google/callback
 *   → BFF 自己拼 redirectUri（不信任前端传入）并写 cookie，返回 { userInfo }
 *   → 本页跳转目标页面
 *
 * ⚠️ 本页**不做 state 校验**：state 的权威校验在后端（Redis 里 shop:oauth:state:{state}
 * 一次性消费，契约 2.5）。前端就算比对了也没用 —— state 存在后端 Redis 里，
 * 前端根本没有可信副本，自造一份反而会拦掉合法回调。
 */
const { t, locale } = useI18n()
const { loginByGoogle } = useAuth()
const route = useRoute()
const localePath = useLocalePath()

type CallbackStatus = 'pending' | 'success' | 'error'

const status = ref<CallbackStatus>('pending')
const errorMsg = ref('')

/** 记住参数，供"重试"按钮复用（不重新走一次 Google 授权，只是再打一次 BFF） */
const params = computed(() => {
  const pick = (key: string) => {
    const raw = route.query[key]
    const value = Array.isArray(raw) ? raw[0] : raw
    return typeof value === 'string' ? value : ''
  }
  return { code: pick('code'), state: pick('state'), error: pick('error') }
})

/**
 * 登录后回到用户原本想去的页面。
 *
 * 两个来源，按优先级：
 *   1) URL 的 ?redirect=（手工跳这个回调页时会带，走与 login.vue 同一套开放重定向防护）
 *   2) sessionStorage（正常 Google 流程：Google 送回的是**固定回调路径**，
 *      BFF 拼的 URL 不带任何 query，所以 login.vue 在跳转前把目标暂存在这里）
 *
 * ⚠️ 两个来源都必须过 /^\/(?!\/)/ 校验：
 *   sessionStorage 虽然是本站写入的，但它同样是**可被 XSS 篡改的存储**，
 *   不能因为"是我们自己写的"就跳过校验 —— 否则这里就成了新的开放重定向入口。
 */
const redirectTarget = computed(() => {
  const raw = route.query.redirect
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value === 'string' && /^\/(?!\/)/.test(value)) {
    return value
  }
  if (import.meta.client) {
    const stored = sessionStorage.getItem('auth-redirect-after-oauth')
    if (stored && /^\/(?!\/)/.test(stored)) {
      return stored
    }
  }
  return localePath('/profile')
})

const run = async () => {
  status.value = 'pending'
  errorMsg.value = ''

  // Google 侧直接拒权（用户在授权页点了"取消"）时不会带 code，
  // 这种情况不该报"系统错误"，文案要告诉用户是他自己取消的
  if (params.value.error) {
    status.value = 'error'
    errorMsg.value = t('auth.googleCancelled')
    return
  }

  if (!params.value.code || !params.value.state) {
    status.value = 'error'
    errorMsg.value = t('auth.googleParamsMissing')
    return
  }

  try {
    // ⚠️ lang 必须显式传：BFF 要用它拼出与授权阶段**逐字相同**的 redirectUri。
    //    Google 会逐字节比对"授权时"与"换 token 时"的 redirect_uri，
    //    不一致就报 redirect_uri_mismatch（400）。
    //    漏传时 BFF 回退 'en'，中文/法语登录都会被拼成 /en/... → 必然失败。
    await loginByGoogle({ code: params.value.code, state: params.value.state, lang: locale.value })
    status.value = 'success'
    // 目标页必须在**成功之后立刻取一次并清掉**：
    //   - 取一次：redirectTarget 是 computed，若迟迟不清 key，后续任何访问都可能重算，
    //     万一期间 key 被改掉就会跳到别处；
    //   - 清掉：这是单次跳转的临时状态，留着会让"下次登录"误用上一次的目标页。
    const target = redirectTarget.value
    if (import.meta.client) {
      sessionStorage.removeItem('auth-redirect-after-oauth')
    }
    // 停留一下让用户看到"成功"：直接跳走的话页面会闪一下空白，
    // 用户会怀疑到底登没登上（这是 OAuth 回跳最常见的体验问题）
    setTimeout(() => {
      navigateTo(target)
    }, 600)
  } catch (e: any) {
    status.value = 'error'
    errorMsg.value = resolveAuthErrorMessage(e) || t('auth.googleCallbackFailedDesc')
  }
}

const retry = () => {
  run()
}

/**
 * 只在客户端跑。
 *
 * 理由：授权码是**一次性**的，Google 换 token 只能成功一次。
 * SSR 阶段执行一次、客户端 hydrate 再执行一次的话，第二次必然拿到
 * "code 已被使用"的失败，用户就会看到"登录失败"——而其实第一次是成功的。
 * 这类"服务端已经改完状态、客户端再跑一遍"的动作，必须严格限定在客户端。
 */
onMounted(run)

useSeoMeta({
  title: () => t('auth.googleCallbackLoading'),
  robots: 'noindex, nofollow',
})
</script>
