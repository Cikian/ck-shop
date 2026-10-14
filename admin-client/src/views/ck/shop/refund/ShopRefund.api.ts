import { defHttp } from '/@/utils/http/axios';

/**
 * 退款管理 · 后台接口（ShopAdminRefundController）
 *
 * <h3>审核是两步式的，界面文案必须如实反映</h3>
 * "审核通过" ≠ "钱已经退了"：
 * 后端先在支付单上**原子占退款额度**，再提交渠道退款；
 * 渠道可能同步成功、可能受理中（REFUNDING）、也可能失败（FAILED，可重试）。
 * 因此审核通过后要把"可能还需重试"讲清楚，不能让客服以为点完就完事了。
 */

enum Api {
  list = '/shop/refund/admin/list',
  detail = '/shop/refund/admin/detail',
  audit = '/shop/refund/admin/audit',
  retry = '/shop/refund/admin/retry',
  refundable = '/shop/refund/admin/refundable',
  /** 复用既有店铺接口（启用中的店铺） */
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

/** 退款单查询条件 —— RefundQuery（逐字段对齐） */
export interface RefundQuery {
  pageNo?: number;
  pageSize?: number;
  /** 退款单号 */
  refundNo?: string;
  /** 订单号 */
  orderNo?: string;
  /** APPLYING/REJECTED/REFUNDING/SUCCESS/FAILED/CLOSED */
  status?: string;
  storeId?: string;
  /** 申请开始时间 yyyy-MM-dd HH:mm:ss */
  startTime?: string;
  /** 申请结束时间 yyyy-MM-dd HH:mm:ss */
  endTime?: string;
}

/** 退款明细行 —— ShopOrderRefundItem（按订单行退款，退款总额 == 各行之和） */
export interface ShopOrderRefundItem {
  id: string;
  refundId: string;
  refundNo: string;
  orderId: string;
  orderItemId: string;
  productId: string;
  /** 无规格为空串 */
  skuId: string;
  productName: string;
  skuName: string;
  /** 退款单价（成交价快照） */
  price: number;
  quantity: number;
  refundAmount: number;
  createTime: string;
}

/** 退款单 —— OrderRefundVO */
export interface OrderRefundVO {
  id: string;
  /** 退款单号(=渠道 out_refund_no) */
  refundNo: string;
  orderId: string;
  orderNo: string;
  paymentId: string;
  paymentNo: string;
  userId: string;
  username: string;
  storeId: string;
  storeOrgCode: string;
  /** FULL 全额 / PARTIAL 部分 */
  refundType: string;
  /** 申请退款金额 */
  refundAmount: number;
  /** 退款商品种类数 */
  itemCount: number;
  /** 退款商品件数 */
  totalQuantity: number;
  /** 申请原因（字典 shop_refund_reason） */
  applyReason: string;
  applyDesc: string;
  /** 凭证图片（JSON 数组字符串） */
  applyImages: string;
  /** APPLYING/REJECTED/REFUNDING/SUCCESS/FAILED/CLOSED */
  status: string;
  applyTime: string;
  auditBy: string;
  auditTime: string;
  auditRemark: string;
  rejectReason: string;
  /** 渠道退款流水号 */
  channelRefundNo: string;
  /** 退款到账时间（渠道时间） */
  refundTime: string;
  failCode: string;
  failReason: string;
  /** 渠道退款重试次数 */
  retryCount: number;
  requestId: string;
  version: number;
  createBy: string;
  createTime: string;
  updateBy: string;
  updateTime: string;
  delFlag: number;
  /** 退款状态中文 */
  statusLabel: string;
  /** 退款类型中文 */
  refundTypeLabel: string;
  /** 退款明细行 */
  items: ShopOrderRefundItem[];
}

/** 退款审核入参 —— RefundAuditDTO（拒绝时 rejectReason 必填，后端硬校验） */
export interface RefundAuditDTO {
  refundNo: string;
  approved: boolean;
  auditRemark?: string;
  rejectReason?: string;
}

/** 某订单的可退金额 */
export interface RefundableVO {
  orderNo: string;
  refundableAmount: number;
}

/** 退款单分页列表（需 shop-refund:list） */
export const list = (params: RefundQuery) => defHttp.get<PageResult<OrderRefundVO>>({ url: Api.list, params });

/** 退款单详情（含退款明细行，需 shop-refund:list） */
export const detail = (refundNo: string) => defHttp.get<OrderRefundVO>({ url: Api.detail, params: { refundNo } });

/**
 * 退款审核（需 shop-refund:audit）
 *
 * approved=true 时 auditRemark 选填；approved=false 时 rejectReason **必填**。
 */
export const audit = (params: RefundAuditDTO) => defHttp.post<string>({ url: Api.audit, params });

/**
 * 重试渠道退款（需 shop-refund:retry，仅 FAILED / REFUNDING）
 * @RequestParam，参数拼到 URL 上。
 */
export const retry = (refundNo: string) => defHttp.post<string>({ url: Api.retry, params: { refundNo } }, { joinParamsToUrl: true });

/** 查询某订单可退金额（客服在审核前先看额度，避免"看着能退、提交被拒"） */
export const refundable = (orderNo: string) => defHttp.get<RefundableVO>({ url: Api.refundable, params: { orderNo } });

/** 启用中的店铺（筛选「所属店铺」用） */
export const storeAll = () => defHttp.get<any[]>({ url: Api.storeAll });
