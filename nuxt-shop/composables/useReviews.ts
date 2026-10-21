// composables/useReviews.ts
//
// 店铺端商品评价（BFF 层 /api/review*）。
//
// 注意：Nuxt 4 的 srcDir 是 app/，本项目根目录下的 composables/ 不会被自动导入
// （useFavorites / useProducts 也都是显式 import 的），所以要这样用：
//   import { useProductReviews, useOrderReviews } from '~~/composables/useReviews'
//
// 设计取舍（与 useFavorites 同一套理由）：
//   1. **汇总与列表同源**：都来自 /api/review 一次返回，页面上的「4.5 分」和
//      「12 条评价」不会各算一套；
//   2. **评价区对游客可见**：这两个读接口不需要登录，未登录时也照常请求；
//   3. **写操作（提交评价）只用服务端返回的汇总覆盖本地状态**，不做"自己 +1 条"的心算；
//   4. **locale / 登录态在 setup 阶段取一次**（它们会在用户点击后才用到，
//      那时再调 useI18n() 属于脱离 setup 上下文，见 useFavorites 的注释）。

import type {
  StoreReviewOverview,
  StoreReviewPage,
  StoreReviewSubmitResult,
  StoreReviewSummary,
  StoreReviewableItem,
} from '~~/types/shop-review'
import { normalizeReviewPage, normalizeReviewSubmitResult, normalizeReviewableItems } from '~~/types/shop-review'
import { useAuth } from '~~/composables/useAuth'
import { readErrorMessage } from '~~/composables/useApiError'
import { useLatestRequest } from '~~/composables/useLatestRequest'

/** 详情页每页评价条数（10 条一屏，避免首屏 DOM 过重） */
export const STORE_REVIEW_PAGE_SIZE = 10

/** 把 $fetch 抛出的错误转成可读文案（实现收敛在 composables/useApiError.ts） */
export function extractReviewErrorMessage(error: unknown, fallback: string): string {
  return readErrorMessage(error, fallback)
}

/**
 * 某个商品的评价（详情节用）：汇总 + 分页列表。
 *
 * - `refresh()` 拉第一页（SSR 阶段也会跑到，首屏就有真实评价）；
 * - `loadMore()` 追加下一页（翻页只影响列表，不改汇总）；
 * - 任一请求失败都按"没有评价"降级，评价区自然不渲染 —— 不让它拖垮整个商品页。
 */
export function useProductReviews(productId: MaybeRefOrGetter<string | undefined>) {
  const { locale } = useI18n()
  const requestFetch = useRequestFetch()
  const guard = useLatestRequest()

  const summary = ref<StoreReviewSummary | null>(null)
  const records = ref<StoreReviewPage['records']>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pages = ref(0)
  const loading = ref(false)
  const loadingMore = ref(false)
  const loaded = ref(false)
  const error = ref('')

  const hasMore = computed(() => pageNo.value < pages.value)
  /** 有真实评价才渲染评价区（0 条 → 整块不出现，见项目"0 值不渲染"约定） */
  const hasReviews = computed(() => total.value > 0)

  function applyOverview(overview: StoreReviewOverview) {
    summary.value = overview.summary
    records.value = overview.page.records
    total.value = overview.page.total
    pageNo.value = overview.page.current
    pages.value = overview.page.pages
  }

  /** 拉某页；第一页时替换列表，其余页追加 */
  async function fetchPage(targetPage: number, append: boolean) {
    const id = toValue(productId)
    if (!id) {
      records.value = []
      total.value = 0
      summary.value = null
      loaded.value = true
      return
    }
    const isLatest = guard.begin()
    if (append) {
      loadingMore.value = true
    } else {
      loading.value = true
    }
    error.value = ''
    try {
      const overview = await requestFetch<StoreReviewOverview>('/api/review', {
        query: { productId: id, pageNo: targetPage, pageSize: STORE_REVIEW_PAGE_SIZE, lang: locale.value },
      })
      if (!isLatest()) return
      if (append) {
        // 追加时只拼列表：summary / total 以服务端最新返回为准（可能刚有人评价过）
        summary.value = overview.summary
        total.value = overview.page.total
        pageNo.value = overview.page.current
        pages.value = overview.page.pages
        records.value = [...records.value, ...overview.page.records]
      } else {
        applyOverview(overview)
      }
    } catch (err) {
      if (!isLatest()) return
      error.value = extractReviewErrorMessage(err, '')
      if (!append) {
        summary.value = null
        records.value = []
        total.value = 0
        pages.value = 0
      }
    } finally {
      if (isLatest()) {
        loading.value = false
        loadingMore.value = false
        loaded.value = true
      }
    }
  }

  /** 重新拉第一页（商品切换 / 提交评价后调用） */
  async function refresh() {
    await fetchPage(1, false)
  }

  /** 加载更多（追加下一页） */
  async function loadMore() {
    if (!hasMore.value || loading.value || loadingMore.value) return
    await fetchPage(pageNo.value + 1, true)
  }

  return { summary, records, total, pageNo, pages, loading, loadingMore, loaded, error, hasMore, hasReviews, refresh, loadMore }
}

/**
 * 某笔订单的可评价明细（订单详情页用）。
 *
 * 未登录不发请求（必然 401，还会在控制台留下噪音），直接把列表置空，
 * 页面据此不渲染「去评价」入口。
 */
export function useOrderReviews(orderNo: MaybeRefOrGetter<string | undefined>) {
  const { locale } = useI18n()
  const { isLoggedIn } = useAuth()
  const requestFetch = useRequestFetch()

  const items = ref<StoreReviewableItem[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const submitting = ref(false)
  const error = ref('')

  /** 有"还没评价"的行才需要显示入口 */
  const hasPending = computed(() => items.value.some(item => !item.reviewed))

  async function load() {
    const no = toValue(orderNo)
    if (!no || !isLoggedIn.value) {
      items.value = []
      loaded.value = true
      return
    }
    loading.value = true
    error.value = ''
    try {
      const raw = await requestFetch<unknown>('/api/review/pending', {
        query: { orderNo: no, lang: locale.value },
      })
      items.value = normalizeReviewableItems(raw)
    } catch (err) {
      error.value = extractReviewErrorMessage(err, '')
      items.value = []
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  /**
   * 提交一条评价。
   * 成功后只把**这一行**标记为已评价（不做整页重拉：订单页重拉代价大，
   * 而且用户刚写的内容就在眼前，页面跳一下反而像丢了）。
   * 抛出的错误交给调用方展示（后端 message 已经是可读中文）。
   */
  async function submit(payload: {
    orderItemId: string
    rating: number
    content: string
    anonymous: boolean
  }): Promise<StoreReviewSubmitResult> {
    submitting.value = true
    try {
      const result = normalizeReviewSubmitResult(await requestFetch<unknown>('/api/review/submit', {
        method: 'POST',
        body: { ...payload, lang: locale.value },
      }))
      items.value = items.value.map(item =>
        item.orderItemId === payload.orderItemId ? { ...item, reviewed: true } : item,
      )
      return result
    } finally {
      submitting.value = false
    }
  }

  return { items, loading, loaded, submitting, error, hasPending, load, submit }
}

/** 供页面/组件复用的分页归一化（订单页重新拉取时可能用到） */
export { normalizeReviewPage }
