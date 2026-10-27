// composables/useProfileFields.ts
//
// 个人中心的**展示派生字段**：资料卡（hero）与账户资料面板共用同一套派生逻辑。
//
// 为什么抽出来：这些 computed 原先都在 pages/profile.vue 里，而它们同时被两处使用
//   · 资料卡：头像首字母、昵称、注册时间、联系方式；
//   · 账户资料面板 / 顶栏：同样的字段。
// 与其复制一份派生逻辑（字段口径会漂移），不如都从这里取。
// 数据源是 useAuth() 的 userInfo（全局 useState）。
//
// ⚠️ 这里**刻意不再派生 username**（`u_xxxxxxxx`）：
//   店铺端的用户名是注册时系统生成的内部标识，用户既不能用它登录、也改不了它；
//   把它摆在个人中心只会让人误以为那是个"账号名"。全站不再展示它。

import { useAuth } from '~~/composables/useAuth'
import { formatDate } from '~~/composables/useDateTimeFormat'

/** 一行展示项：i18n key + 值 + 图标（模板里 t(row.label) 渲染） */
export interface ProfileDisplayRow {
  label: string
  value: string
  icon: string
}

export const useProfileFields = () => {
  const { t, locale } = useI18n()
  const { userInfo } = useAuth()

  /**
   * 昵称：优先用户自己设的 realname；没设过时退回邮箱前缀（比显示"用户"更可辨认），
   * 都没有才用一句通用称呼。**绝不回退到 username**。
   */
  const displayName = computed(() => {
    const realname = (userInfo.value?.realname || '').trim()
    if (realname) return realname
    const email = String(userInfo.value?.email || '')
    const prefix = email.includes('@') ? email.slice(0, email.indexOf('@')) : ''
    return prefix || t('profile.unnamedUser')
  })

  /** 头像兜底：没有头像图时用姓名首字母（中文取第一个字） */
  const avatarInitial = computed(() => {
    const source = displayName.value.trim()
    return source ? source.charAt(0).toUpperCase() : ''
  })

  const contactRows = computed<ProfileDisplayRow[]>(() => {
    const rows: ProfileDisplayRow[] = []
    if (userInfo.value?.email) {
      rows.push({ label: 'profile.email', value: String(userInfo.value.email), icon: 'lucide:mail' })
    }
    if (userInfo.value?.phone) {
      rows.push({ label: 'profile.phone', value: String(userInfo.value.phone), icon: 'lucide:phone' })
    }
    return rows
  })

  /**
   * 注册时间：userInfo.createTime 是后端 SysUser 真实字段。
   * 后端可能给 "2024-01-15 10:20:30"，也可能是 ISO，这里两种都能解析；
   * 真的拿不到就返回空串 -> 整块不渲染（绝不写死一个日期）。
   */
  const memberSinceText = computed(() => {
    const raw = userInfo.value?.createTime
    if (!raw) return ''
    // 统一走 composables/useDateTimeFormat.ts：语言码要经 BCP-47 映射（zh -> zh-CN），
    // 解析规则也要与订单页保持一致（后端给的是 "yyyy-MM-dd HH:mm:ss"）。
    return formatDate(raw, locale.value)
  })

  return { displayName, avatarInitial, contactRows, memberSinceText }
}
