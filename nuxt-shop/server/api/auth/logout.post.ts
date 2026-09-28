// server/api/auth/logout.post.ts
export default defineEventHandler(async (event) => {
  // 如果后端有专门的登出接口（让 token 失效），在这里也调用一下：
  // const api = createApiClient(event, { withAuth: true })
  // await api('/sys/logout', { method: 'POST' })

  deleteCookie(event, 'access-token', { path: '/' })

  return { success: true }
})
