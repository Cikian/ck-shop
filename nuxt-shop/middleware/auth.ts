// middleware/auth.ts
// 这是一个"路由守卫"，如果想强制某个页面必须登录才能访问，
// 在对应页面的 <script setup> 里加一行：
//
//   definePageMeta({ middleware: 'auth' })
//
// Nuxt 会在跳转到这个页面之前，先执行这个文件里的逻辑

export default defineNuxtRouteMiddleware((to) => {
  const { isLoggedIn } = useAuth()

  if (!isLoggedIn.value) {
    return navigateTo('/login')
  }
})
