<template>
  <!--
    结算页「收货地址选择」。

    只负责渲染 + 选择：地址数据由结算页从 /api/address/list 取回后传入，
    组件自己不取数，也就不可能"顺手"造一条假地址出来。

    这里**不用下拉框**：地址是需要核对的（收货人/手机号/详细地址），
    下拉框里塞不下，用户也没法一眼看清。用单选卡片列表更接近真实下单场景。
  -->
  <section class="ui-card overflow-hidden">
    <header class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-b border-[var(--color-line-soft)] px-4 py-3">
      <h2 class="flex items-center gap-2 text-[15px] font-black text-[var(--color-ink)]">
        <UIcon name="lucide:map-pin" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
        {{ t('order.checkoutAddress') }}
      </h2>
      <NuxtLinkLocale
        to="/address"
        class="inline-flex items-center gap-1 text-[13px] font-medium text-[var(--color-brand-600)] hover:underline"
      >
        <UIcon name="lucide:settings-2" class="h-3.5 w-3.5" aria-hidden="true" />
        {{ t('order.checkoutAddressManage') }}
      </NuxtLinkLocale>
    </header>

    <!--
      三种状态互斥，用 fade 过渡（骨架↔内容不再硬切）。
      注意：error 必须排在 empty 前面 —— 加载失败时 addresses 也是空数组，
      如果先判空，会把"网络挂了"显示成"你还没有地址"，用户会以为地址簿被清空。
    -->
    <Transition name="fade" mode="out-in">
      <!-- 加载中 -->
      <div v-if="loading" key="loading" class="space-y-3 p-4" aria-busy="true">
        <div v-for="index in 2" :key="index" class="h-16 animate-pulse rounded-[var(--radius-card-sm)] bg-[var(--color-line-soft)]"></div>
      </div>

      <!-- 加载失败：如实说明 + 重试，不冒充"地址簿为空" -->
      <div v-else-if="error" key="error" class="px-4 py-10 text-center">
        <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-[var(--color-error-50)]">
          <UIcon name="lucide:wifi-off" class="h-6 w-6 text-[var(--color-error)]" aria-hidden="true" />
        </span>
        <p class="mt-4 text-sm font-bold text-[var(--color-ink)]">{{ t('address.loadError') }}</p>
        <p class="mx-auto mt-1 max-w-sm text-[13px] text-[var(--color-muted)]">{{ error }}</p>
        <button type="button" class="ui-pill ui-pill-ghost mt-5 h-10 px-5 text-[13px]" @click="emit('retry')">
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
          {{ t('common.retry') }}
        </button>
      </div>

      <!-- 地址簿为空：给出唯一可执行的动作，而不是一句"请先添加地址" -->
      <div v-else-if="!addresses.length" key="empty" class="px-4 py-10 text-center">
        <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
          <UIcon name="lucide:map-pin-off" class="h-6 w-6 text-[var(--color-brand-400)]" aria-hidden="true" />
        </span>
        <p class="mt-4 text-sm font-bold text-[var(--color-ink)]">{{ t('order.checkoutAddressEmpty') }}</p>
        <p class="mx-auto mt-1 max-w-sm text-[13px] text-[var(--color-muted)]">{{ t('order.checkoutAddressEmptyDesc') }}</p>
        <NuxtLinkLocale to="/address" class="ui-pill ui-pill-brand mt-5 h-10 px-5 text-[13px]">
          <UIcon name="lucide:plus" class="h-4 w-4" aria-hidden="true" />
          {{ t('order.checkoutAddressAdd') }}
        </NuxtLinkLocale>
      </div>

      <!-- 地址列表：单选 -->
      <ul v-else key="list" class="divide-y divide-[var(--color-line-soft)]">
      <li v-for="address in addresses" :key="address.id">
        <label
          class="flex cursor-pointer items-start gap-3 px-4 py-3.5 transition-colors duration-200 hover:bg-[var(--color-brand-50)]/40 motion-reduce:transition-none"
          :class="address.id === modelValue ? 'bg-[var(--color-brand-50)]/60' : ''"
        >
          <input
            type="radio"
            name="checkout-address"
            class="mt-1 h-4 w-4 shrink-0 cursor-pointer accent-[var(--color-brand-500)]"
            :value="address.id"
            :checked="address.id === modelValue"
            @change="emit('update:modelValue', address.id)"
          >
          <span class="min-w-0 flex-1">
            <span class="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span class="text-[14px] font-bold text-[var(--color-ink)]">{{ address.receiverName }}</span>
              <!-- 手机号由后端决定是否脱敏（买家自己的地址簿不脱敏），前端不加工 -->
              <span class="text-[13px] tabular text-[var(--color-ink-soft)]">{{ address.receiverPhone }}</span>
              <span
                v-if="address.isDefault"
                class="rounded-full bg-[var(--color-brand-cta)] px-2 py-0.5 text-[10px] font-bold text-white"
              >
                {{ t('address.defaultTag') }}
              </span>
              <span
                v-if="address.addressTag"
                class="rounded-full bg-[var(--color-line-soft)] px-2 py-0.5 text-[10px] font-bold text-[var(--color-ink-soft)]"
              >
                {{ address.addressTag }}
              </span>
            </span>
            <!--
              整串地址由后端拼好，前端不再各处拼一遍，格式才能全站一致。
              优先用 fullAddressLocalized（当前访客语言），老数据没有这个字段时回退 fullAddress
              （当地语言原件）—— 回退顺序不能反：fullAddress 是给面单/物流看的当地文字，
              中文界面下拿它当展示文案，用户会看到一串泰文/阿拉伯文。
            -->
            <span class="mt-1 block text-[13px] leading-relaxed text-[var(--color-muted)]">
              {{ address.fullAddressLocalized || address.fullAddress || [address.province, address.city, address.district, address.detailAddress].filter(Boolean).join(' ') }}
            </span>
          </span>
        </label>
      </li>
      </ul>
    </Transition>

    <!-- 新增入口：地址簿非空时也保留，避免用户为了加一条地址来回跳页 -->
    <div v-if="!loading && !error && addresses.length" class="border-t border-[var(--color-line-soft)] px-4 py-3">
      <NuxtLinkLocale
        to="/address"
        class="inline-flex items-center gap-1 text-[13px] font-medium text-[var(--color-muted)] transition-colors duration-200 hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
      >
        <UIcon name="lucide:plus" class="h-3.5 w-3.5" aria-hidden="true" />
        {{ t('order.checkoutAddressAdd') }}
      </NuxtLinkLocale>
    </div>
  </section>
</template>

<script setup lang="ts">
/**
 * 结算页地址选择卡片。
 *
 * 只做「展示 + 选择」，取数交给父页面 —— 与 CartSummaryCard 的取舍一致：
 * 展示组件一旦自己取数，就很容易在数据缺失时"补"一个假值。
 */

/** 收货地址（对应后端 AddressVO，字段名照抄 camelCase） */
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
  /** 当地语言 + 面单格式（老数据的唯一整串来源） */
  fullAddress?: string | null
  /** 当前访客语言的整串（契约 §3.4 新增；老数据为空） */
  fullAddressLocalized?: string | null
}

defineProps<{
  /** 地址列表（父页面已按默认地址优先排序） */
  addresses: AddressItem[]
  /** 当前选中的地址 ID */
  modelValue: string | null
  /** 是否正在加载 */
  loading?: boolean
  /** 加载失败文案（空串 = 没有失败）；非空时渲染错误分支 + 重试，而不是冒充"地址簿为空" */
  error?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', id: string): void
  /** 错误分支里的「重试」：取数在父页面，组件只抛事件 */
  (e: 'retry'): void
}>()

const { t } = useI18n()
</script>
