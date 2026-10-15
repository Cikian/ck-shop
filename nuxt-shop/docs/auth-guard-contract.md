# 登录鉴权改造 —— 接口契约（冻结版）

> 本文件由 Lead 在开工前冻结。所有实现者必须严格遵守此契约，
> 不得擅自修改函数名/返回值形状，否则并行修改会互相破坏。

## 背景：为什么必须改

现状 `useAuth.isLoggedIn` 只是 `!!userInfo.value`，而 `userInfo` 来自 **localStorage 缓存**。
真正的凭证 `access-token` 在 **httpOnly cookie** 里，前端读不到。

后果：cookie 过期后，localStorage 里的 `user-info` 仍然存在
→ `isLoggedIn` 依然为 `true` → 页面以为已登录。
**"登录过期"这个需求点目前是无法被识别的。**

因此必须有一个"服务端说了算"的登录态来源。

---

## 1) 新增服务端接口

文件：`nuxt-shop/server/api/auth/session.get.ts`

```
GET /api/auth/session
```

行为：
- 读取 httpOnly cookie `access-token`
- **不存在** → 返回 `{ authenticated: false, userInfo: null }`（HTTP 200，不抛错）
- **存在** → 调用 Java 后端校验 token 是否仍然有效
  - 有效 → 返回 `{ authenticated: true, userInfo: <后端返回的用户信息> }`
  - 无效/过期（后端返回 401/403，或 token 解析失败）→
    **清除失效 cookie**（`deleteCookie(event,'access-token',{path:'/'})`）
    并返回 `{ authenticated: false, userInfo: null }`（HTTP 200）
  - 后端网络异常等**非鉴权类**错误 → 同样安全降级为 `{ authenticated:false, userInfo:null }`，
    **不要**把 500 抛给前端（否则后端抖动会把所有人踢下线）

返回类型（前端要 import，务必一致）：

```ts
export interface AuthSession {
  authenticated: boolean
  userInfo: Record<string, any> | null
}
```

⚠️ 校验用后端接口：请先用 `createApiClient(event, { withAuth: true })` 探测。
JeecgBoot 常用的"取当前登录用户"接口是 `/sys/user/getUserInfo`。
**实现者必须自己确认可用的接口**（可在服务器起来后用 curl 实测，
或参考 `ck-server` 里的 Controller 定义），不要凭空猜测。
若确实找不到专用校验接口，退化为"用 cookie 里的 token 做一次 `withAuth:true` 的
轻量请求，401/403 即视为失效"。

---

## 2) `useAuth` 契约（`nuxt-shop/composables/useAuth.ts`）

必须导出以下内容（名称不可改）：

```ts
const {
  userInfo,        // Ref<UserInfo|null>  —— 保持不变
  isLoggedIn,      // ComputedRef<boolean> —— 语义收紧，见下
  authChecked,     // Ref<boolean>       —— 新增：是否已经完成一次服务端校验
  login,           // 保持不变
  logout,          // 保持不变
  restoreUser,     // 保持不变（向后兼容，仍从 localStorage 回显）
  refreshSession,  // 新增：async () => Promise<boolean>，调 /api/auth/session 并同步状态
} = useAuth()
```

语义要求：
- `refreshSession()`：调用 `/api/auth/session`，
  - `authenticated === true` → 写入 `userInfo.value` + 同步 localStorage，返回 `true`
  - `authenticated === false` → 清空 `userInfo.value` + 删除 localStorage 的 `user-info`，返回 `false`
- `authChecked`：`refreshSession()` 至少成功跑完一次后置 `true`。
- `isLoggedIn`：**保持 `computed(() => !!userInfo.value)` 不变**。
  （不要改成依赖 authChecked，否则首屏闪烁；判定登录态由中间件负责调用 `refreshSession`）
- 必须用 `useState` 存 `authChecked`，避免 SSR/CSR 状态不一致。
- `login()` / `logout()` 成功后要正确维护 `authChecked` 与 localStorage（logout 清空）。

## 3) 路由中间件契约（`nuxt-shop/app/middleware/`）

> ⚠️ **路径勘误（Lead 实测，2026-10-15）**
>
> 中间件**必须**放在 `nuxt-shop/app/middleware/auth.ts`，
> **不是**仓库根的 `nuxt-shop/middleware/auth.ts`。
>
> 根因：Nuxt 4 默认 `srcDir = 'app/'`，本仓库存在 `app/` 目录，
> 因此 Nuxt 只扫描 `app/` 下的 `middleware/`、`pages/`、`components/`。
> 根目录的 `middleware/`（和 `composables/`）都在 srcDir 之外，**从来没被注册过**，
> 这也正是 `composables/` 里到处都要显式 import 的同一个原因。
>
> 实测证据：给 `/zh/cart` 加 `definePageMeta({ middleware: 'auth' })` 后请求该路径返回
> `500 {"message":"Unknown route middleware: 'auth'. Valid middleware: ."}`，
> 注意 `Valid middleware: .` —— 一个都没注册。

- `app/middleware/auth.ts`：**命名中间件**，供页面 `definePageMeta({ middleware: 'auth' })` 使用。
  - 未登录 → `navigateTo(localePath({ path: '/login', query: { redirect: to.fullPath } }))`
  - 注意必须带 `redirect`，且必须是**站内路径**（`to.fullPath` 天然满足）
  - 已登录 → 放行
  - **必须注意 SSR**：服务端可以用 `useRequestHeaders(['cookie'])` 把浏览器 cookie
    转发给 `/api/auth/session`，这样首屏就能判定登录态，不出现"先渲染再跳转"的闪烁。
  - 不要用 `definePageMeta({ middleware: 'auth' })` 之外的全局中间件去拦所有页面。
- 受保护页面清单（由 Lead 负责在页面里加 `definePageMeta`，实现中间件的人不用改页面）：
  `/profile`、`/cart`、`/orders`、`/orders/:orderNo`、`/address`、`/checkout`、`/checkout/pay`

## 4) 公开页面（**绝不加** middleware）

`/`、`/categories`、`/categories/:id`、`/products`、`/products/:slug`、`/login`
这些页面及其调用的接口必须保持匿名可访问（现有 `withAuth:false` 的 BFF 不要动）。

---

## 写入范围（避免并行冲突）

| 归属 | 文件 |
|---|---|
| auth-core | `composables/useAuth.ts`、`server/api/auth/session.get.ts`、`server/api/auth/*.ts` |
| route-guard | `middleware/auth.ts`、`middleware/*.ts` |
| Lead | `app/pages/**`（加 definePageMeta）、`docs/**`、`app/components/Header.vue` |

不要跨范围改文件。需要别人改的东西，通过消息告知 Lead。
