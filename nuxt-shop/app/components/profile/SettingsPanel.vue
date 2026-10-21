<!--
  「账户设置」面板：账户资料表单 + 密码设置/修改

  从 pages/profile.vue 拆出（P1-4 巨型文件拆分第四步）。
  资料与密码**刻意共用一个标签页**（Lead 已裁决）：账户设置在语义上本来就同时管这两件事，
  再开一个 Tab 只会挤压移动端本就不宽裕的横向 tab 栏。

  数据边界：
   · hasPassword / profileLoading / profileError / loadProfile → composables/useProfileAccount.ts（useState 共享）
   · userInfo → composables/useAuth.ts（同样是全局状态）
   · 表单字段、可见性、校验、提交 → 本组件局部
-->
<template>
  <div>
    <form class="mt-5 grid gap-4 sm:grid-cols-2" @submit.prevent="handleSave">
      <div>
        <label for="profile-realname" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
          {{ t('profile.realname') }}
        </label>
        <input
          id="profile-realname"
          v-model="form.realname"
          type="text"
          autocomplete="name"
          class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
          @input="markTouched"
        >
      </div>

      <div>
        <label for="profile-username" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
          {{ t('profile.username') }}
        </label>
        <input
          id="profile-username"
          :value="userInfo?.username || ''"
          type="text"
          disabled
          aria-describedby="profile-username-hint"
          class="h-11 w-full cursor-not-allowed rounded-[var(--radius-input)] border border-[var(--color-line)] bg-[var(--color-line-soft)] px-4 text-sm text-[var(--color-muted)]"
        >
        <!--
          用户名规则（契约 0 节决策 7）：u_ + 8 位小写字母数字，系统生成，**永不可改**。
          必须把"为什么灰着"讲出来 —— 只给一个 disabled 的框，用户会以为是页面坏了
          或者以为自己该去别处改。
        -->
        <p id="profile-username-hint" class="mt-1.5 flex items-start gap-1.5 text-[12px] leading-relaxed text-[var(--color-muted)]">
          <UIcon name="lucide:lock" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          <span>{{ t('auth.usernameReadonlyHint') }}</span>
        </p>
      </div>

      <div>
        <label for="profile-email" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
          {{ t('profile.email') }}
        </label>
        <input
          id="profile-email"
          v-model="form.email"
          type="email"
          autocomplete="email"
          class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
          @input="markTouched"
        >
      </div>

      <div>
        <label for="profile-phone" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
          {{ t('profile.phone') }}
        </label>
        <input
          id="profile-phone"
          v-model="form.phone"
          type="tel"
          autocomplete="tel"
          class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
          @input="markTouched"
        >
      </div>

      <div class="sm:col-span-2">
        <button
          type="submit"
          class="ui-pill ui-pill-brand h-11 px-6 text-sm disabled:cursor-not-allowed disabled:opacity-45"
          :disabled="!dirty"
        >
          <UIcon name="lucide:save" class="h-4 w-4" aria-hidden="true" />
          {{ t('profile.saveChanges') }}
        </button>
      </div>
    </form>

    <!--
      ===== 账户密码：设置 / 修改 =====
      标题由 hasPassword 决定（契约 2.8 / 5.5.2）：
        false -> 「设置密码」（邮箱验证码 / Google 自动注册的用户，没有旧密码）
        true  -> 「修改密码」（必须先验旧密码）
        null  -> 资料还没拉到 / 接口失败：整块不渲染，
                 避免在"不知道用户有没有密码"的情况下给出错误的表单。
    -->
    <section class="mt-7 border-t border-[var(--color-line-soft)] pt-6">
      <h3 class="flex items-center gap-2 text-[15px] font-black text-[var(--color-ink)]">
        <UIcon name="lucide:key-round" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
        {{ t('profile.passwordSection') }}
      </h3>
      <p class="mt-1.5 text-[12px] leading-relaxed text-[var(--color-muted)]">
        {{ t('profile.passwordSectionDesc') }}
      </p>

      <!-- 错误 / 骨架 / 表单：三态 fade 切换，骨架↔表单不再硬切 -->
      <Transition name="fade" mode="out-in">
        <!-- 资料加载失败：把后端/网络的原话展示出来，并给一个重试入口 -->
        <div
          v-if="profileError"
          key="error"
          class="mt-4 flex items-start gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-4 py-3 text-[13px] font-medium text-[var(--color-error-600)]"
          role="alert"
        >
          <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span class="min-w-0 flex-1">
            {{ t('profile.profileLoadFailed') }}：{{ profileError }}
            <button
              type="button"
              class="ml-2 cursor-pointer font-bold underline underline-offset-2 disabled:opacity-45"
              :disabled="profileLoading"
              @click="loadProfile()"
            >
              {{ t('common.retry') }}
            </button>
          </span>
        </div>

        <!-- 加载中：骨架屏（不猜字段数，固定两行） -->
        <div v-else-if="profileLoading && hasPassword === null" key="loading" class="mt-4 space-y-3" aria-busy="true">
          <span class="block h-11 w-full animate-pulse rounded-[var(--radius-input)] bg-[var(--color-line-soft)]"></span>
          <span class="block h-11 w-2/3 animate-pulse rounded-[var(--radius-input)] bg-[var(--color-line-soft)]"></span>
        </div>

        <form v-else-if="hasPassword !== null" key="form" class="mt-4 grid gap-4 sm:grid-cols-2" @submit.prevent="handlePasswordSubmit">
        <!-- 旧密码：只在用户**确实已有密码**时渲染。
              没设过密码的人填不出旧密码，给一个必填框就是纯阻塞。 -->
        <div v-if="hasPassword" class="sm:col-span-2">
          <label for="profile-old-password" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
            {{ t('auth.oldPassword') }}
          </label>
          <input
            id="profile-old-password"
            v-model="passwordForm.oldPassword"
            type="password"
            autocomplete="current-password"
            class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
            @input="markPasswordTouched"
          >
        </div>

        <div>
          <label for="profile-new-password" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
            {{ t('auth.password') }}
          </label>
          <div class="relative">
            <input
              id="profile-new-password"
              v-model="passwordForm.newPassword"
              :type="showNewPassword ? 'text' : 'password'"
              autocomplete="new-password"
              :aria-invalid="passwordMismatch"
              :aria-describedby="passwordStrength ? 'profile-password-strength' : undefined"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white pl-4 pr-11 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
              @input="markPasswordTouched"
            >
            <!-- 密码可见性切换：aria-label 随状态变化，不用 emoji -->
            <button
              type="button"
              class="absolute inset-y-0 right-0 flex w-11 cursor-pointer items-center justify-center text-[var(--color-muted)] transition-colors hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
              :aria-label="showNewPassword ? t('auth.hidePassword') : t('auth.showPassword')"
              :aria-pressed="showNewPassword"
              @click="showNewPassword = !showNewPassword"
            >
              <UIcon :name="showNewPassword ? 'lucide:eye-off' : 'lucide:eye'" class="h-4 w-4" aria-hidden="true" />
            </button>
          </div>

          <!-- 密码强度：轻量三段提示条，只提示不阻断 -->
          <div v-if="passwordStrength" id="profile-password-strength" class="mt-2" aria-live="polite">
            <span class="block h-1 w-full overflow-hidden rounded-full bg-[var(--color-line-soft)]">
              <span
                class="block h-full rounded-full transition-[width] duration-200 motion-reduce:transition-none"
                :class="passwordStrengthBarClass"
              ></span>
            </span>
            <!-- 文案随强度变化：只挂 passwordTooWeak 会在"强"的时候也说密码太弱 -->
            <span class="mt-1 block text-[11px] text-[var(--color-muted)]">
              {{ passwordStrengthText }}
            </span>
          </div>
        </div>

        <div>
          <label for="profile-confirm-password" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
            {{ t('auth.confirmPassword') }}
          </label>
          <div class="relative">
            <input
              id="profile-confirm-password"
              v-model="passwordForm.confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              autocomplete="new-password"
              :aria-invalid="passwordMismatch"
              :aria-describedby="passwordMismatch ? 'profile-confirm-password-error' : undefined"
              class="h-11 w-full rounded-[var(--radius-input)] border bg-white pl-4 pr-11 text-sm text-[var(--color-ink)] transition-colors focus:outline-none focus:ring-2 motion-reduce:transition-none"
              :class="passwordMismatch
                ? 'border-[var(--color-error)] focus:border-[var(--color-error)] focus:ring-[var(--color-error-50)]'
                : 'border-[var(--color-line)] focus:border-[var(--color-brand-400)] focus:ring-[var(--color-brand-100)]'"
              @input="markPasswordTouched"
            >
            <button
              type="button"
              class="absolute inset-y-0 right-0 flex w-11 cursor-pointer items-center justify-center text-[var(--color-muted)] transition-colors hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
              :aria-label="showConfirmPassword ? t('auth.hidePassword') : t('auth.showPassword')"
              :aria-pressed="showConfirmPassword"
              @click="showConfirmPassword = !showConfirmPassword"
            >
              <UIcon :name="showConfirmPassword ? 'lucide:eye-off' : 'lucide:eye'" class="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
          <!-- 字段级错误：不只在 toast 里说，输入框下方也要留痕 -->
          <p
            v-if="passwordMismatch"
            id="profile-confirm-password-error"
            class="mt-1.5 text-[12px] font-medium text-[var(--color-error-600)]"
            role="alert"
          >
            {{ t('auth.passwordMismatch') }}
          </p>
        </div>

        <!-- 标题随 hasPassword 走：这是"设置"还是"修改"由后端状态决定，不由前端猜 -->
        <div class="flex flex-wrap items-center gap-3 sm:col-span-2">
          <!-- 提交态交给 AsyncButton：spinner + 文案切换 + disabled + aria-busy 一处收口 -->
          <AsyncButton
            type="submit"
            class="ui-pill ui-pill-brand h-11 px-6 text-sm disabled:cursor-not-allowed disabled:opacity-45"
            :pending="passwordSubmitting"
            :pending-label="t('common.loading')"
            :disabled="!passwordFormValid"
          >
            <UIcon name="lucide:key-round" class="h-4 w-4" aria-hidden="true" />
            {{ hasPassword ? t('auth.changePassword') : t('auth.setPassword') }}
          </AsyncButton>
          <button
            v-if="passwordTouched"
            type="button"
            class="cursor-pointer text-[13px] font-medium text-[var(--color-muted)] underline underline-offset-2 transition-colors hover:text-[var(--color-ink-soft)] motion-reduce:transition-none"
            :disabled="passwordSubmitting"
            @click="resetPasswordForm"
          >
            {{ t('common.cancel') }}
          </button>
        </div>
        </form>
      </Transition>
    </section>
  </div>
</template>

<script setup lang="ts">
import { readErrorMessage } from '~~/composables/useApiError'
import { useAuth } from '~~/composables/useAuth'
import { useProfileAccount } from '~~/composables/useProfileAccount'

const { t } = useI18n()
const toast = useToast()

const { userInfo, changePassword } = useAuth()
const {
  hasPassword,
  loading: profileLoading,
  error: profileError,
  load: loadProfile,
} = useProfileAccount()

// ---------------------------------------------------------------------------
// 账户资料表单
// ---------------------------------------------------------------------------

const form = reactive({ realname: '', email: '', phone: '' })
/** 用户是否动过表单：动过之后就不要再被 userInfo 的变化覆盖，避免正在输入的内容被冲掉 */
const formTouched = ref(false)

function markTouched() {
  formTouched.value = true
}

function syncForm() {
  form.realname = userInfo.value?.realname ?? ''
  form.email = userInfo.value?.email ?? ''
  form.phone = userInfo.value?.phone ?? ''
}

// immediate：SSR 时 userInfo 为 null；客户端 init-auth 插件恢复登录态后，
// 这个 watch 会再跑一次把真实资料填进表单
watch(() => userInfo.value, () => {
  if (!formTouched.value) syncForm()
}, { immediate: true })

const dirty = computed(() => {
  const current = userInfo.value
  if (!current) return false
  return form.realname !== (current.realname ?? '')
    || form.email !== (current.email ?? '')
    || form.phone !== (current.phone ?? '')
})

/**
 * 保存资料。
 *
 * 注意：后端目前**没有**更新用户资料的接口（server/api 下只有 auth/login + auth/logout），
 * 所以这里只更新前端共享的 userInfo 状态并写回 localStorage —— 写进去的是用户自己刚输入
 * 的内容，不属于伪造数据，但它**不会**同步到服务器，换设备/清缓存后会变回后端原值。
 * 等 BFF 提供更新接口之后，这里应替换成真实请求。
 */
function handleSave() {
  const current = userInfo.value
  if (!current) return

  const next = {
    ...current,
    realname: form.realname.trim(),
    email: form.email.trim(),
    phone: form.phone.trim(),
  }
  userInfo.value = next
  form.realname = next.realname
  form.email = next.email
  form.phone = next.phone
  formTouched.value = false

  if (import.meta.client) {
    // key 必须与 composables/useAuth.ts 里 restoreUser 读取的 'user-info' 一致
    localStorage.setItem('user-info', JSON.stringify(next))
  }

  toast.add({ title: t('shop.profileSaveSuccess'), color: 'success', icon: 'lucide:check' })
}

// ---------------------------------------------------------------------------
// 账户密码
// ---------------------------------------------------------------------------

const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
/** 与资料表单同一套"用户动过就别覆盖"的思路：改动过程中不被任何异步回填冲掉 */
const passwordTouched = ref(false)
const passwordSubmitting = ref(false)
/** 两个密码框的可见性：默认遮住，眼睛图标切换（aria-label 随状态变化） */
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

/**
 * 密码强度：与后端规则一致（8-32 位，至少含字母和数字，契约 2.2）。
 * 做成**轻量**的三段提示条，只提示不阻断 —— 真正的拒绝交给后端校验。
 */
const passwordStrength = computed<'' | 'weak' | 'medium' | 'strong'>(() => {
  const value = passwordForm.newPassword
  if (!value) return ''
  const hasLetter = /[a-zA-Z]/.test(value)
  const hasDigit = /\d/.test(value)
  if (value.length < 8 || !hasLetter || !hasDigit) return 'weak'
  if (value.length >= 12 && /[^a-zA-Z0-9]/.test(value)) return 'strong'
  return 'medium'
})

const passwordStrengthBarClass = computed(() => {
  if (passwordStrength.value === 'weak') return 'bg-[var(--color-error)] w-1/3'
  if (passwordStrength.value === 'medium') return 'bg-[var(--color-warning)] w-2/3'
  if (passwordStrength.value === 'strong') return 'bg-[var(--color-success)] w-full'
  return 'w-0'
})

const passwordMismatch = computed(
  () => !!passwordForm.confirmPassword && passwordForm.newPassword !== passwordForm.confirmPassword,
)

/**
 * 强度提示文案。
 *
 * 按语义分两档用（不再自己拼字符串）：
 *   - 弱：`auth.passwordTooWeak` —— 它就是后端规则的说明（8-32 位、至少含字母和数字）；
 *   - 中 / 强：换用 `auth.strengthMedium` / `auth.strengthStrong` 的档位词，
 *     前缀 `${auth.passwordStrength}:`，读起来是「密码强度：强」。
 *     若这里仍用 passwordTooWeak，密码很强时界面却在说"密码太弱"，自相矛盾。
 */
const passwordStrengthText = computed(() => {
  if (passwordStrength.value === 'weak') return t('auth.passwordTooWeak')
  if (passwordStrength.value === 'medium') return `${t('auth.passwordStrength')}: ${t('auth.strengthMedium')}`
  if (passwordStrength.value === 'strong') return `${t('auth.passwordStrength')}: ${t('auth.strengthStrong')}`
  return ''
})

/** 前端先拦一次低成本错误，省一轮往返；服务端仍会独立校验 */
const passwordFormValid = computed(() => {
  if (passwordSubmitting.value || hasPassword.value === null) return false
  if (hasPassword.value && !passwordForm.oldPassword) return false
  if (!passwordForm.newPassword) return false
  if (passwordForm.newPassword !== passwordForm.confirmPassword) return false
  return true
})

function markPasswordTouched() {
  passwordTouched.value = true
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordTouched.value = false
  showNewPassword.value = false
  showConfirmPassword.value = false
}

/**
 * 提交新密码。
 *
 * 走 useAuth().changePassword（契约 4.1，POST /api/auth/change-password），
 * 它负责带 httpOnly cookie 打 BFF 并解包结果；本组件不自己 $fetch，避免两套错误语义。
 */
async function handlePasswordSubmit() {
  if (passwordSubmitting.value) return

  if (hasPassword.value && !passwordForm.oldPassword) {
    toast.add({ title: t('profile.oldPasswordRequired'), color: 'warning', icon: 'lucide:circle-alert' })
    return
  }
  if (!passwordForm.newPassword) {
    toast.add({ title: t('profile.newPasswordRequired'), color: 'warning', icon: 'lucide:circle-alert' })
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    toast.add({ title: t('auth.passwordMismatch'), color: 'warning', icon: 'lucide:circle-alert' })
    return
  }

  passwordSubmitting.value = true
  // 标题要在这次提交前定下来：提交成功后 hasPassword 会立刻变 true，
  // 否则下面的成功提示会从「密码设置成功」变成「密码已更新」，与用户刚做的事对不上。
  const wasSettingPassword = hasPassword.value === false
  try {
    await changePassword({
      // 没设过密码的账号不该传 oldPassword（后端也接受空，见契约 2.7 注释）
      oldPassword: hasPassword.value ? passwordForm.oldPassword : undefined,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword,
    })

    // ★ 成功后必须把状态同步成"已设置密码"：
    //   否则用户改完密码，标题还停在「设置密码」，会以为刚才没成功（Lead 已明确要求）。
    hasPassword.value = true
    resetPasswordForm()
    toast.add({
      title: wasSettingPassword ? t('profile.passwordSetSuccess') : t('profile.passwordSaved'),
      color: 'success',
      icon: 'lucide:check',
    })
  } catch (error) {
    toast.add({
      title: t('profile.passwordSaveFailed'),
      // 错误文案统一走 readErrorMessage，才能把后端那句真实提示（如"原密码不正确"）显示出来
      description: readErrorMessage(error, t('profile.passwordSaveFailed')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    passwordSubmitting.value = false
  }
}
</script>
