<template>
  <NuxtLinkLocale
    :to="`/products/${product.slug}`"
    class="group relative flex flex-col overflow-hidden bg-white transition-all duration-500 hover:-translate-y-2 hover:shadow-2xl hover:shadow-[var(--color-primary)]/10 cursor-pointer rounded-[var(--radius-card)]"
  >
    <!-- Image Container -->
    <div class="relative overflow-hidden bg-[var(--color-surface-secondary)] rounded-t-[var(--radius-card)]">
      <div class="aspect-square">
        <img
          :src="product.image"
          :alt="product.name"
          class="h-full w-full object-cover transition-all duration-700 ease-out group-hover:scale-110"
        />
      </div>

      <!-- Overlay Gradient -->
      <div class="absolute inset-0 bg-gradient-to-t from-[var(--color-primary)]/0 via-transparent to-transparent opacity-0 transition-opacity duration-500 group-hover:from-[var(--color-primary)]/20 group-hover:opacity-100"></div>

      <!-- Badges -->
      <div class="absolute left-3 top-3 flex flex-wrap gap-2">
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

      <!-- Quick Actions (hover) -->
      <div class="absolute right-3 top-3 flex flex-col gap-2 opacity-0 transition-all duration-500 translate-x-2 group-hover:opacity-100 group-hover:translate-x-0">
        <button
          class="flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-[var(--color-text-secondary)] shadow-lg backdrop-blur-sm transition-all duration-300 hover:bg-[var(--color-error)] hover:text-white cursor-pointer"
          :class="{ 'bg-[var(--color-error)] text-white': isWishlisted }"
          @click.stop="toggleWishlist"
        >
          <UIcon name="lucide:heart" class="h-4 w-4" :class="{ 'fill-current': isWishlisted }" />
        </button>
        <button
          class="flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-[var(--color-text-secondary)] shadow-lg backdrop-blur-sm transition-all duration-300 hover:bg-[var(--color-primary)] hover:text-white cursor-pointer"
          @click.stop="handleShare"
        >
          <UIcon name="lucide:share-2" class="h-4 w-4" />
        </button>
      </div>

      <!-- Add to Cart Overlay -->
      <div class="absolute bottom-0 left-0 right-0 p-3 translate-y-full transition-transform duration-500 ease-out group-hover:translate-y-0">
        <button
          class="flex w-full items-center justify-center gap-2 bg-[var(--color-primary)] py-3 text-sm font-semibold text-white shadow-xl shadow-[var(--color-primary)]/20 transition-all duration-300 hover:bg-[var(--color-primary-700)] cursor-pointer btn-shine rounded-[var(--radius-button)]"
          @click.stop="handleAddToCart"
        >
          <UIcon name="lucide:shopping-bag" class="h-4 w-4" />
          {{ t('product.addToCart') }}
        </button>
      </div>
    </div>

    <!-- Product Info -->
    <div class="flex flex-1 flex-col p-4 sm:p-5">
      <!-- Category -->
      <span class="text-xs font-medium uppercase tracking-wider text-[var(--color-text-muted)] transition-colors duration-300 group-hover:text-[var(--color-accent)]">
        {{ product.category }}
      </span>

      <!-- Name -->
      <h3 class="mt-1 line-clamp-2 text-base font-semibold text-[var(--color-primary)] transition-colors duration-300 group-hover:text-[var(--color-accent)] sm:text-lg">
        {{ product.name }}
      </h3>

      <!-- Rating -->
      <div class="mt-2 flex items-center gap-2">
        <div class="flex items-center gap-0.5 text-[var(--color-warning)]">
          <UIcon
            v-for="i in 5"
            :key="i"
            name="lucide:star"
            class="h-4 w-4"
            :class="i <= Math.floor(product.rating) ? 'fill-current' : 'text-[var(--color-border)]'"
          />
        </div>
        <span class="text-xs text-[var(--color-text-muted)]">
          {{ product.rating }} ({{ product.reviewCount }})
        </span>
      </div>

      <!-- Price + Stock -->
      <div class="mt-auto pt-3 flex items-end justify-between border-t border-[var(--color-border-light)]">
        <div class="flex items-baseline gap-2">
          <span class="text-xl font-bold text-[var(--color-primary)]">
            ${{ product.price.toFixed(2) }}
          </span>
          <span
            v-if="product.originalPrice"
            class="text-sm text-[var(--color-text-muted)] line-through"
          >
            ${{ product.originalPrice.toFixed(2) }}
          </span>
        </div>
        <div class="flex items-center gap-1">
          <span
            class="h-2 w-2 rounded-full animate-pulse"
            :class="product.stock > 0 ? 'bg-[var(--color-success)]' : 'bg-[var(--color-error)]'"
          ></span>
          <span class="text-xs font-medium" :class="product.stock > 0 ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'">
            {{ product.stock > 0 ? t('product.inStock') : t('product.outOfStock') }}
          </span>
        </div>
      </div>
    </div>
  </NuxtLinkLocale>
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

interface Props {
  product: Product
}

const props = defineProps<Props>()
const { t } = useI18n()

const isWishlisted = ref(false)

function toggleWishlist() {
  isWishlisted.value = !isWishlisted.value
}

function handleAddToCart() {
  console.log('Add to cart:', props.product.name)
}

function handleShare() {
  console.log('Share:', props.product.name)
}
</script>
