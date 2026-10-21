// server/api/auth/session.get.ts
// GET /api/auth/session —— 登录态的**服务端权威来源**
//
// 为什么需要这个接口：
// token 存在 httpOnly cookie 里，前端 JS 读不到；而 localStorage 里的 user-info
// 只是一份缓存，cookie 过期后它还在，导致前端误判"已登录"。
// 所以"是否还登录着"这件事必须由服务端回答：只有服务端能看到 cookie，
// 也只有 Java 后端能判断这个 token 是否还有效。
//
// 本接口**一律返回 HTTP 200**，用 body 里的 authenticated 表达结果：
// 调用它的地方是路由中间件，中间件要的是"能不能过"，
// 而不是去分辨 401/403/500 各代表什么；统一 200 可以让调用方只判断一个布尔值。
//
// 校验用的后端接口：GET /shop/account/profile（店铺端专用的轻量"当前用户"接口）。
//
// ⚠️ 改造说明（原先读的是 JeecgBoot 官方的 /sys/user/getUserInfo）：
//   /sys/user/getUserInfo 是**管理端**的接口，会带回 sysAllDictItems 等一大堆
//   店铺端根本用不到的数据，而且无法表达 hasPassword（前端需要它来决定
//   显示"设置密码"还是"修改密码"）。所以店铺端改用 /shop/account/profile。
//
// ⚠️ 但**返回值形状 AuthSession 一个字都不能变**：
//   app/middleware/auth.ts 与 composables/useAuth.ts 都按
//   `{ authenticated, userInfo }` 解析（useAuth 还直接 import 了下面这个类型）。
//   改形状会造成"中间件放行但页面按未登录渲染"这类难查的问题。
//
//   /shop/account/profile 返回的是**扁平的 profile 对象本身**（不是 { userInfo } 包一层），
//   所以下面要把整个 profile 当作 userInfo 返回 —— 字段名恰好包含了前端要的
//   realname / username / avatar / email，展示层无需改动。

// 语言必须走「受支持语言」的唯一来源收敛，**不能写死 zh/en 二选一**：
// 曾经这里是 `query.lang === 'zh' ? 'zh' : 'en'`，法语（fr）用户会被当成 en ——
// 后端按 Accept-Language 返回的异常文案就变成英文；将来新增语言也会同样静默降级。
import { resolveStoreLang } from '~~/types/store-locale'

export interface AuthSession {
  authenticated: boolean
  userInfo: Record<string, any> | null
}

// 未登录的统一返回形状。避免在多处重复写字面量对象。
const UNAUTHENTICATED: AuthSession = { authenticated: false, userInfo: null }

export default defineEventHandler(async (event): Promise<AuthSession> => {
  // SSR 相关：本接口不自己转发 cookie，转发动作由调用方（middleware/auth.ts）负责 ——
  // 它在服务端渲染时用 useRequestHeaders(['cookie']) 把浏览器 cookie 带给这次请求。
  // 少了那一步，SSR 期间这里读不到 token，首屏会被误判为未登录（先渲染再跳转的闪烁）。
  const token = getCookie(event, 'access-token')
  if (!token) {
    return UNAUTHENTICATED
  }

  // 语言透传：后端按 Accept-Language 处理异常文案，这里沿用项目统一做法从 ?lang= 取。
  const query = getQuery(event)
  const lang = resolveStoreLang(query.lang, 'en')

  try {
    const api = createApiClient(event, { withAuth: true, lang })
    const result = await api<Record<string, any>>('/shop/account/profile', {
      method: 'GET',
    })

    // apiClient 的响应拦截器已经解包了 Result 外层，这里拿到的是 profile 本体
    // （{ id, username, realname, email, avatar, phone, hasPassword, ... }）。
    // 只要没抛异常就说明 token 有效，但 body 仍可能缺失（后端 Result 成功却没有 result），
    // 那种情况不能算"已登录"，否则前端会拿到一个没有用户的登录态。
    //
    // ⚠️ 注意这里**不能**再判断 result.userInfo（那是老接口的嵌套形状）。
    //    profile 是扁平的，只要是一个非空对象就算有效登录态。
    if (!result || typeof result !== 'object') {
      return UNAUTHENTICATED
    }
    return { authenticated: true, userInfo: result }
  } catch (error: any) {
    // 判据只用**状态码**，不再去正则匹配错误文案。
    //
    // 历史坑（现已修掉）：apiClient 的 onResponse 曾经对 success:false 一律抛
    // statusCode=400，把后端真实的 401/403 吞掉，于是这里只能靠
    // /token|令牌/i 去猜"这是不是登录失效"。现在 apiClient 会保留真实状态码
    // （HTTP 状态优先，其次用 body.code，code=401 也会映射成 401），
    // 所以这里可以干净地按 401/403 判断。
    const statusCode = error?.statusCode ?? error?.status

    if (statusCode === 401 || statusCode === 403) {
      // cookie 已经是一张废票，删掉它：否则每次读 session 都要白发一次请求到后端，
      // 而且浏览器会一直带着这个死 cookie。
      clearAccessTokenCookie(event)
      return UNAUTHENTICATED
    }

    // ★ 其他错误（后端没起来、网络超时、5xx）**不删 cookie**，也不向上抛。
    // 理由：这类错误说明"我们现在无法判断 token 是否有效"，而不是"token 无效"。
    // 若把 500 抛给前端，后端抖一下会把所有在线用户弹下线；
    // 若删掉 cookie，用户就得重新登录。所以这里安全降级为未登录（调用方会引导去登录页），
    // 但保留 cookie —— 后端恢复后用户刷新页面就能自动回到登录态。
    console.warn(
      `[/api/auth/session] 校验登录态失败（非鉴权错误，已降级）: `
      + `${error?.statusMessage || error?.message || error}`,
    )
    return UNAUTHENTICATED
  }
})
