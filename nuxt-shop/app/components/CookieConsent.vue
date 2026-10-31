<template>
  <!--
    Cookie 同意横幅（Phase 1）。

    行为契约（§1.2 / §10）：
      - 首次访问展示；同意/拒绝后写入 localStorage，之后不再弹出；
      - 与 GA4 **完全解耦**：不做 Consent Mode，也不因同意/拒绝控制 GA4 加载；
      - 文案走 i18n（en / zh / fr），不硬编码。
  -->
  <Transition name="fade">
    <div
      v-if="visible"
      role="dialog"
      aria-modal="false"
      :aria-label="t('cookieConsent.title')"
      class="fixed inset-x-0 bottom-0 z-50 px-3 pb-3 sm:px-4 sm:pb-4"
    >
      <div
        class="ui-card mx-auto flex w-full max-w-3xl flex-col gap-3 border border-[var(--color-line)] p-4 sm:flex-row sm:items-center sm:justify-between sm:gap-6 sm:p-5"
      >
        <p class="text-[13px] leading-relaxed text-[var(--color-ink-soft)]">
          {{ t('cookieConsent.message') }}
        </p>

        <div class="flex shrink-0 items-center gap-2">
          <button
            type="button"
            class="ui-pill ui-pill-ghost h-9 px-4 text-[13px]"
            @click="choose('declined')"
          >
            {{ t('cookieConsent.decline') }}
          </button>
          <button
            type="button"
            class="ui-pill ui-pill-brand h-9 px-5 text-[13px]"
            @click="choose('accepted')"
          >
            {{ t('cookieConsent.accept') }}
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
/**
 * Cookie 同意横幅。
 *
 * 只读/写一个 localStorage 键（ck_cookie_consent），取值 accepted / declined。
 * SSR 阶段 visible 恒为 false（服务端不读 localStorage），水合后 onMounted 再决定是否展示，
 * 避免 hydration 不一致。
 */
const STORAGE_KEY = 'ck_cookie_consent'
type ConsentChoice = 'accepted' | 'declined'

const { t } = useI18n()
const visible = ref(false)

function choose(choice: ConsentChoice): void {
  visible.value = false
  try {
    localStorage.setItem(STORAGE_KEY, choice)
  } catch {
    // localStorage 被禁用/满时静默：下次访问重新询问即可，不阻塞交互
  }
}

onMounted(() => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    visible.value = stored !== 'accepted' && stored !== 'declined'
  } catch {
    // 读不到 localStorage 时不展示，也不影响页面
    visible.value = false
  }
})
</script>
