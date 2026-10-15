// composables/useOrderList.ts
//
// 个人中心「我的订单」的数据源 —— 页面与订单面板**共用同一份状态**。
//
// 为什么要有它：订单列表要同时被两处使用
//   · 页面顶部的「订单数」统计卡（必须在登录后立刻显示真实数量）；
//   · 订单面板里的列表 + 分页 + 退款/取消/确认收货后的刷新。
// 之前这些 ref（orders/orderTotal/pageNo/loading/error）是页面私有的，
// 面板一旦拆成子组件就拿不到、也不好用 props 双向同步。
//
// 用 useState 而不是 ref：状态挂在 Nuxt 的全局 SSR 状态里，
// 页面与面板各自调用 useOrderList() 拿到的是**同一份数据**，
// 面板里翻页/确认收货后刷新，统计卡的数字会跟着变 —— 无需 emit 回传。

import type { OrderListVO, OrderPageResult } from '~~/types/shop-order'
import { readErrorMessage, isUnauthorizedError } from '~~/composables/useApiError'
import { toNumberOrNull } from '~~/types/shop-product'

/** 个人中心只展示最近一页，不做状态筛选（要筛选用 /orders 整页） */
export const PROFILE_ORDER_PAGE_SIZE = 10

export const useOrderList = () => {
  const { t, locale } = useI18n()

  const orders = useState<OrderListVO[]>('profile-orders', () => [])
  const total = useState<number>('profile-orders-total', () => 0)
  const pageNo = useState<number>('profile-orders-page', () => 1)
  const loading = useState<boolean>('profile-orders-loading', () => false)
  const error = useState<string>('profile-orders-error', () => '')

  const totalPages = computed(() => {
    const pages = Math.ceil(total.value / PROFILE_ORDER_PAGE_SIZE)
    return pages > 0 ? pages : 1
  })
  const currentPage = computed(() => pageNo.value)

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const page = await $fetch<OrderPageResult<OrderListVO>>('/api/order', {
        query: {
          pageNo: pageNo.value,
          pageSize: PROFILE_ORDER_PAGE_SIZE,
          lang: locale.value,
        },
      })
      orders.value = Array.isArray(page?.records) ? page.records : []
      total.value = toNumberOrNull(page?.total) ?? 0
      // 后端会回传 pageNo 的权威值（比如超出范围时被纠正），以它为准
      const serverPage = toNumberOrNull(page?.current)
      if (serverPage !== null && serverPage >= 1) pageNo.value = Math.trunc(serverPage)
    } catch (err) {
      // 401/403 不在这里弹错：个人中心未登录时整页已经是登录引导（auth 中间件 + isLoggedIn 分支）
      if (isUnauthorizedError(err)) return
      error.value = readErrorMessage(err, t('order.loadErrorDesc'))
    } finally {
      loading.value = false
    }
  }

  function goToPage(target: number) {
    const next = Math.min(Math.max(target, 1), totalPages.value)
    if (next === pageNo.value) return
    pageNo.value = next
    void load()
  }

  return { orders, total, pageNo, loading, error, totalPages, currentPage, load, goToPage }
}
