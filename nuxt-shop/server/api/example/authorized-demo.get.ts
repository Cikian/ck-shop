// server/api/example/authorized-demo.get.ts
//
// 这个文件是"模板"，展示以后任何需要登录态才能访问的接口该怎么写。
// 用到的时候把文件名、路径、调用的后端接口地址改成你真实的业务接口即可。

export default defineEventHandler(async (event) => {
  // withAuth: true → 内部会自动从 httpOnly cookie 里取 token，
  // 并且自动加上 x-access-token 请求头。
  // 如果 cookie 里没有 token（用户没登录/登录过期），会直接抛 401，
  // 不需要你手动写 if (!token) throw ... 这种判断
  const api = createApiClient(event, { withAuth: true })

  // 假设这是某个需要登录才能访问的后端接口
  return await api('/some/protected/endpoint')
})
