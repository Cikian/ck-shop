<!--
  商品购买栏：操作区（数量 / 立即购买 / 收藏 / 加入购物车）+ 合计 + 服务标签

  从 pages/products/[slug].vue 拆出（商品详情页拆组件第三步，也是最后一块模板）。

  布局契约（用户明确要求）：
    第一行 = 数量输入框 + 立即购买；第二行 = 收藏 + 加入购物车。
  用 grid 而不是 flex 做第二行：flex 项 min-width 默认 auto（受内容宽度影响），
  两个按钮文案长度不同就会一宽一窄；grid 的 minmax(0,1fr) 保证严格等宽。

  组件是**受控**的：数量、加载中、收藏态都由页面持有（它们与规格选择、后端关系耦合），
  本组件只负责渲染与把用户动作抛回去。
-->
<template>
  <div class="mt-7">
    <!-- 第一行：数量输入框 + 购买按钮 -->
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
      <div class="flex h-12 shrink-0 items-center rounded-[var(--radius-button)] border border-[var(--color-line)] bg-white">
        <button
          type="button"
          class="flex h-12 w-12 cursor-pointer items-center justify-center rounded-l-[var(--radius-button)] text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-[var(--color-ink-soft)] motion-reduce:transition-none"
          :disabled="quantity <= 1"
          :aria-label="t('product.decreaseQuantity')"
          @click="emit('change-quantity', -1)"
        >
          <UIcon name="lucide:minus" class="h-4 w-4" aria-hidden="true" />
        </button>
        <span class="w-12 text-center text-base font-bold tabular text-[var(--color-ink)]">{{ quantity }}</span>
        <button
          type="button"
          class="flex h-12 w-12 cursor-pointer items-center justify-center rounded-r-[var(--radius-button)] text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-[var(--color-ink-soft)] motion-reduce:transition-none"
          :disabled="quantity >= maxQuantity"
          :aria-label="t('product.increaseQuantity')"
          @click="emit('change-quantity', 1)"
        >
          <UIcon name="lucide:plus" class="h-4 w-4" aria-hidden="true" />
        </button>
      </div>

      <!--
        立即购买：把这一件（含已选规格与数量）直接写进结算快照跳到 /checkout，
        **不经过购物车**（cartItemId 为空串，下单后后端不会清任何购物车条目）。
      -->
      <button
        type="button"
        class="ui-pill ui-pill-accent h-12 flex-1 text-sm disabled:cursor-not-allowed disabled:opacity-45"
        :disabled="!canBuyNow || buying"
        :aria-busy="buying"
        @click="emit('buy-now')"
      >
        <UIcon
          :name="buying ? 'lucide:loader-circle' : 'lucide:zap'"
          class="h-5 w-5"
          :class="buying ? 'animate-spin' : ''"
          aria-hidden="true"
        />
        {{ buying ? t('product.buying') : buyNowLabel }}
      </button>
    </div>

    <!--
      第二行：收藏按钮 + 加入购物车按钮
      用 grid 而不是 flex：flex 项的 min-width 默认是 auto（受内容宽度影响），
      两个按钮文案长度不同就会一宽一窄；grid 的 minmax(0,1fr) 保证严格等宽。
    -->
    <div class="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
      <!-- 收藏：真实写入 ck_shop_favorite 的关系，返回后按后端的最新状态渲染（不做乐观取反） -->
      <button
        type="button"
        class="ui-pill h-12 gap-2 text-sm"
        :class="favorited
          ? 'border-2 border-[var(--color-brand-500)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]'
          : 'ui-pill-ghost disabled:cursor-not-allowed disabled:opacity-45'"
        :disabled="favoritePending"
        :aria-pressed="favorited"
        :aria-busy="favoritePending"
        :aria-label="favorited ? t('product.removeFromWishlist') : t('product.addToWishlist')"
        @click="emit('toggle-favorite')"
      >
        <UIcon
          :name="favoritePending ? 'lucide:loader-circle' : 'lucide:heart'"
          class="h-5 w-5"
          :class="[favoritePending ? 'animate-spin' : '', favorited && !favoritePending ? 'fill-current' : '']"
          aria-hidden="true"
        />
        {{ favorited ? t('product.wishlisted') : t('product.addToWishlist') }}
      </button>

      <!-- 加入购物车：主体是 SKU，所以必须先命中一个可售组合（无规格商品例外） -->
      <button
        type="button"
        class="ui-pill ui-pill-brand h-12 text-sm disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:shadow-none"
        :disabled="!canAddToCart || adding"
        :aria-busy="adding"
        @click="emit('add-to-cart')"
      >
        <UIcon
          :name="adding ? 'lucide:loader-circle' : 'lucide:shopping-cart'"
          class="h-5 w-5"
          :class="adding ? 'animate-spin' : ''"
          aria-hidden="true"
        />
        {{ adding ? t('product.adding') : addToCartLabel }}
      </button>
    </div>

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

    <div v-if="buyable" class="mt-4 flex items-center justify-between rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)] px-4 py-3">
      <span class="text-sm text-[var(--color-muted)]">{{ t('product.totalItems', { count: quantity }) }}</span>
      <span class="text-lg font-black tabular text-[var(--color-brand-600)]">{{ totalPrice }}</span>
    </div>
  </div>

  <!-- 服务标签：只认真实 tag code（FAST/TOP）与 freeShipping 字段；
       七天无理由是本店对外的统一退货承诺（SiteFooter 也在打同一句），不是商品数据 -->
  <div class="mt-6 flex flex-wrap gap-2 border-t border-[var(--color-line-soft)] pt-5">
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
</template>

<script setup lang="ts">
// 模板里直接按名字读这些 prop（quantity / maxQuantity / buyNowLabel ...）
defineProps<{
  /** 当前数量（受控，父组件持有） */
  quantity: number
  /** 上限 = 可售库存 */
  maxQuantity: number
  canBuyNow: boolean
  buying: boolean
  /** 按钮文案：未登录 / 不可售 / 正常，都来自数据层的同源判断 */
  buyNowLabel: string
  favorited: boolean
  favoritePending: boolean
  canAddToCart: boolean
  adding: boolean
  addToCartLabel: string
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
  (e: 'buy-now'): void
  (e: 'add-to-cart'): void
  (e: 'toggle-favorite'): void
}>()

const { t } = useI18n()
</script>
