import { defHttp } from '/@/utils/http/axios';

/**
 * 用户行为分析 · 后台接口（Phase 1）
 *
 * 契约唯一权威来源：docs/用户行为分析-接口契约.md
 *   - §6.1 接口清单（/shop/analytics/admin/**）
 *   - §6.4 返回字段（前后端并行开发的唯一依据）
 *
 * 统一响应 Result<T>：{ success, code, message, result, timestamp }，
 * defHttp 已解包出 result，因此下面函数直接返回 §6.4 里的 result 类型。
 *
 * 硬性约定（契约 §6.4）：
 *   - 金额一律字符串（BigDecimal → JSON 字符串）
 *   - 比率（clickRate / addCartRate / conversionRate / bounceRate / funnel.step.rate）
 *     为「百分数数值字符串、不带 %、保留 2 位」，例如 "12.34" 表示 12.34%
 *   - 时间格式 yyyy-MM-dd HH:mm:ss
 *   - UV 为近似值时前端必须标注「近似」（todayUv / hour 粒度 uv 为 HyperLogLog 估算）
 *
 * 权限码（契约 §6.2）：
 *   - shop-analytics:view     查看行为统计
 *   - shop-analytics:export   导出明细
 *   - shop-analytics:all      全局汇总视角（超管，绕过 sys_org_code 范围）
 */

enum Api {
  realtimeOverview = '/shop/analytics/admin/realtime/overview',
  trend = '/shop/analytics/admin/trend',
  rankProduct = '/shop/analytics/admin/rank/product',
  funnel = '/shop/analytics/admin/funnel',
  region = '/shop/analytics/admin/region',
  source = '/shop/analytics/admin/source',
  device = '/shop/analytics/admin/device',
  page = '/shop/analytics/admin/page',
  events = '/shop/analytics/admin/events',
}

// ==================================================================
// 查询条件
//
// 说明：契约 §6.4 只锁定了**返回字段**，接口入参未逐字段列出。
// 这里沿用订单统计 / 加购统计的既有口径（店铺 + 起止时间 + 分页），
// 字段名与后端 ShopOrder / ShopCart 统计保持一致，后端实施时请按此对齐。
// ==================================================================

/** 通用查询条件（分布类接口共用） */
export interface AnalyticsBaseQuery {
  /** 店铺 ID；不传 = 当前登录人可见域（后端按 sys_org_code 隔离） */
  storeId?: string;
  /** 开始时间 yyyy-MM-dd HH:mm:ss */
  startTime?: string;
  /** 结束时间 yyyy-MM-dd HH:mm:ss */
  endTime?: string;
}

/** 趋势查询（契约 §6.1：granularity=hour/day，按 dim） */
export interface TrendQuery extends AnalyticsBaseQuery {
  /** 统计粒度，默认 day */
  granularity?: 'hour' | 'day';
  /** 维度：global/store/product/region/source/device/page，默认 global */
  dim?: string;
}

/** 商品排行查询 */
export interface RankQuery extends AnalyticsBaseQuery {
  /** 排行榜条数，默认 10，上限 100 */
  limit?: number;
}

/** 地域分布查询 */
export interface RegionQuery extends AnalyticsBaseQuery {
  /** 下钻级别：country / province / city，默认 country */
  level?: 'country' | 'province' | 'city';
}

/** 行为明细分页查询 */
export interface EventsQuery extends AnalyticsBaseQuery {
  /** 页码，从 1 开始 */
  pageNo?: number;
  /** 分页大小 */
  pageSize?: number;
  /** 事件名（受控枚举，见契约 §4.1） */
  eventName?: string;
  /** 商品 ID */
  productId?: string;
  /** 关键字：visitor_id / user_id / session_id / 页面路径 模糊匹配 */
  keyword?: string;
}

// ==================================================================
// 返回 VO（严格对齐契约 §6.4）
// ==================================================================

/** 实时大盘 —— /realtime/overview */
export interface RealtimeOverviewVO {
  /** 当前在线人数（秒级，Redis ZSET） */
  onlineCount: number;
  /** 今日 PV */
  todayPv: number;
  /** 今日 UV（近似，HyperLogLog） */
  todayUv: number;
  /** 今日 IP 数（去重） */
  todayIp: number;
  /** 今日访客（会话）数 */
  todayVisit: number;
  /** 今日订单数 */
  todayOrderCount: number;
  /** 今日实收（字符串金额） */
  todayPaidAmount: string;
}

/** 趋势点 —— /trend */
export interface TrendPointVO {
  /** 时间 yyyy-MM-dd HH:mm:ss（day 粒度到天） */
  time: string;
  pv: number;
  /** 近似（hour 粒度 HyperLogLog） */
  uv: number;
  visit: number;
  /** 平均停留时长（ms） */
  avgDurationMs: number;
  addCartCount: number;
  orderCount: number;
  paidCount: number;
}

/** 商品排行项 —— /rank/product */
export interface ProductRankVO {
  productId: string;
  productTitle?: string;
  viewCount: number;
  visitCount: number;
  avgDurationMs: number;
  addCartCount: number;
  orderCount: number;
  paidCount: number;
  /** 点击率（字符串） */
  clickRate: string;
  /** 加购率（字符串） */
  addCartRate: string;
  /** 转化率（字符串） */
  conversionRate: string;
}

/** 漏斗步骤 —— /funnel */
export interface FunnelStepVO {
  /** 步骤标识：visit / add_cart / order / paid */
  step: string;
  /** 步骤中文/展示名 */
  label: string;
  count: number;
  /** 相对上一环节的转化率（字符串） */
  rate: string;
}

/** 转化漏斗 —— /funnel */
export interface FunnelVO {
  /** 顺序固定 visit→add_cart→order→paid */
  steps: FunnelStepVO[];
}

/** 地域分布项 —— /region */
export interface RegionVO {
  countryCode: string;
  countryName: string;
  provinceName?: string;
  cityName?: string;
  pv: number;
  uv: number;
  /** 去重 IP 数 */
  ip: number;
}

/** 来源分布项 —— /source */
export interface SourceVO {
  /** 来源：direct/search/referral/utm */
  source: string;
  medium?: string;
  campaign?: string;
  pv: number;
  uv: number;
}

/** 设备分布项 —— /device */
export interface DeviceVO {
  deviceType?: string;
  os?: string;
  browser?: string;
  lang?: string;
  pv: number;
  uv: number;
}

/** 页面排行项 —— /page */
export interface PageVO {
  pagePath: string;
  pv: number;
  uv: number;
  avgDurationMs: number;
  /** 跳出率（字符串） */
  bounceRate: string;
}

/** 行为明细记录（字段与 ck_shop_track_event 列一致，契约 §3.2 / §6.4） */
export interface TrackEventVO {
  id: string;
  /** 事件发生时间 yyyy-MM-dd HH:mm:ss */
  eventTime: string;
  /** 事件名（受控枚举） */
  eventName: string;
  sessionId: string;
  visitorId: string;
  userId?: string;
  orgCode: string;
  storeId?: string;
  productId?: string;
  pageUrl?: string;
  pagePath?: string;
  referrer?: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  deviceType?: string;
  os?: string;
  browser?: string;
  screen?: string;
  lang?: string;
  countryCode?: string;
  countryName?: string;
  provinceName?: string;
  cityName?: string;
  provinceCode?: string;
  /** 原始客户端 IP（本期直接返回，契约 §6.4） */
  ip?: string;
  /** 停留时长（仅 page_leave 事件，ms） */
  durationMs?: number;
  /** 扩展属性 JSON（搜索词/曝光位置等） */
  props?: unknown;
}

/** 分页响应（与 JeecgBoot IPage 一致） */
export interface AnalyticsPageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

// ==================================================================
// 接口函数
// ==================================================================

/** 实时大盘（总览 30s 刷，契约 §6.1） */
export const getRealtimeOverview = () => defHttp.get<RealtimeOverviewVO>({ url: Api.realtimeOverview });

/** 趋势（granularity=hour/day） */
export const getTrend = (params: TrendQuery) => defHttp.get<TrendPointVO[]>({ url: Api.trend, params });

/** 商品排行（访问/加购/转化率/停留） */
export const getRankProduct = (params: RankQuery) => defHttp.get<ProductRankVO[]>({ url: Api.rankProduct, params });

/** 转化漏斗 */
export const getFunnel = (params: AnalyticsBaseQuery) => defHttp.get<FunnelVO>({ url: Api.funnel, params });

/** 地域分布（国家/省/市） */
export const getRegion = (params: RegionQuery) => defHttp.get<RegionVO[]>({ url: Api.region, params });

/** 来源分布 */
export const getSource = (params: AnalyticsBaseQuery) => defHttp.get<SourceVO[]>({ url: Api.source, params });

/** 设备/浏览器/语言分布 */
export const getDevice = (params: AnalyticsBaseQuery) => defHttp.get<DeviceVO[]>({ url: Api.device, params });

/** 页面排行/平均停留/跳出率 */
export const getPage = (params: AnalyticsBaseQuery) => defHttp.get<PageVO[]>({ url: Api.page, params });

/** 行为明细分页（导出用） */
export const getEvents = (params: EventsQuery) => defHttp.get<AnalyticsPageResult<TrackEventVO>>({ url: Api.events, params });
