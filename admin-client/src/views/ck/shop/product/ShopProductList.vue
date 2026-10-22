<template>
  <div class="p-2">
    <!--查询区域-->
    <div class="jeecg-basic-table-form-container">
      <a-form ref="formRef" @keyup.enter="searchQuery" :model="queryParam" :label-col="labelCol" :wrapper-col="wrapperCol">
        <a-row :gutter="24">
          <a-col :lg="6">
            <a-form-item name="keyword">
              <template #label><span title="商品名称">商品名称</span></template>
              <a-input v-model:value="queryParam.keyword" placeholder="输入商品名称模糊搜索" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="spuCode">
              <template #label><span title="SPU编码">SPU编码</span></template>
              <a-input v-model:value="queryParam.spuCode" placeholder="输入SPU编码精确匹配" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="categoryId">
              <template #label><span title="分类">分类</span></template>
              <a-select v-model:value="queryParam.categoryId" placeholder="全部分类" allow-clear show-search optionFilterProp="label" :options="categoryOptions" />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="status">
              <template #label><span title="状态">状态</span></template>
              <a-select v-model:value="queryParam.status" placeholder="全部状态" allow-clear :options="statusOptions" />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="tagId">
              <template #label><span title="标签">标签</span></template>
              <a-select v-model:value="queryParam.tagId" placeholder="全部标签" allow-clear show-search optionFilterProp="label" :options="tagOptions" />
            </a-form-item>
          </a-col>
          <!-- 按店铺筛选只对"能跨店看数据"的账号有意义：运营选了别的店铺也只会得到空结果
               （后端可见域取交集，不会放大范围），因此用按钮权限把它收起来 -->
          <a-col v-auth="'shop-product:filter-store'" :lg="6">
            <a-form-item name="storeId">
              <template #label><span title="所属店铺">所属店铺</span></template>
              <a-select v-model:value="queryParam.storeId" placeholder="全部店铺" allow-clear show-search optionFilterProp="label" :options="storeOptions" />
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

    <!--引用表格-->
    <BasicTable @register="registerTable" :rowSelection="rowSelection">
      <template #tableTitle>
        <a-button type="primary" v-auth="'shop-product:add'" @click="handleAdd" preIcon="ant-design:plus-outlined">新增商品</a-button>
        <a-button type="primary" v-auth="'shop-product:exportXls'" preIcon="ant-design:export-outlined" @click="onExportXls">导出</a-button>
        <j-upload-button type="primary" v-auth="'shop-product:importExcel'" preIcon="ant-design:import-outlined" @click="onImportXls">导入</j-upload-button>
        <a-dropdown v-if="selectedRowKeys.length > 0">
          <template #overlay>
            <a-menu>
              <a-menu-item key="1" @click="batchHandleDelete">
                <Icon icon="ant-design:delete-outlined"></Icon>
                删除
              </a-menu-item>
            </a-menu>
          </template>
          <a-button v-auth="'shop-product:deleteBatch'">
            批量操作
            <Icon icon="mdi:chevron-down"></Icon>
          </a-button>
        </a-dropdown>
        <super-query :config="superQueryConfig" @search="handleSuperQuery" />
      </template>

      <!-- 展开行：直接看该商品的规格组合与库存，不用进编辑页 -->
      <template #expandedRowRender="{ record }">
        <ProductSkuExpand :product-id="record.id" />
      </template>

      <!--操作栏-->
      <template #action="{ record }">
        <TableAction :actions="getTableAction(record)" :dropDownActions="getDropDownAction(record)" />
      </template>
    </BasicTable>

    <!-- 表单区域 -->
    <ShopProductModal ref="registerModal" @success="handleSuccess"></ShopProductModal>
  </div>
</template>

<script lang="ts" name="ck.shop-shopProduct" setup>
  import { ref, reactive } from 'vue';
  import { BasicTable, TableAction } from '/@/components/Table';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { columns, superQuerySchema, setOrgStoreNameMap } from './ShopProduct.data';
  import {
    list,
    deleteOne,
    batchDelete,
    getImportUrl,
    getExportUrl,
    updateProductStatus,
  } from './ShopProduct.api';
  import ShopProductModal from './components/ShopProductModal.vue';
  import ProductSkuExpand from './components/ProductSkuExpand.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { loadProductReferenceOptions } from './productOptionsCache';

  const formRef = ref();
  const queryParam = reactive<any>({});
  const registerModal = ref();
  const { createMessage, createConfirm } = useMessage();

  // 下拉选项
  const categoryOptions = ref<any[]>([]);
  const tagOptions = ref<any[]>([]);
  const storeOptions = ref<any[]>([]);
  const statusOptions = [
    { label: '上架', value: 'Y' },
    { label: '下架', value: 'N' },
  ];

  //注册table数据
  const { tableContext, onExportXls, onImportXls } = useListPage({
    tableProps: {
      title: '商品',
      api: list,
      columns,
      canResize: true,
      useSearchForm: false,
      expandRowByClick: false,
      actionColumn: {
        width: 160,
        fixed: 'right',
      },
      beforeFetch: async (params) => {
        // tagId 只用于筛选，不能作为 ShopProduct 实体字段传给 QueryGenerator
        const { tagId, keyword, ...rest } = queryParam;
        return Object.assign(params, rest, { tagId: tagId || '', keyword: keyword || '' });
      },
    },
    exportConfig: {
      name: '商品',
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

  /**
   * 加载下拉选项（模块级缓存 + 店铺按权限懒加载，见 productOptionsCache.ts）。
   *
   * 分类/标签/店铺都是分钟级不变的参考数据：以前每次进入列表都要打三个请求，
   * 其中 storeAll() 对没有 shop-product:filter-store 权限的账号完全没用（筛选项根本不显示）。
   */
  const { hasPermission } = usePermission();

  async function loadOptions() {
    try {
      const includeStores = hasPermission('shop-product:filter-store');
      const options = await loadProductReferenceOptions(includeStores);
      categoryOptions.value = options.categories;
      tagOptions.value = options.tags;
      storeOptions.value = options.stores.map((item: any) => ({
        label: `${item.name}（${item.code}）`,
        value: item.id,
      }));
      // 「所属店铺」列按商品 sysOrgCode（= 店铺 departOrgCode）反查店铺名
      setOrgStoreNameMap(options.stores);
    } catch (e) {
      // 下拉加载失败不阻塞列表
      console.warn('加载商品筛选项失败', e);
    }
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

  /** 详情 */
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
    await batchDelete({ ids: selectedRowKeys.value }, handleSuccess);
  }

  /** 上架 / 下架 */
  function handleToggleStatus(record) {
    const nextStatus = record.status === 'Y' ? 'N' : 'Y';
    createConfirm({
      iconType: 'warning',
      title: nextStatus === 'Y' ? '确认上架' : '确认下架',
      content:
        nextStatus === 'Y'
          ? `上架后「${record.title || record.spuCode}」将在店铺端可见，是否继续？`
          : `下架后「${record.title || record.spuCode}」将从店铺端隐藏，是否继续？`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        await updateProductStatus({ id: record.id, status: nextStatus });
        createMessage.success(nextStatus === 'Y' ? '上架成功' : '下架成功');
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
        auth: 'shop-product:edit',
      },
      {
        label: record.status === 'Y' ? '下架' : '上架',
        onClick: handleToggleStatus.bind(null, record),
        auth: 'shop-product:edit',
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
        label: '删除',
        popConfirm: {
          title: '是否确认删除（软删除，可从数据库恢复）',
          confirm: handleDelete.bind(null, record),
          placement: 'topLeft',
        },
        auth: 'shop-product:delete',
      },
    ];
  }

  /** 查询 */
  function searchQuery() {
    reload();
  }

  /** 重置 */
  function searchReset() {
    formRef.value.resetFields();
    Object.keys(queryParam).forEach((key) => delete queryParam[key]);
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

  .sku-expand {
    padding: 4px 8px 8px 48px;

    .sku-expand-hint {
      color: rgba(0, 0, 0, 0.45);
      font-size: 13px;
      padding: 8px 0;
    }

    .sku-thumb {
      width: 34px;
      height: 34px;
      object-fit: cover;
      border-radius: 4px;
      border: 1px solid #f0f0f0;
    }

    .sku-price-fallback {
      color: rgba(0, 0, 0, 0.45);
      font-size: 12px;
    }

    :deep(.sku-row-unsaleable) {
      background-color: #fafafa;
      color: rgba(0, 0, 0, 0.45);
    }
  }
</style>
