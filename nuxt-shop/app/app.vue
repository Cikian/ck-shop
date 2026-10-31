<template>
  <UApp>
    <!--
      全局外壳（app.vue 只做"所有页面都必须有"的三件事）：
        1) UApp：Nuxt UI 的根容器，Toast/Modal/Tooltip 都挂在它下面；
        2) NuxtLoadingIndicator：SPA 导航期间的顶部进度条 —— 之前完全没有，
           首页/商品页在抽数据时内容区空白且没有任何反馈；
        3) NuxtRouteAnnouncer：路由变化时给读屏用户的播报（a11y）；
      页面结构（Header / main / Footer）交给 NuxtLayout：
        · layouts/default.vue：Header + main + SiteFooter
        · layouts/auth.vue：Header + main（登录/注册/找回密码/Google 回调）
      Header 以前写死在这里，导致每个页面（包括登录页）都必然带导航，且页脚要靠页面自己补。
    -->
    <!--
      全屏加载遮罩（SSR 首帧即渲染，水合完成后淡出）：
      水合完成前页面只是静态 HTML，按钮都没有事件监听 ——
      不给明确反馈的话，用户只会觉得"点了没反应"。
    -->
    <AppPageLoader />
    <NuxtLoadingIndicator
      :height="3"
      :throttle="200"
      color="var(--color-brand-500)"
    />
    <NuxtRouteAnnouncer />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
    <CookieConsent />
  </UApp>
</template>
