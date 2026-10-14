import { defHttp } from '/@/utils/http/axios';

/**
 * 加购统计（后台）
 *
 * 后端契约：所有接口都需要 `shop-cart:stats` 权限；
 * 可见域由后端按登录人所属部门解析，"指定了无权查看的店铺"会直接报错而不是返回空数据。
 */
enum Api {
  overview = '/shop/cart/admin/overview',
  rank = '/shop/cart/admin/rank',
  trend = '/shop/cart/admin/trend',
  eventPage = '/shop/cart/admin/eventPage',
  storeOptions = '/shop/cart/admin/storeOptions',
}

/** 查询条件（空字段一律不传，后端按默认口径处理） */
export interface CartStatsQuery {
  /** 店铺ID；不传 = 我有权限看到的全部店铺 */
  storeId?: string;
  /** 开始日期 yyyy-MM-dd */
  startDate?: string;
  /** 结束日期 yyyy-MM-dd */
  endDate?: string;
  /** 未指定起止日期时，统计最近 N 天（默认 30，上限 90） */
  days?: number;
  /** 排行榜条数（默认 10，上限 100） */
  limit?: number;
  /** 商品标题 / SPU / 用户账号 模糊搜索 */
  keyword?: string;
  /** 明细页码 */
  pageNo?: number;
  /** 明细分页大小 */
  pageSize?: number;
}

/** 总览：加购类字段受统计区间影响，cart* 是"当前时点"的快照 */
export interface CartStatsOverview {
  addTimes: number;
  addQuantity: number;
  addUsers: number;
  addProducts: number;
  addSkus: number;
  quantityPerUser: number;
  cartItems: number;
  cartQuantity: number;
  cartUsers: number;
  rangeStart: string;
  rangeEnd: string;
}

/** 热度排行行 */
export interface CartStatsRank {
  rank: number;
  productId: string;
  /** 无规格商品为空串 */
  skuId: string;
  title: string;
  spuCode?: string | null;
  coverUrl?: string | null;
  skuName?: string | null;
  addTimes: number;
  addQuantity: number;
  addUsers: number;
  /** 商品当前状态：Y 在售 / N 已下架（运营需要关注"高热度但已下架"） */
  productStatus?: string | null;
  lastAddTime?: string | null;
  /** 相对热度（占榜首的百分比，0-100） */
  heat: number;
}

/** 按日趋势（日期连续，无数据的日期为 0） */
export interface CartStatsTrend {
  statDate: string;
  addTimes: number;
  addQuantity: number;
  addUsers: number;
  addProducts: number;
}

/** 加购明细行 */
export interface CartStatsEvent {
  id: string;
  username?: string | null;
  storeName?: string | null;
  productId: string;
  title?: string | null;
  spuCode?: string | null;
  skuId: string;
  skuName?: string | null;
  quantity: number;
  /** ADD / INCR / UPDATE / REMOVE / CLEAR */
  eventType: string;
  source?: string | null;
  createTime: string;
}

/** 分页响应（与 JeecgBoot IPage 一致） */
export interface CartStatsEventPage {
  records: CartStatsEvent[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** 店铺下拉项 */
export interface CartStoreOption {
  id: string;
  code: string;
  name: string;
}

export const getOverview = (params: CartStatsQuery) => defHttp.get<CartStatsOverview>({ url: Api.overview, params });

export const getRank = (params: CartStatsQuery) => defHttp.get<CartStatsRank[]>({ url: Api.rank, params });

export const getTrend = (params: CartStatsQuery) => defHttp.get<CartStatsTrend[]>({ url: Api.trend, params });

export const getEventPage = (params: CartStatsQuery) => defHttp.get<CartStatsEventPage>({ url: Api.eventPage, params });

export const getStoreOptions = () => defHttp.get<CartStoreOption[]>({ url: Api.storeOptions });
