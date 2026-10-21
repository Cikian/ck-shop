<template>
  <div class="flex min-h-dvh flex-col">
    <!--
      认证类页面布局：只有导航，没有页脚。

      与默认布局的差别仅此一处：登录/注册/忘记密码/Google 回调是任务型页面，
      用户目标只有一个（完成认证），页脚的大量链接会把注意力从表单上拉走。
    -->
    <Header />
    <main class="flex-1">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
// Header 是自动导入的全局组件。
</script>
