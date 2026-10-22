// server/api/__sitemap__/urls.ts
//
// 动态 sitemap 数据源（@nuxtjs/sitemap 的约定路由：/__sitemap__/urls）。
//
// 之前 sitemap 只有静态路由（首页/分类总览/商品总览），所有真实商品页与分类页
// 都不在 sitemap 里 —— 搜索引擎只能靠站内链接慢慢发现，新商品上架后可能要等很久。
//
// 这里按语言分页遍历 BFF 的公开接口，输出：
//   · 每个分类页（多级分类全部展开）
//   · 每个在售商品页（spuCode，与前台路由一致）
// 并带 lastmod（商品用 listedAt，分类用当前时间）。
//
// 走 event.$fetch 调本应用的 BFF，从而复用签名/店铺头/语言参数等既有逻辑，
// 不直接访问 Java 后端（避免绕过 BFF 的响应归一化）。

import type { StoreCategoryNode } from '~~/types/store-category'
import type { StoreProductPage } from '~~/types/shop-product'
import { STORE_LOCALES } from '~~/types/store-locale'

/** 单页条数：与后端上限一致（BFF 会把 pageSize 夹到 60） */
const PAGE_SIZE = 60

/** 防御性上限：即使后端分页异常也不会无限循环 */
const MAX_PAGES = 200

export default defineSitemapEventHandler(async (event) => {
  const urls: Array<{ loc: string; lastmod?: string; changefreq?: string; priority?: number }> = []

  const flattenCategories = (nodes: StoreCategoryNode[]): StoreCategoryNode[] => {
    const result: StoreCategoryNode[] = []
    const stack = [...nodes]
    while (stack.length) {
      const node = stack.shift()
      if (!node) continue
      result.push(node)
      if (node.children?.length) stack.push(...node.children)
    }
    return result
  }

  for (const locale of STORE_LOCALES) {
    const lang = locale.code
    const prefix = `/${lang}`

    // ---- 分类页 ----
    try {
      const categories = await event.$fetch<StoreCategoryNode[]>('/api/categories', { query: { lang } })
      for (const category of flattenCategories(categories || [])) {
        urls.push({
          loc: `${prefix}/categories/${category.id}`,
          changefreq: 'weekly',
          priority: 0.6,
        })
      }
    } catch {
      // 分类接口失败不影响商品 URL 的产出
    }

    // ---- 商品页（分页拉全量） ----
    let pageNo = 1
    while (pageNo <= MAX_PAGES) {
      let page: StoreProductPage
      try {
        page = await event.$fetch<StoreProductPage>('/api/products', {
          query: { lang, pageNo, pageSize: PAGE_SIZE },
        })
      } catch {
        break
      }

      for (const product of page.records || []) {
        if (!product.spuCode) continue
        urls.push({
          loc: `${prefix}/products/${product.spuCode}`,
          lastmod: product.listedAt || undefined,
          changefreq: 'daily',
          priority: 0.8,
        })
      }

      const totalPages = Number(page.pages) || 0
      if (!page.records?.length || pageNo >= totalPages) {
        break
      }
      pageNo += 1
    }
  }

  return urls
})
