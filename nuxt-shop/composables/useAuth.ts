// composables/useAuth.ts
//
// 注意这版和之前 demo 里的最大区别：
// - token 完全不在前端出现（存在 httpOnly cookie，JS 拿不到，也不需要拿）
// - 前端只保存 userInfo（不敏感），用来在页面上显示用户名等信息
// - 每次请求需要登录态的接口时，浏览器会自动带上 cookie（这是 cookie 的天然特性，
//   不需要你手动在请求头里拼 token），server/api 收到请求后自己从 cookie 里读出来，
//   再转发给 Java 后端时加上 x-access-token 请求头

interface UserInfo {
  realname: string
  username: string
  avatar?: string
  email?: string
  [key: string]: any
}

export const useAuth = () => {
  const userInfo = useState<UserInfo | null>('user-info', () => null)

  // 页面刷新后，从 localStorage 恢复用户信息（只是用来"回显"，不代表登录态本身，
  // 登录态真正的凭证 token 在 httpOnly cookie 里，浏览器请求时会自动带上）
  const restoreUser = () => {
    if (import.meta.client && !userInfo.value) {
      const cached = localStorage.getItem('user-info')
      if (cached) {
        try {
          userInfo.value = JSON.parse(cached)
        } catch {
          localStorage.removeItem('user-info')
        }
      }
    }
  }

  const login = async (params: {
    username: string
    password: string
    captcha: string
    checkKey: string
  }) => {
    const data = await $fetch<{ userInfo: UserInfo; tenantList: any[] }>(
      '/api/auth/login',
      {
        method: 'POST',
        body: params,
      }
    )
    userInfo.value = data.userInfo
    if (import.meta.client) {
      localStorage.setItem('user-info', JSON.stringify(data.userInfo))
    }
    return data
  }

  const logout = async () => {
    await $fetch('/api/auth/logout', { method: 'POST' })
    userInfo.value = null
    if (import.meta.client) {
      localStorage.removeItem('user-info')
    }
  }

  const isLoggedIn = computed(() => !!userInfo.value)

  return { userInfo, isLoggedIn, login, logout, restoreUser }
}
