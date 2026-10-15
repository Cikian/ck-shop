// server/api/auth/profile.get.ts
// 前端请求：GET /api/auth/profile
// 对应转发到 Java 后端：GET {baseURL}/shop/account/profile（Header: X-Access-Token）
//
// 契约（见 docs/店铺端账号体系改造-接口契约.md 第 2.8 / 3 节）：
//   后端返回 Result<{
//     id, username, realname, email, avatar, phone,
//     hasPassword: boolean,        // 是否已设置密码（前端据此显示"设置密码"还是"修改密码"）
//     registerStoreCode: string,   // 注册来源店铺
//     createTime: string
//   }>
//
// ⚠️ 与 /api/auth/session 的分工（两者都在读同一个后端接口，但不是重复）：
//   - session: 给**路由中间件**用，永远返回 200，只用 body.authenticated 表达"能不能过"，
//              任何异常都安全降级，绝不把错误抛给调用方；
//   - profile: 给**页面**用，正常抛错（含 401），页面据此展示"请登录"或具体错误。
//   刻意不合并：中间件要的是"绝不中断导航"，页面要的是"能拿到真实错误"，
//   两者的错误策略相反，硬塞进一个接口会让其中一方变得别扭。
//
// ⚠️ 契约 2.8 明确**不复用** /sys/user/getUserInfo：那个接口会带回
//   sysAllDictItems 等大量管理端数据，且无法表达 hasPassword。

import { resolveStoreLang } from '~~/types/store-locale'

/** 契约 2.8 的 profile 返回形状 */
export interface ShopProfile {
  id?: string
  username?: string
  realname?: string
  email?: string
  avatar?: string
  phone?: string
  /** 是否已设置密码：Google / 邮箱验证码自动注册的用户为 false */
  hasPassword?: boolean
  /** 注册来源店铺编码 */
  registerStoreCode?: string
  createTime?: string
}

export default defineEventHandler(async (event): Promise<ShopProfile> => {
  // withAuth: true —— token 从 httpOnly cookie 读取并拼成 X-Access-Token。
  // cookie 缺失时 apiClient 会抛 401「未登录或登录已过期」，前端据此引导登录。
  const api = createApiClient(event, {
    withAuth: true,
    lang: resolveStoreLang(getQuery(event).lang, 'en'),
  })

  // apiClient 的响应拦截器已解包 Result 外层，这里直接拿到 profile 本体。
  // ⚠️ apiClient 的 onResponse 只在 success:false 时抛错，
  //    "success:true 但 result 为 null" 的畸形响应不会抛 —— 这里显式拦一道，
  //    否则页面会拿到 null 并在模板里炸开（比 401 更难排查）。
  const result = await api<ShopProfile | null>('/shop/account/profile', { method: 'GET' })

  if (!result) {
    throw createError({ statusCode: 500, statusMessage: '获取用户信息失败，请稍后重试' })
  }

  return result
})
