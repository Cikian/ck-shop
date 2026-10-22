<template>
  <div class="p-2">
    <!--查询区域-->
    <div class="jeecg-basic-table-form-container">
      <a-form ref="formRef" @keyup.enter="searchQuery" :model="queryParam" :label-col="labelCol" :wrapper-col="wrapperCol">
        <a-row :gutter="24">
          <a-col :lg="6">
            <a-form-item name="keyword">
              <template #label><span title="订单号 / 用户账号 / 收货人 / 手机号">关键字</span></template>
              <a-input v-model:value="queryParam.keyword" placeholder="订单号 / 用户账号 / 收货人 / 手机号" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="orderStatus">
              <template #label><span title="订单状态">订单状态</span></template>
              <a-select v-model:value="queryParam.orderStatus" placeholder="全部状态" allow-clear :options="ORDER_STATUS_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="payStatus">
              <template #label><span title="支付状态">支付状态</span></template>
              <a-select v-model:value="queryParam.payStatus" placeholder="全部状态" allow-clear :options="PAY_STATUS_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="refundStatus">
              <template #label><span title="退款状态">退款状态</span></template>
              <a-select v-model:value="queryParam.refundStatus" placeholder="全部状态" allow-clear :options="REFUND_STATUS_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="storeId">
              <template #label><span title="所属店铺">所属店铺</span></template>
              <a-select
                v-model:value="queryParam.storeId"
                placeholder="全部可见店铺"
                allow-clear
                show-search
                option-filter-prop="label"
                :options="storeOptions"
              />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="payChannel">
              <template #label><span title="支付渠道">支付渠道</span></template>
              <a-select v-model:value="queryParam.payChannel" placeholder="全部渠道" allow-clear :options="PAY_CHANNEL_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :lg="8">
            <a-form-item name="createTimeRange">
              <template #label><span title="下单时间">下单时间</span></template>
              <a-range-picker
                v-model:value="createTimeRange"
                show-time
                :format="TIME_FORMAT"
                :placeholder="['下单时间从', '下单时间到']"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :lg="8">
            <a-form-item name="payTimeRange">
              <template #label><span title="支付时间">支付时间</span></template>
              <a-range-picker
                v-model:value="payTimeRange"
                show-time
                :format="TIME_FORMAT"
                :placeholder="['支付时间从', '支付时间到']"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :lg="8">
            <a-form-item name="amountRange">
              <template #label><span title="应付金额区间">金额区间</span></template>
              <a-input-group compact>
                <a-input-number v-model:value="queryParam.minAmount" placeholder="最低" :min="0" :precision="2" style="width: calc(50% - 14px)" />
                <span class="amount-split">~</span>
                <a-input-number v-model:value="queryParam.maxAmount" placeholder="最高" :min="0" :precision="2" style="width: calc(50% - 14px)" />
              </a-input-group>
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="includeArchived">
              <template #label><span title="是否包含已归档订单">包含已归档</span></template>
              <a-switch v-model:checked="queryParam.includeArchived" checked-children="是" un-checked-children="否" />
              <span class="text-xs text-gray-400 ml-2">默认只看未归档订单</span>
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

    <!-- 订单没有删除能力，这件事必须在界面上讲清楚，否则运营会一直找"删除按钮" -->
    <a-alert
      type="warning"
      show-icon
      class="order-policy-alert"
      message="订单严禁删除，本页不提供任何删除入口"
      description="有效订单（待支付 / 待发货 / 已发货）连归档都不允许。只有【已取消】或【已关闭】的订单可以归档，归档只是从默认列表收起，订单明细、支付单、退款单、操作流水全部完整保留，按单号仍可检索。"
    />

    <!--引用表格-->
    <BasicTable @register="registerTable">
      <!--操作栏-->
      <template #action="{ record }">
        <TableAction :actions="getTableAction(record)" :dropDownActions="getDropDownAction(record)" />
      </template>
    </BasicTable>

    <!-- 详情 / 发货 / 备注 / 关闭 / 补偿 -->
    <ShopOrderDetailModal ref="detailModalRef" />
    <OrderShipModal ref="shipModalRef" @success="handleSuccess" />
    <OrderRemarkModal ref="remarkModalRef" @success="handleSuccess" />
    <OrderCloseModal ref="closeModalRef" @success="handleSuccess" />
    <OrderCompensateModal ref="compensateModalRef" @success="handleSuccess" />
  </div>
</template>

<script lang="ts" name="ck.shop-shopOrder" setup>
  import { ref, reactive } from 'vue';
  import dayjs from 'dayjs';
  import { BasicTable, TableAction } from '/@/components/Table';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { columns, ORDER_TIME_FORMAT, ORDER_STATUS_OPTIONS, PAY_STATUS_OPTIONS, REFUND_STATUS_OPTIONS, PAY_CHANNEL_OPTIONS } from './ShopOrder.data';
  import { list, archive, storeAll } from './ShopOrder.api';
  import ShopOrderDetailModal from './components/ShopOrderDetailModal.vue';
  import OrderShipModal from './components/OrderShipModal.vue';
  import OrderRemarkModal from './components/OrderRemarkModal.vue';
  import OrderCloseModal from './components/OrderCloseModal.vue';
  import OrderCompensateModal from './components/OrderCompensateModal.vue';

  const formRef = ref();
  const { createMessage, createConfirm } = useMessage();

  const detailModalRef = ref();
  const shipModalRef = ref();
  const remarkModalRef = ref();
  const closeModalRef = ref();
  const compensateModalRef = ref();

  const TIME_FORMAT = ORDER_TIME_FORMAT;

  /** 查询条件：除时间区间与金额外都用 v-model 直接绑在这里 */
  const queryParam = reactive<any>({ includeArchived: false });

  /** 下单时间区间 / 支付时间区间：界面用 Dayjs，提交前转成 yyyy-MM-dd HH:mm:ss 字符串 */
  const createTimeRange = ref<any[]>([]);
  const payTimeRange = ref<any[]>([]);

  /** 店铺下拉（启用中的店铺；加载失败不阻塞列表） */
  const storeOptions = ref<any[]>([]);

  /** 终态订单不能再关闭（后端也会拒绝，这里只是少一次无效往返） */
  const FINAL_STATUS = ['COMPLETED', 'CANCELLED', 'CLOSED'];
  /** 只有这两种状态允许归档（有效订单在逻辑层面也禁止删除） */
  const ARCHIVABLE_STATUS = ['CANCELLED', 'CLOSED'];

  /**
   * 组装后端 OrderQuery：
   * 空值一律不传，避免 '' 变成"某个空字符串条件"。
   */
  function buildQuery() {
    const [createStart, createEnd] = createTimeRange.value || [];
    const [payStart, payEnd] = payTimeRange.value || [];
    const params: Record<string, any> = {
      ...queryParam,
      startTime: createStart ? dayjs(createStart).format(TIME_FORMAT) : undefined,
      endTime: createEnd ? dayjs(createEnd).format(TIME_FORMAT) : undefined,
      payStartTime: payStart ? dayjs(payStart).format(TIME_FORMAT) : undefined,
      payEndTime: payEnd ? dayjs(payEnd).format(TIME_FORMAT) : undefined,
    };
    Object.keys(params).forEach((key) => {
      const value = params[key];
      if (value === undefined || value === null || value === '') {
        delete params[key];
      }
    });
    // 归档开关是布尔值，必须有明确取值（false 不能被当成"没传"）
    params.includeArchived = !!queryParam.includeArchived;
    return params;
  }

  //注册table数据
  const { tableContext } = useListPage({
    tableProps: {
      title: '订单',
      api: list,
      columns,
      canResize: true,
      // 默认列集只保留 6 列（订单号/店铺/订单状态/支付状态/应付/下单时间），
      // 其余列 defaultHidden，运营可在「列设置」里按需打开（A8）
      showTableSetting: true,
      useSearchForm: false,
      // 订单严禁删除：不绑定 rowSelection，界面上不会出现任何"选中后批量操作"的入口
      actionColumn: {
        width: 170,
        fixed: 'right',
      },
      beforeFetch: async (params) => {
        // 注意：useListPage 会忽略 beforeFetch 的返回值，只有**就地修改 params** 才生效
        return Object.assign(params, buildQuery());
      },
    },
  });

  const [registerTable, { reload }] = tableContext;

  const labelCol = reactive({ xs: 24, sm: 4, xl: 6, xxl: 4 });
  const wrapperCol = reactive({ xs: 24, sm: 20 });

  /** 加载店铺下拉 */
  async function loadOptions() {
    try {
      const res = await storeAll();
      const records = Array.isArray(res) ? res : res?.records || [];
      storeOptions.value = records.map((item: any) => ({
        label: item.name ? `${item.name}（${item.code || '-'}）` : item.id,
        value: item.id,
      }));
    } catch (e) {
      // 下拉加载失败不阻塞列表（订单列表本身仍可用单号/关键字查询）
      console.warn('加载店铺筛选项失败', e);
    }
  }

  /** 详情 */
  function handleDetail(record: Recordable) {
    detailModalRef.value?.open(record);
  }

  /** 发货（仅待发货订单） */
  function handleShip(record: Recordable) {
    shipModalRef.value?.open(record);
  }

  /** 改卖家备注 */
  function handleRemark(record: Recordable) {
    remarkModalRef.value?.open(record);
  }

  /** 关闭订单 */
  function handleClose(record: Recordable) {
    closeModalRef.value?.open(record);
  }

  /**
   * 归档：把"这不是删除"说清楚再执行
   */
  function handleArchive(record: Recordable) {
    createConfirm({
      iconType: 'warning',
      title: '确认归档订单',
      content: `归档后订单「${record.orderNo}」将从默认列表中收起，可按单号检索。订单明细、支付单、退款单、操作流水全部完整保留，任何情况下都不会被物理删除。是否继续？`,
      okText: '归档',
      cancelText: '取消',
      onOk: async () => {
        await archive(record.orderNo);
        createMessage.success('订单已归档（数据完整保留，可按单号检索）');
        handleSuccess();
      },
    });
  }

  /** 人工补偿：按订单号主动查询渠道支付结果 */
  function handleCompensate(record: Recordable) {
    compensateModalRef.value?.open(record);
  }

  /** 成功回调 */
  function handleSuccess() {
    reload();
  }

  /** 操作栏：详情 / 发货 */
  function getTableAction(record) {
    return [
      {
        label: '详情',
        onClick: handleDetail.bind(null, record),
        auth: 'shop-order:detail',
      },
      {
        label: '发货',
        onClick: handleShip.bind(null, record),
        auth: 'shop-order:ship',
        // 只有待发货订单能发货，其它状态显示出来只会点出错误
        ifShow: record.orderStatus === 'WAIT_SHIP',
      },
    ];
  }

  /** 下拉操作栏：改备注 / 关闭 / 归档 / 人工补偿（没有"删除"） */
  function getDropDownAction(record) {
    return [
      {
        label: '改备注',
        onClick: handleRemark.bind(null, record),
        auth: 'shop-order:remark',
      },
      {
        label: '关闭订单',
        onClick: handleClose.bind(null, record),
        auth: 'shop-order:close',
        ifShow: !FINAL_STATUS.includes(record.orderStatus),
      },
      {
        label: '归档',
        onClick: handleArchive.bind(null, record),
        auth: 'shop-order:archive',
        // 有效订单禁止归档（后端硬校验，前端同步隐藏）
        ifShow: ARCHIVABLE_STATUS.includes(record.orderStatus),
      },
      {
        label: '人工补偿',
        onClick: handleCompensate.bind(null, record),
        auth: 'shop-order:compensate',
      },
    ];
  }

  /** 查询 */
  function searchQuery() {
    reload();
  }

  /** 重置 */
  function searchReset() {
    formRef.value?.resetFields();
    Object.keys(queryParam).forEach((key) => delete queryParam[key]);
    queryParam.includeArchived = false;
    createTimeRange.value = [];
    payTimeRange.value = [];
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
    }
    :deep(.ant-picker),
    :deep(.ant-input-number) {
      width: 100%;
    }
  }

  .order-policy-alert {
    margin-bottom: 12px;
  }

  .amount-split {
    display: inline-block;
    width: 28px;
    line-height: 32px;
    text-align: center;
    color: rgba(0, 0, 0, 0.45);
  }
</style>
