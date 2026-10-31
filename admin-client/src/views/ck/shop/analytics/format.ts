/**
 * 用户行为分析 · 展示格式化工具
 *
 * 与订单统计（ShopOrder.data.ts）保持同一套口径：
 *   - 金额统一 2 位小数，空值显示 '-'
 *   - 比率：后端返回「百分数数值字符串、不带 %、保留 2 位」（如 "12.34"），前端补 %
 *   - 时间格式统一 yyyy-MM-dd HH:mm:ss
 */

/** 时间格式：全模块统一 */
export const ANALYTICS_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** 金额展示：字符串金额，2 位小数；null / '' 显示 '-' */
export function formatAmount(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const num = Number(value);
  return Number.isFinite(num) ? num.toFixed(2) : String(value);
}

/**
 * 比率展示。
 *
 * 已确认标准（后端）：返回「百分数数值字符串、不带 %、保留 2 位」，
 * 例如 "12.34" 表示 12.34%。这里仅补 %；兼容后端直接下发带 % 的字符串时原样返回。
 */
export function formatRate(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const str = String(value).trim();
  if (!str) {
    return '-';
  }
  return str.includes('%') ? str : `${str}%`;
}

/** 停留时长：ms → 人性化文本 */
export function formatDuration(ms: unknown): string {
  const num = Number(ms ?? 0);
  if (!Number.isFinite(num) || num <= 0) {
    return '-';
  }
  if (num < 1000) {
    return `${num}ms`;
  }
  const seconds = Math.round(num / 1000);
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return s ? `${m}m${s}s` : `${m}m`;
}

/** 千分位整数（PV/UV 等大数展示） */
export function formatNumber(value: unknown): string {
  const num = Number(value ?? 0);
  if (!Number.isFinite(num)) {
    return '-';
  }
  return num.toLocaleString('en-US');
}

// ------------------------------------------------------------------
// 漏斗与地域标签（大屏 / 仪表盘 / 明细共用）
// ------------------------------------------------------------------

/** 漏斗固定 4 步顺序（契约 §6.4：visit→add_cart→order→paid） */
export const FUNNEL_STEP_ORDER = ['visit', 'add_cart', 'order', 'paid'] as const;

/** 漏斗步骤展示名 */
export const FUNNEL_STEP_LABELS: Record<string, string> = {
  visit: '访问',
  add_cart: '加购',
  order: '下单',
  paid: '支付',
};

/** 归一化后的漏斗项（图表直接使用） */
export interface FunnelChartItem {
  name: string;
  value: number;
  rate: string;
}

/**
 * 归一化漏斗数据：无论后端返回几步，都补齐为固定 4 步；
 * 缺失步骤补 0 / 0.00%，保证图表在无数据时仍完整渲染，不整块空白。
 */
export function normalizeFunnel(steps?: { step?: string; label?: string; count?: number; rate?: string }[]): FunnelChartItem[] {
  const byStep = new Map((steps || []).map((s) => [s.step, s]));
  return FUNNEL_STEP_ORDER.map((step) => {
    const s = byStep.get(step);
    const rate = s?.rate !== null && s?.rate !== undefined && s?.rate !== '' ? String(s.rate) : '0.00';
    return {
      name: (s && s.label) || FUNNEL_STEP_LABELS[step],
      value: Number(s?.count ?? 0),
      rate,
    };
  });
}

/** 地域标签：国家/省/市依次拼接；全空时回退「未知」（而非 '-'） */
export function regionLabel(row: Recordable): string {
  return [row?.countryName, row?.provinceName, row?.cityName].filter((v) => v).join(' / ') || row?.countryCode || '未知';
}
