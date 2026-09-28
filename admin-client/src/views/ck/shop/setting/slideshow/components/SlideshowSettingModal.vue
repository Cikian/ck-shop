<template>
  <j-modal
    :title="title"
    :height="800"
    :width="1000"
    :visible="visible"
    @ok="handleOk"
    :okButtonProps="{ class: { 'jee-hidden': disableSubmit } }"
    @cancel="handleCancel"
    cancelText="关闭"
  >
    <div style="margin: 20px 20px">
      <a-form :label-col="{ span: 2 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="描述">
          <a-textarea v-model:value="formData.desc" />
        </a-form-item>

        <a-form-item label="跳转URL">
          <a-input addon-before="http://" v-model:value="formData.targetUrl" />
        </a-form-item>
        
        <a-form-item label="关联商品">
          <a-input v-model:value="formData.goods" />
        </a-form-item>

        <a-form-item label="排序">
          <a-input-number id="inputNumber" v-model:value="formData.sort" :min="1" />
        </a-form-item>

        <a-form-item label="启用">
          <j-switch v-model:value="formData.enabled" />
        </a-form-item>
      </a-form>

      <a-tabs v-model:activeKey="activeKey">
        <a-tab-pane v-for="(lang, index) in language" :key="(index + 1).toString()" :tab="lang.name">
          <j-image-upload v-if="formData?.[lang.code]" biz-path="/shop/home/slideshow" v-model:value="formData[lang.code]['pic_url_' + lang.code]" />
        </a-tab-pane>
      </a-tabs>
    </div>
    <template #footer>
      <a-button @click="handleCancel">取消</a-button>
      <a-button :class="{ 'jee-hidden': disableSubmit }" type="primary" @click="handleOk">确认</a-button>
    </template>
  </j-modal>
</template>

<script lang="ts" setup>
  import { nextTick, reactive, ref } from 'vue';
  import JModal from '/@/components/Modal/src/JModal/JModal.vue';
  import { listLang } from '/@/api/common/api';
  import { JUpload } from '@/components/Form/src/jeecg/components/JUpload';
  import JImageUpload from '@/components/Form/src/jeecg/components/JImageUpload.vue';
  import { saveOrUpdate } from '../SlideshowSetting.api';
  import { useMessage } from '@/hooks/web/useMessage';
  import JSwitch from '@/components/Form/src/jeecg/components/JSwitch.vue';

  const activeKey = ref('1');
  const title = ref<string>('');
  const visible = ref<boolean>(false);
  const disableSubmit = ref<boolean>(false);

  const formData = reactive<any>({});
  const language = ref<any>({});
  const { createMessage } = useMessage();
  const emit = defineEmits(['ok']);

  async function loadLang() {
    const res = await listLang();
    const list = Array.isArray(res) ? res : res?.result || res?.data || [];
    language.value = list;

    // 为每个语言初始化 formData 对象容器，防止 template 读取 undefined
    list.forEach((lang: any) => {
      if (!formData[lang.code]) {
        formData[lang.code] = {
          ['pic_url_' + lang.code]: '',
        };
      }
    });
  }

  async function add() {
    title.value = '新增';
    visible.value = true;
    await loadLang();
    formData['enabled'] = 'Y';
  }

  /**
   * 编辑
   * @param record
   */
  async function edit(record) {
    console.log(record);
    title.value = '编辑';
    visible.value = true;
    activeKey.value = '1';

    await loadLang();
    nextTick(() => {
      const recordCopy = JSON.parse(JSON.stringify(record || {}));
      Object.assign(formData, recordCopy);
    });
  }

  /**
   * 取消按钮回调事件
   */
  function handleCancel() {
    visible.value = false;
    Object.keys(formData).forEach((key) => delete formData[key]);
  }

  function handleOk() {
    console.log('ok');
    console.log(JSON.parse(JSON.stringify(formData)));
    submitForm();
  }

  async function submitForm() {
    const isUpdate = ref<boolean>(false);
    if (formData.id) {
      isUpdate.value = true;
    }

    await saveOrUpdate(formData, isUpdate.value).then((res) => {
      if (res.success) {
        createMessage.success(res.message);
        emit('ok');
        handleCancel();
      } else {
        createMessage.warning(res.message);
      }
    });
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
