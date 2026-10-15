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

import type { AddressPayload, AddressVO } from '~~/types/shop-address'
import { isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'

export const useAddressBook = () => {
  const { t, locale } = useI18n()
  const toast = useToast()

  const addresses = useState<AddressVO[]>('profile-addresses', () => [])
  const loading = useState<boolean>('profile-addresses-loading', () => false)
  const error = useState<string>('profile-addresses-error', () => '')
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
      if (isUnauthorizedError(err)) return
      error.value = readErrorMessage(err, t('address.loadErrorDesc'))
    } finally {
      loading.value = false
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
   * 拉单条详情。编辑弹层用它覆盖列表数据 ——
   * 用户在别的标签页改过这条地址时，看到的是改后的值；拉不到就由调用方沿用列表数据。
   */
  async function fetchOne(id: string): Promise<AddressVO | null> {
    try {
      const fresh = await $fetch<AddressVO>(`/api/address/${encodeURIComponent(id)}`, {
        query: { lang: locale.value },
      })
      return fresh?.id ? fresh : null
    } catch {
      // 静默降级：列表数据已经够用，这里不再弹一次错误
      return null
    }
  }

  /** 新增（id 为空）或编辑（id 非空） */
  async function save(id: string, payload: AddressPayload) {
    if (id) {
      await $fetch('/api/address/edit', { method: 'POST', body: { ...payload, id } })
    } else {
      await $fetch('/api/address/add', { method: 'POST', body: payload })
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
      notifyError(err)
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
      notifyError(err)
    } finally {
      mutating.value = false
    }
  }

  return { addresses, loading, error, mutating, load, fetchOne, save, setDefault, remove, notifyError }
}
