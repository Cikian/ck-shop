// server/api/captcha/verify.post.ts
// 前端请求：POST /api/captcha/verify
// body: { id: string, data: object }
// 对应转发到 Java 后端：POST {baseURL}/captcha/verify
//
// 成功时 Java SDK 会签发一次性 ticket：`{code:200,msg:"OK",data:{ticket}}`。
// ticket 绑定「浏览器 IP + User-Agent」（CaptchaTicketService#issue），
// 因此这里必须和 challenge 一样转发 UA + 单个 X-Forwarded-For；
// 随后前端把 ticket 交给 /api/auth/send-code，BFF 再以同一身份转发给 Java 消费。
// 任何一处 UA/IP 不一致，send-code 都会以「请先完成安全验证」失败。
//
// 限流 60 次/分钟/IP：正常用户最多拖错几次，60 次足够；比 challenge 宽松是因为
// 一个 challenge 可能被反复验证（每次失败都要重新 challenge）。

interface VerifyBody {
  id?: unknown
  data?: unknown
}

export default defineEventHandler(async (event) => {
  await enforceRateLimit(event, {
    key: 'captcha:verify',
    limit: 60,
    windowMs: 60_000,
    message: '验证过于频繁，请稍后再试',
  })

  const body = await readCaptchaBody<VerifyBody>(event)

  // 只做最基础的结构收敛：id 必须是字符串、data 必须是对象/数组（下面原样透传），
  // 具体的合法性（challenge 是否存在、轨迹是否匹配）由 Java/验证码服务判定。
  const id = typeof body?.id === 'string' ? body.id.trim() : ''
  const payload: Record<string, unknown> = {
    id,
    data: body?.data ?? {},
  }

  return await proxyCaptcha(event, '/captcha/verify', payload)
})
