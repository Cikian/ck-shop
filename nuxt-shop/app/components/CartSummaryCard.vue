<template>
  <!--
    右侧常驻结算明细卡（sticky 由页面负责包裹）。

    【零伪造契约】这里只渲染父组件给到的真实数字：
      - 运费行：只有确认了"全部已选商品包邮"才显示「免运费」，否则显示无数据占位 `--`，
        绝不写死"运费 ¥12"或"满 99 包邮"；
      - 优惠券 / 「已为你节省」：后端没有"用户可用券"数据源，永远不可能有真实值，
        所以整块已删除（保留一个永远不会渲染的分支就是死代码，还会让读者以为有券功能）。
    【去结算】按钮归页面：页面才知道哪些条目被勾选，这里只负责金额明细。
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
    </div>

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
 *
 * 注意：优惠券金额（couponAmount）与"已为你节省"（savedAmount）已删除 ——
 * 后端没有可用券数据源，调用方也只能传 null，这两个分支永远不会渲染。
 */
defineProps<{
  /** 已选件数（数量之和，与 cart.selectedCount 同口径） */
  selectedQuantity: number
  /** 商品总额（= 已选条目 lineTotal 之和，真实数据） */
  goodsAmount: number
  /** 应付总额（= 商品总额 - 真实优惠；没有任何优惠时等于商品总额） */
  payable: number
  /** 已选商品是否全部确认包邮；数据缺失时为 false → 显示无数据占位 */
  shippingFree: boolean
}>()

const { t } = useI18n()
const { formatCompact } = useStorePrice()
</script>
