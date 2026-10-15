<template>
  <div class="space-y-5">
    <!-- 操作提示：同规格直接切换、点已选值取消；这里用品牌浅底把它和规格区分开 -->
    <p class="flex items-start gap-2 rounded-[12px] bg-[var(--color-brand-50)] px-3 py-2 text-xs text-[var(--color-brand-700)]">
      <UIcon name="lucide:info" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      {{ t('products.spec.tapToClear') }}
    </p>

    <fieldset
      v-for="spec in specs"
      :key="spec.id"
      class="m-0 border-0 p-0"
    >
      <legend class="flex flex-wrap items-center gap-2 text-sm font-bold text-[var(--color-ink)]">
        {{ spec.name }}
        <span
          v-if="spec.isRequired === 'Y'"
          class="text-[var(--color-brand-500)]"
          aria-hidden="true"
        >*</span>
        <span v-else class="text-xs font-normal text-[var(--color-muted)]">
          ({{ t('products.spec.optional') }})
        </span>
        <span v-if="selectedText(spec)" class="text-xs font-normal text-[var(--color-brand-600)]">
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
          class="relative inline-flex min-h-[44px] min-w-[44px] items-center justify-center gap-2 border-2 text-sm font-medium transition-all duration-200 motion-reduce:transition-none"
          :class="[
            // 带图的规格值（颜色）用圆角方框更贴合缩略图；纯文字规格值沿用胶囊形
            value.imageUrl ? 'rounded-[14px]' : 'rounded-[var(--radius-button)]',
            buttonClass(spec.id, value.id),
          ]"
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
              width="44"
              height="44"
              class="h-11 w-11 rounded-[10px] border border-[var(--color-line)] object-cover"
              :class="isDisabled(spec.id, value.id) ? 'grayscale opacity-50' : ''"
              loading="lazy"
            >
            <span class="text-xs">{{ value.value }}</span>
          </template>
          <!-- 纯文字规格值 -->
          <span v-else class="px-2">{{ value.value }}</span>

          <!-- 选中标记：右上角品牌色圆形勾（纯装饰，选中态本身由 aria-pressed 表达） -->
          <span
            v-if="isSelected(spec.id, value.id)"
            class="absolute -right-1.5 -top-1.5 flex h-4 w-4 items-center justify-center rounded-full bg-[var(--color-brand-500)] text-white"
            aria-hidden="true"
          >
            <UIcon name="lucide:check" class="h-2.5 w-2.5" />
          </span>
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
 * 置灰规则（判定逻辑全部收敛在 types/shop-product.ts 的纯函数里，本组件只负责渲染与交互）：
 *   规格值 V 可选 ⇔ 存在至少一个 combination 满足
 *     saleable === 'Y' && stock > 0 && specValueIds 同时包含 V 与"当前已选的**其它规格**的值"
 *   注意"其它规格"是按 specId 排除的（2026-10-14 改版）：
 *     - 同一规格内换值 = **直接切换**，不需要先取消当前所选；
 *       只要「新值 + 其它规格已选值」存在可售组合，点下去就换过去。
 *     - 若不存在那样的组合，该值依旧灰色不可选（这是需求的硬约束）。
 *     - 已选中的值永远可点，作用变成"取消选择"。
 *   本地自测（含真实演示商品的期望矩阵与同规格切换矩阵）见 temp/spec-availability.selfcheck.ts。
 *
 * 【本次改动范围】只改了置灰语义（isSpecValueAvailable 增加按 specId 排除本规格旧值）
 * 与提示文案；props、emit、isSelected/isDisabled/select 的结构与提前 return 顺序保持不变，
 * 它们仍然负责"不许通过任何点击路径选到不可售组合"这条底线。
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

/**
 * 按钮状态样式（只返回状态相关的类，圆角与尺寸在模板里，避免同一份样式写两遍）：
 *   不可选 → 浅灰底 + 灰字 + 降透明度 + 禁用光标
 *   已选中 → 品牌色描边 + brand-50 浅底 + 品牌色文字
 *   未选中 → 白底 + 极浅描边，hover 走品牌色
 */
function buttonClass(specId: string, valueId: string): string {
  if (isDisabled(specId, valueId)) {
    return 'cursor-not-allowed border-[var(--color-line)] bg-[var(--color-surface-secondary)] text-[var(--color-muted)] opacity-60'
  }
  if (isSelected(specId, valueId)) {
    return 'cursor-pointer border-[var(--color-brand-500)] bg-[var(--color-brand-50)] px-3 text-[var(--color-brand-700)] shadow-[0_0_0_3px_rgba(255,77,109,0.10)]'
  }
  return 'cursor-pointer border-[var(--color-line)] bg-white px-3 text-[var(--color-ink-soft)] hover:border-[var(--color-brand-300)] hover:text-[var(--color-brand-600)]'
}

function select(specId: string, valueId: string) {
  const next: SpecSelection = { ...props.selected }

  if (next[specId] === valueId) {
    // 点击"当前已选值" = 取消选择（保留原交互，给用户一条显式清空的路）
    next[specId] = null
  } else if (!isSpecValueAvailable(specId, valueId, props.selected, props.combinations)) {
    // 兜底：即使通过键盘/脚本触发，也不允许选中"与其它规格已选值没有可售组合"的值，
    // 保证不存在的组合无法通过任何点击路径被选中。
    return
  } else {
    // 同规格的另一个值：直接覆盖，等于"切换"——用户不需要先取消当前所选。
    // 能走到这里说明「该值 + 其它规格已选值」存在可售组合（上面的可用性判断已经确认）。
    next[specId] = valueId
  }

  emit('update:selected', next)
}
</script>
