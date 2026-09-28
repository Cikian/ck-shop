// server/api/auth/login.post.ts
// 前端请求：POST /api/auth/login  body: { username, password(明文), captcha, checkKey }
// 对应转发到 Java 后端：POST {baseURL}/sys/login

interface LoginBody {
  username: string
  password: string
  captcha: string
  checkKey: string
}

export default defineEventHandler(async (event) => {
  const body = await readBody<LoginBody>(event)

  if (!body.username || !body.password || !body.captcha || !body.checkKey) {
    throw createError({ statusCode: 400, statusMessage: '缺少必填参数' })
  }

  // 密码在 server 端加密，浏览器全程只传明文（走 HTTPS 的话本身也是加密传输），
  // AES key 不会暴露给浏览器
  const encryptedPassword = encryptPassword(body.password)
  console.log('加密后的密文：', encryptedPassword)
  const api = createApiClient(event)

  // 后端返回结构（经过 apiClient 的响应拦截器自动解包后，
  // 这里拿到的就是 result 字段里的内容：{ token, tenantList, userInfo, departs, multi_depart }）
  const result = await api<{
    token: string
    userInfo: Record<string, any>
    tenantList: any[]
    departs: any[]
  }>('/sys/login', {
    method: 'POST',
    body: {
      username: body.username,
      password: encryptedPassword,
      captcha: body.captcha,
      checkKey: body.checkKey,
    },
  })

  // ============================================================
  // ★★★ token 存储策略 ★★★
  //
  // 存进 httpOnly cookie，而不是返回给前端让它存 localStorage：
  // - httpOnly：浏览器 JS（包括被注入的恶意脚本，XSS 场景）读不到这个 cookie，
  //   比存 localStorage 安全得多（localStorage 里的东西任何脚本都能读）
  // - 之后每次调用需要登录态的接口，server/api 里直接从 cookie 读 token，
  //   自动拼到 x-access-token 请求头发给 Java 后端，前端页面代码完全不用管 token
  // ============================================================
  setCookie(event, 'access-token', result.token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production', // 本地 http 开发时关掉，线上 https 打开
    sameSite: 'lax',
    path: '/',
    maxAge: 60 * 60 * 24 * 7, // 7 天，可以按后端 token 有效期调整
  })

  // userInfo 不算敏感数据（不含密码、token），可以直接返回给前端存起来，
  // 用来在页面上显示"你好，XXX"这种
  return {
    userInfo: result.userInfo,
    tenantList: result.tenantList,
  }
})
