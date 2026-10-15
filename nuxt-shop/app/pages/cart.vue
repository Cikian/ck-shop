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
/** 购物车是私有页面，未登录 / 登录过期由 auth 中间件重定向到登录页并回跳 */
definePageMeta({ middleware: 'auth' })

import { useCartPage } from '~~/composables/useCartPage'

/**
 * 购物车（渲染层）。
 *
 * 展示：四种状态（未登录 / 加载失败 / 加载中 / 空车）+ 凑单提示 + 正常条目 + 失效商品 + 猜你喜欢 + 确认弹层。
 * 数据、金额派生与全部操作都在 composables/useCartPage.ts。
 *
 * 本文件只保留模板直接依赖的 i18n 文案（t），以及必须在本组件注册的生命周期（onMounted 拉购物车）。
 */
const { t } = useI18n()

const {
  ready,
  loadError,
  pendingIds,
  invalidOpen,
  confirmState,
  loginLink,
  showSubtitle,
  bundleRule,
  bundlePercent,
  bundleText,
  selectedItems,
  allSelectedFreeShipping,
  couponDiscount,
  payable,
  savedAmount,
  likeProducts,
  confirmCopy,
  askConfirm,
  runConfirmedAction,
  handleRemove,
  handleQuantity,
  goCheckout,
  reload,
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
} = await useCartPage()

/** SSR 首屏不拉购物车（相对路径 $fetch 不带 httpOnly cookie，必然 401）——挂载后再拉 */
onMounted(reload)

useSeoMeta({
  title: () => t('cart.title'),
  description: () => t('cart.emptyDesc'),
  robots: 'noindex, nofollow',
})
</script>
