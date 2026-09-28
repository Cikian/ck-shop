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
  options: { withAuth?: boolean } = {}
) {
  const config = useRuntimeConfig(event)
  const signUtils = signMd5Utils({ signSecret: config.signSecret })

  // 【核心修改】：从事件上下文中安全获取当前语言代码（如 'zh' 或 'en'）
  // 1. 优先读取 @nuxtjs/i18n 在服务端解析出来的 event.context.locale
  // 2. 其次降级读取前端可能在请求中自带的 'x-language' 头部信息
  // 3. 最后默认回退到 'en'
  const lang = getHeader(event, 'x-language') || event.context.locale || 'en'

  const headers: Record<string, string> = {}
  let token: string | undefined

  if (options.withAuth) {
    token = getCookie(event, 'access-token')
    if (!token) {
      throw createError({ statusCode: 401, statusMessage: '未登录或登录已过期' })
    }
    headers['x-access-token'] = token
    headers['Authorization'] = token
    headers['X-Access-Token'] = token
    headers['tenant-id'] = '0'
  }

  // 【核心修改】：统一将当前前端的语言环境透传给 Java 服务端
  // 转换为 Java/Spring Boot 常见的标准 Locale 格式（例如 zh-CN / en-US）
  headers['Accept-Language'] = lang === 'zh' ? 'zh-CN' : 'en-US'
  headers['x-language'] = lang

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

      if (token) {
        options.headers['token'] = token
      }

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
