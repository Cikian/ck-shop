<template>
  <j-modal
    :title="title"
    :width="720"
    :visible="visible"
    :confirmLoading="saving"
    :okText="decision === 'approve' ? '通过并提交渠道退款' : '拒绝该退款申请'"
    :okType="decision === 'approve' ? 'primary' : 'danger'"
    cancelText="取消"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <a-alert
      type="info"
      show-icon
      class="mb-3"
      message="审核通过 ≠ 钱已退到账"
      description="通过后后端会先占用该支付单的退款额度（原子校验，防并发超额），再提交渠道退款；渠道可能受理中或失败。请以退款单的【状态】与【到账时间】为准，失败可在列表点【重试渠道退款】。"
    />

    <a-descriptions bordered size="small" :column="2" class="mb-3">
      <a-descriptions-item label="退款单号" :span="2">{{ detail.refundNo || '-' }}</a-descriptions-item>
      <a-descriptions-item label="订单号">{{ detail.orderNo || '-' }}</a-descriptions-item>
      <a-descriptions-item label="支付单号">{{ detail.paymentNo || '-' }}</a-descriptions-item>
      <a-descriptions-item label="申请账号">{{ detail.username || '-' }}</a-descriptions-item>
      <a-descriptions-item label="申请时间">{{ detail.applyTime || '-' }}</a-descriptions-item>
      <a-descriptions-item label="退款类型">{{ detail.refundTypeLabel || detail.refundType || '-' }}</a-descriptions-item>
      <a-descriptions-item label="申请退款金额">
        <span class="amount-strong">¥{{ formatAmount(detail.refundAmount) }}</span>
      </a-descriptions-item>
      <a-descriptions-item label="该订单可退金额">
        <span class="amount-strong">{{ refundableText }}</span>
      </a-descriptions-item>
      <a-descriptions-item label="退款件数">{{ detail.totalQuantity ?? '-' }} 件 / {{ detail.itemCount ?? '-' }} 种</a-descriptions-item>
      <a-descriptions-item label="申请原因">{{ detail.applyReason || '-' }}</a-descriptions-item>
      <a-descriptions-item label="申请说明" :span="2">{{ detail.applyDesc || '-' }}</a-descriptions-item>
      <a-descriptions-item v-if="images.length" label="申请凭证" :span="2">
        <a-image-preview-group>
          <a-image v-for="(img, index) in images" :key="index" :src="img" :width="72" :height="72" style="object-fit: cover" />
        </a-image-preview-group>
      </a-descriptions-item>
    </a-descriptions>

    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
      <a-form-item label="审核结果" name="decision">
        <a-radio-group v-model:value="decision" button-style="solid">
          <a-radio-button value="approve">通过（提交渠道退款）</a-radio-button>
          <a-radio-button value="reject">拒绝</a-radio-button>
        </a-radio-group>
      </a-form-item>

      <template v-if="decision === 'approve'">
        <a-form-item label="审核备注" name="auditRemark">
          <a-textarea v-model:value="form.auditRemark" :rows="3" :maxlength="255" show-count placeholder="选填，例如：已核实未发货，同意退款" />
        </a-form-item>
      </template>
      <template v-else>
        <a-form-item label="拒绝原因" name="rejectReason">
          <a-textarea
            v-model:value="form.rejectReason"
            :rows="3"
            :maxlength="255"
            show-count
            placeholder="必填。例如：商品已签收且无质量问题，不符合退款条件"
          />
        </a-form-item>
      </template>
    </a-form>
  </j-modal>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import JModal from '/@/components/Modal/src/JModal/JModal.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { audit, refundable } from '../ShopRefund.api';
  import { formatAmount, parseApplyImages } from '../ShopRefund.data';

  const emit = defineEmits(['success']);
  const { createMessage } = useMessage();

  const title = ref('退款审核');
  const visible = ref(false);
  const saving = ref(false);
  const formRef = ref();
  const detail = ref<Recordable>({});
  const decision = ref<'approve' | 'reject'>('approve');
  const refundableAmount = ref<number | null>(null);

  const form = reactive<{ auditRemark?: string; rejectReason?: string }>({
    auditRemark: undefined,
    rejectReason: undefined,
  });

  const images = computed(() => parseApplyImages(detail.value?.applyImages));

  const refundableText = computed(() => {
    if (refundableAmount.value === null) {
      // 额度查询走的是 shop-refund:list 接口：没有该权限、或查询失败都会落到这里
      return '暂无法获取（可能无查询权限）';
    }
    return `¥${formatAmount(refundableAmount.value)}`;
  });

  /** 拒绝原因必填（后端硬校验），通过时备注选填 */
  const rules = computed(() => ({
    rejectReason: decision.value === 'reject' ? [{ required: true, message: '拒绝退款必须填写原因（后端硬校验）', trigger: 'blur' }] : [],
  }));

  async function open(record: Recordable) {
    detail.value = record || {};
    decision.value = 'approve';
    form.auditRemark = undefined;
    form.rejectReason = undefined;
    refundableAmount.value = null;
    title.value = `退款审核 - ${record?.refundNo || ''}`;
    visible.value = true;
    formRef.value?.clearValidate?.();
    // 先看额度：避免"看着能退、提交却被拒"的体验
    if (record?.orderNo) {
      try {
        const res = await refundable(record.orderNo);
        refundableAmount.value = Number(res?.refundableAmount ?? 0);
      } catch (e) {
        refundableAmount.value = null;
      }
    }
  }

  async function handleOk() {
    try {
      await formRef.value?.validate();
    } catch (e) {
      return;
    }
    saving.value = true;
    try {
      await audit({
        refundNo: detail.value.refundNo,
        approved: decision.value === 'approve',
        auditRemark: String(form.auditRemark || '').trim() || undefined,
        rejectReason: decision.value === 'reject' ? String(form.rejectReason || '').trim() : undefined,
      });
      createMessage.success(decision.value === 'approve' ? '审核通过，已提交渠道退款（请关注是否到账）' : '已拒绝该退款申请');
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

<style lang="less" scoped>
  .amount-strong {
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
</style>
