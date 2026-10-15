<template>
  <!--
    分页条（全站唯一实现）。
    4 个列表页原本各写了一份：orders 列表 / profile 订单 tab / products 搜索页 /
    categories 分类页。页码窗口算法、禁用态、aria 都收在这里，页面只负责"翻页后干什么"。

    三种视觉（variant）刻意保留，因为它们分别对应各页既有设计，不做统一改版：
      info    上一页 · 「第 x / 共 y 页」· 下一页        （订单列表、个人中心订单 tab）
      chip    上一页 · [页码窗口] · 下一页（.chip 药丸） （商品搜索页）
      outline 上一页 · [页码窗口] · 下一页（描边圆形）   （分类页）
  -->
  <nav
    v-if="total > 1"
    class="flex flex-wrap items-center justify-center gap-2"
    :class="margin"
    :aria-label="label"
  >
    <button
      type="button"
      :class="edgeButtonClass"
      :disabled="disabled || current <= 1"
      @click="emit('change', current - 1)"
    >
      <UIcon name="lucide:chevron-left" class="h-4 w-4" aria-hidden="true" />
      <span class="hidden sm:inline">{{ prevLabel }}</span>
    </button>

    <!-- info：不渲染页码窗口，只给一行「第 x / 共 y 页」 -->
    <span v-if="variant === 'info'" class="px-2 tabular text-[var(--color-muted)]" :class="infoTextClass">
      {{ infoLabel }}
    </span>

    <!-- chip / outline：页码窗口（最多 5 个，围绕当前页展开） -->
    <template v-else>
      <button
        v-for="pageNumber in pageWindow"
        :key="pageNumber"
        type="button"
        :class="pageButtonClass(pageNumber)"
        :aria-current="pageNumber === current ? 'page' : undefined"
        @click="emit('change', pageNumber)"
      >
        {{ pageNumber }}
      </button>
    </template>

    <button
      type="button"
      :class="edgeButtonClass"
      :disabled="disabled || current >= total"
      @click="emit('change', current + 1)"
    >
      <span class="hidden sm:inline">{{ nextLabel }}</span>
      <UIcon name="lucide:chevron-right" class="h-4 w-4" aria-hidden="true" />
    </button>
  </nav>
</template>

<script setup lang="ts">
/**
 * 分页条。行为与各页原实现逐字一致：
 *   - 页码窗口：最多 5 个，围绕当前页展开，越界时向另一侧补齐（原 4 处的算法完全相同）
 *   - 上一页 / 下一页：在 1..total 之间的 current ± 1，越界由 disabled 拦住
 *   - 只有 total > 1 才渲染（各页原本的 v-if 条件额外部分仍留在调用方）
 *   - 当前页按钮带 aria-current="page"
 */
const props = withDefaults(defineProps<{
  /** 当前页（1 起） */
  current: number
  /** 总页数 */
  total: number
  /** 视觉变体：info = 文案型；chip = .chip 药丸页码；outline = 描边圆形页码 */
  variant?: 'info' | 'chip' | 'outline'
  /** info 变体的字号：md = 13px（订单列表），sm = 12px（个人中心订单 tab） */
  size?: 'md' | 'sm'
  /** 加载中：两个方向键都禁用 */
  disabled?: boolean
  /** nav 的上边距（各页原本不同：mt-6 / mt-8 / mt-10） */
  margin?: string
  /** nav 的 aria-label（传 t('order.pagination') 或 t('products.pagination')） */
  label?: string
  prevLabel?: string
  nextLabel?: string
  /** info 变体的「第 x / 共 y 页」文案（调用方用 t() 拼好，避免在这里判断 i18n key） */
  infoLabel?: string
}>(), {
  variant: 'info',
  size: 'md',
  disabled: false,
  margin: 'mt-8',
  label: '',
  prevLabel: '',
  nextLabel: '',
  infoLabel: '',
})

const emit = defineEmits<{ change: [page: number] }>()

/** 页码窗口：最多 5 个，围绕当前页展开（与 4 个页面原实现一致） */
const pageWindow = computed(() => {
  const pages = props.total
  const current = props.current
  const size = Math.min(5, pages)
  let start = Math.max(1, current - Math.floor(size / 2))
  const end = Math.min(pages, start + size - 1)
  start = Math.max(1, end - size + 1)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

const infoTextClass = computed(() => (props.size === 'sm' ? 'text-[12px]' : 'text-[13px]'))

/**
 * 上/下页按钮：info / chip 用站内统一的 .ui-pill 药丸；
 * outline（分类页）沿用原本的描边圆形，含 disabled 时不改变边框与文字色。
 */
const edgeButtonClass = computed(() => {
  if (props.variant === 'outline') {
    return 'flex h-10 min-w-[44px] cursor-pointer items-center justify-center gap-1 rounded-full border border-[var(--color-line)] bg-white px-3 text-sm font-bold text-[var(--color-ink-soft)] transition-colors hover:border-[var(--color-brand-300)] hover:text-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-[var(--color-line)] disabled:hover:text-[var(--color-ink-soft)] motion-reduce:transition-none'
  }
  return props.size === 'sm'
    ? 'ui-pill ui-pill-ghost h-9 gap-1 px-4 text-[12px] disabled:cursor-not-allowed disabled:opacity-40'
    : 'ui-pill ui-pill-ghost h-10 gap-1 px-4 text-[13px] disabled:cursor-not-allowed disabled:opacity-40'
})

function pageButtonClass(pageNumber: number): string | string[] {
  if (props.variant === 'outline') {
    return [
      'flex h-10 min-w-[44px] cursor-pointer items-center justify-center rounded-full border px-3 text-sm font-bold transition-colors motion-reduce:transition-none',
      pageNumber === props.current
        ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)] text-white'
        : 'border-[var(--color-line)] bg-white text-[var(--color-ink-soft)] hover:border-[var(--color-brand-300)] hover:text-[var(--color-brand-600)]',
    ]
  }
  return ['chip min-w-[38px] tabular', pageNumber === props.current ? 'chip-active' : '']
}
</script>
