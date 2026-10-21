// server/api/auth/send-code.post.ts
// 前端请求：POST /api/auth/send-code
// body: { email, scene: 'register'|'login'|'reset', ticket, lang? }
// 对应转发到 Java 后端：POST {baseURL}/shop/account/email/send-code
//
// 与改造前的区别：
//   旧的图形验证码（captcha / checkKey）已整体移除，改用 CikVerify 一次性 ticket：
//     1. 浏览器先走 /api/captcha/challenge + /api/captcha/verify（BFF 代理 Java），
//        verify 成功后拿到 ticket；
//     2. 本接口把 ticket 原样交给 Java，Java 用 CaptchaTicketService#consume
//        消费它（一次性、绑定 IP + User-Agent、默认 120s 有效），消费失败一律
//        fail-closed（"请先完成安全验证"）。
//   所以这里**必须**校验 ticket 非空，并转发浏览器 UA + 真实 IP —— ticket 签发时
//   绑定的就是这两项，漏转发会导致"验证成功但发信一直失败"。
//
// 契约（见 docs/店铺端账号体系改造-接口契约.md 第 2.1 / 3 节）：
//   后端返回 Result<{ cooldownSeconds: number }>，apiClient 已解包外层，
//   这里拿到的就是 { cooldownSeconds }，前端据此启动倒计时。

import { resolveStoreLang } from '~~/types/store-locale'
import { captchaForwardHeaders } from '../../utils/captchaProxy'

// 契约 2.1 明确 scene 只有这三个取值。在 BFF 这一层就拦掉非法值，
// 免得把可疑参数透传到后端（后端仍会再校验一次，这里是快速失败 + 明确文案）。
const ALLOWED_SCENES = ['register', 'login', 'reset'] as const
type EmailScene = (typeof ALLOWED_SCENES)[number]

interface SendCodeBody {
  email?: string
  scene?: string
  ticket?: string
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

  // 限流：本接口会真的发邮件（有成本、也会被用来轰炸他人邮箱）。
  // 后端自己还有 ticket 校验 + 冷却，这里是更靠前的一道闸。
  // 两个维度都要限：
  //   · 同一 IP + 同一邮箱：防单账号刷；
  //   · 同一 IP（不限邮箱）：防"换着邮箱刷"——旧实现只有前者，攻击者
  //     随便填不同邮箱就能绕过，把平台当成免费发信机。
  enforceRateLimit(event, {
    key: `auth:send-code:${scene}`,
    limit: 5,
    windowMs: 60_000,
    extra: email,
  })
  enforceRateLimit(event, {
    key: `auth:send-code:ip:${scene}`,
    limit: 10,
    windowMs: 60_000,
    message: '当前网络验证码请求过于频繁，请稍后再试',
  })

  // ticket 是「已完成人机验证」的唯一凭证。为空说明前端没走/跳过了验证码弹窗，
  // 直接快速失败；格式是否合法、是否被消费由 Java SDK 判定（失败文案统一为
  // "请先完成安全验证"，BFF 不替后端判断 ticket 长什么样）。
  const ticket = String(body?.ticket ?? '').trim()
  if (!ticket) {
    throw createError({ statusCode: 400, statusMessage: '请先完成安全验证' })
  }

  // 语言透传：本接口的业务错误文案（"该邮箱已注册"等）由后端按 Accept-Language 生成，
  // 与项目统一做法一致（见 server/api/cart/items.post.ts）。
  // 优先用前端显式传来的 body.lang（useAuthForm 会带当前 locale）；
  // 兜底顺序：x-language 头（apiClient 会转发）→ 中间件解析的 URL 前缀/cookie → en。
  // ⚠️ 不能只写 resolveStoreLang(body?.lang, 'en')：前端若漏传 lang，中/法站点的
  //    后端错误文案会全部退回英文（曾出现过该回归）。
  const lang = resolveStoreLang(
    body?.lang || getHeader(event, 'x-language') || event.context.locale,
    'en',
  )

  const api = createApiClient(event, { lang })

  // 后端的业务错误文案（"该邮箱已注册" / "该邮箱尚未注册" / "请先完成安全验证" /
  // "发送过于频繁，请 N 秒后重试"）是给用户看的最终文案，BFF 不二次改写，
  // 原样抛给前端展示。apiClient 的 onResponse 已在 success:false 时抛错，
  // 错误文案在 error.statusMessage 上，这里只做兜底。
  try {
    return await api<{ cooldownSeconds: number }>('/shop/account/email/send-code', {
      method: 'POST',
      body: { email, scene, ticket },
      // ticket 绑定了签发时的浏览器 UA + IP，消费时必须带同一份身份
      headers: captchaForwardHeaders(event),
    })
  } catch (error: any) {
    throw createError({
      statusCode: error?.statusCode || 500,
      statusMessage: error?.statusMessage || '验证码发送失败，请稍后重试',
    })
  }
})
