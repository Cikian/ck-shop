// composables/useLoginLink.ts
//
// 「去登录」链接的唯一实现（redirect 带上当前页面，登录后能回到原处）。
//
// ⚠️ 这里有两个变体，**不要合并**，差别在于"谁来补 locale 前缀"：
//
//   useLoginLink()      已套 localePath → 渲染出 /zh/login
//                       给那 6 个页面用（cart / checkout / checkout-pay /
//                       orders 列表 / 订单详情 / address），它们的写法历来如此。
//
//   useLoginLinkBare()  裸路径 /login，前缀交给 <NuxtLinkLocale> 补
//                       给 Header.vue 用。Header 里已经踩过一次坑：再套一层
//                       localePath 会拼出 /zh/zh/login，SSR 阶段 vue-router 解析失败
//                       （每条页面刷一条 VUE_ROUTER_R0004），只是 NuxtLinkLocale 渲染时
//                       恰好把重复前缀纠正了 —— 不能依赖这种"恰好"。
//                       另：已经在 /login 上时不带 redirect，避免自指参数。
//
// 历史：`computed(() => localePath({ path: '/login', query: { redirect: route.fullPath } }))`
// 这段在 7 个文件里各写了一份，收敛到这里。

/** 登录链接（已补 locale 前缀） */
export function useLoginLink() {
  const route = useRoute()
  const localePath = useLocalePath()
  return computed(() => localePath({ path: '/login', query: { redirect: route.fullPath } }))
}

/** 登录链接（裸路径，locale 前缀由 <NuxtLinkLocale> 补；已在登录页时不带 redirect） */
export function useLoginLinkBare() {
  const route = useRoute()
  return computed(() => {
    if (route.path.endsWith('/login')) return '/login'
    return { path: '/login', query: { redirect: route.fullPath } }
  })
}
