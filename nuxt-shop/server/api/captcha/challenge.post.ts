// server/api/captcha/challenge.post.ts
// 前端请求：POST /api/captcha/challenge
// body: { type?: 'SLIDER' | 'ROTATE' | 'CONCAT' | 'WORD_IMAGE_CLICK' | 'POW' }
// 对应转发到 Java 后端：POST {baseURL}/captcha/challenge
//
// 背景：ck-server 的 context-path=/api，CikVerify Java SDK 自动注册了
// `POST /api/captcha/challenge`，返回 `{code,msg,data}`（data 里是验证码图片/轨迹
// 等挑战数据）。浏览器端的 @cikian/captcha-web 直接打本站这个同源路径，
// BFF 负责把浏览器身份（UA + 真实 IP）带过去，因为 Java 端签发的 ticket 会绑定
// 这两项（详见 server/utils/captchaProxy.ts 顶部说明）。
//
// 为什么 body 要接受两种形状：
//   · 直接在 curl / 联调里调用时是 `{type:"POW"}`（任务契约）；
//   · @cikian/captcha-web 的 tac 兼容层把 type 注入在 `requestParam.data.type`，
//     实际发出的是 `{"data":{"type":"POW"}}`。
//   两种都要能识别，否则 SDK 选的类型会被 Java 当成「未指定」而回退 defaultType，
//   POW 联调会静默失效。
//
// 限流 20 次/分钟/IP：每次 challenge 都会真的去线上验证码服务生成图片/挑战，
// 不限制的话匿名脚本可以持续消耗配额（4290）。

interface ChallengeBody {
  type?: unknown
  data?: { type?: unknown } | null
}

function pickType(body: ChallengeBody | undefined): string {
  const raw = body?.type ?? body?.data?.type
  return typeof raw === 'string' ? raw.trim() : ''
}

export default defineEventHandler(async (event) => {
  await enforceRateLimit(event, {
    key: 'captcha:challenge',
    limit: 20,
    windowMs: 60_000,
    message: '验证码获取过于频繁，请稍后再试',
  })

  const body = await readCaptchaBody<ChallengeBody>(event)

  const type = pickType(body)
  // 空 type 不传给 Java：Java 收到空串会当成「指定了非法类型」，而缺省才会用
  // 应用配置的 defaultType。
  return await proxyCaptcha(event, '/captcha/challenge', type ? { type } : {})
})
