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
                /register 现在是**真实存在**的页面（app/pages/register.vue，契约 4.2）。
                这里曾经把「注册账号」也指向 /login 并附了一段"站点没有注册页"的注释 ——
                那是当时的权宜之计（宁可两个入口都落到登录页，也不给用户一个 404 的假入口）。
                注册页已落地，这个 404 顾虑消失，两个入口各自直达即可。
                （Header.vue 的「登录 / 注册」同批做了同样的拆分。）
              -->
              <NuxtLinkLocale to="/register" class="ui-pill ui-pill-ghost h-11 px-7 text-sm">
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
                      v-if="userInfo?.avatar && !avatarFailed"
                      :src="userInfo.avatar"
                      :alt="displayName"
                      width="64"
                      height="64"
                      class="h-full w-full object-cover"
                      loading="lazy"
                      @error="avatarFailed = true"
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
                  @click="selectTab('settings')"
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
              统计卡：**收藏数 / 订单数已接通真实接口**；
              优惠券 / 积分后端还没有接口 → 显示「—」+「未开通」角标，
              不显示 0（0 会被当成"我有 0 张券"），更不用 12 / 28 / 1250 这种编造数字。
            -->
            <div class="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
              <div v-for="stat in stats" :key="stat.label" class="ui-card flex items-center gap-3 p-4">
                <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-[12px]" :class="stat.iconClass">
                  <UIcon :name="stat.icon" class="h-5 w-5" aria-hidden="true" />
                </span>
                <span class="min-w-0">
                  <!--
                    后端尚未提供接口的指标（优惠券/积分）显示「—」+「未开通」，
                    绝不显示 0 —— 0 会被当成"我有 0 张优惠券"，与"功能还没做"是两回事。
                  -->
                  <span v-if="stat.available" class="tabular block text-2xl font-black leading-none text-[var(--color-ink)]">
                    {{ stat.value }}
                  </span>
                  <span v-else class="tabular block text-2xl font-black leading-none text-[var(--color-muted)]">—</span>
                  <span class="mt-1 flex items-center gap-1.5 truncate text-xs text-[var(--color-muted)]">
                    {{ t(stat.label) }}
                    <span
                      v-if="!stat.available"
                      class="rounded-full bg-[var(--color-surface-secondary)] px-1.5 py-0.5 text-[10px] font-bold text-[var(--color-muted)]"
                    >
                      {{ t('profile.profileStatComingSoon') }}
                    </span>
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
                @click="selectTab(tab.key)"
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
              <ProfileInfoPanel v-if="activeTab === 'info'" />

              <!-- 账户设置：真实姓名 / 邮箱 / 手机号可编辑，用户名只读 -->
              <ProfileSettingsPanel v-else-if="activeTab === 'settings'" />

              <!--
                我的收藏：真实数据（GET /api/favorite，商品信息由后端实时读取，
                已下架/已删除的商品不会出现在这里）。支持在本页直接取消收藏。
              -->
              <ProfileFavoritesPanel v-else-if="activeTab === 'favorites'" />

              <!--
                我的订单：真实数据（GET /api/order）。
                这里刻意做得比 /orders 整页更紧凑（单行摘要 + 缩略图条），
                但状态文字与按钮可见性**完全沿用后端字段**，与 /orders 是同一套口径；
                需要看明细/付款/退款就跳 /orders。
              -->
              <ProfileOrdersPanel v-else-if="activeTab === 'orders'" />

              <ProfileAddressesPanel v-else-if="activeTab === 'addresses'" />
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 个人中心是私有页面：未登录 / 登录过期时由 auth 中间件重定向到登录页，
 * 并带上 redirect 参数，登录成功后回到这里。
 */
definePageMeta({ middleware: 'auth' })

import { useAuth } from '~~/composables/useAuth'
import { useFavoriteList } from '~~/composables/useFavorites'
import { useOrderList } from '~~/composables/useOrderList'
import { useAddressBook } from '~~/composables/useAddressBook'
import { useProfileAccount } from '~~/composables/useProfileAccount'
import { useProfileFields } from '~~/composables/useProfileFields'

/**
 * 个人中心。
 *
 * 本页最重要的一件事：**删掉所有编造的 mock 数据**。
 * 「我的收藏」「我的订单」「收货地址」三个面板都已接通真实接口
 * （GET /api/favorite、GET /api/order、GET /api/address/list）；
 * 优惠券 / 积分后端仍无接口，因此只能显示真实的 0，
 * 绝不显示假订单号、假金额、假地址、假等级。
 *
 * 订单面板的两条硬规则与 /orders 整页完全一致（同一套后端契约，不能各写一套）：
 *   1) **按钮可见性只用后端 canPay / canCancel / canReceive / canRefund**，前端不重算状态机；
 *   2) **状态文字一律用后端 orderStatusLabel**，前端不自建「状态码 -> 中文」映射。
 */

const { t } = useI18n()
const { userInfo, isLoggedIn, logout } = useAuth()
const localePath = useLocalePath()
const route = useRoute()

/** 头像加载失败时回退到首字母/图标占位（外链头像会 403/被下线） */
const avatarFailed = ref(false)
watch(() => userInfo.value?.avatar, () => { avatarFailed.value = false })

type TabKey = 'info' | 'orders' | 'favorites' | 'addresses' | 'settings'

const tabs: { key: TabKey; label: string; icon: string }[] = [
  { key: 'info', label: 'shop.profilePersonalInfo', icon: 'lucide:user-round' },
  { key: 'orders', label: 'shop.profileMyOrders', icon: 'lucide:package' },
  { key: 'favorites', label: 'shop.profileMyFavorites', icon: 'lucide:heart' },
  { key: 'addresses', label: 'shop.profileAddresses', icon: 'lucide:map-pin' },
  { key: 'settings', label: 'shop.profileSettings', icon: 'lucide:settings' },
]

// 默认落在"个人信息"：一进来就能看到真实资料，比直接看订单/地址空态友好
//
// ★ 但标签页必须能**通过 URL 指定**（?tab=orders / favorites / addresses / settings）。
//   否则会出这个真实缺陷：Header 下拉菜单里的「个人中心 / 我的订单 / 收藏夹」三个入口
//   都指向 /profile，而本页永远从 info 开始 —— 用户点「我的订单」也只会停在「个人信息」，
//   看起来像"点了没反应/跳错了"。让 URL 成为标签页的唯一真相来源后，
//   这些入口只要带上 ?tab=xxx 就能直达对应面板，且刷新/分享链接都能保持在同一面板。
const TAB_KEYS: TabKey[] = ['info', 'orders', 'favorites', 'addresses', 'settings']

/** 把 URL 上的 ?tab= 解析成合法标签；非法/缺失一律回退 info（不做 404，也不留空面板） */
function resolveTabFromQuery(value: unknown): TabKey {
  const raw = String(Array.isArray(value) ? value[0] : (value ?? '')).trim()
  return (TAB_KEYS as string[]).includes(raw) ? (raw as TabKey) : 'info'
}

const activeTab = ref<TabKey>(resolveTabFromQuery(route.query.tab))

/**
 * 切换标签时把 URL 同步过去（replace 而不是 push）：
 *   - replace 不往历史里塞记录，用户点几次标签后按浏览器「返回」不会被标签页困住；
 *   - 同时又让当前面板**可刷新、可复制链接**（这正是 Header 入口依赖的能力）。
 * 只在 query 真的变了才跳，避免无意义的重复导航。
 */
function selectTab(key: TabKey) {
  activeTab.value = key
  const current = resolveTabFromQuery(route.query.tab)
  if (current === key) return
  void navigateTo(
    localePath({ path: '/profile', query: key === 'info' ? {} : { tab: key } }),
    { replace: true },
  )
}

// 浏览器前进/后退时 query 变了，面板要跟着变（否则后退了但界面不动）
watch(() => route.query.tab, (value) => {
  const next = resolveTabFromQuery(value)
  if (next !== activeTab.value) activeTab.value = next
})

const activeTabMeta = computed(
  () => tabs.find(tab => tab.key === activeTab.value)
    ?? { key: 'info' as TabKey, label: 'shop.profilePersonalInfo', icon: 'lucide:user-round' },
)

// ---------------------------------------------------------------------------
// 我的收藏（真实数据）
// ---------------------------------------------------------------------------

// 页面只保留「统计卡要用的 total」和「登录后预加载的 load」；
// 列表渲染与取消收藏已搬进 components/profile/FavoritesPanel.vue ——
// 它调用同一个 composable（内部是 useState），拿到的是同一份数据，
// 所以在面板里取消收藏后，顶部统计卡会一起更新。
const {
  total: favoriteTotal,
  loaded: favoritesLoaded,
  load: loadFavorites,
} = useFavoriteList()

/**
 * 4 张统计卡。
 * 「收藏数」「订单数」是真实接口 total；
 * 优惠券 / 积分后端没有对应接口 → `available: false`，页面显示「— / 未开通」，
 * **不显示 0**（0 会被误读成"有 0 张券"，与"功能没做"是两回事），更不编造数字。
 */
const stats = computed(() => [
  { label: 'shop.profileOrders', value: orderTotal.value, available: true, icon: 'lucide:package', iconClass: 'bg-[var(--color-brand-50)] text-[var(--color-brand-600)]' },
  { label: 'shop.profileFavorites', value: favoriteTotal.value, available: true, icon: 'lucide:heart', iconClass: 'bg-[var(--color-error-50)] text-[var(--color-error-600)]' },
  { label: 'shop.profileCoupons', value: 0, available: false, icon: 'lucide:ticket', iconClass: 'bg-[var(--color-warning-50)] text-[var(--color-warning-600)]' },
  { label: 'shop.profilePoints', value: 0, available: false, icon: 'lucide:gift', iconClass: 'bg-[var(--color-success-50)] text-[var(--color-success-600)]' },
])

/**
 * 收藏数据在**登录态恢复后立刻拉一次**（不是等用户切到收藏标签页）：
 * 顶部「收藏数」统计卡要立即显示真实数量，否则用户会看到"收藏数 0"却明明收藏过商品。
 *   - 未登录不打接口（个人中心未登录时走的是登录引导，不该产生必然 401 的请求）；
 *   - 已经加载过就不再重复拉（取消收藏是本地移除那一行，不需要整页重拉）。
 *   - **只在客户端跑**：登录态已在 SSR 播种，`immediate: true` 在服务端也会命中，
 *     会在 SSR setup 里多发一次依赖 cookie 的请求（hydration 后还会再来一次）。
 */
watch(isLoggedIn, () => {
  if (!import.meta.client) return
  if (isLoggedIn.value && !favoritesLoaded.value) {
    void loadFavorites()
  }
}, { immediate: true })

// ---------------------------------------------------------------------------
// 我的订单（真实数据，字段口径与 /orders 页一致）
// ---------------------------------------------------------------------------

// 订单状态与加载逻辑已搬进 composables/useOrderList.ts：
// 页面与 components/profile/OrdersPanel.vue 调用它拿到的是同一份 useState 状态，
// 所以面板里翻页 / 确认收货后刷新，顶部「订单数」统计卡会同步更新。
const { total: orderTotal, load: loadOrders } = useOrderList()

// ---------------------------------------------------------------------------
// 收货地址（真实数据 + 完整增删改 / 设为默认，字段口径与 /address 页一致）
// ---------------------------------------------------------------------------

// 列表渲染、增删改与两个弹层已搬进 components/profile/AddressesPanel.vue，
// 数据与接口在 composables/useAddressBook.ts（useState 共享，页面与面板同一份数据）。
// 页面只保留「登录后预加载一次」用的 load —— 让用户切到该面板前数据就绪。
const { load: loadAddresses } = useAddressBook()

// 注意：「订单/地址/账户资料首次登录后各拉一次」的那个 immediate watch 放在本文件
// setup 的**末尾**（见 loadProfile 定义之后）—— immediate watch 的回调会在 setup
// 期间**同步**执行，如果它被放在 profileLoading / ordersLoading 等 ref 声明之前，
// 回调里访问这些 const 会直接抛
//   ReferenceError: Cannot access 'profileLoading' before initialization
// 而 SSR 下这个异常会让整页 500。这类"顺序依赖"是巨型单文件组件最难查的坑之一，
// 所以：任何 immediate watch 都必须写在它所用到的所有声明之后。

// 展示派生字段（昵称 / 头像首字母 / 联系方式 / 注册时间）与「个人信息」明细行
// 都在 composables/useProfileFields.ts —— 左侧资料卡与个人信息面板共用同一套口径。
const { displayName, avatarInitial, contactRows, memberSinceText } = useProfileFields()

// 账户资料（是否有密码 / 加载状态）与密码表单见：
//   composables/useProfileAccount.ts + components/profile/SettingsPanel.vue
// （hasPassword 的判据说明也随实现移到了 composable 里，避免两处解释不一致）
const { load: loadProfile } = useProfileAccount()

/**
 * 订单、地址与账户资料都在**登录态恢复后立刻各拉一次**（不是等用户切到对应标签页）：
 * 顶部「订单数 / 收藏数」统计卡要立即显示真实数量，否则用户会看到 0 却明明下过单。
 *   - 未登录不打接口（未登录时本页整页是登录引导，不该产生必然 401 的请求）；
 *   - 三个接口都只在登录态**首次**变为 true 时拉一次，避免重试/翻页后被 watch 冲掉。
 *
 * ⚠️ 位置要求：`immediate: true` 的回调会在 setup 期间**同步**执行，所以本段必须放在
 * useOrderList / useAddressBook / useProfileAccount 三个数据源声明**之后**。
 * （历史上这里踩过一次：watch 写在 profileLoading 声明之前，回调同步访问该 ref 直接抛
 *   ReferenceError: Cannot access 'profileLoading' before initialization，
 *   SSR 下表现为 /profile 整页 500。）
 */
const privateDataLoaded = ref(false)

watch(isLoggedIn, () => {
  // SSR 已播种登录态：immediate 在服务端也会命中，这 3 个 load 依赖浏览器 cookie 语义/客户端
  // 交互状态，只应在客户端首次进页面时跑一次（否则 SSR 多发 3 个请求，且部分会 401）
  if (!import.meta.client) return
  if (!isLoggedIn.value || privateDataLoaded.value) return
  privateDataLoaded.value = true
  void loadOrders()
  void loadAddresses()
  void loadProfile()
}, { immediate: true })


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
  // 走 selectTab 而不是直接赋值：键盘切换标签同样要把 ?tab= 同步到 URL
  selectTab(target.key)
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
  // 个人中心是私有页面（昵称/邮箱/手机号），不允许被搜索引擎收录
  robots: 'noindex, nofollow',
})
</script>
