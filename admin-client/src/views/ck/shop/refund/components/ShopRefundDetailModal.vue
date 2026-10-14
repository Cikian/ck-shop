<template>
  <j-modal :title="title" :width="1100" :maxHeight="640" :visible="visible" @cancel="handleCancel">
    <!-- 只读详情：只给一个关闭按钮 -->
    <template #footer>
      <a-button @click="handleCancel">关闭</a-button>
    </template>
    <a-spin :spinning="loading">
      <template v-if="detail">
        <!-- ==================== 基本信息 ==================== -->
        <a-card :bordered="false" size="small" class="detail-card" title="退款单信息">
          <a-descriptions bordered size="small" :column="{ xs: 1, sm: 2, xl: 3 }">
            <a-descriptions-item label="退款单号">{{ detail.refundNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="订单号">{{ detail.orderNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="支付单号">{{ detail.paymentNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="申请账号">{{ detail.username || '-' }}</a-descriptions-item>
            <a-descriptions-item label="所属店铺">{{ detail.storeId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="退款类型">{{ detail.refundTypeLabel || detail.refundType || '-' }}</a-descriptions-item>
            <a-descriptions-item label="退款金额">
              <span class="amount-strong">¥{{ formatAmount(detail.refundAmount) }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="退款件数"> {{ detail.totalQuantity ?? '-' }} 件 / {{ detail.itemCount ?? '-' }} 种 </a-descriptions-item>
            <a-descriptions-item label="退款状态">
              <a-tag :color="statusColor(detail.status)">{{ detail.statusLabel || detail.status || '-' }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="申请时间">{{ detail.applyTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="申请原因">{{ detail.applyReason || '-' }}</a-descriptions-item>
            <a-descriptions-item label="申请说明">{{ detail.applyDesc || '-' }}</a-descriptions-item>
          </a-descriptions>

          <a-image-preview-group v-if="images.length" class="mt-2">
            <a-image v-for="(img, index) in images" :key="index" :src="img" :width="80" :height="80" style="object-fit: cover" />
          </a-image-preview-group>
        </a-card>

        <!-- ==================== 审核信息 ==================== -->
        <a-card :bordered="false" size="small" class="detail-card" title="审核信息">
          <a-descriptions bordered size="small" :column="{ xs: 1, sm: 2, xl: 3 }">
            <a-descriptions-item label="审核人">{{ detail.auditBy || '（尚未审核）' }}</a-descriptions-item>
            <a-descriptions-item label="审核时间">{{ detail.auditTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="审核备注">{{ detail.auditRemark || '-' }}</a-descriptions-item>
            <a-descriptions-item label="拒绝原因" :span="3">{{ detail.rejectReason || '-' }}</a-descriptions-item>
          </a-descriptions>
        </a-card>

        <!-- ==================== 渠道退款结果 ==================== -->
        <a-card :bordered="false" size="small" class="detail-card" title="渠道退款结果">
          <a-descriptions bordered size="small" :column="{ xs: 1, sm: 2, xl: 3 }">
            <a-descriptions-item label="渠道退款流水号">
              {{ detail.channelRefundNo || '（未提交 / 渠道未返回）' }}
            </a-descriptions-item>
            <a-descriptions-item label="到账时间">{{ detail.refundTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="重试次数">{{ detail.retryCount ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="失败码">{{ detail.failCode || '-' }}</a-descriptions-item>
            <a-descriptions-item label="失败原因" :span="2">
              <span :style="detail.failReason ? 'color:#cf1322' : ''">{{ detail.failReason || '-' }}</span>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <!-- ==================== 退款明细行 ==================== -->
        <a-card :bordered="false" size="small" class="detail-card" title="退款明细">
          <a-table :columns="itemColumns" :data-source="items" :pagination="false" row-key="id" size="small" :scroll="{ x: 800 }">
            <template #summary v-if="items.length">
              <a-table-summary fixed>
                <a-table-summary-row>
                  <a-table-summary-cell :index="0" :col-span="3">合计</a-table-summary-cell>
                  <a-table-summary-cell :index="3" align="center">{{ totalQuantity }} 件</a-table-summary-cell>
                  <a-table-summary-cell :index="4" align="right">
                    <span class="amount-strong">¥{{ formatAmount(totalRefundAmount) }}</span>
                  </a-table-summary-cell>
                </a-table-summary-row>
              </a-table-summary>
            </template>
          </a-table>
        </a-card>
      </template>
    </a-spin>
  </j-modal>
</template>

<script lang="ts" setup>
  import { computed, ref } from 'vue';
  import JModal from '/@/components/Modal/src/JModal/JModal.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { detail as fetchDetail, type OrderRefundVO } from '../ShopRefund.api';
  import { formatAmount, parseApplyImages } from '../ShopRefund.data';

  const { createMessage } = useMessage();

  const title = ref('退款单详情');
  const visible = ref(false);
  const loading = ref(false);
  const detail = ref<OrderRefundVO | null>(null);

  const items = computed(() => detail.value?.items || []);
  const images = computed(() => parseApplyImages(detail.value?.applyImages));

  const totalQuantity = computed(() => items.value.reduce((sum, item) => sum + Number(item.quantity ?? 0), 0));
  const totalRefundAmount = computed(() => items.value.reduce((sum, item) => sum + Number(item.refundAmount ?? 0), 0));

  const STATUS_COLOR: Record<string, string> = {
    APPLYING: 'orange',
    REJECTED: 'red',
    REFUNDING: 'blue',
    SUCCESS: 'green',
    FAILED: 'red',
    CLOSED: 'default',
  };

  function statusColor(status?: string): string {
    return (status && STATUS_COLOR[status]) || 'default';
  }

  const itemColumns = [
    { title: '商品名称', dataIndex: 'productName', width: 260, customRender: ({ text }: any) => text || '-' },
    { title: '规格', dataIndex: 'skuName', width: 160, customRender: ({ text }: any) => text || '无规格' },
    { title: '退款单价', dataIndex: 'price', width: 120, align: 'right', customRender: ({ text }: any) => `¥${formatAmount(text)}` },
    { title: '退款数量', dataIndex: 'quantity', width: 100, align: 'center' },
    {
      title: '本行退款金额',
      dataIndex: 'refundAmount',
      width: 140,
      align: 'right',
      customRender: ({ text }: any) => `¥${formatAmount(text)}`,
    },
  ];

  /** 打开弹窗：按退款单号拉取详情（含退款明细行） */
  async function open(record: Recordable) {
    const refundNo = record?.refundNo;
    if (!refundNo) {
      createMessage.warning('缺少退款单号，无法查看详情');
      return;
    }
    title.value = `退款单详情 - ${refundNo}`;
    visible.value = true;
    loading.value = true;
    detail.value = null;
    try {
      detail.value = await fetchDetail(refundNo);
    } catch (error: any) {
      createMessage.error(error?.message || '获取退款单详情失败');
    } finally {
      loading.value = false;
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

  .amount-strong {
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
</style>
