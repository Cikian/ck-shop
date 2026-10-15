// server/api/auth/send-code.post.ts
// 前端请求：POST /api/auth/send-code
// body: { email, scene: 'register'|'login'|'reset', captcha, checkKey }
// 对应转发到 Java 后端：POST {baseURL}/shop/account/email/send-code
//
// 契约（见 docs/店铺端账号体系改造-接口契约.md 第 2.1 / 3 节）：
//   后端返回 Result<{ cooldownSeconds: number }>，apiClient 已解包外层，
//   这里拿到的就是 { cooldownSeconds }，前端据此启动倒计时。
//
// ⚠️ 图形验证码（captcha / checkKey）**必须原样透传**：
//   它是"发送邮箱验证码"的唯一前置校验，防的是脚本刷邮件。
//   店铺端登录本身不再用图形验证码（契约 2.3），但本接口要用。

import { resolveStoreLang } from '~~/types/store-locale'

// 契约 2.1 明确 scene 只有这三个取值。在 BFF 这一层就拦掉非法值，
// 免得把可疑参数透传到后端（后端仍会再校验一次，这里是快速失败 + 明确文案）。
const ALLOWED_SCENES = ['register', 'login', 'reset'] as const
type EmailScene = (typeof ALLOWED_SCENES)[number]

interface SendCodeBody {
  email?: string
  scene?: string
  captcha?: string
  checkKey?: string
  lang?: string
}

export default defineEventHandler(async (event) => {
  const body = await readBody<SendCodeBody>(event)

  // 统一做 trim：用户从别处复制邮箱时常带首尾空格，直接透传会导致
  // "邮箱已注册/尚未注册"判断失败（后端按精确值查）。
  const email = String(body?.email ?? '').trim()
  if (!email) {
    throw createError({ statusCode: 400, statusMessage: '请输入邮箱' })
  }

  const scene = String(body?.scene ?? '').trim() as EmailScene
  if (!ALLOWED_SCENES.includes(scene)) {
    throw createError({ statusCode: 400, statusMessage: '验证码场景不合法' })
  }

  const captcha = String(body?.captcha ?? '').trim()
  const checkKey = String(body?.checkKey ?? '').trim()
  if (!captcha || !checkKey) {
    throw createError({ statusCode: 400, statusMessage: '请输入图形验证码' })
  }

  // 语言透传：本接口的业务错误文案（"该邮箱已注册"等）由后端按 Accept-Language 生成，
  // 与项目统一做法一致（见 server/api/cart/items.post.ts）。
  const lang = resolveStoreLang(body?.lang, 'en')

  const api = createApiClient(event, { lang })

  // 后端的业务错误文案（"该邮箱已注册" / "该邮箱尚未注册" / "验证码错误" /
  // "发送过于频繁，请 N 秒后重试"）是给用户看的最终文案，BFF 不二次改写，
  // 原样抛给前端展示。apiClient 的 onResponse 已在 success:false 时抛错，
  // 错误文案在 error.statusMessage 上，这里只做兜底。
  try {
    return await api<{ cooldownSeconds: number }>('/shop/account/email/send-code', {
      method: 'POST',
      body: { email, scene, captcha, checkKey },
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '验证码发送失败，请稍后重试',
    })
  }
})
