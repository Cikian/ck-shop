<template>
  <!--
    右侧常驻结算明细卡（sticky 由页面负责包裹）。

    【零伪造契约】这里只渲染父组件给到的真实数字：
      - 优惠券行：couponAmount === null（后端没有"用户可用券"数据源）→ **整行不渲染**；
      - 运费行：只有确认了"全部已选商品包邮"才显示「免运费」，否则显示无数据占位 `--`，
        绝不写死"运费 ¥12"或"满 99 包邮"；
      - 「已为你节省」：只有真的产生了优惠才渲染。
    【去结算】按钮沿用现状：永久 disabled（订单与支付功能未开发），并保留小字说明。
  -->
  <section class="ui-card p-5" :aria-label="t('shop.cartSummary')">
    <h2 class="text-[17px] font-black text-[var(--color-ink)]">{{ t('shop.cartSummary') }}</h2>

    <dl class="mt-4 space-y-3 text-[13px]">
      <div class="flex items-center justify-between gap-3">
        <dt class="text-[var(--color-muted)]">{{ t('shop.cartSelectedCount') }}</dt>
        <dd class="font-bold text-[var(--color-ink)] tabular">
          {{ selectedQuantity }} {{ t('shop.itemsUnit') }}
        </dd>
      </div>

      <div class="flex items-center justify-between gap-3">
        <dt class="text-[var(--color-muted)]">{{ t('shop.cartGoodsTotal') }}</dt>
        <dd class="font-bold text-[var(--color-ink)] tabular">{{ formatCompact(goodsAmount) }}</dd>
      </div>

      <!-- 优惠券：没有真实券数据就没有这一行 -->
      <div v-if="couponAmount !== null" class="flex items-center justify-between gap-3">
        <dt class="text-[var(--color-muted)]">{{ t('shop.cartCoupon') }}</dt>
        <dd class="flex items-center gap-2">
          <button
            type="button"
            class="cursor-pointer text-[12px] font-medium text-[var(--color-brand-600)] hover:underline"
            @click="emit('select-coupon')"
          >
            {{ t('shop.cartSelectCoupon') }}
          </button>
          <span class="font-bold text-[var(--color-brand-600)] tabular">
            -{{ formatCompact(couponAmount) }}
          </span>
        </dd>
      </div>

      <div class="flex items-center justify-between gap-3">
        <dt class="text-[var(--color-muted)]">{{ t('shop.cartShipping') }}</dt>
        <!--
          购物车明细目前没有 freeShipping 字段、也没有运费规则接口，
          所以未知时显示「待计算」——这是一个诚实的占位，而不是写死一个运费数字。
          要显示「免运费」需要后端在购物车明细补 freeShipping 或提供运费规则接口。
        -->
        <dd v-if="shippingFree" class="font-bold text-[var(--color-success-600)]">
          {{ t('shop.cartFreeShipping') }}
        </dd>
        <dd v-else class="font-bold text-[var(--color-muted)]">{{ t('shop.cartShippingPending') }}</dd>
      </div>
    </dl>

    <!-- 醒目分隔线 + 应付总额 -->
    <div class="mt-4 border-t border-[var(--color-line)] pt-4">
      <div class="flex items-center justify-between gap-3">
        <span class="text-[15px] font-black text-[var(--color-ink)]">{{ t('shop.cartPayable') }}</span>
        <span class="price text-[26px] leading-none whitespace-nowrap sm:text-[30px]">{{ formatCompact(payable) }}</span>
      </div>
      <!-- 只有真的优惠过才说"已为你节省" -->
      <p v-if="savedAmount !== null" class="mt-2 text-right text-[12px] text-[var(--color-muted)]">
        {{ t('shop.cartSaved', { amount: formatCompact(savedAmount) }) }}
      </p>
    </div>

    <!--
      这里**不再放「去结算」按钮**。

      原本这里有一个永久 disabled 的「去结算」+ 小字「订单与支付功能开发中」，
      那是订单模块上线前的占位。订单模块完成后，购物车页已经在下方渲染了
      真正可用的「去结算」，于是页面上同时出现两个同名按钮
      （一个灰色不可点、一个蓝色可点）——用户第一反应是"按钮坏了"。
      按钮归页面（页面才知道哪些条目被勾选），这里只负责金额明细。
    -->

    <!-- 服务承诺：店铺级，不依赖任何商品字段 -->
    <div class="mt-5 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 border-t border-[var(--color-line-soft)] pt-4">
      <span class="flex items-center gap-1.5 text-[12px] font-medium text-[var(--color-ink-soft)]">
        <UIcon name="lucide:shield-check" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
        {{ t('shop.serviceGenuine') }}
      </span>
      <span class="flex items-center gap-1.5 text-[12px] font-medium text-[var(--color-ink-soft)]">
        <UIcon name="lucide:rotate-ccw" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
        {{ t('shop.serviceReturn') }}
      </span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useStorePrice } from '~~/composables/useProducts'

/**
 * 结算明细卡。
 *
 * 数据全部由 cart.vue 传入：卡片本身不做任何取数，
 * 也就不可能"顺手"编造一个优惠金额或运费。
 */
defineProps<{
  /** 已选件数（数量之和，与 cart.selectedCount 同口径） */
  selectedQuantity: number
  /** 商品总额（= 已选条目 lineTotal 之和，真实数据） */
  goodsAmount: number
  /** 应付总额（= 商品总额 - 真实优惠；没有任何优惠时等于商品总额） */
  payable: number
  /** 优惠券抵扣金额；null = 后端没有可用券数据 → 整行不渲染 */
  couponAmount: number | null
  /** 已选商品是否全部确认包邮；数据缺失时为 false → 显示无数据占位 */
  shippingFree: boolean
  /** 真实优惠金额；null / 0 → 不渲染"已为你节省" */
  savedAmount: number | null
}>()

const emit = defineEmits<{
  (e: 'select-coupon'): void
}>()

const { t } = useI18n()
const { formatCompact } = useStorePrice()
</script>
