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
