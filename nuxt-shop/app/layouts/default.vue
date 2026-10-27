<template>
  <div class="flex min-h-dvh flex-col">
    <!--
      默认布局：全站导航 + 页面内容 + 页脚。

      为什么要收进布局：
        以前 app.vue 里只有 Header，SiteFooter 由 12 个页面各自手写一遍。
        结果是「新增一个页面」必须记得手动加页脚，漏了就悄悄少一块；
        而且页脚与页面内容之间没有任何结构关系，改全站页脚间距要动 12 个文件。

        login / register / forgot-password / auth/google/callback 四个页面
        历史上就没有页脚（避免表单页出现大片无关链接），它们使用 layouts/auth.vue。

      ⚠️ 页面根的固定契约（2026-10-28）：
        `main` 已经是 `flex-1`（页脚钉在视口底部），所以页面根**只能**写
        `min-h-[calc(100vh-var(--nav-total-height))]`，绝不能写 `min-h-screen`。
        写 `min-h-screen` 等于在"被导航下推了 116px"的位置再占满一整屏 ——
        实测视口 900 时文档高 1382、内容只到 591，多出来的一屏可以滚，滚下去只有奶油色空白
        （用户反馈："下面没有内容，为什么要留白这么多"）。
        内容比一屏短的页面（个人中心 / 购物车 / 订单 / 地址 / 结算）还要再往下传
        `flex-1`，让内容卡撑满可用高度、空态在卡里垂直居中。
    -->
    <Header />
    <main class="flex-1">
      <slot />
    </main>
    <SiteFooter />
  </div>
</template>

<script setup lang="ts">
// Header / SiteFooter 是自动导入的全局组件，无需显式 import。
</script>
