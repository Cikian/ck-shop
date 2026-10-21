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
  import { onBeforeRouteLeave } from 'vue-router';
  import PromotionForm from './PromotionForm.vue';
  import JModal from '/@/components/Modal/src/JModal/JModal.vue';
  import { useMessage } from '/@/hooks/web/useMessage';

  const { createConfirm } = useMessage();

  const title = ref<string>('新增活动');
  const visible = ref<boolean>(false);
  const disableSubmit = ref<boolean>(false);
  const saving = ref<boolean>(false);
  const registerForm = ref();
  /** 打开时的表单快照：用于"关闭/取消/路由离开"时的未保存提示 */
  const formSnapshot = ref<string>('');
  const emit = defineEmits(['register', 'success']);

  /** 重新记录"干净"基线 */
  function markClean() {
    formSnapshot.value = registerForm.value?.getSnapshot?.() || '';
  }

  /** 有未保存修改则二次确认，否则直接关闭 */
  function closeWithDirtyCheck() {
    if (disableSubmit.value || !registerForm.value) {
      visible.value = false;
      return;
    }
    const current = registerForm.value?.getSnapshot?.() || '';
    if (current === formSnapshot.value) {
      visible.value = false;
      return;
    }
    createConfirm({
      iconType: 'warning',
      title: '有未保存的修改',
      content: '关闭后本次对活动与关联商品的修改会丢失，确定放弃吗？',
      okText: '放弃修改',
      cancelText: '继续编辑',
      onOk: () => {
        visible.value = false;
      },
    });
  }

  // 抽屉/弹窗还开着就切菜单 = 静默丢弃编辑，路由离开前先问一句
  onBeforeRouteLeave((_to, _from, next) => {
    if (!visible.value || disableSubmit.value || !registerForm.value) {
      next();
      return;
    }
    const current = registerForm.value?.getSnapshot?.() || '';
    if (current === formSnapshot.value) {
      next();
      return;
    }
    createConfirm({
      iconType: 'warning',
      title: '有未保存的修改',
      content: '离开当前页面会丢失促销活动里尚未保存的修改，确定离开吗？',
      okText: '放弃修改并离开',
      cancelText: '留在本页',
      onOk: () => next(),
      onCancel: () => next(false),
    });
  });

  /**
   * 新增
   */
  function add() {
    title.value = '新增促销活动';
    disableSubmit.value = false;
    visible.value = true;
    nextTick(async () => {
      await registerForm.value.add();
      markClean();
    });
  }

  /**
   * 编辑 / 详情
   * @param record 列表行
   */
  function edit(record) {
    title.value = disableSubmit.value ? '促销活动详情' : '编辑促销活动';
    visible.value = true;
    nextTick(async () => {
      await registerForm.value.edit(record);
      markClean();
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
      // 保存成功后当前状态即新的干净基线，否则"保存并继续编辑"后一关窗就误报未保存
      markClean();
    } finally {
      saving.value = false;
    }
  }

  /**
   * form 保存回调（只有「确认」保存成功才会走到这里）
   */
  function submitCallback() {
    markClean();
    // 保存成功直接关窗，不再走未保存检查（否则会问"要不要放弃"）
    visible.value = false;
    emit('success');
  }

  /**
   * 取消按钮回调事件
   */
  function handleCancel() {
    closeWithDirtyCheck();
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
