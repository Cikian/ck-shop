<template>
  <div class="min-h-screen bg-[var(--color-background)] pt-navbar">
    <!-- Page Header -->
    <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
      <div class="mb-8 sm:mb-10">
        <span class="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
          <UIcon name="lucide:user-circle" class="h-4 w-4" />
          {{ t('profile.title') }}
        </span>
        <h1 class="mt-3 text-3xl font-bold text-[var(--color-primary)] sm:text-4xl text-gradient-primary">
          {{ t('profile.title') }}
        </h1>
        <p class="mt-2 text-sm text-[var(--color-text-muted)]">
          {{ t('profile.subtitle') }}
        </p>
      </div>
    </div>

    <div class="mx-auto max-w-7xl px-4 pb-20 sm:px-6 lg:px-8">
      <!-- Not Logged In -->
      <template v-if="!isLoggedIn">
        <div class="mx-auto max-w-lg">
          <div class="glass rounded-[var(--radius-card)] border border-[var(--color-border)] p-8 sm:p-10 text-center">
            <div class="relative mx-auto">
              <div class="absolute inset-0 rounded-full bg-[var(--color-accent)]/10 blur-2xl animate-pulse"></div>
              <div class="relative flex h-24 w-24 items-center justify-center rounded-full bg-[var(--color-surface-secondary)] border border-[var(--color-border)]">
                <UIcon name="lucide:user" class="h-12 w-12 text-[var(--color-text-muted)]" />
              </div>
            </div>
            <h2 class="mt-8 text-2xl font-bold text-[var(--color-primary)]">
              {{ t('profile.notLoggedIn') }}
            </h2>
            <p class="mt-3 text-sm text-[var(--color-text-muted)]">
              {{ t('profile.pleaseLogin') }}
            </p>
            <div class="mt-8 flex flex-col gap-3 sm:flex-row sm:justify-center">
              <NuxtLinkLocale
                to="/login"
                class="inline-flex items-center justify-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-accent)] px-8 py-3.5 text-sm font-bold text-white transition-all duration-300 hover:bg-[var(--color-accent-700)] hover:shadow-xl hover:shadow-[var(--color-accent)]/30 hover:-translate-y-0.5 cursor-pointer btn-shine"
              >
                <UIcon name="lucide:log-in" class="h-4 w-4" />
                {{ t('profile.goLogin') }}
              </NuxtLinkLocale>
              <NuxtLinkLocale
                to="/register"
                class="inline-flex items-center justify-center gap-2 rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white px-8 py-3.5 text-sm font-bold text-[var(--color-text-secondary)] transition-all duration-300 hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] hover:shadow-md cursor-pointer"
              >
                <UIcon name="lucide:user-plus" class="h-4 w-4" />
                {{ t('profile.register') }}
              </NuxtLinkLocale>
            </div>
            <div class="mt-8 pt-8 border-t border-[var(--color-border-light)]">
              <p class="text-xs text-[var(--color-text-muted)]">
                By signing in, you agree to our Terms of Service and Privacy Policy
              </p>
            </div>
          </div>
        </div>
      </template>

      <!-- Logged In -->
      <template v-else>
        <div class="grid gap-8 lg:grid-cols-4">
          <!-- Sidebar / Profile Card -->
          <div class="lg:col-span-1">
            <div class="glass overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)] shadow-md">
              <!-- Profile Header -->
              <div class="relative bg-primary-gradient p-6 text-white">
                <!-- Decorative blobs -->
                <div class="pointer-events-none absolute inset-0 overflow-hidden">
                  <div class="absolute -top-10 -right-10 h-32 w-32 rounded-full bg-white/10 blur-2xl"></div>
                  <div class="absolute -bottom-10 -left-10 h-32 w-32 rounded-full bg-[var(--color-accent)]/20 blur-2xl"></div>
                </div>
                <div class="relative">
                  <div class="absolute right-0 top-0">
                    <button
                      class="flex h-9 w-9 items-center justify-center rounded-full bg-white/15 text-white transition-all duration-200 hover:bg-white/25 cursor-pointer"
                      aria-label="Edit profile"
                    >
                      <UIcon name="lucide:pencil" class="h-4 w-4" />
                    </button>
                  </div>
                  <div class="relative h-16 w-16 overflow-hidden rounded-full border-2 border-white/30 backdrop-blur-sm shadow-lg">
                    <img
                      v-if="userInfo?.avatar"
                      :src="userInfo.avatar"
                      :alt="userInfo?.realname || 'User avatar'"
                      class="h-full w-full object-cover"
                    />
                    <div v-else class="flex h-full w-full items-center justify-center bg-white/20 text-2xl font-bold">
                      {{ userInfo?.realname?.charAt(0)?.toUpperCase() || 'U' }}
                    </div>
                  </div>
                  <h3 class="mt-4 text-lg font-bold">{{ userInfo?.realname }}</h3>
                  <p class="text-sm text-white/70">@{{ userInfo?.username }}</p>
                  <!-- Member Badge -->
                  <div class="mt-3 inline-flex items-center gap-1.5 rounded-full bg-[var(--color-accent)]/20 px-3 py-1 text-xs font-semibold text-[var(--color-accent-300)] border border-[var(--color-accent)]/30">
                    <UIcon name="lucide:crown" class="h-3 w-3" />
                    Gold Member
                  </div>
                </div>
              </div>

              <!-- Profile Info -->
              <div class="space-y-1 p-6">
                <div class="flex items-center gap-3 py-2">
                  <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-[var(--color-surface-secondary)] text-[var(--color-text-muted)]">
                    <UIcon name="lucide:mail" class="h-4 w-4" />
                  </div>
                  <span class="text-sm text-[var(--color-text-secondary)] truncate">
                    {{ userInfo?.email || '-' }}
                  </span>
                </div>
                <div class="flex items-center gap-3 py-2">
                  <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-[var(--color-surface-secondary)] text-[var(--color-text-muted)]">
                    <UIcon name="lucide:phone" class="h-4 w-4" />
                  </div>
                  <span class="text-sm text-[var(--color-text-secondary)]">
                    {{ userInfo?.phone || '-' }}
                  </span>
                </div>
              </div>

              <!-- Nav Links -->
              <div class="border-t border-[var(--color-border-light)] p-4">
                <nav class="space-y-1">
                  <button
                    v-for="item in navItems"
                    :key="item.key"
                    class="flex w-full items-center gap-3 rounded-[var(--radius-button)] px-4 py-3 text-sm font-medium transition-all duration-200 cursor-pointer"
                    :class="activeTab === item.key
                      ? 'bg-[var(--color-accent)]/10 text-[var(--color-accent)] font-semibold'
                      : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-primary)]'"
                    @click="activeTab = item.key"
                  >
                    <div class="flex h-8 w-8 items-center justify-center rounded-lg" :class="activeTab === item.key ? 'bg-[var(--color-accent)]/20' : 'bg-[var(--color-surface-secondary)]'">
                      <UIcon :name="item.icon" class="h-4 w-4" />
                    </div>
                    {{ t(item.label) }}
                  </button>
                </nav>
              </div>

              <!-- Logout -->
              <div class="border-t border-[var(--color-border-light)] p-4">
                <button
                  @click="handleLogout"
                  class="flex w-full items-center gap-3 rounded-[var(--radius-button)] px-4 py-3 text-sm font-medium text-[var(--color-error)] transition-all duration-200 hover:bg-[var(--color-error)]/10 cursor-pointer"
                >
                  <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-[var(--color-error)]/10">
                    <UIcon name="lucide:log-out" class="h-4 w-4" />
                  </div>
                  {{ t('profile.logout') }}
                </button>
              </div>
            </div>
          </div>

          <!-- Main Content -->
          <div class="lg:col-span-3">
            <!-- Stats Cards -->
            <div class="mb-8 grid grid-cols-2 gap-4 sm:grid-cols-4">
              <div
                v-for="(stat, idx) in stats"
                :key="stat.label"
                class="glass rounded-[var(--radius-card)] border border-[var(--color-border)] p-5 transition-all duration-300 hover:-translate-y-1 hover:shadow-lg"
                :style="{ animationDelay: `${idx * 80}ms` }"
              >
                <div
                  class="flex h-11 w-11 items-center justify-center rounded-xl"
                  :style="{ backgroundColor: stat.bgColor + '20', color: stat.bgColor }"
                >
                  <UIcon :name="stat.icon" class="h-5 w-5" />
                </div>
                <div class="mt-3 text-2xl font-bold text-[var(--color-primary)]">
                  {{ stat.value }}
                </div>
                <div class="text-sm text-[var(--color-text-muted)]">
                  {{ t(stat.label) }}
                </div>
              </div>
            </div>

            <!-- Tab Content -->
            <div class="glass rounded-[var(--radius-card)] border border-[var(--color-border)] p-6 sm:p-8">
              <!-- Orders Tab -->
              <template v-if="activeTab === 'orders'">
                <div class="mb-6 flex items-center justify-between">
                  <h2 class="text-xl font-bold text-[var(--color-primary)]">
                    {{ t('profile.myOrders') }}
                  </h2>
                  <button class="text-sm font-semibold text-[var(--color-accent)] transition-colors hover:text-[var(--color-accent-700)] cursor-pointer">
                    View All
                  </button>
                </div>
                <div class="space-y-4">
                  <div
                    v-for="order in mockOrders"
                    :key="order.id"
                    class="rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white/60 p-4 transition-all duration-300 hover:border-[var(--color-primary)]/30 hover:shadow-md sm:p-5 cursor-pointer"
                  >
                    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <div class="flex items-center gap-3">
                          <span class="text-sm font-bold text-[var(--color-primary)]">
                            #{{ order.id }}
                          </span>
                          <span
                            class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold"
                            :class="{
                              'bg-[var(--color-warning)]/10 text-[var(--color-warning)]': order.status === 'pending',
                              'bg-[var(--color-info)]/10 text-[var(--color-info)]': order.status === 'shipping',
                              'bg-[var(--color-success)]/10 text-[var(--color-success)]': order.status === 'completed',
                            }"
                          >
                            <span class="h-1.5 w-1.5 rounded-full" :class="{
                              'bg-[var(--color-warning)]': order.status === 'pending',
                              'bg-[var(--color-info)]': order.status === 'shipping',
                              'bg-[var(--color-success)]': order.status === 'completed',
                            }"></span>
                            {{ t(`profile.status_${order.status}`) }}
                          </span>
                        </div>
                        <p class="mt-1 text-sm text-[var(--color-text-muted)]">
                          {{ order.date }}
                        </p>
                      </div>
                      <div class="flex items-center gap-4">
                        <div class="text-right">
                          <div class="text-sm text-[var(--color-text-muted)]">
                            {{ order.items }} {{ t('profile.items') }}
                          </div>
                          <div class="text-lg font-bold text-[var(--color-primary)]">
                            ${{ order.total.toFixed(2) }}
                          </div>
                        </div>
                        <button class="flex h-10 w-10 items-center justify-center rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white text-[var(--color-text-secondary)] transition-all duration-200 hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] cursor-pointer">
                          <UIcon name="lucide:chevron-right" class="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </template>

              <!-- Wishlist Tab -->
              <template v-else-if="activeTab === 'wishlist'">
                <div class="mb-6 flex items-center justify-between">
                  <h2 class="text-xl font-bold text-[var(--color-primary)]">
                    {{ t('profile.myWishlist') }}
                  </h2>
                  <span class="text-sm text-[var(--color-text-muted)]">
                    {{ mockWishlist.length }} items
                  </span>
                </div>
                <div class="grid grid-cols-2 gap-4 sm:grid-cols-3">
                  <div
                    v-for="item in mockWishlist"
                    :key="item.id"
                    class="group relative overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)] bg-white transition-all duration-300 hover:border-[var(--color-primary)]/30 hover:shadow-lg hover:-translate-y-1 cursor-pointer"
                  >
                    <div class="relative overflow-hidden">
                      <img
                        :src="item.image"
                        :alt="item.name"
                        class="aspect-square w-full object-cover transition-transform duration-500 group-hover:scale-110"
                      />
                      <button
                        class="absolute right-3 top-3 flex h-8 w-8 items-center justify-center rounded-full bg-[var(--color-error)] text-white shadow-lg transition-all duration-200 hover:scale-110 cursor-pointer"
                      >
                        <UIcon name="lucide:heart" class="h-4 w-4 fill-current" />
                      </button>
                    </div>
                    <div class="p-4">
                      <h4 class="truncate text-sm font-semibold text-[var(--color-primary)]">
                        {{ item.name }}
                      </h4>
                      <p class="mt-1 text-base font-bold text-[var(--color-accent)]">
                        ${{ item.price.toFixed(2) }}
                      </p>
                    </div>
                  </div>
                </div>
              </template>

              <!-- Address Tab -->
              <template v-else-if="activeTab === 'addresses'">
                <div class="mb-6 flex items-center justify-between">
                  <h2 class="text-xl font-bold text-[var(--color-primary)]">
                    {{ t('profile.myAddresses') }}
                  </h2>
                  <button class="inline-flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-accent)] px-4 py-2.5 text-sm font-bold text-white transition-all duration-200 hover:bg-[var(--color-accent-700)] hover:shadow-lg hover:shadow-[var(--color-accent)]/20 cursor-pointer btn-shine">
                    <UIcon name="lucide:plus" class="h-4 w-4" />
                    {{ t('profile.addAddress') }}
                  </button>
                </div>
                <div class="grid gap-4 sm:grid-cols-2">
                  <div
                    v-for="addr in mockAddresses"
                    :key="addr.id"
                    class="rounded-[var(--radius-card)] border border-[var(--color-border)] bg-white/60 p-5 transition-all duration-300 hover:border-[var(--color-primary)]/30 hover:shadow-md"
                  >
                    <div class="mb-3 flex items-center justify-between">
                      <div class="flex items-center gap-2">
                        <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-[var(--color-surface-secondary)] text-[var(--color-text-secondary)]">
                          <UIcon name="lucide:map-pin" class="h-4 w-4" />
                        </div>
                        <span class="font-bold text-[var(--color-primary)]">
                          {{ addr.name }}
                        </span>
                      </div>
                      <span
                        v-if="addr.isDefault"
                        class="rounded-full bg-[var(--color-accent)]/10 px-2.5 py-0.5 text-xs font-semibold text-[var(--color-accent)]"
                      >
                        {{ t('profile.default') }}
                      </span>
                    </div>
                    <p class="text-sm text-[var(--color-text-secondary)] leading-relaxed">
                      {{ addr.address }}
                    </p>
                    <p class="mt-2 text-sm text-[var(--color-text-muted)]">
                      {{ addr.phone }}
                    </p>
                    <div class="mt-4 flex gap-2 pt-4 border-t border-[var(--color-border-light)]">
                      <button class="flex items-center gap-1.5 text-sm font-medium text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-primary)] cursor-pointer">
                        <UIcon name="lucide:pencil" class="h-3.5 w-3.5" />
                        {{ t('common.edit') }}
                      </button>
                      <button class="flex items-center gap-1.5 text-sm font-medium text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-error)] cursor-pointer">
                        <UIcon name="lucide:trash" class="h-3.5 w-3.5" />
                        {{ t('common.delete') }}
                      </button>
                    </div>
                  </div>
                </div>
              </template>

              <!-- Settings Tab -->
              <template v-else-if="activeTab === 'settings'">
                <h2 class="mb-6 text-xl font-bold text-[var(--color-primary)]">
                  {{ t('profile.accountSettings') }}
                </h2>
                <div class="space-y-8">
                  <div>
                    <h3 class="mb-4 text-sm font-bold uppercase tracking-wide text-[var(--color-text-secondary)]">
                      {{ t('profile.personalInfo') }}
                    </h3>
                    <div class="grid gap-4 sm:grid-cols-2">
                      <div>
                        <label class="mb-1.5 block text-sm font-medium text-[var(--color-text-secondary)]">
                          {{ t('profile.realname') }}
                        </label>
                        <input
                          type="text"
                          :value="userInfo?.realname"
                          class="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-white px-4 py-2.5 text-sm text-[var(--color-primary)] transition-all duration-200 focus:border-[var(--color-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]/10"
                        />
                      </div>
                      <div>
                        <label class="mb-1.5 block text-sm font-medium text-[var(--color-text-secondary)]">
                          {{ t('profile.username') }}
                        </label>
                        <input
                          type="text"
                          :value="userInfo?.username"
                          disabled
                          class="w-full cursor-not-allowed rounded-[var(--radius-input)] border border-[var(--color-border)] bg-[var(--color-surface-secondary)] px-4 py-2.5 text-sm text-[var(--color-text-muted)]"
                        />
                      </div>
                      <div>
                        <label class="mb-1.5 block text-sm font-medium text-[var(--color-text-secondary)]">
                          {{ t('profile.email') }}
                        </label>
                        <input
                          type="email"
                          :value="userInfo?.email"
                          class="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-white px-4 py-2.5 text-sm text-[var(--color-primary)] transition-all duration-200 focus:border-[var(--color-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]/10"
                        />
                      </div>
                      <div>
                        <label class="mb-1.5 block text-sm font-medium text-[var(--color-text-secondary)]">
                          {{ t('profile.phone') }}
                        </label>
                        <input
                          type="tel"
                          :value="userInfo?.phone"
                          class="w-full rounded-[var(--radius-input)] border border-[var(--color-border)] bg-white px-4 py-2.5 text-sm text-[var(--color-primary)] transition-all duration-200 focus:border-[var(--color-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]/10"
                        />
                      </div>
                    </div>
                  </div>

                  <div class="pt-4">
                    <button class="inline-flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-bold text-white transition-all duration-200 hover:bg-[var(--color-primary-700)] hover:shadow-lg hover:shadow-[var(--color-primary)]/20 cursor-pointer btn-shine">
                      <UIcon name="lucide:save" class="h-4 w-4" />
                      {{ t('profile.saveChanges') }}
                    </button>
                  </div>
                </div>
              </template>
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

const { t } = useI18n()
const { userInfo, isLoggedIn, logout } = useAuth()

const activeTab = ref('orders')

const navItems = [
  { key: 'orders', icon: 'lucide:shopping-bag', label: 'profile.myOrders' },
  { key: 'wishlist', icon: 'lucide:heart', label: 'profile.myWishlist' },
  { key: 'addresses', icon: 'lucide:map-pin', label: 'profile.myAddresses' },
  { key: 'settings', icon: 'lucide:settings', label: 'profile.accountSettings' },
]

const stats = [
  { label: 'profile.orders', value: 12, icon: 'lucide:shopping-bag', bgColor: 'var(--color-accent)' },
  { label: 'profile.wishlist', value: 28, icon: 'lucide:heart', bgColor: 'var(--color-error)' },
  { label: 'profile.coupons', value: 5, icon: 'lucide:ticket', bgColor: 'var(--color-warning)' },
  { label: 'profile.points', value: 1250, icon: 'lucide:gift', bgColor: 'var(--color-success)' },
]

const mockOrders = [
  { id: 'ORD-2024-001', status: 'completed', date: '2024-01-15', items: 3, total: 189.99 },
  { id: 'ORD-2024-002', status: 'shipping', date: '2024-01-20', items: 2, total: 89.50 },
  { id: 'ORD-2024-003', status: 'pending', date: '2024-01-25', items: 1, total: 59.00 },
]

const mockWishlist = [
  { id: 1, name: 'Premium Leather Backpack', price: 189, image: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=400&h=400&fit=crop' },
  { id: 3, name: 'Minimalist Wrist Watch', price: 149, image: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=400&h=400&fit=crop' },
  { id: 5, name: 'Ceramic Pour Over Coffee Set', price: 45, image: 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=400&h=400&fit=crop' },
]

const mockAddresses = [
  { id: 1, name: 'Home', address: '123 Main Street, Apt 4B, New York, NY 10001', phone: '+1 (555) 123-4567', isDefault: true },
  { id: 2, name: 'Office', address: '456 Business Ave, Suite 300, New York, NY 10002', phone: '+1 (555) 987-6543', isDefault: false },
]

async function handleLogout() {
  await logout()
  navigateTo('/login')
}

useSeoMeta({
  title: () => t('profile.title'),
})
</script>
