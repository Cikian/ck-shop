<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <!-- 页头 -->
    <div class="mx-auto max-w-7xl px-2 sm:px-3 lg:px-4">
      <div class="mb-6 sm:mb-8">
        <span class="section-eyebrow inline-flex items-center gap-2">
          <UIcon name="lucide:user-round" class="h-4 w-4" aria-hidden="true" />
          {{ t('profile.title') }}
        </span>
        <h1 class="mt-3 text-3xl font-black tracking-tight text-[var(--color-ink)] sm:text-4xl">
          {{ t('profile.title') }}
        </h1>
        <p class="mt-2 text-sm text-[var(--color-muted)]">
          {{ t('profile.subtitle') }}
        </p>
      </div>
    </div>

    <div class="mx-auto max-w-7xl px-2 pb-16 sm:px-3 lg:px-4">
      <!-- 未登录态 -->
      <template v-if="!isLoggedIn">
        <div class="mx-auto max-w-lg">
          <div class="ui-card px-6 py-10 text-center sm:px-10">
            <span class="brand-gradient-soft mx-auto flex h-20 w-20 items-center justify-center rounded-full">
              <UIcon name="lucide:user" class="h-9 w-9 text-[var(--color-brand-500)]" aria-hidden="true" />
            </span>
            <h2 class="mt-6 text-xl font-black text-[var(--color-ink)]">
              {{ t('profile.notLoggedIn') }}
            </h2>
            <p class="mt-2 text-sm text-[var(--color-muted)]">
              {{ t('profile.pleaseLogin') }}
            </p>
            <div class="mt-7 flex flex-col gap-3 sm:flex-row sm:justify-center">
              <NuxtLinkLocale to="/login" class="ui-pill ui-pill-brand h-11 px-7 text-sm">
                <UIcon name="lucide:log-in" class="h-4 w-4" aria-hidden="true" />
                {{ t('profile.goLogin') }}
              </NuxtLinkLocale>
              <!--
                站点没有注册页（app/pages 下不存在 register.vue），Header 也是把"注册"入口
                指向 /login。这里保持一致：宁可两个入口都落到登录页，也不给用户一个点进去 404
                的假入口。（若后续补了注册页，这里改成 /register 即可。）
              -->
              <NuxtLinkLocale to="/login" class="ui-pill ui-pill-ghost h-11 px-7 text-sm">
                <UIcon name="lucide:user-plus" class="h-4 w-4" aria-hidden="true" />
                {{ t('profile.register') }}
              </NuxtLinkLocale>
            </div>
          </div>
        </div>
      </template>

      <!-- 已登录态：lg 及以上 = 左 1 栏资料卡 + 右 3 栏（统计 + 内容面板），以下上下堆叠 -->
      <template v-else>
        <div class="grid gap-6 lg:grid-cols-4 lg:gap-8">
          <!-- ===== 左：资料卡（全部字段都来自 userInfo，没有的就不渲染） ===== -->
          <div class="lg:col-span-1">
            <div class="ui-card overflow-hidden">
              <div class="brand-gradient relative p-6 text-white">
                <div class="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
                  <span class="absolute -right-8 -top-10 h-28 w-28 rounded-full bg-white/20 blur-2xl"></span>
                  <span class="absolute -bottom-12 -left-8 h-28 w-28 rounded-full bg-[var(--color-accent-400)]/25 blur-2xl"></span>
                </div>

                <div class="relative flex items-start gap-4">
                  <span class="h-16 w-16 shrink-0 overflow-hidden rounded-full border-2 border-white/40 bg-white/20">
                    <img
                      v-if="userInfo?.avatar"
                      :src="userInfo.avatar"
                      :alt="displayName"
                      class="h-full w-full object-cover"
                    >
                    <span
                      v-else-if="avatarInitial"
                      class="flex h-full w-full items-center justify-center text-2xl font-black"
                      aria-hidden="true"
                    >
                      {{ avatarInitial }}
                    </span>
                    <span v-else class="flex h-full w-full items-center justify-center">
                      <UIcon name="lucide:user" class="h-7 w-7" aria-hidden="true" />
                    </span>
                  </span>

                  <div class="min-w-0 flex-1">
                    <h2 class="truncate text-lg font-black">
                      {{ displayName || t('profile.title') }}
                    </h2>
                    <p v-if="userInfo?.username" class="truncate text-sm text-white/75">
                      @{{ userInfo.username }}
                    </p>
                    <!--
                      这里原本是写死的 "Gold Member" 徽章。后端没有任何会员等级字段，
                      改为只显示真实的注册时间（userInfo.createTime 来自后端 SysUser），
                      取不到就整块不渲染 —— 绝不编造会员等级或日期。
                    -->
                    <p
                      v-if="memberSinceText"
                      class="mt-2 inline-flex max-w-full items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-[11px] font-bold"
                    >
                      <UIcon name="lucide:calendar" class="h-3 w-3 shrink-0" aria-hidden="true" />
                      <span class="truncate">{{ t('profile.memberSince') }} · {{ memberSinceText }}</span>
                    </p>
                  </div>
                </div>

                <button
                  type="button"
                  class="relative mt-5 flex h-10 w-full cursor-pointer items-center justify-center gap-2 rounded-full border border-white/40 bg-white/10 text-[13px] font-bold text-white transition-colors hover:bg-white/20 motion-reduce:transition-none"
                  @click="activeTab = 'settings'"
                >
                  <UIcon name="lucide:pencil" class="h-3.5 w-3.5" aria-hidden="true" />
                  {{ t('profile.editProfile') }}
                </button>
              </div>

              <!-- 邮箱 / 手机号：只在后端返回了对应字段时才渲染 -->
              <dl v-if="contactRows.length" class="space-y-3.5 p-5">
                <div v-for="row in contactRows" :key="row.label" class="min-w-0">
                  <dt class="flex items-center gap-2 text-[11px] font-bold uppercase tracking-wider text-[var(--color-muted)]">
                    <UIcon :name="row.icon" class="h-3.5 w-3.5 shrink-0 text-[var(--color-brand-500)]" aria-hidden="true" />
                    {{ t(row.label) }}
                  </dt>
                  <dd class="mt-0.5 truncate text-[13px] font-medium text-[var(--color-ink)]">
                    {{ row.value }}
                  </dd>
                </div>
              </dl>

              <div class="border-t border-[var(--color-line-soft)] p-4">
                <button
                  type="button"
                  class="flex w-full cursor-pointer items-center justify-center gap-2 rounded-full border border-[var(--color-line)] px-4 py-2.5 text-sm font-bold text-[var(--color-error-600)] transition-colors hover:bg-[var(--color-error-50)] motion-reduce:transition-none"
                  @click="handleLogout"
                >
                  <UIcon name="lucide:log-out" class="h-4 w-4" aria-hidden="true" />
                  {{ t('profile.logout') }}
                </button>
              </div>
            </div>
          </div>

          <!-- ===== 右 3 栏：统计 + 标签页 ===== -->
          <div class="lg:col-span-3">
            <!--
              统计卡：**收藏数已接通真实接口**（GET /api/favorite 的 total）；
              订单 / 优惠券 / 积分后端仍没有对应接口（订单模块有接口，但本页尚未接入），
              所以这三项一律显示真实的 0，绝不用 12 / 28 / 5 / 1250 这种编造数字。
            -->
            <div class="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
              <div v-for="stat in stats" :key="stat.label" class="ui-card flex items-center gap-3 p-4">
                <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-[12px]" :class="stat.iconClass">
                  <UIcon :name="stat.icon" class="h-5 w-5" aria-hidden="true" />
                </span>
                <span class="min-w-0">
                  <span class="tabular block text-2xl font-black leading-none text-[var(--color-ink)]">
                    {{ stat.value }}
                  </span>
                  <span class="mt-1 block truncate text-xs text-[var(--color-muted)]">
                    {{ t(stat.label) }}
                  </span>
                </span>
              </div>
            </div>

            <!-- 标签页（WAI-ARIA tabs 模式：左右方向键 / Home / End 切换） -->
            <div class="no-scrollbar mt-6 flex gap-2 overflow-x-auto" role="tablist" :aria-label="t('profile.title')">
              <button
                v-for="(tab, index) in tabs"
                :id="`profile-tab-${tab.key}`"
                :key="tab.key"
                type="button"
                role="tab"
                :aria-selected="activeTab === tab.key"
                :aria-controls="`profile-panel-${tab.key}`"
                :tabindex="activeTab === tab.key ? 0 : -1"
                class="chip shrink-0"
                :class="activeTab === tab.key ? 'chip-active' : ''"
                @click="activeTab = tab.key"
                @keydown="handleTabKeydown($event, index)"
              >
                <UIcon :name="tab.icon" class="h-4 w-4" aria-hidden="true" />
                {{ t(tab.label) }}
              </button>
            </div>

            <div
              :id="`profile-panel-${activeTab}`"
              role="tabpanel"
              :aria-labelledby="`profile-tab-${activeTab}`"
              class="ui-card mt-4 p-5 sm:p-7"
            >
              <h2 class="text-lg font-black text-[var(--color-ink)]">
                {{ t(activeTabMeta.label) }}
              </h2>

              <!-- 个人信息：只列真实存在的字段 -->
              <template v-if="activeTab === 'info'">
                <dl v-if="infoRows.length" class="mt-5 grid gap-3 sm:grid-cols-2">
                  <div
                    v-for="row in infoRows"
                    :key="row.label"
                    class="min-w-0 rounded-[var(--radius-card-sm)] bg-[var(--color-line-soft)] px-4 py-3"
                  >
                    <dt class="flex items-center gap-2 text-[11px] font-bold uppercase tracking-wider text-[var(--color-muted)]">
                      <UIcon :name="row.icon" class="h-3.5 w-3.5 shrink-0 text-[var(--color-brand-500)]" aria-hidden="true" />
                      {{ t(row.label) }}
                    </dt>
                    <dd class="mt-1 truncate text-sm font-medium text-[var(--color-ink)]">
                      {{ row.value }}
                    </dd>
                  </div>
                </dl>
                <p v-else class="mt-5 text-sm text-[var(--color-muted)]">
                  {{ t('shop.noData') }}
                </p>
              </template>

              <!-- 账户设置：真实姓名 / 邮箱 / 手机号可编辑，用户名只读 -->
              <template v-else-if="activeTab === 'settings'">
                <form class="mt-5 grid gap-4 sm:grid-cols-2" @submit.prevent="handleSave">
                  <div>
                    <label for="profile-realname" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
                      {{ t('profile.realname') }}
                    </label>
                    <input
                      id="profile-realname"
                      v-model="form.realname"
                      type="text"
                      autocomplete="name"
                      class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
                      @input="markTouched"
                    >
                  </div>

                  <div>
                    <label for="profile-username" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
                      {{ t('profile.username') }}
                    </label>
                    <input
                      id="profile-username"
                      :value="userInfo?.username || ''"
                      type="text"
                      disabled
                      class="h-11 w-full cursor-not-allowed rounded-[var(--radius-input)] border border-[var(--color-line)] bg-[var(--color-line-soft)] px-4 text-sm text-[var(--color-muted)]"
                    >
                  </div>

                  <div>
                    <label for="profile-email" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
                      {{ t('profile.email') }}
                    </label>
                    <input
                      id="profile-email"
                      v-model="form.email"
                      type="email"
                      autocomplete="email"
                      class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
                      @input="markTouched"
                    >
                  </div>

                  <div>
                    <label for="profile-phone" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
                      {{ t('profile.phone') }}
                    </label>
                    <input
                      id="profile-phone"
                      v-model="form.phone"
                      type="tel"
                      autocomplete="tel"
                      class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
                      @input="markTouched"
                    >
                  </div>

                  <div class="sm:col-span-2">
                    <button
                      type="submit"
                      class="ui-pill ui-pill-brand h-11 px-6 text-sm disabled:cursor-not-allowed disabled:opacity-45"
                      :disabled="!dirty"
                    >
                      <UIcon name="lucide:save" class="h-4 w-4" aria-hidden="true" />
                      {{ t('profile.saveChanges') }}
                    </button>
                  </div>
                </form>
              </template>

              <!--
                我的收藏：真实数据（GET /api/favorite，商品信息由后端实时读取，
                已下架/已删除的商品不会出现在这里）。支持在本页直接取消收藏。
              -->
              <template v-else-if="activeTab === 'favorites'">
                <p v-if="favoritesLoaded && favoriteTotal > 0" class="mt-1.5 text-[13px] text-[var(--color-muted)]">
                  {{ t('shop.profileFavoritesTotal', { count: favoriteTotal }) }}
                </p>

                <!-- 加载中：骨架屏（不写死行数，固定 3 行即可，避免猜测每页条数） -->
                <ul v-if="favoritesLoading" class="mt-4 space-y-3" aria-busy="true">
                  <li v-for="index in 3" :key="`fav-skeleton-${index}`" class="flex gap-4">
                    <span class="h-20 w-20 shrink-0 animate-pulse rounded-[var(--radius-tile)] bg-[var(--color-line-soft)]"></span>
                    <span class="flex-1 space-y-2 py-1">
                      <span class="block h-4 w-2/3 animate-pulse rounded bg-[var(--color-line-soft)]"></span>
                      <span class="block h-4 w-1/3 animate-pulse rounded bg-[var(--color-line-soft)]"></span>
                    </span>
                  </li>
                </ul>

                <!-- 加载失败：把后端/网络的原话展示出来，用户才知道是重试还是先别管 -->
                <div v-else-if="favoritesError" class="mt-5 flex items-start gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-4 py-3 text-[13px] font-medium text-[var(--color-error-600)]">
                  <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                  <span>{{ t('shop.profileFavoritesLoadError') }}：{{ favoritesError }}</span>
                </div>

                <!-- 有收藏：紧凑列表（封面 + 标题 + 价格 + 取消收藏），点封面/标题进详情 -->
                <ul v-else-if="favoriteItems.length" class="mt-4 divide-y divide-[var(--color-line-soft)]">
                  <li v-for="item in favoriteItems" :key="item.id" class="flex items-center gap-4 py-4">
                    <NuxtLinkLocale
                      :to="`/products/${item.id}`"
                      class="block h-20 w-20 shrink-0 overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-line-soft)]"
                    >
                      <img
                        v-if="favoriteCover(item)"
                        :src="favoriteCover(item)"
                        :alt="item.title"
                        class="h-full w-full object-cover"
                        loading="lazy"
                      >
                      <span v-else class="flex h-full w-full items-center justify-center">
                        <UIcon name="lucide:image-off" class="h-6 w-6 text-[var(--color-muted)]" aria-hidden="true" />
                      </span>
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

                    <button
                      type="button"
                      class="ui-pill ui-pill-ghost h-9 shrink-0 px-4 text-[13px] disabled:cursor-not-allowed disabled:opacity-45"
                      :disabled="favoriteRemovingId === item.id"
                      :aria-label="`${t('product.removeFromWishlist')} - ${item.title}`"
                      @click="handleRemoveFavorite(item.id)"
                    >
                      <UIcon
                        :name="favoriteRemovingId === item.id ? 'lucide:loader-circle' : 'lucide:heart-off'"
                        class="h-4 w-4"
                        :class="favoriteRemovingId === item.id ? 'animate-spin' : ''"
                        aria-hidden="true"
                      />
                      {{ t('product.removeFromWishlist') }}
                    </button>
                  </li>
                </ul>

                <!-- 真的没有收藏 -->
                <div v-else class="flex flex-col items-center justify-center py-14 text-center">
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
              </template>

              <!--
                我的订单 / 收货地址：
                后端目前**没有**订单、地址接口接入本页，所以这里只给真实空态。
                原来的 ORD-2024-001 等订单号、2 条美国地址、"View All"、
                美元金额按钮全部删除。
              -->
              <div v-else class="flex flex-col items-center justify-center py-14 text-center">
                <span class="flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-brand-50)] text-[var(--color-brand-400)]">
                  <UIcon :name="emptyIcon" class="h-9 w-9" aria-hidden="true" />
                </span>
                <p class="mt-5 text-sm font-bold text-[var(--color-ink)]">
                  {{ t(emptyLabel) }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- Footer -->
    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import { useAuth } from '~~/composables/useAuth'
import { useFavoriteList, extractFavoriteErrorMessage } from '~~/composables/useFavorites'
import { useStorePrice } from '~~/composables/useProducts'
import { resolveCoverUrl, resolvePriceView } from '~~/types/shop-product'

/**
 * 个人中心。
 *
 * 本页最重要的一件事：**删掉所有编造的 mock 数据**。
 * 目前只有「我的收藏」接通了真实接口（GET /api/favorite，见 composables/useFavorites.ts）；
 * 订单 / 收货地址 / 优惠券 / 积分 / 会员等级在本页仍无接口，
 * 因此这些位置只能显示真实空态或真实的 0，绝不显示假订单号、假金额、假等级。
 */

const { t, locale } = useI18n()
const { userInfo, isLoggedIn, logout } = useAuth()
const localePath = useLocalePath()
const toast = useToast()
const { formatCompact } = useStorePrice()

type TabKey = 'info' | 'orders' | 'favorites' | 'addresses' | 'settings'

const tabs: { key: TabKey; label: string; icon: string }[] = [
  { key: 'info', label: 'shop.profilePersonalInfo', icon: 'lucide:user-round' },
  { key: 'orders', label: 'shop.profileMyOrders', icon: 'lucide:package' },
  { key: 'favorites', label: 'shop.profileMyFavorites', icon: 'lucide:heart' },
  { key: 'addresses', label: 'shop.profileAddresses', icon: 'lucide:map-pin' },
  { key: 'settings', label: 'shop.profileSettings', icon: 'lucide:settings' },
]

// 默认落在"个人信息"：一进来就能看到真实资料，比直接看订单/地址空态友好
const activeTab = ref<TabKey>('info')

const activeTabMeta = computed(
  () => tabs.find(tab => tab.key === activeTab.value)
    ?? { key: 'info' as TabKey, label: 'shop.profilePersonalInfo', icon: 'lucide:user-round' },
)

// ---------------------------------------------------------------------------
// 我的收藏（真实数据）
// ---------------------------------------------------------------------------

const {
  items: favoriteItems,
  total: favoriteTotal,
  loading: favoritesLoading,
  loaded: favoritesLoaded,
  error: favoritesError,
  load: loadFavorites,
  remove: removeFavorite,
} = useFavoriteList()

/**
 * 4 张统计卡。
 * 「收藏数」用收藏接口返回的真实 total；其余三项后端没有对应接口，
 * 数值只能写成常量 0（接口补齐后再接数据）——**绝不用编造的数字填充**。
 */
const stats = computed(() => [
  { label: 'shop.profileOrders', value: 0, icon: 'lucide:package', iconClass: 'bg-[var(--color-brand-50)] text-[var(--color-brand-600)]' },
  { label: 'shop.profileFavorites', value: favoriteTotal.value, icon: 'lucide:heart', iconClass: 'bg-[var(--color-error-50)] text-[var(--color-error-600)]' },
  { label: 'shop.profileCoupons', value: 0, icon: 'lucide:ticket', iconClass: 'bg-[var(--color-warning-50)] text-[var(--color-warning-600)]' },
  { label: 'shop.profilePoints', value: 0, icon: 'lucide:gift', iconClass: 'bg-[var(--color-success-50)] text-[var(--color-success-600)]' },
])

/** 正在取消收藏的那一行 id（按钮转圈 + 防重复点击） */
const favoriteRemovingId = ref('')

function favoriteCover(item: { coverUrl: string | null; mainImage: string | null }): string {
  return resolveCoverUrl(item)
}

/** 收藏列表里的价格：与商品卡片同一套口径（minPrice 优先、整数不带小数） */
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

/**
 * 收藏数据在**登录态恢复后立刻拉一次**（不是等用户切到收藏标签页）：
 * 顶部「收藏数」统计卡要立即显示真实数量，否则用户会看到"收藏数 0"却明明收藏过商品。
 *   - 未登录不打接口（个人中心未登录时走的是登录引导，不该产生必然 401 的请求）；
 *   - 已经加载过就不再重复拉（取消收藏是本地移除那一行，不需要整页重拉）。
 */
watch(isLoggedIn, () => {
  if (isLoggedIn.value && !favoritesLoaded.value) {
    void loadFavorites()
  }
}, { immediate: true })

/** 空态图标：订单 / 地址各自不同，纯装饰，不暗示任何数据（收藏面板有自己的空态） */
const EMPTY_ICONS: Record<string, string> = {
  info: 'lucide:inbox',
  orders: 'lucide:package-open',
  addresses: 'lucide:map-pin-off',
}
const emptyIcon = computed(() => EMPTY_ICONS[activeTab.value] ?? 'lucide:inbox')

/**
 * 空态文案：订单 / 地址各自不同。
 * 这两个面板后端在本页还没有接口，所以只显示真实空态 ——
 * 不出现任何编造的订单号、地址或金额。
 */
const EMPTY_LABELS: Record<string, string> = {
  info: 'shop.noData',
  orders: 'shop.profileOrdersEmpty',
  addresses: 'shop.profileAddressesEmpty',
}
const emptyLabel = computed(() => EMPTY_LABELS[activeTab.value] ?? 'shop.noData')

const displayName = computed(() => userInfo.value?.realname || userInfo.value?.username || '')

/** 头像兜底：没有头像图时用姓名首字母（中文取第一个字） */
const avatarInitial = computed(() => {
  const source = displayName.value.trim()
  return source ? source.charAt(0).toUpperCase() : ''
})

const contactRows = computed(() => {
  const rows: { label: string; value: string; icon: string }[] = []
  if (userInfo.value?.email) {
    rows.push({ label: 'profile.email', value: String(userInfo.value.email), icon: 'lucide:mail' })
  }
  if (userInfo.value?.phone) {
    rows.push({ label: 'profile.phone', value: String(userInfo.value.phone), icon: 'lucide:phone' })
  }
  return rows
})

/**
 * 注册时间：userInfo.createTime 是后端 SysUser 真实字段（LoginController 直接返回 SysUser）。
 * 后端可能给 "2024-01-15 10:20:30"，也可能是 ISO，这里两种都能解析；
 * 真的拿不到就返回空串 -> 整块不渲染（绝不写死一个日期）。
 */
const memberSinceText = computed(() => {
  const raw = userInfo.value?.createTime
  if (!raw) return ''
  const parsed = new Date(String(raw).replace(' ', 'T'))
  if (Number.isNaN(parsed.getTime())) return String(raw)
  return new Intl.DateTimeFormat(locale.value, { year: 'numeric', month: 'short', day: 'numeric' }).format(parsed)
})

/** 个人信息面板：只放真实有值的字段，空字段整行不渲染 */
const infoRows = computed(() => {
  const rows: { label: string; value: string; icon: string }[] = []
  if (userInfo.value?.realname) {
    rows.push({ label: 'profile.realname', value: String(userInfo.value.realname), icon: 'lucide:user-round' })
  }
  if (userInfo.value?.username) {
    rows.push({ label: 'profile.username', value: String(userInfo.value.username), icon: 'lucide:at-sign' })
  }
  rows.push(...contactRows.value)
  if (memberSinceText.value) {
    rows.push({ label: 'profile.memberSince', value: memberSinceText.value, icon: 'lucide:calendar' })
  }
  return rows
})

// ---------------------------------------------------------------------------
// 账户设置表单
// ---------------------------------------------------------------------------

const form = reactive({ realname: '', email: '', phone: '' })
/** 用户是否动过表单：动过之后就不要再被 userInfo 的变化覆盖，避免正在输入的内容被冲掉 */
const formTouched = ref(false)

function markTouched() {
  formTouched.value = true
}

function syncForm() {
  form.realname = userInfo.value?.realname ?? ''
  form.email = userInfo.value?.email ?? ''
  form.phone = userInfo.value?.phone ?? ''
}

// immediate：SSR 时 userInfo 为 null；客户端 init-auth 插件恢复登录态后，
// 这个 watch 会再跑一次把真实资料填进表单
watch(() => userInfo.value, () => {
  if (!formTouched.value) syncForm()
}, { immediate: true })

const dirty = computed(() => {
  const current = userInfo.value
  if (!current) return false
  return form.realname !== (current.realname ?? '')
    || form.email !== (current.email ?? '')
    || form.phone !== (current.phone ?? '')
})

/**
 * 保存资料。
 *
 * 注意：后端目前**没有**更新用户资料的接口（server/api 下只有 auth/login + auth/logout），
 * 所以这里只更新前端共享的 userInfo 状态并写回 localStorage —— 写进去的是用户自己刚输入
 * 的内容，不属于伪造数据，但它**不会**同步到服务器，换设备/清缓存后会变回后端原值。
 * 等 BFF 提供 /api/auth/profile 之后，这里应替换成真实请求。
 */
function handleSave() {
  const current = userInfo.value
  if (!current) return

  const next = {
    ...current,
    realname: form.realname.trim(),
    email: form.email.trim(),
    phone: form.phone.trim(),
  }
  userInfo.value = next
  form.realname = next.realname
  form.email = next.email
  form.phone = next.phone
  formTouched.value = false

  if (import.meta.client) {
    // key 必须与 composables/useAuth.ts 里 restoreUser 读取的 'user-info' 一致
    localStorage.setItem('user-info', JSON.stringify(next))
  }

  toast.add({ title: t('shop.profileSaveSuccess'), color: 'success', icon: 'lucide:check' })
}

/** WAI-ARIA tabs 键盘交互：左右方向键循环，Home / End 跳首尾，并把焦点移到新选中的标签 */
function handleTabKeydown(event: KeyboardEvent, index: number) {
  const last = tabs.length - 1
  let next = index
  if (event.key === 'ArrowRight') next = index === last ? 0 : index + 1
  else if (event.key === 'ArrowLeft') next = index === 0 ? last : index - 1
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = last
  else return

  event.preventDefault()
  const target = tabs[next]
  if (!target) return
  activeTab.value = target.key
  if (import.meta.client) {
    document.getElementById(`profile-tab-${target.key}`)?.focus()
  }
}

async function handleLogout() {
  await logout()
  await navigateTo(localePath('/login'))
}

useSeoMeta({
  title: () => t('profile.title'),
})
</script>
