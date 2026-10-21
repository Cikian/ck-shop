<template>
  <!--
    购物车单行（= 一个 SKU）。

    【重构要点】旧版是「一行里左右堆叠的卡片」，新版对齐重新设计后的
    「表头化商品清单」：桌面端 6 列网格（勾选 / 商品信息 / 单价 / 数量 / 小计 / 操作），
    与 cart.vue 的表头列宽**逐列对齐**；移动端同 DOM 换 3 列网格，改成上下堆叠，
    不会把商品名、步进器、小计挤在同一行。

    【不重复实现后端规则】失效判定一律来自后端直出的 item.status / item.selectable：
    前端只做「怎么显示」，不改「能不能买」，否则迟早出现"详情页说有货、购物车说没货"。
  -->
  <li
    class="group relative overflow-hidden transition-colors duration-200 motion-reduce:transition-none"
    :class="invalid ? 'bg-[var(--color-surface-secondary)]/55' : 'hover:bg-[var(--color-brand-50)]/45'"
    :aria-busy="pending ? 'true' : undefined"
  >
    <!--
      两个断点的列宽必须与 cart.vue 表头完全一致，改动时两处一起改：
        lg（窄桌面）：lg:grid-cols-[56px_minmax(0,1fr)_72px_104px_84px_64px]
        xl（宽桌面）：xl:grid-cols-[84px_minmax(0,1fr)_88px_124px_96px_76px]
      移动端：grid-cols-[26px_minmax(0,1fr)_auto]
    -->
    <div class="grid grid-cols-[26px_minmax(0,1fr)_auto] items-start gap-x-3 gap-y-2.5 px-3.5 py-4 lg:grid-cols-[56px_minmax(0,1fr)_72px_104px_84px_64px] lg:items-center lg:gap-x-4 lg:gap-y-0 lg:px-4 xl:grid-cols-[84px_minmax(0,1fr)_88px_124px_96px_76px]">
      <!-- 勾选：失效条目禁用，但保留占位，整行左边界不会跳动。
           靠左对齐是为了与 cart.vue 表头里的"全选"复选框严格同一竖线。 -->
      <div class="col-start-1 row-start-1 flex justify-start">
        <!-- relative 不能省：勾选对勾是 absolute，定位基准必须是这个 label -->
        <label class="relative flex h-[22px] w-[22px] cursor-pointer items-center justify-center">
          <input
            :id="checkboxId"
            type="checkbox"
            class="peer h-[18px] w-[18px] cursor-pointer appearance-none rounded-full border-2 border-[var(--color-line)] bg-white transition-colors duration-200 checked:border-[var(--color-brand-500)] checked:bg-[var(--color-brand-500)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
            :checked="selected"
            :disabled="invalid"
            :aria-label="t('cart.selectItem', { title: item.title })"
            @change="emit('toggle', item.id)"
          />
          <UIcon
            name="lucide:check"
            class="pointer-events-none absolute h-3 w-3 text-white opacity-0 transition-opacity duration-150 peer-checked:opacity-100 motion-reduce:transition-none"
            aria-hidden="true"
          />
        </label>
      </div>

      <!-- 商品信息：缩略图 + 标题 + 规格胶囊 + 件数 -->
      <div class="col-span-2 col-start-2 row-start-1 flex min-w-0 items-start gap-3 lg:col-span-1 lg:col-start-2">
        <NuxtLinkLocale
          :to="`/products/${item.productId}`"
          class="relative block h-[72px] w-[72px] shrink-0 overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)] lg:h-20 lg:w-20"
          :aria-label="t('cart.goProduct')"
        >
          <ShopImage
            :src="item.coverUrl"
            :alt="item.title"
            width="72"
            height="72"
            class="h-full w-full object-cover transition-transform duration-300 motion-reduce:transition-none"
            :class="invalid ? 'opacity-40 grayscale' : 'group-hover:scale-105'"
            icon-class="h-6 w-6"
          />

          <!-- 失效原因压在封面上：一眼看到"为什么灰" -->
          <span
            v-if="invalid"
            class="absolute inset-x-0 bottom-0 flex items-center justify-center gap-1 bg-[var(--color-ink)]/80 py-1 text-[10px] font-semibold text-white"
          >
            <UIcon name="lucide:ban" class="h-3 w-3" aria-hidden="true" />
            {{ statusText }}
          </span>
        </NuxtLinkLocale>

        <div class="min-w-0 flex-1">
          <NuxtLinkLocale
            :to="`/products/${item.productId}`"
            class="line-clamp-2 text-[14px] font-bold leading-snug transition-colors duration-200 lg:text-[15px] motion-reduce:transition-none"
            :class="invalid
              ? 'text-[var(--color-muted)]'
              : 'text-[var(--color-ink)] hover:text-[var(--color-brand-600)]'"
          >
            {{ item.title || item.spuCode || '—' }}
          </NuxtLinkLocale>

          <!-- SKU 是购物车的主体，规格必须显式展示 -->
          <div class="mt-1.5 flex flex-wrap items-center gap-x-1.5 gap-y-1">
            <span v-if="item.skuName" class="tag-service tag-service-neutral">{{ item.skuName }}</span>
            <span v-else class="text-[11px] text-[var(--color-muted)]">{{ t('cart.noSpec') }}</span>
            <span v-if="item.unit" class="text-[11px] text-[var(--color-muted)]">/ {{ item.unit }}</span>
            <span class="text-[11px] text-[var(--color-muted)] tabular">{{ item.quantity }} {{ t('shop.itemsUnit') }}</span>
          </div>

          <!-- 只有"可结算但库存不足"才在这里解释原因：失效行封面上已有状态条，说两遍是噪音 -->
          <p
            v-if="statusMessage"
            class="mt-1.5 flex flex-wrap items-center gap-1.5 text-[11px] font-medium"
            :class="statusToneClass"
          >
            <UIcon :name="statusIcon" class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            <span>{{ statusMessage }}</span>
            <button
              v-if="item.status === 'STOCK_INSUFFICIENT' && item.stock > 0"
              type="button"
              class="cursor-pointer font-bold underline decoration-dotted underline-offset-2"
              @click="emit('fix-quantity', item.id, item.stock)"
            >
              {{ t('cart.fixQuantity', { count: item.stock }) }}
            </button>
          </p>
        </div>
      </div>

      <!-- 单价（移动端这行的标签由单元格内补齐，因为表头在移动端隐藏） -->
      <div class="col-start-2 row-start-2 flex items-baseline gap-1.5 lg:col-start-3 lg:row-start-1">
        <span class="text-[11px] text-[var(--color-muted)] lg:hidden">{{ t('cart.unitPrice') }}</span>
        <span
          class="text-[15px] font-bold tabular"
          :class="invalid ? 'text-[var(--color-muted)]' : 'text-[var(--color-ink)]'"
        >
          {{ formatCompact(item.price) }}
        </span>
      </div>

      <!-- 数量：失效行不显示（后端只认库存，前端不给"改一个数量就好了"的错觉） -->
      <div
        v-if="!invalid"
        class="col-start-2 row-start-3 flex flex-col items-start gap-1 lg:col-start-4 lg:row-start-1 lg:items-center"
      >
        <div class="flex items-center overflow-hidden rounded-full border border-[var(--color-line)] bg-white">
          <button
            type="button"
            class="flex h-9 w-9 cursor-pointer items-center justify-center text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none lg:h-8 lg:w-8 xl:h-9 xl:w-9"
            :disabled="pending || item.quantity <= 1"
            :aria-label="t('product.decreaseQuantity')"
            @click="emit('update-quantity', item.id, item.quantity - 1)"
          >
            <UIcon name="lucide:minus" class="h-3.5 w-3.5" aria-hidden="true" />
          </button>
          <input
            type="number"
            inputmode="numeric"
            min="1"
            :max="maxQuantity"
            :value="item.quantity"
            :disabled="pending"
            :aria-label="t('cart.quantity')"
            class="h-9 w-12 border-x border-[var(--color-line-soft)] bg-transparent text-center text-[13px] font-bold text-[var(--color-ink)] outline-none disabled:cursor-not-allowed disabled:text-[var(--color-muted)] [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none lg:h-8 lg:w-9 xl:h-9 xl:w-11"
            @change="onQuantityInput"
          />
          <button
            type="button"
            class="flex h-9 w-9 cursor-pointer items-center justify-center text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none lg:h-8 lg:w-8 xl:h-9 xl:w-9"
            :disabled="pending || item.quantity >= maxQuantity"
            :aria-label="t('product.increaseQuantity')"
            @click="emit('update-quantity', item.id, item.quantity + 1)"
          >
            <UIcon name="lucide:plus" class="h-3.5 w-3.5" aria-hidden="true" />
          </button>
        </div>
        <!-- 到顶了要说明原因，而不是让 + 号莫名其妙地灰掉 -->
        <span v-if="atMaxQuantity" class="text-[11px] whitespace-nowrap text-[var(--color-warning-600)]">
          {{ t('cart.maxStock') }}
        </span>
      </div>

      <!-- 小计：失效条目的 lineTotal 恒为 0，显示 ¥0 只会误导，所以整块不渲染 -->
      <div
        v-if="!invalid"
        class="col-start-3 row-start-2 flex flex-col items-end justify-self-end lg:col-start-5 lg:row-start-1 lg:items-start lg:justify-self-start"
      >
        <span class="text-[11px] text-[var(--color-muted)] lg:hidden">{{ t('cart.subtotal') }}</span>
        <span class="price text-[18px] leading-none whitespace-nowrap xl:text-[20px]">{{ formatCompact(item.lineTotal) }}</span>
      </div>

      <!-- 操作：一个 DOM，移动端靠网格显式定位换行（失效行没有步进器/小计，上移一行填位） -->
      <div
        class="justify-self-end lg:col-start-6 lg:row-start-1 lg:justify-self-start"
        :class="invalid ? 'col-start-3 row-start-2' : 'col-start-3 row-start-3'"
      >
        <button
          type="button"
          class="flex cursor-pointer items-center gap-1 rounded-full px-2 py-1.5 text-[12px] font-medium text-[var(--color-muted)] transition-colors duration-200 hover:bg-[var(--color-error)]/10 hover:text-[var(--color-error)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
          :aria-label="t('cart.removeItem', { title: item.title })"
          :disabled="pending"
          @click="emit('remove', item.id)"
        >
          <UIcon name="lucide:trash-2" class="h-4 w-4" aria-hidden="true" />
          <span class="hidden lg:inline">{{ t('cart.remove') }}</span>
        </button>
      </div>
    </div>

    <!-- 请求进行中遮罩：避免连点重复提交（fade 出现/消失，不再是硬切） -->
    <Transition name="fade">
      <div
        v-if="pending"
        class="absolute inset-0 cursor-wait bg-white/60 backdrop-blur-[1px]"
        aria-hidden="true"
      ></div>
    </Transition>
  </li>
</template>

<script setup lang="ts">
import type { StoreCartItem } from '~~/types/shop-cart'
import { cartItemStatusKey, cartItemStatusTone } from '~~/types/shop-cart'
import { useStorePrice } from '~~/composables/useProducts'

/**
 * 购物车单行。展示契约与 StoreProductCard 一致：
 *   - 价格统一走 useStorePrice().formatCompact（整数不带 .00）；
 *   - 没有数据的元素整块不渲染（失效行的小计就是这种情况）。
 */
const props = defineProps<{
  item: StoreCartItem
  selected: boolean
  pending?: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle', itemId: string): void
  (e: 'remove', itemId: string): void
  (e: 'update-quantity', itemId: string, quantity: number): void
  (e: 'fix-quantity', itemId: string, quantity: number): void
}>()

const { t } = useI18n()
const { formatCompact } = useStorePrice()

// 封面失败回退由 <ShopImage> 统一处理（外链图挂掉不该显示"碎图 + alt"）

/** 失效 = 不可勾选结算（下架 / 无货 / 规格失效），判定完全来自后端 */
const invalid = computed(() => !props.item.selectable)

/** 复选框 id：label 与 input 关联用；也保证 SSR/CSR 一致 */
const checkboxId = computed(() => `cart-select-${props.item.id}`)

/** 可购上限取"库存"与单品上限两者的较小值（沿用既有口径，行为不变） */
const maxQuantity = computed(() => Math.max(1, Math.min(props.item.stock || 1, 999)))
const atMaxQuantity = computed(() => props.item.quantity >= maxQuantity.value)

const statusText = computed(() => t(`cart.status.${cartItemStatusKey(props.item.status)}`, {
  count: props.item.stock,
}))

/** 说明文案只给"可结算但库存不足"，失效行交给封面状态条 */
const statusMessage = computed(() => (
  props.item.selectable && props.item.status !== 'AVAILABLE' ? statusText.value : ''
))

const statusToneClass = computed(() => {
  switch (cartItemStatusTone(props.item.status)) {
    case 'warning':
      return 'text-[var(--color-warning-600)]'
    case 'danger':
      return 'text-[var(--color-error)]'
    default:
      return 'text-[var(--color-muted)]'
  }
})

const statusIcon = computed(() => {
  switch (props.item.status) {
    case 'STOCK_INSUFFICIENT':
      return 'lucide:alert-triangle'
    case 'OUT_OF_STOCK':
      return 'lucide:package-x'
    case 'SKU_INVALID':
      return 'lucide:layers'
    case 'OFFLINE':
      return 'lucide:ban'
    default:
      return 'lucide:info'
  }
})

/**
 * 手动输入数量：非法值（空/0/负数/非数字/超库存）一律回退原值，
 * 不"猜一个数字"——用户改错了要能立刻看出来没生效。
 */
function onQuantityInput(event: Event) {
  const target = event.target as HTMLInputElement
  const raw = Number(target.value)
  if (!Number.isFinite(raw) || raw < 1) {
    target.value = String(props.item.quantity)
    return
  }
  const next = Math.min(Math.trunc(raw), maxQuantity.value)
  target.value = String(next)
  if (next !== props.item.quantity) {
    emit('update-quantity', props.item.id, next)
  }
}
</script>
