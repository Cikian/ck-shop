<template>
  <header class="fixed top-0 left-0 right-0 z-50 px-4 pt-4 sm:px-6 lg:px-8">
    <nav class="glass-nav mx-auto max-w-7xl rounded-2xl transition-all duration-500 ease-out"
         :class="{ 'shadow-lg': scrolled }">
      <div class="flex items-center justify-between px-4 py-3 sm:px-6">
        <!-- Logo -->
        <NuxtLinkLocale to="/" class="flex items-center gap-2.5 group cursor-pointer">
          <div class="flex h-9 w-9 items-center justify-center rounded-xl bg-[var(--color-primary)] text-white transition-transform duration-300 group-hover:scale-110 group-hover:shadow-lg">
            <span class="text-base font-bold">N</span>
          </div>
          <span class="text-lg font-bold text-[var(--color-primary)] transition-colors duration-300 group-hover:text-[var(--color-accent)]">
            Nuxt Shop
          </span>
        </NuxtLinkLocale>

        <!-- Desktop Navigation -->
        <div class="hidden items-center gap-1 md:flex">
          <NuxtLinkLocale
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="relative px-4 py-2 text-sm font-medium text-[var(--color-text-secondary)] transition-all duration-200 hover:text-[var(--color-primary)] cursor-pointer group"
          >
            {{ t(item.label) }}
            <span class="absolute bottom-0 left-1/2 h-0.5 w-0 -translate-x-1/2 bg-[var(--color-accent)] transition-all duration-300 group-hover:w-1/2"></span>
          </NuxtLinkLocale>
        </div>

        <!-- Right Actions -->
        <div class="flex items-center gap-2 sm:gap-3">
          <!-- Search Button -->
          <button
            class="flex h-10 w-10 items-center justify-center rounded-xl text-[var(--color-text-secondary)] transition-all duration-200 hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-primary)] cursor-pointer"
            aria-label="Search"
          >
            <UIcon name="lucide:search" class="h-5 w-5" />
          </button>

          <!-- Cart Button -->
          <button
            class="relative flex h-10 w-10 items-center justify-center rounded-xl text-[var(--color-text-secondary)] transition-all duration-200 hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-primary)] cursor-pointer"
            aria-label="Cart"
          >
            <UIcon name="lucide:shopping-bag" class="h-5 w-5" />
            <span
              v-if="cartCount > 0"
              class="absolute -top-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full bg-[var(--color-accent)] text-xs font-bold text-white animate-scale-in"
            >
              {{ cartCount }}
            </span>
          </button>

          <!-- Language Switcher (Desktop) -->
          <div class="hidden sm:block">
            <UDropdownMenu :items="localeMenuItems" :popper="{ placement: 'bottom-end' }" :modal="false">
              <button
                class="group relative flex h-10 w-10 items-center justify-center rounded-xl text-[var(--color-text-secondary)] transition-all duration-300 hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-primary)] cursor-pointer"
                aria-label="Switch language"
              >
                <UIcon name="lucide:globe" class="h-5 w-5 transition-all duration-300 group-hover:scale-110 group-hover:rotate-12" />
              </button>
              <template #item-leading="{ item }">
                <span class="text-lg leading-none">
                  {{ item._flag }}
                </span>
              </template>
              <template #item-label="{ item }">
                <span :class="item.checked ? 'font-semibold text-[var(--color-primary)]' : ''">
                  {{ item.label }}
                </span>
              </template>
              <template #item-trailing="{ item }">
                <UIcon v-if="item.checked" name="lucide:check" class="h-4 w-4 text-[var(--color-primary)]" />
              </template>
            </UDropdownMenu>
          </div>

          <!-- User Actions -->
          <template v-if="isLoggedIn">
            <div class="hidden sm:block">
              <UDropdownMenu :items="userMenuItems" :popper="{ placement: 'bottom-end' }" :modal="false">
                <button class="group flex items-center gap-1.5 cursor-pointer">
                  <div class="relative h-10 w-10 overflow-hidden rounded-xl transition-all duration-300 group-hover:shadow-lg group-hover:brightness-110">
                    <img
                      v-if="userInfo?.avatar"
                      :src="userInfo.avatar"
                      :alt="userInfo?.realname || 'User avatar'"
                      class="h-full w-full object-cover"
                    />
                    <div v-else class="flex h-full w-full items-center justify-center bg-[var(--color-surface-secondary)] text-[var(--color-primary)]">
                      <UIcon name="lucide:user" class="h-5 w-5" />
                    </div>
                  </div>
                  <UIcon name="lucide:chevron-down" class="h-4 w-4 text-[var(--color-text-muted)] transition-transform duration-300 group-hover:text-[var(--color-primary)]" />
                </button>
              </UDropdownMenu>
            </div>
          </template>
          <template v-else>
            <NuxtLinkLocale
              to="/login"
              class="hidden h-10 items-center gap-2 rounded-xl bg-[var(--color-primary)] px-5 text-sm font-semibold text-white transition-all duration-300 hover:bg-[var(--color-primary-light)] hover:shadow-lg hover:shadow-[var(--color-primary)]/20 sm:inline-flex cursor-pointer btn-shine"
            >
              <UIcon name="lucide:log-in" class="h-4 w-4" />
              {{ t('nav.login') }}
            </NuxtLinkLocale>
          </template>

          <!-- Mobile Menu Toggle -->
          <button
            @click="mobileMenuOpen = !mobileMenuOpen"
            class="flex h-10 w-10 items-center justify-center rounded-xl text-[var(--color-text-secondary)] transition-all duration-200 hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-primary)] md:hidden cursor-pointer"
            aria-label="Toggle menu"
          >
            <UIcon v-if="!mobileMenuOpen" name="lucide:menu" class="h-5 w-5 transition-transform duration-300" />
            <UIcon v-else name="lucide:x" class="h-5 w-5 transition-transform duration-300" />
          </button>
        </div>
      </div>

      <!-- Mobile Menu -->
      <div
        class="overflow-hidden transition-all duration-300 ease-out md:hidden"
        :class="mobileMenuOpen ? 'max-h-96 opacity-100' : 'max-h-0 opacity-0'"
      >
        <div class="space-y-1 border-t border-[var(--color-border-light)] px-4 py-3">
          <NuxtLinkLocale
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="block rounded-xl px-4 py-2.5 text-sm font-medium text-[var(--color-text-secondary)] transition-all duration-200 hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-primary)] cursor-pointer"
            @click="mobileMenuOpen = false"
          >
            {{ t(item.label) }}
          </NuxtLinkLocale>

          <!-- Mobile Language -->
          <div class="pt-2">
            <p class="mb-2 px-1 text-xs font-semibold uppercase tracking-wider text-[var(--color-text-muted)]">
              {{ t('nav.language') || 'Language' }}
            </p>
            <div class="space-y-1">
              <button
                v-for="loc in locales"
                :key="loc.code"
                @click="changeLocale(loc.code); mobileMenuOpen = false"
                class="flex w-full items-center gap-3 rounded-xl px-4 py-2.5 text-sm font-medium transition-all duration-200 cursor-pointer"
                :class="loc.code === locale
                  ? 'bg-[var(--color-primary)]/10 text-[var(--color-primary)]'
                  : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-primary)]'"
              >
                <span class="text-lg">{{ loc.icon }}</span>
                <span class="flex-1 text-left">{{ loc.name }}</span>
                <UIcon v-if="loc.code === locale" name="lucide:check" class="h-4 w-4" />
              </button>
            </div>
          </div>

          <!-- Mobile Auth -->
          <div class="pt-2">
            <NuxtLinkLocale
              v-if="!isLoggedIn"
              to="/login"
              class="flex w-full items-center justify-center gap-2 rounded-xl bg-[var(--color-primary)] px-4 py-2.5 text-sm font-semibold text-white transition-all duration-200 hover:bg-[var(--color-primary-light)] cursor-pointer"
              @click="mobileMenuOpen = false"
            >
              <UIcon name="lucide:log-in" class="h-4 w-4" />
              {{ t('nav.login') }}
            </NuxtLinkLocale>
            <div v-else class="space-y-1">
              <NuxtLinkLocale
                to="/profile"
                class="flex items-center gap-3 rounded-xl px-4 py-2.5 text-sm font-medium text-[var(--color-text-secondary)] transition-all duration-200 hover:bg-[var(--color-surface-secondary)] cursor-pointer"
                @click="mobileMenuOpen = false"
              >
                <UIcon name="lucide:user" class="h-4 w-4" />
                {{ t('nav.profile') }}
              </NuxtLinkLocale>
              <button
                @click="handleLogout"
                class="flex w-full items-center gap-3 rounded-xl px-4 py-2.5 text-sm font-medium text-[var(--color-error)] transition-all duration-200 hover:bg-[var(--color-error)]/10 cursor-pointer"
              >
                <UIcon name="lucide:log-out" class="h-4 w-4" />
                {{ t('nav.logout') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </nav>
  </header>
</template>

<script setup lang="ts">
import { useAuth } from '~~/composables/useAuth'
import type { DropdownMenuItem } from '@nuxt/ui'

const { t, locale, locales } = useI18n()
const switchLocalePath = useSwitchLocalePath()
const localePath = useLocalePath()
const router = useRouter()
const { userInfo, isLoggedIn, logout } = useAuth()

const mobileMenuOpen = ref(false)
const scrolled = ref(false)
const cartCount = ref(3)

const navItems = [
  { to: '/products', label: 'nav.products' },
]

const userMenuItems = computed<DropdownMenuItem[]>(() => [
  {
    label: t('nav.profile'),
    icon: 'lucide:user',
    onClick: () => router.push(localePath('/profile')),
  },
  {
    label: t('nav.orders'),
    icon: 'lucide:package',
    onClick: () => router.push(localePath('/profile')),
  },
  {
    label: t('nav.wishlist'),
    icon: 'lucide:heart',
    onClick: () => router.push(localePath('/profile')),
  },
  {
    label: t('nav.settings'),
    icon: 'lucide:settings',
    onClick: () => router.push(localePath('/profile')),
  },
  {
    type: 'separator',
  },
  {
    label: t('nav.logout'),
    icon: 'lucide:log-out',
    onClick: () => handleLogout(),
  },
])

const localeMenuItems = computed<DropdownMenuItem[]>(() =>
  locales.value.map((loc: any) => ({
    label: loc.name,
    code: loc.code,
    _flag: loc.icon,
    checked: loc.code === locale.value,
    onSelect: () => changeLocale(loc.code),
  } as any))
)

function changeLocale(code: string) {
  if (code === locale.value) return
  router.push(switchLocalePath(code))
}

async function handleLogout() {
  await logout()
  mobileMenuOpen.value = false
  navigateTo('/login')
}

// Scroll effect
if (typeof window !== 'undefined') {
  const handleScroll = () => {
    scrolled.value = window.scrollY > 20
  }
  onMounted(() => {
    window.addEventListener('scroll', handleScroll, { passive: true })
  })
  onUnmounted(() => {
    window.removeEventListener('scroll', handleScroll)
  })
}
</script>
