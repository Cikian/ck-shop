// types/shop-order.ts
//
// 订单相关的后端出参类型 —— **唯一来源**。
//
// 此前 /orders 整页与个人中心的订单面板各写了一份（profile 版还少 productId/skuName 等字段），
// 已经开始漂移：后端给 StoreOrderItemVO 加字段时只改一处，另一处就悄悄少了那个字段。
// 这里取两边的并集（超集），两个消费方都从这里 import。
//
// 约定：所有后端可选字段都写成 `xxx?: T | null` —— 后端 JSON 里显式给 null 是常态，
// 只写 `?` 会让"字段存在但为 null"绕过类型检查。

/**
 * 订单明细（后端 StoreOrderItemVO 的子集，够画缩略图 / 行内合计即可）。
 *
 * ⚠️ 这里是**唯一来源**。历史上 composables/useOrderDetail.ts 里还复制了一份
 * 并已经漂移（那份多 productAmount/promotionTitle/refundStatus，少 actualAmount）。
 * 本次取并集收敛到这里；useOrderDetail 改为 import + 再导出，既有调用点不用改。
 */
export interface OrderItemVO {
  id?: string | null
  productId?: string | null
  productName?: string | null
  skuName?: string | null
  productImage?: string | null
  price?: number | null
  quantity?: number | null
  /** 行原价合计（未扣优惠） */
  productAmount?: number | null
  /** 行实付合计 */
  actualAmount?: number | null
  /** 命中的促销名称（没有促销时为 null/空） */
  promotionTitle?: string | null
  refundQuantity?: number | null
  refundAmount?: number | null
  /** 该行的退款状态（后端 refundStatus，用于行内标记"退款中/已退款"） */
  refundStatus?: string | null
}

/** 订单列表项（后端 StoreOrderListVO） */
export interface OrderListVO {
  orderNo: string
  orderStatus?: string | null
  orderStatusLabel?: string | null
  payStatus?: string | null
  refundStatus?: string | null
  payAmount?: number | null
  paidAmount?: number | null
  refundAmount?: number | null
  totalQuantity?: number | null
  itemCount?: number | null
  createTime?: string | null
  items?: OrderItemVO[] | null
  canPay?: boolean | null
  canCancel?: boolean | null
  canReceive?: boolean | null
  canRefund?: boolean | null
}

/** MyBatis-Plus Page 出参 */
export interface OrderPageResult<T> {
  records?: T[] | null
  total?: number | null
  size?: number | null
  current?: number | null
  pages?: number | null
}
