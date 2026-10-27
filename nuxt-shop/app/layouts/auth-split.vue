<template>
  <!--
    认证页「全屏分栏」布局：不渲染全局 Header。

    与 auth.vue 的区别：
      · auth.vue   保留导航，用于忘记密码 / Google 回调等「任务页」；
      · auth-split 只给登录、注册使用 —— 左侧品牌插画要占满整个视口高度，
        顶部再压一条固定导航会把插画的构图切掉，也让左右分栏不再是 1:1。

    页面自己负责品牌、返回入口与语言（见 app/components/auth/AuthAside.vue）。
  -->
  <main class="auth-split-page min-h-dvh bg-[var(--color-cream)]">
    <slot />
  </main>
</template>

<style>
/*
 * 桌面端全屏分栏不需要整页滚动条：
 *   · 右侧表单自己 overflow-y-auto；
 *   · 整页保留 scrollbar-gutter 会在最右侧留一条 10px 的奶油色缝，
 *     白色右栏看起来像"没铺满"。
 * 用 :has() 只在本布局生效，离开登录/注册页后 html 恢复全局行为。
 */
@media (min-width: 1024px) {
  html:has(.auth-split-page) {
    scrollbar-gutter: auto;
    overflow: hidden;
  }
}
</style>
