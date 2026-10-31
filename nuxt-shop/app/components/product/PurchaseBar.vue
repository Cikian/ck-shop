<!--
  商品信息面板底部区块：数量 → 合计

  口径：docs/商品页布局与底部动作条-设计说明.md 2.6
  原 PurchaseBar（数量 + 三个按钮 + 合计 + 服务标签）拆分后：
    · **三个按钮** → 新的固定底部动作条 components/product/StickyActionBar.vue；
    · 数量步进器 / 合计 → 留在右侧信息面板底部（本组件）。

  2026-10 设计稿精简：数量下方的两行辅助信息（"支持加入购物车…"提示 +
  品质优选 / 包邮 / 七天无理由 服务标签）整体移除，
  底部只保留可操作的数量与合计，动作条上的按钮本身已经说明了能做什么。

  组件仍是受控的：数量与加载态由页面持有（与规格选择、后端关系耦合），
  本组件只渲染并抛回数量变化。
-->
<template>
  <div class="mt-7">
    <!-- 数量：右列底部、动作条上方（验收 #10） -->
    <div class="flex items-center justify-between gap-3">
      <span class="text-sm font-bold text-[var(--color-ink)]">{{ t('product.quantity') }}</span>
      <div class="flex h-11 shrink-0 items-center rounded-[var(--radius-button)] border border-[var(--color-line)] bg-white">
        <button
          type="button"
          class="flex h-11 w-11 cursor-pointer items-center justify-center rounded-l-[var(--radius-button)] text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-[var(--color-ink-soft)] motion-reduce:transition-none"
          :disabled="quantity <= 1"
          :aria-label="t('product.decreaseQuantity')"
          @click="emit('change-quantity', -1)"
        >
          <UIcon name="lucide:minus" class="h-4 w-4" aria-hidden="true" />
        </button>
        <span class="w-11 text-center text-base font-bold tabular text-[var(--color-ink)]">{{ quantity }}</span>
        <button
          type="button"
          class="flex h-11 w-11 cursor-pointer items-center justify-center rounded-r-[var(--radius-button)] text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-[var(--color-ink-soft)] motion-reduce:transition-none"
          :disabled="quantity >= maxQuantity"
          :aria-label="t('product.increaseQuantity')"
          @click="emit('change-quantity', 1)"
        >
          <UIcon name="lucide:plus" class="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    </div>

    <!-- 合计（不可售时整行不渲染） -->
    <div v-if="buyable" class="mt-3 flex items-center justify-between rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)] px-4 py-3">
      <span class="text-sm text-[var(--color-muted)]">{{ t('product.totalItems', { count: quantity }) }}</span>
      <span class="text-lg font-black tabular text-[var(--color-brand-600)]">{{ totalPrice }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  /** 当前数量（受控，父组件持有） */
  quantity: number
  /** 上限 = 可售库存 */
  maxQuantity: number
  /** 合计金额（已格式化） */
  totalPrice: string
  /** 是否显示合计条（不可售时不显示） */
  buyable: boolean
}>()

const emit = defineEmits<{
  (e: 'change-quantity', delta: number): void
}>()

const { t } = useI18n()

</script>
