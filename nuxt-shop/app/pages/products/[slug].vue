<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <!-- ===================== 加载态 ===================== -->
    <div v-if="pending" class="mx-auto max-w-7xl px-2 py-10 sm:px-3 lg:px-4">
      <div class="grid gap-10 lg:grid-cols-2 lg:gap-12">
        <div class="space-y-4">
          <div class="ui-card aspect-square animate-pulse bg-[var(--color-surface-secondary)]"></div>
          <div class="grid grid-cols-5 gap-3">
            <div v-for="i in 5" :key="`thumb-skeleton-${i}`" class="aspect-square animate-pulse rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)]"></div>
          </div>
        </div>
        <div class="space-y-5">
          <div class="h-5 w-1/3 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
          <div class="h-9 w-3/4 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
          <div class="h-4 w-1/2 animate-pulse rounded bg-[var(--color-surface-secondary)]"></div>
          <div class="h-24 w-full animate-pulse rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)]"></div>
          <div class="h-12 w-2/3 animate-pulse rounded-full bg-[var(--color-surface-secondary)]"></div>
          <div class="h-12 w-full animate-pulse rounded-full bg-[var(--color-surface-secondary)]"></div>
        </div>
      </div>
    </div>

    <!-- ===================== 404 ===================== -->
    <div v-else-if="isNotFound" class="mx-auto max-w-7xl px-2 py-20 text-center sm:px-3 lg:px-4">
      <span class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-surface-secondary)]">
        <UIcon name="lucide:package-x" class="h-10 w-10 text-[var(--color-brand-500)]" aria-hidden="true" />
      </span>
      <h1 class="mt-6 text-2xl font-black text-[var(--color-ink)]">{{ t('product.notFound') }}</h1>
      <p class="mt-2 text-sm text-[var(--color-muted)]">{{ t('product.notFoundDesc') }}</p>
      <NuxtLinkLocale
        to="/products"
        class="ui-pill ui-pill-brand mt-6 h-11 px-6 text-sm"
      >
        <UIcon name="lucide:arrow-left" class="h-4 w-4" aria-hidden="true" />
        {{ t('product.backToProducts') }}
      </NuxtLinkLocale>
    </div>

    <!-- ===================== 其它错误 ===================== -->
    <div v-else-if="error || !product" class="mx-auto max-w-7xl px-2 py-20 text-center sm:px-3 lg:px-4">
      <span class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-error-50)]">
        <UIcon name="lucide:wifi-off" class="h-10 w-10 text-[var(--color-error)]" aria-hidden="true" />
      </span>
      <h1 class="mt-6 text-2xl font-black text-[var(--color-ink)]">{{ t('product.loadError') }}</h1>
      <p class="mt-2 text-sm text-[var(--color-muted)]">{{ t('product.loadErrorDesc') }}</p>
      <div class="mt-6 flex flex-wrap items-center justify-center gap-3">
        <button type="button" class="ui-pill ui-pill-brand h-11 px-6 text-sm" @click="refresh()">
          <UIcon name="lucide:rotate-ccw" class="h-4 w-4" aria-hidden="true" />
          {{ t('common.retry') }}
        </button>
        <NuxtLinkLocale to="/products" class="ui-pill ui-pill-ghost h-11 px-6 text-sm">
          {{ t('product.backToProducts') }}
        </NuxtLinkLocale>
      </div>
    </div>

    <!-- ===================== 商品详情 ===================== -->
    <div v-else class="mx-auto max-w-7xl px-2 sm:px-3 lg:px-4">
      <!-- 面包屑 -->
      <nav class="no-scrollbar flex items-center gap-2 overflow-x-auto py-5 text-[13px] text-[var(--color-muted)]" :aria-label="t('cart.breadcrumbLabel')">
        <NuxtLinkLocale to="/" class="flex shrink-0 items-center gap-1 transition-colors hover:text-[var(--color-brand-600)]">
          <UIcon name="lucide:home" class="h-3.5 w-3.5" aria-hidden="true" />
          {{ t('common.home') }}
        </NuxtLinkLocale>
        <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5 shrink-0 text-[var(--color-line)]" aria-hidden="true" />
        <NuxtLinkLocale to="/products" class="shrink-0 transition-colors hover:text-[var(--color-brand-600)]">
          {{ t('products.all') }}
        </NuxtLinkLocale>
        <template v-if="product.categoryName">
          <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5 shrink-0 text-[var(--color-line)]" aria-hidden="true" />
          <NuxtLinkLocale
            v-if="product.categoryId"
            :to="`/categories/${product.categoryId}`"
            class="shrink-0 transition-colors hover:text-[var(--color-brand-600)]"
          >
            {{ product.categoryName }}
          </NuxtLinkLocale>
          <span v-else class="shrink-0">{{ product.categoryName }}</span>
        </template>
        <UIcon name="lucide:chevron-right" class="h-3.5 w-3.5 shrink-0 text-[var(--color-line)]" aria-hidden="true" />
        <span class="line-clamp-1 font-medium text-[var(--color-ink-soft)]">{{ product.title }}</span>
      </nav>

      <!-- ===================== 上半部：左图右信息 ===================== -->
      <div class="grid gap-8 lg:grid-cols-2 lg:gap-12">
        <!-- 左：图集（组件见 components/product/Gallery.vue；当前图下标仍由本页持有，切规格时会重置） -->
        <ProductGallery
          v-model:active-index="activeImageIndex"
          :main-image="mainImage"
          :gallery="gallery"
          :title="product.title"
          :is-recommend="product.isRecommend === 'Y'"
          :badges="imageBadges"
          :favorited="favorited"
          :favorite-pending="favoritePending"
          @share="handleShare"
          @toggle-favorite="handleToggleFavorite"
        />
        <!-- 右：商品信息（保持原有全部功能） -->
        <div class="flex flex-col">
          <!-- 分类 + 商品编码 -->
          <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px]">
            <span v-if="product.categoryName" class="inline-flex items-center gap-1 font-bold text-[var(--color-brand-600)]">
              <UIcon name="lucide:bookmark" class="h-3.5 w-3.5" aria-hidden="true" />
              {{ product.categoryName }}
            </span>
            <span v-if="product.spuCode" class="text-[var(--color-muted)]">
              {{ t('product.spuCode') }}: {{ product.spuCode }}
            </span>
          </div>

          <h1 class="mt-2.5 text-2xl font-black leading-tight text-[var(--color-ink)] sm:text-3xl">
            {{ product.title }}
          </h1>
          <p v-if="product.subTitle" class="mt-2 text-sm text-[var(--color-muted)]">
            {{ product.subTitle }}
          </p>

          <!-- 价格块：超大现价 + 划线价 + 直降价签 -->
          <div class="price-block mt-5 px-5 py-4">
            <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <span class="price text-[38px] leading-none sm:text-[42px]">{{ priceText }}</span>
              <span v-if="product.unit" class="text-sm text-[var(--color-muted)]">/ {{ product.unit }}</span>
            </div>
            <div v-if="priceView.showOriginal || priceView.discountAmount !== null" class="mt-2.5 flex flex-wrap items-center gap-2">
              <span v-if="priceView.showOriginal" class="price-strike text-sm">
                {{ formatCompact(priceView.originalPrice) }}
              </span>
              <span v-if="priceView.discountAmount !== null" class="badge badge-hot">
                {{ t('shop.discountOff', { amount: formatCompact(priceView.discountAmount) }) }}
              </span>
            </div>
            <!--
              价格区间提示：**必须先占位再隐藏，不能用 v-if 移除**。
              选中具体规格后这句提示就不该再出现，但用 v-if 直接把它从 DOM 删掉会让价格块矮一行，
              它下面的所有内容（统计行、规格、按钮…）整体上移 —— 用户点规格时页面会"跳"一下。
              改用 visibility:hidden 保留占位：它不参与绘制、不进入无障碍树，但**仍然占高度**，
              于是价格块高度在"未选规格 ↔ 已选规格"之间恒定不变。
              外层还用 hasPriceRange 兜一道：不跨价格区间的商品（本来就不会出现这句提示）
              不预留这一行，避免白白多出一段空白。
            -->
            <p
              v-if="hasPriceRange"
              class="mt-2 text-xs text-[var(--color-muted)]"
              :class="{ invisible: !showRangeHint }"
            >
              {{ t('products.priceRangeHint') }}
            </p>
          </div>

          <!-- 统计行：销量 / 评分 / 收藏，各自为 0 时该项不渲染 -->
          <div v-if="hasAnyStat" class="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-[13px] text-[var(--color-muted)]">
            <span v-if="product.salesCount > 0" class="inline-flex items-center gap-1.5">
              <UIcon name="lucide:flame" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
              {{ t('shop.sold', { count: formatCompactCount(product.salesCount) }) }}
            </span>
            <span v-if="rating !== null" class="inline-flex items-center gap-1.5">
              <UIcon name="lucide:star" class="h-4 w-4 fill-current text-[var(--color-accent-500)]" aria-hidden="true" />
              {{ ratingText }}
            </span>
            <span v-if="displayFavoriteCount > 0" class="inline-flex items-center gap-1.5">
              <UIcon name="lucide:heart" class="h-4 w-4 text-[var(--color-brand-400)]" aria-hidden="true" />
              {{ t('shop.profileFavorites') }} {{ formatCompactCount(displayFavoriteCount) }}
            </span>
          </div>

          <!-- 库存 -->
          <div class="mt-4 flex flex-wrap items-center gap-2 text-[13px]">
            <span
              class="h-2.5 w-2.5 rounded-full"
              :class="displayStock > 0 ? 'bg-[var(--color-success)]' : 'bg-[var(--color-error)]'"
              aria-hidden="true"
            ></span>
            <span class="font-bold" :class="displayStock > 0 ? 'text-[var(--color-success-600)]' : 'text-[var(--color-error-600)]'">
              {{ displayStock > 0 ? t('products.stock.inStock') : t('products.stock.outOfStock') }}
            </span>
            <span v-if="displayStock > 0" class="text-[var(--color-muted)]">
              {{ t('products.stock.remaining', { count: displayStock }) }}
            </span>
          </div>

          <!-- 规格选择器（核心交互：不存在 / 不可售组合置灰） -->
          <div v-if="hasSpecs" class="mt-7">
            <h2 class="text-sm font-bold text-[var(--color-ink)]">{{ t('products.spec.title') }}</h2>
            <div class="mt-3">
              <ProductSpecPicker
                :specs="specs"
                :combinations="combinations"
                :selected="selected"
                @update:selected="onSelectionChange"
              />
            </div>

            <!-- 组合状态提示 -->
            <p
              v-if="unavailableNotice"
              class="mt-3 inline-flex items-center gap-2 text-sm"
              :class="unavailableNotice.error ? 'text-[var(--color-error-600)]' : 'text-[var(--color-muted)]'"
            >
              <UIcon :name="unavailableNotice.error ? 'lucide:ban' : 'lucide:info'" class="h-4 w-4" aria-hidden="true" />
              {{ unavailableNotice.text }}
            </p>
            <p v-else-if="combination" class="mt-3 inline-flex items-center gap-2 text-sm text-[var(--color-ink-soft)]">
              <UIcon name="lucide:package-check" class="h-4 w-4 text-[var(--color-success)]" aria-hidden="true" />
              {{ combination.combinationName || combination.combinationKey }}
            </p>
          </div>

          <!--
            操作区（两行两列，用户指定的布局）：
              第一行 = 数量输入框 + 立即购买
              第二行 = 收藏 + 加入购物车
            三种动作的可用性判断同源（同一个 canAddToCart）：登录态 + 规格是否选全 + 是否可售，
            避免出现"能加购却买不了"这类自相矛盾。
          -->
          <div class="mt-7">
            <!-- 第一行：数量输入框 + 购买按钮 -->
            <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
              <div class="flex h-12 shrink-0 items-center rounded-[var(--radius-button)] border border-[var(--color-line)] bg-white">
                <button
                  type="button"
                  class="flex h-12 w-12 cursor-pointer items-center justify-center rounded-l-[var(--radius-button)] text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-[var(--color-ink-soft)] motion-reduce:transition-none"
                  :disabled="quantity <= 1"
                  :aria-label="t('product.decreaseQuantity')"
                  @click="changeQuantity(-1)"
                >
                  <UIcon name="lucide:minus" class="h-4 w-4" aria-hidden="true" />
                </button>
                <span class="w-12 text-center text-base font-bold tabular text-[var(--color-ink)]">{{ quantity }}</span>
                <button
                  type="button"
                  class="flex h-12 w-12 cursor-pointer items-center justify-center rounded-r-[var(--radius-button)] text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-[var(--color-ink-soft)] motion-reduce:transition-none"
                  :disabled="quantity >= maxQuantity"
                  :aria-label="t('product.increaseQuantity')"
                  @click="changeQuantity(1)"
                >
                  <UIcon name="lucide:plus" class="h-4 w-4" aria-hidden="true" />
                </button>
              </div>

              <!--
                立即购买：把这一件（含已选规格与数量）直接写进结算快照跳到 /checkout，
                **不经过购物车**（cartItemId 为空串，下单后后端不会清任何购物车条目）。
              -->
              <button
                type="button"
                class="ui-pill ui-pill-accent h-12 flex-1 text-sm disabled:cursor-not-allowed disabled:opacity-45"
                :disabled="!canBuyNow || buying"
                :aria-busy="buying"
                @click="handleBuyNow"
              >
                <UIcon
                  :name="buying ? 'lucide:loader-circle' : 'lucide:zap'"
                  class="h-5 w-5"
                  :class="buying ? 'animate-spin' : ''"
                  aria-hidden="true"
                />
                {{ buying ? t('product.buying') : buyNowLabel }}
              </button>
            </div>

            <!--
              第二行：收藏按钮 + 加入购物车按钮
              用 grid 而不是 flex：flex 项的 min-width 默认是 auto（受内容宽度影响），
              两个按钮文案长度不同就会一宽一窄；grid 的 minmax(0,1fr) 保证严格等宽。
            -->
            <div class="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <!-- 收藏：真实写入 ck_shop_favorite 的关系，返回后按后端的最新状态渲染（不做乐观取反） -->
              <button
                type="button"
                class="ui-pill h-12 gap-2 text-sm"
                :class="favorited
                  ? 'border-2 border-[var(--color-brand-500)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]'
                  : 'ui-pill-ghost disabled:cursor-not-allowed disabled:opacity-45'"
                :disabled="favoritePending"
                :aria-pressed="favorited"
                :aria-busy="favoritePending"
                :aria-label="favorited ? t('product.removeFromWishlist') : t('product.addToWishlist')"
                @click="handleToggleFavorite"
              >
                <UIcon
                  :name="favoritePending ? 'lucide:loader-circle' : 'lucide:heart'"
                  class="h-5 w-5"
                  :class="[favoritePending ? 'animate-spin' : '', favorited && !favoritePending ? 'fill-current' : '']"
                  aria-hidden="true"
                />
                {{ favorited ? t('product.wishlisted') : t('product.addToWishlist') }}
              </button>

              <!-- 加入购物车：主体是 SKU，所以必须先命中一个可售组合（无规格商品例外） -->
              <button
                type="button"
                class="ui-pill ui-pill-brand h-12 text-sm disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:shadow-none"
                :disabled="!canAddToCart || adding"
                :aria-busy="adding"
                @click="handleAddToCart"
              >
                <UIcon
                  :name="adding ? 'lucide:loader-circle' : 'lucide:shopping-cart'"
                  class="h-5 w-5"
                  :class="adding ? 'animate-spin' : ''"
                  aria-hidden="true"
                />
                {{ adding ? t('product.adding') : addToCartLabel }}
              </button>
            </div>

            <p class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[var(--color-muted)]">
              <span class="inline-flex items-center gap-1">
                <UIcon name="lucide:check" class="h-3.5 w-3.5 text-[var(--color-success)]" aria-hidden="true" />
                {{ t('product.cartHint') }}
              </span>
              <NuxtLinkLocale
                to="/cart"
                class="inline-flex items-center gap-1 font-bold text-[var(--color-brand-600)] transition-colors hover:text-[var(--color-brand-700)]"
              >
                <UIcon name="lucide:shopping-cart" class="h-3.5 w-3.5" aria-hidden="true" />
                {{ t('product.viewCart') }}
              </NuxtLinkLocale>
            </p>

            <div v-if="buyable" class="mt-4 flex items-center justify-between rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)] px-4 py-3">
              <span class="text-sm text-[var(--color-muted)]">{{ t('product.totalItems', { count: quantity }) }}</span>
              <span class="text-lg font-black tabular text-[var(--color-brand-600)]">{{ totalPrice }}</span>
            </div>
          </div>

          <!-- 服务标签：只认真实 tag code（FAST/TOP）与 freeShipping 字段；
               七天无理由是本店对外的统一退货承诺（SiteFooter 也在打同一句），不是商品数据 -->
          <div class="mt-6 flex flex-wrap gap-2 border-t border-[var(--color-line-soft)] pt-5">
            <span v-if="hasFastTag" class="tag-service">
              <UIcon name="lucide:zap" class="h-3.5 w-3.5" aria-hidden="true" />
              {{ t('shop.serviceFast') }}
            </span>
            <span v-if="hasTopTag" class="tag-service">
              <UIcon name="lucide:award" class="h-3.5 w-3.5" aria-hidden="true" />
              {{ t('shop.serviceTop') }}
            </span>
            <span v-if="product.freeShipping" class="tag-service">
              <UIcon name="lucide:truck" class="h-3.5 w-3.5" aria-hidden="true" />
              {{ t('shop.freeShipping') }}
            </span>
            <span class="tag-service tag-service-neutral">
              <UIcon name="lucide:rotate-ccw" class="h-3.5 w-3.5" aria-hidden="true" />
              {{ t('shop.serviceReturn') }}
            </span>
          </div>
        </div>
      </div>

      <!--
           ===================== 通栏吸顶锚点导航 =====================
           top 用实测的 Header 高度（Header 是 fixed，导航是两行，比单行 token 高），
           SSR 首帧回退到 --nav-total-height（导航总高），这样桌面/移动端都不会被导航盖住。

           这里的负 margin 是为了"在容器内做通栏"：把容器的横向内边距抵消掉，
           再自己补一份，使吸顶条的背景铺满内容区宽度。
           ⚠️ 必须与容器内边距**成对**：容器是 px-2/sm:px-3/lg:px-4，
              这里就只能是 -mx-2/sm:-mx-3/lg:-mx-4 —— 负 margin 比容器内边距大
              会把元素捅出容器边界，直接撑出横向滚动条。 -->
      <nav
        ref="anchorBar"
        class="sticky z-30 -mx-2 bg-[var(--color-cream)] px-2 pb-3 pt-6 sm:-mx-3 sm:px-3 lg:-mx-4 lg:px-4"
        :style="{ top: stickyTop }"
      >
        <div class="no-scrollbar flex items-center gap-2 overflow-x-auto rounded-full bg-white px-2 py-1.5 shadow-[var(--shadow-card)]">
          <button
            v-for="item in anchorItems"
            :key="item.id"
            type="button"
            class="chip shrink-0"
            :class="activeAnchor === item.id ? 'chip-active' : ''"
            :aria-current="activeAnchor === item.id ? 'true' : undefined"
            @click="scrollToAnchor(item.id)"
          >
            {{ t(item.labelKey) }}
          </button>
        </div>
      </nav>

      <!-- ===================== 下半部：单列竖向堆叠 =====================
           用户明确要求：详情下方必须竖向滚动，绝不能并排多栏。
           因此这里恒为单列（没有任何 lg:grid-cols-*），
           模块之间用极浅分隔线 + 大间距区隔。 -->
      <div class="pb-16">
        <!-- 1. 商品详情 -->
        <section ref="detailSection" class="border-t border-[var(--color-line-soft)] py-10">
          <h2 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
            <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
            {{ t('shop.anchorDetail') }}
          </h2>
          <!-- description 是管理端录入的富文本（后端契约：富文本 HTML） -->
          <div
            v-if="product.description"
            class="mt-5 text-[15px] leading-relaxed text-[var(--color-ink-soft)] [&_a]:text-[var(--color-brand-600)] [&_h2]:mt-5 [&_h2]:text-base [&_h2]:font-bold [&_h3]:mt-5 [&_h3]:font-bold [&_img]:my-4 [&_img]:h-auto [&_img]:w-full [&_img]:rounded-[var(--radius-tile)] [&_li]:ml-5 [&_li]:list-disc [&_p]:mb-4"
            v-html="product.description"
          ></div>
          <p v-else class="mt-5 text-sm text-[var(--color-muted)]">{{ t('product.noDescription') }}</p>
        </section>

        <!-- 2. 规格参数：后端没有参数表，只用真实字段拼；没有一行数据则整块不渲染 -->
        <section v-if="specRows.length" ref="specSection" class="border-t border-[var(--color-line-soft)] py-10">
          <h2 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
            <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
            {{ t('shop.anchorSpec') }}
          </h2>
          <dl class="mt-5 divide-y divide-[var(--color-line-soft)] overflow-hidden rounded-[var(--radius-tile)] bg-[var(--color-surface-secondary)] px-5">
            <div v-for="row in specRows" :key="row.labelKey" class="flex flex-wrap gap-x-4 gap-y-1 py-3.5">
              <dt class="w-24 shrink-0 text-[13px] text-[var(--color-muted)]">{{ t(row.labelKey) }}</dt>
              <dd class="min-w-0 flex-1 text-[13px] font-medium text-[var(--color-ink)]">{{ row.value }}</dd>
            </div>
          </dl>
        </section>

        <!-- 3. 用户评价：后端没有评价接口，只展示真实汇总（评分 + 评价数）；无数据整块不渲染 -->
        <section v-if="hasReview" ref="reviewSection" class="border-t border-[var(--color-line-soft)] py-10">
          <h2 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
            <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
            {{ t('shop.anchorReview') }}
          </h2>
          <div class="mt-5 flex flex-wrap items-center gap-x-6 gap-y-4">
            <div v-if="rating !== null" class="flex items-baseline gap-1">
              <span class="text-4xl font-black leading-none tabular text-[var(--color-brand-600)]">{{ ratingText }}</span>
            </div>
            <div class="space-y-2">
              <div v-if="rating !== null" class="flex items-center gap-0.5" aria-hidden="true">
                <UIcon
                  v-for="star in 5"
                  :key="`star-${star}`"
                  name="lucide:star"
                  class="h-4 w-4"
                  :class="star <= ratingStars ? 'fill-current text-[var(--color-accent-500)]' : 'text-[var(--color-line)]'"
                />
              </div>
              <p v-if="product.reviewCount > 0" class="text-[13px] text-[var(--color-muted)]">
                {{ t('shop.reviewCount', { count: product.reviewCount }) }}
              </p>
            </div>
          </div>
        </section>

        <!-- 4. 为你推荐：真实同分类商品（排除当前商品），最多 4 张；无数据整块不渲染 -->
        <section v-if="related.length" ref="recommendSection" class="border-t border-[var(--color-line-soft)] py-10">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 class="flex items-center gap-2 text-lg font-black text-[var(--color-ink)]">
              <span class="h-5 w-1 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true"></span>
              {{ t('shop.anchorRecommend') }}
            </h2>
            <NuxtLinkLocale
              to="/products"
              class="inline-flex items-center gap-1 text-[13px] font-bold text-[var(--color-brand-600)] transition-colors hover:text-[var(--color-brand-700)]"
            >
              {{ t('common.viewMore') }}
              <UIcon name="lucide:arrow-right" class="h-3.5 w-3.5" aria-hidden="true" />
            </NuxtLinkLocale>
          </div>
          <div class="mt-5 grid grid-cols-2 gap-4 sm:gap-5 lg:grid-cols-4 lg:gap-6">
            <StoreProductCard
              v-for="item in related"
              :key="item.id"
              :product="item"
            />
          </div>
        </section>
      </div>
    </div>

    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
import type { SpecSelection } from '~~/types/shop-product'
import { formatCompactCount } from '~~/types/shop-product'
import { useStorePrice, useStoreProductList } from '~~/composables/useProducts'
import { useProductDetail } from '~~/composables/useProductDetail'
import { extractCartErrorMessage, useCart } from '~~/composables/useCart'
import { extractFavoriteErrorMessage } from '~~/composables/useFavorites'
import { writeCheckoutSnapshot } from '~~/composables/useCheckoutSnapshot'
import { useAuth } from '~~/composables/useAuth'

/**
 * 商品详情页。
 *
 * 布局契约（用户明确要求）：
 *   上半部 = 左图集 / 右信息 两栏；
 *   下半部 = **单列竖向堆叠**，四个模块依次为 商品详情 → 规格参数 → 用户评价 → 为你推荐，
 *           模块之间只有极浅分隔线，绝不并排成多栏。
 *
 * 购买闭环（2026-10-14 扩充）：
 *   加入购物车 / **立即购买** / **收藏** 三个动作都在右侧信息区，
 *   口径完全一致——未登录先引导登录、有规格必须选全、规格组合必须可售；
 *   三个动作都不做本地乐观更新，全部以服务端返回的状态为准：
 *     · 加购后购物车整体由 useCart 用后端返回值替换；
 *     · 立即购买把这一件写进结算快照，金额由结算页调服务端重算（前端不碰钱）；
 *     · 收藏由 useProductFavorite 回填 favorited 与收藏数。
 *
 * 数据契约：所有模块都遵守「零值不渲染」——
 *   评分 / 评价数 / 销量 / 收藏数为 0 时不渲染对应元素；
 *   规格参数只用后端真实字段（spuCode / categoryName）拼，没有数据的行不出现；
 *   评价模块只展示真实汇总（ratingAvg + reviewCount），后端没有评价接口就**不编造评价列表**。
 */
const route = useRoute()
const { t } = useI18n()
const toast = useToast()
const localePath = useLocalePath()
const { isLoggedIn } = useAuth()
const { addItem } = useCart()
const { format, formatCompact } = useStorePrice()

// 数据与派生状态全部来自 composables/useProductDetail.ts ——
// 该页面曾近 1060 行，先把数据层收拢，模板拆组件时才有清晰边界。
const {
  slug,
  product,
  pending,
  error,
  refresh,
  quantity,
  adding,
  buying,
  activeImageIndex,
  selected,
  favorited,
  favoriteCount,
  favoritePending,
  refreshFavorite,
  toggleFavorite,
  specs,
  combinations,
  hasSpecs,
  combination,
  selectionComplete,
  isSkuSellable,
  gallery,
  mainImage,
  priceView,
  priceText,
  showRangeHint,
  hasPriceRange,
  rating,
  ratingText,
  ratingStars,
  displayFavoriteCount,
  hasAnyStat,
  specRows,
  hasReview,
  displayStock,
  maxQuantity,
  unitPrice,
  totalPrice,
  buyable,
  unavailableNotice,
  canAddToCart,
  addToCartLabel,
  canBuyNow,
  buyNowLabel,
  imageBadges,
  tagCodes,
  hasFastTag,
  hasTopTag,
} = await useProductDetail()

// ===== 为你推荐：同分类真实商品，排除当前商品，最多 4 张 =====
// 这段留在页面而不是 composable：它要按「详情里的 categoryId」过滤，必须等详情拿到后再发起；
// 而 async composable 在自身 await 之后已脱离 setup 上下文，不能再调 useStoreProductList。
const relatedPageNo = ref(1)
const { data: relatedPage } = await useStoreProductList({
  pageNo: relatedPageNo,
  pageSize: 5,
  categoryId: computed(() => product.value?.categoryId ?? undefined),
})
const related = computed(() =>
  (relatedPage.value?.records ?? []).filter(item => item.id !== product.value?.id).slice(0, 4),
)

// ===== 吸顶锚点导航 =====
type AnchorId = 'detail' | 'spec' | 'review' | 'recommend'

const anchorBar = ref<HTMLElement | null>(null)
const detailSection = ref<HTMLElement | null>(null)
const specSection = ref<HTMLElement | null>(null)
const reviewSection = ref<HTMLElement | null>(null)
const recommendSection = ref<HTMLElement | null>(null)

const activeAnchor = ref<AnchorId>('detail')
/** 吸顶位置：SSR 先用设计 token（导航总高，移动端两行 128px / 桌面 116px），挂载后用实测的 Header 高度修正（Header 是 fixed） */
const stickyTop = ref('var(--nav-total-height)')

/**
 * 只给「真的渲染出来的模块」生成锚点：
 * 规格参数 / 用户评价 / 为你推荐 在没数据时整块不渲染，
 * 此时不应该出现点了没反应的死锚点。
 */
const anchorItems = computed<{ id: AnchorId; labelKey: string; el: HTMLElement | null }[]>(() => {
  const items: { id: AnchorId; labelKey: string; el: HTMLElement | null }[] = [
    { id: 'detail', labelKey: 'shop.anchorDetail', el: detailSection.value },
  ]
  if (specRows.value.length) items.push({ id: 'spec', labelKey: 'shop.anchorSpec', el: specSection.value })
  if (hasReview.value) items.push({ id: 'review', labelKey: 'shop.anchorReview', el: reviewSection.value })
  if (related.value.length) items.push({ id: 'recommend', labelKey: 'shop.anchorRecommend', el: recommendSection.value })
  return items
})

/** Header 是 fixed 的全局导航，高度随时可能变（移动端多一行搜索框），实测最稳 */
function headerHeight(): number {
  const header = document.querySelector('header')
  return header instanceof HTMLElement ? header.getBoundingClientRect().height : 0
}

/** 锚点滚动/高亮都要避开 fixed Header + 吸顶锚点栏本身 */
function scrollOffset(): number {
  return headerHeight() + (anchorBar.value?.getBoundingClientRect().height ?? 0)
}

let ticking = false
let rafId = 0

function syncStickyTop() {
  const height = headerHeight()
  if (height > 0) stickyTop.value = `${Math.round(height)}px`
}

/** 当前高亮：取最后一个「顶部已越过 Header + 锚点栏」的模块 */
function updateActiveAnchor() {
  const probe = scrollOffset() + 24
  let current: AnchorId = 'detail'
  for (const item of anchorItems.value) {
    if (!item.el) continue
    if (item.el.getBoundingClientRect().top - probe <= 0) current = item.id
  }
  activeAnchor.value = current
}

function onScroll() {
  if (ticking) return
  ticking = true
  rafId = requestAnimationFrame(() => {
    ticking = false
    updateActiveAnchor()
  })
}

function onResize() {
  syncStickyTop()
  updateActiveAnchor()
}

function scrollToAnchor(id: AnchorId) {
  if (!import.meta.client) return
  const item = anchorItems.value.find(entry => entry.id === id)
  if (!item?.el) return
  const top = item.el.getBoundingClientRect().top + window.scrollY - scrollOffset() - 16
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: Math.max(top, 0), behavior: reduced ? 'auto' : 'smooth' })
  // 立即高亮，不等平滑滚动结束（否则中间态会闪回上一个模块）
  activeAnchor.value = id
}

onMounted(() => {
  syncStickyTop()
  updateActiveAnchor()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onResize)
  // 收藏态只能客户端查（需要 access-token cookie；SSR 期间还没恢复登录态）
  void refreshFavorite()
})

onUnmounted(() => {
  // 只清理浏览器侧监听：SSR 不执行 unmount，但加个判断更保险
  if (!import.meta.client) return
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onResize)
  if (rafId) cancelAnimationFrame(rafId)
  ticking = false
})

const isNotFound = computed(() => (error.value as any)?.statusCode === 404)

/** 切换选择时重置数量与主图 */
function onSelectionChange(next: SpecSelection) {
  selected.value = next
  quantity.value = 1
  activeImageIndex.value = 0
}

function changeQuantity(delta: number) {
  quantity.value = Math.min(Math.max(1, quantity.value + delta), maxQuantity.value)
}

// 商品切换（同为详情路由参数变化）时重置本地状态
watch(
  () => product.value?.id,
  () => {
    selected.value = {}
    quantity.value = 1
    activeImageIndex.value = 0
    // 收藏态同样要跟着换：否则会显示"上一件商品已收藏"的心形
    void refreshFavorite()
  },
)

async function handleShare() {
  if (!import.meta.client) return
  try {
    await navigator.clipboard.writeText(window.location.href)
  } catch {
    // 剪贴板不可用时静默降级（与重构前一致的行为）
  }
}

/**
 * 加入购物车。
 *
 * 失败一律把后端的原话展示出来（例如"库存不足：最多可购买 3 件…"），
 * 不在这里替换成"操作失败"这种没有信息量的兜底文案——
 * 后端已经算清楚了原因，前端再包一层只会让用户猜。
 */
async function handleAddToCart() {
  if (!product.value || adding.value) return

  if (!isLoggedIn.value) {
    await navigateTo(localePath({ path: '/login', query: { redirect: route.fullPath } }))
    return
  }
  if (hasSpecs.value && !selectionComplete.value) {
    toast.add({ title: t('product.selectSpecFirst'), color: 'warning', icon: 'lucide:info' })
    return
  }

  adding.value = true
  try {
    await addItem({
      productId: product.value.id,
      // 无规格商品必须传空串，后端据此区分"无规格"与"未选规格"
      skuId: combination.value?.id ?? '',
      quantity: quantity.value,
      source: 'detail',
    })
    toast.add({ title: t('product.addedToCart'), color: 'success', icon: 'lucide:check' })
    quantity.value = 1
  } catch (error) {
    toast.add({
      title: t('cart.actionFailed'),
      description: extractCartErrorMessage(error, t('cart.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    adding.value = false
  }
}

/**
 * 立即购买。
 *
 * 与"加入购物车"的**唯一区别**：不写购物车，而是把"这一件 + 已选规格 + 数量"直接写进结算快照，
 * 跳到 /checkout 由服务端算钱。三个前置校验与加购同源（登录态 / 规格选全 / 可售），
 * 避免出现"能加购却买不了"或者反过来的自相矛盾。
 *
 * 快照写入失败（隐私模式禁用 sessionStorage）时必须显式提示：
 * 否则用户会被送到结算页看到"没有待结算商品"，完全不知道是自己浏览器的问题。
 */
async function handleBuyNow() {
  if (!product.value || buying.value) return

  if (!isLoggedIn.value) {
    await navigateTo(localePath({ path: '/login', query: { redirect: route.fullPath } }))
    return
  }
  if (hasSpecs.value && !selectionComplete.value) {
    toast.add({ title: t('product.selectSpecFirst'), color: 'warning', icon: 'lucide:info' })
    return
  }
  if (!buyable.value) return

  const written = writeCheckoutSnapshot([{
    // 立即购买不经过购物车：cartItemId 空串 -> 下单成功后后端不清任何购物车条目
    cartItemId: '',
    productId: product.value.id,
    // 无规格商品必须传空串（后端据此区分"无规格"与"未选规格"）
    skuId: combination.value?.id ?? '',
    quantity: quantity.value,
  }])
  if (!written) {
    toast.add({ title: t('product.buyNowFailed'), color: 'error', icon: 'lucide:circle-alert' })
    return
  }

  buying.value = true
  try {
    await navigateTo(localePath('/checkout'))
  } finally {
    buying.value = false
  }
}

/**
 * 收藏 / 取消收藏。
 *
 * 未登录时先引导登录（与加购同一套交互）：收藏是用户维度的数据，没有"游客收藏"这回事。
 * 状态一律取接口返回值，不做本地取反 —— 已收藏/未收藏的实时状态只有后端知道。
 */
async function handleToggleFavorite() {
  if (!product.value || favoritePending.value) return

  if (!isLoggedIn.value) {
    await navigateTo(localePath({ path: '/login', query: { redirect: route.fullPath } }))
    return
  }

  try {
    const status = await toggleFavorite()
    toast.add({
      title: status.favorited ? t('product.addedToWishlist') : t('product.removedFromWishlist'),
      color: 'success',
      icon: 'lucide:heart',
    })
  } catch (error) {
    toast.add({
      title: t('product.wishlistFailed'),
      description: extractFavoriteErrorMessage(error, t('cart.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  }
}

useSeoMeta({
  title: () => product.value?.seoTitle || product.value?.title || t('product.notFound'),
  description: () => product.value?.seoDescription || product.value?.subTitle || '',
  ogTitle: () => product.value?.seoTitle || product.value?.title || '',
  ogDescription: () => product.value?.seoDescription || product.value?.subTitle || '',
  ogImage: () => product.value?.coverUrl || product.value?.mainImage || '',
})
</script>
