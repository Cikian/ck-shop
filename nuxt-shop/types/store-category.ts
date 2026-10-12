// types/store-category.ts
//
// 店铺端分类树节点（后端 StoreCategoryVO 的店铺端投影）。
// 数据来源：GET /api/categories -> Java 后端 GET /shop/store/category/tree?lang=
//   - 后端已按**当前店铺**过滤（全局共享节点 + 本店专属节点）；
//   - name 已按 lang 解析（后端会做"目标语言 -> zh -> en -> 任一非空"的兜底）；
//   - productCount 是"含子分类的本店已上架商品数"，前端不二次计算。
// 本文件保持零依赖，只描述后端返回的字段。

export interface StoreCategoryNode {
  id: string
  parentId?: string | null
  /** 层级: 1-一级, 2-二级, 3-三级 */
  level?: number | null
  /** 当前语言分类名 */
  name: string
  /** 图标URL（可能为空） */
  iconUrl?: string | null
  /** 封面URL（可能为空） */
  coverUrl?: string | null
  sortOrder?: number | null
  /** 含子分类的本店已上架商品数 */
  productCount?: number | null
  children?: StoreCategoryNode[]
}
