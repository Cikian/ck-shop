<template>
  <j-modal
    :title="title"
    :width="680"
    :visible="visible"
    :confirmLoading="saving"
    okText="确认关闭"
    okType="danger"
    cancelText="取消"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <!-- 已支付的订单不一定能关：先把"钱"这件事讲清楚，避免关单失败后才发现 -->
    <a-alert
      v-if="paidMoreThanRefunded"
      type="error"
      show-icon
      class="mb-3"
      message="该订单已收款且尚未全额退款，无法关闭"
      description="关闭已支付订单前必须先完成全额退款，否则会出现【订单已关闭但资金未退回】的事故。请先到退款管理完成退款，再回来关闭。"
    />
    <a-alert
      v-else
      type="warning"
      show-icon
      class="mb-3"
      message="关闭订单会同时释放库存并关闭未结算的支付单"
      description="已支付订单必须先全额退款才能关闭（后端硬校验）；关闭后订单进入终态，不能再发货。"
    />

    <a-descriptions bordered size="small" :column="2" class="mb-3">
      <a-descriptions-item label="订单号">{{ detail.orderNo || '-' }}</a-descriptions-item>
      <a-descriptions-item label="当前状态">{{ detail.orderStatusLabel || detail.orderStatus || '-' }}</a-descriptions-item>
      <a-descriptions-item label="应付金额">{{ formatAmount(detail.payAmount) }}</a-descriptions-item>
      <a-descriptions-item label="已收 / 已退"> {{ formatAmount(detail.paidAmount) }} / {{ formatAmount(detail.refundAmount) }} </a-descriptions-item>
    </a-descriptions>

    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }">
      <a-form-item label="关闭原因" name="reason">
        <a-textarea v-model:value="form.reason" :rows="3" :maxlength="255" show-count placeholder="必填。例如：买家电话要求取消 / 商品缺货无法履约" />
      </a-form-item>
    </a-form>
  </j-modal>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import JModal from '/@/components/Modal/src/JModal/JModal.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { close } from '../ShopOrder.api';
  import { formatAmount } from '../ShopOrder.data';

  const emit = defineEmits(['success']);
  const { createMessage } = useMessage();

  const title = ref('关闭订单');
  const visible = ref(false);
  const saving = ref(false);
  const detail = ref<Recordable>({});
  const formRef = ref();

  const form = reactive<{ orderNo: string; reason?: string }>({ orderNo: '', reason: undefined });

  /** 已收 > 已退：后端一定会拒绝，前端提前把原因说清楚 */
  const paidMoreThanRefunded = computed(() => {
    const paid = Number(detail.value?.paidAmount || 0);
    const refunded = Number(detail.value?.refundAmount || 0);
    return Number.isFinite(paid) && Number.isFinite(refunded) && paid > refunded;
  });

  const rules = {
    reason: [{ required: true, message: '请填写关闭原因（会写入订单操作流水）', trigger: 'blur' }],
  };

  function open(record: Recordable) {
    detail.value = record || {};
    form.orderNo = record?.orderNo || '';
    form.reason = undefined;
    title.value = `关闭订单 - ${record?.orderNo || ''}`;
    visible.value = true;
    formRef.value?.clearValidate?.();
  }

  async function handleOk() {
    try {
      await formRef.value?.validate();
    } catch (e) {
      return;
    }
    saving.value = true;
    try {
      await close({ orderNo: form.orderNo, reason: String(form.reason || '').trim() });
      createMessage.success('订单已关闭');
      visible.value = false;
      emit('success');
    } finally {
      saving.value = false;
    }
  }

  function handleCancel() {
    visible.value = false;
  }

  defineExpose({ open });
</script>
