// composables/useProfileFields.ts
//
// 个人中心的**展示派生字段**：侧栏资料卡与「个人信息」面板共用同一套派生逻辑。
//
// 为什么抽出来：这些 computed 原先都在 pages/profile.vue 里，而它们同时被两处使用
//   · 左侧资料卡：头像首字母、昵称、注册时间、联系方式；
//   · 「个人信息」面板：把同样的字段列成 dl 明细（infoRows）。
// 面板拆成组件后，两处都要用 —— 与其复制一份派生逻辑（字段口径会漂移），
// 不如都从这里取。数据源是 useAuth() 的 userInfo（全局 useState）。

import { useAuth } from '~~/composables/useAuth'
import { formatDate } from '~~/composables/useDateTimeFormat'

/** 一行展示项：i18n key + 值 + 图标（模板里 t(row.label) 渲染） */
export interface ProfileDisplayRow {
  label: string
  value: string
  icon: string
}

export const useProfileFields = () => {
  const { locale } = useI18n()
  const { userInfo } = useAuth()

  const displayName = computed(() => userInfo.value?.realname || userInfo.value?.username || '')

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

  /** 个人信息面板：只放真实有值的字段，空字段整行不渲染 */
  const infoRows = computed<ProfileDisplayRow[]>(() => {
    const rows: ProfileDisplayRow[] = []
    if (userInfo.value?.realname) {
      rows.push({ label: 'profile.realname', value: String(userInfo.value.realname), icon: 'lucide:user-round' })
    }
    if (userInfo.value?.username) {
      rows.push({ label: 'profile.username', value: String(userInfo.value.username), icon: 'lucide:at-sign' })
    }
    rows.push(...contactRows.value)
    if (memberSinceText.value) {
      rows.push({ label: 'profile.memberSince', value: memberSinceText.value, icon: 'lucide:calendar' })
    }
    return rows
  })

  return { displayName, avatarInitial, contactRows, memberSinceText, infoRows }
}
