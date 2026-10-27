<template>
  <article class="ui-card ui-card-hover group flex flex-col overflow-hidden">
    <!-- 封面 + 信息：整块是一个链接，点击进入商品详情 -->
    <NuxtLinkLocale :to="`/products/${product.id}`" class="flex flex-1 flex-col">
      <div class="relative aspect-square overflow-hidden bg-[var(--color-surface-secondary)]">
        <ShopImage
          :src="coverUrl"
          :alt="product.title"
          width="600"
          height="600"
          :loading="eager ? 'eager' : 'lazy'"
          :fetchpriority="eager ? 'high' : 'auto'"
          class="h-full w-full object-cover transition-transform duration-500 ease-out group-hover:scale-105 motion-reduce:transition-none motion-reduce:group-hover:scale-100"
          icon-class="h-9 w-9"
        />

        <!-- 角标：只渲染真实存在的推荐标记与 HOT/SALE/NEW/PICK 标签 -->
        <div v-if="imageBadges.length" class="absolute left-2.5 top-2.5 flex flex-wrap gap-1.5">
          <span
            v-if="product.isRecommend === 'Y'"
            class="badge badge-new"
          >
            {{ t('products.recommended') }}
          </span>
          <span
            v-for="tag in imageBadges"
            :key="tag.id"
            class="badge"
            :class="badgeToneClass(tag.code)"
          >
            {{ tag.name }}
          </span>
        </div>

        <!-- 无货遮罩 -->
        <div
          v-if="!inStock"
          class="absolute inset-0 flex items-center justify-center bg-white/68 backdrop-blur-[1px]"
        >
          <span class="rounded-full bg-[var(--color-ink)]/80 px-3 py-1.5 text-xs font-bold text-white">
            {{ t('products.stock.outOfStock') }}
          </span>
        </div>
      </div>

      <!-- 信息区 -->
      <div class="flex flex-1 flex-col p-3.5">
        <h3 class="line-clamp-2 text-[15px] font-bold leading-snug text-[var(--color-ink)] transition-colors group-hover:text-[var(--color-brand-600)]">
          {{ product.title }}
        </h3>

        <p v-if="product.subTitle" class="mt-1.5 line-clamp-1 text-xs text-[var(--color-muted)]">
          {{ product.subTitle }}
        </p>

        <!-- 价格：现价超大 + 划线原价（跨价格区间时展示最低价，区间说明留给详情页） -->
        <div class="mt-2.5 flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
          <span class="price text-[26px] leading-none">{{ priceText }}</span>
          <span v-if="priceView.showOriginal" class="price-strike text-xs">{{ originalText }}</span>
        </div>

        <!-- 统计行：销量为 0 时整行不渲染 -->
        <p v-if="statsText" class="mt-1.5 text-xs text-[var(--color-muted)]">
          {{ statsText }}
        </p>

        <!-- 服务标签 -->
        <div v-if="serviceTags.length || product.freeShipping" class="mt-2.5 flex flex-wrap gap-1.5">
          <span v-if="product.freeShipping" class="tag-service">{{ t('shop.freeShipping') }}</span>
          <span v-for="tag in serviceTags" :key="tag.id" class="tag-service">
            {{ tag.name }}
          </span>
        </div>
      </div>
    </NuxtLinkLocale>

    <!--
      快捷加购。
      卡片上拿不到规格信息，所以这里**先查一次详情**再决定：
        - 商品有规格 → 跳到详情页去选规格（绝不替用户随便挑一个 SKU 塞进购物车）；
        - 无规格商品 → 直接加购，一次点击完成。
      多花一次请求换来的是"购物车里永远不会有没选规格的东西"，这个交换是划算的。
    -->
    <div class="px-3.5 pb-3.5">
      <AsyncButton
        type="button"
        class="ui-pill ui-pill-brand h-9 w-full text-[13px] disabled:cursor-not-allowed disabled:opacity-45"
        :pending="adding"
        :pending-label="t('product.adding')"
        :disabled="!inStock"
        :aria-label="`${t('products.addToCart')} - ${product.title}`"
        @click="handleQuickAdd"
      >
        <UIcon name="lucide:shopping-cart" class="h-4 w-4" aria-hidden="true" />
        {{ t('products.addToCart') }}
      </AsyncButton>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { StoreProductCard, StoreProductDetail, ShopTagRef, BadgeTone } from '~~/types/shop-product'
import {
  formatCompactCount,
  resolveCoverUrl,
  resolvePriceView,
  resolveRating,
  resolveTagTone,
} from '~~/types/shop-product'
import { useStorePrice } from '~~/composables/useProducts'
import { isUnauthorizedError } from '~~/composables/useApiError'
import { extractCartErrorMessage, useCart } from '~~/composables/useCart'
import { useAuth } from '~~/composables/useAuth'

/**
 * 店铺端商品卡片（国内电商风格）。
 *
 * 只展示后端真实字段，且遵守统一渲染契约：
 *   salesCount / ratingAvg / reviewCount / favoriteCount 为 0 时，
 *   对应元素**整块不渲染**，绝不显示 0 或兜底假数据。
 *   originalPrice 只有在严格高于现价时才显示划线。
 *
 * 角标与价格的计算逻辑全部来自 types/shop-product.ts，
 * 组件里不做第二套判断（避免同类逻辑散落十几处后逐渐不一致）。
 */
const props = defineProps<{
  product: StoreProductCard
  /** 首屏首行的卡片用 eager：等滚动到才加载会让 LCP 变慢 */
  eager?: boolean
}>()

const { t, locale } = useI18n()
const { formatCompact } = useStorePrice()
const { addItem } = useCart()
const { isLoggedIn } = useAuth()
const toast = useToast()
const route = useRoute()
const localePath = useLocalePath()

const adding = ref(false)

const coverUrl = computed(() => resolveCoverUrl(props.product))
const inStock = computed(() => props.product.stock > 0)

const priceView = computed(() => resolvePriceView(props.product))
const priceText = computed(() => {
  const value = priceView.value.price
  if (value === null) return '--'
  return formatCompact(value)
})
const originalText = computed(() =>
  priceView.value.originalPrice === null ? '' : formatCompact(priceView.value.originalPrice),
)

/** 统计行：销量与评分都没有数据时返回空串 → 整行不渲染 */
const statsText = computed(() => {
  const parts: string[] = []
  if (props.product.salesCount > 0) {
    parts.push(t('shop.sold', { count: formatCompactCount(props.product.salesCount) }))
  }
  const rating = resolveRating(props.product)
  if (rating !== null) {
    parts.push(`★ ${rating.toFixed(1)}`)
  }
  return parts.join(' · ')
})

/** 封面角标：HOT / SALE / NEW / PICK 最多 3 个 */
const IMAGE_BADGE_CODES = new Set(['HOT', 'SALE', 'NEW', 'PICK'])
const imageBadges = computed<ShopTagRef[]>(() => {
  const limit = props.product.isRecommend === 'Y' ? 2 : 3
  return (props.product.tags || [])
    .filter(tag => IMAGE_BADGE_CODES.has((tag.code || '').toUpperCase()))
    .slice(0, limit)
})

/** 信息区服务标签：FAST / TOP（其余商品级标签已作为角标展示，不重复） */
const SERVICE_TAG_CODES = new Set(['FAST', 'TOP'])
const serviceTags = computed<ShopTagRef[]>(() =>
  (props.product.tags || [])
    .filter(tag => SERVICE_TAG_CODES.has((tag.code || '').toUpperCase()))
    .slice(0, 2),
)

function badgeToneClass(code: string | null | undefined): string {
  const tone: BadgeTone = resolveTagTone(code)
  switch (tone) {
    case 'hot':
      return 'badge-hot'
    case 'sale':
      return 'badge-sale'
    case 'new':
      return 'badge-new'
    default:
      return 'badge-soft'
  }
}

async function handleQuickAdd() {
  if (adding.value) return

  if (!isLoggedIn.value) {
    await navigateTo(localePath({ path: '/login', query: { redirect: route.fullPath } }))
    return
  }

  adding.value = true
  try {
    // 卡片数据里没有规格，先取详情：只在"点击加购"这一刻多花一次请求
    const detail = await $fetch<StoreProductDetail>(
      `/api/products/${encodeURIComponent(props.product.id)}`,
      { query: { lang: locale.value } },
    )

    if (detail?.specs?.length) {
      // 有规格：必须让用户自己选，绝不替他默认一个 SKU
      toast.add({ title: t('product.selectSpecFirst'), color: 'warning', icon: 'lucide:info' })
      await navigateTo(localePath(`/products/${props.product.id}`))
      return
    }

    await addItem({ productId: props.product.id, skuId: '', quantity: 1, source: 'list' })
    toast.add({ title: t('product.addedToCart'), color: 'success', icon: 'lucide:check' })
  } catch (error) {
    // 401/403 = 登录态已失效：交给全局会话守卫统一清理登录态、提示并跳登录页，
    // 这里再弹一条"加购失败"只会让用户同时看到两条互相矛盾的提示。
    if (isUnauthorizedError(error)) return
    toast.add({
      title: t('cart.actionFailed'),
      description: extractCartErrorMessage(error, t('cart.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    adding.value = false
  }
}
</script>
