<template>
  <!--
    店铺端页脚。
    深墨底色 + 服务承诺条 + 品牌列 + 三个链接列 + 版权条。
    链接文案沿用既有 footer.* 语言包，不新增硬编码中文。
  -->
  <footer class="mt-16 bg-[#1F1A1C] text-white">
    <!--
      服务承诺条：浅色底 + 圆形品牌色图标 + 文字在下（对齐设计稿）。
      放在深色页脚的最上方，视觉上是一条独立的浅色带，而不是深色页脚里的一行小字。
    -->
    <div class="bg-white">
      <div class="mx-auto grid max-w-7xl grid-cols-2 gap-x-4 gap-y-6 px-2 py-7 sm:px-3 md:grid-cols-4 lg:px-4">
        <div
          v-for="promise in servicePromises"
          :key="promise.key"
          class="flex flex-col items-center gap-2 text-center"
        >
          <span class="flex h-11 w-11 items-center justify-center rounded-full bg-[var(--color-brand-50)]">
            <UIcon :name="promise.icon" class="h-5 w-5 text-[var(--color-brand-500)]" aria-hidden="true" />
          </span>
          <span class="text-sm font-bold text-[var(--color-ink)]">{{ t(promise.key) }}</span>
        </div>
      </div>
    </div>

    <!-- 链接区 -->
    <div class="mx-auto max-w-7xl px-2 py-12 sm:px-3 lg:px-4">
      <div class="grid grid-cols-2 gap-8 md:grid-cols-4">
        <!-- 品牌列 -->
        <div class="col-span-2 md:col-span-1">
          <div class="flex items-center gap-2">
            <span class="brand-gradient flex h-8 w-8 items-center justify-center rounded-[10px] text-white">
              <UIcon name="lucide:zap" class="h-4 w-4" aria-hidden="true" />
            </span>
            <span class="text-[15px] font-black tracking-tight">{{ t('shop.brandName') }}</span>
          </div>
          <p class="mt-3 max-w-xs text-[13px] leading-relaxed text-white/55">
            {{ t('home.description') }}
          </p>
          <p class="mt-4 text-xs font-bold uppercase tracking-wider text-white/40">
            {{ t('footer.followUs') }}
          </p>
          <div class="mt-2 flex items-center gap-2">
            <a
              v-for="social in socials"
              :key="social.label"
              :href="social.href"
              class="flex h-8 w-8 items-center justify-center rounded-full bg-white/10 text-white/70 transition-colors hover:bg-[var(--color-brand-500)] hover:text-white"
              :aria-label="social.label"
            >
              <UIcon :name="social.icon" class="h-4 w-4" aria-hidden="true" />
            </a>
          </div>
        </div>

        <!-- 三个链接列 -->
        <div v-for="column in linkColumns" :key="column.titleKey">
          <h2 class="text-[13px] font-bold text-white/90">{{ t(column.titleKey) }}</h2>
          <ul class="mt-4 space-y-2.5">
            <li v-for="link in column.links" :key="link">
              <span class="cursor-pointer text-[13px] text-white/55 transition-colors hover:text-white">
                {{ t(link) }}
              </span>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <!-- 版权条 -->
    <div class="border-t border-white/10">
      <div class="mx-auto flex max-w-7xl flex-col gap-3 px-2 py-5 text-xs text-white/45 sm:flex-row sm:items-center sm:justify-between sm:px-3 lg:px-4">
        <p>© {{ year }} {{ t('shop.brandName') }}. {{ t('footer.allRightsReserved') }}</p>
        <div class="flex flex-wrap items-center gap-4">
          <span class="cursor-pointer transition-colors hover:text-white/80">{{ t('footer.privacyPolicy') }}</span>
          <span class="cursor-pointer transition-colors hover:text-white/80">{{ t('footer.termsOfService') }}</span>
          <span class="cursor-pointer transition-colors hover:text-white/80">{{ t('footer.cookiePolicy') }}</span>
        </div>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
/**
 * 页脚。
 *
 * 社交链接是**占位**：后端没有「店铺社交账号」字段，所以统一指向站点根，
 * 不做成看起来能点、实际死链的假导航。等后端补字段后再替换
 * （见 docs/store-redesign/后端促销字段清单.md）。
 */
const { t } = useI18n()

const year = new Date().getFullYear()

const servicePromises = [
  { key: 'shop.serviceGenuine', icon: 'lucide:shield-check' },
  { key: 'shop.serviceFast', icon: 'lucide:truck' },
  { key: 'shop.serviceReturn', icon: 'lucide:rotate-ccw' },
  { key: 'shop.serviceTop', icon: 'lucide:award' },
]

const socials = [
  { icon: 'lucide:message-circle', label: 'WeChat', href: '/' },
  { icon: 'lucide:instagram', label: 'Instagram', href: '/' },
  { icon: 'lucide:youtube', label: 'YouTube', href: '/' },
]

const linkColumns = [
  {
    titleKey: 'footer.shop',
    links: ['footer.shoppingGuide', 'footer.paymentMethod', 'footer.shippingDelivery', 'footer.returnPolicy'],
  },
  {
    titleKey: 'footer.customerService',
    links: ['footer.helpCenter', 'footer.contactUs', 'footer.orderTracking', 'footer.faq'],
  },
  {
    titleKey: 'footer.about',
    links: ['footer.aboutUs', 'footer.careers', 'footer.press', 'footer.sustainability'],
  },
]
</script>
