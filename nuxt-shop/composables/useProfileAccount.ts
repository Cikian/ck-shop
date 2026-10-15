// composables/useProfileAccount.ts
//
// 「账户资料 + 是否有密码」的状态层：GET /api/auth/profile。
//
// 为什么单独抽出来：这份数据被两处同时需要
//   · 页面在登录态恢复后**预加载**一次（这样用户切到「账户设置」时标题已经正确，
//     不会先显示"设置密码"再跳成"修改密码"）；
//   · components/profile/SettingsPanel.vue 的密码区块要靠 hasPassword 决定标题与是否渲染旧密码框。
// 用 useState 共享，页面与面板拿到同一份状态，改完密码后标题能立刻同步。

import { isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'

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

export const useProfileAccount = () => {
  const { t, locale } = useI18n()

  /**
   * 三态：true = 已有密码（改密码要验旧密码）、false = 没设过密码（设置密码）、
   * null = 还不知道（资料没拉到/接口失败）→ 模板走"先不渲染密码表单"的保守分支。
   */
  const hasPassword = useState<boolean | null>('profile-has-password', () => null)
  const loading = useState<boolean>('profile-account-loading', () => false)
  const error = useState<string>('profile-account-error', () => '')

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const data = await $fetch<AuthProfileVO>('/api/auth/profile', { query: { lang: locale.value } })
      // 只有后端**明确给了** hasPassword 才认；给不出就当 null（未知），
      // 绝不默认成 false 骗用户去"设置密码"—— 那会给已有密码的用户一个不验旧密码的表单，最终被后端拒绝。
      hasPassword.value = typeof data?.hasPassword === 'boolean' ? data.hasPassword : null
    } catch (err) {
      // 401/403 不弹错：未登录时个人中心整页已是登录引导（auth 中间件 + isLoggedIn 分支）
      if (isUnauthorizedError(err)) return
      error.value = readErrorMessage(err, t('profile.profileLoadFailed'))
    } finally {
      loading.value = false
    }
  }

  return { hasPassword, loading, error, load }
}
