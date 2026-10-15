// server/utils/apiClient.ts
//
// 这是整个项目里"和 Java 后端通信"的唯一入口。
// 所有 server/api/*.ts 文件都应该通过这里创建的客户端去请求后端，
// 不要在各个文件里各自写 $fetch('http://127.0.0.1:9951/api/...')——
// 那样一旦 baseURL 变了，或者要加统一的错误处理，就得改几十个文件。
//
// 放在 server/utils/ 目录下的文件，Nuxt/Nitro 会自动扫描注册，
// 在 server/api/ 的任何文件里可以直接用 createApiClient(...)，不需要 import。

import type { H3Event } from 'h3'
import { signMd5Utils } from './signMd5'
import { STORE_INTL_LOCALE_TAGS } from '~~/types/store-locale'

interface BackendEnvelope<T = unknown> {
  code: number
  message: string
  result: T
  success: boolean
  timestamp: number
}

/**
 * 哪些业务 code 值得**原样**变成 HTTP 状态码。
 *
 * 只放行这三个：它们表达的是"调用方该换个身份/该慢下来"，前端据此走登录引导或退避。
 *
 * 特别注意**不能**放行 500：JeecgBoot 的 {@code Result.error(msg)} 把
 * "邮箱或密码错误""库存不足""优惠券已失效"这类**业务失败**也标成 code=500，
 * 但 HTTP 状态是 200。如果照搬成 HTTP 500：
 *   · 监控/告警会把用户输错密码当成服务端故障；
 *   · 任何"5xx 才重试"的客户端逻辑会对业务错误反复重试。
 * 所以业务失败统一回落成 400（与改造前的行为一致），语义是"请求本身没成事"，
 * 真正的 HTTP 5xx 仍然由 httpStatus 优先级兜住。
 */
const FORWARDABLE_CODES = new Set([401, 403, 429])

/**
 * 把「后端业务失败」翻译成**正确的 HTTP 状态码**。
 *
 * 以前这里不分青红皂白一律抛 400，导致调用方（以及前端页面）完全无法区分
 * 「参数错了」和「登录已失效」：
 *   · 后端对失效 token 回的是 HTTP 401 + body{success:false, code:401}；
 *   · 而拦截器看到 success:false 就先抛 400，onResponseError 里那个真实状态
 *     根本没机会执行；
 *   · 结果 server/api/auth/session.get.ts 只能用正则去猜错误文案里有没有
 *     "token/令牌"，其他所有"401 就跳登录"的逻辑都不可靠。
 *
 * 现在的规则：能拿到的真实 HTTP 状态优先，其次用 body.code（限白名单），
 * 都没有才退化为 400。
 */
function resolveFailureStatus(httpStatus: number | undefined, envelopeCode: unknown): number {
  if (typeof httpStatus === 'number' && httpStatus >= 400) {
    return httpStatus
  }
  const code = Number(envelopeCode)
  return FORWARDABLE_CODES.has(code) ? code : 400
}

export function createApiClient(
  event: H3Event,
  options: { withAuth?: boolean; lang?: string } = {}
) {
  const config = useRuntimeConfig(event)
  const signUtils = signMd5Utils({ signSecret: config.signSecret })

  // 【核心修改】：从事件上下文中安全获取当前语言代码（如 'zh' 或 'en'）
  // 1. 优先使用调用方显式传入的 lang（BFF 里用 ?lang= 解析出来的值最准确，
  //    因为 /api/* 这类路径没有语言前缀，i18n 中间件解析不出 event.context.locale）
  // 2. 其次读取前端可能在请求中自带的 'x-language' 头部信息
  // 3. 再读 @nuxtjs/i18n 在服务端解析出来的 event.context.locale
  // 4. 最后默认回退到 'en'
  const lang = options.lang || getHeader(event, 'x-language') || event.context.locale || 'en'

  const headers: Record<string, string> = {}
  let token: string | undefined

  if (options.withAuth) {
    token = getCookie(event, 'access-token')
    if (!token) {
      throw createError({ statusCode: 401, statusMessage: '未登录或登录已过期' })
    }
    // ⚠️ 只发**一个** token 头，且用后端真正读取的那个名字：X-Access-Token。
    //
    // 曾经这里同时设置 'x-access-token'、'X-Access-Token' 和 'Authorization'。
    // HTTP 头名大小写不敏感，但 undici 把它们当成三个不同的键各发一遍，
    // Tomcat 收到重复的 X-Access-Token 后用 ", " 拼成一个值，
    // 后端拿到的就是 "eyJ...eyJ..., eyJ...eyJ..." ——
    // JeecgBoot 报 "The token was expected to have 3 parts, but got > 3"（Token非法无效!），
    // 结果是**所有需要登录态的接口都调不通**。
    // 本项目此前只有匿名接口，所以这个坑一直没暴露；购物车是第一个要求登录的功能。
    //
    // 后端读取位置：config/shiro/filters/JwtFilter#executeLogin 与 common/util/TokenUtils
    // 都只读 X-Access-Token；'token' 读的是**请求参数**而不是请求头，Authorization 则完全不读。
    headers['X-Access-Token'] = token
    headers['tenant-id'] = '0'
  }

  // 【核心修改】：统一将当前前端的语言环境透传给 Java 服务端
  // 之前这里写的是 `lang === 'zh' ? 'zh-CN' : 'en-US'`，于是**法语站点**
  // 也被声明成 en-US：页面骨架是法语，但后端生成的文案（异常提示、标签）
  // 全是英文。改为按 types/store-locale.ts 的 BCP-47 映射表翻译，
  // 新增语言时不需要再改这里。
  headers['Accept-Language'] = STORE_INTL_LOCALE_TAGS[lang] || 'en-US'
  headers['x-language'] = lang

  // 店铺标识：由本站在服务端配置，通过请求头传给后端。
  // 刻意**不放进 URL**——商品链接要分享到社交平台，必须短；
  // 而且请求头由服务端注入，浏览器拿不到也改不了，后端据此过滤即可保证不串店。
  const storeCode = (config.storeCode as string | undefined) || ''
  if (storeCode) {
    headers['X-Store-Code'] = storeCode
  }

  return $fetch.create({
    baseURL: config.apiBaseUrl,

    headers,

    /**
     * 超时必须有：没有它时，后端一旦卡住（或连不上），
     * await 这个调用的 Nitro 请求会**一直挂着**，而 SSR 期间
     * app/middleware/auth.ts、首页/商品页的 setup 都在等它 ——
     * 结果是整个前台所有服务端渲染页面一起变慢甚至超时，而不是"降级展示"。
     */
    timeout: Number(config.apiTimeoutMs) || 10000,

    /**
     * 重试刻意关闭（ofetch 默认会对部分失败的 GET 自动重试一次）。
     * 这里的调用既有幂等 GET，也有下单/加购这类非幂等 POST，
     * 统一重试会带来"我的购物车怎么多了一件"这类难查的问题；
     * 需要重试的场景应由业务层用幂等键显式实现（订单已经有 idempotentKey）。
     */
    retry: 0,

    onRequest({ request, options }) {
      const timestamp = signUtils.getTimestamp().toString()
      const url = request as string
      const params = options.params as Record<string, any> | undefined
      const body = options.body as Record<string, any> | undefined

      const sign = signUtils.getSign(url, params, body)

      options.headers = options.headers || {}
      options.headers['timestamp'] = timestamp
      options.headers['sign'] = sign
      options.headers['version'] = 'v3'

      // 只在开发环境打印：生产日志里堆满请求明细既没用，又会泄露
      // 完整接口地址（含查询参数，例如订单号、邮箱）。
      if (import.meta.dev) {
        console.log(`[API →] ${options.method || 'GET'} ${config.apiBaseUrl}${url}`)
      }
    },

    onResponse({ response }) {
      const body = response._data as BackendEnvelope

      if (body && typeof body === 'object' && 'success' in body) {
        if (!body.success) {
          throw createError({
            statusCode: resolveFailureStatus(response.status, body.code),
            statusMessage: body.message || '请求失败',
          })
        }
        response._data = body.result
      }
    },

    onResponseError({ response }) {
      const message =
        (response._data as any)?.message || `后端接口错误 (${response.status})`
      throw createError({
        statusCode: response.status || 500,
        statusMessage: message,
      })
    },

    /**
     * 请求根本没拿到响应时（后端没启动、端口不通、超时被 abort）走这里。
     * 区分 504（超时，值得重试/稍后再试）与 502（连不上，属于部署问题），
     * 前端才能给出"稍后重试"还是"服务不可用"的正确提示。
     */
    onRequestError({ error }) {
      const name = String((error as any)?.name || '')
      const cause = String((error as any)?.cause?.name || '')
      const isTimeout = name === 'AbortError' || name === 'TimeoutError'
        || cause === 'AbortError' || cause === 'TimeoutError'

      throw createError({
        statusCode: isTimeout ? 504 : 502,
        statusMessage: isTimeout
          ? '后端服务响应超时，请稍后重试'
          : '无法连接后端服务，请确认服务已启动',
      })
    },
  })
}
