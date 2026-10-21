// app/router.options.ts
//
// 路由级的运行时配置。Nuxt 会在构建时把这里的默认导出合并进 createRouter()。
//
// 为什么 scrollBehavior 必须放在**这个文件**而不是 nuxt.config：
//   nuxt.config 的 `router.options` 只接受可序列化的值（@nuxt/schema 里叫
//   RouterConfigSerializable：linkActiveClass / hashMode / scrollBehaviorType …）。
//   Nuxt 生成 .nuxt/router.options.mjs 时对每个值调用 genString()（内部 JSON.stringify），
//   函数会被序列化成 undefined/垃圾字符串 —— scrollBehavior 直接丢失，且可能让构建产物报错。
//   放在 app/router.options.ts 里则是真正的运行时函数，可以安全使用 window。
//
// 为什么需要它：
//   之前没有配置，Nuxt 默认行为叠加上 tailwind.css 的 `html { scroll-behavior: smooth }`：
//   从长列表点进详情页时，浏览器会先把列表"平滑滚回顶部"再渲染新页面，观感像卡了一拍。
//   现在显式接管三件事：
//     · 前进/后退：恢复浏览器保存的位置（savedPosition）；
//     · 带 hash：滚到锚点，顶部让出固定导航高度（--nav-total-height ≈ 116/128px）；
//     · 普通导航：立即回顶，不做平滑动画。
//   prefers-reduced-motion 下所有滚动一律 instant。

import type { RouterConfig } from '@nuxt/schema'

/** 用户是否开启了「减少动效」（只在浏览器端求值） */
function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export default <RouterConfig> {
  scrollBehavior(to, _from, savedPosition) {
    // 浏览器前进/后退：恢复原来的滚动位置（不覆盖，交给浏览器）
    if (savedPosition) return savedPosition

    // 锚点跳转：滚到目标元素，顶部留出固定导航的高度
    if (to.hash) {
      return {
        el: to.hash,
        top: 96,
        behavior: prefersReducedMotion() ? 'instant' : 'smooth',
      }
    }

    // 普通导航：立即回顶。这里不能用 smooth —— 长页面上会先"滚上去"再渲染下一页。
    return { top: 0, left: 0, behavior: 'instant' }
  },
}
