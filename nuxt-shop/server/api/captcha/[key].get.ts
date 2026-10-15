// server/api/captcha/[key].get.ts
// 前端请求路径：GET /api/captcha/任意key
// 对应转发到 Java 后端：GET {baseURL}/sys/randomImage/{key}
//
// 之所以自己重新起一个路径叫 /api/captcha/xxx，而不是完全照抄后端的
// /api/sys/randomImage/xxx —— 是为了让前端调用的地址更直观、更符合
// REST 习惯，不用死记后端内部的接口命名。这一层"改名"正是 server/api
// 作为中间层的好处之一：前端只认自己的地址，后端接口怎么设计、怎么改，
// 前端完全无感知，只要 server/api 这层跟着更新映射就行。

export default defineEventHandler(async (event) => {
  const key = getRouterParam(event, 'key') as string

  // 限流：这是匿名接口，每次调用都会让后端生成并缓存一张验证码图片。
  // 不限流的话，脚本可以用任意 key 疯狂调用，持续占用后端内存/Redis。
  enforceRateLimit(event, {
    key: 'captcha',
    limit: 60,
    windowMs: 60_000,
  })

  // key 由前端生成（useCaptcha.ts），完全客户端可控，因此必须收敛形态：
  // 只允许小写字母数字、长度 8-32。后端会把它当缓存键用，放任任意长度/
  // 任意字符（甚至超长字符串）等于把后端的键空间交给调用方。
  if (!/^[a-z0-9]{8,32}$/.test(key || '')) {
    throw createError({ statusCode: 400, statusMessage: '验证码标识不合法' })
  }

  const api = createApiClient(event)

  // 后端返回 { code, message, result: "data:image/jpg;base64,...", success, timestamp }
  // createApiClient 的响应拦截器已经自动把 result 解出来了，
  // 所以这里直接拿到的就是 base64 图片字符串
  const imageBase64 = await api<string>(`/sys/randomImage/${key}`)

  return { image: imageBase64 }
})
