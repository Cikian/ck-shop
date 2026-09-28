<template>
  <footer class="bg-[var(--color-primary)] text-white">
    <!-- Main Footer -->
    <div class="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
      <div class="grid grid-cols-2 gap-8 md:grid-cols-4 lg:gap-12">
        <!-- Brand Column -->
        <div class="col-span-2 md:col-span-1">
          <div class="flex items-center gap-2">
            <div
              class="flex h-10 w-10 items-center justify-center rounded-xl bg-white text-[var(--color-accent)]"
            >
              <span class="text-lg font-bold">N</span>
            </div>
            <span class="text-xl font-bold">Nuxt Shop</span>
          </div>
          <p class="mt-4 text-sm text-white/60">
            {{ t('home.description') }}
          </p>
          <!-- Social Icons -->
          <div class="mt-6 flex items-center gap-3">
            <a
              href="#"
              class="flex h-9 w-9 items-center justify-center rounded-full bg-white/10 text-white transition-all duration-200 hover:bg-[var(--color-accent)] cursor-pointer"
              aria-label="Twitter"
            >
              <UIcon name="lucide:twitter" class="h-4 w-4" />
            </a>
            <a
              href="#"
              class="flex h-9 w-9 items-center justify-center rounded-full bg-white/10 text-white transition-all duration-200 hover:bg-[var(--color-accent)] cursor-pointer"
              aria-label="Instagram"
            >
              <UIcon name="lucide:instagram" class="h-4 w-4" />
            </a>
            <a
              href="#"
              class="flex h-9 w-9 items-center justify-center rounded-full bg-white/10 text-white transition-all duration-200 hover:bg-[var(--color-accent)] cursor-pointer"
              aria-label="Facebook"
            >
              <UIcon name="lucide:facebook" class="h-4 w-4" />
            </a>
            <a
              href="#"
              class="flex h-9 w-9 items-center justify-center rounded-full bg-white/10 text-white transition-all duration-200 hover:bg-[var(--color-accent)] cursor-pointer"
              aria-label="YouTube"
            >
              <UIcon name="lucide:youtube" class="h-4 w-4" />
            </a>
          </div>
        </div>

        <!-- Shop -->
        <div>
          <h3 class="text-sm font-semibold uppercase tracking-wider">
            {{ t('footer.shop') }}
          </h3>
          <ul class="mt-4 space-y-3">
            <li>
              <NuxtLinkLocale
                to="/products"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.shoppingGuide') }}
              </NuxtLinkLocale>
            </li>
            <li>
              <a
                href="#"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.paymentMethod') }}
              </a>
            </li>
            <li>
              <a
                href="#"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.shippingDelivery') }}
              </a>
            </li>
            <li>
              <a
                href="#"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.returnPolicy') }}
              </a>
            </li>
          </ul>
        </div>

        <!-- Customer Service -->
        <div>
          <h3 class="text-sm font-semibold uppercase tracking-wider">
            {{ t('footer.customerService') }}
          </h3>
          <ul class="mt-4 space-y-3">
            <li>
              <a
                href="#"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.helpCenter') }}
              </a>
            </li>
            <li>
              <a
                href="#"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.contactUs') }}
              </a>
            </li>
            <li>
              <a
                href="#"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.orderTracking') }}
              </a>
            </li>
            <li>
              <a
                href="#"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.faq') }}
              </a>
            </li>
          </ul>
        </div>

        <!-- About -->
        <div>
          <h3 class="text-sm font-semibold uppercase tracking-wider">
            {{ t('footer.about') }}
          </h3>
          <ul class="mt-4 space-y-3">
            <li>
              <a
                href="#"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.aboutUs') }}
              </a>
            </li>
            <li>
              <a
                href="#"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.careers') }}
              </a>
            </li>
            <li>
              <a
                href="#"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.press') }}
              </a>
            </li>
            <li>
              <a
                href="#"
                class="text-sm text-white/60 transition-colors duration-200 hover:text-white cursor-pointer"
              >
                {{ t('footer.sustainability') }}
              </a>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <!-- Bottom Bar -->
    <div class="border-t border-white/10">
      <div
        class="mx-auto flex max-w-7xl flex-col items-center justify-between gap-4 px-4 py-6 sm:flex-row sm:px-6 lg:px-8"
      >
        <p class="text-sm text-white/50">
          © {{ new Date().getFullYear() }} Nuxt Shop. {{ t('footer.allRightsReserved') }}.
        </p>
        <div class="flex items-center gap-6">
          <a
            href="#"
            class="text-sm text-white/50 transition-colors duration-200 hover:text-white cursor-pointer"
          >
            {{ t('footer.privacyPolicy') }}
          </a>
          <a
            href="#"
            class="text-sm text-white/50 transition-colors duration-200 hover:text-white cursor-pointer"
          >
            {{ t('footer.termsOfService') }}
          </a>
          <a
            href="#"
            class="text-sm text-white/50 transition-colors duration-200 hover:text-white cursor-pointer"
          >
            {{ t('footer.cookiePolicy') }}
          </a>
        </div>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
const { t } = useI18n()
</script>
