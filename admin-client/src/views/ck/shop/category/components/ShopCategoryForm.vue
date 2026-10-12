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
                            <a-form-item
                                label="父级分类"
                                v-bind="validateInfos.parentId"
                                id="ShopCategoryForm-parentId"
                                name="parentId"
                            >
                                <a-select
                                    v-model:value="formData.parentId"
                                    placeholder="顶级分类"
                                    show-search
                                    optionFilterProp="label"
                                    :options="parentOptions"
                                />
                                <span class="store-hint">选「顶级分类」即为一级分类；后端会校验父分类必须存在、同店铺且不能是自己，并自动计算层级</span>
                            </a-form-item>
                        </a-col>
                        <a-col :span="24">
                            <a-form-item label="所属店铺" v-bind="validateInfos.sysOrgCode" id="ShopCategoryForm-sysOrgCode"
                                name="sysOrgCode">
                                <a-select v-model:value="formData.sysOrgCode" placeholder="请选择店铺（归属到店铺绑定的部门）"
                                    allow-clear show-search optionFilterProp="label" :options="storeOptions"
                                    :disabled="!canManageStoreOrg" />
                                <span v-if="!canManageStoreOrg" class="store-hint">归属由账号所在部门决定</span>
                                <span v-else class="store-hint">分类只能属于一个店铺；留空则由后端按操作人部门自动归属</span>
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
import { list as categoryList, queryById, saveOrUpdate } from '../ShopCategory.api';
import { storeAll } from '../../product/ShopProduct.api';
import JFormContainer from '/@/components/Form/src/container/JFormContainer.vue';
import JImageUpload from '/@/components/Form/src/jeecg/components/JImageUpload.vue';
import JSwitch from '/@/components/Form/src/jeecg/components/JSwitch.vue';
import LangTabs from '/@/components/Form/src/jeecg/components/LangTabs.vue';
import { useMessage } from '/@/hooks/web/useMessage';
import { usePermission } from '/@/hooks/web/usePermission';
import { getDateByPicker, getValueType } from '/@/utils';
const props = defineProps({
    formDisabled: { type: Boolean, default: false },
    // 兼容旧用法：父级可通过它额外关闭表单（当前无调用方传入）
    formDataProp: { type: Object, default: () => ({}) },
    formBpm: { type: Boolean, default: true }
});
const formRef = ref();
const langTabsRef = ref();
const useForm = Form.useForm;
const emit = defineEmits(['register', 'ok']);
const formData = reactive<Record<string, any>>({
    id: '',
    name: '',
    // 父级分类：'0' = 顶级分类（后端会校验父分类必须存在、同部门、不是自己，并自动算 level）
    parentId: '0',
    sortOrder: undefined,
    coverUrl: '',
    isShow: 'Y',
    // 所属店铺：值 = 店铺绑定的部门 org_code（departOrgCode），为空则由后端按操作人部门归属
    sysOrgCode: undefined,
    translations: []
});
const { createMessage } = useMessage();
const { hasPermission } = usePermission();

/** 只有管理员/总部角色有 shop-store:manage-all，能改分类归属 */
const canManageStoreOrg = computed(() => hasPermission('shop-store:manage-all'));

/** 店铺下拉：只列绑定了部门的店铺，value 用 departOrgCode（新模型没有"全局共享"分类） */
const storeOptions = ref<any[]>([]);
async function loadStores() {
    try {
        const res = await storeAll();
        const records = res || [];
        storeOptions.value = records
            .filter((item: any) => !!item?.departOrgCode)
            .map((item: any) => ({ label: `${item.name}（${item.code}）`, value: item.departOrgCode }));
    } catch (e) {
        console.warn('店铺列表加载失败', e);
    }
}

/** 可选的父级分类原始列表（来自 /shop/category/list） */
const parentRecords = ref<any[]>([]);
async function loadParentCategories() {
    try {
        // pageSize 给大一些：下拉里要能看到全部分类，避免漏掉超出首页的父分类
        const res = await categoryList({ pageNo: 1, pageSize: 500 });
        parentRecords.value = res?.records || res || [];
    } catch (e) {
        console.warn('父级分类加载失败', e);
        parentRecords.value = [];
    }
}

/**
 * 父级分类下拉：
 * 第一项固定为「顶级分类」（值 '0'），其余按层级缩进显示；
 * 必须排除自己（后端也会拒绝"父级 = 自己"）。
 */
const parentOptions = computed(() => {
    const options = parentRecords.value
        .filter((item: any) => item?.id && item.id !== formData.id)
        .map((item: any) => {
            const level = Math.max(1, Number(item.level) || 1);
            return { label: `${'　'.repeat(level - 1)}${item.name || item.id}`, value: item.id };
        });
    return [{ label: '顶级分类', value: '0' }, ...options];
});
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
        if (props.formDataProp.disabled === false) {
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
    // 店铺下拉 + 父级分类下拉（分类按店独立，父级必须同店铺）
    loadStores();
    loadParentCategories();
    // 先刷新组件，清空之前的翻译数据
    await langTabsRef.value?.refresh();

    if (record.id) {
        const queryRes = await queryById({ id: record.id });
        if (queryRes) {
            const data = queryRes || {};
            Object.assign(formData, data);
            // 父级为空统一回填成 '0'（顶级），避免下拉框显示空白
            formData.parentId = data.parentId || '0';
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
    // 归属为空统一转成 null，避免库里存一堆 ''
    model.sysOrgCode = model.sysOrgCode || null;
    // 父级分类：空统一转成 '0'（顶级），后端据 level 自动计算层级
    model.parentId = model.parentId || '0';
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
.store-hint {
    display: block;
    margin-top: 4px;
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
    line-height: 1.6;
}
</style>
