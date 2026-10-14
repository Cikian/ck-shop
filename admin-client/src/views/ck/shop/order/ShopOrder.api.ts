import { defHttp } from '/@/utils/http/axios';
import type { OrderRefundVO } from '../refund/ShopRefund.api';

/**
 * 订单管理 · 后台接口
 *
 * 路径、入参、出参严格照抄后端三份 Controller 与对应 VO，不要自行改名：
 * - ShopAdminOrderController   /shop/order/admin/**
 * - ShopAdminRefundController  /shop/refund/admin/**
 * - ShopAdminOrderStatsController /shop/order/admin/stats/**
 *
 * <h3>为什么这里**没有**删除接口</h3>
 * 订单严禁物理删除，后端也没有 /delete、/deleteBatch——
 * 不是"前端忘了加按钮"，而是这个能力根本不存在。
 * 清理列表用【归档】，且只对已取消/已关闭订单开放。
 */

enum Api {
  list = '/shop/order/admin/list',
  detail = '/shop/order/admin/detail',
  ship = '/shop/order/admin/ship',
  close = '/shop/order/admin/close',
  remark = '/shop/order/admin/remark',
  archive = '/shop/order/admin/archive',
  compensate = '/shop/order/admin/compensate',
  statsOverview = '/shop/order/admin/stats/overview',
  statsStatus = '/shop/order/admin/stats/status',
  statsTrend = '/shop/order/admin/stats/trend',
  statsRankProduct = '/shop/order/admin/stats/rank/product',
  statsRankCategory = '/shop/order/admin/stats/rank/category',
  taskCloseTimeout = '/shop/order/admin/stats/task/close-timeout',
  taskCompensate = '/shop/order/admin/stats/task/compensate',
  /** ↓ 复用既有店铺接口（启用中的店铺，无额外权限码），避免后端为筛选再造一套 */
  storeAll = '/shop/store/all',
}

/** JeecgBoot 分页返回结构（与 IPage 字段一致） */
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** 后台订单列表查询条件（OrderQuery，逐字段对齐） */
export interface OrderQuery {
  pageNo?: number;
  pageSize?: number;
  /** 订单号（精确） */
  orderNo?: string;
  /** 关键字：订单号 / 用户账号 / 收货人 / 手机号，一个输入框搜全部 */
  keyword?: string;
  /** WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED */
  orderStatus?: string;
  /** UNPAID/PAID/PARTIAL_REFUND/REFUNDED */
  payStatus?: string;
  /** NONE/APPLYING/REJECTED/PARTIAL/FULL */
  refundStatus?: string;
  storeId?: string;
  /** MOCK/WECHAT/ALIPAY */
  payChannel?: string;
  /** web/h5/app/api */
  orderSource?: string;
  /** 下单开始时间 yyyy-MM-dd HH:mm:ss */
  startTime?: string;
  /** 下单结束时间 yyyy-MM-dd HH:mm:ss */
  endTime?: string;
  /** 支付开始时间 yyyy-MM-dd HH:mm:ss */
  payStartTime?: string;
  /** 支付结束时间 yyyy-MM-dd HH:mm:ss */
  payEndTime?: string;
  /** 金额下限（按应付金额） */
  minAmount?: number;
  /** 金额上限（按应付金额） */
  maxAmount?: number;
  /** 是否包含已归档订单（默认否） */
  includeArchived?: boolean;
}

/** 后台订单明细（含成本，后台专属） —— AdminOrderItemVO */
export interface AdminOrderItemVO {
  id: string;
  orderId: string;
  orderNo: string;
  storeId: string;
  storeOrgCode: string;
  userId: string;
  /** 商品ID（仅作追溯线索） */
  productId: string;
  /** SKU 组合ID；无规格为空串 */
  skuId: string;
  spuCode: string;
  skuCode: string;
  productName: string;
  skuName: string;
  productImage: string;
  unit: string;
  categoryId: string;
  categoryName: string;
  weight: number;
  originalPrice: number;
  price: number;
  quantity: number;
  totalAmount: number;
  productAmount: number;
  discountAmount: number;
  actualAmount: number;
  /** 成本单价快照 */
  costPrice: number;
  /** 成本小计 */
  costAmount: number;
  promotionId: string;
  promotionType: string;
  promotionTitle: string;
  promotionDiscount: number;
  promotionInstanceId: string;
  /** 行退款状态: NONE/PARTIAL/FULL */
  refundStatus: string;
  refundQuantity: number;
  refundAmount: number;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
  /** 归档标记: 0-正常, 1-已归档 */
  delFlag: number;
  /** 行退款状态中文 */
  refundStatusLabel: string;
  /** 本行可退金额 = 实付 − 已退（服务端算好） */
  refundableAmount: number;
}

/** 后台订单列表项 —— AdminOrderListVO（继承 ShopOrder 全部经营字段） */
export interface AdminOrderListVO {
  id: string;
  orderNo: string;
  parentOrderNo: string;
  /** 是否子单: 0-否, 1-是 */
  splitFlag: number;
  idempotentKey: string;
  storeId: string;
  storeName: string;
  /** 店铺部门编码快照（数据权限隔离维度） */
  storeOrgCode: string;
  userId: string;
  username: string;
  orderStatus: string;
  payStatus: string;
  refundStatus: string;
  version: number;
  totalAmount: number;
  productAmount: number;
  promotionDiscount: number;
  couponDiscount: number;
  pointsDiscount: number;
  memberDiscount: number;
  freightAmount: number;
  otherFee: number;
  discountAmount: number;
  /** 应付金额 */
  payAmount: number;
  /** 实收（由支付单回写） */
  paidAmount: number;
  /** 累计已退 */
  refundAmount: number;
  costAmount: number;
  grossProfit: number;
  currency: string;
  itemCount: number;
  totalQuantity: number;
  totalWeight: number;
  receiverName: string;
  /** 收货人手机号（后台列表已脱敏） */
  receiverPhone: string;
  receiverProvince: string;
  receiverCity: string;
  receiverDistrict: string;
  receiverAddress: string;
  receiverPostalCode: string;
  receiverTag: string;
  /** 完整地址 JSON 快照 */
  addressSnapshot: string;
  payChannel: string;
  payType: string;
  paymentNo: string;
  payDeadline: string;
  payTime: string;
  shipTime: string;
  receiveTime: string;
  cancelTime: string;
  closeTime: string;
  finishTime: string;
  logisticsCompany: string;
  logisticsNo: string;
  shipRemark: string;
  /** USER/ADMIN/TIMEOUT/REFUND */
  cancelType: string;
  cancelReason: string;
  orderSource: string;
  clientIp: string;
  userAgent: string;
  referrer: string;
  channelCode: string;
  utmSource: string;
  buyerRemark: string;
  sellerRemark: string;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
  /** 归档标记: 0-正常, 1-已归档（不是删除标记） */
  delFlag: number;
  /** 订单状态中文 */
  orderStatusLabel: string;
  /** 支付状态中文 */
  payStatusLabel: string;
  /** 退款状态中文 */
  refundStatusLabel: string;
  items: AdminOrderItemVO[];
  /** 待审核退款单数（列表红点） */
  pendingRefundCount: number;
}

/** 后台订单详情 —— AdminOrderDetailVO */
export interface AdminOrderDetailVO extends AdminOrderListVO {
  payments: OrderPaymentVO[];
  refunds: OrderRefundVO[];
  events: OrderEventVO[];
  /** 完整收货地址 JSON 快照 */
  addressSnapshotJson: string;
}

/** 支付单 —— OrderPaymentVO */
export interface OrderPaymentVO {
  id: string;
  /** 支付单号(=渠道 out_trade_no) */
  paymentNo: string;
  orderId: string;
  orderNo: string;
  userId: string;
  storeId: string;
  storeOrgCode: string;
  /** MOCK/WECHAT/ALIPAY */
  channel: string;
  payType: string;
  /** 应付金额 */
  amount: number;
  /** 渠道实收金额 */
  paidAmount: number;
  /** 累计已退金额（退款额度校验依据） */
  refundAmount: number;
  currency: string;
  /** INIT/PENDING/SUCCESS/FAILED/CLOSED */
  status: string;
  /** 渠道流水号（对账主键） */
  channelTradeNo: string;
  channelBuyerId: string;
  expireTime: string;
  payTime: string;
  closeTime: string;
  notifyTime: string;
  notifyCount: number;
  lastNotifyBody: string;
  errorCode: string;
  errorMsg: string;
  requestId: string;
  version: number;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
  delFlag: number;
  /** 支付单状态中文 */
  statusLabel: string;
  /** 剩余可退金额 = 实收 − 累计已退 */
  refundableAmount: number;
}

/** 订单操作流水 —— OrderEventVO（这张表是"数据可追溯"的落点，字段不做裁剪） */
export interface OrderEventVO {
  id: string;
  orderId: string;
  orderNo: string;
  storeId: string;
  storeOrgCode: string;
  /** 事件类型 */
  eventType: string;
  /** 变更前订单状态 */
  fromStatus: string;
  /** 变更后订单状态 */
  toStatus: string;
  fromPayStatus: string;
  toPayStatus: string;
  /** USER/ADMIN/SYSTEM/CHANNEL */
  operatorType: string;
  operatorId: string;
  operatorName: string;
  /** 本次事件涉及金额 */
  amount: number;
  /** 事件说明 */
  remark: string;
  /** 事件明细 JSON（金额快照/渠道报文摘要/物流信息等） */
  detail: string;
  /** 来源 IP */
  clientIp: string;
  userAgent: string;
  /** 发生时间（= createTime，后端单独下发便于直接展示） */
  createTime: string;
  /** 事件类型中文 */
  eventTypeLabel: string;
  /** 操作者类型中文 */
  operatorTypeLabel: string;
  occurredTime: string;
}

/** 发货入参 —— OrderShipDTO（物流公司与单号要么都填、要么都不填） */
export interface OrderShipDTO {
  orderNo: string;
  logisticsCompany?: string;
  logisticsNo?: string;
  shipRemark?: string;
}

/** 关闭/取消入参 —— OrderActionDTO */
export interface OrderActionDTO {
  orderNo: string;
  reason?: string;
}

/** 卖家备注入参 —— OrderRemarkDTO */
export interface OrderRemarkDTO {
  orderNo: string;
  sellerRemark?: string;
}

/** 订单统计总览 —— OrderStatsOverviewVO */
export interface OrderStatsOverviewVO {
  totalOrders: number;
  waitPayOrders: number;
  waitShipOrders: number;
  shippedOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  closedOrders: number;
  paidOrders: number;
  /** GMV（已支付订单应付金额合计，不扣退款） */
  gmv: number;
  /** 实收 */
  paidAmount: number;
  /** 退款合计 */
  refundAmount: number;
  /** 净收入 = 实收 − 退款 */
  netAmount: number;
  discountAmount: number;
  freightAmount: number;
  costAmount: number;
  /** 毛利合计（不扣退款） */
  grossProfit: number;
  /** 客单价 */
  avgOrderAmount: number;
  totalQuantity: number;
  /** 支付转化率 = 已支付订单数 / 订单总数 */
  payConversionRate: number;
  /** 退款率 = 退款金额 / 实收金额（按金额，不按单数） */
  refundRate: number;
  /** 毛利率 = 毛利 / 成交金额 */
  grossProfitRate: number;
  cartUsers: number;
  orderUsers: number;
  /** 加购转下单转化率 */
  cartConversionRate: number;
  todayOrders: number;
  todayPaidAmount: number;
  todayRefundAmount: number;
  todayGmv: number;
  /** 风险：已关闭但已收款订单数（必须为 0，不为 0 需立即人工介入） */
  closedButPaidOrders: number;
  /** 风险：支付中（结果未知）的支付单数 */
  pendingPayments: number;
  /** 风险：待审核退款单数 */
  pendingRefunds: number;
}

/** 订单趋势（按日） —— OrderStatsTrendVO */
export interface OrderStatsTrendVO {
  /** 日期 yyyy-MM-dd */
  date: string;
  /** 下单数（按下单时间） */
  orderCount: number;
  /** 支付单数（按支付时间） */
  paidCount: number;
  gmv: number;
  paidAmount: number;
  refundAmount: number;
  netAmount: number;
  quantity: number;
}

/** 订单排行项 —— OrderStatsRankVO */
export interface OrderStatsRankVO {
  rank: number;
  productId: string;
  skuId: string;
  productName: string;
  skuName: string;
  productImage: string;
  categoryName: string;
  /** 销量（件） */
  quantity: number;
  orderCount: number;
  /** 销售额（实付） */
  salesAmount: number;
  grossProfit: number;
}

/** 订单状态分布 —— OrderStatsStatusVO */
export interface OrderStatsStatusVO {
  status: string;
  statusLabel: string;
  orderCount: number;
  /** 金额合计（应付） */
  amount: number;
}

/** 任务执行结果（超时关单 / 支付补单 / 人工补偿） */
export interface OrderTaskResult {
  scanned?: number;
  succeeded?: number;
  failed?: number;
  skipped?: number;
  costMillis?: number;
  message?: string;
}

// ==================================================================
// 公共下拉
// ==================================================================

/** 订单分页列表（需 shop-order:list） */
export const list = (params: OrderQuery) => defHttp.get<PageResult<AdminOrderListVO>>({ url: Api.list, params });

/** 订单详情：含支付单 / 退款单 / 完整操作流水（需 shop-order:detail） */
export const detail = (orderNo: string) => defHttp.get<AdminOrderDetailVO>({ url: Api.detail, params: { orderNo } });

/** 发货（需 shop-order:ship）；物流公司与单号必须成对填写 */
export const ship = (params: OrderShipDTO) => defHttp.post<string>({ url: Api.ship, params });

/**
 * 关闭/取消订单（需 shop-order:close）
 *
 * 已支付订单必须先全额退款，否则后端会拒绝并给出明确原因。
 */
export const close = (params: OrderActionDTO) => defHttp.post<string>({ url: Api.close, params });

/** 修改卖家备注（需 shop-order:remark，会写操作流水） */
export const remark = (params: OrderRemarkDTO) => defHttp.post<string>({ url: Api.remark, params });

/**
 * 归档（需 shop-order:archive）
 *
 * 仅已取消/已关闭订单可归档；归档只是"不再出现在默认列表"，数据完整保留。
 * @RequestParam，因此把参数拼到 URL 上。
 */
export const archive = (orderNo: string) => defHttp.post<string>({ url: Api.archive, params: { orderNo } }, { joinParamsToUrl: true });

/**
 * 人工补偿：按订单号主动向渠道查询支付结果（需 shop-order:compensate）
 *
 * 返回 scanned/succeeded/skipped/message，必须展示给用户看——
 * "补单跑了但没查到"与"补单成功"是完全不同的结论。
 */
export const compensate = (orderNo: string) => defHttp.post<OrderTaskResult>({ url: Api.compensate, params: { orderNo } }, { joinParamsToUrl: true });

// ==================================================================
// 订单统计
// ==================================================================

/** 统计总览（需 shop-order:stats） */
export const statsOverview = (params: OrderQuery) => defHttp.get<OrderStatsOverviewVO>({ url: Api.statsOverview, params });

/** 状态分布（饼图） */
export const statsStatus = (params: OrderQuery) => defHttp.get<OrderStatsStatusVO[]>({ url: Api.statsStatus, params });

/** 按日趋势 */
export const statsTrend = (params: OrderQuery) => defHttp.get<OrderStatsTrendVO[]>({ url: Api.statsTrend, params });

/** 排行查询 = OrderQuery + limit（rank 接口额外接 limit，默认 10，上限 100） */
export interface RankQuery extends OrderQuery {
  /** 排行榜条数 */
  limit?: number;
}

/** 热销商品 / SKU 排行 */
export const statsRankProduct = (params: RankQuery) => defHttp.get<OrderStatsRankVO[]>({ url: Api.statsRankProduct, params });

/** 热销分类排行 */
export const statsRankCategory = (params: RankQuery) => defHttp.get<OrderStatsRankVO[]>({ url: Api.statsRankCategory, params });

/** 运维：手动触发超时关单任务（需 shop-order:compensate） */
export const runCloseTimeoutTask = (batchSize: number) =>
  defHttp.post<OrderTaskResult>({ url: Api.taskCloseTimeout, params: { batchSize } }, { joinParamsToUrl: true });

/** 运维：手动触发支付补单任务（需 shop-order:compensate） */
export const runCompensateTask = (batchSize: number) =>
  defHttp.post<OrderTaskResult>({ url: Api.taskCompensate, params: { batchSize } }, { joinParamsToUrl: true });

// ==================================================================
// 公共下拉
// ==================================================================

/** 启用中的店铺（筛选「所属店铺」用） */
export const storeAll = () => defHttp.get<any[]>({ url: Api.storeAll });
