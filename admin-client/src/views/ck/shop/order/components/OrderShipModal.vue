<template>
  <j-modal
    :title="title"
    :width="720"
    :visible="visible"
    :confirmLoading="saving"
    okText="确认发货"
    cancelText="取消"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <a-alert
      type="info"
      show-icon
      class="mb-3"
      message="物流公司与物流单号要么都填、要么都不填"
      description="虚拟商品 / 门店自提可以都留空（后端会自动记录为「无需物流」）。只填公司不填单号会被后端拒绝：买家拿着空单号查物流必然投诉。"
    />

    <!-- 发货前先把收货信息摊开，避免"发错地址"这种不可逆事故 -->
    <a-descriptions bordered size="small" :column="2" class="mb-3">
      <a-descriptions-item label="订单号">{{ detail.orderNo || '-' }}</a-descriptions-item>
      <a-descriptions-item label="买家账号">{{ detail.username || '-' }}</a-descriptions-item>
      <a-descriptions-item label="收货人">{{ detail.receiverName || '-' }}</a-descriptions-item>
      <a-descriptions-item label="联系电话">
        {{ detail.receiverPhone || '-' }}
        <span class="text-xs text-gray-400">（后台已脱敏）</span>
      </a-descriptions-item>
      <a-descriptions-item label="收货地址" :span="2">
        {{ fullAddress }}
      </a-descriptions-item>
      <a-descriptions-item label="商品件数">{{ detail.totalQuantity ?? '-' }} 件</a-descriptions-item>
      <a-descriptions-item label="买家备注">{{ detail.buyerRemark || '-' }}</a-descriptions-item>
    </a-descriptions>

    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }">
      <a-form-item label="物流公司" name="logisticsCompany">
        <a-input v-model:value="form.logisticsCompany" placeholder="例如：顺丰速运 / 中通快递" allow-clear :maxlength="50" />
      </a-form-item>
      <a-form-item label="物流单号" name="logisticsNo">
        <a-input v-model:value="form.logisticsNo" placeholder="填写后请再核对一次，单号错误会让买家查不到物流" allow-clear :maxlength="64" />
      </a-form-item>
      <a-form-item label="发货备注" name="shipRemark">
        <a-textarea v-model:value="form.shipRemark" :rows="3" :maxlength="255" show-count placeholder="选填，例如：分两个包裹发出" />
      </a-form-item>
    </a-form>
  </j-modal>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import JModal from '/@/components/Modal/src/JModal/JModal.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { ship } from '../ShopOrder.api';

  const emit = defineEmits(['success']);
  const { createMessage } = useMessage();

  const title = ref('订单发货');
  const visible = ref(false);
  const saving = ref(false);
  const formRef = ref();
  const detail = ref<Recordable>({});

  const form = reactive<{ orderNo: string; logisticsCompany?: string; logisticsNo?: string; shipRemark?: string }>({
    orderNo: '',
    logisticsCompany: undefined,
    logisticsNo: undefined,
    shipRemark: undefined,
  });

  /** 收货地址拼接：任一字段缺失都不影响展示，空值不补 "null" */
  const fullAddress = computed(() => {
    const d = detail.value || {};
    const region = [d.receiverProvince, d.receiverCity, d.receiverDistrict].filter((item) => !!item).join(' ');
    return [region, d.receiverAddress].filter((item) => !!item).join(' ') || '-';
  });

  /**
   * 校验：物流公司与单号必须成对出现。
   * 后端也会校验一次（接口可被直接调用），前端这层只是为了少一次失败的往返。
   */
  function validatePair(_rule: any, _value: any, callback: (error?: Error) => void) {
    const hasCompany = !!String(form.logisticsCompany || '').trim();
    const hasNo = !!String(form.logisticsNo || '').trim();
    if (hasCompany !== hasNo) {
      callback(new Error('物流公司与物流单号需要同时填写，或同时留空（虚拟发货）'));
      return;
    }
    callback();
  }

  const rules = {
    logisticsCompany: [{ validator: validatePair, trigger: 'blur' }],
    logisticsNo: [{ validator: validatePair, trigger: 'blur' }],
  };

  /** 打开弹窗，record 为列表行 */
  function open(record: Recordable) {
    detail.value = record || {};
    form.orderNo = record?.orderNo || '';
    form.logisticsCompany = undefined;
    form.logisticsNo = undefined;
    form.shipRemark = undefined;
    title.value = `订单发货 - ${record?.orderNo || ''}`;
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
      await ship({
        orderNo: form.orderNo,
        logisticsCompany: String(form.logisticsCompany || '').trim() || undefined,
        logisticsNo: String(form.logisticsNo || '').trim() || undefined,
        shipRemark: String(form.shipRemark || '').trim() || undefined,
      });
      createMessage.success('发货成功');
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
