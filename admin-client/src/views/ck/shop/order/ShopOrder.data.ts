import { h } from 'vue';
import { Badge, Tag, Tooltip } from 'ant-design-vue';
import { BasicColumn } from '/@/components/Table';

/**
 * 订单管理 · 列表列定义与展示口径
 *
 * <h3>状态文案一律用后端下发的 label</h3>
 * 后端已经返回 {@code orderStatusLabel / payStatusLabel / refundStatusLabel}，
 * 前端**不再维护第二份状态码→中文映射表**——两份映射迟早会不一致，
 * 而不一致的那一天，运营看到的就是"数据库里没有的状态"。
 * 这里只保留"颜色"这一层纯展示信息（颜色不进业务判断）。
 *
 * <h3>金额口径</h3>
 * 后端金额是 decimal(12,2)，前端展示统一 2 位小数；
 * 空值一律显示 '-'，**不要把空值当 0**（0 与"没有这个数"是两回事）。
 */

/** 时间格式：全模块统一 */
export const ORDER_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** 订单状态选项（仅用于查询下拉，文案与后端 OrderStatus 枚举一致） */
export const ORDER_STATUS_OPTIONS = [
  { label: '待支付', value: 'WAIT_PAY' },
  { label: '待发货', value: 'WAIT_SHIP' },
  { label: '已发货', value: 'SHIPPED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '已关闭', value: 'CLOSED' },
];

/** 支付状态选项（文案与后端 PayStatus 枚举一致） */
export const PAY_STATUS_OPTIONS = [
  { label: '未支付', value: 'UNPAID' },
  { label: '已支付', value: 'PAID' },
  { label: '部分退款', value: 'PARTIAL_REFUND' },
  { label: '已退款', value: 'REFUNDED' },
];

/** 退款状态选项（订单维度，文案与后端 RefundStatus 枚举一致） */
export const REFUND_STATUS_OPTIONS = [
  { label: '无售后', value: 'NONE' },
  { label: '退款申请中', value: 'APPLYING' },
  { label: '退款已拒绝', value: 'REJECTED' },
  { label: '部分退款', value: 'PARTIAL' },
  { label: '全额退款', value: 'FULL' },
];

/** 支付渠道选项 */
export const PAY_CHANNEL_OPTIONS = [
  { label: '模拟支付', value: 'MOCK' },
  { label: '微信支付', value: 'WECHAT' },
  { label: '支付宝', value: 'ALIPAY' },
];

/** 订单状态 -> Tag 颜色（纯展示） */
const ORDER_STATUS_COLOR: Record<string, string> = {
  WAIT_PAY: 'orange',
  WAIT_SHIP: 'blue',
  SHIPPED: 'cyan',
  COMPLETED: 'green',
  CANCELLED: 'default',
  CLOSED: 'default',
};

/** 支付状态 -> Tag 颜色 */
const PAY_STATUS_COLOR: Record<string, string> = {
  UNPAID: 'default',
  PAID: 'green',
  PARTIAL_REFUND: 'orange',
  REFUNDED: 'red',
};

/** 退款状态 -> Tag 颜色 */
const REFUND_STATUS_COLOR: Record<string, string> = {
  NONE: 'default',
  APPLYING: 'orange',
  REJECTED: 'red',
  PARTIAL: 'gold',
  FULL: 'red',
};

/**
 * 金额展示：统一 2 位小数；null / '' 显示 '-'
 */
export function formatAmount(value: any): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const num = Number(value);
  return Number.isFinite(num) ? num.toFixed(2) : String(value);
}

/** 比率展示：后端给出的是 0~1 的小数（如 0.0833），这里统一转成百分比 */
export function formatRate(value: any): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const num = Number(value);
  return Number.isFinite(num) ? `${(num * 100).toFixed(2)}%` : String(value);
}

/** 通用：彩色 Tag；文案优先用后端 label，缺失时退回状态码而不是显示空白 */
function renderLabelTag(label: any, code: any, colorMap: Record<string, string>) {
  const text = label || code;
  if (!text) {
    return h('span', { style: 'color:rgba(0,0,0,.35)' }, '-');
  }
  return h(Tag, { color: colorMap[code] || 'default', style: 'margin-right:0' }, () => String(text));
}

/** 订单状态 Tag */
export function renderOrderStatusTag(record: Recordable) {
  return renderLabelTag(record?.orderStatusLabel, record?.orderStatus, ORDER_STATUS_COLOR);
}

/** 支付状态 Tag */
export function renderPayStatusTag(record: Recordable) {
  return renderLabelTag(record?.payStatusLabel, record?.payStatus, PAY_STATUS_COLOR);
}

/** 退款状态 Tag */
export function renderRefundStatusTag(record: Recordable) {
  return renderLabelTag(record?.refundStatusLabel, record?.refundStatus, REFUND_STATUS_COLOR);
}

// 列表数据
export const columns: BasicColumn[] = [
  {
    title: '订单号',
    align: 'left',
    dataIndex: 'orderNo',
    width: 200,
    fixed: 'left',
    customRender: ({ record }) => {
      const archived = Number(record.delFlag) === 1;
      return h('div', { style: 'line-height:1.5' }, [
        h('div', { style: 'font-weight:500' }, record.orderNo || '-'),
        // 归档不是删除：必须让运营一眼看出"数据还在，只是被收起来了"
        archived ? h(Tag, { color: 'default', style: 'margin-top:2px' }, () => '已归档') : null,
      ]);
    },
  },
  {
    title: '店铺',
    align: 'left',
    dataIndex: 'storeName',
    width: 150,
    customRender: ({ record }) => h('span', record.storeName || record.storeId || '-'),
  },
  {
    title: '用户账号',
    align: 'left',
    dataIndex: 'username',
    // 默认隐藏：13 列全开会到 ~1.7k px，1366 笔记本必须横向滚动；详情弹窗里有完整信息
    defaultHidden: true,
    width: 130,
    customRender: ({ text }) => h('span', text || '-'),
  },
  {
    title: '订单状态',
    align: 'center',
    dataIndex: 'orderStatus',
    width: 100,
    customRender: ({ record }) => renderOrderStatusTag(record),
  },
  {
    title: '支付状态',
    align: 'center',
    dataIndex: 'payStatus',
    width: 110,
    customRender: ({ record }) => renderPayStatusTag(record),
  },
  {
    title: '退款状态',
    align: 'center',
    dataIndex: 'refundStatus',
    // 默认隐藏：13 列全开会到 ~1.7k px，1366 笔记本必须横向滚动；详情弹窗里有完整信息
    defaultHidden: true,
    width: 110,
    customRender: ({ record }) => renderRefundStatusTag(record),
  },
  {
    title: '应付金额',
    align: 'right',
    dataIndex: 'payAmount',
    width: 110,
    customRender: ({ text }) => h('span', { style: 'font-variant-numeric:tabular-nums' }, formatAmount(text)),
  },
  {
    title: '已支付',
    align: 'right',
    dataIndex: 'paidAmount',
    // 默认隐藏：13 列全开会到 ~1.7k px，1366 笔记本必须横向滚动；详情弹窗里有完整信息
    defaultHidden: true,
    width: 110,
    customRender: ({ text }) => h('span', { style: 'font-variant-numeric:tabular-nums' }, formatAmount(text)),
  },
  {
    title: '已退款',
    align: 'right',
    dataIndex: 'refundAmount',
    // 默认隐藏：13 列全开会到 ~1.7k px，1366 笔记本必须横向滚动；详情弹窗里有完整信息
    defaultHidden: true,
    width: 110,
    customRender: ({ text }) => {
      const num = Number(text);
      const refunded = Number.isFinite(num) && num > 0;
      return h('span', { style: `font-variant-numeric:tabular-nums;${refunded ? 'color:#cf1322;font-weight:500' : ''}` }, formatAmount(text));
    },
  },
  {
    title: '商品件数',
    align: 'center',
    dataIndex: 'totalQuantity',
    // 默认隐藏：13 列全开会到 ~1.7k px，1366 笔记本必须横向滚动；详情弹窗里有完整信息
    defaultHidden: true,
    width: 90,
    customRender: ({ record }) => {
      const qty = record.totalQuantity;
      const kinds = record.itemCount;
      if (qty === null || qty === undefined) {
        return h('span', { style: 'color:rgba(0,0,0,.35)' }, '-');
      }
      return h(Tooltip, { title: `${kinds ?? 0} 种商品` }, () => h('span', `${qty} 件`));
    },
  },
  {
    title: '收货人',
    align: 'left',
    dataIndex: 'receiverName',
    // 默认隐藏：13 列全开会到 ~1.7k px，1366 笔记本必须横向滚动；详情弹窗里有完整信息
    defaultHidden: true,
    width: 150,
    customRender: ({ record }) => {
      const name = record.receiverName || '-';
      const phone = record.receiverPhone;
      return h('div', { style: 'line-height:1.5' }, [
        h('div', name),
        // 手机号是**后端已脱敏**后的值，这里原样展示，前端不做二次加工
        phone ? h('div', { style: 'font-size:12px;color:rgba(0,0,0,.45)' }, phone) : null,
      ]);
    },
  },
  {
    title: '下单时间',
    align: 'center',
    dataIndex: 'createTime',
    width: 170,
    customRender: ({ text }) => h('span', text || '-'),
  },
  {
    title: '支付时间',
    align: 'center',
    dataIndex: 'payTime',
    // 默认隐藏：13 列全开会到 ~1.7k px，1366 笔记本必须横向滚动；详情弹窗里有完整信息
    defaultHidden: true,
    width: 170,
    customRender: ({ text }) => h('span', text || '-'),
  },
  {
    title: '待处理退款',
    align: 'center',
    dataIndex: 'pendingRefundCount',
    // 默认隐藏：13 列全开会到 ~1.7k px，1366 笔记本必须横向滚动；详情弹窗里有完整信息
    defaultHidden: true,
    width: 110,
    customRender: ({ text }) => {
      const count = Number(text ?? 0);
      if (!Number.isFinite(count) || count <= 0) {
        return h('span', { style: 'color:rgba(0,0,0,.35)' }, '无');
      }
      // 待审核退款是客服的处理压力，用红点 + 数字提示，避免被埋在长列表里
      return h(Tooltip, { title: `${count} 笔退款申请待审核` }, () => h(Badge, { count, overflowCount: 99, style: 'margin-right:0' }));
    },
  },
];
