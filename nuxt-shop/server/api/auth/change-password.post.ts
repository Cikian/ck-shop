// server/api/auth/change-password.post.ts
// 前端请求：POST /api/auth/change-password
// body: { oldPassword?, newPassword, confirmPassword }
// 对应转发到 Java 后端：POST {baseURL}/shop/account/password/change
//
// 契约（见 docs/店铺端账号体系改造-接口契约.md 第 2.7 / 3 节）：
//   Header 带 X-Access-Token（登录态），后端返回 Result<{ success: boolean }>。
//
// 用途（契约 2.7）：Google 自动注册、以及邮箱验证码首次登录自动注册的用户
// 原本**没有密码**，需要在个人中心补设密码；老用户则是修改密码。
// 契约明确 oldPassword "Google 注册用户无密码时可为空"，
// 所以 BFF 这一层**不能**把 oldPassword 当成必填。

import { resolveStoreLang } from '~~/types/store-locale'

interface ChangePasswordBody {
  oldPassword?: string
  newPassword?: string
  confirmPassword?: string
  lang?: string
}

export default defineEventHandler(async (event) => {
  const body = await readBody<ChangePasswordBody>(event)

  // ⚠️ oldPassword **可以为空**（无密码账号首次设密码），只做类型收敛不做必填校验。
  //    旧密码是否正确、该不该要求旧密码，由后端按"用户当前是否已有密码"判断。
  const oldPassword = String(body?.oldPassword ?? '')

  const newPassword = String(body?.newPassword ?? '')
  if (!newPassword) {
    throw createError({ statusCode: 400, statusMessage: '请输入新密码' })
  }

  const confirmPassword = String(body?.confirmPassword ?? '')
  if (!confirmPassword) {
    throw createError({ statusCode: 400, statusMessage: '请再次输入新密码' })
  }

  const encryptedNew = encryptPassword(newPassword)
  // 只有非空才加密转发：空串加密出来是一段合法密文，后端会把它当成
  // "提供了一个旧密码"去做比对，反而破坏了"无密码账号首次设密码"的路径。
  const encryptedOld = oldPassword ? encryptPassword(oldPassword) : ''

  // 限流：改密码接口会校验旧密码，属于可被暴力尝试的凭据校验点。
  await enforceRateLimit(event, {
    key: 'auth:change-password',
    limit: 10,
    windowMs: 10 * 60_000,
  })

  // withAuth: true —— apiClient 会从 httpOnly cookie 读 token 并拼 X-Access-Token；
  // 读不到时它自己抛 401「未登录或登录已过期」，前端据此引导去登录页。
  const api = createApiClient(event, { withAuth: true, lang: resolveStoreLang(body?.lang, 'en') })

  try {
    const result = await api<{ success: boolean }>('/shop/account/password/change', {
      method: 'POST',
      body: {
        oldPassword: encryptedOld,
        newPassword: encryptedNew,
        confirmPassword,
      },
    })
    return { success: result?.success ?? true }
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '密码修改失败，请稍后重试',
    })
  }
})
