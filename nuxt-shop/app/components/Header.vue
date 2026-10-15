<template>
  <!--
    店铺端全局导航（国内电商风格）。
    两行结构：
      Row 1  Logo | 长搜索框 | 登录/订单/购物车
      Row 2  全部分类 + 一级分类横向导航（桌面端，移动端隐藏）
    移动端收敛为：Logo | 购物车 | 汉堡，搜索框单独一行。
  -->
  <header class="fixed inset-x-0 top-0 z-50 bg-white shadow-[0_1px_0_rgba(31,26,28,0.06)]">
    <!-- Row 1 -->
    <div class="mx-auto flex h-16 max-w-7xl items-center gap-3 px-2 sm:px-3 lg:px-4">
      <!-- Logo -->
      <NuxtLinkLocale to="/" class="flex shrink-0 items-center gap-2" :aria-label="t('common.home')">
        <span
          class="brand-gradient flex h-9 w-9 items-center justify-center rounded-[12px] text-white shadow-[0_4px_12px_-2px_rgba(255,77,109,0.5)]"
        >
          <UIcon name="lucide:zap" class="h-5 w-5" aria-hidden="true" />
        </span>
        <span class="hidden text-[17px] font-black tracking-tight text-[var(--color-ink)] sm:inline">
          {{ brandName }}
        </span>
      </NuxtLinkLocale>

      <!-- Desktop search -->
      <form class="ml-2 hidden flex-1 md:block" role="search" @submit.prevent="submitSearch">
        <div class="flex h-10 items-center rounded-full bg-[var(--color-surface-secondary)] pl-4 pr-1">
          <UIcon name="lucide:search" class="h-4 w-4 shrink-0 text-[var(--color-muted)]" aria-hidden="true" />
          <input
            v-model="keyword"
            type="search"
            :placeholder="t('shop.searchPlaceholder')"
            :aria-label="t('shop.searchPlaceholder')"
            class="h-full min-w-0 flex-1 bg-transparent px-3 text-sm text-[var(--color-ink)] outline-none placeholder:text-[var(--color-muted)]"
          >
          <button
            type="submit"
            class="ui-pill-brand h-8 shrink-0 px-5 text-[13px] font-bold"
          >
            {{ t('shop.searchSubmit') }}
          </button>
        </div>
      </form>

      <!-- Right actions -->
      <div class="ml-auto flex shrink-0 items-center gap-1 sm:gap-2">
        <!--
          未登录时的两个入口。**必须拆成两个独立链接**：
          原来是「登录 / 注册」一个链接指向 /login —— 用户点「注册」落到登录页，
          只能自己再找一遍注册入口（甚至以为注册功能不存在）。
          现在 /register 已经是真实存在的页面（app/pages/register.vue），
          两个入口各自直达，不再是"看起来有注册、点进去是登录"的假入口。
        -->
        <template v-if="!isLoggedIn">
          <NuxtLinkLocale
            :to="loginLink"
            class="hidden text-[13px] font-medium text-[var(--color-ink-soft)] transition-colors hover:text-[var(--color-brand-600)] sm:inline"
          >
            {{ t('login.title') }}
          </NuxtLinkLocale>
          <span class="hidden text-[13px] text-[var(--color-line)] sm:inline" aria-hidden="true">/</span>
          <NuxtLinkLocale
            :to="registerLink"
            class="hidden text-[13px] font-medium text-[var(--color-ink-soft)] transition-colors hover:text-[var(--color-brand-600)] sm:inline"
          >
            {{ t('login.register') }}
          </NuxtLinkLocale>
        </template>

        <!--
          顶栏「我的订单」快捷入口。
          ⚠️ 必须带 ?tab=orders：个人中心默认停在「个人信息」，
             裸跳 /profile 会让用户点「我的订单」却看到个人信息（下拉菜单里同款问题）。
        -->
        <NuxtLinkLocale
          v-if="isLoggedIn"
          :to="{ path: '/profile', query: { tab: 'orders' } }"
          class="hidden items-center gap-1.5 text-[13px] font-medium text-[var(--color-ink-soft)] transition-colors hover:text-[var(--color-brand-600)] sm:flex"
        >
          <UIcon name="lucide:package" class="h-4 w-4" aria-hidden="true" />
          {{ t('nav.orders') }}
        </NuxtLinkLocale>

        <!-- Cart -->
        <NuxtLinkLocale
          to="/cart"
          class="relative flex h-10 items-center gap-1.5 rounded-full px-3 text-[13px] font-bold text-[var(--color-ink)] transition-colors hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)]"
          :aria-label="cartAriaLabel"
        >
          <UIcon name="lucide:shopping-cart" class="h-[18px] w-[18px]" aria-hidden="true" />
          <span class="hidden sm:inline">{{ t('nav.cart') }}</span>
          <span
            v-if="cartBadge"
            class="absolute -right-0.5 top-0 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-[var(--color-brand-500)] px-1 text-[10px] font-black leading-none text-white"
            aria-hidden="true"
          >
            {{ cartBadge }}
          </span>
        </NuxtLinkLocale>

        <!-- Language (keep existing multi-language feature) -->
        <UDropdownMenu :items="localeMenuItems" :popper="{ placement: 'bottom-end' }" :modal="false">
          <button
            type="button"
            class="hidden h-10 items-center gap-1 rounded-full px-3 text-[13px] font-medium text-[var(--color-ink-soft)] transition-colors hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] sm:flex"
            aria-label="Switch language"
          >
            <UIcon name="lucide:globe" class="h-[18px] w-[18px]" aria-hidden="true" />
            <span class="uppercase">{{ locale }}</span>
          </button>
          <template #item-leading="{ item }">
            <span class="text-base leading-none">{{ item._flag }}</span>
          </template>
        </UDropdownMenu>

        <!-- User -->
        <UDropdownMenu v-if="isLoggedIn" :items="userMenuItems" :popper="{ placement: 'bottom-end' }" :modal="false">
          <button type="button" class="hidden h-10 items-center gap-1.5 sm:flex" :aria-label="t('nav.profile')">
            <span class="h-8 w-8 overflow-hidden rounded-full bg-[var(--color-brand-50)]">
              <img
                v-if="userInfo?.avatar"
                :src="userInfo.avatar"
                :alt="userInfo?.realname || t('nav.profile')"
                class="h-full w-full object-cover"
              >
              <span v-else class="flex h-full w-full items-center justify-center text-[var(--color-brand-600)]">
                <UIcon name="lucide:user" class="h-4 w-4" aria-hidden="true" />
              </span>
            </span>
            <UIcon name="lucide:chevron-down" class="h-3.5 w-3.5 text-[var(--color-muted)]" aria-hidden="true" />
          </button>
        </UDropdownMenu>

        <!-- Mobile menu toggle -->
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-[var(--color-ink)] transition-colors hover:bg-[var(--color-brand-50)] md:hidden"
          :aria-label="t('nav.menu')"
          :aria-expanded="mobileMenuOpen"
          @click="mobileMenuOpen = !mobileMenuOpen"
        >
          <UIcon :name="mobileMenuOpen ? 'lucide:x' : 'lucide:menu'" class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>
    </div>

    <!-- Row 2: categories (desktop) -->
    <nav class="hidden border-t border-[var(--color-line-soft)] md:block" :aria-label="t('shop.allCategories')">
      <div class="mx-auto flex h-[52px] max-w-7xl items-center gap-1 px-2 sm:px-3 lg:px-4">
        <NuxtLinkLocale
          to="/categories"
          class="flex h-9 items-center gap-1.5 rounded-full px-4 text-[13px] transition-colors"
          :class="isCategoriesIndex ? NAV_ITEM_ACTIVE : NAV_ITEM_IDLE"
          :aria-current="isCategoriesIndex ? 'page' : undefined"
        >
          <UIcon name="lucide:layout-grid" class="h-4 w-4" aria-hidden="true" />
          {{ t('shop.allCategories') }}
        </NuxtLinkLocale>

        <span class="mx-1 h-5 w-px bg-[var(--color-line)]" aria-hidden="true" />

        <!-- 当前分类高亮：命中一级分类、或命中的二级分类的一级父级 -->
        <NuxtLinkLocale
          v-for="cat in topCategories"
          :key="cat.id"
          :to="`/categories/${cat.id}`"
          class="rounded-full px-3 py-2 text-[13px] transition-colors"
          :class="activeCategoryId === cat.id ? NAV_ITEM_ACTIVE : NAV_ITEM_IDLE"
          :aria-current="activeCategoryId === cat.id ? 'page' : undefined"
        >
          {{ cat.name }}
        </NuxtLinkLocale>

        <NuxtLinkLocale
          to="/products"
          class="ml-auto rounded-full px-3 py-2 text-[13px] transition-colors"
          :class="isProductsPage
            ? NAV_ITEM_ACTIVE
            : 'font-medium text-[var(--color-muted)] hover:text-[var(--color-brand-600)]'"
          :aria-current="isProductsPage ? 'page' : undefined"
        >
          {{ t('products.all') }}
        </NuxtLinkLocale>
      </div>
    </nav>

    <!-- Mobile search row -->
    <form class="border-t border-[var(--color-line-soft)] px-4 pb-3 pt-2.5 md:hidden" role="search" @submit.prevent="submitSearch">
      <div class="flex h-10 items-center rounded-full bg-[var(--color-surface-secondary)] pl-4 pr-1">
        <UIcon name="lucide:search" class="h-4 w-4 shrink-0 text-[var(--color-muted)]" aria-hidden="true" />
        <input
          v-model="keyword"
          type="search"
          :placeholder="t('shop.searchPlaceholder')"
          :aria-label="t('shop.searchPlaceholder')"
          class="h-full min-w-0 flex-1 bg-transparent px-3 text-sm text-[var(--color-ink)] outline-none placeholder:text-[var(--color-muted)]"
        >
        <button type="submit" class="ui-pill-brand h-8 shrink-0 px-4 text-[13px] font-bold">
          {{ t('shop.searchSubmit') }}
        </button>
      </div>
    </form>

    <!-- Mobile drawer -->
    <div
      class="overflow-hidden border-t border-[var(--color-line-soft)] bg-white transition-[max-height,opacity] duration-300 ease-out md:hidden"
      :class="mobileMenuOpen ? 'max-h-[32rem] opacity-100' : 'max-h-0 opacity-0'"
    >
      <div class="space-y-3 px-4 py-4">
        <div>
          <p class="mb-2 text-xs font-bold uppercase tracking-wider text-[var(--color-muted)]">
            {{ t('shop.allCategories') }}
          </p>
          <div class="flex flex-wrap gap-2">
            <NuxtLinkLocale
              v-for="cat in topCategories"
              :key="cat.id"
              :to="`/categories/${cat.id}`"
              class="chip"
              @click="mobileMenuOpen = false"
            >
              {{ cat.name }}
            </NuxtLinkLocale>
            <NuxtLinkLocale to="/categories" class="chip" @click="mobileMenuOpen = false">
              {{ t('shop.viewAll') }}
            </NuxtLinkLocale>
          </div>
        </div>

        <div class="space-y-1 border-t border-[var(--color-line-soft)] pt-3">
          <NuxtLinkLocale
            to="/products"
            class="flex items-center gap-3 rounded-[12px] px-3 py-2.5 text-sm font-medium text-[var(--color-ink-soft)] hover:bg-[var(--color-brand-50)]"
            @click="mobileMenuOpen = false"
          >
            <UIcon name="lucide:package-search" class="h-4 w-4" aria-hidden="true" />
            {{ t('products.all') }}
          </NuxtLinkLocale>
          <NuxtLinkLocale
            v-if="isLoggedIn"
            to="/profile"
            class="flex items-center gap-3 rounded-[12px] px-3 py-2.5 text-sm font-medium text-[var(--color-ink-soft)] hover:bg-[var(--color-brand-50)]"
            @click="mobileMenuOpen = false"
          >
            <UIcon name="lucide:user" class="h-4 w-4" aria-hidden="true" />
            {{ t('nav.profile') }}
          </NuxtLinkLocale>
        </div>

        <div class="border-t border-[var(--color-line-soft)] pt-3">
          <p class="mb-2 text-xs font-bold uppercase tracking-wider text-[var(--color-muted)]">
            {{ t('nav.language') }}
          </p>
          <div class="space-y-1">
            <button
              v-for="loc in locales"
              :key="loc.code"
              type="button"
              class="flex w-full items-center gap-3 rounded-[12px] px-3 py-2.5 text-sm font-medium transition-colors"
              :class="loc.code === locale
                ? 'bg-[var(--color-brand-50)] text-[var(--color-brand-600)]'
                : 'text-[var(--color-ink-soft)] hover:bg-[var(--color-surface-secondary)]'"
              @click="changeLocale(loc.code); mobileMenuOpen = false"
            >
              <span class="text-base leading-none">{{ loc.icon }}</span>
              <span class="flex-1 text-left">{{ loc.name }}</span>
              <UIcon v-if="loc.code === locale" name="lucide:check" class="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        </div>

        <div class="border-t border-[var(--color-line-soft)] pt-3">
          <!-- 移动端：登录 / 注册 各自一个入口（原来只有一个「登录」落到 /login） -->
          <NuxtLinkLocale
            v-if="!isLoggedIn"
            :to="loginLink"
            class="ui-pill-brand h-11 w-full text-sm"
            @click="mobileMenuOpen = false"
          >
            {{ t('login.title') }}
          </NuxtLinkLocale>
          <NuxtLinkLocale
            v-if="!isLoggedIn"
            :to="registerLink"
            class="ui-pill ui-pill-ghost mt-2 h-11 w-full text-sm"
            @click="mobileMenuOpen = false"
          >
            <UIcon name="lucide:user-plus" class="h-4 w-4" aria-hidden="true" />
            {{ t('login.register') }}
          </NuxtLinkLocale>
          <button
            v-if="isLoggedIn"
            type="button"
            class="flex w-full items-center justify-center gap-2 rounded-full border border-[var(--color-line)] px-4 py-2.5 text-sm font-medium text-[var(--color-error)]"
            @click="handleLogout"
          >
            <UIcon name="lucide:log-out" class="h-4 w-4" aria-hidden="true" />
            {{ t('nav.logout') }}
          </button>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { StoreCategoryNode } from '~~/types/store-category'
import { useAuth } from '~~/composables/useAuth'
import { useCart } from '~~/composables/useCart'

/**
 * 全局导航。
 *
 * 分类行读取的是后端真实分类（/api/categories），**不硬编码任何分类名**：
 * 换一个店铺、换一套分类，导航自动跟着变 —— 这是「店铺端要通用」的关键。
 * 与首页共用同一个 useFetch key，因此不会重复请求。
 */
const { t, locale, locales } = useI18n()
const switchLocalePath = useSwitchLocalePath()
const localePath = useLocalePath()
const router = useRouter()
const route = useRoute()
const { userInfo, isLoggedIn, logout } = useAuth()
const { count, refreshCount } = useCart()

const mobileMenuOpen = ref(false)
const keyword = ref('')

/**
 * 登录入口链接：必须带上**当前页面**作为 redirect，登录后才能回到用户原本在看的地方。
 *
 * 之前这里直接 to="/login"（不带 redirect），而 login.vue 在没有 redirect 时会
 * 兜底跳到个人中心 —— 结果用户从首页点「登录」，登录完莫名其妙出现在个人中心。
 *
 * ⚠️ 不要在当前已经在登录页时再套一层 redirect=/login，那只会让登录后回到登录页。
 * 这种情况直接给不带的裸路径，让 login.vue 用它自己的兜底逻辑。
 *
 * ⚠️⚠️ 这里返回的必须是**不带 locale 前缀**的路径，绝对不能套 localePath()。
 * 模板里两个使用处都是 <NuxtLinkLocale>，它自己会补 locale 前缀；
 * 如果这里再套一层 localePath，就会拼成 /zh/zh/login 这种不存在的路径：
 *   - 它在 SSR 阶段被 vue-router 解析失败，每个页面都会打一条
 *     "[VUE_ROUTER_R0004] No match found for location with path /zh/zh/login"；
 *   - 现在渲染出的 href 之所以还是对的，只是因为 NuxtLinkLocale 渲染时把重复前缀
 *     纠正掉了 —— 这是"恰好被纠正"，不能依赖。一旦该纠正行为变化（升级 Nuxt 等），
 *     用户点「登录」就会直接 404。
 * 同样的坑在 profile.vue 的「查看全部订单」链接上也踩过一次（/zh/zh/orders），
 * 结论一致：**谁负责补前缀，就只让一方补**，另一方给裸路径。
 */
const loginLink = computed(() => {
  if (route.path.endsWith('/login')) {
    return '/login'
  }
  return { path: '/login', query: { redirect: route.fullPath } }
})

/**
 * 注册入口链接。
 *
 * 与 loginLink 一样，返回的必须是**不带 locale 前缀**的裸路径，
 * 使用处是 <NuxtLinkLocale>，前缀由它补 —— 这里再套 localePath() 就会得到 /zh/zh/register
 * （与上面 loginLink 那段注释里记录的坑完全同一个）。
 *
 * 这里**刻意不带 redirect**：register.vue 在注册成功后直接签发 token 并登录（契约 2.2），
 * 完成后由它自己决定去哪。硬塞一个 redirect=/register 只会让用户注册完又回到注册页。
 * 唯一的例外是用户已经在 /register 上（刷新/直接访问），此时也让链接保持裸路径，
 * 避免出现 redirect=/register 这种自指参数。
 */
const registerLink = computed(() => '/register')

/**
 * 站点字标。
 *
 * ⚠️ 这里**不能**用 t('home.title')：它的值是「欢迎来到 Nuxt Shop」，
 * 放进 Logo 位置会渲染成一整句话。
 *
 * 真实店铺名在 `ck_shop_store.name`（如「户外专卖店」），但店铺端目前**没有**
 * 获取店铺信息的接口（`theme_config` 也是 NULL），所以先用这个中性字标兜底。
 * 后端补上 `GET /api/store/info` 后，这里改为读店铺名 + `logo_url` 即可。
 */
const brandName = computed(() => t('shop.brandName'))

const { data: categoryTree } = await useFetch<StoreCategoryNode[]>('/api/categories', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `store-categories-${locale.value}`),
  default: () => [],
  watch: [locale],
})

/** /api/categories 返回的就是分类树的**根节点数组**，直接取前 7 个即可 */
const topCategories = computed(() => (categoryTree.value || []).slice(0, 7))

// ---------------------------------------------------------------------------
// 分类导航的激活态
// ---------------------------------------------------------------------------
//
// 之前「全部分类」是写死的粉色胶囊，它在任何页面都亮着 ——
// 用户明明在「户外服装」分类页，看到的却是「全部分类」处于激活状态。
// 激活态必须由当前路由推导出来，而不是写死在模板里。

/**
 * 当前路由命中的**一级分类** id。
 * 命中的是二级分类时向上回溯到它的一级父级 —— 导航栏只展示一级分类，
 * 如果只做精确匹配，user 点进二级分类后整行就都不亮了。
 */
const activeCategoryId = computed<string>(() => {
  const match = /\/categories\/([^/?#]+)/.exec(route.path)
  if (!match?.[1]) return ''
  const id = decodeURIComponent(match[1])
  for (const top of categoryTree.value || []) {
    if (top.id === id) return id
    if ((top.children || []).some(child => child.id === id)) return top.id
  }
  return id
})

/** 分类总览页（/categories） */
const isCategoriesIndex = computed(() => /\/categories\/?$/.test(route.path))
/** 商品页（搜索/全部商品，/products）—— 注意不匹配 /products/{id} 详情页 */
const isProductsPage = computed(() => /\/products\/?$/.test(route.path))

/** 分类项：激活 / 常态。两套 class 都写全，避免 tailwind 的类合并歧义 */
const NAV_ITEM_ACTIVE = 'bg-[var(--color-brand-50)] text-[var(--color-brand-600)] font-bold'
const NAV_ITEM_IDLE = 'text-[var(--color-ink-soft)] font-medium hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)]'

const cartBadge = computed(() => {
  const total = count.value.totalQuantity
  if (!total) return ''
  return total > 99 ? '99+' : String(total)
})

const cartAriaLabel = computed(() =>
  cartBadge.value ? `${t('nav.cart')} (${count.value.totalQuantity})` : t('nav.cart'),
)

function submitSearch() {
  const q = keyword.value.trim()
  mobileMenuOpen.value = false
  router.push(localePath({ path: '/products', query: q ? { keyword: q } : {} }))
}

/**
 * 用户菜单。
 *
 * ⚠️ 「我的订单」「收藏夹」必须带上 ?tab=，不能三个都往 /profile 裸跳：
 *   个人中心默认停在「个人信息」，不带 tab 的话点「我的订单」也只会看到个人信息，
 *   用户会以为"点了没反应/跳错了"（这是真实收到过的反馈）。
 *   让 URL 带上目标标签，profile.vue 会据此直接打开对应面板。
 */
const userMenuItems = computed<DropdownMenuItem[]>(() => [
  { label: t('nav.profile'), icon: 'lucide:user', onSelect: () => router.push(localePath('/profile')) },
  { label: t('nav.orders'), icon: 'lucide:package', onSelect: () => router.push(localePath({ path: '/profile', query: { tab: 'orders' } })) },
  { label: t('nav.wishlist'), icon: 'lucide:heart', onSelect: () => router.push(localePath({ path: '/profile', query: { tab: 'favorites' } })) },
  { label: t('nav.cart'), icon: 'lucide:shopping-cart', onSelect: () => router.push(localePath('/cart')) },
  { type: 'separator' },
  { label: t('nav.logout'), icon: 'lucide:log-out', onSelect: () => handleLogout() },
])

const localeMenuItems = computed<DropdownMenuItem[]>(() =>
  locales.value.map((loc: any) => ({
    label: loc.name,
    code: loc.code,
    _flag: loc.icon,
    checked: loc.code === locale.value,
    onSelect: () => changeLocale(loc.code),
  } as any)),
)

function changeLocale(code: string) {
  if (code === locale.value) return
  router.push(switchLocalePath(code))
}

async function handleLogout() {
  await logout()
  mobileMenuOpen.value = false
  await navigateTo(localePath('/login'))
}

/** 路由变化时收起移动端抽屉，同时把搜索框回填成当前关键词（搜索页刷新后保持一致） */
watch(() => route.fullPath, () => {
  mobileMenuOpen.value = false
  const q = route.query.keyword
  keyword.value = typeof q === 'string' ? q : ''
}, { immediate: true })

onMounted(() => {
  // /api/cart/count 需要 httpOnly cookie，只在客户端请求
  refreshCount()
})
</script>
