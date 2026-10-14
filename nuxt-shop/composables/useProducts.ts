// composables/useProducts.ts
//
// 店铺端商品数据获取 + 价格格式化。
//
// 注意：Nuxt 4 的 srcDir 是 app/，本项目根目录下的 composables/ 不会被自动导入
// （useAuth / useCaptcha 也都是显式 import 的），所以要这样用：
//   import { useStoreProductList } from '~~/composables/useProducts'
//
// 数据链路：页面 -> Nuxt BFF(/api/products) -> createApiClient -> Java 后端
//   GET /shop/product/store/list    （后端分页，恒返回 { records, total, size, current, pages }）
//   GET /shop/product/store/detail  （恒返回商品详情对象）

import type { StoreProductDetail, StoreProductPage } from '~~/types/shop-product'
import { formatPriceNumber, formatShopPrice } from '~~/types/shop-product'

/** 列表页每页条数（后端默认值也是 12） */
export const STORE_PRODUCT_PAGE_SIZE = 12

/**
 * 商品列表排序方式。
 * 取值与 Java 后端 queryStorePageList 的白名单一一对应；
 * 前端只传这几个字面量，从不把任意字符串透传给后端。
 */
export type StoreProductSort = 'default' | 'sales' | 'newest' | 'price_asc' | 'price_desc'

export function emptyStoreProductPage(pageSize: number = STORE_PRODUCT_PAGE_SIZE): StoreProductPage {
  return { records: [], total: 0, size: pageSize, current: 1, pages: 0 }
}

export interface StoreProductListQuery {
  /** 当前页码，1 开始 */
  pageNo: Ref<number>
  pageSize?: number
  categoryId?: Ref<string | null | undefined>
  keyword?: Ref<string | null | undefined>
  /** 排序；'default' 时不下发该参数，后端走默认排序 */
  sortBy?: Ref<StoreProductSort | null | undefined>
}

/**
 * 商品列表（后端分页）。
 * key 里带上了 locale / 页码 / 筛选条件，key 变化时 Nuxt 会自动重新请求，
 * 因此语言切换、翻页、搜索、改排序都会真实打到后端。
 */
export function useStoreProductList(options: StoreProductListQuery) {
  const { locale } = useI18n()
  const pageSize = options.pageSize ?? STORE_PRODUCT_PAGE_SIZE

  const requestKey = computed(() =>
    [
      'store-product-list',
      locale.value,
      options.pageNo.value,
      pageSize,
      options.categoryId?.value ?? '',
      options.keyword?.value ?? '',
      options.sortBy?.value ?? '',
    ].join(':'),
  )

  return useAsyncData<StoreProductPage>(
    requestKey,
    () =>
      $fetch<StoreProductPage>('/api/products', {
        query: {
          lang: locale.value,
          pageNo: options.pageNo.value,
          pageSize,
          categoryId: options.categoryId?.value ?? undefined,
          keyword: options.keyword?.value || undefined,
          sortBy: options.sortBy?.value && options.sortBy.value !== 'default'
            ? options.sortBy.value
            : undefined,
        },
      }),
    { default: () => emptyStoreProductPage(pageSize) },
  )
}

/**
 * 商品详情。slug 就是后端商品 id（契约：id 同时作为店铺端 slug）。
 * key 里带上 slug + locale，路由参数或语言变化都会重新请求。
 */
export function useStoreProductDetail(slug: MaybeRefOrGetter<string | undefined>) {
  const { locale } = useI18n()

  const requestKey = computed(() => `store-product-detail:${toValue(slug) ?? ''}:${locale.value}`)

  return useAsyncData<StoreProductDetail | null>(
    requestKey,
    async () => {
      const id = toValue(slug)
      if (!id) return null
      return await $fetch<StoreProductDetail>(`/api/products/${encodeURIComponent(id)}`, {
        query: { lang: locale.value },
      })
    },
    { default: () => null },
  )
}

/**
 * 价格格式化：数字部分交给 Intl.NumberFormat 按当前 locale 处理（千分位/小数分隔符），
 * 货币符号复用 i18n 的 common.currency（zh=¥ / en=$ / fr=€），不臆造汇率。
 */
export function useStorePrice() {
  const { t, locale } = useI18n()

  const symbol = computed(() => t('common.currency'))

  function format(value: number | null | undefined): string {
    if (value === null || value === undefined) return '--'
    return `${symbol.value}${formatPriceNumber(value, locale.value)}`
  }

  /**
   * 商城风格价格：整数不带小数（¥4,299），有角分才带两位（¥211.68）。
   * 新设计（珊瑚玫红方案）里卡片与详情页统一用它 —— 价格是页面最大的字，
   * 拖着 `.00` 会显得很业余。
   */
  function formatCompact(value: number | null | undefined): string {
    if (value === null || value === undefined) return '--'
    return `${symbol.value}${formatShopPrice(value, locale.value)}`
  }

  /** 价格区间：min/max 相同或只有一个时退化为单价格 */
  function formatRange(min: number | null | undefined, max: number | null | undefined, fallback?: number | null): string {
    const low = min ?? fallback ?? null
    const high = max ?? min ?? fallback ?? null
    if (low === null) return '--'
    if (high === null || Number(low) === Number(high)) return format(low)
    return `${format(low)} - ${format(high)}`
  }

  return { format, formatCompact, formatRange, symbol }
}
