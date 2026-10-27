<!--
  「更换头像」弹层

  两件事：
   1. 上传本机图片 —— 选图后先本地预览，确认再保存；真正上传时由 BFF 用 sharp
      裁成 512×512 的正方形 webp（前端头像框是正圆，非正方形图直接被裁会切掉主体）。
   2. 选预设头像 —— 8 张站内插画，点一下直接生效（不需要再点保存）。

  为什么预设头像走「后端白名单」而不是前端自由传 URL：
  任意 URL 会让头像变成可以指向第三方域名的追踪像素，也可能随时被替换成违规图片。
  白名单在后端 ShopAccountConstant.PRESET_AVATARS，本组件的 PRESET_AVATARS 必须与之逐字一致。

  数据边界：写接口与状态都在 composables/useProfileAccount.ts，
  这里只负责"选图 → 预览 → 调用 → 提示"。
-->
<template>
  <UModal
    :open="open"
    :title="t('profile.avatarTitle')"
    :description="t('profile.avatarDesc')"
    :ui="{ content: 'rounded-[var(--radius-card)]' }"
    @update:open="emit('update:open', $event)"
  >
    <template #body>
      <div class="space-y-6">
        <!-- ===== 上传本机图片 ===== -->
        <section>
          <h3 class="flex items-center gap-2 text-[13px] font-black text-[var(--color-ink)]">
            <UIcon name="lucide:upload" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
            {{ t('profile.avatarUpload') }}
          </h3>

          <div class="mt-3 flex items-center gap-4">
            <!-- 预览：选了图显示本地预览（object URL），没选就显示当前头像 -->
            <span class="relative flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-full border-2 border-white bg-[var(--color-surface-secondary)] shadow-[0_2px_10px_rgba(31,26,28,0.12)]">
              <img
                v-if="previewUrl || currentAvatar"
                :src="previewUrl || currentAvatar"
                alt=""
                class="h-full w-full object-cover"
                @error="previewFailed = true"
              >
              <span v-else-if="avatarInitial" class="text-2xl font-black text-[var(--color-brand-500)]" aria-hidden="true">
                {{ avatarInitial }}
              </span>
              <UIcon v-else name="lucide:user" class="h-8 w-8 text-[var(--color-brand-300)]" aria-hidden="true" />
            </span>

            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <label
                  for="avatar-file-input"
                  class="ui-pill ui-pill-ghost h-9 cursor-pointer px-4 text-[13px]"
                >
                  <UIcon name="lucide:image-plus" class="h-4 w-4" aria-hidden="true" />
                  {{ t('profile.avatarChooseFile') }}
                </label>
                <button
                  v-if="pickedFile"
                  type="button"
                  class="cursor-pointer text-[13px] font-medium text-[var(--color-muted)] underline underline-offset-2 transition-colors hover:text-[var(--color-ink-soft)] motion-reduce:transition-none"
                  :disabled="saving"
                  @click="clearPicked"
                >
                  {{ t('common.cancel') }}
                </button>
              </div>
              <input
                id="avatar-file-input"
                ref="fileInput"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                class="sr-only"
                @change="onFileChange"
              >
            </div>
          </div>

          <!-- 选了图才出现"保存头像"：没选就上传等于空请求 -->
          <div v-if="pickedFile" class="mt-3 flex items-center gap-3">
            <AsyncButton
              type="button"
              class="ui-pill ui-pill-brand h-9 px-5 text-[13px] disabled:cursor-not-allowed disabled:opacity-45"
              :pending="saving"
              :pending-label="t('profile.avatarUploading')"
              @click="savePicked"
            >
              <UIcon name="lucide:check" class="h-4 w-4" aria-hidden="true" />
              {{ t('profile.avatarSave') }}
            </AsyncButton>
            <span class="truncate text-[12px] text-[var(--color-muted)]">{{ pickedFileName }}</span>
          </div>
        </section>

        <!-- ===== 预设头像 ===== -->
        <section class="border-t border-[var(--color-line-soft)] pt-5">
          <h3 class="flex items-center gap-2 text-[13px] font-black text-[var(--color-ink)]">
            <UIcon name="lucide:palette" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
            {{ t('profile.avatarPresets') }}
          </h3>

          <div class="mt-3 grid grid-cols-4 gap-2.5 sm:gap-3">
            <button
              v-for="preset in PRESET_AVATARS"
              :key="preset"
              type="button"
              class="group relative aspect-square cursor-pointer overflow-hidden rounded-full border-2 transition-[border-color,box-shadow] duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-500)] motion-reduce:transition-none"
              :class="isCurrentPreset(preset)
                ? 'border-[var(--color-brand-500)] shadow-[0_2px_10px_rgba(255,77,109,0.28)]'
                : 'border-transparent hover:border-[var(--color-brand-200)]'"
              :aria-pressed="isCurrentPreset(preset)"
              :aria-label="t('profile.avatarPresetLabel')"
              :disabled="saving"
              @click="choosePreset(preset)"
            >
              <img :src="preset" alt="" class="h-full w-full object-cover" loading="lazy">
              <span
                v-if="isCurrentPreset(preset)"
                class="pointer-events-none absolute inset-0 flex items-center justify-center bg-[var(--color-brand-500)]/22"
                aria-hidden="true"
              >
                <UIcon name="lucide:check" class="h-5 w-5 text-white drop-shadow" />
              </span>
            </button>
          </div>
        </section>
      </div>
    </template>

    <template #footer>
      <div class="flex w-full items-center justify-between gap-3">
        <!-- 恢复默认：清掉头像，前端回落到昵称首字母 -->
        <button
          type="button"
          class="font-inherit cursor-pointer text-[13px] font-medium text-[var(--color-muted)] underline underline-offset-2 transition-colors hover:text-[var(--color-error-600)] disabled:cursor-not-allowed disabled:opacity-45 motion-reduce:transition-none"
          :disabled="saving || !currentAvatar"
          @click="resetAvatar"
        >
          {{ t('profile.avatarReset') }}
        </button>
        <button
          type="button"
          class="cursor-pointer rounded-full border border-[var(--color-line)] px-5 py-2 text-sm font-semibold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] motion-reduce:transition-none"
          @click="emit('update:open', false)"
        >
          {{ t('common.close') }}
        </button>
      </div>
    </template>
  </UModal>
</template>

<script setup lang="ts">
import { isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'
import { useAuth } from '~~/composables/useAuth'
import { useProfileAccount } from '~~/composables/useProfileAccount'
import { useProfileFields } from '~~/composables/useProfileFields'

/**
 * 预设头像清单 —— 必须与后端 ShopAccountConstant.PRESET_AVATARS 逐字一致。
 * 文件在 nuxt-shop/public/avatars/（256×256 webp，单张 2~4KB）。
 */
const PRESET_AVATARS = [
  '/avatars/preset-1.webp',
  '/avatars/preset-2.webp',
  '/avatars/preset-3.webp',
  '/avatars/preset-4.webp',
  '/avatars/preset-5.webp',
  '/avatars/preset-6.webp',
  '/avatars/preset-7.webp',
  '/avatars/preset-8.webp',
]

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'update:open', open: boolean): void }>()

const { t } = useI18n()
const toast = useToast()
const { userInfo } = useAuth()
const { avatarInitial } = useProfileFields()
const { saving, selectAvatar, uploadAvatar } = useProfileAccount()

const fileInput = ref<HTMLInputElement | null>(null)
const pickedFile = ref<File | null>(null)
const pickedFileName = computed(() => pickedFile.value?.name || '')
const previewUrl = ref('')
const previewFailed = ref(false)

const currentAvatar = computed(() => String(userInfo.value?.avatar || ''))

function isCurrentPreset(preset: string) {
  return currentAvatar.value === preset
}

/** 释放 object URL，避免反复选图后内存里堆一串 blob */
function revokePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function clearPicked() {
  revokePreview()
  pickedFile.value = null
  previewFailed.value = false
  if (fileInput.value) fileInput.value.value = ''
}

/** 每次打开弹层都从干净状态开始（上一次选的文件不该残留） */
watch(() => props.open, (open) => {
  if (open) clearPicked()
})

// 组件卸载（路由切走）时也要释放
onBeforeUnmount(revokePreview)

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // 前端先拦一道：类型/体积不对就不必等一次往返。
  // （后端与 BFF 各还有一层校验，这里只是省时间）
  if (!/^image\/(jpeg|png|webp)$/i.test(file.type)) {
    toast.add({ title: t('profile.avatarInvalidType'), color: 'warning', icon: 'lucide:circle-alert' })
    input.value = ''
    return
  }
  if (file.size > 8 * 1024 * 1024) {
    toast.add({ title: t('profile.avatarTooLarge'), color: 'warning', icon: 'lucide:circle-alert' })
    input.value = ''
    return
  }

  revokePreview()
  pickedFile.value = file
  previewFailed.value = false
  previewUrl.value = URL.createObjectURL(file)
}

/** 保存上传的图片：成功即关闭弹层（头像已经在资料卡上换好了） */
async function savePicked() {
  const file = pickedFile.value
  if (!file || saving.value) return
  try {
    await uploadAvatar(file)
    clearPicked()
    toast.add({ title: t('profile.avatarSaved'), color: 'success', icon: 'lucide:check' })
    emit('update:open', false)
  } catch (error) {
    if (isUnauthorizedError(error)) return
    toast.add({
      title: t('profile.avatarSaveFailed'),
      description: readErrorMessage(error, t('profile.avatarSaveFailed')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  }
}

/** 选预设头像：点一下直接生效 */
async function choosePreset(preset: string) {
  if (saving.value) return
  if (isCurrentPreset(preset)) {
    emit('update:open', false)
    return
  }
  try {
    await selectAvatar(preset)
    toast.add({ title: t('profile.avatarSaved'), color: 'success', icon: 'lucide:check' })
    emit('update:open', false)
  } catch (error) {
    if (isUnauthorizedError(error)) return
    toast.add({
      title: t('profile.avatarSaveFailed'),
      description: readErrorMessage(error, t('profile.avatarSaveFailed')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  }
}

/** 恢复默认头像（后端写 NULL，前端回落到首字母占位） */
async function resetAvatar() {
  if (saving.value) return
  try {
    await selectAvatar('')
    toast.add({ title: t('profile.avatarResetDone'), color: 'success', icon: 'lucide:check' })
    emit('update:open', false)
  } catch (error) {
    if (isUnauthorizedError(error)) return
    toast.add({
      title: t('profile.avatarSaveFailed'),
      description: readErrorMessage(error, t('profile.avatarSaveFailed')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  }
}
</script>
