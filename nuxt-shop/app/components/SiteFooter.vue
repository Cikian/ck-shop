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

    <!--
      品牌区。
      三列「购物指南 / 客户服务 / 关于我们」共 12 个链接已移除：
      目标页一个都没有实现，渲染成 <span> 只是"看起来能点的假链接"，
      渲染成 <a href="/"> 更糟（点了刷新当前页）。等页面真正做出来再加。
    -->
    <div class="mx-auto max-w-7xl px-2 py-12 sm:px-3 lg:px-4">
      <div class="max-w-xl">
        <div class="flex items-center gap-2">
          <span class="brand-gradient flex h-8 w-8 items-center justify-center rounded-[10px] text-white">
            <UIcon name="lucide:zap" class="h-4 w-4" aria-hidden="true" />
          </span>
          <span class="text-[15px] font-black tracking-tight">{{ brandName }}</span>
        </div>
        <p class="mt-3 max-w-xs text-[13px] leading-relaxed text-white/55">
          {{ t('home.description') }}
        </p>
        <p class="mt-4 text-xs font-bold uppercase tracking-wider text-white/40">
          {{ t('footer.followUs') }}
        </p>
        <!--
          社交入口是纯占位：后端没有「店铺社交账号」字段，之前每个图标都指向 href="/"，
          点了只会刷新当前页 —— 那是假导航。这里改成非交互 span：
          保留 title 供鼠标悬停识别平台，aria-hidden 让读屏跳过（它不可操作，念出来只会误导）。
          等后端补了账号字段，再把这些 span 换成真实 <a href="...">。
        -->
        <div class="mt-2 flex items-center gap-2">
          <span
            v-for="social in socials"
            :key="social.label"
            class="flex h-8 w-8 items-center justify-center rounded-full bg-white/10 text-white/70"
            :title="social.label"
            aria-hidden="true"
          >
            <UIcon :name="social.icon" class="h-4 w-4" />
          </span>
        </div>
      </div>
    </div>

    <!-- 版权条 -->
    <div class="border-t border-white/10">
      <div class="mx-auto flex max-w-7xl flex-col gap-3 px-2 py-5 text-xs text-white/45 sm:flex-row sm:items-center sm:justify-between sm:px-3 lg:px-4">
        <p>© {{ year }} {{ brandName }}. {{ t('footer.allRightsReserved') }}</p>
        <!-- 法务页尚未实现：明确写"即将上线"，而不是把它渲染成可点击的链接 -->
        <p class="text-white/35">{{ t('footer.legalComingSoon') }}</p>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
/**
 * 页脚。
 *
 * 这里是**纯展示**，不再有任何"看起来能点"的元素：
 *   · 社交图标：后端没有「店铺社交账号」字段，所以只做平台标识（非交互 span，带 title）；
 *   · 三列链接（12 个）已删除；法务条款改成一行"即将上线"说明。
 * 等页面真正实现后再换成真实链接。
 */
const { t } = useI18n()

// 品牌名与 Header 同源（店铺公开信息），回退到 i18n 的站名
const { storeName } = useStoreInfo()
const brandName = computed(() => storeName.value || t('shop.brandName'))

const year = new Date().getFullYear()

const servicePromises = [
  { key: 'shop.serviceGenuine', icon: 'lucide:shield-check' },
  { key: 'shop.serviceFast', icon: 'lucide:truck' },
  { key: 'shop.serviceReturn', icon: 'lucide:rotate-ccw' },
  { key: 'shop.serviceTop', icon: 'lucide:award' },
]

/** 只做平台标识，不带 url —— 没有真实账号前不允许出现可点击的假入口 */
const socials = [
  { icon: 'lucide:message-circle', label: 'WeChat' },
  { icon: 'lucide:instagram', label: 'Instagram' },
  { icon: 'lucide:youtube', label: 'YouTube' },
]
</script>
