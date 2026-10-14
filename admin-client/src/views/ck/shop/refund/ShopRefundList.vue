<template>
  <div class="p-2">
    <!--查询区域-->
    <div class="jeecg-basic-table-form-container">
      <a-form ref="formRef" @keyup.enter="searchQuery" :model="queryParam" :label-col="labelCol" :wrapper-col="wrapperCol">
        <a-row :gutter="24">
          <a-col :lg="6">
            <a-form-item name="refundNo">
              <template #label><span title="退款单号">退款单号</span></template>
              <a-input v-model:value="queryParam.refundNo" placeholder="输入退款单号精确搜索" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="orderNo">
              <template #label><span title="订单号">订单号</span></template>
              <a-input v-model:value="queryParam.orderNo" placeholder="输入订单号精确搜索" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :lg="6">
            <a-form-item name="status">
              <template #label><span title="退款状态">退款状态</span></template>
              <a-select v-model:value="queryParam.status" placeholder="全部状态" allow-clear :options="REFUND_ORDER_STATUS_OPTIONS" />
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
          <a-col :lg="12">
            <a-form-item name="applyTimeRange">
              <template #label><span title="申请时间">申请时间</span></template>
              <a-range-picker
                v-model:value="applyTimeRange"
                show-time
                :format="TIME_FORMAT"
                :placeholder="['申请时间从', '申请时间到']"
                style="width: 100%"
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

    <!-- 审核是两步式的：这个前提必须写在界面上，否则"点了通过钱却没到"会被当成系统故障 -->
    <a-alert
      type="info"
      show-icon
      class="refund-tip-alert"
      message="「审核通过」不等于「钱已退到账」"
      description="审核通过后，后端先在该笔支付单上占用退款额度（原子校验，防并发超额），再向渠道提交退款。渠道可能同步成功、可能受理中（退款中）、也可能失败（可重试）。请以【状态】与【到账时间】为准，失败或退款中的单子可以点【重试渠道退款】。"
    />

    <!--引用表格-->
    <BasicTable @register="registerTable">
      <!--操作栏-->
      <template #action="{ record }">
        <TableAction :actions="getTableAction(record)" />
      </template>
    </BasicTable>

    <RefundAuditModal ref="auditModalRef" @success="handleSuccess" />
    <ShopRefundDetailModal ref="detailModalRef" />
  </div>
</template>

<script lang="ts" name="ck.shop-shopRefund" setup>
  import { ref, reactive } from 'vue';
  import dayjs from 'dayjs';
  import { BasicTable, TableAction } from '/@/components/Table';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { columns, REFUND_ORDER_STATUS_OPTIONS, REFUND_TIME_FORMAT, canAudit, canRetryChannelRefund } from './ShopRefund.data';
  import { list, retry, storeAll } from './ShopRefund.api';
  import RefundAuditModal from './components/RefundAuditModal.vue';
  import ShopRefundDetailModal from './components/ShopRefundDetailModal.vue';

  const formRef = ref();
  const { createMessage, createConfirm } = useMessage();

  const auditModalRef = ref();
  const detailModalRef = ref();

  const TIME_FORMAT = REFUND_TIME_FORMAT;

  const queryParam = reactive<any>({});
  const applyTimeRange = ref<any[]>([]);
  const storeOptions = ref<any[]>([]);

  /** 申请时间区间 → RefundQuery.startTime / endTime */
  function buildQuery() {
    const [start, end] = applyTimeRange.value || [];
    const params: Record<string, any> = {
      ...queryParam,
      startTime: start ? dayjs(start).format(TIME_FORMAT) : undefined,
      endTime: end ? dayjs(end).format(TIME_FORMAT) : undefined,
    };
    Object.keys(params).forEach((key) => {
      const value = params[key];
      if (value === undefined || value === null || value === '') {
        delete params[key];
      }
    });
    return params;
  }

  //注册table数据
  const { tableContext } = useListPage({
    tableProps: {
      title: '退款单',
      api: list,
      columns,
      canResize: true,
      useSearchForm: false,
      actionColumn: {
        width: 170,
        fixed: 'right',
      },
      beforeFetch: async (params) => {
        // useListPage 会忽略返回值，必须就地修改 params
        return Object.assign(params, buildQuery());
      },
    },
  });

  const [registerTable, { reload }] = tableContext;

  const labelCol = reactive({ xs: 24, sm: 4, xl: 6, xxl: 4 });
  const wrapperCol = reactive({ xs: 24, sm: 20 });

  /** 加载店铺下拉（失败不阻塞列表） */
  async function loadOptions() {
    try {
      const res = await storeAll();
      const records = Array.isArray(res) ? res : res?.records || [];
      storeOptions.value = records.map((item: any) => ({
        label: item.name ? `${item.name}（${item.code || '-'}）` : item.id,
        value: item.id,
      }));
    } catch (e) {
      console.warn('加载店铺筛选项失败', e);
    }
  }

  /** 详情 */
  function handleDetail(record: Recordable) {
    detailModalRef.value?.open(record);
  }

  /** 审核（通过 / 拒绝） */
  function handleAudit(record: Recordable) {
    auditModalRef.value?.open(record);
  }

  /**
   * 重试渠道退款（仅失败 / 退款中）
   *
   * 重试前必须确认"这笔退款当前确实没有成功"——重复退款是真金白银的损失，
   * 因此这里用确认框再拦一道，并把状态念出来。
   */
  function handleRetry(record: Recordable) {
    createConfirm({
      iconType: 'warning',
      title: '确认重试渠道退款',
      content: `退款单「${record.refundNo}」当前状态为「${record.statusLabel || record.status}」，将重新向渠道提交退款 ¥${record.refundAmount}。请确认该笔退款尚未成功到账。`,
      okText: '重试',
      cancelText: '取消',
      onOk: async () => {
        await retry(record.refundNo);
        createMessage.success('已重新提交渠道退款');
        handleSuccess();
      },
    });
  }

  /** 成功回调 */
  function handleSuccess() {
    reload();
  }

  /** 操作栏：详情 / 审核 / 重试 */
  function getTableAction(record) {
    return [
      {
        label: '详情',
        onClick: handleDetail.bind(null, record),
        auth: 'shop-refund:list',
      },
      {
        label: '审核',
        onClick: handleAudit.bind(null, record),
        auth: 'shop-refund:audit',
        // 只有待审核的退款单能审核
        ifShow: canAudit(record),
      },
      {
        label: '重试渠道退款',
        onClick: handleRetry.bind(null, record),
        auth: 'shop-refund:retry',
        // 只有失败/退款中可以重试：成功或已撤销的单子重试会变成重复退款
        ifShow: canRetryChannelRefund(record),
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
    applyTimeRange.value = [];
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
    :deep(.ant-picker) {
      width: 100%;
    }
  }

  .refund-tip-alert {
    margin-bottom: 12px;
  }
</style>
