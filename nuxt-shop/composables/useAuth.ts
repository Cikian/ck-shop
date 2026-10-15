// composables/useAuth.ts
//
// 注意这版和之前 demo 里的最大区别：
// - token 完全不在前端出现（存在 httpOnly cookie，JS 拿不到，也不需要拿）
// - 前端只保存 userInfo（不敏感），用来在页面上显示用户名等信息
// - 每次请求需要登录态的接口时，浏览器会自动带上 cookie（这是 cookie 的天然特性，
//   不需要你手动在请求头里拼 token），server/api 收到请求后自己从 cookie 里读出来，
//   再转发给 Java 后端时加上 x-access-token 请求头
//
// 关于"登录过期"：localStorage 里的 user-info 只是一份用于显示的缓存，
// 它不会因为 cookie 过期而消失，所以**不能**用它判断"是否还登录着"。
// 真正的判定入口是 refreshSession()，它去问服务端（见 server/api/auth/session.get.ts）。
//
// 契约来自 docs/店铺端账号体系改造-接口契约.md 第 3、4 节。
// 店铺端**没有用户名登录**：旧的 login({ username, password, captcha, checkKey })
// 已在本次改造中删除（全仓库唯一调用点 app/pages/login.vue 已重做）。
// 现在只有三条登录路径：邮箱密码 / 邮箱验证码 / Google OAuth。
// 用 #server 而不是 ~：Nuxt 4 下 srcDir 是 app/，`~/*` 指向 app/*，
// 拿 server 目录里的类型必须走 #server（Nitro 提供的别名，指向 <rootDir>/server）。
import type { AuthSession } from '#server/api/auth/session.get'

interface UserInfo {
  realname: string
  username: string
  avatar?: string
  email?: string
  [key: string]: any
}

/** 邮箱验证码场景：注册 / 登录 / 重置密码 */
export type EmailCodeScene = 'register' | 'login' | 'reset'

/** 三条登录路径都会返回的形状 */
interface AuthResult {
  userInfo: UserInfo
  isNewUser?: boolean
  storeCode?: string
  [key: string]: any
}

/**
 * BFF 错误里抽出"最像人话"的一句。
 *
 * 优先级：statusMessage > data.message > message。
 * 为什么不是 message 优先：ofetch 把非 2xx 的 `message` 固定成
 * "400 Bad Request" 这类 HTTP 短语，真正的业务文案在后端 body 里。
 */
export const resolveAuthErrorMessage = (error: any): string => {
  return (
    error?.data?.statusMessage
    || error?.data?.message
    || error?.statusMessage
    || error?.message
    || ''
  )
}

export const useAuth = () => {
  const userInfo = useState<UserInfo | null>('user-info', () => null)

  // 是否已经完成过**一次服务端**登录态校验。
  // 必须用 useState 而不是 ref：SSR 渲染出来的首屏和客户端 hydrate 时状态要一致，
  // ref 在服务端和客户端是两份互不相干的值，会造成 hydration 不匹配。
  const authChecked = useState<boolean>('auth-checked', () => false)

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

  const persistUser = (info: UserInfo | null) => {
    userInfo.value = info
    if (import.meta.client) {
      if (info) {
        localStorage.setItem('user-info', JSON.stringify(info))
      } else {
        localStorage.removeItem('user-info')
      }
    }
  }

  // ★ 登录态的唯一权威判定：问服务端（服务端才看得到 httpOnly cookie 里的 token）。
  //
  // 存在的意义：localStorage 里的 user-info 只是缓存，cookie 过期后它依然在，
  // 光看它会把"已过期"当成"已登录"。所以任何"这个用户到底还登录着吗"的判断，
  // 都必须走这里。
  //
  // 返回值语义：true = 服务端确认已登录；false = 未登录/已过期/校验不出来。
  // 无论真假，userInfo 与 localStorage 都会跟着对齐，避免出现
  // "isLoggedIn 为 true 但服务端其实不认" 的不一致状态。
  const refreshSession = async (): Promise<boolean> => {
    try {
      const data = await $fetch<AuthSession>('/api/auth/session')
      if (data?.authenticated && data.userInfo) {
        persistUser(data.userInfo as UserInfo)
        return true
      }
      persistUser(null)
      return false
    } catch {
      // /api/auth/session 契约上永远返回 200，走到这里说明是网络异常（如用户断网）。
      // 无法确认登录态时保守地当作未登录，并清掉本地缓存 —— 缓存留着只会继续误导页面。
      persistUser(null)
      return false
    } finally {
      // 放在 finally：无论成功失败都算"校验过了"。
      // 否则网络异常时 authChecked 永远为 false，中间件会一直卡在等待校验的状态。
      authChecked.value = true
    }
  }

  /**
   * 所有"登录成功后"的统一收尾。
   *
   * 三个登录入口（密码 / 邮箱验证码 / Google）在服务端做的事完全一样：
   * BFF 已经写好了 httpOnly cookie，这里只需要把 userInfo 落进前端状态。
   * 抽出来是为了防止三处各写一遍后漏掉 authChecked —— 漏了会让中间件再白问一次服务端。
   */
  const acceptAuthResult = (data: AuthResult | null | undefined): AuthResult => {
    if (data?.userInfo) {
      persistUser(data.userInfo)
    }
    // 登录接口本身就是一次服务端认证，此刻登录态已被服务端确认，不用再打一次 /api/auth/session。
    authChecked.value = true
    return data as AuthResult
  }

  /**
   * 发送邮箱验证码（注册 / 登录 / 重置密码共用）。
   *
   * 为什么带图形验证码：契约第 0 节决策 8 —— 图形验证码只在**发送邮箱验证码**时校验，
   * 登录本身不再需要它。这是防止有人拿脚本刷邮件接口的最低成本手段。
   * 图形验证码只能由后端校验，所以 captcha + checkKey 必须原样透传。
   */
  const sendEmailCode = async (params: {
    email: string
    scene: EmailCodeScene
    captcha: string
    checkKey: string
  }) => {
    return await $fetch<{ cooldownSeconds: number }>('/api/auth/send-code', {
      method: 'POST',
      body: params,
    })
  }

  /** 注册：成功后后端直接签发 token（免二次登录），BFF 已写入 cookie */
  const register = async (params: {
    email: string
    emailCode: string
    password: string
    confirmPassword: string
  }) => {
    const data = await $fetch<AuthResult>('/api/auth/register', {
      method: 'POST',
      body: params,
    })
    return acceptAuthResult(data)
  }

  /** 邮箱 + 密码登录 */
  const loginByPassword = async (params: { email: string; password: string }) => {
    const data = await $fetch<AuthResult>('/api/auth/login/password', {
      method: 'POST',
      body: params,
    })
    return acceptAuthResult(data)
  }

  /**
   * 邮箱 + 邮箱验证码登录。
   *
   * ⚠️ 后端对**未注册**邮箱会直接自动注册并登录（契约 2.4），
   * 所以这个方法既可能"登录"也可能"注册"，调用方不要假设用户一定已存在。
   */
  const loginByEmailCode = async (params: { email: string; emailCode: string }) => {
    const data = await $fetch<AuthResult>('/api/auth/login/email-code', {
      method: 'POST',
      body: params,
    })
    return acceptAuthResult(data)
  }

  /**
   * 取 Google 授权地址。
   *
   * ⚠️ `redirectUri` 要传**站内相对路径**（如 `/zh/auth/google/callback`），不要传绝对 URL：
   * BFF 的 google-auth-url.get.ts 有开放重定向防护（toSafeRelativePath），
   * 只接受以单个 `/` 开头的站内路径，origin 由服务端自己从当前请求推导。
   * 传 `https://host/...` 会被判为绝对地址并**丢弃**，回退到默认回调路径。
   *
   * 后端**未配置 google.client-id/secret** 时会返回业务错误，前端据此禁用 Google 按钮
   * （契约 2.5 最后一条）。所以调用方必须 try/catch，不能假设它总会成功。
   *
   * ⚠️ `lang` 由**调用方**传入，而不是在这里调 `useI18n()` 现取：
   * `useI18n()` 是 Vue composable，只能在组件 `setup()` 顶层同步调用。
   * 而 `useAuth()` 还会被 **Nuxt 插件**（app/plugins/init-auth.client.ts）调用，
   * 那里没有组件实例，调 `useI18n()` 会直接抛
   * "Must be called at the top of a `setup` function"，整个应用初始化失败（用户实测踩到过）。
   * 所以本 composable 必须保持**零 i18n 依赖**，语言一律靠参数传入。
   */
  const fetchGoogleAuthUrl = async (redirectUri: string, lang?: string) => {
    return await $fetch<{ authUrl: string; state: string; redirectUri: string }>(
      '/api/auth/google/auth-url',
      {
        method: 'GET',
        // lang 必须一起传：BFF 用它 (a) 把站内相对路径补成带语言前缀的绝对地址、
        // (b) 兜底路径也按同一语言生成。漏传会回退 'en'，与前端传入的 /zh/... 不一致。
        query: { redirectUri, lang },
      },
    )
  }

  /**
   * Google 回调换登录态。
   *
   * 只传 code / state / lang：
   *   - redirectUri 由 BFF 按当前站点 origin + **语言前缀**自行拼接，
   *     不接受前端传入（契约 3.3），避免被改成攻击者可控的回调地址；
   *   - ⚠️ lang **必须传**：BFF 要用它拼出与授权阶段**逐字相同**的 redirectUri。
   *     Google 会逐字节比对两者，不一致就报 `redirect_uri_mismatch`（400）；
   *     漏传时 BFF 回退到 'en'，于是"用中文登录也会被拼成 /en/..." —— 必然报错。
   *     这也是"无论用哪个语言登录都回到中文/英文"的根因之一。
   * 语言从当前 UI 上下文取，不硬编码。
   */
  const loginByGoogle = async (params: { code: string; state: string; lang?: string }) => {
    const data = await $fetch<AuthResult>('/api/auth/google/callback', {
      method: 'POST',
      body: {
        code: params.code,
        state: params.state,
        // lang 由调用方（回调页）传入，见上面 fetchGoogleAuthUrl 的说明
        lang: params.lang,
      },
    })
    return acceptAuthResult(data)
  }

  /** 忘记密码：用 scene=reset 的邮箱验证码换一次密码重置 */
  const resetPassword = async (params: {
    email: string
    emailCode: string
    newPassword: string
    confirmPassword: string
  }) => {
    return await $fetch<{ success: boolean }>('/api/auth/reset-password', {
      method: 'POST',
      body: params,
    })
  }

  /**
   * 已登录状态下设置 / 修改密码。
   *
   * oldPassword 可空：Google 注册或邮箱验证码首次登录自动建的账号本来就没有密码
   * （后端用 hasPassword 表达，见契约 2.8），首次设置时 oldPassword 传空即可。
   */
  const changePassword = async (params: {
    oldPassword?: string
    newPassword: string
    confirmPassword: string
  }) => {
    return await $fetch<{ success: boolean }>('/api/auth/change-password', {
      method: 'POST',
      body: params,
    })
  }

  const logout = async () => {
    await $fetch('/api/auth/logout', { method: 'POST' })
    persistUser(null)
    // 已明确登出，同样算校验完成：中间件不该因为 authChecked 还是 false 而再去问服务端。
    authChecked.value = true
  }

  const isLoggedIn = computed(() => !!userInfo.value)

  return {
    // 既有导出（契约 4.1 要求全部保留）
    userInfo,
    isLoggedIn,
    authChecked,
    logout,
    restoreUser,
    refreshSession,
    // 本次新增
    sendEmailCode,
    register,
    loginByPassword,
    loginByEmailCode,
    fetchGoogleAuthUrl,
    loginByGoogle,
    resetPassword,
    changePassword,
  }
}
