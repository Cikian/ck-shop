<template>
  <div class="p-2">
    <!--查询区域-->
    <div class="jeecg-basic-table-form-container">
      <a-form ref="formRef" @keyup.enter="searchQuery" :model="queryParam" :label-col="labelCol" :wrapper-col="wrapperCol">
        <a-row :gutter="24">
          <a-col :lg="6">
            <a-form-item name="code">
              <template #label><span title="活动编码">活动编码</span></template>
              <a-input v-model:value="queryParam.code" placeholder="输入活动编码模糊搜索" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="title">
              <template #label><span title="活动名称">活动名称</span></template>
              <a-input v-model:value="queryParam.title" placeholder="查翻译表与主表的标题" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="type">
              <template #label><span title="活动类型">活动类型</span></template>
              <a-select v-model:value="queryParam.type" placeholder="全部类型" allow-clear :options="TYPE_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="status">
              <template #label><span title="状态">状态</span></template>
              <a-select v-model:value="queryParam.status" placeholder="全部状态" allow-clear :options="STATUS_OPTIONS" />
            </a-form-item>
          </a-col>
          <!-- 按店铺筛选只对「能跨店看数据」的账号有意义：运营选了别的店铺也只会得到空结果，
               因此与商品列表一致，用按钮权限把它收起来 -->
          <a-col v-auth="'shop-promotion:filter-store'" :lg="6">
            <a-form-item name="storeId">
              <template #label><span title="所属店铺">所属店铺</span></template>
              <a-select
                v-model:value="queryParam.storeId"
                placeholder="全部店铺"
                allow-clear
                show-search
                option-filter-prop="label"
                :options="storeOptions"
              />
            </a-form-item>
          </a-col>
          <a-col :lg="8">
            <a-form-item name="startTimeRange">
              <template #label><span title="活动开始时间">开始时间</span></template>
              <a-range-picker
                v-model:value="startTimeRange"
                show-time
                :format="TIME_FORMAT"
                :placeholder="['开始时间从', '开始时间到']"
                @change="syncTimeRange"
              />
            </a-form-item>
          </a-col>
          <a-col :xl="6" :lg="7" :md="8" :sm="24">
            <span style="float: left; overflow: hidden" class="table-page-search-submitButtons">
              <a-button type="primary" preIcon="ant-design:search-outlined" @click="searchQuery">查询</a-button>
              <a-button type="primary" preIcon="ant-design:reload-outlined" @click="searchReset" style="margin-left: 8px">重置</a-button>
            </span>
          </a-col>
        </a-row>
      </a-form>
    </div>

    <!-- 导入导出的局限必须写在界面上，不能让用户以为「导入就把商品也导进来了」 -->
    <a-alert
      type="warning"
      show-icon
      class="import-export-alert"
      message="导入 / 导出只覆盖活动主表字段"
      description="导出：活动主表字段 + 店铺名 + 类型/状态（中文）。导入：只导入主表字段（编码、类型、名称、时间窗、力度、状态、店铺编码）；多语言标题与关联商品不参与导入，需要导入后到「编辑活动」里逐个补。"
    />

    <!--引用表格-->
    <BasicTable @register="registerTable" :rowSelection="rowSelection">
      <template #tableTitle>
        <a-button type="primary" v-auth="'shop-promotion:add'" @click="handleAdd" preIcon="ant-design:plus-outlined">新增活动</a-button>
        <a-button type="primary" v-auth="'shop-promotion:exportXls'" preIcon="ant-design:export-outlined" @click="handleExport">导出</a-button>
        <j-upload-button type="primary" v-auth="'shop-promotion:importExcel'" preIcon="ant-design:import-outlined" @click="onImportXls"
          >导入</j-upload-button
        >
        <a-dropdown v-if="selectedRowKeys.length > 0">
          <template #overlay>
            <a-menu>
              <a-menu-item key="batch-on" v-auth="'shop-promotion:edit'" @click="batchChangeStatus('Y')">
                <Icon icon="ant-design:arrow-up-outlined"></Icon>
                批量上线（{{ selectedRowKeys.length }}）
              </a-menu-item>
              <a-menu-item key="batch-off" v-auth="'shop-promotion:edit'" @click="batchChangeStatus('N')">
                <Icon icon="ant-design:arrow-down-outlined"></Icon>
                批量下线（{{ selectedRowKeys.length }}）
              </a-menu-item>
              <!-- 删除放最后并标红 -->
              <a-menu-item key="batch-delete" v-auth="'shop-promotion:deleteBatch'" danger @click="batchHandleDelete">
                <Icon icon="ant-design:delete-outlined"></Icon>
                批量删除（{{ selectedRowKeys.length }}）
              </a-menu-item>
            </a-menu>
          </template>
          <a-button v-auth="'shop-promotion:edit'">
            批量操作
            <Icon icon="mdi:chevron-down"></Icon>
          </a-button>
        </a-dropdown>
        <!-- 高级查询 -->
        <super-query :config="superQueryConfig" @search="handleSuperQuery" />
      </template>

      <!--操作栏-->
      <template #action="{ record }">
        <TableAction :actions="getTableAction(record)" :dropDownActions="getDropDownAction(record)" />
      </template>
    </BasicTable>

    <!-- 表单区域 -->
    <PromotionModal ref="registerModal" @success="handleSuccess"></PromotionModal>
  </div>
</template>

<script lang="ts" name="ck.shop-shopPromotion" setup>
  import { ref, reactive } from 'vue';
  import dayjs from 'dayjs';
  import { BasicTable, TableAction } from '/@/components/Table';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { columns, superQuerySchema, PROMOTION_TIME_FORMAT, PROMOTION_TYPE_OPTIONS, PROMOTION_STATUS_OPTIONS } from './ShopPromotion.data';
  import { list, deleteOne, batchDelete, getImportUrl, getExportUrl, copyPromotion, updatePromotionStatus, updateStatusBatch, storeAll } from './ShopPromotion.api';
  import PromotionModal from './components/PromotionModal.vue';

  const formRef = ref();
  const queryParam = reactive<any>({});
  const registerModal = ref();
  const { createMessage, createConfirm } = useMessage();

  const TYPE_OPTIONS = PROMOTION_TYPE_OPTIONS;
  const STATUS_OPTIONS = PROMOTION_STATUS_OPTIONS;
  const TIME_FORMAT = PROMOTION_TIME_FORMAT;

  /** 店铺下拉（仅在拥有按店铺筛选权限时展示，但选项照常加载，避免切换权限后空列表） */
  const storeOptions = ref<any[]>([]);

  /** 活动开始时间范围：转成契约的 startTimeBegin / startTimeEnd 两个字符串 */
  const startTimeRange = ref<any[]>([]);

  //注册table数据
  const { tableContext, onExportXls, onImportXls } = useListPage({
    tableProps: {
      title: '促销活动',
      api: list,
      columns,
      canResize: true,
      // 列设置：默认列集之外还能自己加（清单 A24）
      showTableSetting: true,
      useSearchForm: false,
      actionColumn: {
        width: 200,
        fixed: 'right',
      },
      beforeFetch: async (params) => {
        return Object.assign(params, queryParam);
      },
    },
    exportConfig: {
      name: '促销活动',
      url: getExportUrl,
      params: queryParam,
    },
    importConfig: {
      url: getImportUrl,
      success: handleSuccess,
    },
  });

  const [registerTable, { reload }, { rowSelection, selectedRowKeys }] = tableContext;

  const labelCol = reactive({ xs: 24, sm: 4, xl: 6, xxl: 4 });
  const wrapperCol = reactive({ xs: 24, sm: 20 });

  /** 高级查询配置 */
  const superQueryConfig = reactive(superQuerySchema);

  function handleSuperQuery(params) {
    Object.keys(params).map((k) => {
      queryParam[k] = params[k];
    });
    searchQuery();
  }

  /** 加载店铺下拉 */
  async function loadOptions() {
    try {
      const res = await storeAll();
      const records = Array.isArray(res) ? res : res?.records || [];
      storeOptions.value = records.map((item: any) => ({
        label: `${item.name || item.id}（${item.code || '-'}）`,
        value: item.id,
      }));
    } catch (e) {
      // 下拉加载失败不阻塞列表
      console.warn('加载店铺筛选项失败', e);
    }
  }

  /** RangePicker -> startTimeBegin / startTimeEnd */
  function syncTimeRange(value: any) {
    const [start, end] = value || [];
    queryParam.startTimeBegin = start ? dayjs(start).format(TIME_FORMAT) : undefined;
    queryParam.startTimeEnd = end ? dayjs(end).format(TIME_FORMAT) : undefined;
  }

  /** 新增 */
  function handleAdd() {
    registerModal.value.disableSubmit = false;
    registerModal.value.add();
  }

  /** 编辑 */
  function handleEdit(record: Recordable) {
    registerModal.value.disableSubmit = false;
    registerModal.value.edit(record);
  }

  /** 详情（只读） */
  function handleDetail(record: Recordable) {
    registerModal.value.disableSubmit = true;
    registerModal.value.edit(record);
  }

  /** 删除 */
  async function handleDelete(record) {
    await deleteOne({ id: record.id }, handleSuccess);
  }

  /** 批量删除 */
  async function batchHandleDelete() {
    await batchDelete({ ids: selectedRowKeys.value }, handleSuccess, selectedRowKeys.value.length);
  }

  /** 批量上线/下线：确认框写清条数与影响 */
  function batchChangeStatus(status: 'Y' | 'N') {
    const count = selectedRowKeys.value.length;
    if (!count) {
      return;
    }
    const action = status === 'Y' ? '上线' : '下线';
    createConfirm({
      iconType: 'warning',
      title: `确认批量${action}`,
      content: `将把选中的 ${count} 个活动${action}，${status === 'Y' ? '上线后前台立即生效' : '下线后前台立即失效'}。`,
      okText: `确认${action}`,
      cancelText: '取消',
      onOk: async () => {
        const res: any = await updateStatusBatch({ ids: selectedRowKeys.value.join(','), status });
        if (res?.success === false) {
          throw new Error(res.message || `${action}失败`);
        }
        createMessage.success(res?.message || `已${action} ${count} 个活动`);
        handleSuccess();
      },
    });
  }

  /**
   * 复制活动。
   * 后端语义：新 id、编码默认「原编码-COPY-时间戳」、时间窗原样复制、状态强制停用、标题加「(副本)」。
   * 这里必须把「复制出来是停用的」说清楚，否则运营会以为复制完就上线了。
   */
  function handleCopy(record) {
    createConfirm({
      iconType: 'warning',
      title: '确认复制活动',
      content: `将复制「${record.title || record.code}」的多语言标题与关联商品。时间窗原样复制，状态强制为「停用」，编码默认「${record.code}-COPY-时间戳」，复制后请确认时间窗与商品再启用。`,
      okText: '复制',
      cancelText: '取消',
      onOk: async () => {
        const res = await copyPromotion({ id: record.id });
        if (res?.success) {
          createMessage.success('复制成功：新活动为「停用」状态，请编辑确认时间窗与商品后启用');
          handleSuccess();
        } else {
          createMessage.warning(res?.message || '复制失败');
        }
      },
    });
  }

  /** 上 / 下线 */
  function handleToggleStatus(record) {
    const nextStatus = record.status === 'Y' ? 'N' : 'Y';
    createConfirm({
      iconType: 'warning',
      title: nextStatus === 'Y' ? '确认启用' : '确认停用',
      content:
        nextStatus === 'Y'
          ? `启用后「${record.title || record.code}」在时间窗内会出现在店铺端首页秒杀条，是否继续？`
          : `停用后「${record.title || record.code}」立即从店铺端首页隐藏，是否继续？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await updatePromotionStatus({ id: record.id, status: nextStatus });
        createMessage.success(nextStatus === 'Y' ? '启用成功' : '停用成功');
        handleSuccess();
      },
    });
  }

  /** 成功回调 */
  function handleSuccess() {
    selectedRowKeys.value = [];
    reload();
  }

  /** 操作栏 */
  function getTableAction(record) {
    return [
      {
        label: '编辑',
        onClick: handleEdit.bind(null, record),
        auth: 'shop-promotion:edit',
      },
      {
        label: record.status === 'Y' ? '停用' : '启用',
        onClick: handleToggleStatus.bind(null, record),
        auth: 'shop-promotion:edit',
      },
    ];
  }

  /** 下拉操作栏 */
  function getDropDownAction(record) {
    return [
      {
        label: '详情',
        onClick: handleDetail.bind(null, record),
      },
      {
        label: '复制',
        onClick: handleCopy.bind(null, record),
        auth: 'shop-promotion:copy',
      },
      {
        label: '删除',
        popConfirm: {
          title: '是否确认删除（软删除，会同时清理多语言与关联商品）',
          confirm: handleDelete.bind(null, record),
          placement: 'topLeft',
        },
        auth: 'shop-promotion:delete',
      },
    ];
  }

  /** 查询 */
  function searchQuery() {
    reload();
  }

  /**
   * 导出前先讲清楚"导出的是当前筛选结果"（清单 A24），
   * 避免运营以为导出的是全量活动。
   */
  function handleExport() {
    const active = Object.entries(queryParam || {})
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
      .map(([key, value]) => `${key}=${value}`);
    createConfirm({
      iconType: 'info',
      title: '导出当前筛选结果',
      content: active.length
        ? `将按当前筛选条件导出（${active.join('，')}），包含全部页；不是全量数据。`
        : '当前没有筛选条件，将导出全部数据。',
      okText: '开始导出',
      cancelText: '取消',
      onOk: () => {
        onExportXls();
      },
    });
  }

  /** 重置 */
  function searchReset() {
    formRef.value.resetFields();
    Object.keys(queryParam).forEach((key) => delete queryParam[key]);
    startTimeRange.value = [];
    selectedRowKeys.value = [];
    reload();
  }

  loadOptions();
</script>

<style lang="less" scoped>
  .jeecg-basic-table-form-container {
    padding: 0;
    .table-page-search-submitButtons {
      display: block;
      margin-bottom: 24px;
      white-space: nowrap;
    }
    .ant-form-item:not(.ant-form-item-with-help) {
      margin-bottom: 16px;
      height: 32px;
    }
    :deep(.ant-picker),
    :deep(.ant-input-number) {
      width: 100%;
    }
  }

  .import-export-alert {
    margin-bottom: 12px;
  }
</style>
