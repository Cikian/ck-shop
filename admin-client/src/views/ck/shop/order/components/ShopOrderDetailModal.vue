<template>
  <j-modal :title="title" :width="1280" :maxHeight="680" :visible="visible" @cancel="handleCancel">
    <!-- 只读详情：只给一个关闭按钮，不出现"确定"这种会让人误以为能改数据的按钮 -->
    <template #footer>
      <a-button @click="handleCancel">关闭</a-button>
    </template>
    <a-spin :spinning="loading">
      <template v-if="detail">
        <!-- ==================== 基本信息 ==================== -->
        <a-card :bordered="false" size="small" class="detail-card" title="基本信息">
          <template #extra>
            <a-tag v-if="isArchived" color="default">已归档（数据完整保留，未删除）</a-tag>
          </template>
          <a-descriptions bordered size="small" :column="{ xs: 1, sm: 2, xl: 3 }">
            <a-descriptions-item label="订单号">{{ detail.orderNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="所属店铺">{{ detail.storeName || detail.storeId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="用户账号">{{ detail.username || '-' }}</a-descriptions-item>
            <a-descriptions-item label="订单状态">
              <a-tag :color="orderStatusColor(detail.orderStatus)">{{ detail.orderStatusLabel || detail.orderStatus || '-' }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="支付状态">{{ detail.payStatusLabel || detail.payStatus || '-' }}</a-descriptions-item>
            <a-descriptions-item label="退款状态">{{ detail.refundStatusLabel || detail.refundStatus || '-' }}</a-descriptions-item>
            <a-descriptions-item label="下单时间">{{ detail.createTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="支付时间">{{ detail.payTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="支付截止">{{ detail.payDeadline || '-' }}</a-descriptions-item>
            <a-descriptions-item label="发货时间">{{ detail.shipTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="完成时间">{{ detail.finishTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="取消/关闭时间">{{ detail.cancelTime || detail.closeTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="物流公司">{{ detail.logisticsCompany || '-' }}</a-descriptions-item>
            <a-descriptions-item label="物流单号">{{ detail.logisticsNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="发货备注">{{ detail.shipRemark || '-' }}</a-descriptions-item>
            <a-descriptions-item label="取消来源">{{ cancelTypeText(detail.cancelType) }}</a-descriptions-item>
            <a-descriptions-item label="取消/关闭原因" :span="2">{{ detail.cancelReason || '-' }}</a-descriptions-item>
            <a-descriptions-item label="下单来源">{{ detail.orderSource || '-' }}</a-descriptions-item>
            <a-descriptions-item label="支付渠道">
              {{ detail.payChannel || '-' }}<span v-if="detail.payType"> / {{ detail.payType }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="下单 IP">{{ detail.clientIp || '-' }}</a-descriptions-item>
            <a-descriptions-item label="渠道 / UTM">
              {{ [detail.channelCode, detail.utmSource].filter((item) => !!item).join(' / ') || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="User-Agent" :span="2">
              <span class="break-all">{{ detail.userAgent || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="买家备注">{{ detail.buyerRemark || '-' }}</a-descriptions-item>
            <a-descriptions-item label="卖家备注">{{ detail.sellerRemark || '-' }}</a-descriptions-item>
            <a-descriptions-item label="商品件数">{{ detail.totalQuantity ?? '-' }} 件 / {{ detail.itemCount ?? '-' }} 种</a-descriptions-item>
          </a-descriptions>
        </a-card>

        <!-- ==================== 金额明细 ==================== -->
        <a-card :bordered="false" size="small" class="detail-card" title="金额明细">
          <template #extra>
            <a-tag v-if="canViewCost" color="purple">成本 / 毛利为后台专属字段，前台不可见</a-tag>
            <a-tag v-else color="default">无成本/毛利查看权限（shop-order:cost）</a-tag>
          </template>
          <a-descriptions bordered size="small" :column="{ xs: 2, sm: 3, xl: 4 }">
            <a-descriptions-item label="吊牌价合计">¥{{ formatAmount(detail.totalAmount) }}</a-descriptions-item>
            <a-descriptions-item label="成交金额">¥{{ formatAmount(detail.productAmount) }}</a-descriptions-item>
            <a-descriptions-item label="促销优惠">¥{{ formatAmount(detail.promotionDiscount) }}</a-descriptions-item>
            <a-descriptions-item label="优惠券抵扣">¥{{ formatAmount(detail.couponDiscount) }}</a-descriptions-item>
            <a-descriptions-item label="积分抵扣">¥{{ formatAmount(detail.pointsDiscount) }}</a-descriptions-item>
            <a-descriptions-item label="会员优惠">¥{{ formatAmount(detail.memberDiscount) }}</a-descriptions-item>
            <a-descriptions-item label="优惠合计">¥{{ formatAmount(detail.discountAmount) }}</a-descriptions-item>
            <a-descriptions-item label="运费">¥{{ formatAmount(detail.freightAmount) }}</a-descriptions-item>
            <a-descriptions-item label="其它费用">¥{{ formatAmount(detail.otherFee) }}</a-descriptions-item>
            <a-descriptions-item label="应付金额">
              <span class="amount-strong">¥{{ formatAmount(detail.payAmount) }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="已支付（实收）">
              <span class="amount-strong">¥{{ formatAmount(detail.paidAmount) }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="已退款">
              <span class="amount-strong amount-refund">¥{{ formatAmount(detail.refundAmount) }}</span>
            </a-descriptions-item>
            <!-- 成本/毛利：无 shop-order:cost 时后端已置空，这里整块不渲染 -->
            <template v-if="canViewCost">
              <a-descriptions-item label="成本合计">¥{{ formatAmount(detail.costAmount) }}</a-descriptions-item>
              <a-descriptions-item label="毛利">
                <span class="amount-strong">¥{{ formatAmount(detail.grossProfit) }}</span>
                <span class="text-xs text-gray-400">（不扣退款）</span>
              </a-descriptions-item>
            </template>
            <a-descriptions-item label="商品总重量">{{ detail.totalWeight ?? '-' }} kg</a-descriptions-item>
            <a-descriptions-item label="币种">{{ detail.currency || '-' }}</a-descriptions-item>
          </a-descriptions>
        </a-card>

        <!-- ==================== 收货地址快照 ==================== -->
        <a-card :bordered="false" size="small" class="detail-card" title="收货地址快照">
          <template #extra>
            <span class="text-xs text-gray-400">手机号由后端脱敏后下发</span>
          </template>
          <a-descriptions bordered size="small" :column="{ xs: 1, sm: 2, xl: 3 }">
            <a-descriptions-item label="收货人">{{ detail.receiverName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="联系电话">{{ detail.receiverPhone || '-' }}</a-descriptions-item>
            <a-descriptions-item label="邮编">{{ detail.receiverPostalCode || '-' }}</a-descriptions-item>
            <a-descriptions-item label="省 / 市 / 区">
              {{ [detail.receiverProvince, detail.receiverCity, detail.receiverDistrict].filter((item) => !!item).join(' ') || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="详细地址" :span="2">{{ detail.receiverAddress || '-' }}</a-descriptions-item>
            <a-descriptions-item label="地址标签">{{ detail.receiverTag || '-' }}</a-descriptions-item>
          </a-descriptions>
          <a-collapse v-if="addressSnapshotText" ghost class="mt-2">
            <a-collapse-panel key="snapshot" header="完整地址 JSON 快照（含地址簿 ID 与全部原始字段，用于事后逐字段核对）">
              <pre class="detail-json">{{ addressSnapshotText }}</pre>
            </a-collapse-panel>
          </a-collapse>
        </a-card>

        <!-- ==================== 商品明细 ==================== -->
        <a-card :bordered="false" size="small" class="detail-card" title="商品明细">
          <a-table :columns="itemColumns" :data-source="items" :pagination="false" row-key="id" size="small" :scroll="{ x: 1500 }" />
        </a-card>

        <!-- ==================== 支付单 ==================== -->
        <a-card :bordered="false" size="small" class="detail-card" title="支付单列表">
          <a-table :columns="paymentColumns" :data-source="payments" :pagination="false" row-key="id" size="small" :scroll="{ x: 1300 }" />
        </a-card>

        <!-- ==================== 退款单 ==================== -->
        <a-card :bordered="false" size="small" class="detail-card" title="退款单列表">
          <a-table :columns="refundColumns" :data-source="refunds" :pagination="false" row-key="id" size="small" :scroll="{ x: 1300 }" />
        </a-card>

        <!-- ==================== 操作流水 ==================== -->
        <a-card :bordered="false" size="small" class="detail-card" title="完整操作流水（时间线）">
          <OrderEventTimeline :events="events" />
        </a-card>
      </template>

      <!-- 详情加载失败：不能只剩一个空白弹窗，给出原因与重试入口 -->
      <a-result v-else-if="loadError" status="warning" title="订单详情加载失败" :sub-title="loadError">
        <template #extra>
          <a-button type="primary" :loading="loading" @click="retryDetail">重试</a-button>
          <a-button @click="handleCancel">关闭</a-button>
        </template>
      </a-result>
    </a-spin>
  </j-modal>
</template>

<script lang="ts" setup>
  import { computed, h, ref } from 'vue';
  import { Tag } from 'ant-design-vue';
  import JModal from '/@/components/Modal/src/JModal/JModal.vue';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { detail as fetchDetail, type AdminOrderDetailVO, type AdminOrderItemVO, type OrderEventVO } from '../ShopOrder.api';
  import { formatAmount } from '../ShopOrder.data';
  import OrderEventTimeline from './OrderEventTimeline.vue';

  const { createMessage } = useMessage();

  const title = ref('订单详情');
  const visible = ref(false);
  const loading = ref(false);
  const detail = ref<AdminOrderDetailVO | null>(null);
  /** 详情加载失败原因：非空时弹窗展示重试页，而不是空白 */
  const loadError = ref('');
  /** 最近一次打开的订单号：重试时复用 */
  const currentOrderNo = ref('');
  /** 成本/毛利字段级权限（与后端 shop-order:cost 同一口径） */
  const { hasPermission } = usePermission();
  const canViewCost = computed(() => hasPermission('shop-order:cost'));

  const isArchived = computed(() => Number(detail.value?.delFlag) === 1);
  const items = computed<AdminOrderItemVO[]>(() => detail.value?.items || []);
  const payments = computed(() => detail.value?.payments || []);
  const refunds = computed(() => detail.value?.refunds || []);
  const events = computed<OrderEventVO[]>(() => detail.value?.events || []);

  /** 完整地址 JSON：优先用详情 VO 的 addressSnapshotJson，回退地址快照字段 */
  const addressSnapshotText = computed(() => {
    const raw = detail.value?.addressSnapshotJson || detail.value?.addressSnapshot;
    if (!raw) {
      return '';
    }
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch (e) {
      return raw;
    }
  });

  const ORDER_STATUS_COLOR: Record<string, string> = {
    WAIT_PAY: 'orange',
    WAIT_SHIP: 'blue',
    SHIPPED: 'cyan',
    COMPLETED: 'green',
    CANCELLED: 'default',
    CLOSED: 'default',
  };

  function orderStatusColor(status?: string): string {
    return (status && ORDER_STATUS_COLOR[status]) || 'default';
  }

  /** 取消来源：后端下发的是枚举码，这里仅做展示翻译 */
  function cancelTypeText(type?: string): string {
    const map: Record<string, string> = {
      USER: '买家取消',
      ADMIN: '管理员关闭',
      TIMEOUT: '超时未支付关闭',
      REFUND: '退款导致关闭',
    };
    return (type && map[type]) || '-';
  }

  /** 支付单状态颜色（文案用后端 statusLabel） */
  const PAYMENT_STATUS_COLOR: Record<string, string> = {
    INIT: 'default',
    PENDING: 'orange',
    SUCCESS: 'green',
    FAILED: 'red',
    CLOSED: 'default',
  };

  /** 退款单状态颜色（文案用后端 statusLabel） */
  const REFUND_STATUS_COLOR: Record<string, string> = {
    APPLYING: 'orange',
    REJECTED: 'red',
    REFUNDING: 'blue',
    SUCCESS: 'green',
    FAILED: 'red',
    CLOSED: 'default',
  };

  const money = (value: any) => h('span', { style: 'font-variant-numeric:tabular-nums' }, formatAmount(value));

  const itemColumns = [
    {
      title: '商品',
      dataIndex: 'productName',
      width: 280,
      customRender: ({ record }: any) =>
        h('div', { style: 'display:flex;gap:8px;align-items:flex-start' }, [
          h('img', {
            src: record.productImage || '',
            style: 'width:40px;height:40px;object-fit:cover;border-radius:4px;border:1px solid #f0f0f0;flex:none',
          }),
          h('div', { style: 'min-width:0;line-height:1.5' }, [
            h('div', { style: 'font-weight:500' }, record.productName || '-'),
            h('div', { style: 'font-size:12px;color:rgba(0,0,0,.45)' }, record.skuName || '无规格'),
            h('div', { style: 'font-size:12px;color:rgba(0,0,0,.35)' }, `SPU：${record.spuCode || '-'} / SKU：${record.skuCode || '-'}`),
          ]),
        ]),
    },
    { title: '分类', dataIndex: 'categoryName', width: 110, customRender: ({ text }: any) => h('span', text || '-') },
    { title: '单价', dataIndex: 'price', width: 100, align: 'right', customRender: ({ text }: any) => money(text) },
    { title: '数量', dataIndex: 'quantity', width: 70, align: 'center' },
    { title: '原价小计', dataIndex: 'totalAmount', width: 110, align: 'right', customRender: ({ text }: any) => money(text) },
    { title: '成交小计', dataIndex: 'productAmount', width: 110, align: 'right', customRender: ({ text }: any) => money(text) },
    { title: '本行优惠', dataIndex: 'discountAmount', width: 110, align: 'right', customRender: ({ text }: any) => money(text) },
    { title: '实付', dataIndex: 'actualAmount', width: 110, align: 'right', customRender: ({ text }: any) => money(text) },
    { title: '成本单价', dataIndex: 'costPrice', width: 110, align: 'right', customRender: ({ text }: any) => money(text) },
    { title: '成本小计', dataIndex: 'costAmount', width: 110, align: 'right', customRender: ({ text }: any) => money(text) },
    {
      title: '促销',
      dataIndex: 'promotionTitle',
      width: 140,
      customRender: ({ record }: any) => h('span', record.promotionTitle || '-'),
    },
    {
      title: '行退款状态',
      dataIndex: 'refundStatus',
      width: 110,
      customRender: ({ record }: any) =>
        h(
          Tag,
          { color: record.refundStatus === 'NONE' || !record.refundStatus ? 'default' : 'orange' },
          () => record.refundStatusLabel || record.refundStatus || '-'
        ),
    },
    { title: '已退数量', dataIndex: 'refundQuantity', width: 90, align: 'center', customRender: ({ text }: any) => h('span', text ?? '-') },
    { title: '已退金额', dataIndex: 'refundAmount', width: 110, align: 'right', customRender: ({ text }: any) => money(text) },
    {
      title: '本行可退',
      dataIndex: 'refundableAmount',
      width: 110,
      align: 'right',
      customRender: ({ text }: any) => h('span', { style: 'font-variant-numeric:tabular-nums;color:#0958d9' }, formatAmount(text)),
    },
  ];

  const paymentColumns = [
    { title: '支付单号', dataIndex: 'paymentNo', width: 200, customRender: ({ text }: any) => h('span', text || '-') },
    {
      title: '渠道',
      dataIndex: 'channel',
      width: 90,
      customRender: ({ record }: any) => h('span', `${record.channel || '-'}${record.payType ? ' / ' + record.payType : ''}`),
    },
    {
      title: '渠道流水号',
      dataIndex: 'channelTradeNo',
      width: 200,
      customRender: ({ text }: any) => (text ? h('span', text) : h('span', { style: 'color:rgba(0,0,0,.35)' }, '（渠道未返回）')),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      customRender: ({ record }: any) =>
        h(Tag, { color: PAYMENT_STATUS_COLOR[record.status] || 'default' }, () => record.statusLabel || record.status || '-'),
    },
    { title: '应付金额', dataIndex: 'amount', width: 110, align: 'right', customRender: ({ text }: any) => money(text) },
    { title: '渠道实收', dataIndex: 'paidAmount', width: 110, align: 'right', customRender: ({ text }: any) => money(text) },
    { title: '累计已退', dataIndex: 'refundAmount', width: 110, align: 'right', customRender: ({ text }: any) => money(text) },
    {
      title: '剩余可退',
      dataIndex: 'refundableAmount',
      width: 110,
      align: 'right',
      customRender: ({ text }: any) => h('span', { style: 'font-variant-numeric:tabular-nums;color:#0958d9' }, formatAmount(text)),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 170, customRender: ({ text }: any) => h('span', text || '-') },
    { title: '支付时间', dataIndex: 'payTime', width: 170, customRender: ({ text }: any) => h('span', text || '-') },
    {
      title: '回调次数',
      dataIndex: 'notifyCount',
      width: 90,
      align: 'center',
      customRender: ({ text }: any) => h('span', text ?? 0),
    },
    {
      title: '失败原因',
      dataIndex: 'errorMsg',
      width: 160,
      customRender: ({ record }: any) =>
        record.errorMsg ? h('span', { style: 'color:#cf1322' }, record.errorMsg) : h('span', { style: 'color:rgba(0,0,0,.35)' }, '-'),
    },
  ];

  const refundColumns = [
    { title: '退款单号', dataIndex: 'refundNo', width: 200, customRender: ({ text }: any) => h('span', text || '-') },
    {
      title: '退款类型',
      dataIndex: 'refundType',
      width: 100,
      customRender: ({ record }: any) => h('span', record.refundTypeLabel || record.refundType || '-'),
    },
    { title: '退款金额', dataIndex: 'refundAmount', width: 110, align: 'right', customRender: ({ text }: any) => money(text) },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      customRender: ({ record }: any) =>
        h(Tag, { color: REFUND_STATUS_COLOR[record.status] || 'default' }, () => record.statusLabel || record.status || '-'),
    },
    { title: '申请原因', dataIndex: 'applyReason', width: 140, customRender: ({ text }: any) => h('span', text || '-') },
    { title: '申请时间', dataIndex: 'applyTime', width: 170, customRender: ({ text }: any) => h('span', text || '-') },
    { title: '审核人', dataIndex: 'auditBy', width: 100, customRender: ({ text }: any) => h('span', text || '-') },
    { title: '审核时间', dataIndex: 'auditTime', width: 170, customRender: ({ text }: any) => h('span', text || '-') },
    { title: '拒绝原因', dataIndex: 'rejectReason', width: 150, customRender: ({ text }: any) => h('span', text || '-') },
    {
      title: '渠道退款流水号',
      dataIndex: 'channelRefundNo',
      width: 200,
      customRender: ({ text }: any) => (text ? h('span', text) : h('span', { style: 'color:rgba(0,0,0,.35)' }, '（未提交/未返回）')),
    },
    { title: '到账时间', dataIndex: 'refundTime', width: 170, customRender: ({ text }: any) => h('span', text || '-') },
    {
      title: '失败原因',
      dataIndex: 'failReason',
      width: 150,
      customRender: ({ text }: any) => (text ? h('span', { style: 'color:#cf1322' }, text) : h('span', { style: 'color:rgba(0,0,0,.35)' }, '-')),
    },
  ];

  /** 打开弹窗：按订单号拉取完整详情（含支付单/退款单/全部流水） */
  async function open(record: Recordable) {
    const orderNo = record?.orderNo;
    if (!orderNo) {
      createMessage.warning('缺少订单号，无法查看详情');
      return;
    }
    title.value = `订单详情 - ${orderNo}`;
    currentOrderNo.value = orderNo;
    visible.value = true;
    await loadDetail(orderNo);
  }

  /** 拉详情：失败时记录原因，弹窗展示「重试/关闭」而不是空白 */
  async function loadDetail(orderNo: string) {
    loading.value = true;
    loadError.value = '';
    detail.value = null;
    try {
      detail.value = await fetchDetail(orderNo);
    } catch (error: any) {
      loadError.value = error?.message || '获取订单详情失败';
      createMessage.error(loadError.value);
    } finally {
      loading.value = false;
    }
  }

  function retryDetail() {
    if (currentOrderNo.value) {
      void loadDetail(currentOrderNo.value);
    }
  }

  function handleCancel() {
    visible.value = false;
  }

  defineExpose({ open });
</script>

<style lang="less" scoped>
  .detail-card {
    margin-bottom: 12px;
  }

  .break-all {
    word-break: break-all;
  }

  .amount-strong {
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .amount-refund {
    color: #cf1322;
  }

  .detail-json {
    max-height: 260px;
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
</style>
