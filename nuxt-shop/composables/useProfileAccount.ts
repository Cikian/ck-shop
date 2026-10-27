// composables/useProfileAccount.ts
//
// 「账户资料 + 是否有密码」的状态层：GET /api/auth/profile，以及
// 「保存资料 / 换头像」三个写接口（BFF：/api/auth/profile/update、/api/auth/avatar、/api/auth/avatar/upload）。
//
// 为什么单独抽出来：这份数据被两处同时需要
//   · 页面在登录态恢复后**预加载**一次（这样用户切到「账户设置」时标题已经正确，
//     不会先显示"设置密码"再跳成"修改密码"）；
//   · components/profile/SettingsPanel.vue 的密码区块要靠 hasPassword 决定标题与是否渲染旧密码框。
// 用 useState 共享，页面与面板拿到同一份状态，改完密码后标题能立刻同步。
//
// 写接口的**统一收口点**也在这里：
//   · 三个写接口都返回**最新的完整资料**（后端 update 后回读一次），
//     所以成功后调用 updateUserInfo(...) 就能让顶栏、资料卡、下拉菜单同步更新；
//   · 错误一律向调用方抛出（由组件决定 toast 文案）——
//     这里如果用 toast 自己吞掉，组件就没法区分"手机号被占用"与"网络断了"。

import { isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'
import { useAuth } from '~~/composables/useAuth'

/** /api/auth/profile 的出参（只列本页真正用到的字段） */
export interface AuthProfileVO {
  id?: string | null
  username?: string | null
  realname?: string | null
  email?: string | null
  avatar?: string | null
  phone?: string | null
  hasPassword?: boolean | null
  registerStoreCode?: string | null
  createTime?: string | null
}

/** 可编辑的资料字段（邮箱是登录标识、username 不可改，都不在这里） */
export interface ProfileEditableFields {
  realname: string
  phone: string
}

export const useProfileAccount = () => {
  const { t, locale } = useI18n()
  const { updateUserInfo } = useAuth()

  /**
   * 三态：true = 已有密码（改密码要验旧密码）、false = 没设过密码（设置密码）、
   * null = 还不知道（资料没拉到/接口失败）→ 模板走"先不渲染密码表单"的保守分支。
   */
  const hasPassword = useState<boolean | null>('profile-has-password', () => null)
  const loading = useState<boolean>('profile-account-loading', () => false)
  const error = useState<string>('profile-account-error', () => '')

  /** 写操作进行中（三个写接口共用，防重复提交） */
  const saving = useState<boolean>('profile-account-saving', () => false)

  /** 把后端回读的最新资料同步进共享的 userInfo */
  function applyProfile(data: AuthProfileVO | null | undefined) {
    if (!data) return
    const patch: Record<string, unknown> = {}
    if (typeof data.realname === 'string') patch.realname = data.realname
    if (typeof data.phone === 'string') patch.phone = data.phone
    if (typeof data.email === 'string') patch.email = data.email
    // 头像要用"字段是否存在"判断，不能只看类型：
    // 恢复默认头像时后端回的是 avatar = null，若按"是字符串才写"就会被跳过，
    // 结果点了"恢复默认"、界面还挂着旧头像 —— 看起来像没生效。
    if ('avatar' in data) patch.avatar = data.avatar ?? ''
    if (Object.keys(patch).length) updateUserInfo(patch as any)
  }

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const data = await $fetch<AuthProfileVO>('/api/auth/profile', { query: { lang: locale.value } })
      // 只有后端**明确给了** hasPassword 才认；给不出就当 null（未知），
      // 绝不默认成 false 骗用户去"设置密码"—— 那会给已有密码的用户一个不验旧密码的表单，最终被后端拒绝。
      hasPassword.value = typeof data?.hasPassword === 'boolean' ? data.hasPassword : null
      // 顺带把资料同步一次：换设备登录时 localStorage 里的缓存可能早于服务端
      applyProfile(data)
    } catch (err) {
      // 401/403 不弹错：未登录时个人中心整页已是登录引导（auth 中间件 + isLoggedIn 分支）
      if (isUnauthorizedError(err)) return
      error.value = readErrorMessage(err, t('profile.profileLoadFailed'))
    } finally {
      loading.value = false
    }
  }

  /**
   * 保存昵称 / 手机号（真实落库）。
   *
   * 失败一律向上抛：手机号被占用、格式不合法这类错误必须让用户看到后端原话，
   * 而不是被这里统一换成一句"保存失败"。
   */
  async function saveProfile(payload: ProfileEditableFields) {
    saving.value = true
    try {
      const data = await $fetch<AuthProfileVO>('/api/auth/profile/update', {
        method: 'POST',
        query: { lang: locale.value },
        body: { realname: payload.realname, phone: payload.phone },
      })
      applyProfile(data)
      return data
    } finally {
      saving.value = false
    }
  }

  /** 选择预设头像 / 恢复默认（传空串） */
  async function selectAvatar(avatar: string) {
    saving.value = true
    try {
      const data = await $fetch<AuthProfileVO>('/api/auth/avatar', {
        method: 'POST',
        query: { lang: locale.value },
        body: { avatar },
      })
      applyProfile(data)
      return data
    } finally {
      saving.value = false
    }
  }

  /** 上传自定义头像（BFF 会用 sharp 裁成 512×512 webp 再转给后端） */
  async function uploadAvatar(file: File) {
    saving.value = true
    try {
      const form = new FormData()
      form.append('file', file, file.name || 'avatar')
      const data = await $fetch<AuthProfileVO>('/api/auth/avatar/upload', {
        method: 'POST',
        query: { lang: locale.value },
        body: form,
      })
      applyProfile(data)
      return data
    } finally {
      saving.value = false
    }
  }

  return {
    hasPassword,
    loading,
    error,
    saving,
    load,
    saveProfile,
    selectAvatar,
    uploadAvatar,
  }
}
