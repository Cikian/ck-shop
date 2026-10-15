// server/api/auth/reset-password.post.ts
// 前端请求：POST /api/auth/reset-password
// body: { email, emailCode, newPassword, confirmPassword }
// 对应转发到 Java 后端：POST {baseURL}/shop/account/password/reset
//
// 契约（见 docs/店铺端账号体系改造-接口契约.md 第 2.6 / 3 节）：
//   后端返回 Result<{ success: boolean }>。
//
// 这是**未登录**场景（忘记密码），所以走 createApiClient(event) 不带 withAuth：
// 用户此刻没有有效 token，带了反而会在 BFF 层就抛 401。

import { resolveStoreLang } from '~~/types/store-locale'

interface ResetPasswordBody {
  email?: string
  emailCode?: string
  newPassword?: string
  confirmPassword?: string
  lang?: string
}

export default defineEventHandler(async (event) => {
  const body = await readBody<ResetPasswordBody>(event)

  const email = String(body?.email ?? '').trim()
  if (!email) {
    throw createError({ statusCode: 400, statusMessage: '请输入邮箱' })
  }

  // 限流：未登录接口，且验证码可被暴力猜，10 分钟 5 次（按 IP + 邮箱）。
  enforceRateLimit(event, {
    key: 'auth:reset-password',
    limit: 5,
    windowMs: 10 * 60_000,
    extra: email,
  })

  // 验证码是 scene=reset 的那个（契约 2.6），后端校验后立即删除（一次性）。
  const emailCode = String(body?.emailCode ?? '').trim()
  if (!emailCode) {
    throw createError({ statusCode: 400, statusMessage: '请输入邮箱验证码' })
  }

  const newPassword = String(body?.newPassword ?? '')
  if (!newPassword) {
    throw createError({ statusCode: 400, statusMessage: '请输入新密码' })
  }

  const confirmPassword = String(body?.confirmPassword ?? '')
  if (!confirmPassword) {
    throw createError({ statusCode: 400, statusMessage: '请再次输入新密码' })
  }

  // 与注册一致：明文进、服务端 AES 加密后转发。
  // 密码强度（8-32 位含字母数字）与两次一致性由后端做权威校验，
  // 文案原样回给前端（避免 BFF/后端两套规则给出矛盾的提示）。
  const encryptedPassword = encryptPassword(newPassword)

  const api = createApiClient(event, { lang: resolveStoreLang(body?.lang, 'en') })

  try {
    const result = await api<{ success: boolean }>('/shop/account/password/reset', {
      method: 'POST',
      body: {
        email,
        emailCode,
        newPassword: encryptedPassword,
        confirmPassword,
      },
    })
    return { success: result?.success ?? true }
  } catch (error: any) {
    // 后端业务文案原样透传："验证码错误或已过期" / "该邮箱尚未注册" /
    // "两次输入的密码不一致" / "新密码不能与原密码相同" 等。
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '密码重置失败，请稍后重试',
    })
  }
})
