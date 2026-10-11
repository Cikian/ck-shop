<template>
  <a-spin :spinning="confirmLoading">
    <JFormContainer :disabled="disabled">
      <template #detail>
        <a-form ref="formRef" class="antd-modal-form" :labelCol="labelCol" :wrapperCol="wrapperCol" name="ShopTagForm">
          <a-row>
            <a-col :span="24">
              <a-form-item label="标签编码" v-bind="validateInfos.code" id="ShopTagForm-code" name="code">
                <a-input v-model:value="formData.code" placeholder="请输入标签唯一标识码，例如 NEW" allow-clear></a-input>
                <template #extra>
                  <span class="form-field-hint">标签编码需全局唯一，前台按该编码匹配商品标签</span>
                </template>
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="是否启用" v-bind="validateInfos.isActive" id="ShopTagForm-isActive" name="isActive">
                <j-switch v-model:value="formData.isActive"></j-switch>
              </a-form-item>
            </a-col>
          </a-row>
        </a-form>
      </template>
    </JFormContainer>

    <LangTabs
      ref="langTabsRef"
      v-model:translations="formData.translations"
      title="多语言配置"
      tabs-type="card"
      padding="0 10%"
      :default-fields="{ name: '' }"
    >
      <template #default="{ item }">
        <JFormContainer :disabled="disabled">
          <template #detail>
            <a-form class="antd-modal-form" :labelCol="labelCol" :wrapperCol="wrapperCol" name="ShopTagForm" style="padding: 0">
              <a-row>
                <a-col :span="24">
                  <a-form-item label="名称" required id="ShopTagForm-trans-name" name="transName">
                    <a-input v-model:value="item.name" placeholder="请输入标签名称" allow-clear></a-input>
                  </a-form-item>
                </a-col>
              </a-row>
            </a-form>
          </template>
        </JFormContainer>
      </template>
    </LangTabs>
  </a-spin>
</template>

<script lang="ts" setup>
  import { Form } from 'ant-design-vue';
  import { computed, nextTick, reactive, ref } from 'vue';
  import { queryById, saveOrUpdate } from '../ShopTag.api';
  import JFormContainer from '/@/components/Form/src/container/JFormContainer.vue';
  import JSwitch from '/@/components/Form/src/jeecg/components/JSwitch.vue';
  import LangTabs from '/@/components/Form/src/jeecg/components/LangTabs.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { getDateByPicker, getValueType } from '/@/utils';

  const props = defineProps({
    formDisabled: { type: Boolean, default: false },
    formBpm: { type: Boolean, default: true },
  });
  const formRef = ref();
  const langTabsRef = ref();
  const useForm = Form.useForm;
  const emit = defineEmits(['register', 'ok']);
  const formData = reactive<Record<string, any>>({
    id: '',
    code: '',
    isActive: 'Y',
    translations: [],
  });
  const { createMessage } = useMessage();
  const labelCol = ref<any>({ xs: { span: 24 }, sm: { span: 5 } });
  const wrapperCol = ref<any>({ xs: { span: 24 }, sm: { span: 16 } });
  const confirmLoading = ref<boolean>(false);
  //表单验证
  const validatorRules = reactive({
    code: [
      { required: true, message: '请输入标签编码!' },
      { pattern: /^[A-Za-z0-9_-]+$/, message: '标签编码仅支持字母、数字、下划线和中划线!' },
    ],
    isActive: [{ required: false }],
  });
  const { resetFields, validate, validateInfos } = useForm(formData, validatorRules, { immediate: false });
  //日期个性化选择
  const fieldPickers = reactive({});

  // 表单禁用：本表单不涉及 BPM，formBpm=false 时直接取 formDisabled
  const disabled = computed(() => {
    if (props.formBpm === true) {
      return true;
    }
    return props.formDisabled;
  });

  /**
   * 新增
   */
  function add() {
    edit({});
  }

  /**
   * 编辑
   */
  async function edit(record) {
    await nextTick();
    resetFields();
    // 先刷新组件，清空之前的翻译数据
    await langTabsRef.value?.refresh();

    formData.id = '';
    formData.code = '';
    formData.isActive = 'Y';

    if (record.id) {
      const queryRes = await queryById({ id: record.id });
      if (queryRes) {
        const data = queryRes || {};
        Object.assign(formData, data);
        // LangTabs 内部自动兼容数组 / JSON 字符串两种形态
        langTabsRef.value?.mergeFrom(data.translations);
      }
    }
  }

  /**
   * 提交数据
   */
  async function submitForm() {
    try {
      // 触发表单验证
      await validate();
    } catch ({ errorFields }) {
      if (errorFields) {
        const firstField = errorFields[0];
        if (firstField) {
          formRef.value.scrollToField(firstField.name, { behavior: 'smooth', block: 'center' });
        }
      }
      return Promise.reject(errorFields);
    }
    // 组装提交数据：从 LangTabs 获取清理过的 translations
    const translations = langTabsRef.value?.clean() ?? [];
    // 多语言字段不在 a-form 模型内，这里手动校验「名称必填」（至少一种语言填写名称）
    const hasName = translations.some((item) => !!String(item?.name ?? '').trim());
    if (!hasName) {
      createMessage.warning('请至少填写一种语言的标签名称');
      return;
    }
    confirmLoading.value = true;
    const isUpdate = ref<boolean>(false);
    let model = JSON.parse(JSON.stringify(formData));
    model.translations = translations;
    if (model.id) {
      isUpdate.value = true;
    }
    //循环数据
    for (let data in model) {
      // 更新个性化日期选择器的值
      model[data] = getDateByPicker(model[data], fieldPickers[data]);
      //如果该数据是数组并且是字符串类型
      if (model[data] instanceof Array) {
        let valueType = getValueType(formRef.value.getProps, data);
        const arr = model[data];
        const isPrimitiveArray =
          arr.length === 0 ||
          arr.every((item: any) => typeof item === 'string' || typeof item === 'number' || typeof item === 'boolean' || item == null);
        // 基础类型数组（多选 ID 等）且 valueType=string：按 Jeecg 惯例逗号拼接
        // 对象数组（如 translations）保持数组，交给 axios 序列化为请求体中的 JSON 数组
        if (valueType === 'string' && isPrimitiveArray) {
          model[data] = arr.join(',');
        }
      }
    }
    await saveOrUpdate(model, isUpdate.value)
      .then((res) => {
        if (res.success) {
          createMessage.success(res.message);
          emit('ok');
        } else {
          createMessage.warning(res.message);
        }
      })
      .finally(() => {
        confirmLoading.value = false;
      });
  }

  defineExpose({
    add,
    edit,
    submitForm,
  });
</script>

<style lang="less" scoped>
  .antd-modal-form {
    padding: 14px 20px;
  }

  .form-field-hint {
    font-size: 12px;
    line-height: 1.6;
    color: rgba(0, 0, 0, 0.45);
  }
</style>
