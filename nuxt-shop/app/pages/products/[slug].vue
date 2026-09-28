<template>
  <div class="min-h-screen bg-[var(--color-background)] pt-navbar">
    <!-- Loading State -->
    <div v-if="pending" class="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
      <div class="grid gap-10 lg:grid-cols-2">
        <div class="space-y-4">
          <div class="aspect-square animate-pulse rounded-[var(--radius-card)] bg-[var(--color-surface-secondary)]"></div>
          <div class="grid grid-cols-4 gap-3">
            <div v-for="i in 4" :key="i" class="aspect-square animate-pulse rounded-[var(--radius-button)] bg-[var(--color-surface-secondary)]"></div>
          </div>
        </div>
        <div class="space-y-6">
          <div class="h-8 w-1/3 animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
          <div class="h-10 w-2/3 animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
          <div class="h-4 w-1/4 animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
          <div class="h-12 w-1/3 animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
          <div class="space-y-2">
            <div class="h-4 w-full animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
            <div class="h-4 w-full animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
            <div class="h-4 w-2/3 animate-pulse rounded-lg bg-[var(--color-surface-secondary)]"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- Product Not Found -->
    <div v-else-if="!product" class="mx-auto max-w-7xl px-4 py-20 text-center sm:px-6 lg:px-8">
      <div class="relative mx-auto">
        <div class="absolute inset-0 rounded-full bg-[var(--color-error)]/10 blur-2xl animate-pulse"></div>
        <div class="relative flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-surface-secondary)] glass border border-[var(--color-border)]">
          <UIcon name="lucide:package-x" class="h-10 w-10 text-[var(--color-error)]" />
        </div>
      </div>
      <h2 class="mt-8 text-2xl font-bold text-[var(--color-primary)]">
        {{ t('product.notFound') }}
      </h2>
      <p class="mt-2 text-sm text-[var(--color-text-muted)]">
        The product you're looking for doesn't exist or has been removed.
      </p>
      <NuxtLinkLocale
        to="/products"
        class="mt-6 inline-flex items-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-white transition-all duration-200 hover:bg-[var(--color-primary-700)] hover:shadow-lg hover:shadow-[var(--color-primary)]/20 cursor-pointer btn-shine"
      >
        <UIcon name="lucide:arrow-left" class="h-4 w-4" />
        {{ t('product.backToProducts') }}
      </NuxtLinkLocale>
    </div>

    <!-- Product Detail -->
    <div v-else class="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <!-- Breadcrumb -->
      <nav class="mb-8 flex items-center gap-2 text-sm">
        <NuxtLinkLocale
          to="/"
          class="flex items-center gap-1 text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-primary)] cursor-pointer"
        >
          <UIcon name="lucide:home" class="h-3.5 w-3.5" />
          {{ t('common.home') }}
        </NuxtLinkLocale>
        <span class="text-[var(--color-border)]">
          <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5" />
        </span>
        <NuxtLinkLocale
          to="/products"
          class="text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-primary)] cursor-pointer"
        >
          {{ t('products.title') }}
        </NuxtLinkLocale>
        <span class="text-[var(--color-border)]">
          <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5" />
        </span>
        <span class="font-medium text-[var(--color-text-secondary)] line-clamp-1">{{ product.name }}</span>
      </nav>

      <div class="grid gap-10 lg:gap-16 lg:grid-cols-2">
        <!-- Image Gallery -->
        <div class="space-y-4">
          <!-- Main Image -->
          <div class="relative overflow-hidden rounded-[var(--radius-card)] bg-[var(--color-surface-secondary)] glass border border-[var(--color-border)]">
            <img
              :key="mainImage"
              :src="mainImage"
              :alt="product.name"
              class="aspect-square w-full object-cover transition-all duration-500 animate-scale-in"
            />
            <!-- Badges -->
            <div class="absolute left-4 top-4 flex flex-wrap gap-2">
              <span
                v-if="product.isNew"
                class="inline-flex items-center gap-1 rounded-full bg-[var(--color-accent)] px-3 py-1 text-xs font-bold text-white shadow-lg shadow-[var(--color-accent)]/30"
              >
                <UIcon name="lucide:sparkles" class="h-3 w-3" />
                {{ t('product.new') }}
              </span>
              <span
                v-if="product.isBestseller"
                class="inline-flex items-center gap-1 rounded-full bg-[var(--color-primary)] px-3 py-1 text-xs font-bold text-white shadow-lg"
              >
                <UIcon name="lucide:zap" class="h-3 w-3" />
                {{ t('product.bestseller') }}
              </span>
              <span
                v-if="product.discount"
                class="inline-flex items-center gap-1 rounded-full bg-[var(--color-error)] px-3 py-1 text-xs font-bold text-white shadow-lg shadow-[var(--color-error)]/30"
              >
                <UIcon name="lucide:tag" class="h-3 w-3" />
                -{{ product.discount }}%
              </span>
            </div>
            <!-- Share & Wishlist -->
            <div class="absolute right-4 top-4 flex flex-col gap-2">
              <button
                class="flex h-10 w-10 items-center justify-center rounded-full bg-white/90 text-[var(--color-text-secondary)] shadow-lg backdrop-blur-sm transition-all duration-300 hover:bg-[var(--color-primary)] hover:text-white cursor-pointer"
                @click="handleShare"
              >
                <UIcon name="lucide:share-2" class="h-4 w-4" />
              </button>
              <button
                class="flex h-10 w-10 items-center justify-center rounded-full bg-white/90 shadow-lg backdrop-blur-sm transition-all duration-300 cursor-pointer"
                :class="isWishlisted ? 'bg-[var(--color-error)] text-white' : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-error)] hover:text-white'"
                @click="isWishlisted = !isWishlisted"
              >
                <UIcon name="lucide:heart" class="h-4 w-4" :class="{ 'fill-current': isWishlisted }" />
              </button>
            </div>
          </div>
          <!-- Thumbnails -->
          <div v-if="product.images && product.images.length > 1" class="grid grid-cols-4 gap-3">
            <button
              v-for="(img, idx) in product.images"
              :key="idx"
              @click="activeImageIndex = idx"
              class="overflow-hidden rounded-[var(--radius-button)] border-2 transition-all duration-300 cursor-pointer"
              :class="activeImageIndex === idx
                ? 'border-[var(--color-accent)] shadow-lg shadow-[var(--color-accent)]/20 scale-[1.02]'
                : 'border-transparent opacity-60 hover:opacity-100 hover:border-[var(--color-border)]'"
            >
              <img
                :src="img"
                :alt="`${product.name} ${idx + 1}`"
                class="aspect-square w-full object-cover transition-transform duration-500 hover:scale-110"
              />
            </button>
          </div>
        </div>

        <!-- Product Info -->
        <div class="flex flex-col">
          <!-- Category & SKU -->
          <div class="flex items-center gap-4 text-sm">
            <span class="inline-flex items-center gap-1 font-semibold text-[var(--color-accent)]">
              <UIcon name="lucide:bookmark" class="h-3.5 w-3.5" />
              {{ product.category }}
            </span>
            <span class="text-[var(--color-text-muted)]">SKU: #{{ String(product.id).padStart(6, '0') }}</span>
          </div>

          <!-- Name -->
          <h1 class="mt-3 text-3xl font-bold text-[var(--color-primary)] sm:text-4xl leading-tight">
            {{ product.name }}
          </h1>

          <!-- Rating -->
          <div class="mt-4 flex items-center gap-3">
            <div class="flex items-center gap-0.5 text-[var(--color-warning)]">
              <UIcon
                v-for="i in 5"
                :key="i"
                name="lucide:star"
                class="h-5 w-5"
                :class="i <= Math.floor(product.rating) ? 'fill-current' : 'text-[var(--color-border)]'"
              />
            </div>
            <span class="text-sm font-semibold text-[var(--color-text-secondary)]">
              {{ product.rating }}
            </span>
            <span class="text-sm text-[var(--color-text-muted)]">
              ({{ product.reviewCount }} {{ t('product.reviews') }})
            </span>
            <button class="text-sm font-medium text-[var(--color-accent)] transition-colors hover:text-[var(--color-accent-700)] cursor-pointer">
              Write a review
            </button>
          </div>

          <!-- Price -->
          <div class="mt-6 flex items-baseline gap-3">
            <span class="text-4xl font-bold text-[var(--color-primary)]">
              ${{ product.price.toFixed(2) }}
            </span>
            <span
              v-if="product.originalPrice"
              class="text-lg text-[var(--color-text-muted)] line-through"
            >
              ${{ product.originalPrice.toFixed(2) }}
            </span>
            <span
              v-if="product.discount"
              class="inline-flex items-center gap-1 rounded-lg bg-[var(--color-error)]/10 px-2.5 py-1 text-sm font-bold text-[var(--color-error)]"
            >
              <UIcon name="lucide:trending-down" class="h-3.5 w-3.5" />
              {{ t('product.save') }} {{ product.discount }}%
            </span>
          </div>

          <!-- Stock Status -->
          <div class="mt-4 flex items-center gap-2">
            <span
              class="inline-flex h-2.5 w-2.5 rounded-full animate-pulse"
              :class="product.stock > 0 ? 'bg-[var(--color-success)]' : 'bg-[var(--color-error)]'"
            ></span>
            <span class="text-sm font-medium" :class="product.stock > 0 ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'">
              {{ product.stock > 0 ? t('product.inStock') : t('product.outOfStock') }}
            </span>
            <span v-if="product.stock > 0 && product.stock < 20" class="text-sm text-[var(--color-text-muted)]">
              ({{ product.stock }} {{ t('product.itemsLeft') }})
            </span>
          </div>

          <!-- Description -->
          <div class="mt-8">
            <h3 class="text-sm font-bold uppercase tracking-wide text-[var(--color-text-secondary)]">
              {{ t('product.description') }}
            </h3>
            <p class="mt-3 text-base leading-relaxed text-[var(--color-text-secondary)]">
              {{ product.description }}
            </p>
          </div>

          <!-- Quantity + Add to Cart -->
          <div class="mt-8">
            <div class="flex flex-col gap-4 sm:flex-row sm:items-center">
              <!-- Quantity Selector -->
              <div class="flex items-center rounded-[var(--radius-button)] border border-[var(--color-border)] bg-white shadow-sm">
                <button
                  @click="quantity = Math.max(1, quantity - 1)"
                  :disabled="quantity <= 1"
                  class="flex h-12 w-12 items-center justify-center text-[var(--color-text-secondary)] transition-all duration-200 hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-primary)] disabled:opacity-40 cursor-pointer first:rounded-l-[var(--radius-button)]"
                >
                  <UIcon name="lucide:minus" class="h-4 w-4" />
                </button>
                <span class="w-12 text-center text-base font-bold text-[var(--color-primary)]">
                  {{ quantity }}
                </span>
                <button
                  @click="quantity = Math.min(product.stock, quantity + 1)"
                  :disabled="quantity >= product.stock"
                  class="flex h-12 w-12 items-center justify-center text-[var(--color-text-secondary)] transition-all duration-200 hover:bg-[var(--color-surface-secondary)] hover:text-[var(--color-primary)] disabled:opacity-40 cursor-pointer last:rounded-r-[var(--radius-button)]"
                >
                  <UIcon name="lucide:plus" class="h-4 w-4" />
                </button>
              </div>

              <!-- Add to Cart Button -->
              <button
                @click="handleAddToCart"
                :disabled="product.stock <= 0"
                class="flex flex-1 items-center justify-center gap-2 rounded-[var(--radius-button)] bg-[var(--color-accent)] px-8 py-3.5 text-sm font-bold text-white transition-all duration-300 hover:bg-[var(--color-accent-700)] hover:shadow-xl hover:shadow-[var(--color-accent)]/30 hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0 cursor-pointer btn-shine"
              >
                <UIcon name="lucide:shopping-bag" class="h-5 w-5" />
                {{ t('product.addToCart') }}
              </button>
            </div>

            <!-- Total Price -->
            <div class="mt-4 flex items-center justify-between rounded-[var(--radius-button)] bg-[var(--color-surface-secondary)] px-4 py-3">
              <span class="text-sm text-[var(--color-text-muted)]">Total ({{ quantity }} items)</span>
              <span class="text-xl font-bold text-[var(--color-primary)]">
                ${{ (product.price * quantity).toFixed(2) }}
              </span>
            </div>
          </div>

          <!-- Quick Info Cards -->
          <div class="mt-8 grid grid-cols-3 gap-3">
            <div class="glass rounded-[var(--radius-button)] border border-[var(--color-border)] p-4 text-center transition-all duration-300 hover:-translate-y-0.5 hover:shadow-md">
              <div class="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-[var(--color-success)]/10 text-[var(--color-success)]">
                <UIcon name="lucide:truck" class="h-5 w-5" />
              </div>
              <p class="mt-2 text-xs font-semibold text-[var(--color-primary)]">{{ t('product.freeShipping') }}</p>
              <p class="mt-0.5 text-[10px] text-[var(--color-text-muted)]">Orders over $50</p>
            </div>
            <div class="glass rounded-[var(--radius-button)] border border-[var(--color-border)] p-4 text-center transition-all duration-300 hover:-translate-y-0.5 hover:shadow-md">
              <div class="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-[var(--color-info)]/10 text-[var(--color-info)]">
                <UIcon name="lucide:shield-check" class="h-5 w-5" />
              </div>
              <p class="mt-2 text-xs font-semibold text-[var(--color-primary)]">{{ t('product.securePayment') }}</p>
              <p class="mt-0.5 text-[10px] text-[var(--color-text-muted)]">100% secure</p>
            </div>
            <div class="glass rounded-[var(--radius-button)] border border-[var(--color-border)] p-4 text-center transition-all duration-300 hover:-translate-y-0.5 hover:shadow-md">
              <div class="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-[var(--color-warning)]/10 text-[var(--color-warning)]">
                <UIcon name="lucide:rotate-ccw" class="h-5 w-5" />
              </div>
              <p class="mt-2 text-xs font-semibold text-[var(--color-primary)]">{{ t('product.easyReturns') }}</p>
              <p class="mt-0.5 text-[10px] text-[var(--color-text-muted)]">30-day policy</p>
            </div>
          </div>

          <!-- Specifications -->
          <div class="mt-10">
            <h3 class="text-sm font-bold uppercase tracking-wide text-[var(--color-text-secondary)]">
              {{ t('product.specifications') }}
            </h3>
            <div class="mt-4 overflow-hidden rounded-[var(--radius-card)] border border-[var(--color-border)] bg-white shadow-sm">
              <div
                v-for="(item, index) in specificationsList"
                :key="item.label"
                class="flex border-b border-[var(--color-border-light)] last:border-b-0"
                :class="index % 2 === 0 ? 'bg-white' : 'bg-[var(--color-surface-secondary)]/30'"
              >
                <div class="w-2/5 px-5 py-3.5 text-sm font-medium text-[var(--color-text-secondary)] sm:w-1/3 sm:px-6">
                  {{ item.label }}
                </div>
                <div class="flex-1 px-5 py-3.5 text-sm text-[var(--color-text-muted)] sm:px-6">
                  {{ item.value }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Related Products -->
      <section class="mt-20">
        <div class="mb-10 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <span class="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-widest text-[var(--color-accent)]">
              <UIcon name="lucide:sparkles" class="h-4 w-4" />
              Related Products
            </span>
            <h2 class="mt-3 text-2xl font-bold text-[var(--color-primary)] sm:text-3xl">
              You might also like
            </h2>
          </div>
          <NuxtLinkLocale
            to="/products"
            class="group inline-flex items-center gap-2 text-sm font-semibold text-[var(--color-primary)] transition-colors duration-200 hover:text-[var(--color-accent)] cursor-pointer"
          >
            {{ t('common.viewMore') }}
            <UIcon name="lucide:arrow-right" class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-1" />
          </NuxtLinkLocale>
        </div>

        <div class="grid grid-cols-2 gap-4 sm:gap-6 lg:grid-cols-4">
          <ProductCard
            v-for="(related, idx) in relatedProducts"
            :key="related.id"
            :product="related"
            :style="{ animationDelay: `${idx * 80}ms` }"
            class="animate-fade-in-up opacity-0"
          />
        </div>
      </section>
    </div>

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
  images: string[]
  rating: number
  reviewCount: number
  isNew?: boolean
  isBestseller?: boolean
  discount?: number
  category: string
  stock: number
  specifications?: Record<string, string>
}

const route = useRoute()
const { t, locale } = useI18n()

const quantity = ref(1)
const activeImageIndex = ref(0)
const isWishlisted = ref(false)

const { data: product, pending } = await useFetch<Product>(
  () => `/api/products/${route.params.slug}`,
  () => ({ query: { lang: locale.value } }),
)

const mainImage = computed(() => {
  if (!product.value?.images?.length) return product.value?.image || ''
  return product.value.images[activeImageIndex.value] || product.value.image
})

const specificationsList = computed(() => {
  if (!product.value?.specifications) {
    return [
      { label: 'Category', value: product.value?.category || '-' },
      { label: 'SKU', value: product.value ? `#${String(product.value.id).padStart(6, '0')}` : '-' },
      { label: 'Stock', value: product.value ? `${product.value.stock} items` : '-' },
      { label: 'Rating', value: product.value ? `${product.value.rating} / 5` : '-' },
    ]
  }
  return Object.entries(product.value.specifications).map(([label, value]) => ({ label, value }))
})

const relatedProducts = computed(() => {
  return [
    {
      id: 101,
      slug: 'related-1',
      name: 'Premium Wireless Headphones',
      description: 'High-quality wireless headphones with noise cancellation',
      price: 199.99,
      originalPrice: 249.99,
      image: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&h=400&fit=crop',
      rating: 4.8,
      reviewCount: 256,
      isBestseller: true,
      discount: 20,
      category: 'Electronics',
      stock: 45,
    },
    {
      id: 102,
      slug: 'related-2',
      name: 'Smart Watch Pro',
      description: 'Advanced smartwatch with health tracking features',
      price: 299.00,
      image: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=400&h=400&fit=crop',
      rating: 4.6,
      reviewCount: 189,
      isNew: true,
      category: 'Electronics',
      stock: 32,
    },
    {
      id: 103,
      slug: 'related-3',
      name: 'Leather Backpack',
      description: 'Stylish genuine leather backpack for everyday use',
      price: 149.00,
      image: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=400&h=400&fit=crop',
      rating: 4.9,
      reviewCount: 312,
      isBestseller: true,
      category: 'Bags',
      stock: 28,
    },
    {
      id: 104,
      slug: 'related-4',
      name: 'Minimalist Sunglasses',
      description: 'UV protection sunglasses with premium frame',
      price: 89.99,
      originalPrice: 129.99,
      image: 'https://images.unsplash.com/photo-1572635196237-14b3f281503f?w=400&h=400&fit=crop',
      rating: 4.5,
      reviewCount: 178,
      discount: 31,
      category: 'Accessories',
      stock: 60,
    },
  ]
})

function handleAddToCart() {
  console.log('Add to cart:', product.value?.name, 'x', quantity.value)
}

function handleShare() {
  console.log('Share:', product.value?.name)
}

useSeoMeta({
  title: () => product.value?.name || t('product.notFound'),
  description: () => product.value?.description || '',
})
</script>
