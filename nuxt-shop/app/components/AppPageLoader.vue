<!--
  app/components/AppPageLoader.vue

  全屏加载遮罩（2026-10-23 第五轮反馈）。

  为什么需要它：
    页面是 SSR 出来的静态 HTML，**水合完成前没有任何事件监听** —— 这段时间里所有按钮
    （图集缩略图、加购、切换语言…）都点不动，用户会以为"页面卡死了"。
    与其让用户对着一个"看起来能用、其实点不动"的页面，不如明确地盖一层"正在准备"。

  设计（ui-ux-pro-max 的 loading 指引 + 本站 token）：
    · 结构：品牌核心圆（山形标记）+ 两圈扩散涟漪 + 两条不同速度的轨道光点 + 扫光进度条 + 文案微光；
    · 动效全部只动 `transform` / `opacity`（合成层完成，不触发布局与重绘），
      6 个动画元素各自独立，CPU 占用极低；不用 canvas / 不跑 JS 循环 / 不做大面积 blur；
    · 节奏：核心 2.8s 呼吸浮沉、涟漪 2.4s 错峰、轨道 2.4s / 3.6s 反向、进度条 1.2s 扫光；
    · 无障碍：role="status" + aria-live，视觉隐藏的「加载中」文案；`prefers-reduced-motion`
      下所有动画关闭，只留静态标记（进度条改为轻微透明度呼吸）。

  时序：
    · SSR 首帧就渲染（CSS 随文档到达即开始动，不依赖 JS）；
    · 水合完成（onNuxtReady）→ 淡出 320ms → 从 DOM 移除；
    · 客户端路由切换：只在导航超过 220ms 时出现（快速跳转不闪屏）；
    · 兜底：即使 JS 崩了，CSS 也会在 14s 后自动淡出并放开点击（`page-loader-safety`）。
-->
<template>
  <div
    v-if="visible"
    class="page-loader"
    :class="{ 'page-loader--leaving': leaving }"
    role="status"
    aria-live="polite"
    aria-busy="true"
  >
    <!-- 背景光晕：静态渐变，不参与动画 -->
    <span class="page-loader__glow" aria-hidden="true"></span>

    <!-- 底部山脊 + 晨光横扫：户外语义的装饰层 -->
    <span class="page-loader__ridge" aria-hidden="true">
      <svg viewBox="0 0 1440 148" preserveAspectRatio="none" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M0 118 120 74l70 26 96-52 78 44 92-62 84 52 96-38 88 46 96-58 84 52 92-30 96 34 110-44 110 38" opacity="0.9" />
        <path d="M0 138 110 104l84 22 92-40 88 32 96-46 82 38 94-28 88 34 96-44 86 38 94-24 92 26 108-32 100 26" opacity="0.55" />
      </svg>
    </span>
    <span class="page-loader__aurora" aria-hidden="true"></span>

    <!-- 标记区 -->
    <div class="page-loader__mark" aria-hidden="true">
      <span class="page-loader__track page-loader__track--a"></span>
      <span class="page-loader__track page-loader__track--b"></span>

      <span class="page-loader__ring page-loader__ring--a"></span>
      <span class="page-loader__ring page-loader__ring--b"></span>

      <span class="page-loader__orbit page-loader__orbit--a">
        <span class="page-loader__dot"></span>
      </span>
      <span class="page-loader__orbit page-loader__orbit--b">
        <span class="page-loader__dot page-loader__dot--accent"></span>
      </span>

      <span class="page-loader__core">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 18h18" />
          <path d="M7 18 12 7l5 11" />
          <path d="M9.5 14h5" />
        </svg>
      </span>
    </div>

    <!-- 文案 -->
    <p v-if="storeName" class="page-loader__brand">{{ storeName }}</p>
    <p class="page-loader__hint">{{ t('common.pageLoading') }}</p>

    <!-- 扫光进度条 -->
    <span class="page-loader__bar" aria-hidden="true"><span class="page-loader__bar-fill"></span></span>

    <span class="sr-only">{{ t('common.loading') }}</span>
  </div>
</template>

<script setup lang="ts">
const { t } = useI18n()
const nuxtApp = useNuxtApp()
/** 品牌名来自全站共享的 store-info（SSR 已取好），为空就不显示这一行 */
const { storeName } = useStoreInfo()

/** 首帧即显示：SSR 输出里就带着遮罩，CSS 到达就开始动 */
const visible = ref(true)
/** 正在淡出：先过渡，再从 DOM 移除（避免过渡期间残留可点击层） */
const leaving = ref(false)

let removeTimer: ReturnType<typeof setTimeout> | null = null
let navShowTimer: ReturnType<typeof setTimeout> | null = null

function hide() {
  if (leaving.value) return
  leaving.value = true
  removeTimer = setTimeout(() => { visible.value = false }, 340)
}

function show() {
  if (removeTimer) { clearTimeout(removeTimer); removeTimer = null }
  leaving.value = false
  visible.value = true
}

onMounted(() => {
  /*
   * 首屏：等 Nuxt 完成水合再收。
   * 用 onNuxtReady 而不是 onMounted —— onMounted 只说明"这个组件挂上了"，
   * 此时页面里其它组件的事件监听还没装好，收起遮罩等于把用户交回一个点不动的页面。
   */
  onNuxtReady(() => {
    requestAnimationFrame(() => hide())
  })

  /*
   * 客户端路由切换：只在"导航超过 220ms"时出现。
   * 阈值是为了避免快速跳转时遮罩闪一下（loading 指引：near-instant 的等待不要给闪烁反馈）。
   */
  const offStart = nuxtApp.hook('page:loading:start', () => {
    navShowTimer = setTimeout(() => show(), 220)
  })
  const offEnd = nuxtApp.hook('page:loading:end', () => {
    if (navShowTimer) { clearTimeout(navShowTimer); navShowTimer = null }
    if (visible.value) hide()
  })

  onBeforeUnmount(() => {
    offStart()
    offEnd()
    if (navShowTimer) clearTimeout(navShowTimer)
    if (removeTimer) clearTimeout(removeTimer)
  })
})
</script>
