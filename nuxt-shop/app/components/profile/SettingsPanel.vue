<!--
  「账户资料」面板：只读展示 + 编辑模式 + 账户密码

  从 pages/profile.vue 拆出（P1-4 巨型文件拆分第四步）。

  ⚠️ 2026-10-27（第三轮）本组件的行为契约：

   1. **默认只读**：进入本分区只看得到信息，看不到任何输入框。
      点分区表头或资料卡上的「编辑资料」才进入编辑模式（`editing` prop，页面持有）。
      保存成功 / 点「取消」都退回只读。 —— 用户原话：
      "当前进入账户资料，直接就是可以编辑的状态，应该点击『编辑资料』才进入编辑模式，
      非编辑模式时，只是展示就好了"。
   2. **头像入口只有一个**：点资料卡上的大头像换头像（pages/profile.vue）。
      本组件**不再**有「更换头像」按钮 —— 两个入口重复，用户已明确指出。
   3. **只读模式补充信息**：账户概览（订单 / 收藏 / 收货地址 数量，均可点直达对应分区）、
      个人资料（昵称 / 手机号 / 邮箱）、默认收货地址。 —— 用户要求"应该有更多的信息能展示在个人资料中"。
   4. **邮箱不做特殊底色**：它是登录标识、不可改，但不需要一整块灰色背景来强调（用户原话：
      "邮箱不需要特殊的背景色"）；只读靠 `readonly` + 锁图标说明，视觉上与其它字段同级。

  数据边界：
   · hasPassword / loading / error / saving / saveProfile → composables/useProfileAccount.ts（useState 共享）
   · userInfo → composables/useAuth.ts；地址簿 → composables/useAddressBook.ts（与收货地址分区同一份数据）
   · 表单字段、编辑态、校验、提交 → 本组件局部
-->
<template>
  <div>
    <!-- ==================== 只读模式 ==================== -->
    <div v-if="!editing">
      <!-- 账户概览：三个真实数字，都能点进对应分区 -->
      <section>
        <h3 class="flex items-center gap-1.5 text-[13px] font-black text-[var(--color-ink)]">
          <UIcon name="lucide:chart-no-axes-column" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
          {{ t('profile.overviewTitle') }}
        </h3>

        <div class="mt-3 grid grid-cols-3 gap-2.5 sm:gap-3">
          <button
            v-for="tile in overviewTiles"
            :key="tile.key"
            type="button"
            class="group flex cursor-pointer items-center justify-between gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-surface-secondary)] px-3 py-3.5 text-left transition-shadow duration-200 hover:shadow-[var(--shadow-md)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-500)] motion-reduce:transition-none sm:px-4"
            @click="emit('goto-tab', tile.key)"
          >
            <span class="min-w-0">
              <span class="tabular block text-[22px] font-black leading-none text-[var(--color-brand-600)]">
                {{ tile.value }}
              </span>
              <span class="mt-1.5 block truncate text-[12px] text-[var(--color-muted)]">{{ t(tile.label) }}</span>
            </span>
            <span
              class="hidden h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[var(--color-surface)] text-[var(--color-brand-500)] transition-colors duration-200 group-hover:text-[var(--color-brand-600)] motion-reduce:transition-none sm:flex"
              aria-hidden="true"
            >
              <UIcon :name="tile.icon" class="h-4 w-4" />
            </span>
          </button>
        </div>
      </section>

      <!-- 个人资料：只读，不做任何可编辑外观 -->
      <section class="mt-6 border-t border-[var(--color-line-soft)] pt-5">
        <h3 class="flex items-center gap-1.5 text-[13px] font-black text-[var(--color-ink)]">
          <UIcon name="lucide:user-round" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
          {{ t('profile.infoTitle') }}
        </h3>

        <dl class="mt-3.5 grid gap-x-8 gap-y-4 sm:grid-cols-2">
          <div class="min-w-0">
            <dt class="text-[12.5px] text-[var(--color-muted)]">{{ t('profile.realname') }}</dt>
            <dd class="mt-1 break-words text-[15px] font-bold text-[var(--color-ink)]">{{ displayName }}</dd>
          </div>

          <div class="min-w-0">
            <dt class="text-[12.5px] text-[var(--color-muted)]">{{ t('profile.phone') }}</dt>
            <dd
              class="tabular mt-1 break-words text-[15px] font-bold"
              :class="phone ? 'text-[var(--color-ink)]' : 'font-medium text-[var(--color-muted)]'"
            >
              {{ phone || t('profile.notSet') }}
            </dd>
          </div>

          <div class="min-w-0 sm:col-span-2">
            <dt class="text-[12.5px] text-[var(--color-muted)]">{{ t('profile.email') }}</dt>
            <dd class="mt-1 break-words text-[15px] font-bold text-[var(--color-ink)]">
              {{ email || t('profile.notSet') }}
            </dd>
          </div>
        </dl>
      </section>

      <!-- 默认收货地址：摘要 + 去管理 -->
      <section class="mt-6 border-t border-[var(--color-line-soft)] pt-5">
        <div class="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
          <h3 class="flex items-center gap-1.5 text-[13px] font-black text-[var(--color-ink)]">
            <UIcon name="lucide:map-pin" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
            {{ t('profile.defaultAddressTitle') }}
          </h3>
          <button
            type="button"
            class="ui-pill ui-pill-ghost h-9 shrink-0 cursor-pointer px-4 text-[12.5px]"
            @click="emit('goto-tab', 'addresses')"
          >
            {{ t('profile.manageAddress') }}
            <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5" aria-hidden="true" />
          </button>
        </div>

        <div
          v-if="defaultAddress"
          class="mt-3 rounded-[var(--radius-card-sm)] border border-[var(--color-line)] px-4 py-3"
        >
          <p class="flex flex-wrap items-center gap-x-2 gap-y-1 text-[13.5px]">
            <span class="font-bold text-[var(--color-ink)]">{{ defaultAddress.receiverName || '—' }}</span>
            <span class="tabular text-[var(--color-ink-soft)]">{{ defaultAddress.receiverPhone || '' }}</span>
            <span
              v-if="defaultAddress.addressTag"
              class="rounded-full bg-[var(--color-line-soft)] px-2 py-0.5 text-[10px] font-bold text-[var(--color-ink-soft)]"
            >
              {{ defaultAddress.addressTag }}
            </span>
          </p>
          <p class="mt-1.5 text-[12.5px] leading-relaxed text-[var(--color-muted)]">
            {{ defaultAddress.fullAddress || '—' }}
          </p>
        </div>

        <p v-else class="mt-3 text-[13px] text-[var(--color-muted)]">
          {{ t('profile.noDefaultAddress') }}
        </p>
      </section>
    </div>

    <!-- ==================== 编辑模式 ==================== -->
    <form v-else @submit.prevent="handleSave">
      <div class="grid gap-4 sm:grid-cols-2">
        <div>
          <label for="profile-realname" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
            {{ t('profile.realname') }}
          </label>
          <input
            id="profile-realname"
            v-model="form.realname"
            type="text"
            autocomplete="nickname"
            maxlength="30"
            :aria-invalid="realnameInvalid"
            :aria-describedby="realnameInvalid ? 'profile-realname-error' : undefined"
            class="h-11 w-full rounded-[var(--radius-input)] border bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:outline-none focus:ring-2 motion-reduce:transition-none"
            :class="realnameInvalid
              ? 'border-[var(--color-error)] focus:border-[var(--color-error)] focus:ring-[var(--color-error-50)]'
              : 'border-[var(--color-line)] focus:border-[var(--color-brand-400)] focus:ring-[var(--color-brand-100)]'"
            @input="markTouched"
          >
          <p
            v-if="realnameInvalid"
            id="profile-realname-error"
            class="mt-1.5 text-[12px] font-medium text-[var(--color-error-600)]"
            role="alert"
          >
            {{ t('profile.realnameRequired') }}
          </p>
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
            maxlength="20"
            class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
            @input="markTouched"
          >
          <p class="mt-1.5 text-[12px] leading-relaxed text-[var(--color-muted)]">
            {{ t('profile.phoneHint') }}
          </p>
        </div>

        <!--
          邮箱：登录标识，只读。
          **不加任何特殊底色**（原来是 --color-line-soft 灰底，用户明确说"邮箱不需要特殊的背景色"）：
          与其它输入框同一套边框/白底，只靠 lock 图标 + 说明表达"改不了"。
        -->
        <div class="sm:col-span-2">
          <label for="profile-email" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
            {{ t('profile.email') }}
          </label>
          <input
            id="profile-email"
            :value="email"
            type="email"
            readonly
            aria-describedby="profile-email-hint"
            class="h-11 w-full cursor-not-allowed rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)]"
          >
          <p id="profile-email-hint" class="mt-1.5 flex items-start gap-1.5 text-[12px] leading-relaxed text-[var(--color-muted)]">
            <UIcon name="lucide:lock" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            <span>{{ t('profile.emailReadonlyHint') }}</span>
          </p>
        </div>
      </div>

      <div class="mt-5 flex flex-wrap items-center gap-3">
        <!--
          不可保存时用**中性 ghost** 而不是「brand 色 + opacity-45」：
          半透明的珊瑚药丸会被看成"加载中的品牌按钮"，既不像灰掉的禁用态，
          也会在页面上留下一块浑浊的粉色。ghost 一眼就是"现在不能点"。
        -->
        <AsyncButton
          type="submit"
          class="ui-pill h-11 px-6 text-sm disabled:cursor-not-allowed"
          :class="canSaveProfile ? 'ui-pill-brand' : 'ui-pill-ghost text-[var(--color-muted)]'"
          :pending="saving"
          :pending-label="t('profile.savingProfile')"
          :disabled="!canSaveProfile"
        >
          <UIcon name="lucide:save" class="h-4 w-4" aria-hidden="true" />
          {{ t('profile.saveChanges') }}
        </AsyncButton>
        <button
          type="button"
          class="cursor-pointer text-[13px] font-medium text-[var(--color-muted)] underline underline-offset-2 transition-colors hover:text-[var(--color-ink-soft)] motion-reduce:transition-none"
          :disabled="saving"
          @click="cancelEdit"
        >
          {{ t('common.cancel') }}
        </button>
      </div>
    </form>

    <!-- ==================== 账户密码（独立于编辑模式，点开才出现表单） ==================== -->
    <section v-if="!editing" class="mt-6 border-t border-[var(--color-line-soft)] pt-5">
      <div class="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
        <div class="min-w-0">
          <h3 class="flex items-center gap-1.5 text-[13px] font-black text-[var(--color-ink)]">
            <UIcon name="lucide:key-round" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
            {{ t('profile.passwordSection') }}
          </h3>
          <p class="mt-1 text-[12px] leading-relaxed text-[var(--color-muted)]">
            {{ t('profile.passwordSectionDesc') }}
          </p>
        </div>

        <!-- 资料还没拉到（hasPassword === null）时不猜标题：先给骨架，避免"设置/修改"二选一猜错 -->
        <span
          v-if="hasPassword === null"
          class="mt-1 block h-9 w-28 shrink-0 animate-pulse rounded-full bg-[var(--color-line-soft)]"
          aria-hidden="true"
        ></span>
        <button
          v-else
          type="button"
          class="ui-pill ui-pill-ghost h-9 shrink-0 cursor-pointer px-4 text-[12.5px]"
          :aria-expanded="passwordFormOpen"
          aria-controls="profile-password-form"
          @click="passwordFormOpen = !passwordFormOpen"
        >
          {{ hasPassword ? t('auth.changePassword') : t('auth.setPassword') }}
        </button>
      </div>

      <div v-if="passwordFormOpen" id="profile-password-form" class="mt-4">
        <!-- 资料加载失败：把后端/网络的原话展示出来，并给一个重试入口 -->
        <div
          v-if="profileError"
          class="flex items-start gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-4 py-3 text-[13px] font-medium text-[var(--color-error-600)]"
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

        <!--
          加载中：骨架屏（不猜字段数，固定两行）。
          ⚠️ 判据是 hasPassword === null（"还不知道"），**不能**写成
          `profileLoading && hasPassword === null`：SSR 时 profileLoading 恒为 false、
          hasPassword 恒为 null，那个条件在服务端一个分支都不匹配（渲染出空注释节点），
          而客户端这时候 loading 已经是 true（会渲染骨架 div）——
          hydration 直接报 node mismatch。把"未知"本身当成加载态，两端才一致。
        -->
        <div v-else-if="hasPassword === null" class="space-y-3" aria-busy="true">
          <span class="block h-11 w-full animate-pulse rounded-[var(--radius-input)] bg-[var(--color-line-soft)]"></span>
          <span class="block h-11 w-2/3 animate-pulse rounded-[var(--radius-input)] bg-[var(--color-line-soft)]"></span>
        </div>

        <form v-else class="grid gap-4 sm:grid-cols-2" @submit.prevent="handlePasswordSubmit">
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

          <div class="flex flex-wrap items-center gap-3 sm:col-span-2">
            <!-- 提交态交给 AsyncButton：spinner + 文案切换 + disabled + aria-busy 一处收口 -->
            <AsyncButton
              type="submit"
              class="ui-pill h-11 px-6 text-sm disabled:cursor-not-allowed"
              :class="passwordFormValid ? 'ui-pill-brand' : 'ui-pill-ghost text-[var(--color-muted)]'"
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
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { AddressVO } from '~~/types/shop-address'
import { isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'
import { useAddressBook } from '~~/composables/useAddressBook'
import { useAuth } from '~~/composables/useAuth'
import { useProfileAccount } from '~~/composables/useProfileAccount'
import { useProfileFields } from '~~/composables/useProfileFields'

/**
 * 分区跳转用的键（只列本组件会跳的那三个）。
 * 页面那边直接把它喂给 selectTab()，本组件不需要知道 URL 长什么样。
 */
type PanelTabKey = 'orders' | 'favorites' | 'addresses'

const props = defineProps<{
  /** 是否处于编辑模式（页面持有：表头「编辑资料」与资料卡按钮共用同一个开关） */
  editing: boolean
  /** 账户概览三个数字（真实接口 total，由页面从 useOrderList / useFavoriteList / useAddressBook 传入） */
  orderTotal: number
  favoriteTotal: number
  addressTotal: number
}>()

const emit = defineEmits<{
  (e: 'update:editing', value: boolean): void
  (e: 'goto-tab', key: PanelTabKey): void
}>()

const { t } = useI18n()
const toast = useToast()

const { userInfo, changePassword } = useAuth()
const { displayName } = useProfileFields()
const {
  hasPassword,
  loading: profileLoading,
  error: profileError,
  saving,
  load: loadProfile,
  saveProfile,
} = useProfileAccount()

// 默认地址：与「收货地址」分区共用同一份 useState 数据，不额外发请求
const { addresses } = useAddressBook()

const email = computed(() => String(userInfo.value?.email || ''))
const phone = computed(() => String(userInfo.value?.phone || '').trim())

/** 默认地址：优先后端标记的 isDefault，没有标记时退回第一条（与下单页取默认地址的口径一致） */
const defaultAddress = computed<AddressVO | null>(() => {
  const list = addresses.value ?? []
  return list.find(item => item.isDefault === true) ?? list[0] ?? null
})

/** 账户概览三个格子（全部真实数据，全部可点直达；icon 只做视觉锚点，容器已 aria-hidden） */
const overviewTiles = computed<{ key: PanelTabKey; label: string; value: string; icon: string }[]>(() => [
  { key: 'orders', label: 'shop.profileOrders', value: String(props.orderTotal ?? 0), icon: 'lucide:package' },
  { key: 'favorites', label: 'shop.profileFavorites', value: String(props.favoriteTotal ?? 0), icon: 'lucide:heart' },
  { key: 'addresses', label: 'shop.profileAddresses', value: String(props.addressTotal ?? 0), icon: 'lucide:map-pin' },
])

// ---------------------------------------------------------------------------
// 编辑模式
// ---------------------------------------------------------------------------

const form = reactive({ realname: '', phone: '' })
/** 用户是否动过表单：动过之后就不要再被 userInfo 的变化覆盖，避免正在输入的内容被冲掉 */
const formTouched = ref(false)

function syncForm() {
  form.realname = userInfo.value?.realname ?? ''
  form.phone = userInfo.value?.phone ?? ''
}

function markTouched() {
  formTouched.value = true
}

// immediate：SSR 时 userInfo 为 null；客户端 init-auth 插件恢复登录态后，
// 这个 watch 会再跑一次把真实资料填进表单
watch(() => userInfo.value, () => {
  if (!formTouched.value) syncForm()
}, { immediate: true })

/**
 * 进入编辑模式时**每次都从当前真实资料重新填一遍**：
 * 上一次改了一半就取消的草稿，不能带到下一次进来。
 */
watch(() => props.editing, (editing) => {
  if (editing) {
    formTouched.value = false
    syncForm()
  }
})

const dirty = computed(() => {
  const current = userInfo.value
  if (!current) return false
  return form.realname.trim() !== (current.realname ?? '')
    || form.phone.trim() !== (current.phone ?? '')
})

/** 昵称为空时后端会拒绝（空昵称会让资料卡显示成通用称呼），前端先给出字段级提示 */
const realnameInvalid = computed(() => form.realname.trim().length === 0)

/**
 * 保存按钮是否可用。
 * 单独抽成 computed：模板里既要用它做 disabled，又要用它切换「brand / ghost」两套配色，
 * 两处分别写 `!dirty || realnameInvalid` 迟早会漂移。
 */
const canSaveProfile = computed(() => dirty.value && !realnameInvalid.value)

/** 取消编辑：丢弃改动、退回只读（页面的 `editing` 是唯一开关，这里只 emit 回去） */
function cancelEdit() {
  formTouched.value = false
  syncForm()
  emit('update:editing', false)
}

/**
 * 保存资料 —— **真写库**（POST /api/auth/profile/update）。
 *
 * 成功后后端会把最新资料回读一遍返回，composable 用它更新共享的 userInfo，
 * 于是顶栏昵称、资料卡、只读模式里的昵称立刻同步，不需要刷新页面。
 * 同时退出编辑模式：保存完应当"看到结果"，而不是继续停在输入框上。
 */
async function handleSave() {
  if (!canSaveProfile.value || saving.value) return
  try {
    await saveProfile({ realname: form.realname.trim(), phone: form.phone.trim() })
    formTouched.value = false
    toast.add({ title: t('shop.profileSaveSuccess'), color: 'success', icon: 'lucide:check' })
    emit('update:editing', false)
  } catch (error) {
    // 401/403 = 登录态已失效：交给全局会话守卫统一清理登录态、提示并跳登录页，
    // 这里再弹一条"保存失败"只会让用户同时看到两条互相矛盾的提示。
    if (isUnauthorizedError(error)) return
    toast.add({
      title: t('profile.profileSaveFailed'),
      // 后端原话（如"该手机号已被其他账号使用"）比一句"保存失败"有用得多
      description: readErrorMessage(error, t('profile.profileSaveFailed')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  }
}

// ---------------------------------------------------------------------------
// 账户密码
// ---------------------------------------------------------------------------

/** 密码表单默认收起：只读模式下页面不该出现任何输入框 */
const passwordFormOpen = ref(false)

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
    // 改完密码把表单收回去：只读模式下不该留着一堆空输入框
    passwordFormOpen.value = false
    toast.add({
      title: wasSettingPassword ? t('profile.passwordSetSuccess') : t('profile.passwordSaved'),
      color: 'success',
      icon: 'lucide:check',
    })
  } catch (error) {
    // 401/403 = 登录态已失效：交给全局会话守卫统一清理登录态、提示并跳登录页，
    // 这里再弹一条"保存失败"只会让用户同时看到两条互相矛盾的提示。
    if (isUnauthorizedError(error)) return
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
