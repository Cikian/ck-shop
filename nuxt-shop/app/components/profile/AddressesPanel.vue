<!--
  收货地址面板（含新增/编辑弹层与删除二次确认）

  从 pages/profile.vue 拆出（P1-4 巨型文件拆分第三步）。
  数据与接口在 composables/useAddressBook.ts（useState 共享，页面与面板同一份数据）；
  本组件只负责：表单字段、弹层开关、必填校验，以及把提交动作接到数据层。
-->
<template>
  <div>
    <div class="mt-4 flex justify-end">
      <button type="button" class="ui-pill ui-pill-brand h-10 shrink-0 px-5 text-[13px]" @click="openCreate">
        <UIcon name="lucide:plus" class="h-4 w-4" aria-hidden="true" />
        {{ t('address.add') }}
      </button>
    </div>

    <!-- 加载失败 -->
    <div v-if="error" class="mt-4 flex items-start gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-4 py-3 text-[13px] font-medium text-[var(--color-error-600)]">
      <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <span class="min-w-0 flex-1">
        {{ t('address.loadError') }}：{{ error }}
        <button
          type="button"
          class="ml-2 cursor-pointer font-bold underline underline-offset-2 disabled:opacity-45"
          :disabled="loading"
          @click="load()"
        >
          {{ t('common.retry') }}
        </button>
      </span>
    </div>

    <!-- 加载中：骨架屏 -->
    <ul v-else-if="loading && !addresses.length" class="mt-4 space-y-3" aria-busy="true" :aria-label="t('common.loading')">
      <li v-for="index in 3" :key="`addr-skeleton-${index}`" class="space-y-2.5 rounded-[var(--radius-card-sm)] bg-[var(--color-line-soft)]/50 p-4">
        <span class="block h-4 w-1/4 animate-pulse rounded bg-[var(--color-line-soft)]"></span>
        <span class="block h-3 w-2/3 animate-pulse rounded bg-[var(--color-line-soft)]"></span>
      </li>
    </ul>

    <!-- 有地址 -->
    <ul v-else-if="addresses.length" class="mt-4 space-y-3">
      <li
        v-for="address in addresses"
        :key="address.id"
        class="rounded-[var(--radius-card-sm)] border border-[var(--color-line-soft)] p-4"
      >
        <div class="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
          <div class="min-w-0 flex-1">
            <p class="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span class="text-[14px] font-bold text-[var(--color-ink)]">{{ address.receiverName || '—' }}</span>
              <!-- 手机号是否脱敏由后端决定，前端不加工 -->
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
            <p v-if="isPositive(address.useCount)" class="mt-1.5 text-[12px] tabular text-[var(--color-muted)]">
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

    <!-- 真的没有地址 -->
    <div v-else class="flex flex-col items-center justify-center py-14 text-center">
      <span class="flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-brand-50)] text-[var(--color-brand-400)]">
        <UIcon name="lucide:map-pin-off" class="h-9 w-9" aria-hidden="true" />
      </span>
      <p class="mt-5 text-sm font-bold text-[var(--color-ink)]">
        {{ t('address.empty') }}
      </p>
      <p class="mt-2 max-w-md text-[13px] text-[var(--color-muted)]">
        {{ t('address.emptyDesc') }}
      </p>
      <button type="button" class="ui-pill ui-pill-brand mt-5 h-10 px-6 text-[13px]" @click="openCreate">
        <UIcon name="lucide:plus" class="h-4 w-4" aria-hidden="true" />
        {{ t('address.add') }}
      </button>
    </div>

    <!-- 地址新增 / 编辑弹层（字段口径与 /address 页一致） -->
    <UModal
      v-model:open="formState.open"
      :title="formState.id ? t('address.editTitle') : t('address.addTitle')"
      :ui="{ content: 'rounded-[var(--radius-card)]' }"
      @update:open="onFormOpenChange"
    >
      <template #body>
        <form class="grid gap-4 sm:grid-cols-2" @submit.prevent="submitForm">
          <div>
            <label for="profile-address-name" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.receiverName') }}
            </label>
            <input
              id="profile-address-name"
              v-model="form.receiverName"
              type="text"
              autocomplete="name"
              :placeholder="t('address.namePlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div>
            <label for="profile-address-phone" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.receiverPhone') }}
            </label>
            <input
              id="profile-address-phone"
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
            <label for="profile-address-detail" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.detailAddress') }}
            </label>
            <input
              id="profile-address-detail"
              v-model="form.detailAddress"
              type="text"
              autocomplete="street-address"
              :placeholder="t('address.detailPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div>
            <label for="profile-address-postal" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.postalCode') }}
            </label>
            <input
              id="profile-address-postal"
              v-model="form.postalCode"
              type="text"
              autocomplete="postal-code"
              :placeholder="t('address.postalPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div>
            <label for="profile-address-tag" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.addressTag') }}
            </label>
            <input
              id="profile-address-tag"
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

    <!-- 删除地址二次确认（删除不可撤销，必须让用户再点一次） -->
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
  </div>
</template>

<script setup lang="ts">
import type { AddressVO } from '~~/types/shop-address'
import { isPositive } from '~~/composables/useApiError'
import { useAddressBook } from '~~/composables/useAddressBook'

const { t, locale } = useI18n()
const toast = useToast()

const { addresses, loading, error, mutating, load, fetchOne, save, setDefault, remove, notifyError } = useAddressBook()

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
 * 打开编辑弹层：先用列表数据把表单填上（不阻塞用户），
 * 再拉一次**单条详情**覆盖 —— 用户在别的标签页改过这条地址时，看到的是改后的值。
 */
async function openEdit(address: AddressVO) {
  resetForm()
  formState.pending = false
  formState.open = true
  fillForm(address)
  const fresh = await fetchOne(address.id)
  if (fresh?.id && formState.id === fresh.id) fillForm(fresh)
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
    await save(formState.id, {
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
    })
    formState.open = false
  } catch (err) {
    notifyError(err)
  } finally {
    formState.pending = false
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
    await remove(deleteState.id)
    deleteState.open = false
  } finally {
    deleteState.pending = false
  }
}
</script>
