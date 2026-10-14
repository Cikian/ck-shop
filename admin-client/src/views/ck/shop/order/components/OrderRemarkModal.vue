<template>
  <j-modal
    :title="title"
    :width="640"
    :visible="visible"
    :confirmLoading="saving"
    okText="保存备注"
    cancelText="取消"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <a-alert
      type="info"
      show-icon
      class="mb-3"
      message="卖家备注会写入订单操作流水"
      description="流水里会记录「谁在什么时候改成了什么」。请勿在此填写与订单无关的内容（退款、补单等操作另有专用入口，各自留痕）。"
    />

    <a-descriptions bordered size="small" :column="1" class="mb-3">
      <a-descriptions-item label="订单号">{{ detail.orderNo || '-' }}</a-descriptions-item>
      <a-descriptions-item label="买家备注">{{ detail.buyerRemark || '-' }}</a-descriptions-item>
    </a-descriptions>

    <a-form ref="formRef" :model="form" :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }">
      <a-form-item label="卖家备注" name="sellerRemark">
        <a-textarea v-model:value="form.sellerRemark" :rows="4" :maxlength="500" show-count placeholder="例如：已电话确认可退，等待仓库收货后处理" />
      </a-form-item>
    </a-form>
  </j-modal>
</template>

<script lang="ts" setup>
  import { reactive, ref } from 'vue';
  import JModal from '/@/components/Modal/src/JModal/JModal.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { remark } from '../ShopOrder.api';

  const emit = defineEmits(['success']);
  const { createMessage } = useMessage();

  const title = ref('修改卖家备注');
  const visible = ref(false);
  const saving = ref(false);
  const detail = ref<Recordable>({});

  const form = reactive<{ orderNo: string; sellerRemark?: string }>({ orderNo: '', sellerRemark: undefined });

  /** 打开弹窗并回填该订单当前的卖家备注 */
  function open(record: Recordable) {
    detail.value = record || {};
    form.orderNo = record?.orderNo || '';
    form.sellerRemark = record?.sellerRemark || undefined;
    title.value = `修改卖家备注 - ${record?.orderNo || ''}`;
    visible.value = true;
  }

  async function handleOk() {
    saving.value = true;
    try {
      // 备注允许清空（trim 后为空串即"清空备注"），因此这里不做非空校验
      await remark({ orderNo: form.orderNo, sellerRemark: String(form.sellerRemark || '').trim() });
      createMessage.success('备注已保存');
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
