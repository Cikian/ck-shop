// server/utils/captchaProxy.ts
//
// CikVerify 验证码 BFF 代理的公共部分：challenge / verify 两个接口的行为完全一致
// （同源请求体大小限制、同 IP 限流、转发浏览器 UA 与真实 IP、原样返回 Java 的
// `{code,msg,data}` 信封），区别只有 Java 路径、body 形状与限流阈值。
//
// 为什么需要 UA / X-Forwarded-For（不是可选项）：
//   Java 端 SDK 在 verify 成功签发 ticket 时，会把「调用方 IP + User-Agent」一起
//   绑定到 ticket 上（CaptchaTicketService#issue）。发信接口消费 ticket 时再按同样
//   的 IP/UA 校验。BFF 是唯一知道浏览器地址的一层，如果这里不转发：
//     · Java 只能看到 BFF 自己的 127.0.0.1，所有用户共用一个 IP —— 一人的 ticket
//       可被另一人消费，IP 限流也全部失效；
//     · UA 绑定会退化成「BFF 的 Node UA」，并且发信时如果漏转发又对不上。
//   所以 challenge / verify / send-code 三条链路必须转发**同一份** UA + XFF。
//
// X-Forwarded-For 只放【单个】IP：Java SDK 只取逗号前的第一个值，这里直接给
// getClientIp(event) 的结果，绝不透传浏览器带来的整串 XFF（否则可伪造）。

import type { H3Event } from 'h3'
import { getClientIp } from './clientIp'

/** 与 CikVerify 接入检查项一致：challenge/verify 的 body 不得大于 256KB。 */
export const CAPTCHA_MAX_BODY_BYTES = 256 * 1024

/** Java 验证码网关的响应信封（与验证码服务 `{code,msg,data}` 完全一致）。 */
export interface CaptchaEnvelope {
  code: number
  msg: string
  data?: unknown
}

/**
 * 读取并校验验证码接口的请求体。
 *
 * 两步限制：
 *   1) 先看 Content-Length（有的话），超限立刻 413，不把大 body 读进内存；
 *   2) 再对**已解析**的 body 做一次序列化长度兜底 —— 分块传输（无 Content-Length）
 *      或 `Content-Length` 与实际不符时也能拦住。
 *
 * 注意这里不校验业务字段：challenge 的 `type` 可空（缺省由服务端 defaultType 决定），
 * verify 的 `id/data` 交给 Java 校验并返回它自己的 403/4000，BFF 不重复发明规则。
 */
export async function readCaptchaBody<T>(event: H3Event): Promise<T | undefined> {
  const declared = Number(getHeader(event, 'content-length') || 0)
  if (Number.isFinite(declared) && declared > CAPTCHA_MAX_BODY_BYTES) {
    throw createError({ statusCode: 413, statusMessage: '请求体过大' })
  }

  // 注意不吞 readBody 的错误：h3 对非法 JSON 回 400、对超限 body 回 413，
  // 这些都是正确的快速失败；吞掉会把一个明显非法的请求当成「空 body」放行，
  // 反而让后端去猜。空 body（真正没传）时 readBody 返回 undefined，不会抛错。
  const body = await readBody<T>(event)
  if (body !== undefined && body !== null) {
    let size = 0
    try {
      size = Buffer.byteLength(JSON.stringify(body), 'utf8')
    } catch {
      // 无法序列化的 body 交给后端判非法，不在这里误伤
    }
    if (size > CAPTCHA_MAX_BODY_BYTES) {
      throw createError({ statusCode: 413, statusMessage: '请求体过大' })
    }
  }
  return body
}

/** 转发给 Java 的浏览器身份头：单个真实 IP + 浏览器原始 UA。 */
export function captchaForwardHeaders(event: H3Event): Record<string, string> {
  const headers: Record<string, string> = {}

  const userAgent = getHeader(event, 'user-agent')
  if (userAgent) {
    headers['User-Agent'] = userAgent
  }

  const clientIp = getClientIp(event)
  if (clientIp && clientIp !== 'unknown') {
    // 只放单个 IP，且不包含端口
    headers['X-Forwarded-For'] = clientIp
  }

  return headers
}

/**
 * 把请求代理到 Java 的验证码网关，并**保真**返回它的一层信封。
 *
 * 关键点：
 *   · Java 的 CaptchaClientController 对业务失败（4000/4001/403/5000…）返回的是
 *     HTTP 200 + `{code,msg,data}`，这是验证码服务/SDK 的既定契约，前端 tac 也是按
 *     `res.code === 200` 判成功的。所以**不能**按 HTTP 状态重写 code，否则前端拿到
 *     一个 200/400 错配的响应，会一直 reload 或直接报"资源加载失败"。
 *   · 真正的失败（Java 连不上、超时、返回非信封内容）必须暴露出来：返回 502 +
 *     `{code:5000,msg:"验证服务暂不可用"}`，绝不吞成 200 —— 否则前端会以为拿到了
 *     一个合法的 challenge 响应，进而卡在永久 loading。
 *   · Java 若真的回了非 2xx 状态（网关前置错误等），保留它的 HTTP 状态，body 原样透传。
 *
 * 实现上用 `api.raw` + `ignoreResponseError` 而不是普通 $fetch：
 * createApiClient 的 onResponseError 会重新抛 createError 并把 body 换成 statusMessage，
 * 那会丢掉 `{code,msg,data}`；ignoreResponseError 让非 2xx 也把原始响应交回来。
 * 另外 JeecgBoot 的全局 ResponseBodyAdvice 会给 SDK 的 `ApiResult` 补一个 `success` 字段，
 * 所以这里必须用 `rawEnvelope: true`，否则默认的 Jeecg Result 解包会把 `success:true`
 * 当成 `{result: ...}` 解出 undefined（challenge 变 502），`success:false` 时又会抛错丢信封。
 */
export async function proxyCaptcha(
  event: H3Event,
  path: string,
  body: Record<string, unknown>,
): Promise<CaptchaEnvelope> {
  const api = createApiClient(event, { rawEnvelope: true })
  const headers = captchaForwardHeaders(event)

  try {
    const response = await api.raw<CaptchaEnvelope>(path, {
      method: 'POST',
      body,
      headers,
      ignoreResponseError: true,
    })

    const envelope = response._data

    // 非信封响应（HTML 错误页 / 空体 / 网关降级）视为上游故障，不能让前端当成功处理
    if (!envelope || typeof envelope !== 'object' || typeof envelope.code !== 'number') {
      console.error('[captchaProxy] non-envelope response', path, `status=${response.status}`)
      setResponseStatus(event, 502)
      return { code: 5000, msg: '验证服务暂不可用' }
    }

    // Java 正常时是 200；若前置网关回了非 2xx，则保留状态码与 body
    if (response.status && response.status !== 200) {
      setResponseStatus(event, response.status)
    }

    return envelope
  } catch (error: any) {
    // 连不上 / 超时 / DNS 失败：fail-closed，明确 502。
    // 只记录一行摘要（不打印请求 body/头，避免把用户邮箱等带进日志）。
    console.error(
      '[captchaProxy] upstream unavailable',
      path,
      error?.statusCode || error?.status || 502,
      error?.statusMessage || error?.message || 'unknown error',
    )
    setResponseStatus(event, 502)
    return { code: 5000, msg: '验证服务暂不可用' }
  }
}
