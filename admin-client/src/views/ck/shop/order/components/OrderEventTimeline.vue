<template>
  <div class="order-event-timeline">
    <div v-if="events.length" class="timeline-toolbar">
      <span class="text-xs text-gray-400"> 共 {{ events.length }} 条操作流水（只追加，不设删除标记；这是订单事故复盘的第一手材料） </span>
      <a-radio-group v-model:value="sortOrder" size="small" button-style="solid">
        <a-radio-button value="desc">最新在前</a-radio-button>
        <a-radio-button value="asc">最早在前</a-radio-button>
      </a-radio-group>
    </div>

    <a-empty v-if="!events.length" description="该订单暂无操作流水" />

    <a-timeline v-else class="timeline-body">
      <a-timeline-item v-for="event in sortedEvents" :key="event.id || event.occurredTime + event.eventType" :color="dotColor(event)">
        <div class="event-head">
          <a-tag :color="operatorColor(event.operatorType)" style="margin-right: 6px">
            {{ event.operatorTypeLabel || event.operatorType || '未知来源' }}
          </a-tag>
          <span class="event-type">{{ event.eventTypeLabel || event.eventType || '-' }}</span>
          <span class="event-time">{{ event.occurredTime || event.createTime || '-' }}</span>
        </div>

        <div class="event-meta">
          <span v-if="event.operatorName">操作者：{{ event.operatorName }}</span>
          <span v-if="event.amount !== null && event.amount !== undefined">涉及金额：¥{{ formatAmount(event.amount) }}</span>
          <span v-if="event.clientIp">IP：{{ event.clientIp }}</span>
        </div>

        <!-- 状态轨迹可回放：不必靠猜"这条订单当时是怎么走到这一步的" -->
        <div v-if="statusFlow(event)" class="event-status">
          {{ statusFlow(event) }}
        </div>

        <div v-if="event.remark" class="event-remark">{{ event.remark }}</div>

        <!-- 事件明细 JSON（金额快照 / 渠道报文摘要 / 物流信息），排查时不裁剪 -->
        <a-collapse v-if="event.detail" ghost class="event-detail">
          <a-collapse-panel key="detail" header="事件明细（原始 JSON）">
            <pre class="event-detail-json">{{ prettyDetail(event.detail) }}</pre>
          </a-collapse-panel>
        </a-collapse>
      </a-timeline-item>
    </a-timeline>
  </div>
</template>

<script lang="ts" setup>
  import { computed, ref } from 'vue';
  import { type OrderEventVO } from '../ShopOrder.api';
  import { formatAmount } from '../ShopOrder.data';

  const props = defineProps<{
    /** 后端已按发生时间升序返回，这里只做展示排序 */
    events?: OrderEventVO[];
  }>();

  const events = computed<OrderEventVO[]>(() => props.events || []);
  const sortOrder = ref<'desc' | 'asc'>('desc');

  const sortedEvents = computed(() => {
    const list = [...events.value];
    return sortOrder.value === 'desc' ? list.reverse() : list;
  });

  /**
   * 事件里的 fromStatus / toStatus 后端**没有**下发中文（只对事件类型、操作者类型给了 label），
   * 所以这里维护一份订单/支付状态枚举映射——这是本模块里唯一"自己维护"的状态文案，
   * 且仅用于流水的前后状态展示，不参与任何业务判断。
   */
  const ORDER_STATUS_TEXT: Record<string, string> = {
    WAIT_PAY: '待支付',
    WAIT_SHIP: '待发货',
    SHIPPED: '已发货',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    CLOSED: '已关闭',
  };

  const PAY_STATUS_TEXT: Record<string, string> = {
    UNPAID: '未支付',
    PAID: '已支付',
    PARTIAL_REFUND: '部分退款',
    REFUNDED: '已退款',
  };

  function statusText(map: Record<string, string>, code?: string): string {
    if (!code) {
      return '';
    }
    return map[code] || code;
  }

  /** 拼接"订单状态 A → B；支付状态 C → D"，没有变化则不显示 */
  function statusFlow(event: OrderEventVO): string {
    const parts: string[] = [];
    if (event.fromStatus || event.toStatus) {
      if (event.fromStatus !== event.toStatus) {
        parts.push(`订单状态：${statusText(ORDER_STATUS_TEXT, event.fromStatus) || '—'} → ${statusText(ORDER_STATUS_TEXT, event.toStatus)}`);
      } else {
        parts.push(`订单状态：${statusText(ORDER_STATUS_TEXT, event.toStatus)}`);
      }
    }
    if (event.fromPayStatus !== event.toPayStatus) {
      parts.push(`支付状态：${statusText(PAY_STATUS_TEXT, event.fromPayStatus) || '—'} → ${statusText(PAY_STATUS_TEXT, event.toPayStatus)}`);
    }
    return parts.join('　|　');
  }

  /** 时间线圆点颜色按"谁干的"区分：事故复盘时最先要回答的就是这个问题 */
  function dotColor(event: OrderEventVO): string {
    const map: Record<string, string> = {
      USER: 'blue',
      ADMIN: 'red',
      SYSTEM: 'gray',
      CHANNEL: 'green',
    };
    return map[event.operatorType] || 'gray';
  }

  function operatorColor(type?: string): string {
    const map: Record<string, string> = {
      USER: 'blue',
      ADMIN: 'red',
      SYSTEM: 'default',
      CHANNEL: 'green',
    };
    return (type && map[type]) || 'default';
  }

  /** 明细 JSON 美化展示；解析失败就原样输出，绝不吞掉线索 */
  function prettyDetail(detail?: string): string {
    if (!detail) {
      return '';
    }
    try {
      return JSON.stringify(JSON.parse(detail), null, 2);
    } catch (e) {
      return detail;
    }
  }
</script>

<style lang="less" scoped>
  .order-event-timeline {
    .timeline-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
    }

    .timeline-body {
      padding-top: 4px;
      max-height: 520px;
      overflow-y: auto;
    }

    .event-head {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 4px;

      .event-type {
        font-weight: 500;
        color: rgba(0, 0, 0, 0.88);
      }

      .event-time {
        margin-left: auto;
        font-size: 12px;
        color: rgba(0, 0, 0, 0.45);
      }
    }

    .event-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-top: 2px;
      font-size: 12px;
      color: rgba(0, 0, 0, 0.45);
    }

    .event-status {
      margin-top: 4px;
      font-size: 12px;
      color: rgba(0, 0, 0, 0.65);
    }

    .event-remark {
      margin-top: 4px;
      color: rgba(0, 0, 0, 0.75);
      word-break: break-all;
    }

    .event-detail {
      margin-top: 2px;

      :deep(.ant-collapse-header) {
        padding: 2px 0 !important;
        font-size: 12px;
      }

      :deep(.ant-collapse-content-box) {
        padding: 4px 0 !important;
      }
    }

    .event-detail-json {
      max-height: 220px;
      margin: 0;
      padding: 8px;
      overflow: auto;
      font-size: 12px;
      line-height: 1.5;
      background: rgba(0, 0, 0, 0.03);
      border-radius: 4px;
      white-space: pre-wrap;
      word-break: break-all;
    }
  }
</style>
