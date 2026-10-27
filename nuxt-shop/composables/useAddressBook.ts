// composables/useAddressBook.ts
//
// 收货地址的数据层：加载 + 增删改 + 设为默认。
//
// 为什么抽出来：
//   · 个人中心的「收货地址」面板与 /address 整页做的是同一件事（同一批接口、同一套字段口径）；
//   · 面板要从 profile.vue 拆成子组件，状态必须能跨组件共享 —— 用 useState 挂到全局 SSR 状态，
//     页面与面板各自调用本 composable 就是同一份数据。
//
// 边界：这里只管**数据与接口**；表单字段、弹层开关、必填校验属于 UI，留在组件里。
//
// ⚠️ 登录失效（401）不在这里"就地报错"：
// 地址簿的每个接口都要求登录，401 只有一个含义 —— token 已经不管用了。
// 这时正确的动作是"清掉前端登录态 + 引导重新登录"（composables/useSessionExpiry.ts，
// 以及 app/plugins/api-session-guard.client.ts 的全局兜底），而不是给用户弹一条
// 「新增地址失败：未登录或登录已过期」然后把他留在"看起来还登录着"的页面上。
// 以前就是这个毛病：用户 token 失效后仍能进个人中心/地址管理，新增地址时才看到一句
// 红字报错，右上角却一直显示已登录。

import type { AddressPayload, AddressVO } from '~~/types/shop-address'
import { isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'
import { useSessionExpiry } from '~~/composables/useSessionExpiry'

export const useAddressBook = () => {
  const { t, locale } = useI18n()
  const toast = useToast()
  const { handleSessionExpired } = useSessionExpiry()

  const addresses = useState<AddressVO[]>('profile-addresses', () => [])
  const loading = useState<boolean>('profile-addresses-loading', () => false)
  const error = useState<string>('profile-addresses-error', () => '')
  /**
   * 是否已经跑过一次加载（成功/401/失败都算）。
   *
   * 与 useOrderList 里的 loaded 同理：面板必须能区分"还没加载过"和"加载完了就是空的"，
   * 否则 SSR 会渲染空态、客户端 hydrate 时渲染骨架，直接触发 hydration mismatch。
   */
  const loaded = useState<boolean>('profile-addresses-loaded', () => false)
  /** 设为默认 / 删除等写操作进行中：统一禁用各行的按钮，防重复提交 */
  const mutating = useState<boolean>('profile-addresses-mutating', () => false)

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const list = await $fetch<AddressVO[]>('/api/address/list', { query: { lang: locale.value } })
      // 没有 id 的记录无法编辑/删除，直接过滤掉，避免渲染出点了就报错的按钮
      addresses.value = (Array.isArray(list) ? list : []).filter(item => !!item?.id)
    } catch (err) {
      if (isUnauthorizedError(err)) {
        // 交给统一处理：清登录态 + 提示 + 受保护页跳登录。
        // 这里不再把错误写进 error.value —— 页面马上要跳走了，写进去只会闪一下。
        addresses.value = []
        handleSessionExpired({ source: 'address-list' })
        return
      }
      error.value = readErrorMessage(err, t('address.loadErrorDesc'))
    } finally {
      loading.value = false
      // 401 分支提前 return 也会经过这里：一律标记"加载过了"，避免面板永远停在骨架上
      loaded.value = true
    }
  }

  function notifyError(err: unknown) {
    toast.add({
      title: t('address.actionFailed'),
      description: readErrorMessage(err, t('address.loadErrorDesc')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  }

  /**
   * 401 的收口：统一处理并告诉调用方"已经管过了，别再弹错误提示"。
   * 返回 true 表示这条错误是登录失效，调用方应直接结束当前动作。
   */
  function handleUnauthorized(err: unknown, source: string): boolean {
    if (!isUnauthorizedError(err)) return false
    handleSessionExpired({ source })
    return true
  }

  /**
   * 拉单条详情。编辑弹层用它覆盖列表数据 ——
   * 用户在别的标签页改过这条地址时，看到的是改后的值；拉不到就由调用方沿用列表数据。
   */
  async function fetchOne(id: string): Promise<AddressVO | null> {
    try {
      const fresh = await $fetch<AddressVO>(`/api/address/${encodeURIComponent(id)}`, {
        query: { lang: locale.value },
      })
      return fresh?.id ? fresh : null
    } catch (err) {
      // 401 不能静默：列表数据还在屏幕上，用户会以为"编辑弹层只是没刷新"，
      // 后续提交必然失败。交给统一处理把人引导去重新登录。
      // 其余错误静默降级：列表数据已经够用，这里不再弹一次错误。
      handleUnauthorized(err, 'address-detail')
      return null
    }
  }

  /** 新增（id 为空）或编辑（id 非空） */
  async function save(id: string, payload: AddressPayload) {
    try {
      if (id) {
        await $fetch('/api/address/edit', { method: 'POST', body: { ...payload, id } })
      } else {
        await $fetch('/api/address/add', { method: 'POST', body: payload })
      }
    } catch (err) {
      // 登录失效：由统一处理负责提示与跳转，这里**不弹**「新增地址失败」——
      // 那句话会把"你的登录过期了"说成"这个地址保存有问题"，误导用户反复重试。
      if (handleUnauthorized(err, id ? 'address-edit' : 'address-add')) return
      throw err
    }
    toast.add({ title: t('address.savedToast'), color: 'success', icon: 'lucide:check' })
    await load()
  }

  async function setDefault(id: string) {
    if (mutating.value) return
    mutating.value = true
    try {
      // id 走查询参数（后端方法签名没有 @RequestBody）
      await $fetch('/api/address/set-default', {
        method: 'POST',
        query: { id, lang: locale.value },
      })
      toast.add({ title: t('address.defaultSetToast'), color: 'success', icon: 'lucide:check' })
      await load()
    } catch (err) {
      if (!handleUnauthorized(err, 'address-set-default')) notifyError(err)
    } finally {
      mutating.value = false
    }
  }

  async function remove(id: string) {
    if (mutating.value) return
    mutating.value = true
    try {
      await $fetch('/api/address/delete', {
        method: 'POST',
        query: { id, lang: locale.value },
      })
      toast.add({ title: t('address.deletedToast'), color: 'success', icon: 'lucide:check' })
      await load()
    } catch (err) {
      if (!handleUnauthorized(err, 'address-delete')) notifyError(err)
    } finally {
      mutating.value = false
    }
  }

  return { addresses, loading, loaded, error, mutating, load, fetchOne, save, setDefault, remove, notifyError }
}
