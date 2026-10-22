import { h } from 'vue';
import { Tag, Tooltip } from 'ant-design-vue';
import dayjs from 'dayjs';
import { BasicColumn } from '/@/components/Table';

/**
 * 促销活动 · 列表列定义与展示口径
 *
 * 两点口径务必保持：
 * 1. 类型 / 状态的中文一律在这里渲染（Tag 着色），页面上不出现 DIRECT / Y 这类裸枚举；
 * 2. 「未开始 / 进行中 / 已结束」由 startTime / endTime **前端现算**（契约 2.2 注），
 *    服务端不返回该字段，也不用服务端时间判断，避免服务端时区/时钟与用户看到的不一致。
 */

/** 时间格式：全模块统一，dayjs 直接用这个常量格式化 */
export const PROMOTION_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/**
 * 活动类型选项（契约 1：DIRECT 直降 / PERCENT 折扣 / FULL_CUT 满减 / SECKILL 秒杀）
 */
export const PROMOTION_TYPE_OPTIONS = [
  { label: '直降', value: 'DIRECT' },
  { label: '折扣', value: 'PERCENT' },
  { label: '满减', value: 'FULL_CUT' },
  { label: '秒杀', value: 'SECKILL' },
];

/** 启用状态选项 */
export const PROMOTION_STATUS_OPTIONS = [
  { label: '启用', value: 'Y' },
  { label: '停用', value: 'N' },
];

/** 类型 -> 中文 */
const TYPE_TEXT_MAP: Record<string, string> = {
  DIRECT: '直降',
  PERCENT: '折扣',
  FULL_CUT: '满减',
  SECKILL: '秒杀',
};

/** 类型 -> Tag 颜色 */
const TYPE_COLOR_MAP: Record<string, string> = {
  DIRECT: 'magenta',
  PERCENT: 'orange',
  FULL_CUT: 'blue',
  SECKILL: 'red',
};

/**
 * 活动多语言清单已改为运行时从 `/sys/lang` 读取（PromotionForm 的 loadLanguages）。
 * 之前这里写死 zh/en/fr：系统语言管理新增/禁用语言后，促销页不跟随，
 * 而商品表单早已用 listLang() 动态生成 —— 两套口径不一致。
 */

/** 类型中文 */
export function getTypeText(type?: string): string {
  return TYPE_TEXT_MAP[type || ''] || (type ? String(type) : '-');
}

/** 类型渲染：彩色 Tag */
export function renderTypeTag(text: any) {
  if (!text) {
    return h('span', { style: 'color:var(--shop-text-tertiary)' }, '-');
  }
  return h(Tag, { color: TYPE_COLOR_MAP[text] || 'default' }, () => getTypeText(text));
}

/** 状态渲染：Y 绿色启用 / N 红色停用 */
export function renderStatusTag(text: any) {
  if (text === 'Y') {
    return h(Tag, { color: 'green' }, () => '启用');
  }
  if (text === 'N') {
    return h(Tag, { color: 'red' }, () => '停用');
  }
  return h('span', { style: 'color:var(--shop-text-tertiary)' }, '-');
}

export type PromotionPhase = '未开始' | '进行中' | '已结束' | '-';

/**
 * 活动阶段：完全由 startTime / endTime 与浏览器当前时间算出。
 * 缺时间或时间非法时返回 '-'，不猜。
 */
export function getPromotionPhase(record: Recordable | null | undefined): PromotionPhase {
  const start = record?.startTime ? dayjs(record.startTime) : null;
  const end = record?.endTime ? dayjs(record.endTime) : null;
  const startValid = !!start && start.isValid();
  const endValid = !!end && end.isValid();
  if (!startValid && !endValid) {
    return '-';
  }
  const now = dayjs();
  if (startValid && start && now.isBefore(start)) {
    return '未开始';
  }
  if (endValid && end && now.isAfter(end)) {
    return '已结束';
  }
  return '进行中';
}

/** 阶段 -> Tag 颜色 */
export function renderPhaseTag(record: Recordable) {
  const phase = getPromotionPhase(record);
  if (phase === '进行中') {
    return h(Tag, { color: 'green', style: 'margin-right:0' }, () => phase);
  }
  if (phase === '未开始') {
    return h(Tag, { color: 'blue', style: 'margin-right:0' }, () => phase);
  }
  if (phase === '已结束') {
    return h(Tag, { color: 'default', style: 'margin-right:0' }, () => phase);
  }
  return h('span', { style: 'color:var(--shop-text-tertiary)' }, '-');
}

/**
 * 时间窗列：阶段 Tag + 「开始 ~ 结束」。
 * 阶段的判定依据与前台倒计时一致（endTime），运营在列表里就能看出这条是不是还在跑。
 */
export function renderTimeWindow(record: Recordable) {
  const start = record?.startTime || '-';
  const end = record?.endTime || '-';
  return h('div', { style: 'display:flex;flex-direction:column;gap:2px;line-height:1.4' }, [
    h('div', {}, [renderPhaseTag(record)]),
    h('div', { style: 'font-size:12px;color:var(--shop-text-secondary)' }, `${start} ~ ${end}`),
  ]);
}

/** 金额展示：null/'' 一律显示 '-'，不要把空值当 0 */
function formatAmount(value: any): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const num = Number(value);
  return Number.isFinite(num) ? num.toFixed(2) : String(value);
}

/**
 * 「力度」列：按活动类型展示不同字段，避免把折扣率当金额读。
 * - DIRECT   直降金额
 * - PERCENT  折扣率（8.50 表示 8.5 折）
 * - FULL_CUT 满减门槛
 * - SECKILL  限量（+ 每人限购）
 */
export function getActivityValueText(record: Recordable): string {
  const type = record?.type;
  if (type === 'DIRECT') {
    return `直降 ¥${formatAmount(record.discountValue)}`;
  }
  if (type === 'PERCENT') {
    if (record.discountValue === null || record.discountValue === undefined || record.discountValue === '') {
      return '折扣率未设置';
    }
    // 8.5 表示 8.5 折（不是 85 折，也不是 0.85 折）
    return `${Number(record.discountValue)} 折`;
  }
  if (type === 'FULL_CUT') {
    return `满 ¥${formatAmount(record.thresholdAmount)} 可用`;
  }
  if (type === 'SECKILL') {
    if (record.seckillStock === null || record.seckillStock === undefined || record.seckillStock === '') {
      return '限量未设置';
    }
    const limit = record.limitPerUser;
    return limit ? `限量 ${record.seckillStock} / 每人限购 ${limit}` : `限量 ${record.seckillStock}`;
  }
  return '-';
}

// 列表数据
export const columns: BasicColumn[] = [
  {
    title: '活动编码',
    align: 'center',
    dataIndex: 'code',
    width: 170,
  },
  {
    title: '活动名称',
    align: 'left',
    dataIndex: 'title',
    width: 220,
    customRender: ({ record }) => {
      const title = record.title || '-';
      const sub = record.subTitle ? String(record.subTitle) : '';
      return h('div', { style: 'min-width:0' }, [
        h('div', { style: 'font-weight:500;color:var(--shop-text);overflow:hidden;text-overflow:ellipsis;white-space:nowrap', title }, title),
        sub ? h('div', { style: 'font-size:12px;color:var(--shop-text-secondary)', title: sub }, sub) : null,
      ]);
    },
  },
  {
    title: '所属店铺',
    align: 'left',
    dataIndex: 'storeName',
    width: 160,
    customRender: ({ record }) => h('span', record.storeName || record.storeId || '-'),
  },
  {
    title: '类型',
    align: 'center',
    dataIndex: 'type',
    width: 100,
    customRender: ({ text }) => renderTypeTag(text),
  },
  {
    title: '活动时间窗',
    align: 'left',
    dataIndex: 'startTime',
    width: 300,
    customRender: ({ record }) => renderTimeWindow(record),
  },
  {
    title: '力度',
    align: 'center',
    dataIndex: 'discountValue',
    width: 170,
    customRender: ({ record }) => h('span', getActivityValueText(record)),
  },
  {
    title: '状态',
    align: 'center',
    dataIndex: 'status',
    width: 90,
    customRender: ({ text }) => renderStatusTag(text),
  },
  {
    title: '关联商品数',
    align: 'center',
    dataIndex: 'productCount',
    width: 110,
    customRender: ({ text }) => {
      const count = Number(text ?? 0);
      if (!Number.isFinite(count) || count <= 0) {
        return h(Tooltip, { title: '该活动还没有关联商品，前台不会产生实际优惠' }, () => h(Tag, { color: 'default' }, () => '0'));
      }
      return h(Tag, { color: 'cyan' }, () => String(count));
    },
  },
  {
    title: '创建时间',
    align: 'center',
    dataIndex: 'createTime',
    width: 170,
  },
];

// 高级查询数据
export const superQuerySchema = {
  code: { title: '活动编码', order: 0, view: 'text', type: 'string' },
  title: { title: '活动名称（查翻译表与主表）', order: 1, view: 'text', type: 'string' },
  type: { title: '活动类型: DIRECT-直降, PERCENT-折扣, FULL_CUT-满减, SECKILL-秒杀', order: 2, view: 'text', type: 'string' },
  status: { title: '状态: Y-启用, N-停用', order: 3, view: 'text', type: 'string' },
  startTime: { title: '开始时间', order: 4, view: 'datetime', type: 'string' },
  endTime: { title: '结束时间', order: 5, view: 'datetime', type: 'string' },
};
