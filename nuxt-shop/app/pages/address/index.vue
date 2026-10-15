<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto max-w-4xl px-2 pb-16 sm:px-3 lg:px-4">
      <header class="mb-5 flex flex-wrap items-end justify-between gap-x-4 gap-y-2">
        <div>
          <h1 class="text-[26px] font-black leading-none text-[var(--color-ink)] sm:text-[32px]">
            {{ t('address.title') }}
          </h1>
          <p class="mt-2 text-[13px] text-[var(--color-muted)]">{{ t('address.subtitle') }}</p>
        </div>
        <button
          v-if="!unauthorized"
          type="button"
          class="ui-pill ui-pill-brand h-11 shrink-0 px-5 text-[13px]"
          @click="openCreate"
        >
          <UIcon name="lucide:plus" class="h-4 w-4" aria-hidden="true" />
          {{ t('address.add') }}
        </button>
      </header>

      <!-- 未登录 -->
      <section v-if="unauthorized" class="ui-card px-6 py-16 text-center">
        <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
          <UIcon name="lucide:lock" class="h-8 w-8 text-[var(--color-brand-500)]" aria-hidden="true" />
        </span>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('address.loginRequired') }}</h2>
        <p class="mx-auto mt-2 max-w-md text-sm text-[var(--color-muted)]">{{ t('address.loginRequiredDesc') }}</p>
        <NuxtLinkLocale :to="loginLink" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm">
          <UIcon name="lucide:log-in" class="h-4 w-4" aria-hidden="true" />
          {{ t('address.loginAction') }}
        </NuxtLinkLocale>
      </section>

      <!-- 加载失败 -->
      <section v-else-if="loadError" class="ui-card px-6 py-16 text-center">
        <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error)]/10">
          <UIcon name="lucide:wifi-off" class="h-8 w-8 text-[var(--color-error)]" aria-hidden="true" />
        </span>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('address.loadError') }}</h2>
        <p class="mt-2 text-sm text-[var(--color-muted)]">{{ loadError }}</p>
        <button type="button" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm" @click="loadAddresses()">
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
          {{ t('common.retry') }}
        </button>
      </section>

      <!-- 骨架屏 -->
      <section v-else-if="loading && !addresses.length" class="space-y-4" aria-busy="true" :aria-label="t('common.loading')">
        <div v-for="index in 3" :key="index" class="ui-card space-y-3 p-5">
          <div class="h-4 w-1/4 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
          <div class="h-3 w-2/3 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
        </div>
      </section>

      <!-- 空态 -->
      <section v-else-if="!addresses.length" class="ui-card px-6 py-20 text-center">
        <span class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
          <UIcon name="lucide:map-pin-off" class="h-9 w-9 text-[var(--color-brand-400)]" aria-hidden="true" />
        </span>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('address.empty') }}</h2>
        <p class="mx-auto mt-2 max-w-md text-sm text-[var(--color-muted)]">{{ t('address.emptyDesc') }}</p>
        <button type="button" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm" @click="openCreate">
          <UIcon name="lucide:plus" class="h-4 w-4" aria-hidden="true" />
          {{ t('address.add') }}
        </button>
      </section>

      <!-- 列表 -->
      <ul v-else class="space-y-4">
        <li v-for="address in addresses" :key="address.id" class="ui-card p-5">
          <div class="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
            <div class="min-w-0 flex-1">
              <p class="flex flex-wrap items-center gap-x-2 gap-y-1">
                <span class="text-[15px] font-bold text-[var(--color-ink)]">{{ address.receiverName || '—' }}</span>
                <!-- 手机号由后端决定是否脱敏（买家自己的地址簿不脱敏），前端不加工 -->
                <span class="text-[13px] tabular text-[var(--color-ink-soft)]">{{ address.receiverPhone || '' }}</span>
                <span
                  v-if="address.isDefault"
                  class="rounded-full bg-[var(--color-brand-500)] px-2 py-0.5 text-[10px] font-bold text-white"
                >
                  {{ t('address.defaultTag') }}
                </span>
                <span
                  v-if="address.addressTag"
                  class="rounded-full bg-[var(--color-line-soft)] px-2 py-0.5 text-[10px] font-bold text-[var(--color-ink-soft)]"
                >
                  {{ address.addressTag }}
                </span>
              </p>
              <!-- fullAddress 由后端拼好，前端不再各处拼一遍 -->
              <p class="mt-1.5 text-[13px] leading-relaxed text-[var(--color-muted)]">
                {{ address.fullAddress || '—' }}
              </p>
              <!-- 使用次数：有真实数据才显示，没有就不显示（不写死 0 次的噪音） -->
              <p v-if="isPositiveCount(address.useCount)" class="mt-1.5 text-[12px] tabular text-[var(--color-muted)]">
                {{ t('address.useCount', { count: address.useCount }) }}
              </p>
            </div>

            <div class="flex shrink-0 flex-wrap items-center gap-2">
              <button
                v-if="!address.isDefault"
                type="button"
                class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[12px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-brand-500)] hover:text-[var(--color-brand-600)] disabled:opacity-40 motion-reduce:transition-none"
                :disabled="mutating"
                @click="setDefault(address.id)"
              >
                {{ t('address.setDefault') }}
              </button>
              <button
                type="button"
                class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[12px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-brand-500)] hover:text-[var(--color-brand-600)] disabled:opacity-40 motion-reduce:transition-none"
                :disabled="mutating"
                @click="openEdit(address)"
              >
                {{ t('common.edit') }}
              </button>
              <button
                type="button"
                class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[12px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-error)] hover:text-[var(--color-error)] disabled:opacity-40 motion-reduce:transition-none"
                :disabled="mutating"
                @click="askDelete(address)"
              >
                {{ t('common.delete') }}
              </button>
            </div>
          </div>
        </li>
      </ul>
    </div>

    <!-- 新增 / 编辑弹层 -->
    <UModal
      v-model:open="formState.open"
      :title="formState.id ? t('address.editTitle') : t('address.addTitle')"
      :ui="{ content: 'rounded-[var(--radius-card)]' }"
      @update:open="onFormOpenChange"
    >
      <template #body>
        <form class="grid gap-4 sm:grid-cols-2" @submit.prevent="submitForm">
          <div>
            <label for="address-name" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.receiverName') }}
            </label>
            <input
              id="address-name"
              v-model="form.receiverName"
              type="text"
              autocomplete="name"
              :placeholder="t('address.namePlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div>
            <label for="address-phone" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.receiverPhone') }}
            </label>
            <input
              id="address-phone"
              v-model="form.receiverPhone"
              type="tel"
              autocomplete="tel"
              :placeholder="t('address.phonePlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div class="grid grid-cols-3 gap-3 sm:col-span-2">
            <input
              v-model="form.province"
              type="text"
              :aria-label="t('address.provincePlaceholder')"
              :placeholder="t('address.provincePlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-3 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
            <input
              v-model="form.city"
              type="text"
              :aria-label="t('address.cityPlaceholder')"
              :placeholder="t('address.cityPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-3 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
            <input
              v-model="form.district"
              type="text"
              :aria-label="t('address.districtPlaceholder')"
              :placeholder="t('address.districtPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-3 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div class="sm:col-span-2">
            <label for="address-detail" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.detailAddress') }}
            </label>
            <input
              id="address-detail"
              v-model="form.detailAddress"
              type="text"
              autocomplete="street-address"
              :placeholder="t('address.detailPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div>
            <label for="address-postal" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.postalCode') }}
            </label>
            <input
              id="address-postal"
              v-model="form.postalCode"
              type="text"
              autocomplete="postal-code"
              :placeholder="t('address.postalPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div>
            <label for="address-tag" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.addressTag') }}
            </label>
            <input
              id="address-tag"
              v-model="form.addressTag"
              type="text"
              :placeholder="t('address.tagPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <label class="flex cursor-pointer items-center gap-2.5 sm:col-span-2">
            <input v-model="form.isDefault" type="checkbox" class="h-4 w-4 cursor-pointer accent-[var(--color-brand-500)]">
            <span class="text-[13px] font-medium text-[var(--color-ink-soft)]">{{ t('address.isDefaultLabel') }}</span>
          </label>
        </form>
      </template>

      <template #footer>
        <div class="flex w-full justify-end gap-3">
          <button
            type="button"
            class="cursor-pointer rounded-full border border-[var(--color-line)] px-5 py-2.5 text-sm font-semibold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] motion-reduce:transition-none"
            :disabled="formState.pending"
            @click="formState.open = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="button"
            class="cursor-pointer rounded-full bg-[var(--color-brand-500)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
            :disabled="formState.pending"
            @click="submitForm"
          >
            {{ formState.pending ? t('address.saving') : t('common.save') }}
          </button>
        </div>
      </template>
    </UModal>

    <!-- 删除二次确认 -->
    <UModal
      v-model:open="deleteState.open"
      :title="t('address.deleteTitle')"
      :description="t('address.deleteDesc')"
      :ui="{ content: 'rounded-[var(--radius-card)]' }"
    >
      <template #footer>
        <div class="flex w-full justify-end gap-3">
          <button
            type="button"
            class="cursor-pointer rounded-full border border-[var(--color-line)] px-5 py-2.5 text-sm font-semibold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] motion-reduce:transition-none"
            @click="deleteState.open = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="button"
            class="cursor-pointer rounded-full bg-[var(--color-error)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-error-600)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
            :disabled="deleteState.pending"
            @click="confirmDelete"
          >
            {{ t('address.deleteConfirm') }}
          </button>
        </div>
      </template>
    </UModal>

    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
/** 地址簿是私有页面，未登录 / 登录过期由 auth 中间件重定向到登录页并回跳 */
definePageMeta({ middleware: 'auth' })

import { toNumberOrNull } from '~~/types/shop-product'

/**
 * 收货地址簿。
 *
 * 与订单接口的两点差异（后端 ShopStoreAddressController 的设计）：
 *   1) 地址**不按店铺隔离**：同一个人在不同店铺前台共用一份地址簿；
 *      但依然必须登录，所有操作都带 userId 过滤；
 *   2) 手机号**不脱敏**（这是买家自己的地址簿，脱敏只会让他无法核对）。
 *
 * isDefault 的三态（后端 AddressDTO 的注释）：null = 不改动当前默认状态，
 * true = 设为默认。所以提交时"没勾选"要传 null 而不是 false ——
 * 否则编辑一条非默认地址会把用户真正想保留的默认地址顺手取消掉。
 */

/** 收货地址（后端 AddressVO） */
interface AddressVO {
  id: string
  receiverName?: string | null
  receiverPhone?: string | null
  province?: string | null
  city?: string | null
  district?: string | null
  detailAddress?: string | null
  postalCode?: string | null
  addressTag?: string | null
  isDefault?: boolean | null
  masked?: boolean | null
  useCount?: number | null
  fullAddress?: string | null
}

const { t, locale } = useI18n()
const localePath = useLocalePath()
const route = useRoute()
const toast = useToast()

const loading = ref(false)
const mutating = ref(false)
const loadError = ref('')
const unauthorized = ref(false)
const addresses = ref<AddressVO[]>([])

const formState = reactive({ open: false, id: '', pending: false })
const deleteState = reactive({ open: false, pending: false, id: '' })

const form = reactive({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  postalCode: '',
  addressTag: '',
  isDefault: false,
})

const loginLink = computed(() => localePath({
  path: '/login',
  query: { redirect: route.fullPath },
}))

/** 使用次数大于 0 才显示（0 次是噪音，不是信息） */
function isPositiveCount(value: unknown): boolean {
  const num = toNumberOrNull(value)
  return num !== null && num > 0
}

/** 从 $fetch 抛出的错误里取出可读文案（后端业务异常 message 是中文，直接展示） */
function readErrorMessage(error: unknown, fallback: string): string {
  const err = error as any
  return err?.data?.statusMessage || err?.data?.message || err?.statusMessage || err?.message || fallback
}

function isUnauthorizedError(error: unknown): boolean {
  const err = error as any
  const code = err?.statusCode ?? err?.status ?? err?.data?.statusCode
  return code === 401 || code === 403
}

function notifyError(error: unknown) {
  toast.add({
    title: t('address.actionFailed'),
    description: readErrorMessage(error, t('address.loadErrorDesc')),
    color: 'error',
    icon: 'lucide:circle-alert',
  })
}

async function loadAddresses() {
  loading.value = true
  loadError.value = ''
  try {
    const list = await $fetch<AddressVO[]>('/api/address/list', { query: { lang: locale.value } })
    addresses.value = (Array.isArray(list) ? list : []).filter(item => !!item?.id)
    unauthorized.value = false
  } catch (error) {
    if (isUnauthorizedError(error)) {
      unauthorized.value = true
      return
    }
    loadError.value = readErrorMessage(error, t('address.loadErrorDesc'))
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.receiverName = ''
  form.receiverPhone = ''
  form.province = ''
  form.city = ''
  form.district = ''
  form.detailAddress = ''
  form.postalCode = ''
  form.addressTag = ''
  form.isDefault = false
}

function openCreate() {
  resetForm()
  formState.id = ''
  formState.pending = false
  formState.open = true
}

/** 把一条地址填进表单（新增/编辑共用，字段口径只有这一处） */
function fillForm(address: AddressVO) {
  form.receiverName = address.receiverName ?? ''
  form.receiverPhone = address.receiverPhone ?? ''
  form.province = address.province ?? ''
  form.city = address.city ?? ''
  form.district = address.district ?? ''
  form.detailAddress = address.detailAddress ?? ''
  form.postalCode = address.postalCode ?? ''
  form.addressTag = address.addressTag ?? ''
  // 已经是默认地址时勾上，避免用户以为"编辑一下默认就没了"
  form.isDefault = address.isDefault === true
  formState.id = address.id
}

/**
 * 打开编辑弹层。
 *
 * 先用列表里的数据把表单填上（列表已经是最新的，不阻塞用户），
 * 再向 /api/address/:id 拉一次**单条详情**覆盖表单 ——
 * 如果用户在另一个标签页改过这条地址，打开弹层时看到的会是改过之后的值，
 * 而不是一份可能过期的列表快照。详情拉不到就继续用列表数据，不打扰用户。
 */
async function openEdit(address: AddressVO) {
  resetForm()
  formState.pending = false
  formState.open = true
  fillForm(address)
  try {
    const fresh = await $fetch<AddressVO>(`/api/address/${encodeURIComponent(address.id)}`, {
      query: { lang: locale.value },
    })
    if (fresh?.id && formState.id === fresh.id) fillForm(fresh)
  } catch {
    // 静默降级：列表数据已经够用，这里不再弹一次错误
  }
}

function onFormOpenChange(open: boolean) {
  formState.open = open
  if (!open) formState.pending = false
}

/** 前端先做一次必填校验，把话说清楚（后端也会校验，这里只是少一次往返） */
function validateForm(): string {
  if (!form.receiverName.trim()) return t('address.receiverRequired')
  if (!form.receiverPhone.trim()) return t('address.phoneRequired')
  if (!form.detailAddress.trim()) return t('address.detailRequired')
  return ''
}

async function submitForm() {
  if (formState.pending) return
  const invalid = validateForm()
  if (invalid) {
    toast.add({ title: invalid, color: 'warning', icon: 'lucide:circle-alert' })
    return
  }

  formState.pending = true
  try {
    const payload = {
      receiverName: form.receiverName.trim(),
      receiverPhone: form.receiverPhone.trim(),
      province: form.province.trim(),
      city: form.city.trim(),
      district: form.district.trim(),
      detailAddress: form.detailAddress.trim(),
      postalCode: form.postalCode.trim(),
      addressTag: form.addressTag.trim(),
      // 勾选 -> true（设为默认）；未勾选 -> null（不改动默认状态，而不是"取消默认"）
      isDefault: form.isDefault ? true : null,
      lang: locale.value,
    }

    if (formState.id) {
      await $fetch('/api/address/edit', { method: 'POST', body: { ...payload, id: formState.id } })
    } else {
      await $fetch('/api/address/add', { method: 'POST', body: payload })
    }

    toast.add({ title: t('address.savedToast'), color: 'success', icon: 'lucide:check' })
    formState.open = false
    await loadAddresses()
  } catch (error) {
    if (isUnauthorizedError(error)) {
      unauthorized.value = true
      return
    }
    notifyError(error)
  } finally {
    formState.pending = false
  }
}

async function setDefault(id: string) {
  if (mutating.value) return
  mutating.value = true
  try {
    // id 走查询参数（后端方法签名没有 @RequestBody）
    await $fetch('/api/address/set-default', {
      method: 'POST',
      query: { id, lang: locale.value },
    })
    toast.add({ title: t('address.defaultSetToast'), color: 'success', icon: 'lucide:check' })
    await loadAddresses()
  } catch (error) {
    if (isUnauthorizedError(error)) {
      unauthorized.value = true
      return
    }
    notifyError(error)
  } finally {
    mutating.value = false
  }
}

function askDelete(address: AddressVO) {
  deleteState.id = address.id
  deleteState.open = true
}

async function confirmDelete() {
  if (deleteState.pending || !deleteState.id) return
  deleteState.pending = true
  try {
    await $fetch('/api/address/delete', {
      method: 'POST',
      query: { id: deleteState.id, lang: locale.value },
    })
    toast.add({ title: t('address.deletedToast'), color: 'success', icon: 'lucide:check' })
    deleteState.open = false
    await loadAddresses()
  } catch (error) {
    if (isUnauthorizedError(error)) {
      unauthorized.value = true
      return
    }
    notifyError(error)
  } finally {
    deleteState.pending = false
  }
}

onMounted(loadAddresses)

useSeoMeta({
  title: () => t('address.title'),
  robots: 'noindex, nofollow',
})
</script>
