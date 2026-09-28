// app/plugins/init-auth.client.ts
//
// 文件名里的 .client.ts 表示这个插件只在浏览器端执行（服务端渲染阶段跳过），
// Nuxt 会在应用启动时自动执行这里的代码——用来把 localStorage 里存的
// 用户信息，恢复到 useAuth() 的响应式状态里（页面刷新后能立刻显示"已登录"状态，
// 不用等某个请求回来才知道）

import {useAuth} from "~~/composables/useAuth";

export default defineNuxtPlugin(() => {
  const { restoreUser } = useAuth()
  restoreUser()
})
