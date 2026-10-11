<template>
  <ProductForm ref="registerForm" @ok="submitCallback" />
</template>

<script lang="ts" setup>
  import { computed, nextTick, ref } from 'vue';
  import ProductForm from './ProductForm.vue';

  /**
   * 商品编辑入口
   * 之所以用抽屉而不是弹窗：商品表单包含多语言、图集、规格、SKU 组合表格，
   * 内容量大且需要横向空间，弹窗形态在高分辨率下也会显得局促。
   *
   * disableSubmit 由内部 ProductForm 的 readonly 状态驱动，
   * 列表页设置 registerModal.value.disableSubmit = true 即进入只读详情模式。
   */
  const registerForm = ref();
  const innerReadonly = ref<boolean>(false);
  const disableSubmit = computed({
    get: () => innerReadonly.value,
    set: (val: boolean) => {
      innerReadonly.value = !!val;
      registerForm.value?.setReadonly?.(!!val);
    },
  });

  const emit = defineEmits(['register', 'success']);

  function add() {
    disableSubmit.value = false;
    nextTick(() => {
      registerForm.value.add();
    });
  }

  function edit(record: any) {
    registerForm.value.edit(record);
  }

  function submitCallback() {
    emit('success');
  }

  defineExpose({
    add,
    edit,
    disableSubmit,
  });
</script>
