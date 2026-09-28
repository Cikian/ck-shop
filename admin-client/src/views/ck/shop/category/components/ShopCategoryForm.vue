<template>
    <a-spin :spinning="confirmLoading">
        <JFormContainer :disabled="disabled">
            <template #detail>
                <a-form ref="formRef" class="antd-modal-form" :labelCol="labelCol" :wrapperCol="wrapperCol"
                    name="ShopCategoryForm">
                    <a-row>
                        <a-col :span="24">
                            <a-form-item label="内部名称" v-bind="validateInfos.name" id="ShopCategoryForm-name"
                                name="name">
                                <a-input v-model:value="formData.name" placeholder="请输入内部名称" allow-clear></a-input>
                            </a-form-item>
                        </a-col>
                        <a-col :span="24">
                            <a-form-item label="排序" v-bind="validateInfos.sortOrder" id="ShopCategoryForm-sortOrder"
                                name="sortOrder">
                                <a-input-number v-model:value="formData.sortOrder" placeholder="请输入排序"
                                    style="width: 100%" />
                            </a-form-item>
                        </a-col>
                        <a-col :span="24">
                            <a-tooltip placement="top" :arrow="false">
                                <template #title>当语言封面未上传时，默认使用该封面</template>
                                <a-form-item label="默认封面" v-bind="validateInfos.coverUrl" id="ShopCategoryForm-coverUrl"
                                    name="coverUrl">
                                    <j-image-upload biz-path="/shop/category"
                                        v-model:value="formData.coverUrl"></j-image-upload>
                                </a-form-item>
                            </a-tooltip>
                        </a-col>
                        <a-col :span="24">
                            <a-form-item label="是否启用" v-bind="validateInfos.isShow" id="ShopCategoryForm-isShow"
                                name="isShow">
                                <j-switch v-model:value="formData.isShow"></j-switch>
                            </a-form-item>
                        </a-col>
                    </a-row>
                </a-form>
            </template>
        </JFormContainer>

        <LangTabs ref="langTabsRef" v-model:translations="formData.translations" title="多语言配置" tabs-type="card"
            padding="0 10%" :default-fields="{ name: '', coverUrl: '' }">
            <template #default="{ item }">
                <JFormContainer :disabled="disabled">
                    <template #detail>
                        <a-form ref="formRef" class="antd-modal-form" :labelCol="labelCol" :wrapperCol="wrapperCol"
                            name="ShopCategoryForm" style="padding: 0;">
                            <a-row>
                                <a-col :span="24">
                                    <a-form-item label="名称" v-bind="validateInfos.name" id="ShopCategoryForm-name"
                                        name="name">
                                        <a-input v-model:value="item.name" placeholder="请输入名称" allow-clear></a-input>
                                    </a-form-item>
                                </a-col>
                                <a-col :span="24">
                                    <a-form-item label="语言封面" v-bind="validateInfos.coverUrl"
                                        id="ShopCategoryForm-coverUrl" name="coverUrl">
                                        <j-image-upload biz-path="/shop/category"
                                            v-model:value="item.coverUrl"></j-image-upload>
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
import { queryById, saveOrUpdate } from '../ShopCategory.api';
import JFormContainer from '/@/components/Form/src/container/JFormContainer.vue';
import JImageUpload from '/@/components/Form/src/jeecg/components/JImageUpload.vue';
import JSwitch from '/@/components/Form/src/jeecg/components/JSwitch.vue';
import LangTabs from '/@/components/Form/src/jeecg/components/LangTabs.vue';
import { useMessage } from '/@/hooks/web/useMessage';
import { getDateByPicker, getValueType } from '/@/utils';
const props = defineProps({
    formDisabled: { type: Boolean, default: false },
    formData: { type: Object, default: () => ({}) },
    formBpm: { type: Boolean, default: true }
});
const formRef = ref();
const langTabsRef = ref();
const useForm = Form.useForm;
const emit = defineEmits(['register', 'ok']);
const formData = reactive<Record<string, any>>({
    id: '',
    name: '',
    sortOrder: undefined,
    coverUrl: '',
    isShow: 'Y',
    translations: []
});
const { createMessage } = useMessage();
const labelCol = ref<any>({ xs: { span: 24 }, sm: { span: 5 } });
const wrapperCol = ref<any>({ xs: { span: 24 }, sm: { span: 16 } });
const confirmLoading = ref<boolean>(false);
//表单验证
const validatorRules = reactive({
    sortOrder: [{ required: false }, { pattern: /^\d+$/, message: '请输入非负整数!' },],
    iconUrl: [{ required: false }, { pattern: /^((ht|f)tps?):\/\/[\w\-]+(\.[\w\-]+)+([\w\-.,@?^=%&:\/~+#]*[\w\-@?^=%&\/~+#])?$/, message: '请输入正确的网址!' },],
    coverUrl: [{ required: false }, { pattern: /^((ht|f)tps?):\/\/[\w\-]+(\.[\w\-]+)+([\w\-.,@?^=%&:\/~+#]*[\w\-@?^=%&\/~+#])?$/, message: '请输入正确的网址!' },],
});
const { resetFields, validate, validateInfos } = useForm(formData, validatorRules, { immediate: false });
//日期个性化选择
const fieldPickers = reactive({});

// 表单禁用
const disabled = computed(() => {
    if (props.formBpm === true) {
        if (props.formData.disabled === false) {
            return false;
        } else {
            return true;
        }
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
    confirmLoading.value = true;
    const isUpdate = ref<boolean>(false);
    // 组装提交数据：从 LangTabs 获取清理过的 translations
    let model = JSON.parse(JSON.stringify(formData));
    model.translations = langTabsRef.value?.clean() ?? [];
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
            const isPrimitiveArray = arr.length === 0 || arr.every((item: any) =>
                typeof item === 'string' || typeof item === 'number' || typeof item === 'boolean' || item == null
            );
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
</style>
