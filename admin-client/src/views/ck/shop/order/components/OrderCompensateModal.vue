<template>
  <j-modal
    :title="title"
    :width="640"
    :visible="visible"
    :confirmLoading="saving"
    :okText="result ? '关闭' : '立即补偿'"
    cancelText="取消"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <template v-if="!result">
      <a-alert
        type="warning"
        show-icon
        class="mb-3"
        message="人工补偿 = 主动向支付渠道查询这笔订单的真实支付结果"
        description="适用场景：用户说「我付了钱但订单还是待支付」、渠道回调丢失、超时关单后疑似收到钱。补偿只做「查询并回写」，不会重复收款。"
      />

      <a-descriptions bordered size="small" :column="2" class="mb-3">
        <a-descriptions-item label="订单号">{{ detail.orderNo || '-' }}</a-descriptions-item>
        <a-descriptions-item label="订单状态">
          {{ detail.orderStatusLabel || detail.orderStatus || '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="支付状态">
          {{ detail.payStatusLabel || detail.payStatus || '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="应付 / 已收"> {{ formatAmount(detail.payAmount) }} / {{ formatAmount(detail.paidAmount) }} </a-descriptions-item>
      </a-descriptions>
    </template>

    <!-- 补偿结果必须完整展示：scanned=0 与 succeeded=1 是完全不同的结论，
         只弹一句"操作成功"会让客服误以为钱已经找回来了 -->
    <template v-else>
      <a-result
        :status="result.succeeded && Number(result.succeeded) > 0 ? 'success' : 'info'"
        :title="resultTitle"
        :sub-title="result.message || ''"
      >
        <template #extra>
          <a-descriptions bordered size="small" :column="2" style="width: 100%">
            <a-descriptions-item label="扫描订单数">{{ result.scanned ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="补偿成功数">{{ result.succeeded ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="跳过数">{{ result.skipped ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="说明">{{ result.message || '-' }}</a-descriptions-item>
          </a-descriptions>
        </template>
      </a-result>
    </template>
  </j-modal>
</template>

<script lang="ts" setup>
  import { computed, ref } from 'vue';
  import JModal from '/@/components/Modal/src/JModal/JModal.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { compensate, type OrderTaskResult } from '../ShopOrder.api';
  import { formatAmount } from '../ShopOrder.data';

  const emit = defineEmits(['success']);
  const { createMessage } = useMessage();

  const title = ref('人工补偿');
  const visible = ref(false);
  const saving = ref(false);
  const detail = ref<Recordable>({});
  /** 补偿结果；非空即表示已跑过一次，弹窗切换成结果视图 */
  const result = ref<OrderTaskResult | null>(null);

  const resultTitle = computed(() => {
    const succeeded = Number(result.value?.succeeded ?? 0);
    if (succeeded > 0) {
      return '补偿成功：已按渠道结果回写订单';
    }
    return '补偿完成，但未发现新的支付结果';
  });

  function open(record: Recordable) {
    detail.value = record || {};
    result.value = null;
    title.value = `人工补偿 - ${record?.orderNo || ''}`;
    visible.value = true;
  }

  async function handleOk() {
    // 结果视图下「关闭」只是关窗，成功回调由上一轮已经发出，这里不重复触发
    if (result.value) {
      visible.value = false;
      return;
    }
    saving.value = true;
    try {
      const res = await compensate(detail.value?.orderNo);
      result.value = res || {};
      const succeeded = Number(res?.succeeded ?? 0);
      if (succeeded > 0) {
        createMessage.success(`补偿成功：${res?.message || '已回写订单支付结果'}`);
        emit('success');
      } else {
        createMessage.warning(res?.message || '补偿完成，未发现新的支付结果');
      }
    } finally {
      saving.value = false;
    }
  }

  function handleCancel() {
    visible.value = false;
  }

  defineExpose({ open });
</script>
