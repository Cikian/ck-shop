<script setup lang="ts">
// 分类总览：只展示**本店可见**的分类。
//
// 后端已按店铺过滤好（全局共享节点 + 本店专属节点），前端不做也无法做店铺过滤——
// 店铺身份由本站服务端配置（STORE_CODE）通过 X-Store-Code 请求头带给后端，
// 浏览器不感知也改不了，因此 A 店的分类不可能出现在 B 店。
//
// 每个节点带的 productCount 是"含子分类的本店已上架商品数"（后端汇总，前端不再二次计算）。
const { t } = useI18n()

const { data: categories } = await useFetch<any[]>('/api/categories', {
  default: () => [],
})

useSeoMeta({
  title: () => t('categories.title'),
  description: () => t('categories.description'),
})
</script>

<template>
  <div class="min-h-screen bg-[var(--color-background)] pt-navbar">
    <div class="mx-auto max-w-7xl px-4 pb-16 sm:px-6 lg:px-8">
      <header class="mb-8 pt-8 sm:pt-10">
        <h1 class="text-3xl font-bold text-[var(--color-primary)] sm:text-4xl">
          {{ t('categories.title') }}
        </h1>
        <p class="mt-2 text-sm text-[var(--color-text-muted)]">
          {{ t('categories.description') }}
        </p>
      </header>

      <p
        v-if="!categories.length"
        class="rounded-xl border border-dashed border-[var(--color-border)] p-12 text-center text-sm text-[var(--color-text-muted)]"
      >
        {{ t('categories.empty') }}
      </p>

      <div v-else class="space-y-10">
        <section v-for="top in categories" :key="top.id">
          <!-- 一级分类：可点击进入该分类的商品列表 -->
          <div class="mb-4 flex flex-wrap items-baseline gap-3">
            <NuxtLink
              :to="`/categories/${top.id}`"
              class="text-xl font-semibold text-[var(--color-primary)] transition-colors hover:text-[var(--color-accent)]"
            >
              {{ top.name }}
            </NuxtLink>
            <span class="text-sm text-[var(--color-text-muted)]">
              {{ top.productCount || 0 }}
            </span>
          </div>

          <!-- 二级分类 -->
          <div v-if="top.children?.length" class="flex flex-wrap gap-2">
            <NuxtLink
              v-for="child in top.children"
              :key="child.id"
              :to="`/categories/${child.id}`"
              class="inline-flex items-center gap-1.5 rounded-full border border-[var(--color-border)] px-3 py-1.5 text-sm text-[var(--color-text)] transition-colors hover:border-[var(--color-accent)] hover:text-[var(--color-accent)]"
            >
              <span>{{ child.name }}</span>
              <span class="text-xs text-[var(--color-text-muted)]">{{ child.productCount || 0 }}</span>
            </NuxtLink>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>
