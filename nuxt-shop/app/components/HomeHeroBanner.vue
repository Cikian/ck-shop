<template>
  <section class="relative overflow-hidden bg-hero-gradient pt-32 pb-16 sm:pt-40 sm:pb-24">
    <!-- Decorative Blobs -->
    <div class="pointer-events-none absolute inset-0 overflow-hidden">
      <div class="absolute -top-20 -left-20 h-72 w-72 rounded-full bg-[var(--color-accent)]/10 blur-3xl animate-blob"></div>
      <div class="absolute top-40 -right-20 h-96 w-96 rounded-full bg-[var(--color-info)]/10 blur-3xl animate-blob animation-delay-200"></div>
      <div class="absolute -bottom-20 left-1/3 h-80 w-80 rounded-full bg-purple-500/10 blur-3xl animate-blob animation-delay-400"></div>
    </div>

    <!-- Grid Pattern Overlay -->
    <div class="pointer-events-none absolute inset-0 bg-[linear-gradient(rgba(15,23,42,0.03)_1px,transparent_1px),linear-gradient(90deg,rgba(15,23,42,0.03)_1px,transparent_1px)] bg-[size:40px_40px] [mask-image:radial-gradient(ellipse_60%_60%_at_50%_40%,black,transparent)]"></div>

    <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
      <div class="grid items-center gap-12 lg:grid-cols-2 lg:gap-16">
        <!-- Content -->
        <div class="order-2 lg:order-1">
          <!-- Badge -->
          <div class="inline-flex items-center gap-2 rounded-full border border-[var(--color-accent)]/20 bg-[var(--color-accent)]/10 px-4 py-1.5 animate-fade-in-up">
            <span class="relative flex h-2 w-2">
              <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-[var(--color-accent)] opacity-75"></span>
              <span class="relative inline-flex h-2 w-2 rounded-full bg-[var(--color-accent)]"></span>
            </span>
            <span class="text-sm font-semibold text-[var(--color-accent)]">
              {{ t('home.newCollection') }}
            </span>
          </div>

          <!-- Heading -->
          <h1 class="mt-6 text-4xl font-bold leading-tight text-[var(--color-primary)] animate-fade-in-up animation-delay-100 sm:text-5xl lg:text-6xl">
            {{ t('hero.title') }}
            <span class="block text-gradient">{{ t('hero.highlight') }}</span>
          </h1>

          <!-- Description -->
          <p class="mt-6 text-base leading-relaxed text-[var(--color-text-secondary)] animate-fade-in-up animation-delay-200 sm:text-lg">
            {{ t('hero.subtitle') }}
          </p>

          <!-- CTA Buttons -->
          <div class="mt-8 flex flex-col gap-4 animate-fade-in-up animation-delay-300 sm:flex-row">
            <NuxtLinkLocale
              to="/products"
              class="group inline-flex h-14 items-center justify-center gap-2 rounded-2xl bg-[var(--color-primary)] px-8 text-base font-semibold text-white transition-all duration-300 hover:bg-[var(--color-primary-light)] hover:shadow-xl hover:shadow-[var(--color-primary)]/20 cursor-pointer btn-shine"
            >
              {{ t('hero.shopNow') }}
              <UIcon name="lucide:arrow-right" class="h-5 w-5 transition-transform duration-300 group-hover:translate-x-1" />
            </NuxtLinkLocale>
            <button
              class="group inline-flex h-14 items-center justify-center gap-2 rounded-2xl border border-[var(--color-border)] bg-white/60 px-8 text-base font-semibold text-[var(--color-primary)] backdrop-blur-sm transition-all duration-300 hover:border-[var(--color-primary)]/30 hover:bg-white cursor-pointer"
            >
              <UIcon name="lucide:play-circle" class="h-5 w-5 text-[var(--color-accent)]" />
              {{ t('hero.learnMore') }}
            </button>
          </div>

          <!-- Stats -->
          <div class="mt-12 grid grid-cols-3 gap-6 animate-fade-in-up animation-delay-400">
            <div>
              <div class="text-2xl font-bold text-[var(--color-primary)] sm:text-3xl">
                10K+
              </div>
              <div class="mt-1 text-sm text-[var(--color-text-muted)]">
                {{ t('hero.products') }}
              </div>
            </div>
            <div>
              <div class="text-2xl font-bold text-[var(--color-primary)] sm:text-3xl">
                50K+
              </div>
              <div class="mt-1 text-sm text-[var(--color-text-muted)]">
                {{ t('hero.customers') }}
              </div>
            </div>
            <div>
              <div class="text-2xl font-bold text-[var(--color-primary)] sm:text-3xl">
                4.9★
              </div>
              <div class="mt-1 text-sm text-[var(--color-text-muted)]">
                {{ t('hero.rating') }}
              </div>
            </div>
          </div>
        </div>

        <!-- Visual -->
        <div class="relative order-1 lg:order-2">
          <div class="relative animate-float">
            <!-- Main Product Card -->
            <div
              class="relative overflow-hidden rounded-[2rem] bg-gradient-to-br from-white to-[var(--color-surface-secondary)] p-4 shadow-2xl shadow-[var(--color-primary)]/10 transition-transform duration-300"
              :class="{ 'cursor-pointer hover:scale-[1.02]': currentSlide?.targetUrl }"
              @click="handleSlideClick"
            >
              <div class="relative aspect-square overflow-hidden rounded-[1.5rem]">
                <img
                  v-if="currentSlide"
                  :src="currentSlide.pic_url"
                  :alt="currentSlide.title || ''"
                  class="h-full w-full object-cover transition-all duration-700"
                />
                <!-- Overlay Gradient -->
                <div class="absolute inset-0 bg-gradient-to-t from-[var(--color-primary)]/40 via-transparent to-transparent"></div>

                <!-- Floating Badge -->
                <div class="absolute left-4 top-4 glass rounded-full px-4 py-2 text-sm font-semibold text-[var(--color-primary)] shadow-lg">
                  {{ t('home.bestseller') }}
                </div>
              </div>
            </div>

            <!-- Floating Card 1 -->
            <div class="absolute -left-4 top-1/4 glass hidden rounded-2xl p-3 shadow-xl animate-float-slow sm:block">
              <div class="flex items-center gap-3">
                <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-[var(--color-success)]/10 text-[var(--color-success)]">
                  <UIcon name="lucide:truck" class="h-5 w-5" />
                </div>
                <div>
                  <div class="text-sm font-semibold text-[var(--color-primary)]">Free Shipping</div>
                  <div class="text-xs text-[var(--color-text-muted)]">On orders over $99</div>
                </div>
              </div>
            </div>

            <!-- Floating Card 2 -->
            <div class="absolute -right-2 bottom-1/4 glass hidden rounded-2xl p-3 shadow-xl animate-float animation-delay-300 sm:block">
              <div class="flex items-center gap-3">
                <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-[var(--color-accent)]/10 text-[var(--color-accent)]">
                  <UIcon name="lucide:star" class="h-5 w-5 fill-current" />
                </div>
                <div>
                  <div class="text-sm font-semibold text-[var(--color-primary)]">4.9 Rating</div>
                  <div class="flex gap-0.5 text-xs text-[var(--color-warning)]">
                    ★★★★★
                    <span class="text-[var(--color-text-muted)]">(2.3k)</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Dots -->
          <div class="mt-8 flex items-center justify-center gap-2 lg:mt-10">
            <button
              v-for="(_, index) in slides"
              :key="index"
              @click="currentSlideIndex = index"
              class="group relative h-2 rounded-full transition-all duration-300 cursor-pointer"
              :class="currentSlideIndex === index
                ? 'w-8 bg-[var(--color-primary)]'
                : 'w-2 bg-[var(--color-border)] hover:bg-[var(--color-text-muted)]'"
              :aria-label="`Go to slide ${index + 1}`"
            ></button>
          </div>
        </div>
      </div>
    </div>

    <!-- Feature Highlights -->
    <div class="relative mx-auto mt-16 max-w-7xl px-4 sm:px-6 lg:mt-20 lg:px-8">
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div
          v-for="(feature, idx) in features"
          :key="feature.title"
          class="glass group rounded-2xl p-5 transition-all duration-500 hover:-translate-y-1 hover:shadow-xl cursor-pointer animate-fade-in-up"
          :style="{ animationDelay: `${(idx + 1) * 100}ms` }"
        >
          <div
            class="mb-4 flex h-12 w-12 items-center justify-center rounded-xl transition-all duration-300 group-hover:scale-110"
            :style="{ backgroundColor: feature.color + '15', color: feature.color }"
          >
            <UIcon :name="feature.icon" class="h-6 w-6" />
          </div>
          <h3 class="text-base font-semibold text-[var(--color-primary)]">
            {{ t(feature.titleKey) }}
          </h3>
          <p class="mt-1 text-sm text-[var(--color-text-muted)]">
            {{ t(feature.descKey) }}
          </p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { SlideItem } from '@@/server/api/home/slide.get'

const { t, locale } = useI18n()

const currentSlideIndex = ref(0)

// 通过响应式的 locale 作为 query 参数，语言切换时 useFetch 会自动重新请求
// 同时用 key 包含 locale，确保缓存也按语言隔离
const { data: slidesData } = await useFetch<SlideItem[]>('/api/home/slide', {
  query: computed(() => ({ lang: locale.value })),
  key: computed(() => `home-slide-${locale.value}`),
  watch: [locale],
})

const slides = computed(() => slidesData.value || [])

const currentSlide = computed(() => slides.value[currentSlideIndex.value] || null)

const features = [
  {
    icon: 'lucide:truck',
    titleKey: 'home.freeShipping',
    descKey: 'home.freeShippingDesc',
    color: 'var(--color-accent)',
  },
  {
    icon: 'lucide:shield-check',
    titleKey: 'home.securePayment',
    descKey: 'home.securePaymentDesc',
    color: 'var(--color-success)',
  },
  {
    icon: 'lucide:headphones',
    titleKey: 'home.support247',
    descKey: 'home.support247Desc',
    color: 'var(--color-info)',
  },
  {
    icon: 'lucide:rotate-ccw',
    titleKey: 'home.easyReturns',
    descKey: 'home.easyReturnsDesc',
    color: 'var(--color-error)',
  },
]

function handleSlideClick() {
  const url = currentSlide.value?.targetUrl
  if (url) {
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}

// Auto play
let autoplayInterval: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  autoplayInterval = setInterval(() => {
    if (slides.value.length > 0) {
      currentSlideIndex.value = (currentSlideIndex.value + 1) % slides.value.length
    }
  }, 5000)
})

onUnmounted(() => {
  if (autoplayInterval) {
    clearInterval(autoplayInterval)
  }
})
</script>
