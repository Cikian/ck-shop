<template>
  <div class="min-h-screen bg-[var(--color-background)]">
    <!-- Hero Section -->
    <HomeHeroBanner />

    <!-- Featured Products Section -->
    <section class="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <div class="mb-12 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <span class="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
            <UIcon name="lucide:sparkles" class="h-4 w-4" />
            {{ t('home.featured') }}
          </span>
          <h2 class="mt-3 text-3xl font-bold text-[var(--color-primary)] sm:text-4xl">
            {{ t('home.bestsellers') }}
          </h2>
          <p class="mt-2 text-[var(--color-text-muted)]">
            {{ t('home.bestsellersDesc') }}
          </p>
        </div>
        <NuxtLinkLocale
          to="/products"
          class="group inline-flex items-center gap-2 text-sm font-semibold text-[var(--color-primary)] transition-colors duration-200 hover:text-[var(--color-accent)] cursor-pointer"
        >
          {{ t('common.viewMore') }}
          <UIcon name="lucide:arrow-right" class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-1" />
        </NuxtLinkLocale>
      </div>

      <div class="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3 xl:grid-cols-4">
        <ProductCard
          v-for="(product, idx) in featuredProducts"
          :key="product.id"
          :product="product"
          :style="{ animationDelay: `${idx * 80}ms` }"
          class="animate-fade-in-up opacity-0"
        />
      </div>
    </section>

    <!-- Categories Bento Grid -->
    <section class="bg-[var(--color-surface-secondary)] py-20">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mb-12 text-center">
          <span class="text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
            {{ t('home.categories') }}
          </span>
          <h2 class="mt-3 text-3xl font-bold text-[var(--color-primary)] sm:text-4xl">
            {{ t('home.shopByCategory') }}
          </h2>
          <p class="mx-auto mt-2 max-w-2xl text-[var(--color-text-muted)]">
            {{ t('home.shopByCategoryDesc') }}
          </p>
        </div>

        <div class="grid grid-cols-2 gap-4 sm:gap-6 md:grid-cols-4">
          <div
            v-for="(cat, idx) in categories"
            :key="cat.name"
            class="group relative overflow-hidden cursor-pointer animate-fade-in-up opacity-0 rounded-[var(--radius-card)] shadow-md transition-all duration-500 hover:-translate-y-2 hover:shadow-xl"
            :style="{ animationDelay: `${idx * 100}ms` }"
          >
            <div class="aspect-square sm:aspect-[4/5]">
              <img
                :src="cat.image"
                :alt="cat.name"
                class="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110"
              />
            </div>
            <div class="absolute inset-0 bg-gradient-to-t from-[var(--color-primary)]/85 via-[var(--color-primary)]/30 to-transparent transition-all duration-500 group-hover:from-[var(--color-primary)]/95 group-hover:via-[var(--color-accent)]/20"></div>
            <div class="absolute bottom-0 left-0 right-0 p-5 sm:p-6">
              <h3 class="text-lg font-bold text-white sm:text-xl">{{ cat.name }}</h3>
              <p class="mt-1 text-sm text-white/70">{{ cat.count }} {{ t('home.items') }}</p>
              <div class="mt-3 inline-flex items-center gap-1 text-sm font-medium text-white transition-all duration-300 group-hover:text-[var(--color-accent-300)]">
                {{ t('common.viewMore') }}
                <UIcon name="lucide:arrow-right" class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-1" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- New Arrivals Section -->
    <section class="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <div class="mb-12 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <span class="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
            <UIcon name="lucide:zap" class="h-4 w-4" />
            {{ t('home.newArrivals') }}
          </span>
          <h2 class="mt-3 text-3xl font-bold text-[var(--color-primary)] sm:text-4xl">
            {{ t('home.justDropped') }}
          </h2>
          <p class="mt-2 text-[var(--color-text-muted)]">
            {{ t('home.newArrivalsDesc') }}
          </p>
        </div>
      </div>

      <div class="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-3 xl:grid-cols-4">
        <ProductCard
          v-for="(product, idx) in newProducts"
          :key="product.id"
          :product="product"
          :style="{ animationDelay: `${idx * 80}ms` }"
          class="animate-fade-in-up opacity-0"
        />
      </div>
    </section>

    <!-- Promo Banner -->
    <section class="mx-auto max-w-7xl px-4 pb-20 sm:px-6 lg:px-8">
      <div class="relative overflow-hidden bg-primary-gradient p-8 sm:p-12 lg:p-16 rounded-[2rem]">
        <!-- Decorative elements -->
        <div class="pointer-events-none absolute inset-0">
          <div class="absolute -top-20 -right-20 h-64 w-64 rounded-full bg-white/10 blur-3xl animate-blob"></div>
          <div class="absolute -bottom-32 -left-10 h-80 w-80 rounded-full bg-[var(--color-accent)]/20 blur-3xl animate-blob animation-delay-300"></div>
          <div class="absolute top-1/2 left-1/3 h-40 w-40 rounded-full bg-[var(--color-info)]/10 blur-2xl"></div>
        </div>

        <!-- Grid pattern overlay -->
        <div class="pointer-events-none absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.03)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.03)_1px,transparent_1px)] bg-[size:40px_40px]"></div>

        <div class="relative grid items-center gap-8 lg:grid-cols-2 lg:gap-12">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-white/10 px-4 py-1.5 text-sm font-semibold text-white backdrop-blur-sm border border-white/10">
              <UIcon name="lucide:gift" class="h-4 w-4" />
              {{ t('home.limitedOffer') }}
            </span>
            <h2 class="mt-5 text-3xl font-bold text-white sm:text-4xl lg:text-5xl leading-tight">
              {{ t('home.summerSale') }}
              <span class="block text-gradient mt-2">Up to 50% OFF</span>
            </h2>
            <p class="mt-4 text-base text-white/70 sm:text-lg max-w-lg">
              {{ t('home.summerSaleDesc') }}
            </p>
            <div class="mt-8 flex flex-col gap-4 sm:flex-row">
              <NuxtLinkLocale
                to="/products"
                class="inline-flex h-12 items-center justify-center gap-2 rounded-2xl bg-white px-8 text-sm font-semibold text-[var(--color-primary)] transition-all duration-300 hover:bg-[var(--color-surface-secondary)] hover:shadow-xl cursor-pointer btn-shine"
              >
                {{ t('home.shopNow') }}
                <UIcon name="lucide:arrow-right" class="h-4 w-4" />
              </NuxtLinkLocale>
              <button class="inline-flex h-12 items-center justify-center gap-2 rounded-2xl border border-white/20 bg-white/5 px-8 text-sm font-semibold text-white transition-all duration-300 hover:bg-white/10 hover:border-white/30 cursor-pointer backdrop-blur-sm">
                <UIcon name="lucide:clock" class="h-4 w-4" />
                2 Days Left
              </button>
            </div>
          </div>

          <!-- Countdown / Visual -->
          <div class="relative hidden lg:block">
            <div class="absolute -right-8 top-1/2 -translate-y-1/2">
              <div class="relative">
                <div class="absolute inset-0 rounded-full bg-[var(--color-accent)]/20 blur-2xl animate-pulse"></div>
                <div class="relative grid grid-cols-3 gap-3">
                  <div class="glass-dark flex flex-col items-center justify-center rounded-2xl p-5 border border-white/10">
                    <span class="text-3xl font-bold text-white">12</span>
                    <span class="mt-1 text-xs text-white/60 uppercase">Hours</span>
                  </div>
                  <div class="glass-dark flex flex-col items-center justify-center rounded-2xl p-5 border border-white/10">
                    <span class="text-3xl font-bold text-white">45</span>
                    <span class="mt-1 text-xs text-white/60 uppercase">Mins</span>
                  </div>
                  <div class="glass-dark flex flex-col items-center justify-center rounded-2xl p-5 border border-white/10">
                    <span class="text-3xl font-bold text-white">30</span>
                    <span class="mt-1 text-xs text-white/60 uppercase">Secs</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Testimonials -->
    <section class="bg-[var(--color-surface-secondary)] py-20">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mb-12 text-center">
          <span class="text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
            {{ t('home.testimonials') }}
          </span>
          <h2 class="mt-3 text-3xl font-bold text-[var(--color-primary)] sm:text-4xl">
            {{ t('home.whatCustomersSay') }}
          </h2>
          <p class="mx-auto mt-2 max-w-2xl text-[var(--color-text-muted)]">
            Don't just take our word for it — hear from our happy customers
          </p>
        </div>

        <div class="grid gap-6 md:grid-cols-3">
          <div
            v-for="(review, idx) in testimonials"
            :key="idx"
            class="glass rounded-3xl p-6 transition-all duration-500 hover:-translate-y-2 hover:shadow-xl animate-fade-in-up opacity-0 sm:p-8 border border-white/50"
            :style="{ animationDelay: `${idx * 120}ms` }"
          >
            <div class="flex gap-1 text-[var(--color-warning)]">
              <UIcon v-for="i in 5" :key="i" name="lucide:star" class="h-5 w-5 fill-current" />
            </div>
            <p class="mt-4 text-base leading-relaxed text-[var(--color-text-secondary)]">
              "{{ review.text }}"
            </p>
            <div class="mt-6 flex items-center gap-3">
              <div class="flex h-12 w-12 items-center justify-center rounded-full bg-gradient-to-br from-[var(--color-accent)]/20 to-[var(--color-accent)]/5 text-lg font-bold text-[var(--color-accent)] border border-[var(--color-accent)]/20">
                {{ review.avatar }}
              </div>
              <div>
                <div class="font-semibold text-[var(--color-primary)]">{{ review.name }}</div>
                <div class="flex items-center gap-1 text-sm text-[var(--color-text-muted)]">
                  <UIcon name="lucide:badge-check" class="h-3.5 w-3.5 text-[var(--color-success)]" />
                  {{ review.role }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Newsletter / CTA Section -->
    <section class="relative overflow-hidden bg-primary-gradient py-20 sm:py-24">
      <!-- Background decoration -->
      <div class="pointer-events-none absolute inset-0">
        <div class="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-[var(--color-accent)]/15 blur-3xl animate-blob"></div>
        <div class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-[var(--color-info)]/10 blur-3xl animate-blob animation-delay-300"></div>
      </div>

      <!-- Grid pattern -->
      <div class="pointer-events-none absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.03)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.03)_1px,transparent_1px)] bg-[size:40px_40px]"></div>

      <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center">
          <div class="mx-auto mb-6 flex h-14 w-14 items-center justify-center rounded-2xl bg-white/10 backdrop-blur-sm border border-white/10">
            <UIcon name="lucide:mail" class="h-7 w-7 text-white" />
          </div>
          <h2 class="text-3xl font-bold text-white sm:text-4xl">
            {{ t('footer.newsletter') }}
          </h2>
          <p class="mx-auto mt-4 max-w-lg text-base text-white/70">
            {{ t('footer.newsletterDesc') }}
          </p>
          <form class="mt-8 flex flex-col gap-3 sm:flex-row" @submit.prevent="handleSubscribe">
            <div class="relative flex-1">
              <span class="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-white/40">
                <UIcon name="lucide:mail" class="h-5 w-5" />
              </span>
              <input
                v-model="email"
                type="email"
                :placeholder="t('footer.emailPlaceholder')"
                class="w-full rounded-2xl border border-white/10 bg-white/10 py-3.5 pl-12 pr-4 text-sm text-white placeholder:text-white/40 backdrop-blur-sm transition-all duration-200 focus:border-[var(--color-accent)] focus:outline-none focus:ring-2 focus:ring-[var(--color-accent)]/20 focus:bg-white/15"
              />
            </div>
            <button
              type="submit"
              class="inline-flex h-14 items-center justify-center gap-2 rounded-2xl bg-[var(--color-accent)] px-8 text-sm font-semibold text-white transition-all duration-300 hover:bg-[var(--color-accent-light)] hover:shadow-xl hover:shadow-[var(--color-accent)]/30 cursor-pointer btn-shine"
            >
              {{ t('footer.subscribe') }}
              <UIcon name="lucide:arrow-right" class="h-4 w-4" />
            </button>
          </form>
          <div class="mt-6 flex items-center justify-center gap-6 text-xs text-white/40">
            <span class="flex items-center gap-1.5">
              <UIcon name="lucide:shield-check" class="h-3.5 w-3.5" />
              {{ t('home.noSpam') }}
            </span>
            <span class="flex items-center gap-1.5">
              <UIcon name="lucide:unlock" class="h-3.5 w-3.5" />
              Unsubscribe anytime
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
interface Product {
  id: number
  slug: string
  name: string
  description: string
  price: number
  originalPrice?: number
  image: string
  rating: number
  reviewCount: number
  isNew?: boolean
  isBestseller?: boolean
  discount?: number
  category: string
  stock: number
}

const { t, locale } = useI18n()
const email = ref('')

const { data: products } = await useFetch<Product[]>('/api/products', {
  query: { lang: locale.value },
})

const featuredProducts = computed(() => {
  return products.value?.filter(p => p.isBestseller).slice(0, 4) || []
})

const newProducts = computed(() => {
  return products.value?.filter(p => p.isNew).slice(0, 4) || []
})

const categories = [
  { name: 'Shoes', count: 128, image: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&h=700&fit=crop' },
  { name: 'Bags', count: 86, image: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&h=700&fit=crop' },
  { name: 'Accessories', count: 245, image: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&h=700&fit=crop' },
  { name: 'Electronics', count: 67, image: 'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=600&h=700&fit=crop' },
]

const testimonials = [
  {
    text: 'Absolutely love the quality! The packaging was beautiful and the product exceeded my expectations. Will definitely order again.',
    name: 'Sarah Johnson',
    role: 'Verified Buyer',
    avatar: 'SJ',
  },
  {
    text: 'Fast shipping and amazing customer service. They helped me track my order every step of the way. Highly recommend!',
    name: 'Michael Chen',
    role: 'Verified Buyer',
    avatar: 'MC',
  },
  {
    text: 'The attention to detail is incredible. You can tell they really care about their products and customers. 10/10 experience.',
    name: 'Emily Davis',
    role: 'Verified Buyer',
    avatar: 'ED',
  },
]

function handleSubscribe() {
  console.log('Subscribe email:', email.value)
  email.value = ''
}

useSeoMeta({
  title: () => t('home.title'),
  description: () => t('home.description'),
})
</script>
