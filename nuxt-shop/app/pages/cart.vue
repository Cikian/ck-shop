<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto max-w-7xl px-2 pb-16 sm:px-3 lg:px-4">
      <!-- ① 标题行：左「购物车 + 共 N 件商品」，右「继续逛逛」 -->
      <header class="mb-5 flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div class="flex flex-wrap items-end gap-x-3 gap-y-1">
          <h1 class="text-[26px] font-black leading-none text-[var(--color-ink)] sm:text-[32px]">
            {{ t('cart.title') }}
          </h1>
          <p v-if="showSubtitle" class="pb-0.5 text-[13px] tabular text-[var(--color-muted)]">
            {{ t('cart.subtitle', { count: summary.totalQuantity }) }}
          </p>
          <p v-else-if="unauthorized" class="pb-0.5 text-[13px] text-[var(--color-muted)]">
            {{ t('cart.subtitleEmpty') }}
          </p>
        </div>

        <NuxtLinkLocale
          to="/products"
          class="group inline-flex cursor-pointer items-center gap-1 text-[13px] font-medium text-[var(--color-muted)] transition-colors duration-200 hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
        >
          {{ t('shop.cartContinue') }}
          <UIcon
            name="lucide:chevron-right"
            class="h-3.5 w-3.5 transition-transform duration-200 group-hover:translate-x-0.5 motion-reduce:transition-none"
            aria-hidden="true"
          />
        </NuxtLinkLocale>
      </header>

      <!-- ② 未登录 -->
      <section v-if="unauthorized" class="ui-card px-6 py-16 text-center">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
          <UIcon name="lucide:lock" class="h-8 w-8 text-[var(--color-brand-500)]" aria-hidden="true" />
        </div>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('cart.loginRequired') }}</h2>
        <p class="mx-auto mt-2 max-w-md text-sm text-[var(--color-muted)]">{{ t('cart.loginRequiredDesc') }}</p>
        <NuxtLinkLocale :to="loginLink" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm">
          <UIcon name="lucide:log-in" class="h-4 w-4" aria-hidden="true" />
          {{ t('cart.loginAction') }}
        </NuxtLinkLocale>
      </section>

      <!-- ③ 加载失败 -->
      <section v-else-if="loadError" class="ui-card px-6 py-16 text-center">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error)]/10">
          <UIcon name="lucide:wifi-off" class="h-8 w-8 text-[var(--color-error)]" aria-hidden="true" />
        </div>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('cart.loadError') }}</h2>
        <p class="mt-2 text-sm text-[var(--color-muted)]">{{ loadError }}</p>
        <button type="button" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm" @click="reload()">
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
          {{ t('cart.retry') }}
        </button>
      </section>

      <!-- ④ 骨架屏：按新布局（左清单 + 右明细卡）占位，避免首屏跳动 -->
      <section v-else-if="!ready || (loading && !items.length)" aria-busy="true" :aria-label="t('common.loading')">
        <div class="lg:grid lg:grid-cols-3 lg:items-start lg:gap-6">
          <div class="ui-card overflow-hidden lg:col-span-2">
            <div class="hidden border-b border-[var(--color-line-soft)] px-4 py-3 lg:block">
              <div class="h-3 w-1/3 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
            </div>
            <div
              v-for="index in 3"
              :key="index"
              class="flex items-center gap-3 border-b border-[var(--color-line-soft)] px-3.5 py-4 last:border-b-0 lg:px-4"
            >
              <div class="h-[18px] w-[18px] shrink-0 animate-pulse rounded-full bg-[var(--color-line-soft)]"></div>
              <div class="h-[72px] w-[72px] shrink-0 animate-pulse rounded-[var(--radius-tile)] bg-[var(--color-line-soft)] lg:h-20 lg:w-20"></div>
              <div class="min-w-0 flex-1 space-y-2.5">
                <div class="h-4 w-2/3 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
                <div class="h-3 w-1/4 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
              </div>
              <div class="hidden h-8 w-28 shrink-0 animate-pulse rounded-full bg-[var(--color-line-soft)] lg:block"></div>
            </div>
          </div>
          <div class="ui-card mt-6 space-y-3 p-5 lg:mt-0">
            <div class="h-4 w-1/3 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
            <div class="h-3 w-full animate-pulse rounded bg-[var(--color-line-soft)]"></div>
            <div class="h-3 w-5/6 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
            <div class="h-3 w-2/3 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
            <div class="h-12 w-full animate-pulse rounded-full bg-[var(--color-line-soft)]"></div>
          </div>
        </div>
      </section>

      <!-- ⑤ 空购物车 -->
      <section v-else-if="!items.length" class="ui-card px-6 py-20 text-center">
        <div class="relative mx-auto h-20 w-20">
          <div class="absolute inset-0 animate-pulse rounded-full bg-[var(--color-accent)]/15 blur-2xl"></div>
          <div class="relative flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
            <UIcon name="lucide:shopping-bag" class="h-9 w-9 text-[var(--color-brand-500)]" aria-hidden="true" />
          </div>
        </div>
        <h2 class="mt-8 text-xl font-bold text-[var(--color-ink)] sm:text-2xl">{{ t('cart.empty') }}</h2>
        <p class="mx-auto mt-2 max-w-md text-sm text-[var(--color-muted)]">{{ t('cart.emptyDesc') }}</p>
        <NuxtLinkLocale to="/products" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm">
          <UIcon name="lucide:arrow-right" class="h-4 w-4" aria-hidden="true" />
          {{ t('cart.emptyAction') }}
        </NuxtLinkLocale>
      </section>

      <!-- ⑥ 购物车内容 -->
      <template v-else>
        <!--
          凑单进度卡：**只有后端真的返回了进行中的满减规则才渲染**。
          促销接口当前固定返回 []（Java 接口尚未提供），所以默认整块不出现——这是预期行为。
        -->
        <section v-if="bundleRule" class="ui-card mb-5 flex flex-wrap items-center gap-4 p-4 sm:p-5">
          <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
            <UIcon name="lucide:gift" class="h-5 w-5 text-[var(--color-brand-500)]" aria-hidden="true" />
          </span>
          <div class="min-w-[200px] flex-1">
            <p class="text-[14px] font-bold text-[var(--color-ink)]">{{ bundleText }}</p>
            <div
              class="mt-2 h-2 w-full overflow-hidden rounded-full bg-[var(--color-line)]"
              role="progressbar"
              :aria-label="bundleText"
              aria-valuemin="0"
              :aria-valuemax="bundleRule?.target ?? 0"
              :aria-valuenow="bundleRule?.current ?? 0"
            >
              <div
                class="brand-gradient h-full rounded-full transition-[width] duration-500 motion-reduce:transition-none"
                :style="{ width: `${bundlePercent}%` }"
              ></div>
            </div>
          </div>
          <NuxtLinkLocale to="/products" class="ui-pill ui-pill-brand h-10 shrink-0 px-5 text-[13px]">
            {{ t('shop.cartGoBundle') }}
          </NuxtLinkLocale>
        </section>

        <div class="lg:grid lg:grid-cols-3 lg:items-start lg:gap-6">
          <!-- 左栏：表头化的商品清单 -->
          <div class="lg:col-span-2">
            <div class="ui-card overflow-hidden">
              <!-- 桌面端表头：全选 + 各列名称。列宽必须与 CartLineItem 行内的网格一致 -->
              <div class="hidden items-center gap-x-4 border-b border-[var(--color-line-soft)] px-4 py-3 text-[12px] text-[var(--color-muted)] lg:grid lg:grid-cols-[56px_minmax(0,1fr)_72px_104px_84px_64px] xl:grid-cols-[84px_minmax(0,1fr)_88px_124px_96px_76px]">
                <label class="flex cursor-pointer items-center gap-1.5 select-none">
                  <span class="relative flex h-[22px] w-[22px] items-center justify-center">
                    <input
                      type="checkbox"
                      class="peer h-[18px] w-[18px] cursor-pointer appearance-none rounded-full border-2 border-[var(--color-line)] bg-white transition-colors duration-200 checked:border-[var(--color-brand-500)] checked:bg-[var(--color-brand-500)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
                      :checked="allSelected"
                      :disabled="!validCount || mutating"
                      :aria-label="t('cart.selectAll')"
                      @change="toggleSelectAll"
                    />
                    <UIcon
                      name="lucide:check"
                      class="pointer-events-none absolute h-3 w-3 text-white opacity-0 transition-opacity duration-150 peer-checked:opacity-100 motion-reduce:transition-none"
                      aria-hidden="true"
                    />
                  </span>
                  <span class="whitespace-nowrap text-[12px] font-bold text-[var(--color-ink-soft)]">{{ t('cart.selectAll') }}</span>
                </label>
                <span>{{ t('shop.cartTableGoods') }}</span>
                <span>{{ t('shop.cartTablePrice') }}</span>
                <span class="text-center">{{ t('shop.cartTableQty') }}</span>
                <span>{{ t('shop.cartTableSubtotal') }}</span>
                <span>{{ t('shop.cartTableAction') }}</span>
              </div>

              <!-- 移动端工具行：表头在移动端隐藏，全选必须有落脚点 -->
              <div class="flex items-center justify-between gap-3 border-b border-[var(--color-line-soft)] px-3.5 py-3 lg:hidden">
                <label class="flex cursor-pointer items-center gap-2 select-none">
                  <span class="relative flex h-[22px] w-[22px] items-center justify-center">
                    <input
                      type="checkbox"
                      class="peer h-[18px] w-[18px] cursor-pointer appearance-none rounded-full border-2 border-[var(--color-line)] bg-white transition-colors duration-200 checked:border-[var(--color-brand-500)] checked:bg-[var(--color-brand-500)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
                      :checked="allSelected"
                      :disabled="!validCount || mutating"
                      :aria-label="t('cart.selectAll')"
                      @change="toggleSelectAll"
                    />
                    <UIcon
                      name="lucide:check"
                      class="pointer-events-none absolute h-3 w-3 text-white opacity-0 transition-opacity duration-150 peer-checked:opacity-100 motion-reduce:transition-none"
                      aria-hidden="true"
                    />
                  </span>
                  <span class="text-[13px] font-bold text-[var(--color-ink-soft)]">{{ t('cart.selectAll') }}</span>
                </label>
                <span class="text-[12px] tabular text-[var(--color-muted)]">
                  {{ t('cart.selectedCount', { count: selectedQuantity }) }}
                </span>
              </div>

              <!-- 可结算商品行 -->
              <ul v-if="validItems.length" class="divide-y divide-[var(--color-line-soft)]">
                <CartLineItem
                  v-for="item in validItems"
                  :key="item.id"
                  :item="item"
                  :selected="isSelected(item.id)"
                  :pending="pendingIds.includes(item.id)"
                  @toggle="toggleSelect"
                  @remove="handleRemove"
                  @update-quantity="handleQuantity"
                  @fix-quantity="handleQuantity"
                />
              </ul>
              <!-- 全部失效时不留一张空表：给一行如实的说明 -->
              <p v-else class="px-4 py-10 text-center text-[13px] text-[var(--color-muted)]">
                {{ t('cart.groupValid') }} · 0
              </p>

              <!-- 底部工具栏：破坏性批量操作集中在这里 -->
              <div class="flex items-center justify-between gap-3 border-t border-[var(--color-line-soft)] px-3.5 py-3 sm:px-4">
                <button
                  type="button"
                  class="inline-flex cursor-pointer items-center gap-1.5 rounded-full border border-[var(--color-line)] px-3.5 py-2 text-[13px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-error)] hover:text-[var(--color-error)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
                  :disabled="mutating || !selectedItems.length"
                  @click="askConfirm('remove')"
                >
                  <UIcon name="lucide:trash-2" class="h-4 w-4" aria-hidden="true" />
                  {{ t('cart.removeSelected') }}
                  <span v-if="selectedQuantity" class="tabular text-[var(--color-muted)]">({{ selectedQuantity }})</span>
                </button>

                <button
                  type="button"
                  class="inline-flex cursor-pointer items-center gap-1.5 rounded-full px-3.5 py-2 text-[13px] font-medium text-[var(--color-muted)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
                  :disabled="mutating"
                  @click="askConfirm('clear')"
                >
                  <UIcon name="lucide:trash" class="h-4 w-4" aria-hidden="true" />
                  {{ t('cart.clearCart') }}
                </button>
              </div>
            </div>

            <!-- 失效商品：默认折叠，只占一行高 -->
            <section v-if="hasInvalid" class="ui-card mt-5 overflow-hidden">
              <div class="flex items-center gap-2.5 px-3.5 py-3.5 sm:px-4">
                <button
                  type="button"
                  class="flex min-w-0 flex-1 cursor-pointer items-center gap-2.5 text-left"
                  :aria-expanded="invalidOpen"
                  aria-controls="cart-invalid-panel"
                  @click="invalidOpen = !invalidOpen"
                >
                  <span class="h-2 w-2 shrink-0 rounded-full bg-[var(--color-muted)]" aria-hidden="true"></span>
                  <span class="shrink-0 text-[14px] font-black text-[var(--color-ink)]">{{ t('shop.cartInvalidTitle') }}</span>
                  <span class="shrink-0 text-[13px] tabular text-[var(--color-muted)]">
                    {{ invalidItems.length }} {{ t('shop.itemsUnit') }}
                  </span>
                  <span class="hidden min-w-0 truncate text-[12px] text-[var(--color-muted)] sm:inline">
                    · {{ t('cart.groupInvalidHint') }}
                  </span>
                  <UIcon
                    name="lucide:chevron-down"
                    class="ml-auto h-4 w-4 shrink-0 text-[var(--color-muted)] transition-transform duration-200 motion-reduce:transition-none"
                    :class="invalidOpen ? 'rotate-180' : ''"
                    aria-hidden="true"
                  />
                </button>
                <button
                  type="button"
                  class="ui-pill ui-pill-ghost h-8 shrink-0 px-3 text-[12px] disabled:opacity-40"
                  :disabled="mutating"
                  @click="askConfirm('invalid')"
                >
                  {{ t('cart.clearInvalid') }}
                </button>
              </div>

              <ul
                v-show="invalidOpen"
                id="cart-invalid-panel"
                class="divide-y divide-[var(--color-line-soft)] border-t border-[var(--color-line-soft)]"
              >
                <CartLineItem
                  v-for="item in invalidItems"
                  :key="item.id"
                  :item="item"
                  :selected="false"
                  :pending="pendingIds.includes(item.id)"
                  @toggle="toggleSelect"
                  @remove="handleRemove"
                  @update-quantity="handleQuantity"
                  @fix-quantity="handleQuantity"
                />
              </ul>
            </section>
          </div>

          <!-- 右栏：结算明细（桌面端常驻跟随） -->
          <aside class="mt-6 lg:col-span-1 lg:mt-0 lg:sticky lg:top-[132px]">
            <CartSummaryCard
              :selected-quantity="selectedQuantity"
              :goods-amount="selectedAmount"
              :payable="payable"
              :coupon-amount="couponDiscount"
              :shipping-free="allSelectedFreeShipping"
              :saved-amount="savedAmount"
            />

            <!--
              去结算（本次新增）。

              只把「已勾选且可结算」的条目交给结算页：传的是 购物车明细ID + 商品 + 规格 + 数量，
              **不传任何金额** —— 结算页会调 /api/order/preview，由服务端重新算钱。

              为什么按钮加在页面里而不是改 CartSummaryCard：本次约束是"只新增组件、不改已有组件"，
              因此 CartSummaryCard 内部那个 disabled 的去结算按钮保持原样（它的文案已过时，
              需要在后续放开组件改动时一并删除）。
            -->
            <button
              type="button"
              class="mt-4 flex h-12 w-full items-center justify-center gap-2 rounded-full bg-[var(--color-brand-500)] text-[15px] font-bold text-white transition-colors duration-200 hover:bg-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:bg-[var(--color-ink)]/20 motion-reduce:transition-none"
              :disabled="mutating || !selectedItems.length"
              @click="goCheckout"
            >
              <UIcon name="lucide:credit-card" class="h-4 w-4" aria-hidden="true" />
              {{ t('cart.checkout') }}
              <span v-if="selectedQuantity" class="tabular">({{ selectedQuantity }})</span>
            </button>
            <p class="mt-2.5 text-center text-[11px] leading-relaxed text-[var(--color-muted)]">
              {{ t('shop.checkoutReadyHint') }}
            </p>
          </aside>
        </div>

        <!-- 猜你喜欢横向商品条：没有真实商品就整块不渲染 -->
        <section v-if="likeProducts.length" class="mt-8">
          <div class="mb-4 flex flex-wrap items-end justify-between gap-x-4 gap-y-1">
            <h2 class="flex items-center gap-2 text-[18px] font-black text-[var(--color-ink)]">
              <UIcon name="lucide:flame" class="h-5 w-5 text-[var(--color-brand-500)]" aria-hidden="true" />
              {{ t('shop.likeTitle') }}
            </h2>
            <p class="text-[12px] text-[var(--color-muted)]">{{ t('shop.likeSubtitle') }}</p>
          </div>

          <ul class="no-scrollbar flex snap-x snap-mandatory gap-4 overflow-x-auto pb-1">
            <li
              v-for="product in likeProducts"
              :key="product.id"
              class="w-[72%] shrink-0 snap-start sm:w-[46%] lg:w-auto lg:shrink lg:flex-1"
            >
              <StoreProductCard :product="product" />
            </li>
          </ul>
        </section>
      </template>
    </div>

    <!-- 二次确认：删除选中 / 清理失效 / 清空购物车都是破坏性操作 -->
    <UModal
      v-model:open="confirmState.open"
      :title="confirmCopy.title"
      :description="confirmCopy.description"
      :ui="{ content: 'rounded-[var(--radius-card)]' }"
    >
      <template #footer>
        <div class="flex w-full justify-end gap-3">
          <button
            type="button"
            class="cursor-pointer rounded-full border border-[var(--color-line)] px-5 py-2.5 text-sm font-semibold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] motion-reduce:transition-none"
            @click="confirmState.open = false"
          >
            {{ t('cart.cancel') }}
          </button>
          <button
            type="button"
            class="cursor-pointer rounded-full bg-[var(--color-error)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-error-600)] motion-reduce:transition-none"
            @click="runConfirmedAction()"
          >
            {{ t('cart.confirmOk') }}
          </button>
        </div>
      </template>
    </UModal>

    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
/**
 * 购物车是私有页面：未登录 / 登录过期时由 auth 中间件重定向到登录页，
 * 登录成功后回到购物车（redirect 参数由中间件写入）。
 */
definePageMeta({ middleware: 'auth' })

import { extractCartErrorMessage, useCart } from '~~/composables/useCart'
import { writeCheckoutSnapshot } from '~~/composables/useCheckoutSnapshot'
import { useStorePrice, useStoreProductList } from '~~/composables/useProducts'
import { toNumberOrNull } from '~~/types/shop-product'
import type { StoreCartItem } from '~~/types/shop-cart'

/**
 * 购物车页（重新设计版）。
 *
 * 布局：标题行 → 凑单进度（有真实促销才出现）→ 左「表头化清单」+ 右「常驻结算明细」
 *       → 失效商品折叠组 → 猜你喜欢 → 页脚。
 *
 * 三条硬规则（沿用项目既有约定）：
 *   1. 所有数据来自后端真实字段；优惠券/运费/促销没有数据来源就**整块不渲染**，
 *      绝不写死"满 300 减 30"、"-¥30"、"运费 ¥12"；
 *   2. 购物车的取数与操作全部来自 useCart()，本页只负责展示与交互编排，行为一行未改；
 *   3. 「去结算」本次已接通：只把已勾选的可结算条目快照写进 sessionStorage 再跳 /checkout，
 *      **不传任何金额**（金额由结算页调服务端 preview 计算）。
 */

const { t, locale } = useI18n()
const localePath = useLocalePath()
const route = useRoute()
const toast = useToast()
const { formatCompact } = useStorePrice()

const {
  items,
  summary,
  loading,
  mutating,
  unauthorized,
  validItems,
  invalidItems,
  validCount,
  selectedQuantity,
  selectedAmount,
  allSelected,
  hasInvalid,
  isSelected,
  toggleSelect,
  toggleSelectAll,
  fetchCart,
  updateQuantity,
  removeItem,
  batchRemove,
  clearCart,
  clearInvalid,
} = useCart()

/** SSR 首屏不拉购物车：服务端相对路径 $fetch 不会带 httpOnly cookie，必然 401（保持原逻辑） */
const ready = ref(false)
const loadError = ref('')
const pendingIds = ref<string[]>([])

/** 失效商品分组默认折叠（重新设计的要求：只占一行高） */
const invalidOpen = ref(false)

const confirmState = reactive({
  open: false,
  kind: 'clear' as 'clear' | 'remove' | 'invalid',
})

const loginLink = computed(() => localePath({
  path: '/login',
  query: { redirect: route.fullPath },
}))

/** 登录态下才显示"共 N 件商品"；未登录显示登录引导文案，已登录空车则交给空状态卡片说明 */
const showSubtitle = computed(() => !unauthorized.value && items.value.length > 0)

// ---------------------------------------------------------------------------
// 凑单进度（真实促销数据）
// ---------------------------------------------------------------------------

/**
 * 进行中的促销规则（BFF：server/api/promotions/active.get.ts）。
 * - query/key 都跟着 locale 变：key 一变 Nuxt 就会重新取，切换语言时活动标题跟着变
 *   （取法与首页 index.vue 一致，只是 key 多了 store-cart- 前缀，避免两个调用点共享缓存时
 *    选项不一致）；
 * - 带 lang 是因为后端将来要按语言解析活动标题（接口注释里写明显式 ?lang= 优先）；
 * - server: false —— 与购物车一致，首屏不在服务端发这个请求；
 * - default: () => [] —— 接口不存在 / 请求失败 / 返回空数组都退化成"没有促销"，
 *   绝不会让购物车页崩掉。
 */
const { data: activePromotions } = useFetch<unknown[]>('/api/promotions/active', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `store-cart-active-promotions-${locale.value}`),
  default: () => [],
  server: false,
})

interface BundleRule {
  /** 满减门槛，来自后端活动配置 */
  target: number
  /** 已凑单金额：后端给了就用后端的，否则用本地真实的"已选商品金额" */
  current: number
}

/**
 * 从活动列表里挑出「满减凑单」规则。
 *
 * 【重要】当前 BFF 的 ShopActivePromotion 只返回
 *   { id, type, title, startTime, endTime, linkUrl, productId }
 * ——**没有满减门槛字段**（threshold_amount 只在 docs/store-redesign/后端促销字段清单.md
 * §2.1 的表结构里，Java 接口尚未提供）。因此这里的判定是：
 *   读不到门槛（thresholdAmount / threshold）就返回 null → 凑单卡整块不渲染。
 * 宁可不显示，也绝不前端写死"满 300 减 30"。等后端补上门槛与已凑单金额后，
 * 这里不用改就能正确渲染。
 */
const bundleRule = computed<BundleRule | null>(() => {
  const list = Array.isArray(activePromotions.value) ? activePromotions.value : []
  const now = Date.now()
  for (const raw of list) {
    const entry = (raw ?? {}) as Record<string, unknown>
    const type = String(entry.type ?? '').toUpperCase()
    // 只有满减类活动才有"凑单门槛"；直降/折扣/秒杀拿来做进度条是误导
    if (type && type !== 'FULL_CUT') continue

    // 活动时间窗：已结束 / 还没开始的活动都不该拿来引导凑单（时间字段与 BFF 契约一致）
    const startAt = Date.parse(String(entry.startTime ?? ''))
    const endAt = Date.parse(String(entry.endTime ?? ''))
    if (Number.isFinite(endAt) && endAt <= now) continue
    if (Number.isFinite(startAt) && startAt > now) continue

    const target = toNumberOrNull(entry.thresholdAmount) ?? toNumberOrNull(entry.threshold)
    if (target === null || target <= 0) continue
    const backendCurrent = toNumberOrNull(entry.currentAmount) ?? toNumberOrNull(entry.current)
    return { target, current: backendCurrent ?? selectedAmount.value }
  }
  return null
})

const bundlePercent = computed(() => {
  if (!bundleRule.value) return 0
  const ratio = bundleRule.value.current / bundleRule.value.target
  return Math.min(100, Math.max(0, Math.round(ratio * 100)))
})

const bundleText = computed(() => bundleRule.value
  ? t('shop.cartBundleProgress', {
      current: formatCompact(bundleRule.value.current),
      target: formatCompact(bundleRule.value.target),
    })
  : '')

// ---------------------------------------------------------------------------
// 结算明细的真实数据
// ---------------------------------------------------------------------------

/** 勾选的可结算条目（批量删除、运费判断共用同一份口径） */
const selectedItems = computed(() => validItems.value.filter(item => isSelected(item.id)))

/** 购物车明细当前还没有 freeShipping 字段（见 types/shop-cart.ts）；后端补上后这里自动生效 */
type CartItemWithShipping = StoreCartItem & { freeShipping?: boolean }

/** 只有"全部已选商品都确认包邮"才算免运费；拿不到依据时为 false → 卡上显示无数据占位 */
const allSelectedFreeShipping = computed(() => (
  selectedItems.value.length > 0
  && selectedItems.value.every(item => (item as CartItemWithShipping).freeShipping === true)
))

/**
 * 优惠券抵扣。
 * 后端没有「用户可用券」接口（见 docs/store-redesign/后端促销字段清单.md §2.3），
 * 所以这里恒为 null：优惠券行、"已为你节省"整块不渲染，也绝不会凭空减掉一个金额。
 * 接口上线后只需给这个 computed 赋真实值，模板不用改。
 */
const couponDiscount = computed<number | null>(() => null)

/** 应付总额 = 商品总额 - 真实优惠（当前没有优惠，所以等于商品总额） */
const payable = computed(() => (
  Math.max(0, Math.round((selectedAmount.value - (couponDiscount.value ?? 0)) * 100) / 100)
))

const savedAmount = computed(() => (
  couponDiscount.value !== null && couponDiscount.value > 0 ? couponDiscount.value : null
))

// ---------------------------------------------------------------------------
// 猜你喜欢
// ---------------------------------------------------------------------------

const likePageNo = ref(1)
const { data: likePage } = await useStoreProductList({ pageNo: likePageNo, pageSize: 4 })
const likeProducts = computed(() => likePage.value?.records ?? [])

// ---------------------------------------------------------------------------
// 交互（逻辑与重构前保持一致）
// ---------------------------------------------------------------------------

const confirmCopy = computed(() => {
  if (confirmState.kind === 'remove') {
    return {
      title: t('cart.confirmRemoveTitle'),
      description: t('cart.confirmRemoveDesc', { count: selectedQuantity.value }),
    }
  }
  if (confirmState.kind === 'invalid') {
    return {
      title: t('cart.clearInvalid'),
      description: t('cart.confirmInvalidDesc', { count: summary.value.invalidItems }),
    }
  }
  return {
    title: t('cart.confirmClearTitle'),
    description: t('cart.confirmClearDesc', { count: summary.value.totalItems }),
  }
})

function askConfirm(kind: 'clear' | 'remove' | 'invalid') {
  if (kind === 'remove' && !selectedItems.value.length) return
  confirmState.kind = kind
  confirmState.open = true
}

function notifyError(error: unknown) {
  toast.add({
    title: t('cart.actionFailed'),
    description: extractCartErrorMessage(error, t('cart.loadErrorDesc')),
    color: 'error',
    icon: 'lucide:circle-alert',
  })
}

async function runConfirmedAction() {
  confirmState.open = false
  // 先取快照：清空/清理之后 summary 会立刻归零，届时再读就只剩 0 了
  const invalidCount = summary.value.invalidItems
  try {
    if (confirmState.kind === 'remove') {
      const ids = items.value.filter(item => item.selectable && isSelected(item.id)).map(item => item.id)
      if (!ids.length) return
      await batchRemove(ids)
      toast.add({ title: t('cart.removedToast'), color: 'success', icon: 'lucide:check' })
    } else if (confirmState.kind === 'invalid') {
      await clearInvalid()
      toast.add({
        title: invalidCount
          ? t('cart.invalidClearedToast', { count: invalidCount })
          : t('cart.noInvalidToast'),
        color: 'success',
        icon: 'lucide:check',
      })
    } else {
      await clearCart()
      toast.add({ title: t('cart.clearedToast'), color: 'success', icon: 'lucide:check' })
    }
  } catch (error) {
    notifyError(error)
  }
}

/** 单条操作统一走"标记 pending → 调接口 → 清 pending"，防止连点重复提交 */
async function withPending(itemId: string, action: () => Promise<unknown>) {
  if (pendingIds.value.includes(itemId)) return
  pendingIds.value = [...pendingIds.value, itemId]
  try {
    await action()
  } catch (error) {
    notifyError(error)
  } finally {
    pendingIds.value = pendingIds.value.filter(id => id !== itemId)
  }
}

async function handleRemove(itemId: string) {
  await withPending(itemId, async () => {
    await removeItem(itemId)
    toast.add({ title: t('cart.removedToast'), color: 'success', icon: 'lucide:check' })
  })
}

async function handleQuantity(itemId: string, quantity: number) {
  const item = items.value.find(entry => entry.id === itemId)
  if (!item || quantity < 1 || quantity === item.quantity) return
  await withPending(itemId, () => updateQuantity(itemId, quantity))
}

/**
 * 去结算（本次新增）。
 *
 * 只把「已勾选且可结算」的条目传给结算页：购物车明细 ID + 商品 + 规格 + 数量，
 * **不传任何金额** —— 结算页会调 /api/order/preview 由服务端重新算钱，
 * 这样"购物车看到的价格"与"实际下单的价格"必然同源。
 *
 * 用 sessionStorage 而不是内存状态：结算页刷新后不该把用户踢回购物车；
 * 用 sessionStorage 而不是 localStorage：关掉标签页这份"待下单清单"就该消失。
 * cartItemId 会随下单一起提交，后端据此**只清除这些条目**，
 * 购物车里没勾选的商品不会被顺手删掉。
 *
 * 快照的读写收敛在 composables/useCheckoutSnapshot.ts（购物车 / 立即购买 / 结算页三处共用）。
 */
async function goCheckout() {
  const chosen = selectedItems.value
  if (!chosen.length) return
  const written = writeCheckoutSnapshot(chosen.map(item => ({
    cartItemId: item.id,
    productId: item.productId,
    skuId: item.skuId || '',
    quantity: item.quantity,
  })))
  if (!written) {
    // sessionStorage 不可用（隐私模式）时不能静默跳转：否则结算页会显示"没有待结算商品"，
    // 用户完全不知道发生了什么
    toast.add({
      title: t('cart.checkoutSnapshotFailed'),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
    return
  }
  await navigateTo(localePath('/checkout'))
}

async function reload() {
  loadError.value = ''
  try {
    await fetchCart()
  } catch (error) {
    loadError.value = extractCartErrorMessage(error, t('cart.loadErrorDesc'))
  } finally {
    ready.value = true
  }
}

onMounted(reload)

useSeoMeta({
  title: () => t('cart.title'),
  description: () => t('cart.emptyDesc'),
  robots: 'noindex, nofollow',
})
</script>
