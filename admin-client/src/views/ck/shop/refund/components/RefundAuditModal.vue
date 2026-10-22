<template>
  <j-modal
    :title="title"
    :width="720"
    :visible="visible"
    :confirmLoading="saving"
    :okText="okText"
    :okType="decision === 'reject' ? 'danger' : 'primary'"
    :okButtonProps="{ disabled: okDisabled }"
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

    <a-alert
      v-if="exceedsRefundable"
      type="error"
      show-icon
      class="mb-3"
      message="申请金额超过当前可退金额"
      description="直接通过会被后端拒绝（或造成超额退款）。请先核对订单的已退金额，必要时改走部分退款或让买家重新申请。"
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
        <a-button
          v-if="!refundableLoading && refundableAmount === null"
          type="link"
          size="small"
          @click="loadRefundable(detail.orderNo)"
        >
          重试
        </a-button>
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
        <!--
          默认不选中：旧版默认就是"通过"，打开弹窗直接点确认会立刻发起真实渠道退款。
          退款是资金操作，必须由人显式选择"通过 / 拒绝"后才能提交。
        -->
        <a-radio-group v-model:value="decision" button-style="solid">
          <a-radio-button value="approve">通过（提交渠道退款）</a-radio-button>
          <a-radio-button value="reject">拒绝</a-radio-button>
        </a-radio-group>
        <span v-if="!decision" class="field-hint">请先选择审核结果</span>
      </a-form-item>

      <template v-if="decision === 'approve'">
        <a-form-item label="审核备注" name="auditRemark">
          <a-textarea v-model:value="form.auditRemark" :rows="3" :maxlength="255" show-count placeholder="选填，例如：已核实未发货，同意退款" />
        </a-form-item>
      </template>
      <template v-else-if="decision === 'reject'">
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
  import { computed, h, reactive, ref } from 'vue';
  import JModal from '/@/components/Modal/src/JModal/JModal.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { audit, refundable } from '../ShopRefund.api';
  import { formatAmount, parseApplyImages } from '../ShopRefund.data';

  const emit = defineEmits(['success']);
  const { createMessage, createConfirm } = useMessage();

  const title = ref('退款审核');
  const visible = ref(false);
  const saving = ref(false);
  /** 可退金额查询中：未拿到结果前不允许提交"通过"（旧版并发查询未完成也能提交） */
  const refundableLoading = ref(false);
  const formRef = ref();
  const detail = ref<Recordable>({});
  /** 空串 = 尚未选择；绝不给默认值，避免"打开弹窗点确认就退款" */
  const decision = ref<'approve' | 'reject' | ''>('');
  const refundableAmount = ref<number | null>(null);

  const form = reactive<{ auditRemark?: string; rejectReason?: string }>({
    auditRemark: undefined,
    rejectReason: undefined,
  });

  const images = computed(() => parseApplyImages(detail.value?.applyImages));

  const refundableText = computed(() => {
    if (refundableLoading.value) {
      return '查询中…';
    }
    if (refundableAmount.value === null) {
      // 额度查询走的是 shop-refund:list 接口：没有该权限、或查询失败都会落到这里
      return '暂无法获取（可点重试；没有查询权限时请联系管理员）';
    }
    return `¥${formatAmount(refundableAmount.value)}`;
  });

  /** 申请金额 > 可退金额：不允许提交，避免必然被后端拒绝的"假成功" */
  const exceedsRefundable = computed(() => {
    if (decision.value !== 'approve' || refundableAmount.value === null) {
      return false;
    }
    return Number(detail.value.refundAmount || 0) - refundableAmount.value > 0.001;
  });

  const okText = computed(() => {
    if (!decision.value) {
      return '请先选择审核结果';
    }
    return decision.value === 'approve' ? '通过并提交渠道退款' : '拒绝该退款申请';
  });

  /** 未选择结果 / 可退金额未就绪 / 超可退金额 / 提交中 → 禁止点确认 */
  const okDisabled = computed(() => {
    if (saving.value || !decision.value) {
      return true;
    }
    if (decision.value === 'approve') {
      return refundableLoading.value || refundableAmount.value === null || exceedsRefundable.value;
    }
    return false;
  });

  /** 拒绝原因必填（后端硬校验），通过时备注选填 */
  const rules = computed(() => ({
    rejectReason: decision.value === 'reject' ? [{ required: true, message: '拒绝退款必须填写原因（后端硬校验）', trigger: 'blur' }] : [],
  }));

  async function loadRefundable(orderNo?: string) {
    if (!orderNo) {
      refundableAmount.value = null;
      return;
    }
    refundableLoading.value = true;
    refundableAmount.value = null;
    try {
      const res = await refundable(orderNo);
      refundableAmount.value = Number(res?.refundableAmount ?? 0);
    } catch (e) {
      refundableAmount.value = null;
    } finally {
      refundableLoading.value = false;
    }
  }

  async function open(record: Recordable) {
    detail.value = record || {};
    decision.value = '';
    form.auditRemark = undefined;
    form.rejectReason = undefined;
    refundableAmount.value = null;
    title.value = `退款审核 - ${record?.refundNo || ''}`;
    visible.value = true;
    formRef.value?.clearValidate?.();
    // 先看额度：避免"看着能退、提交却被拒"的体验
    await loadRefundable(record?.orderNo);
  }

  /** 真正提交审核（与二次确认分开，便于确认框里复用） */
  async function submitAudit() {
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
    } catch (e: any) {
      createMessage.error(e?.message || '提交失败，请稍后重试');
      // 抛回给确认框：失败时保持弹窗打开，便于重试或取消
      throw e;
    } finally {
      saving.value = false;
    }
  }

  async function handleOk() {
    if (!decision.value) {
      createMessage.warning('请先选择审核结果（通过 / 拒绝）');
      return;
    }
    try {
      await formRef.value?.validate();
    } catch (e) {
      return;
    }
    if (decision.value !== 'approve') {
      try {
        await submitAudit();
      } catch {
        // 失败提示已在 submitAudit 内给出；这里是 JModal 的 ok 回调，不能再向上抛
      }
      return;
    }
    if (refundableLoading.value || refundableAmount.value === null) {
      createMessage.warning('可退金额尚未获取，请稍后重试');
      return;
    }
    if (exceedsRefundable.value) {
      createMessage.warning('申请金额超过当前可退金额，无法通过，请核对后再操作');
      return;
    }
    // 二次确认：复述退款单号/订单号/支付单号/申请金额/可退金额，退款不可撤销
    createConfirm({
      iconType: 'warning',
      title: '确认发起渠道退款？',
      okText: '确认退款',
      cancelText: '再想想',
      content: h('div', [
        h('p', `退款单号：${detail.value.refundNo || '-'}`),
        h('p', `订单号：${detail.value.orderNo || '-'}`),
        h('p', `支付单号：${detail.value.paymentNo || '-'}`),
        h('p', `申请退款金额：¥${formatAmount(detail.value.refundAmount)}`),
        h('p', `该订单当前可退金额：¥${formatAmount(refundableAmount.value)}`),
        h('p', { style: 'margin-top: 8px; color: var(--shop-error); font-weight: 600' }, '提交后会立即调用渠道退款，且不可撤销。'),
      ]),
      onOk: () => submitAudit(),
    });
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
