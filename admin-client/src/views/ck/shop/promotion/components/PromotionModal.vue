<template>
  <j-modal
    :title="title"
    :maxHeight="640"
    :width="1200"
    :visible="visible"
    @ok="handleOk"
    :okButtonProps="{ className: { 'jee-hidden': disableSubmit } }"
    @cancel="handleCancel"
    cancelText="关闭"
  >
    <PromotionForm ref="registerForm" @ok="submitCallback" :formDisabled="disableSubmit" />
    <template #footer>
      <a-button @click="handleCancel">取消</a-button>
      <!-- 保存并继续编辑：新增成功后据 result 回填活动 id，不关窗，便于接着配关联商品 -->
      <a-button v-if="!disableSubmit" :loading="saving" @click="handleSaveAndContinue">保存并继续编辑</a-button>
      <a-button :class="{ 'jee-hidden': disableSubmit }" type="primary" :loading="saving" @click="handleOk">确认</a-button>
    </template>
  </j-modal>
</template>

<script lang="ts" setup>
  import { ref, nextTick } from 'vue';
  import PromotionForm from './PromotionForm.vue';
  import JModal from '/@/components/Modal/src/JModal/JModal.vue';

  const title = ref<string>('新增活动');
  const visible = ref<boolean>(false);
  const disableSubmit = ref<boolean>(false);
  const saving = ref<boolean>(false);
  const registerForm = ref();
  const emit = defineEmits(['register', 'success']);

  /**
   * 新增
   */
  function add() {
    title.value = '新增促销活动';
    disableSubmit.value = false;
    visible.value = true;
    nextTick(() => {
      registerForm.value.add();
    });
  }

  /**
   * 编辑 / 详情
   * @param record 列表行
   */
  function edit(record) {
    title.value = disableSubmit.value ? '促销活动详情' : '编辑促销活动';
    visible.value = true;
    nextTick(() => {
      registerForm.value.edit(record);
    });
  }

  /**
   * 确定按钮点击事件：保存成功后由表单 emit('ok') 回调关窗
   */
  async function handleOk() {
    if (disableSubmit.value) {
      return;
    }
    saving.value = true;
    try {
      await registerForm.value.submitForm(false);
    } finally {
      saving.value = false;
    }
  }

  /**
   * 保存并继续编辑：不关窗，表单内部把后端返回的活动 id 回填后继续编辑
   */
  async function handleSaveAndContinue() {
    saving.value = true;
    try {
      await registerForm.value.submitForm(true);
    } finally {
      saving.value = false;
    }
  }

  /**
   * form 保存回调（只有「确认」保存成功才会走到这里）
   */
  function submitCallback() {
    handleCancel();
    emit('success');
  }

  /**
   * 取消按钮回调事件
   */
  function handleCancel() {
    visible.value = false;
  }

  defineExpose({
    add,
    edit,
    disableSubmit,
  });
</script>

<style lang="less">
  /**隐藏样式-modal确定按钮 */
  .jee-hidden {
    display: none !important;
  }
</style>
<style lang="less" scoped></style>
