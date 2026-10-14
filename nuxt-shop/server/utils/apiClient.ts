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

interface BackendEnvelope<T = any> {
  code: number
  message: string
  result: T
  success: boolean
  timestamp: number
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
  // 转换为 Java/Spring Boot 常见的标准 Locale 格式（例如 zh-CN / en-US）
  headers['Accept-Language'] = lang === 'zh' ? 'zh-CN' : 'en-US'
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

      console.log(`[API →] ${options.method || 'GET'} ${config.apiBaseUrl}${url}`)
    },

    onResponse({ response }) {
      const body = response._data as BackendEnvelope

      if (body && typeof body === 'object' && 'success' in body) {
        if (!body.success) {
          throw createError({
            statusCode: 400,
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
  })
}
