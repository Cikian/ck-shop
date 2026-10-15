<!--
  「个人信息」面板：只列真实存在的字段

  从 pages/profile.vue 拆出（P1-4 巨型文件拆分第五步，最后一个面板）。
  字段派生在 composables/useProfileFields.ts —— 左侧资料卡与本面板共用同一套口径
  （侧栏要多显示头像首字母与联系方式，本面板把它列成明细）。

  空字段整行不渲染，真的一个字段都没有时才显示「暂无数据」——绝不写死假资料。
-->
<template>
  <div>
    <dl v-if="infoRows.length" class="mt-5 grid gap-3 sm:grid-cols-2">
      <div
        v-for="row in infoRows"
        :key="row.label"
        class="min-w-0 rounded-[var(--radius-card-sm)] bg-[var(--color-line-soft)] px-4 py-3"
      >
        <dt class="flex items-center gap-2 text-[11px] font-bold uppercase tracking-wider text-[var(--color-muted)]">
          <UIcon :name="row.icon" class="h-3.5 w-3.5 shrink-0 text-[var(--color-brand-500)]" aria-hidden="true" />
          {{ t(row.label) }}
        </dt>
        <dd class="mt-1 truncate text-sm font-medium text-[var(--color-ink)]">
          {{ row.value }}
        </dd>
      </div>
    </dl>
    <p v-else class="mt-5 text-sm text-[var(--color-muted)]">
      {{ t('shop.noData') }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { useProfileFields } from '~~/composables/useProfileFields'

const { t } = useI18n()
const { infoRows } = useProfileFields()
</script>
