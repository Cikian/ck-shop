<script setup lang="ts">
import type { NuxtError } from '#app'

/**
 * 全局错误页。
 *
 * 之前项目里没有 app/error.vue，404/502/504 会落到 Nuxt 内置的英文白底裸页：
 * 没有 Header、没有品牌、没有语言切换、也没有回首页的入口，
 * 用户点开一个失效链接后基本只能自己改地址栏。
 *
 * 这一版保持"轻"：不渲染完整 Header（错误页可能发生在会话/数据都不可用的时刻），
 * 只给品牌标识 + 状态码 + 一句人话 + 两个明确动作。
 */
const props = defineProps<{ error: NuxtError }>()

const { t } = useI18n()

const statusCode = computed(() => Number(props.error?.statusCode) || 500)
const isNotFound = computed(() => statusCode.value === 404)

const title = computed(() =>
  isNotFound.value
    ? t('error.notFoundTitle', '这个页面走丢了')
    : t('error.serverTitle', '服务暂时开小差'),
)

const description = computed(() =>
  isNotFound.value
    ? t('error.notFoundDesc', '链接可能已失效或被删除，回到首页继续逛逛吧。')
    : t('error.serverDesc', '我们正在恢复，稍后重试或先回首页看看。'),
)

/** 错误页本身不该被搜索引擎收录 */
useSeoMeta({ robots: 'noindex, nofollow' })

function goHome() {
  clearError({ redirect: '/' })
}

function retry() {
  clearError({ redirect: useRoute().fullPath })
}
</script>

<template>
  <div class="flex min-h-dvh flex-col items-center justify-center bg-[var(--color-cream)] px-4 py-16">
    <div class="ui-card w-full max-w-md px-6 py-10 text-center sm:px-10">
      <div class="mx-auto flex items-center justify-center gap-2">
        <span class="brand-gradient flex h-10 w-10 items-center justify-center rounded-[12px] text-white">
          <UIcon name="lucide:zap" class="h-5 w-5" aria-hidden="true" />
        </span>
        <span class="text-[17px] font-black tracking-tight text-[var(--color-ink)]">
          {{ t('shop.brandName', 'Cikian Shop') }}
        </span>
      </div>

      <p class="price mt-8 text-[56px] leading-none" aria-hidden="true">
        {{ statusCode }}
      </p>

      <h1 class="mt-4 text-xl font-black text-[var(--color-ink)]">
        {{ title }}
      </h1>
      <p class="mt-2 text-sm leading-relaxed text-[var(--color-muted)]">
        {{ description }}
      </p>

      <div class="mt-8 flex flex-col gap-3 sm:flex-row sm:justify-center">
        <button type="button" class="ui-pill ui-pill-brand h-11 px-6 text-sm" @click="goHome">
          <UIcon name="lucide:home" class="h-4 w-4" aria-hidden="true" />
          {{ t('error.backHome', '回到首页') }}
        </button>
        <button
          v-if="!isNotFound"
          type="button"
          class="ui-pill ui-pill-ghost h-11 px-6 text-sm"
          @click="retry"
        >
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
          {{ t('error.retry', '重新加载') }}
        </button>
      </div>
    </div>
  </div>
</template>
