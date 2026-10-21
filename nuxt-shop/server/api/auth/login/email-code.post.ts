// server/api/auth/login-email-code.post.ts
// 前端请求：POST /api/auth/login/email-code
// body: { email, emailCode }
// 对应转发到 Java 后端：POST {baseURL}/shop/account/login/email-code
//
// 契约（见 docs/店铺端账号体系改造-接口契约.md 第 2.4 / 3 节）：
//   后端返回 Result<{ token, userInfo }>。
//
// ⚠️ 这个接口对**未注册邮箱会自动注册并登录**（契约 2.4：
//   "新用户自动注册" 与 Google 新用户逻辑一致）。
//   所以前端可以把它当成"邮箱一键登录/注册"，不需要先判断邮箱是否存在。
//   ⚠️ 但**发验证码**那一步（/api/auth/send-code, scene=login）对未注册邮箱
//   会报"该邮箱尚未注册"（契约 2.1）—— 两条规则的组合结果是：
//   用户必须先走 scene=register 拿到验证码，才能用邮箱验证码登录新账号。
//   这是后端契约的既有设计，BFF 不额外加工，只如实透传文案。

import { resolveStoreLang } from '~~/types/store-locale'

interface LoginEmailCodeBody {
  email?: string
  emailCode?: string
  lang?: string
}

export default defineEventHandler(async (event) => {
  const body = await readBody<LoginEmailCodeBody>(event)

  const email = String(body?.email ?? '').trim()
  if (!email) {
    throw createError({ statusCode: 400, statusMessage: '请输入邮箱' })
  }

  // 限流：验证码可以被暴力猜（6 位数字），必须限制尝试频率。
  // 后端还有「同一验证码最多错 5 次即作废 + 锁定 15 分钟」的硬限制，
  // 这里是边缘层，防止高频请求先打到后端。
  enforceRateLimit(event, {
    key: 'auth:login-email-code',
    limit: 10,
    windowMs: 60_000,
    extra: email,
  })
  enforceRateLimit(event, {
    key: 'auth:login-email-code:ip',
    limit: 20,
    windowMs: 60_000,
    message: '登录尝试过于频繁，请稍后再试',
  })

  const emailCode = String(body?.emailCode ?? '').trim()
  if (!emailCode) {
    throw createError({ statusCode: 400, statusMessage: '请输入邮箱验证码' })
  }

  const api = createApiClient(event, { lang: resolveStoreLang(body?.lang, 'en') })

  let result: { token: string; userInfo: Record<string, any> }
  try {
    // 注意：这里**不需要**密码，也**不传**密码。
    // 自动注册出来的账号 password 为空，用户后续可在个人中心用
    // /api/auth/change-password 补设（契约 2.7）。
    result = await api<{ token: string; userInfo: Record<string, any> }>(
      '/shop/account/login/email-code',
      {
        method: 'POST',
        body: { email, emailCode },
      },
    )
  } catch (error: any) {
    // 后端业务文案原样透传："验证码错误或已过期" / "账号已被冻结" 等。
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '登录失败，请稍后重试',
    })
  }

  // ============================================================
  // ★★★ token 存储策略（与其它登录路径**完全一致**）★★★
  // 详见 authCookie.ts：Secure/有效期集中一处，避免各写一份后不一致。
  // ============================================================
  setAccessTokenCookie(event, result.token)

  return { userInfo: result.userInfo }
})
