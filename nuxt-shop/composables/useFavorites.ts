// composables/useFavorites.ts
//
// 店铺端收藏（BFF 层 /api/favorite*）。
//
// 注意：Nuxt 4 的 srcDir 是 app/，本项目根目录下的 composables/ 不会被自动导入
// （useAuth / useProducts 也都是显式 import 的），所以要这样用：
//   import { useProductFavorite, useFavoriteList } from '~~/composables/useFavorites'
//
// 设计取舍：
//   1. **收藏态与收藏数一律以服务端返回值覆盖本地状态**，不做"点击后先自己取反"的乐观更新：
//      收藏是有唯一索引约束的写操作，本地猜错了界面就会显示"已收藏"而后端其实没有。
//      toggle 接口直接返回操作后的最终状态，前端照着渲染即可。
//   2. **未登录不请求、不报错**：商品详情页对游客必须能正常看，
//      收藏态降级为"未收藏"，用户点按钮时才跳登录页（与加购同一套交互）。
//   3. **locale / 登录态在 setup 阶段取一次**：这两个动作都是"稍后被用户点击触发"的，
//      那时再调 useI18n() 属于脱离 setup 上下文，容易拿到不可靠的实例
//      （useCart 里踩过同一个坑，注释也在那里）。
//   4. 收藏列表与商品列表共用 StoreProductPage / StoreProductCard 结构，
//      不搞第二套卡片（收藏里的商品和列表里的商品长得一样）。

import type { StoreFavoriteStatus } from '~~/types/shop-favorite'
import type { StoreProductPage } from '~~/types/shop-product'
import { normalizeFavoriteStatus } from '~~/types/shop-favorite'
import { useAuth } from '~~/composables/useAuth'
import { readErrorMessage } from '~~/composables/useApiError'

/**
 * 从 $fetch 抛出的错误里取出可读文案（后端业务异常 message 是中文，直接展示）。
 * 实现收敛到 composables/useApiError.ts —— 原来这份与页面里的 readErrorMessage 逐字相同。
 */
export function extractFavoriteErrorMessage(error: unknown, fallback: string): string {
  return readErrorMessage(error, fallback)
}

/** 收藏列表每页条数（与商品列表保持一致的观感） */
export const STORE_FAVORITE_PAGE_SIZE = 12

/**
 * 某个商品的收藏态 + 收藏数（商品详情页用）。
 *
 * 需要登录态才能拿到"我收藏没有"，所以：
 *   - 未登录：refresh() 直接返回，favorited=false，不打接口、不弹错；
 *   - 已登录：refresh() 拉一次；toggle() 取反并返回操作后的状态。
 */
export function useProductFavorite(productId: MaybeRefOrGetter<string | undefined>) {
  const { locale } = useI18n()
  const { isLoggedIn } = useAuth()

  /** 当前用户是否已收藏；未登录 / 未加载完成时为 false */
  const favorited = ref(false)
  /** 商品收藏总数（后端直出）；null 表示还没拿到数据，此时沿用商品详情里的收藏数 */
  const favoriteCount = ref<number | null>(null)
  /** 收藏请求进行中（按钮转圈 + 防重复点击） */
  const pending = ref(false)

  function applyStatus(status: StoreFavoriteStatus) {
    favorited.value = status.favorited
    favoriteCount.value = status.favoriteCount
  }

  /** 拉取收藏态（未登录时静默跳过：这条接口对游客没有意义） */
  async function refresh() {
    const id = toValue(productId)
    if (!id || !isLoggedIn.value) {
      favorited.value = false
      return
    }
    try {
      const status = await $fetch<unknown>('/api/favorite/status', {
        query: { productId: id, lang: locale.value },
      })
      applyStatus(normalizeFavoriteStatus(status))
    } catch {
      // 收藏态拿不到不该影响商品详情页：保持"未收藏"，用户点击时再暴露真实错误
      favorited.value = false
    }
  }

  /**
   * 收藏 / 取消收藏。
   * 抛出的错误交给调用方（页面）决定怎么提示 —— 这里不吞异常，
   * 否则用户点了没反应会以为按钮坏了。
   */
  async function toggle(): Promise<StoreFavoriteStatus> {
    const id = toValue(productId)
    if (!id) {
      throw new Error('missing product id')
    }
    pending.value = true
    try {
      const status = normalizeFavoriteStatus(await $fetch<unknown>('/api/favorite/toggle', {
        method: 'POST',
        body: { productId: id, lang: locale.value },
      }))
      applyStatus(status)
      return status
    } finally {
      pending.value = false
    }
  }

  return { favorited, favoriteCount, pending, refresh, toggle }
}

/**
 * "我的收藏"分页列表（个人中心用）。
 *
 * 同样把 locale / 登录态在 setup 阶段取好；未登录时返回空页，不打接口
 * （个人中心未登录时本来就走登录引导，这里只是不产生一次必然 401 的请求）。
 */
export function useFavoriteList(pageSize: number = STORE_FAVORITE_PAGE_SIZE) {
  const { locale } = useI18n()
  const { isLoggedIn } = useAuth()

  const items = ref<StoreProductPage['records']>([])
  const total = ref(0)
  const pageNo = ref(1)
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref('')

  async function load(targetPage: number = pageNo.value) {
    if (!isLoggedIn.value) {
      items.value = []
      total.value = 0
      loaded.value = true
      return
    }
    loading.value = true
    error.value = ''
    try {
      const page = await $fetch<StoreProductPage>('/api/favorite', {
        query: { pageNo: targetPage, pageSize, lang: locale.value },
      })
      items.value = page.records
      total.value = page.total
      pageNo.value = page.current
    } catch (err) {
      error.value = extractFavoriteErrorMessage(err, '')
      items.value = []
      total.value = 0
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  /**
   * 取消收藏（列表内直接操作）。
   * 只移除本地那一行、不整页重拉：用户点完立刻看到结果，
   * 分页也不会因为少了一条而整体跳位。
   */
  async function remove(productId: string) {
    const status = normalizeFavoriteStatus(await $fetch<unknown>('/api/favorite/toggle', {
      method: 'POST',
      body: { productId, lang: locale.value },
    }))
    if (!status.favorited) {
      items.value = items.value.filter(item => item.id !== productId)
      total.value = Math.max(total.value - 1, 0)
    }
    return status
  }

  return { items, total, pageNo, pageSize, loading, loaded, error, load, remove }
}
