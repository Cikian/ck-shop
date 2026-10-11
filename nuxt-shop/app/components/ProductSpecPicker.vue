<template>
  <div class="space-y-6">
    <p class="flex items-start gap-2 text-xs text-[var(--color-text-muted)]">
      <UIcon name="lucide:info" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      {{ t('products.spec.tapToClear') }}
    </p>

    <fieldset
      v-for="spec in specs"
      :key="spec.id"
      class="m-0 border-0 p-0"
    >
      <legend class="flex flex-wrap items-center gap-2 text-sm font-bold uppercase tracking-wide text-[var(--color-text-secondary)]">
        {{ spec.name }}
        <span
          v-if="spec.isRequired === 'Y'"
          class="text-[var(--color-error)]"
          aria-hidden="true"
        >*</span>
        <span v-else class="text-xs font-normal normal-case text-[var(--color-text-muted)]">
          ({{ t('products.spec.optional') }})
        </span>
        <span class="text-xs font-normal normal-case text-[var(--color-text-muted)]">
          {{ selectedText(spec) }}
        </span>
      </legend>

      <div
        class="mt-3 flex flex-wrap gap-3"
        role="group"
        :aria-label="spec.name"
      >
        <button
          v-for="value in spec.values"
          :key="value.id"
          type="button"
          class="relative inline-flex min-h-[44px] min-w-[44px] items-center justify-center gap-2 rounded-[var(--radius-button)] border-2 text-sm font-medium transition-all duration-200 motion-reduce:transition-none"
          :class="buttonClass(spec.id, value.id)"
          :disabled="isDisabled(spec.id, value.id)"
          :aria-disabled="isDisabled(spec.id, value.id) ? 'true' : undefined"
          :aria-pressed="isSelected(spec.id, value.id)"
          :title="isDisabled(spec.id, value.id) ? t('products.spec.unavailable') : value.value"
          @click="select(spec.id, value.id)"
        >
          <!-- 带图的规格值（颜色色块）：图片 + 文案 -->
          <template v-if="value.imageUrl">
            <img
              :src="value.imageUrl"
              :alt="value.value"
              class="h-11 w-11 rounded-md border border-[var(--color-border-light)] object-cover"
              :class="isDisabled(spec.id, value.id) ? 'grayscale opacity-50' : ''"
              loading="lazy"
            />
            <span class="text-xs">{{ value.value }}</span>
          </template>
          <!-- 纯文字规格值 -->
          <span v-else class="px-2">{{ value.value }}</span>

          <UIcon
            v-if="isSelected(spec.id, value.id)"
            name="lucide:check"
            class="absolute -right-1.5 -top-1.5 h-4 w-4 rounded-full bg-[var(--color-accent)] p-0.5 text-white"
            aria-hidden="true"
          />
          <span v-if="isDisabled(spec.id, value.id)" class="sr-only">{{ t('products.spec.unavailable') }}</span>
        </button>
      </div>
    </fieldset>
  </div>
</template>

<script setup lang="ts">
import type { SpecSelection, StoreSkuCombination, StoreSpec } from '~~/types/shop-product'
import { computeUnavailableValueIds, isSpecValueAvailable } from '~~/types/shop-product'

/**
 * 规格选择器。
 *
 * 置灰规则（与后端契约、Lead 用 SQL 复算的期望值一致）：
 *   规格值 V 可选 ⇔ 存在至少一个 combination 满足
 *     saleable === 'Y' && stock > 0 && specValueIds 同时包含 V 与"当前已选的所有其它规格值"
 *   一个组合每个规格只有一个值，所以同规格里已选了别的值时，V 无法与之共存 => 置灰
 *   （例：已选 白色，则 红色/黑色 置灰；想换颜色先点一下已选值取消）。
 *   判定逻辑全部收敛在 types/shop-product.ts 的纯函数里，本组件只负责渲染与交互，
 *   本地自测（含真实演示商品的期望矩阵）见 temp/spec-availability.selfcheck.ts。
 *
 * 已选中值的策略：**保持选中，不自动清空**（选中态优先，不参与置灰），
 * 用户再次点击已选值即可取消，避免出现"被清空/卡在非法状态"。
 */
const props = defineProps<{
  specs: StoreSpec[]
  combinations: StoreSkuCombination[]
  selected: SpecSelection
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:selected', value: SpecSelection): void
}>()

const { t } = useI18n()

const unavailableValueIds = computed(() =>
  computeUnavailableValueIds(props.specs, props.combinations, props.selected),
)

function isSelected(specId: string, valueId: string): boolean {
  return props.selected?.[specId] === valueId
}

function isDisabled(specId: string, valueId: string): boolean {
  if (props.disabled) return true
  // 已选中的值永远可点（用于取消选择）
  if (isSelected(specId, valueId)) return false
  return unavailableValueIds.value.has(valueId)
}

function selectedText(spec: StoreSpec): string {
  const valueId = props.selected?.[spec.id]
  if (!valueId) return ''
  const value = spec.values.find(item => item.id === valueId)
  return value ? `· ${value.value}` : ''
}

function buttonClass(specId: string, valueId: string): string {
  if (isDisabled(specId, valueId)) {
    return 'cursor-not-allowed border-[var(--color-border-light)] bg-[var(--color-surface-secondary)] text-[var(--color-text-muted)] opacity-60'
  }
  if (isSelected(specId, valueId)) {
    return 'cursor-pointer border-[var(--color-accent)] bg-[var(--color-accent)]/10 px-3 text-[var(--color-primary)] shadow-sm'
  }
  return 'cursor-pointer border-[var(--color-border)] bg-white px-3 text-[var(--color-text-secondary)] hover:border-[var(--color-accent)] hover:text-[var(--color-primary)]'
}

function select(specId: string, valueId: string) {
  const next: SpecSelection = { ...props.selected }

  if (next[specId] === valueId) {
    // 再次点击 = 取消选择
    next[specId] = null
  } else if (!isSpecValueAvailable(specId, valueId, props.selected, props.combinations)) {
    // 兜底：即使通过键盘/脚本触发，也不允许选中"会导致无可售组合"的值，
    // 保证不存在的组合无法通过任何点击路径被选中。
    return
  } else {
    next[specId] = valueId
  }

  emit('update:selected', next)
}
</script>
