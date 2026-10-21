<template>
  <!--
    首页左侧竖向分类菜单（设计稿首屏左栏）。
    桌面端显示；移动端隐藏（移动端分类入口在 Header 的抽屉里 + 首页的分类横滑条）。

    分类数据是后端真实分类树（/api/categories），**不硬编码任何分类名**：
    换一个店铺、换一套分类，这个菜单自动跟着变。
  -->
  <nav
    class="ui-card flex h-full flex-col overflow-visible p-2"
    :aria-label="t('shop.allCategories')"
  >
    <!-- 首屏骨架：只在分类还没回来时出现 -->
    <template v-if="pending && !categories.length">
      <div
        v-for="i in 8"
        :key="i"
        class="flex items-center gap-3 rounded-[12px] px-3 py-[11px]"
        aria-hidden="true"
      >
        <span class="h-5 w-5 shrink-0 animate-pulse rounded bg-[var(--color-surface-secondary)]" />
        <span class="h-4 flex-1 animate-pulse rounded bg-[var(--color-surface-secondary)]" />
      </div>
    </template>

    <ul v-else class="flex flex-1 flex-col">
      <li
        v-for="category in visibleCategories"
        :key="category.id"
        class="group relative"
        @mouseenter="openMenu(category.id)"
        @mouseleave="scheduleClose"
        @focusin="openMenu(category.id)"
        @focusout="onFocusOut"
        @keydown.escape="closeNow"
      >
        <NuxtLinkLocale
          :to="`/categories/${category.id}`"
          class="flex items-center gap-3 rounded-[12px] px-3 py-[11px] text-sm font-medium text-[var(--color-ink-soft)] transition-colors hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)]"
          :aria-haspopup="hasChildren(category) ? 'true' : undefined"
          :aria-expanded="hasChildren(category) ? openId === category.id : undefined"
          :aria-controls="hasChildren(category) ? panelId(category.id) : undefined"
        >
          <!--
            图标是**装饰性**的（分类名始终以文字展示，图标不承担语义）。
            按分类名做关键词匹配，匹配不到就用中性图标 ——
            绝不按"第几个分类"轮换：那会在户外店里把手机图标配给「户外服装」。
          -->
          <UIcon :name="iconFor(category.name)" class="h-[18px] w-[18px] shrink-0" aria-hidden="true" />
          <span class="min-w-0 flex-1 truncate">{{ category.name }}</span>
          <UIcon
            v-if="hasChildren(category)"
            name="lucide:chevron-right"
            class="h-4 w-4 shrink-0 text-[var(--color-muted)] transition-transform duration-200 group-hover:translate-x-0.5 motion-reduce:transition-none"
            aria-hidden="true"
          />
        </NuxtLinkLocale>

        <!--
          二级分类浮层。

          ⚠️ 两个关键点：
          1) 用 `pl-2` 把"8px 空隙"变成浮层自己的内边距 —— 空隙属于浮层的命中区，
             鼠标从一级项斜向移到浮层时不会先离开 hover 目标（之前用 ml-2，
             中间是死区，慢一点移动浮层就消失了）。
          2) 关闭有 120ms 延迟（scheduleClose）—— 允许用户短暂移出后再回来；
             纯 CSS group-hover 做不了延迟，所以这里用 openId 显式控制。
          键盘同样可用：focusin 打开、ESC 关闭、焦点离开 li 时关闭。
        -->
        <div
          v-if="hasChildren(category)"
          :id="panelId(category.id)"
          class="absolute left-full top-0 z-40 pl-2 transition-[opacity,transform,visibility] duration-200 ease-out motion-reduce:transition-none"
          :class="openId === category.id
            ? 'visible translate-x-0 opacity-100'
            : 'invisible translate-x-1 opacity-0'"
        >
          <div class="w-56 rounded-[16px] bg-white p-3 shadow-[var(--shadow-card-hover)]">
            <p class="mb-2 px-1 text-xs font-bold uppercase tracking-wider text-[var(--color-muted)]">
              {{ category.name }}
            </p>
            <ul class="grid gap-0.5">
              <li v-for="child in childrenOf(category)" :key="child.id">
                <NuxtLinkLocale
                  :to="`/categories/${child.id}`"
                  class="block truncate rounded-[10px] px-2 py-1.5 text-[13px] text-[var(--color-ink-soft)] transition-colors hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)]"
                >
                  {{ child.name }}
                </NuxtLinkLocale>
              </li>
            </ul>
          </div>
        </div>
      </li>
    </ul>

    <NuxtLinkLocale
      v-if="visibleCategories.length"
      to="/categories"
      class="mt-1 flex items-center justify-center gap-1 rounded-[12px] px-3 py-2.5 text-[13px] font-bold text-[var(--color-brand-600)] transition-colors hover:bg-[var(--color-brand-50)]"
    >
      {{ t('shop.viewAll') }}
      <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5" aria-hidden="true" />
    </NuxtLinkLocale>
  </nav>
</template>

<script setup lang="ts">
import type { StoreCategoryNode } from '~~/types/store-category'

/**
 * 左侧竖向分类菜单。
 *
 * 上限 8 项：分类菜单是首屏的"导航柱"，超过 8 项会把整列撑得比右边的 Banner 高很多，
 * 首屏比例失衡。更全的分类走底部「查看全部」。
 *
 * 激活态：这里**不写死任何"当前分类"**。以前第一项永远带激活样式，
 * 用户点进别的分类后左栏仍亮着第一项 —— 与 Header 那套按路由推导的高亮互相矛盾。
 * 菜单只负责导航，激活态由 Header 承担。
 */
const props = defineProps<{
  categories: StoreCategoryNode[]
  pending?: boolean
}>()

const { t } = useI18n()

/** 最多 8 项：菜单是首屏的"导航柱"，再多会把整列撑得比右侧 Banner 高太多 */
const visibleCategories = computed(() => (props.categories || []).slice(0, 8))

// ---------------------------------------------------------------------------
// 二级浮层的开关（hover 延迟 + 键盘）
// ---------------------------------------------------------------------------
const openId = ref('')
/** 关闭延迟：允许鼠标在「一级项 → 浮层」之间短暂离开命中区（120ms 内回来不算关闭） */
const CLOSE_DELAY_MS = 120
let closeTimer: ReturnType<typeof setTimeout> | null = null

function cancelClose() {
  if (closeTimer === null) return
  clearTimeout(closeTimer)
  closeTimer = null
}

function openMenu(id: string) {
  cancelClose()
  openId.value = id
}

function scheduleClose() {
  cancelClose()
  closeTimer = setTimeout(() => {
    openId.value = ''
    closeTimer = null
  }, CLOSE_DELAY_MS)
}

function closeNow() {
  cancelClose()
  openId.value = ''
}

/** 键盘：焦点离开整个 li（含浮层里的链接）才关闭；在 li 内部移动不关 */
function onFocusOut(event: FocusEvent) {
  const next = event.relatedTarget
  if (next instanceof Node && (event.currentTarget as HTMLElement | null)?.contains(next)) return
  closeNow()
}

function panelId(categoryId: string): string {
  return `home-category-panel-${categoryId}`
}

function hasChildren(category: StoreCategoryNode): boolean {
  return (category.children?.length ?? 0) > 0
}

onUnmounted(cancelClose)

/**
 * 装饰图标：按分类名做**关键词**匹配，匹配不到用中性图标。
 *
 * 为什么不能按位置轮换：分类数量与顺序完全由后端决定，
 * 户外店只有 4 个分类时，位置轮换会把「手机图标」配给「户外服装」——
 * 比没有图标更糟，用户会以为点错地方了。
 *
 * 匹配不到时退化成 `lucide:tag`，是"不猜"而不是"乱猜"。
 * 规则顺序有讲究：先匹配更具体的品类词（包 / 鞋 / 工具），
 * 再匹配泛化词（智能 / 数码），否则「户外工具」会被「户外」抢先命中。
 */
const ICON_RULES: Array<{ test: RegExp; icon: string }> = [
  { test: /包|箱/, icon: 'lucide:backpack' },
  { test: /鞋|靴/, icon: 'lucide:footprints' },
  { test: /服装|服饰|衣|裤/, icon: 'lucide:shirt' },
  { test: /露营|帐篷|营地/, icon: 'lucide:tent' },
  { test: /工具|五金|器材|配件/, icon: 'lucide:wrench' },
  { test: /户外|登山|徒步/, icon: 'lucide:mountain' },
  { test: /手表|穿戴/, icon: 'lucide:watch' },
  { test: /手机|通信/, icon: 'lucide:smartphone' },
  { test: /电脑|办公|笔电|笔记本/, icon: 'lucide:laptop' },
  { test: /影音|耳机|音箱|音乐/, icon: 'lucide:headphones' },
  { test: /数码|电器|电子/, icon: 'lucide:plug' },
  { test: /相机|摄影|摄像/, icon: 'lucide:camera' },
  { test: /食品|生鲜|零食|粮|茶|酒/, icon: 'lucide:apple' },
  { test: /美妆|护肤|化妆|香氛/, icon: 'lucide:sparkles' },
  { test: /家居|家纺|家具|厨具/, icon: 'lucide:sofa' },
  { test: /运动|健身/, icon: 'lucide:dumbbell' },
  { test: /母婴|玩具|童/, icon: 'lucide:baby' },
  { test: /图书|文具|书/, icon: 'lucide:book-open' },
  { test: /汽车|车品/, icon: 'lucide:car' },
  { test: /珠宝|首饰|饰/, icon: 'lucide:gem' },
]

const FALLBACK_ICON = 'lucide:tag'

function iconFor(name: string): string {
  const text = (name || '').trim()
  if (!text) return FALLBACK_ICON
  return ICON_RULES.find(rule => rule.test.test(text))?.icon ?? FALLBACK_ICON
}

/** 子分类：后端只返回一层 children，这里不做递归（三层分类在菜单里也展示不下） */
function childrenOf(category: StoreCategoryNode): StoreCategoryNode[] {
  return (category.children || []).slice(0, 12)
}
</script>
