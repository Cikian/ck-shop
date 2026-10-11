<template>
  <a-spin :spinning="confirmLoading">
    <JFormContainer :disabled="disabled">
      <template #detail>
        <a-form ref="formRef" class="antd-modal-form" :labelCol="labelCol" :wrapperCol="wrapperCol" name="ShopBrandForm">
          <a-row>
            <a-col :span="24">
              <a-form-item label="品牌编码" v-bind="validateInfos.code" id="ShopBrandForm-code" name="code">
                <a-input v-model:value="formData.code" placeholder="请输入品牌编码，例如 APPLE" allow-clear></a-input>
                <template #extra>
                  <span class="form-field-hint">品牌编码需全局唯一，前台按该编码筛选商品</span>
                </template>
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="品牌LOGO" v-bind="validateInfos.logoUrl" id="ShopBrandForm-logoUrl" name="logoUrl">
                <j-image-upload biz-path="/shop/brand" v-model:value="formData.logoUrl"></j-image-upload>
                <template #extra>
                  <span class="form-field-hint">建议上传正方形 LOGO，前台按 1:1 展示</span>
                </template>
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="排序" v-bind="validateInfos.sortOrder" id="ShopBrandForm-sortOrder" name="sortOrder">
                <a-input-number
                  v-model:value="formData.sortOrder"
                  placeholder="请输入排序（非负整数，数字越小越靠前）"
                  :min="0"
                  :precision="0"
                  style="width: 100%"
                />
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="是否启用" v-bind="validateInfos.isShow" id="ShopBrandForm-isShow" name="isShow">
                <j-switch v-model:value="formData.isShow"></j-switch>
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
      :default-fields="{ name: '', description: '' }"
    >
      <template #default="{ item }">
        <JFormContainer :disabled="disabled">
          <template #detail>
            <a-form class="antd-modal-form" :labelCol="labelCol" :wrapperCol="wrapperCol" name="ShopBrandForm" style="padding: 0">
              <a-row>
                <a-col :span="24">
                  <a-form-item label="名称" required id="ShopBrandForm-trans-name" name="transName">
                    <a-input v-model:value="item.name" placeholder="请输入品牌名称" allow-clear></a-input>
                  </a-form-item>
                </a-col>
                <a-col :span="24">
                  <a-form-item label="描述" id="ShopBrandForm-trans-description" name="transDescription">
                    <a-textarea v-model:value="item.description" placeholder="请输入品牌描述，可留空" :rows="3" allow-clear></a-textarea>
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
  import { queryById, saveOrUpdate } from '../ShopBrand.api';
  import JFormContainer from '/@/components/Form/src/container/JFormContainer.vue';
  import JImageUpload from '/@/components/Form/src/jeecg/components/JImageUpload.vue';
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
    logoUrl: '',
    sortOrder: 0,
    isShow: 'Y',
    translations: [],
  });
  const { createMessage } = useMessage();
  const labelCol = ref<any>({ xs: { span: 24 }, sm: { span: 5 } });
  const wrapperCol = ref<any>({ xs: { span: 24 }, sm: { span: 16 } });
  const confirmLoading = ref<boolean>(false);
  //表单验证
  const validatorRules = reactive({
    code: [
      { required: true, message: '请输入品牌编码!' },
      { pattern: /^[A-Za-z0-9_-]+$/, message: '品牌编码仅支持字母、数字、下划线和中划线!' },
    ],
    sortOrder: [{ required: false }, { pattern: /^\d+$/, message: '请输入非负整数!' }],
    // 说明：LOGO 由 j-image-upload 回传相对路径（非完整 URL），因此不做 URL 格式校验
    logoUrl: [{ required: false }],
    isShow: [{ required: false }],
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
    formData.logoUrl = '';
    formData.sortOrder = 0;
    formData.isShow = 'Y';

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
      createMessage.warning('请至少填写一种语言的品牌名称');
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
