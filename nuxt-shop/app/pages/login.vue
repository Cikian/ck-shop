<template>
  <div class="max-w-sm mx-auto p-6 mt-10">
    <h1 class="text-xl font-bold mb-4">{{ t('login.title') }}</h1>

    <form @submit.prevent="handleLogin" class="flex flex-col gap-3">
      <SfInput
        v-model="form.username"
        :placeholder="t('login.username')"
        required
      />
      <SfInput
        v-model="form.password"
        :placeholder="t('login.password')"
        type="password"
        required
      />

      <!-- 验证码：输入框 + 图片，点图片刷新 -->
      <div class="flex gap-2 items-stretch">
        <SfInput
          v-model="form.captcha"
          :placeholder="t('login.captcha')"
          required
          class="flex-1"
        />
        <img
          v-if="imageBase64"
          :src="imageBase64"
          :alt="t('login.captcha')"
          class="h-10 cursor-pointer border rounded"
          :title="t('login.refreshCaptcha')"
          @click="refreshCaptcha"
        />
        <div
          v-else
          class="h-10 w-24 flex items-center justify-center text-xs text-neutral-400 border rounded"
        >
          ...
        </div>
      </div>

      <SfButton type="submit" :disabled="loading">
        {{ loading ? t('login.loading') : t('login.submit') }}
      </SfButton>

      <p v-if="errorMsg" class="text-red-500 text-sm">{{ errorMsg }}</p>
    </form>
  </div>
</template>

<script setup lang="ts">
import { SfButton, SfInput } from '@storefront-ui/vue'
import {useAuth} from "~~/composables/useAuth";
import {useCaptcha} from "~~/composables/useCaptcha";

const { t } = useI18n()
const { login } = useAuth()
const { imageBase64, checkKey, refreshCaptcha } = useCaptcha()

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
    await navigateTo('/profile')
  } catch (e: any) {
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
