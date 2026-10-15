<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <div class="mx-auto max-w-7xl px-2 pb-16 sm:px-3 lg:px-4">
      <!-- 标题行 -->
      <header class="mb-5 flex flex-wrap items-end justify-between gap-x-4 gap-y-2">
        <div>
          <h1 class="text-[26px] font-black leading-none text-[var(--color-ink)] sm:text-[32px]">
            {{ t('order.checkoutTitle') }}
          </h1>
          <p v-if="preview" class="mt-1.5 text-[13px] tabular text-[var(--color-muted)]">
            {{ t('order.totalQuantity', { count: preview.amount?.totalQuantity ?? 0 }) }}
          </p>
        </div>
        <NuxtLinkLocale
          to="/cart"
          class="group inline-flex cursor-pointer items-center gap-1 text-[13px] font-medium text-[var(--color-muted)] transition-colors duration-200 hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
        >
          <UIcon name="lucide:chevron-left" class="h-3.5 w-3.5" aria-hidden="true" />
          {{ t('order.checkoutBackToCart') }}
        </NuxtLinkLocale>
      </header>

      <!-- 未登录 -->
      <section v-if="unauthorized" class="ui-card px-6 py-16 text-center">
        <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
          <UIcon name="lucide:lock" class="h-8 w-8 text-[var(--color-brand-500)]" aria-hidden="true" />
        </span>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('order.loginRequired') }}</h2>
        <p class="mx-auto mt-2 max-w-md text-sm text-[var(--color-muted)]">{{ t('order.loginRequiredDesc') }}</p>
        <NuxtLinkLocale :to="loginLink" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm">
          <UIcon name="lucide:log-in" class="h-4 w-4" aria-hidden="true" />
          {{ t('order.loginAction') }}
        </NuxtLinkLocale>
      </section>

      <!-- 加载失败 -->
      <section v-else-if="loadError" class="ui-card px-6 py-16 text-center">
        <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-error)]/10">
          <UIcon name="lucide:wifi-off" class="h-8 w-8 text-[var(--color-error)]" aria-hidden="true" />
        </span>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('order.loadError') }}</h2>
        <p class="mt-2 text-sm text-[var(--color-muted)]">{{ loadError }}</p>
        <button type="button" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm" @click="bootstrap()">
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
          {{ t('common.retry') }}
        </button>
      </section>

      <!-- 没有待结算商品（直接从地址栏打开 /checkout，或购物车勾选已被清空） -->
      <section v-else-if="ready && !snapshotItems.length" class="ui-card px-6 py-20 text-center">
        <span class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
          <UIcon name="lucide:shopping-cart" class="h-9 w-9 text-[var(--color-brand-400)]" aria-hidden="true" />
        </span>
        <h2 class="mt-6 text-xl font-bold text-[var(--color-ink)]">{{ t('order.checkoutEmpty') }}</h2>
        <p class="mx-auto mt-2 max-w-md text-sm text-[var(--color-muted)]">{{ t('order.checkoutEmptyDesc') }}</p>
        <NuxtLinkLocale to="/cart" class="ui-pill ui-pill-brand mt-6 h-12 px-7 text-sm">
          <UIcon name="lucide:arrow-right" class="h-4 w-4" aria-hidden="true" />
          {{ t('order.checkoutBackToCart') }}
        </NuxtLinkLocale>
      </section>

      <!-- 骨架屏 -->
      <section v-else-if="loading || !preview" aria-busy="true" :aria-label="t('common.loading')">
        <div class="lg:grid lg:grid-cols-3 lg:items-start lg:gap-6">
          <div class="space-y-5 lg:col-span-2">
            <div class="ui-card space-y-3 p-5">
              <div class="h-4 w-1/4 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
              <div class="h-16 animate-pulse rounded-[var(--radius-card-sm)] bg-[var(--color-line-soft)]"></div>
            </div>
            <div class="ui-card space-y-3 p-5">
              <div class="h-4 w-1/3 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
              <div v-for="index in 2" :key="index" class="h-20 animate-pulse rounded-[var(--radius-card-sm)] bg-[var(--color-line-soft)]"></div>
            </div>
          </div>
          <div class="ui-card mt-6 space-y-3 p-5 lg:mt-0">
            <div class="h-4 w-1/3 animate-pulse rounded bg-[var(--color-line-soft)]"></div>
            <div class="h-3 w-full animate-pulse rounded bg-[var(--color-line-soft)]"></div>
            <div class="h-12 w-full animate-pulse rounded-full bg-[var(--color-line-soft)]"></div>
          </div>
        </div>
      </section>

      <!-- 正文 -->
      <div v-else class="lg:grid lg:grid-cols-3 lg:items-start lg:gap-6">
        <!-- 左：地址 + 商品 + 备注 -->
        <div class="space-y-5 lg:col-span-2">
          <CheckoutAddressPicker
            v-model="selectedAddressId"
            :addresses="addresses"
            :loading="addressLoading"
          />

          <section class="ui-card overflow-hidden">
            <header class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-b border-[var(--color-line-soft)] px-4 py-3">
              <h2 class="flex items-center gap-2 text-[15px] font-black text-[var(--color-ink)]">
                <UIcon name="lucide:package" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
                {{ t('order.checkoutItems') }}
              </h2>
              <button
                type="button"
                class="inline-flex cursor-pointer items-center gap-1 text-[13px] font-medium text-[var(--color-muted)] transition-colors duration-200 hover:text-[var(--color-brand-600)] disabled:opacity-40 motion-reduce:transition-none"
                :disabled="loading"
                @click="loadPreview()"
              >
                <UIcon name="lucide:refresh-cw" class="h-3.5 w-3.5" aria-hidden="true" />
                {{ t('order.checkoutRefresh') }}
              </button>
            </header>

            <ul class="divide-y divide-[var(--color-line-soft)]">
              <li
                v-for="(item, index) in preview.items"
                :key="`${item.productId}-${item.skuId || ''}-${index}`"
                class="flex gap-3 px-4 py-4"
                :class="item.available === false ? 'bg-[var(--color-error-50)]/60' : ''"
              >
                <span class="h-[72px] w-[72px] shrink-0 overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-line-soft)]">
                  <img
                    v-if="item.productImage"
                    :src="item.productImage"
                    :alt="item.productName || ''"
                    width="72"
                    height="72"
                    class="h-full w-full object-cover"
                    loading="lazy"
                  >
                  <span v-else class="flex h-full w-full items-center justify-center">
                    <UIcon name="lucide:image" class="h-6 w-6 text-[var(--color-muted)]" aria-hidden="true" />
                  </span>
                </span>

                <div class="min-w-0 flex-1">
                  <p class="line-clamp-2 text-[14px] font-bold text-[var(--color-ink)]">
                    {{ item.productName || '—' }}
                  </p>
                  <p class="mt-1 truncate text-[12px] text-[var(--color-muted)]">
                    {{ item.skuName || t('cart.noSpec') }}
                  </p>
                  <!-- 不可结算时把后端的 invalidReason 原样说出来，而不是一句"结算失败" -->
                  <p
                    v-if="item.available === false && item.invalidReason"
                    class="mt-1.5 inline-flex items-center gap-1 rounded-full bg-[var(--color-error)]/10 px-2 py-0.5 text-[12px] font-bold text-[var(--color-error-600)]"
                  >
                    <UIcon name="lucide:circle-alert" class="h-3.5 w-3.5" aria-hidden="true" />
                    {{ item.invalidReason }}
                  </p>
                </div>

                <div class="shrink-0 text-right">
                  <p class="price text-[16px]">{{ format(item.price) }}</p>
                  <p class="mt-1 text-[12px] tabular text-[var(--color-muted)]">× {{ item.quantity ?? 0 }}</p>
                  <p class="mt-1 text-[13px] font-bold tabular text-[var(--color-ink)]">
                    {{ format(item.actualAmount ?? item.productAmount) }}
                  </p>
                </div>
              </li>
            </ul>
          </section>

          <!-- 买家备注 -->
          <section class="ui-card p-4">
            <label for="checkout-remark" class="mb-1.5 flex items-center gap-2 text-[15px] font-black text-[var(--color-ink)]">
              <UIcon name="lucide:message-square" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
              {{ t('order.checkoutRemark') }}
            </label>
            <textarea
              id="checkout-remark"
              v-model="buyerRemark"
              rows="3"
              maxlength="200"
              :placeholder="t('order.checkoutRemarkPlaceholder')"
              class="w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-3 py-2.5 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            ></textarea>
          </section>
        </div>

        <!-- 右：金额明细 + 提交 -->
        <aside class="mt-6 lg:col-span-1 lg:mt-0 lg:sticky lg:top-[132px]">
          <section class="ui-card p-5" :aria-label="t('order.checkoutAmount')">
            <h2 class="text-[17px] font-black text-[var(--color-ink)]">{{ t('order.checkoutAmount') }}</h2>

            <!--
              【绝不算钱】这里每一行都是 preview.amount 里的原始数字，
              前端只做「数字 -> 两位小数文本」的格式化，不做任何加减乘除。
            -->
            <dl class="mt-4 space-y-3 text-[13px]">
              <div v-if="hasNumber(amount?.totalAmount)" class="flex items-center justify-between gap-3">
                <dt class="text-[var(--color-muted)]">{{ t('order.amountTotal') }}</dt>
                <dd class="tabular text-[var(--color-muted)] line-through">{{ format(amount?.totalAmount) }}</dd>
              </div>

              <div class="flex items-center justify-between gap-3">
                <dt class="text-[var(--color-muted)]">{{ t('order.amountProduct') }}</dt>
                <dd class="font-bold tabular text-[var(--color-ink)]">{{ format(amount?.productAmount) }}</dd>
              </div>

              <div v-if="isPositive(amount?.promotionDiscount)" class="flex items-center justify-between gap-3">
                <dt class="text-[var(--color-muted)]">{{ t('order.amountPromotion') }}</dt>
                <dd class="font-bold tabular text-[var(--color-brand-600)]">-{{ format(amount?.promotionDiscount) }}</dd>
              </div>

              <div v-if="isPositive(amount?.couponDiscount)" class="flex items-center justify-between gap-3">
                <dt class="text-[var(--color-muted)]">{{ t('order.amountCoupon') }}</dt>
                <dd class="font-bold tabular text-[var(--color-brand-600)]">-{{ format(amount?.couponDiscount) }}</dd>
              </div>

              <div v-if="isPositive(amount?.pointsDiscount)" class="flex items-center justify-between gap-3">
                <dt class="text-[var(--color-muted)]">{{ t('order.amountPoints') }}</dt>
                <dd class="font-bold tabular text-[var(--color-brand-600)]">-{{ format(amount?.pointsDiscount) }}</dd>
              </div>

              <div v-if="isPositive(amount?.memberDiscount)" class="flex items-center justify-between gap-3">
                <dt class="text-[var(--color-muted)]">{{ t('order.amountMember') }}</dt>
                <dd class="font-bold tabular text-[var(--color-brand-600)]">-{{ format(amount?.memberDiscount) }}</dd>
              </div>

              <div v-if="isPositive(amount?.discountAmount)" class="flex items-center justify-between gap-3">
                <dt class="text-[var(--color-muted)]">{{ t('order.amountDiscount') }}</dt>
                <dd class="font-bold tabular text-[var(--color-brand-600)]">-{{ format(amount?.discountAmount) }}</dd>
              </div>

              <div class="flex items-center justify-between gap-3">
                <dt class="text-[var(--color-muted)]">{{ t('order.amountFreight') }}</dt>
                <dd class="font-bold tabular text-[var(--color-ink)]">{{ format(amount?.freightAmount) }}</dd>
              </div>

              <div v-if="isPositive(amount?.otherFee)" class="flex items-center justify-between gap-3">
                <dt class="text-[var(--color-muted)]">{{ t('order.amountOtherFee') }}</dt>
                <dd class="font-bold tabular text-[var(--color-ink)]">{{ format(amount?.otherFee) }}</dd>
              </div>
            </dl>

            <div class="mt-4 border-t border-[var(--color-line)] pt-4">
              <div class="flex items-center justify-between gap-3">
                <span class="text-[15px] font-black text-[var(--color-ink)]">{{ t('order.amountPay') }}</span>
                <span class="price text-[26px] leading-none whitespace-nowrap sm:text-[30px]">{{ format(amount?.payAmount) }}</span>
              </div>
              <p v-if="tokenExpireText" class="mt-2 text-right text-[12px] text-[var(--color-muted)]">
                {{ t('order.checkoutTokenExpire', { time: tokenExpireText }) }}
              </p>
            </div>

            <!-- 有不可结算的明细：说清有几件，并禁用提交 -->
            <p
              v-if="hasInvalidItem"
              class="mt-4 flex items-start gap-1.5 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-3 py-2.5 text-[12px] font-medium text-[var(--color-error-600)]"
            >
              <UIcon name="lucide:circle-alert" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>{{ t('order.checkoutInvalidTip', { count: invalidCount }) }}</span>
            </p>

            <p
              v-else-if="!selectedAddressId"
              class="mt-4 flex items-start gap-1.5 rounded-[var(--radius-card-sm)] bg-[var(--color-warning-50)] px-3 py-2.5 text-[12px] font-medium text-[var(--color-warning-600)]"
            >
              <UIcon name="lucide:map-pin" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>{{ t('order.checkoutAddressRequired') }}</span>
            </p>

            <button
              type="button"
              class="mt-5 flex h-12 w-full items-center justify-center gap-2 rounded-full bg-[var(--color-brand-500)] text-[15px] font-bold text-white transition-colors duration-200 hover:bg-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:bg-[var(--color-ink)]/20 motion-reduce:transition-none"
              :disabled="!canSubmit"
              @click="submitOrder"
            >
              <UIcon v-if="submitting" name="lucide:loader-2" class="h-4 w-4 animate-spin" aria-hidden="true" />
              {{ submitting ? t('order.checkoutSubmitting') : t('order.checkoutSubmit') }}
            </button>

            <!-- 幂等键：进页面生成一次，之后所有重试复用（后端幂等的前提） -->
            <p class="mt-2.5 text-center text-[11px] leading-relaxed text-[var(--color-muted)]">
              {{ t('order.checkoutIdempotentHint') }}
            </p>
          </section>
        </aside>
      </div>
    </div>

    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
/** 结算页是私有页面，未登录 / 登录过期由 auth 中间件重定向到登录页并回跳 */
definePageMeta({ middleware: 'auth' })

import { toIntlLocale, toNumberOrNull } from '~~/types/shop-product'
import { useStorePrice } from '~~/composables/useProducts'
import { clearCheckoutSnapshot, readCheckoutSnapshot } from '~~/composables/useCheckoutSnapshot'
import type { CheckoutSnapshotItem } from '~~/composables/useCheckoutSnapshot'
import { isPositive, isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'
import { useIdempotencyKey } from '~~/composables/useIdempotencyKey'
import { useLoginLink } from '~~/composables/useLoginLink'

/**
 * 结算页（下单闭环第一步）。
 *
 * 三条不可动摇的规则：
 *   1) **金额一律以服务端 /api/order/preview 返回为准**。
 *      本页不做任何金额运算，只把后端给的数字格式化成两位小数展示；
 *      「商品金额/优惠/运费/应付」全部来自 preview.amount。
 *   2) **idempotentKey 在进入页面时生成一次**（crypto.randomUUID()），
 *      之后无论用户点几次「提交订单」、无论网络重试多少次，都用同一个值 ——
 *      这是后端幂等的前提。若每次点击都新生成，幂等就失效了（后端也会拒绝不传的情况）。
 *      注意：点「重新计算金额」重新拿 priceToken 时**不换幂等键** ——
 *      用户的意图仍是"下这一单"，换键会产生第二笔订单。
 *   3) **allAvailable === false 时禁止提交**，并把不可结算的行标红、显示 invalidReason。
 *      让用户看到"哪一件没货了"，而不是一句"结算失败"。
 *
 * 结算数据传递：**购物车页的「去结算」与商品详情页的「立即购买」**都会把待下单条目快照
 * 写进 sessionStorage，本页读出来再调 preview。两者的区别只在 cartItemId：
 * 购物车买有 cartItemId（下单后后端只清这些条目），立即购买是空串（不清任何购物车条目）。
 * 用 sessionStorage 而不是内存状态，是因为刷新页面后
 * 用户不该被踢回购物车；用 sessionStorage 而不是 localStorage，是因为
 * 关掉标签页后这份「待下单清单」就该消失。
 */

/** 结算快照里的一条（由购物车页 / 商品详情页「立即购买」写入） */
// CheckoutSnapshotItem 的字段语义见 composables/useCheckoutSnapshot.ts
// （cartItemId 为空串表示"立即购买、不关联购物车条目"）

/** 结算/订单明细（后端 StoreOrderItemVO，字段名照抄 camelCase） */
interface PreviewItem {
  id?: string | null
  productId: string
  skuId?: string | null
  productName?: string | null
  skuName?: string | null
  productImage?: string | null
  unit?: string | null
  originalPrice?: number | null
  price?: number | null
  quantity?: number | null
  productAmount?: number | null
  discountAmount?: number | null
  actualAmount?: number | null
  promotionTitle?: string | null
  available?: boolean | null
  invalidReason?: string | null
  stock?: number | null
}

/** 金额明细（后端 OrderAmountVO，全部可能为空 -> 展示为 --） */
interface PreviewAmount {
  totalAmount?: number | null
  productAmount?: number | null
  promotionDiscount?: number | null
  couponDiscount?: number | null
  pointsDiscount?: number | null
  memberDiscount?: number | null
  discountAmount?: number | null
  freightAmount?: number | null
  otherFee?: number | null
  payAmount?: number | null
  itemCount?: number | null
  totalQuantity?: number | null
}

/** 结算预览出参（后端 OrderPreviewVO） */
interface PreviewResult {
  items: PreviewItem[]
  amount: PreviewAmount | null
  priceToken: string | null
  priceTokenExpireAt: number | null
  hasInvalidItem: boolean
  allAvailable: boolean
}

/** 下单结果（后端 OrderSubmitVO） */
interface SubmitResult {
  orderNo: string
  orderStatusLabel?: string | null
  payAmount?: number | null
  idempotentReused?: boolean | null
}

/** 收货地址（后端 AddressVO） */
interface AddressItem {
  id: string
  receiverName?: string | null
  receiverPhone?: string | null
  province?: string | null
  city?: string | null
  district?: string | null
  detailAddress?: string | null
  addressTag?: string | null
  isDefault?: boolean | null
  fullAddress?: string | null
}

const { t, locale } = useI18n()
const localePath = useLocalePath()
const route = useRoute()
const toast = useToast()
const { format } = useStorePrice()

const ready = ref(false)
const loading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const unauthorized = ref(false)

const snapshotItems = ref<CheckoutSnapshotItem[]>([])
const preview = ref<PreviewResult | null>(null)
const addresses = ref<AddressItem[]>([])
const addressLoading = ref(false)
const selectedAddressId = ref<string | null>(null)
const buyerRemark = ref('')

/**
 * 幂等键：进入结算页时生成一次（setup 顶层调用一次），所有提交重试复用同一个值。
 * 见文件头注释 —— 这是后端幂等的前提，绝不能每次点击都重新生成。
 * 生成/降级逻辑在 composables/useIdempotencyKey.ts，prefix 'order' 用于非安全上下文兜底。
 */
const idempotentKey = useIdempotencyKey('order')

const amount = computed(() => preview.value?.amount ?? null)

/** 明细里 available === false 的条数（后端也可能只给 hasInvalidItem，这里以明细为准再做兜底） */
const invalidCount = computed(() => (preview.value?.items ?? []).filter(item => item.available === false).length)

/** 后端明确说"有不可结算明细"或明细里确实有不可用的行，都禁止提交 */
const hasInvalidItem = computed(() => preview.value?.hasInvalidItem === true || invalidCount.value > 0)

/** 提交按钮可用性：全部可结算 + 有价格令牌 + 选了地址 + 没有正在提交 */
const canSubmit = computed(() => (
  !!preview.value
  && preview.value.allAvailable !== false
  && !hasInvalidItem.value
  && !!preview.value.priceToken
  && !!selectedAddressId.value
  && !submitting.value
))

/** 价格令牌过期时间（epoch 毫秒，后端直出，不存在时区解析问题） */
const tokenExpireText = computed(() => {
  const expireAt = toNumberOrNull(preview.value?.priceTokenExpireAt)
  if (expireAt === null || expireAt <= 0) return ''
  return new Intl.DateTimeFormat(toIntlLocale(locale.value), {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(expireAt))
})

const loginLink = useLoginLink()

/** 有值（含 0）即 true —— 用于"这一行该不该渲染" */
function hasNumber(value: unknown): boolean {
  return toNumberOrNull(value) !== null
}

/** 从 sessionStorage 读购物车页 / 详情页「立即购买」写入的结算快照 */
// 归一化（空值收敛、非法行过滤）在 composables/useCheckoutSnapshot.ts 里只实现一次

/** 归一化 preview 出参：数字统一收敛成 number|null，避免模板里出现字符串运算 */
function normalizePreview(raw: any): PreviewResult {
  const amountRaw = (raw?.amount ?? {}) as Record<string, unknown>
  const toNum = (value: unknown) => toNumberOrNull(value)
  return {
    items: Array.isArray(raw?.items)
      ? raw.items.map((item: any) => ({
          id: item?.id ?? null,
          productId: String(item?.productId ?? ''),
          skuId: item?.skuId ?? '',
          productName: item?.productName ?? null,
          skuName: item?.skuName ?? null,
          productImage: item?.productImage ?? null,
          unit: item?.unit ?? null,
          originalPrice: toNum(item?.originalPrice),
          price: toNum(item?.price),
          quantity: toNum(item?.quantity),
          productAmount: toNum(item?.productAmount),
          discountAmount: toNum(item?.discountAmount),
          actualAmount: toNum(item?.actualAmount),
          promotionTitle: item?.promotionTitle ?? null,
          available: item?.available === undefined || item?.available === null ? null : Boolean(item.available),
          invalidReason: item?.invalidReason ?? null,
          stock: toNum(item?.stock),
        }))
      : [],
    amount: {
      totalAmount: toNum(amountRaw.totalAmount),
      productAmount: toNum(amountRaw.productAmount),
      promotionDiscount: toNum(amountRaw.promotionDiscount),
      couponDiscount: toNum(amountRaw.couponDiscount),
      pointsDiscount: toNum(amountRaw.pointsDiscount),
      memberDiscount: toNum(amountRaw.memberDiscount),
      discountAmount: toNum(amountRaw.discountAmount),
      freightAmount: toNum(amountRaw.freightAmount),
      otherFee: toNum(amountRaw.otherFee),
      payAmount: toNum(amountRaw.payAmount),
      itemCount: toNum(amountRaw.itemCount),
      totalQuantity: toNum(amountRaw.totalQuantity),
    },
    priceToken: raw?.priceToken ?? null,
    priceTokenExpireAt: toNum(raw?.priceTokenExpireAt),
    hasInvalidItem: raw?.hasInvalidItem === true,
    // 后端没给 allAvailable 时按明细兜底：只要没有不可结算的行就算全部可结算
    allAvailable: raw?.allAvailable === undefined || raw?.allAvailable === null
      ? !(Array.isArray(raw?.items) && raw.items.some((item: any) => item?.available === false))
      : Boolean(raw.allAvailable),
  }
}

/** 调结算预览（金额、价格令牌、可用性全部来自这一次请求） */
async function loadPreview() {
  if (!snapshotItems.value.length) return
  loading.value = true
  try {
    const result = await $fetch<PreviewResult>('/api/order/preview', {
      method: 'POST',
      body: {
        items: snapshotItems.value.map(item => ({
          productId: item.productId,
          skuId: item.skuId,
          quantity: item.quantity,
        })),
        lang: locale.value,
      },
    })
    preview.value = normalizePreview(result)
    loadError.value = ''
  } catch (error) {
    if (isUnauthorizedError(error)) {
      unauthorized.value = true
      return
    }
    loadError.value = readErrorMessage(error, t('order.loadErrorDesc'))
  } finally {
    loading.value = false
  }
}

/** 拉地址簿并默认选中「默认地址」，没有默认地址就选第一条 */
async function loadAddresses() {
  addressLoading.value = true
  try {
    const list = await $fetch<AddressItem[]>('/api/address/list', { query: { lang: locale.value } })
    const normalized = (Array.isArray(list) ? list : []).filter(item => !!item?.id)
    addresses.value = normalized
    const preferred = normalized.find(item => item.isDefault === true) ?? normalized[0]
    // 只在用户还没选过（或选中的地址已不存在）时才覆盖选择，避免刷新把用户的选择冲掉
    if (!selectedAddressId.value || !normalized.some(item => item.id === selectedAddressId.value)) {
      selectedAddressId.value = preferred?.id ?? null
    }
  } catch (error) {
    if (isUnauthorizedError(error)) {
      unauthorized.value = true
      return
    }
    // 地址拉不到不该让整个结算页白屏：金额与商品仍然可看，只是无法提交
    toast.add({
      title: t('order.checkoutAddress'),
      description: readErrorMessage(error, t('order.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    addressLoading.value = false
  }
}

async function bootstrap() {
  loadError.value = ''
  unauthorized.value = false
  snapshotItems.value = readCheckoutSnapshot()
  if (!snapshotItems.value.length) {
    ready.value = true
    return
  }
  await Promise.all([loadPreview(), loadAddresses()])
  ready.value = true
}

async function submitOrder() {
  if (!canSubmit.value || !preview.value?.priceToken) return
  submitting.value = true
  try {
    const result = await $fetch<SubmitResult>('/api/order/submit', {
      method: 'POST',
      body: {
        // 幂等键：本页进入时生成一次，重试复用
        idempotentKey: idempotentKey.value,
        priceToken: preview.value.priceToken,
        items: snapshotItems.value.map(item => ({
          productId: item.productId,
          skuId: item.skuId,
          quantity: item.quantity,
        })),
        addressId: selectedAddressId.value,
        buyerRemark: buyerRemark.value.trim(),
        orderSource: 'web',
        referrer: route.fullPath,
        // 下单成功后后端只清这些购物车条目，没勾选的商品原地保留
        cartItemIds: snapshotItems.value.map(item => item.cartItemId).filter(Boolean),
        lang: locale.value,
      },
    })

    // 快照用掉就清掉：用户从支付页返回时不会又看到一份待下单清单
    clearCheckoutSnapshot()

    toast.add({
      // 命中幂等说明这一单之前已经提交过，不能再说一次"下单成功"让用户以为买了两单
      title: result?.idempotentReused ? t('order.checkoutReusedToast') : t('order.checkoutCreatedToast'),
      color: 'success',
      icon: 'lucide:check',
    })

    await navigateTo(localePath({ path: '/checkout/pay', query: { orderNo: result.orderNo } }))
  } catch (error) {
    if (isUnauthorizedError(error)) {
      unauthorized.value = true
      return
    }
    toast.add({
      title: t('order.checkoutFail'),
      description: readErrorMessage(error, t('order.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
    // 下单失败常见原因是价格令牌过期（促销变了/超时），这里静默重算一次金额，
    // 让用户看到的是最新价格。注意**幂等键不变**，用户的意图还是同一单。
    await loadPreview()
  } finally {
    submitting.value = false
  }
}

onMounted(bootstrap)

useSeoMeta({
  title: () => t('order.checkoutTitle'),
  robots: 'noindex, nofollow',
})
</script>
