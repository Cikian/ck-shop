// server/api/auth/login-password.post.ts
// 前端请求：POST /api/auth/login/password
// body: { email, password(明文) }
// 对应转发到 Java 后端：POST {baseURL}/shop/account/login/password
//
// 契约（见 docs/店铺端账号体系改造-接口契约.md 第 2.3 / 3 节）：
//   后端返回 Result<{ token, userInfo }>。
//
// ⚠️ 与旧 login.post.ts（已删除）的区别：
//   1) 登录标识从 username 换成 **email**（店铺端不再有用户名登录，
//      username 是注册时系统生成的 `u_xxxxxxxx`，用户根本记不住）；
//   2) **没有图形验证码**（契约 2.3 明确店铺端不再使用验证码登录），
//      所以这里不接收也不校验 captcha / checkKey。

import { resolveStoreLang } from '~~/types/store-locale'

interface LoginPasswordBody {
  email?: string
  password?: string
  lang?: string
}

export default defineEventHandler(async (event) => {
  const body = await readBody<LoginPasswordBody>(event)

  const email = String(body?.email ?? '').trim()
  if (!email) {
    throw createError({ statusCode: 400, statusMessage: '请输入邮箱' })
  }

  // 限流：这是撞库/密码喷洒的主要入口，此前完全没有任何节流。
  // 两个维度：
  //   · IP + 邮箱：挡住单 IP 扫同一账号；
  //   · 仅 IP：挡住"一个 IP 换着邮箱撞库"（只有前一个维度时，换邮箱即可绕过）。
  enforceRateLimit(event, {
    key: 'auth:login-password',
    limit: 10,
    windowMs: 60_000,
    extra: email,
  })
  enforceRateLimit(event, {
    key: 'auth:login-password:ip',
    limit: 20,
    windowMs: 60_000,
    message: '登录尝试过于频繁，请稍后再试',
  })

  // 密码明文透传，由服务端 encryptPassword() 加密后再发给后端
  // （AES key 在 runtimeConfig，不会暴露给浏览器）。
  const password = String(body?.password ?? '')
  if (!password) {
    throw createError({ statusCode: 400, statusMessage: '请输入密码' })
  }

  const encryptedPassword = encryptPassword(password)

  const api = createApiClient(event, { lang: resolveStoreLang(body?.lang, 'en') })

  let result: { token: string; userInfo: Record<string, any> }
  try {
    result = await api<{ token: string; userInfo: Record<string, any> }>(
      '/shop/account/login/password',
      {
        method: 'POST',
        body: { email, password: encryptedPassword },
      },
    )
  } catch (error: any) {
    // 后端业务文案原样透传：
    // "邮箱或密码错误" / "账号已被冻结" / "登录失败次数过多，请稍后再试" /
    // "该账号不是店铺会员"（契约 2.3 要求校验 shop_customer 角色）。
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '登录失败，请稍后重试',
    })
  }

  // ============================================================
  // ★★★ token 存储策略 ★★★
  //
  // 存进 httpOnly cookie，而不是返回给前端让它存 localStorage：
  // - httpOnly：浏览器 JS（包括被注入的恶意脚本，XSS 场景）读不到这个 cookie，
  //   比存 localStorage 安全得多（localStorage 里的东西任何脚本都能读）
  // - 之后每次调用需要登录态的接口，server/api 里直接从 cookie 读 token，
  //   自动拼到 x-access-token 请求头发给 Java 后端，前端页面代码完全不用管 token
  //
  // Secure / SameSite / 有效期由 server/utils/authCookie.ts 统一决定，
  // 不要在业务文件里各写一份（历史缺陷：各写一份后 secure 依赖 NODE_ENV，
  // 而生产启动命令没设 NODE_ENV，线上 cookie 全程不带 Secure）。
  // ============================================================
  setAccessTokenCookie(event, result.token)

  // userInfo 不算敏感数据（不含密码、token），可以直接返回给前端存起来，
  // 用来在页面上显示"你好，XXX"这种。token 只活在 httpOnly cookie 里。
  return { userInfo: result.userInfo }
})
