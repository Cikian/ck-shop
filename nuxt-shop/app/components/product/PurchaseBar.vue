<!--
  商品信息面板底部区块：数量 → 合计 → 加购提示 → 服务标签

  口径：docs/商品页布局与底部动作条-设计说明.md 2.6
  原 PurchaseBar（数量 + 三个按钮 + 合计 + 服务标签）拆分后：
    · **三个按钮** → 新的固定底部动作条 components/product/StickyActionBar.vue；
    · 数量步进器 / 合计 / 提示 / 服务标签 → 留在右侧信息面板底部（本组件）。

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

    <!-- 加购提示 + 查看购物车 -->
    <p class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[var(--color-muted)]">
      <span class="inline-flex items-center gap-1">
        <UIcon name="lucide:check" class="h-3.5 w-3.5 text-[var(--color-success)]" aria-hidden="true" />
        {{ t('product.cartHint') }}
      </span>
      <NuxtLinkLocale
        to="/cart"
        class="inline-flex items-center gap-1 font-bold text-[var(--color-brand-600)] transition-colors hover:text-[var(--color-brand-700)]"
      >
        <UIcon name="lucide:shopping-bag" class="h-3.5 w-3.5" aria-hidden="true" />
        {{ t('product.viewCart') }}
      </NuxtLinkLocale>
    </p>

    <!-- 服务标签：只认真实 tag code（FAST/TOP）与 freeShipping 字段；
         七天无理由是本店对外的统一退货承诺（SiteFooter 也在打同一句），不是商品数据 -->
    <div class="mt-5 flex flex-wrap gap-2 border-t border-[var(--color-line-soft)] pt-4">
      <span v-if="hasFastTag" class="tag-service">
        <UIcon name="lucide:zap" class="h-3.5 w-3.5" aria-hidden="true" />
        {{ t('shop.serviceFast') }}
      </span>
      <span v-if="hasTopTag" class="tag-service">
        <UIcon name="lucide:award" class="h-3.5 w-3.5" aria-hidden="true" />
        {{ t('shop.serviceTop') }}
      </span>
      <span v-if="freeShipping" class="tag-service">
        <UIcon name="lucide:truck" class="h-3.5 w-3.5" aria-hidden="true" />
        {{ t('shop.freeShipping') }}
      </span>
      <span class="tag-service tag-service-neutral">
        <UIcon name="lucide:rotate-ccw" class="h-3.5 w-3.5" aria-hidden="true" />
        {{ t('shop.serviceReturn') }}
      </span>
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
  hasFastTag: boolean
  hasTopTag: boolean
  freeShipping: boolean
}>()

const emit = defineEmits<{
  (e: 'change-quantity', delta: number): void
}>()

const { t } = useI18n()

</script>
