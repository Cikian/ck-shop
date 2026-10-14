// types/shop-favorite.ts
//
// 店铺端（nuxt-shop）收藏相关类型 + 纯函数工具。
//
// 与 types/shop-product.ts / types/shop-cart.ts 同样的约定：本文件刻意不依赖
// 任何 Nuxt / Vue API —— 服务端路由用它做数据归一化，前端页面/组件用它渲染状态。
//
// 数据来源（Java 后端，全部通过 server/utils/apiClient.ts 的 createApiClient 调用）：
//   GET  /shop/favorite/store/status?productId -> FavoriteStatusVO
//        { productId, favorited, favoriteCount }
//   POST /shop/favorite/store/toggle           -> FavoriteStatusVO（操作后的最终状态）
//   GET  /shop/favorite/store/list             -> IPage<StoreProductCardVO>
//
// 收藏列表的卡片结构与商品列表**完全一致**，所以直接复用 types/shop-product.ts 的
// StoreProductPage + normalizePage，本文件只放"收藏状态"这一处后端独有的结构。

import { toInteger, toNullableString } from './shop-product'

/** 收藏状态（商品详情页心形按钮 + 收藏数字） */
export interface StoreFavoriteStatus {
  productId: string
  /** 当前登录用户是否已收藏 */
  favorited: boolean
  /** 商品收藏总数；0 = 无数据，前端按项目约定整块不渲染 */
  favoriteCount: number
}

/**
 * 收藏状态归一化。
 *
 * `favorited` 刻意写成"白名单为真"而不是 `Boolean(x)`：
 * Java 的 Boolean 经过 JSON 序列化后可能是 true / 'true' / 'Y' / 1 四种形态
 * （项目里其它布尔字段已经踩过 'false' 字符串被判为真值的坑），
 * 只有明确等于真值才算收藏过。
 */
export function normalizeFavoriteStatus(raw: unknown): StoreFavoriteStatus {
  const item = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
  const value = item.favorited
  const favorited = value === true || value === 1 || value === '1'
    || (typeof value === 'string' && ['true', 'y', 'yes'].includes(value.trim().toLowerCase()))
  return {
    productId: toNullableString(item.productId) ?? '',
    favorited,
    favoriteCount: Math.max(toInteger(item.favoriteCount, 0), 0),
  }
}
