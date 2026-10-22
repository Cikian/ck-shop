import { h } from 'vue';
import { Tag, Tooltip } from 'ant-design-vue';
import { BasicColumn } from '/@/components/Table';

/**
 * 退款管理 · 列表列定义与展示口径
 *
 * 与订单模块同一套口径：
 * 1. 状态文案一律用后端下发的 {@code statusLabel}（这里**不再维护第二份映射表**），
 *    本文件里的映射只负责"颜色"这一层纯展示信息；
 * 2. 金额统一 2 位小数，空值显示 '-'；
 * 3. 重试按钮只在 FAILED / REFUNDING 出现——退款中重试是"渠道结果未知"的正常操作，
 *    成功/已撤销的退款单重试会变成重复退款。
 */

/** 时间格式：全模块统一 */
export const REFUND_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** 退款单状态选项（文案与后端 RefundOrderStatus 枚举一致） */
export const REFUND_ORDER_STATUS_OPTIONS = [
  { label: '待审核', value: 'APPLYING' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '退款中', value: 'REFUNDING' },
  { label: '退款成功', value: 'SUCCESS' },
  { label: '退款失败', value: 'FAILED' },
  { label: '已撤销', value: 'CLOSED' },
];

/** 退款单状态 -> Tag 颜色（纯展示） */
const REFUND_ORDER_STATUS_COLOR: Record<string, string> = {
  APPLYING: 'orange',
  REJECTED: 'red',
  REFUNDING: 'blue',
  SUCCESS: 'green',
  FAILED: 'red',
  CLOSED: 'default',
};

/**
 * 金额展示：统一 2 位小数；null / '' 显示 '-'
 *
 * 与订单模块各自持有一份：两个页面分属不同权限域（shop-order / shop-refund），
 * 一个纯粹的格式化函数不值得让它们互相依赖。
 */
export function formatAmount(value: any): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const num = Number(value);
  return Number.isFinite(num) ? num.toFixed(2) : String(value);
}

/**
 * 解析申请凭证图片（后端存的是 JSON 数组字符串）
 *
 * 解析失败一律返回空数组：凭证只是辅助材料，不能因为一条脏数据让整页报错。
 */
export function parseApplyImages(value: any): string[] {
  if (!value) {
    return [];
  }
  if (Array.isArray(value)) {
    return value.filter((item) => !!item);
  }
  try {
    const parsed = JSON.parse(String(value));
    return Array.isArray(parsed) ? parsed.filter((item) => !!item) : [];
  } catch (e) {
    console.warn('退款凭证图片解析失败', e);
    return [];
  }
}

/** 退款单状态 Tag：文案用后端 statusLabel，颜色按状态码 */
export function renderRefundOrderStatusTag(record: Recordable) {
  const code = record?.status;
  const text = record?.statusLabel || code;
  if (!text) {
    return h('span', { style: 'color:var(--shop-text-tertiary)' }, '-');
  }
  return h(Tag, { color: REFUND_ORDER_STATUS_COLOR[code] || 'default', style: 'margin-right:0' }, () => String(text));
}

/** 是否允许重试渠道退款（后端也只允许失败/退款中重试） */
export function canRetryChannelRefund(record: Recordable): boolean {
  return record?.status === 'FAILED' || record?.status === 'REFUNDING';
}

/** 是否待审核（审核按钮的唯一显示条件） */
export function canAudit(record: Recordable): boolean {
  return record?.status === 'APPLYING';
}

// 列表数据
export const columns: BasicColumn[] = [
  {
    title: '退款单号',
    align: 'left',
    dataIndex: 'refundNo',
    width: 200,
    fixed: 'left',
    customRender: ({ record }) => h('span', { style: 'font-weight:500' }, record.refundNo || '-'),
  },
  {
    title: '订单号',
    align: 'left',
    dataIndex: 'orderNo',
    width: 200,
    customRender: ({ text }) => h('span', text || '-'),
  },
  {
    title: '退款类型',
    align: 'center',
    dataIndex: 'refundType',
    width: 100,
    customRender: ({ record }) => {
      const text = record.refundTypeLabel || record.refundType;
      if (!text) {
        return h('span', { style: 'color:var(--shop-text-tertiary)' }, '-');
      }
      return h(Tag, { color: record.refundType === 'FULL' ? 'red' : 'orange', style: 'margin-right:0' }, () => String(text));
    },
  },
  {
    title: '退款金额',
    align: 'right',
    dataIndex: 'refundAmount',
    width: 120,
    customRender: ({ text }) => h('span', { style: 'font-variant-numeric:tabular-nums;font-weight:500' }, formatAmount(text)),
  },
  {
    title: '状态',
    align: 'center',
    dataIndex: 'status',
    width: 100,
    customRender: ({ record }) => renderRefundOrderStatusTag(record),
  },
  {
    title: '申请原因',
    align: 'left',
    dataIndex: 'applyReason',
    width: 160,
    customRender: ({ record }) => {
      const reason = record.applyReason || '-';
      const desc = record.applyDesc;
      return h('div', { style: 'line-height:1.5' }, [
        h('div', reason),
        desc
          ? h(Tooltip, { title: desc }, () =>
              h(
                'div',
                {
                  style: 'font-size:12px;color:var(--shop-text-secondary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:150px',
                },
                desc
              )
            )
          : null,
      ]);
    },
  },
  {
    title: '申请时间',
    align: 'center',
    dataIndex: 'applyTime',
    width: 170,
    customRender: ({ text }) => h('span', text || '-'),
  },
  {
    title: '审核人',
    align: 'left',
    dataIndex: 'auditBy',
    width: 110,
    customRender: ({ text }) => h('span', text || '-'),
  },
  {
    title: '审核时间',
    align: 'center',
    dataIndex: 'auditTime',
    width: 170,
    customRender: ({ text }) => h('span', text || '-'),
  },
  {
    title: '渠道退款流水号',
    align: 'left',
    dataIndex: 'channelRefundNo',
    width: 200,
    customRender: ({ record }) => {
      const no = record.channelRefundNo;
      if (no) {
        return h('span', no);
      }
      // 失败原因比重试次数更有价值：客服据此判断"是该重试还是该找渠道"
      return record.failReason
        ? h(Tooltip, { title: record.failReason }, () => h('span', { style: 'color:var(--shop-error)' }, record.failCode ? `失败：${record.failCode}` : '失败'))
        : h('span', { style: 'color:var(--shop-text-tertiary)' }, '-');
    },
  },
  {
    title: '到账时间',
    align: 'center',
    dataIndex: 'refundTime',
    width: 170,
    customRender: ({ text }) => h('span', text || '-'),
  },
];
