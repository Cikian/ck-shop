<template>
  <div class="p-4 bg-gray-50/50 min-h-full">
    <a-card :bordered="false" class="shadow-sm rounded-lg">
      <!-- 顶部操作区 -->
      <div class="flex justify-between items-center mb-4">
        <div class="text-base font-semibold text-gray-800 flex items-center">
          <span class="w-1 h-4 bg-blue-600 rounded mr-2 inline-block"></span>
          轮播图设置
        </div>
        <a-button type="primary" v-auth="'ck:sys:lang:add'" @click="handleAdd" preIcon="ant-design:plus-outlined" class="rounded-md">
          新增轮播图
        </a-button>
      </div>

      <!-- 表格区域 -->
      <a-table
        :columns="columns"
        :data-source="data"
        :loading="loading"
        row-key="id"
        size="middle"
        :pagination="{ pageSize: 10, showQuickJumper: true, showSizeChanger: true }"
        class="border border-gray-100 rounded-md overflow-hidden"
      >
        <!-- 表头自定义 -->
        <template #headerCell="{ column }">
          <template v-if="column.key === 'desc'">
            <span>
              <SmileOutlined class="mr-1 text-blue-500" />
              描述
            </span>
          </template>
        </template>

        <!-- 表格单元格自定义 -->
        <template #bodyCell="{ column, record }">
          <!-- 关联商品列 -->
          <template v-if="column.key === 'goods'">
            <span v-if="record.goods" class="font-medium text-gray-700">
              {{ record.goods }}
            </span>
            <span v-else class="text-gray-400 italic">未关联</span>
          </template>

          <!-- 启用状态列 -->
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'processing' : 'default'">
              {{ record.enabled ? '已启用' : '已禁用' }}
            </a-tag>
          </template>

          <!-- 操作列 -->
          <template v-if="column.key === 'action'">
            <span>
              <a-button type="link" size="small" @click="editSlide(record)">编辑</a-button>
              <a-divider type="vertical" />
              <a-button danger type="link" size="small" @click="editSlide(record)">删除</a-button>
            </span>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 表单弹窗组件 -->
    <SlideshowSettingModal ref="registerModal" @ok="handleSuccess" />
  </div>
</template>

<script lang="ts" name="ck.sys-SlideshowSetting" setup>
  import { onMounted, ref } from 'vue';
  import { SmileOutlined } from '@ant-design/icons-vue';
  import { useUserStore } from '/@/store/modules/user';
  import { list } from './SlideshowSetting.api';
  import SlideshowSettingModal from './components/SlideshowSettingModal.vue';

  const userStore = useUserStore();

  const registerModal = ref();
  const data = ref<any[]>([]);
  const loading = ref<boolean>(false);

  const columns = [
    {
      title: '描述',
      dataIndex: 'desc',
      key: 'desc',
      align: 'center',
      ellipsis: true,
    },
    {
      title: '关联商品',
      dataIndex: 'goods',
      key: 'goods',
      align: 'center',
      ellipsis: true,
    },
    {
      title: '排序',
      dataIndex: 'sort',
      key: 'sort',
      align: 'center',
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      align: 'center',
    },
    {
      title: '操作',
      key: 'action',
      align: 'center',
    },
  ];

  function editSlide(record: any) {
    registerModal.value?.edit(record);
  }

  function handleAdd() {
    registerModal.value?.add();
  }

  function handleSuccess() {
    loadData();
  }

  async function loadData() {
    try {
      loading.value = true;
      const res = await list();
      data.value = Array.isArray(res) ? res : res?.result || res?.data || [];
    } catch (error) {
      console.error('加载轮播图列表失败:', error);
    } finally {
      loading.value = false;
    }
  }

  onMounted(() => {
    loadData();
  });
</script>

<style lang="less" scoped>
  :deep(.ant-card-body) {
    padding: 16px 24px;
  }

  :deep(.ant-table-wrapper) {
    .ant-table-thead > tr > th {
      background-color: #fafafa;
      font-weight: 600;
    }
  }
</style>
