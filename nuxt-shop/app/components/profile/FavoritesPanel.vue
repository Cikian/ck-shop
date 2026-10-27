<!--
  我的收藏面板

  从 pages/profile.vue 拆出（该页当时近 2000 行、5 个面板 + 4 个弹层混在一个文件里）。
  拆分原则：**面板自己持有自己的数据与交互**，页面只负责"当前是哪个标签页"。

  为什么这里能安全地自己调 useFavoriteList()：
  该 composable 内部用 useState（全局状态）而不是 page-local ref，
  所以页面与面板拿到的是**同一份数据** —— 面板里取消收藏后，
  页面顶部「收藏数」统计卡会一起更新，不需要任何 props/emit 传递。

  数据加载仍由页面在登录态恢复后触发一次（顶部统计卡要用），
  面板只负责渲染 + 行内操作，不做重复请求。
-->
<template>
  <!-- flex flex-1 flex-col：让空态能在伸展后的内容卡里垂直居中（内容少时不留大片死白） -->
  <div class="flex flex-1 flex-col">
  <p v-if="favoritesLoaded && favoriteTotal > 0" class="mt-1.5 text-[13px] text-[var(--color-muted)]">
    {{ t('shop.profileFavoritesTotal', { count: favoriteTotal }) }}
  </p>

  <!-- 骨架 / 错误 / 列表 / 空态：四态 fade，列表本身用 list 过渡（取消收藏时行有退出动画） -->
  <Transition name="fade" mode="out-in">
    <!-- 加载中：骨架屏（不写死行数，固定 3 行即可，避免猜测每页条数） -->
    <ul v-if="favoritesLoading" key="loading" class="mt-4 space-y-3" aria-busy="true">
      <li v-for="index in 3" :key="`fav-skeleton-${index}`" class="flex gap-4">
        <span class="h-20 w-20 shrink-0 animate-pulse rounded-[var(--radius-tile)] bg-[var(--color-line-soft)]"></span>
        <span class="flex-1 space-y-2 py-1">
          <span class="block h-4 w-2/3 animate-pulse rounded bg-[var(--color-line-soft)]"></span>
          <span class="block h-4 w-1/3 animate-pulse rounded bg-[var(--color-line-soft)]"></span>
        </span>
      </li>
    </ul>

    <!-- 加载失败：把后端/网络的原话展示出来，用户才知道是重试还是先别管 -->
    <div v-else-if="favoritesError" key="error" class="mt-5 flex items-start gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-4 py-3 text-[13px] font-medium text-[var(--color-error-600)]">
      <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <span>{{ t('shop.profileFavoritesLoadError') }}：{{ favoritesError }}</span>
    </div>

    <!-- 有收藏：紧凑列表（封面 + 标题 + 价格 + 取消收藏），点封面/标题进详情 -->
    <TransitionGroup v-else-if="favoriteItems.length" key="list" name="list" tag="ul" class="relative mt-4 divide-y divide-[var(--color-line-soft)]">
      <li v-for="item in favoriteItems" :key="item.id" class="flex items-center gap-4 py-4">
        <NuxtLinkLocale
          :to="`/products/${item.id}`"
          class="block h-20 w-20 shrink-0 overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-line-soft)]"
        >
          <ShopImage
            :src="favoriteCover(item)"
            :alt="item.title"
            width="80"
            height="80"
            class="h-full w-full object-cover"
            icon-class="h-6 w-6"
          />
        </NuxtLinkLocale>

        <div class="min-w-0 flex-1">
          <NuxtLinkLocale
            :to="`/products/${item.id}`"
            class="line-clamp-2 text-[14px] font-bold text-[var(--color-ink)] transition-colors hover:text-[var(--color-brand-600)]"
          >
            {{ item.title }}
          </NuxtLinkLocale>
          <p v-if="item.subTitle" class="mt-1 truncate text-[12px] text-[var(--color-muted)]">
            {{ item.subTitle }}
          </p>
          <p class="price mt-1.5 text-[16px] leading-none">{{ favoritePrice(item) }}</p>
        </div>

        <AsyncButton
          type="button"
          class="ui-pill ui-pill-ghost h-9 shrink-0 px-4 text-[13px] disabled:cursor-not-allowed disabled:opacity-45"
          :pending="favoriteRemovingId === item.id"
          :pending-label="t('common.loading')"
          :aria-label="`${t('product.removeFromWishlist')} - ${item.title}`"
          @click="handleRemoveFavorite(item.id)"
        >
          <UIcon name="lucide:heart-off" class="h-4 w-4" aria-hidden="true" />
          {{ t('product.removeFromWishlist') }}
        </AsyncButton>
      </li>
    </TransitionGroup>

    <!-- 真的没有收藏 -->
    <div v-else key="empty" class="flex flex-1 flex-col items-center justify-center py-14 text-center">
      <span class="flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-brand-50)] text-[var(--color-brand-400)]">
        <UIcon name="lucide:heart-off" class="h-9 w-9" aria-hidden="true" />
      </span>
      <p class="mt-5 text-sm font-bold text-[var(--color-ink)]">
        {{ t('shop.profileFavoritesEmpty') }}
      </p>
      <NuxtLinkLocale to="/products" class="ui-pill ui-pill-brand mt-5 h-10 px-6 text-[13px]">
        <UIcon name="lucide:arrow-right" class="h-4 w-4" aria-hidden="true" />
        {{ t('shop.profileFavoritesAction') }}
      </NuxtLinkLocale>
    </div>
  </Transition>
  </div>
</template>

<script setup lang="ts">
import { isUnauthorizedError } from '~~/composables/useApiError'
import { extractFavoriteErrorMessage, useFavoriteList } from '~~/composables/useFavorites'
import { useStorePrice } from '~~/composables/useProducts'
import { resolveCoverUrl, resolvePriceView } from '~~/types/shop-product'

const { t } = useI18n()
const toast = useToast()

// 与商品卡片同一套价格口径（minPrice 优先、整数不带小数）
const { formatCompact } = useStorePrice()

// 与页面共用同一份全局状态（useFavoriteList 内部是 useState）
const {
  items: favoriteItems,
  total: favoriteTotal,
  loading: favoritesLoading,
  loaded: favoritesLoaded,
  error: favoritesError,
  remove: removeFavorite,
} = useFavoriteList()

/** 正在取消收藏的那一行 id（按钮转圈 + 防重复点击） */
const favoriteRemovingId = ref('')

function favoriteCover(item: { coverUrl: string | null; mainImage: string | null }): string {
  return resolveCoverUrl(item)
}

function favoritePrice(item: Parameters<typeof resolvePriceView>[0]): string {
  return formatCompact(resolvePriceView(item).price)
}

async function handleRemoveFavorite(productId: string) {
  if (favoriteRemovingId.value) return
  favoriteRemovingId.value = productId
  try {
    await removeFavorite(productId)
    toast.add({ title: t('product.removedFromWishlist'), color: 'success', icon: 'lucide:heart-off' })
  } catch (error) {
    // 401/403 = 登录态已失效：交给全局会话守卫统一清理登录态、提示并跳登录页，
    // 这里再弹一条"操作失败"只会让用户同时看到两条互相矛盾的提示。
    if (isUnauthorizedError(error)) return
    toast.add({
      title: t('product.wishlistFailed'),
      description: extractFavoriteErrorMessage(error, t('shop.profileFavoritesLoadError')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    favoriteRemovingId.value = ''
  }
}
</script>
