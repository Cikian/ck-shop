// server/api/auth/register.post.ts
// 前端请求：POST /api/auth/register
// body: { email, emailCode, password, confirmPassword }
// 对应转发到 Java 后端：POST {baseURL}/shop/account/register
//
// 契约（见 docs/店铺端账号体系改造-接口契约.md 第 2.2 / 3 节）：
//   后端返回 Result<{ token, userInfo, storeCode }>。
//
// ⚠️ 注册成功后**直接签发 token 并登录**（契约 2.2：免二次登录），
//   所以这里必须走和登录完全一样的 setCookie 流程 —— 见本文件末尾的说明。

import { resolveStoreLang } from '~~/types/store-locale'

interface RegisterBody {
  email?: string
  emailCode?: string
  password?: string
  confirmPassword?: string
  lang?: string
}

export default defineEventHandler(async (event) => {
  const body = await readBody<RegisterBody>(event)

  const email = String(body?.email ?? '').trim()
  if (!email) {
    throw createError({ statusCode: 400, statusMessage: '请输入邮箱' })
  }

  // 限流：注册会消耗验证码并写库，限制到 10 分钟 5 次（按 IP + 邮箱）。
  // 仅按 IP 再限一层：防"同一 IP 不断换邮箱批量注册"，那是最常见的养号方式。
  enforceRateLimit(event, {
    key: 'auth:register',
    limit: 5,
    windowMs: 10 * 60_000,
    extra: email,
  })
  enforceRateLimit(event, {
    key: 'auth:register:ip',
    limit: 10,
    windowMs: 60 * 60_000,
    message: '当前网络注册次数过多，请稍后再试',
  })

  const emailCode = String(body?.emailCode ?? '').trim()
  if (!emailCode) {
    throw createError({ statusCode: 400, statusMessage: '请输入邮箱验证码' })
  }

  // 密码**明文**透传：真正的加密由 server/utils/crypto.ts 的 encryptPassword()
  // 在服务端做（AES key 在 runtimeConfig，不进浏览器包）。
  //
  // ⚠️ 但契约 2.3/2.2 说明后端的 password 字段语义是"服务端 AES 解密的密文"，
  //   即后端期待收到 AES 密文。所以这里和旧登录接口一样先加密再转发。
  //   （密码强度 8-32 位含字母数字由后端做权威校验；BFF 只拦"空"，
  //     避免前后端两套强度规则不一致时出现"前端过了后端不过"的困惑。）
  const password = String(body?.password ?? '')
  if (!password) {
    throw createError({ statusCode: 400, statusMessage: '请输入密码' })
  }

  const confirmPassword = String(body?.confirmPassword ?? '')
  if (!confirmPassword) {
    throw createError({ statusCode: 400, statusMessage: '请再次输入密码' })
  }

  const encryptedPassword = encryptPassword(password)

  const api = createApiClient(event, { lang: resolveStoreLang(body?.lang, 'en') })

  let result: { token: string; userInfo: Record<string, any>; storeCode?: string }
  try {
    result = await api<{ token: string; userInfo: Record<string, any>; storeCode?: string }>(
      '/shop/account/register',
      {
        method: 'POST',
        body: { email, emailCode, password: encryptedPassword, confirmPassword },
      },
    )
  } catch (error: any) {
    // 后端业务文案（"该邮箱已注册" / "验证码错误或已过期" / "两次输入的密码不一致" /
    // "密码需 8-32 位且至少包含字母和数字"）**原样透传**给前端展示。
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '注册失败，请稍后重试',
    })
  }

  // ============================================================
  // ★★★ token 存储策略（与所有登录路径**完全一致**）★★★
  //
  // 存进 httpOnly cookie，而不是返回给前端让它存 localStorage：
  // - httpOnly：浏览器 JS（包括被注入的恶意脚本，XSS 场景）读不到这个 cookie，
  //   比存 localStorage 安全得多（localStorage 里的东西任何脚本都能读）
  // - 之后每次调用需要登录态的接口，server/api 里直接从 cookie 读 token，
  //   自动拼到 x-access-token 请求头发给 Java 后端，前端页面代码完全不用管 token
  //
  // Secure/有效期统一由 authCookie.ts 决定（默认 Secure=true）。
  // ============================================================
  setAccessTokenCookie(event, result.token)

  // userInfo 不算敏感数据（不含密码、token），可以直接返回给前端存起来。
  // token **绝不**出现在返回值里 —— 它只活在 httpOnly cookie 中。
  return {
    userInfo: result.userInfo,
    storeCode: result.storeCode ?? null,
  }
})
