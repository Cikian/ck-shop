<template>
  <div class="p-4">
    <!-- ==================== 筛选条件 ==================== -->
    <a-card :bordered="false" class="mb-4">
      <a-form layout="inline" :model="query">
        <a-form-item label="店铺">
          <a-select
            v-model:value="query.storeId"
            :options="storeOptions"
            placeholder="全部可见店铺"
            style="width: 200px"
            allow-clear
            show-search
            option-filter-prop="label"
            @change="reloadAll"
          />
        </a-form-item>

        <a-form-item label="统计区间">
          <a-radio-group v-model:value="rangePreset" button-style="solid" @change="reloadAll">
            <a-radio-button value="1">今日</a-radio-button>
            <a-radio-button value="7">近 7 天</a-radio-button>
            <a-radio-button value="30">近 30 天</a-radio-button>
            <a-radio-button value="90">近 90 天</a-radio-button>
            <a-radio-button value="custom">自定义</a-radio-button>
          </a-radio-group>
        </a-form-item>

        <a-form-item v-if="rangePreset === 'custom'" label="日期">
          <a-range-picker v-model:value="customRange" :allow-clear="false" @change="reloadAll" />
        </a-form-item>

        <a-form-item>
          <a-button type="primary" :loading="overviewLoading" @click="reloadAll">刷新</a-button>
        </a-form-item>
      </a-form>

      <p class="mt-2 text-xs text-gray-400">
        统计区间：{{ rangeText }}　|　口径：GMV = 已支付订单的应付金额之和（不扣退款）；实收 = 已支付订单实收之和； 净收入 = 实收 −
        退款；退款率按金额计算；下单量按下单时间、GMV/实收按支付时间、退款额按退款到账时间。
      </p>
    </a-card>

    <!-- ==================== 风险指标（最该盯的三个数） ==================== -->
    <a-card :bordered="false" class="mb-4 risk-card" :class="{ 'risk-card--danger': hasRisk }">
      <template #title>
        <span class="risk-title">风险指标</span>
        <a-tag v-if="hasRisk" color="red" class="ml-2">存在异常，请立即人工介入</a-tag>
        <a-tag v-else color="green" class="ml-2">当前无异常</a-tag>
      </template>

      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :md="8">
          <a-card :bordered="false" size="small" :class="{ 'risk-item--danger': closedButPaid > 0 }">
            <a-statistic
              title="已关闭但已收款订单（掉单）"
              :value="closedButPaid"
              :value-style="closedButPaid > 0 ? { color: '#cf1322', fontWeight: 600 } : undefined"
            />
            <p class="risk-hint"> 必须长期为 0。不为 0 说明"用户付了钱、订单却关着"，不会体现在任何常规经营指标里，需立即人工处理。 </p>
          </a-card>
        </a-col>
        <a-col :xs="24" :md="8">
          <a-card :bordered="false" size="small" :class="{ 'risk-item--warn': pendingPayments > 0 }">
            <a-statistic
              title="结果未知的支付单（支付中）"
              :value="pendingPayments"
              :value-style="pendingPayments > 0 ? { color: '#d46b08', fontWeight: 600 } : undefined"
            />
            <p class="risk-hint">渠道结果未回写。可在下方【支付补单任务】主动向渠道查询，避免掉单。</p>
          </a-card>
        </a-col>
        <a-col :xs="24" :md="8">
          <a-card :bordered="false" size="small" :class="{ 'risk-item--warn': pendingRefunds > 0 }">
            <a-statistic
              title="待审核退款单"
              :value="pendingRefunds"
              :value-style="pendingRefunds > 0 ? { color: '#d46b08', fontWeight: 600 } : undefined"
            />
            <p class="risk-hint">客服的处理压力。到「退款管理」逐笔审核；审核通过后还需关注渠道退款是否成功。</p>
          </a-card>
        </a-col>
      </a-row>
    </a-card>

    <!-- ==================== 总览卡片 ==================== -->
    <a-card :bordered="false" class="mb-4" size="small" title="订单量">
      <a-row :gutter="[16, 16]">
        <a-col v-for="card in countCards" :key="card.title" :xs="12" :sm="8" :md="6" :xl="3">
          <a-statistic :title="card.title" :value="card.value" :precision="0" />
        </a-col>
      </a-row>
    </a-card>

    <a-card :bordered="false" class="mb-4" size="small" title="金额">
      <a-row :gutter="[16, 16]">
        <a-col v-for="card in amountCards" :key="card.title" :xs="12" :sm="8" :md="6" :xl="4">
          <a-statistic :title="card.title" :value="card.value" :precision="2" prefix="¥" />
          <p v-if="card.hint" class="text-xs text-gray-400">{{ card.hint }}</p>
        </a-col>
      </a-row>
    </a-card>

    <a-card :bordered="false" class="mb-4" size="small" title="转化与比率">
      <a-row :gutter="[16, 16]">
        <a-col v-for="card in rateCards" :key="card.title" :xs="12" :sm="8" :md="6" :xl="4">
          <a-statistic :title="card.title" :value="card.text" />
          <p v-if="card.hint" class="text-xs text-gray-400">{{ card.hint }}</p>
        </a-col>
      </a-row>
    </a-card>

    <a-card :bordered="false" class="mb-4" size="small" title="今日">
      <a-row :gutter="[16, 16]">
        <a-col :xs="12" :sm="6">
          <a-statistic title="今日订单数" :value="overview?.todayOrders ?? 0" :precision="0" />
        </a-col>
        <a-col :xs="12" :sm="6">
          <a-statistic title="今日 GMV" :value="overview?.todayGmv ?? 0" :precision="2" prefix="¥" />
        </a-col>
        <a-col :xs="12" :sm="6">
          <a-statistic title="今日实收" :value="overview?.todayPaidAmount ?? 0" :precision="2" prefix="¥" />
        </a-col>
        <a-col :xs="12" :sm="6">
          <a-statistic title="今日退款" :value="overview?.todayRefundAmount ?? 0" :precision="2" prefix="¥" :value-style="{ color: '#cf1322' }" />
        </a-col>
      </a-row>
    </a-card>

    <!-- ==================== 图表 ==================== -->
    <a-row :gutter="[16, 16]" class="mb-4">
      <a-col :xs="24" :xl="9">
        <a-card :bordered="false" size="small" title="订单状态分布">
          <div ref="statusRef" style="height: 340px; width: 100%"></div>
          <a-empty v-if="!statusLoading && !statusList.length" description="该区间没有订单" />
        </a-card>
      </a-col>
      <a-col :xs="24" :xl="15">
        <a-card :bordered="false" size="small" title="订单趋势（按日）">
          <div ref="trendRef" style="height: 340px; width: 100%"></div>
          <a-empty v-if="!trendLoading && !trendList.length" description="该区间没有趋势数据" />
        </a-card>
      </a-col>
    </a-row>

    <!-- ==================== 排行 ==================== -->
    <a-card :bordered="false" size="small" class="mb-4" title="热销商品 / SKU 排行">
      <template #extra>
        <a-select v-model:value="rankLimit" style="width: 110px" :options="rankLimitOptions" @change="loadRank" />
      </template>
      <a-table
        :columns="productRankColumns"
        :data-source="productRankList"
        :loading="rankLoading"
        :pagination="false"
        row-key="rankKey"
        size="small"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'rank'">
            <a-tag :color="record.rank <= 3 ? 'orange' : 'default'">{{ record.rank }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'product'">
            <div class="flex items-center gap-2">
              <a-avatar shape="square" :size="36" :src="record.productImage || undefined" />
              <div class="min-w-0">
                <div class="truncate" style="max-width: 320px">{{ record.productName || '（商品已删除）' }}</div>
                <div class="text-xs text-gray-400">
                  {{ record.categoryName || '—' }}
                  <span v-if="record.skuName"> · {{ record.skuName }}</span>
                </div>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'salesAmount'"> ¥{{ formatAmount(record.salesAmount) }} </template>
          <template v-else-if="column.dataIndex === 'grossProfit'"> ¥{{ formatAmount(record.grossProfit) }} </template>
        </template>
      </a-table>
    </a-card>

    <a-card :bordered="false" size="small" class="mb-4" title="热销分类排行">
      <template #extra>
        <a-select v-model:value="categoryLimit" style="width: 110px" :options="rankLimitOptions" @change="loadCategoryRank" />
      </template>
      <a-table
        :columns="categoryRankColumns"
        :data-source="categoryRankList"
        :loading="categoryLoading"
        :pagination="false"
        row-key="rankKey"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'rank'">
            <a-tag :color="record.rank <= 3 ? 'orange' : 'default'">{{ record.rank }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'salesAmount'"> ¥{{ formatAmount(record.salesAmount) }} </template>
          <template v-else-if="column.dataIndex === 'grossProfit'"> ¥{{ formatAmount(record.grossProfit) }} </template>
        </template>
      </a-table>
    </a-card>

    <!-- ==================== 运维任务 ==================== -->
    <a-card :bordered="false" size="small" title="运维任务（手动触发）">
      <a-alert
        type="warning"
        show-icon
        class="mb-3"
        message="这两个任务平时由定时任务自动跑，手动触发只用于「怀疑掉单 / 需要立即核对」的场景"
        description="超时关单：关闭已过支付截止时间的待支付订单（关单前会先向渠道确认真实支付结果）。支付补单：把结果未知的支付单向渠道查一遍并回写。两者都不会重复收款或重复退款。"
      />
      <a-space wrap>
        <span>单批处理条数：</span>
        <a-input-number v-model:value="batchSize" :min="1" :max="1000" :precision="0" style="width: 120px" :disabled="!canManageAllStores" />
        <template v-if="canManageAllStores">
          <a-button v-auth="'shop-order:compensate'" type="primary" :loading="taskLoading === 'close'" @click="runTask('close')">
            手动触发超时关单任务
          </a-button>
          <a-button v-auth="'shop-order:compensate'" :loading="taskLoading === 'compensate'" @click="runTask('compensate')">
            手动触发支付补单任务
          </a-button>
        </template>
        <span v-else class="field-hint">这两个任务会扫描所有店铺的订单，仅总部/平台管理员（shop-store:manage-all）可手动触发</span>
      </a-space>

      <a-descriptions v-if="taskResult" bordered size="small" :column="{ xs: 2, sm: 3, xl: 6 }" class="mt-3">
        <a-descriptions-item label="任务">{{ taskResult.name }}</a-descriptions-item>
        <a-descriptions-item label="扫描条数">{{ taskResult.data?.scanned ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="成功条数">{{ taskResult.data?.succeeded ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="失败条数">{{ taskResult.data?.failed ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="跳过条数">{{ taskResult.data?.skipped ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="耗时(ms)">{{ taskResult.data?.costMillis ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="说明" :span="6">{{ taskResult.data?.message || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, ref } from 'vue';
  import dayjs, { Dayjs } from 'dayjs';
  import type { EChartsOption } from 'echarts';
  import { Input } from 'ant-design-vue';
  import { useECharts } from '/@/hooks/web/useECharts';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { usePermission } from '/@/hooks/web/usePermission';
  import {
    statsOverview,
    statsStatus,
    statsTrend,
    statsRankProduct,
    statsRankCategory,
    runCloseTimeoutTask,
    runCompensateTask,
    storeAll,
    type OrderQuery,
    type OrderStatsOverviewVO,
    type OrderStatsRankVO,
    type OrderStatsStatusVO,
    type OrderStatsTrendVO,
    type OrderTaskResult,
  } from './ShopOrder.api';
  import { formatAmount, formatRate, ORDER_TIME_FORMAT } from './ShopOrder.data';

  const { createMessage, createConfirm } = useMessage();
  const { hasPermission } = usePermission();
  /**
   * 运维任务会扫描**所有店铺**的订单，后端也要求 shop-store:manage-all；
   * 前端不满足权限时不显示按钮（而不是"显示了点了才 403"）。
   */
  const canManageAllStores = computed(() => hasPermission('shop-store:manage-all'));

  // ------------------------------------------------------------------
  // 查询条件
  // ------------------------------------------------------------------

  type RangePreset = '1' | '7' | '30' | '90' | 'custom';

  const rangePreset = ref<RangePreset>('30');
  const customRange = ref<[Dayjs, Dayjs]>([dayjs().subtract(29, 'day'), dayjs()]);
  const query = ref<{ storeId?: string }>({ storeId: undefined });

  const storeOptions = ref<any[]>([]);

  /**
   * 界面上的"快捷区间 / 自定义日期" → 后端 OrderQuery。
   *
   * 结束时间取当日 23:59:59：后端 QueryDateUtils.parseEnd 会把它 +1 秒变成
   * "次日 00:00:00（右开）"，与后端左闭右开口径严格对齐，不会漏掉最后一秒的订单。
   */
  function buildQuery(): OrderQuery {
    if (rangePreset.value === 'custom') {
      const [start, end] = customRange.value || [];
      return {
        storeId: query.value.storeId,
        startTime: start ? dayjs(start).startOf('day').format(ORDER_TIME_FORMAT) : undefined,
        endTime: end ? dayjs(end).endOf('day').format(ORDER_TIME_FORMAT) : undefined,
      };
    }
    const days = Number(rangePreset.value);
    return {
      storeId: query.value.storeId,
      startTime: dayjs()
        .subtract(days - 1, 'day')
        .startOf('day')
        .format(ORDER_TIME_FORMAT),
      endTime: dayjs().endOf('day').format(ORDER_TIME_FORMAT),
    };
  }

  const rangeText = computed(() => {
    const params = buildQuery();
    return `${params.startTime || '-'} ~ ${params.endTime || '-'}`;
  });

  async function loadStores() {
    try {
      const res = await storeAll();
      const records = Array.isArray(res) ? res : res?.records || [];
      storeOptions.value = records.map((item: any) => ({ label: item.name || item.id, value: item.id }));
    } catch (e) {
      // 店铺下拉失败不阻塞统计（统计本身仍按可见域返回）
      console.warn('加载店铺列表失败', e);
    }
  }

  // ------------------------------------------------------------------
  // 总览
  // ------------------------------------------------------------------

  const overviewLoading = ref(false);
  const overview = ref<OrderStatsOverviewVO | null>(null);

  const countCards = computed(() => {
    const data = overview.value;
    return [
      { title: '订单总数', value: data?.totalOrders ?? 0 },
      { title: '待支付', value: data?.waitPayOrders ?? 0 },
      { title: '待发货', value: data?.waitShipOrders ?? 0 },
      { title: '已发货', value: data?.shippedOrders ?? 0 },
      { title: '已完成', value: data?.completedOrders ?? 0 },
      { title: '已取消', value: data?.cancelledOrders ?? 0 },
      { title: '已关闭', value: data?.closedOrders ?? 0 },
      { title: '已支付', value: data?.paidOrders ?? 0 },
    ];
  });

  const amountCards = computed(() => {
    const data = overview.value;
    return [
      { title: 'GMV', value: data?.gmv ?? 0, hint: '已支付订单应付金额合计，不扣退款' },
      { title: '实收', value: data?.paidAmount ?? 0, hint: '已支付订单实收合计' },
      { title: '退款', value: data?.refundAmount ?? 0, hint: '退款成功累计' },
      { title: '净收入', value: data?.netAmount ?? 0, hint: '实收 − 退款，这才是留在账上的钱' },
      { title: '客单价', value: data?.avgOrderAmount ?? 0, hint: '实收 / 已支付订单数' },
      { title: '优惠合计', value: data?.discountAmount ?? 0, hint: '促销 + 券 + 积分 + 会员' },
      { title: '运费合计', value: data?.freightAmount ?? 0, hint: '' },
      { title: '成本合计', value: data?.costAmount ?? 0, hint: '后台专属口径' },
      { title: '毛利', value: data?.grossProfit ?? 0, hint: '成交 − 优惠 − 成本，不扣退款' },
    ];
  });

  const rateCards = computed(() => {
    const data = overview.value;
    return [
      { title: '支付转化率', text: formatRate(data?.payConversionRate), hint: '已支付订单数 / 订单总数' },
      { title: '退款率', text: formatRate(data?.refundRate), hint: '按金额算，不按单数' },
      { title: '毛利率', text: formatRate(data?.grossProfitRate), hint: '毛利 / 成交金额' },
      { title: '加购用户数', text: `${data?.cartUsers ?? 0}`, hint: '区间内加购去重人数' },
      { title: '下单用户数', text: `${data?.orderUsers ?? 0}`, hint: '区间内下单去重人数' },
      { title: '加购转化率', text: formatRate(data?.cartConversionRate), hint: '下单用户数 / 加购用户数' },
    ];
  });

  const closedButPaid = computed(() => Number(overview.value?.closedButPaidOrders ?? 0));
  const pendingPayments = computed(() => Number(overview.value?.pendingPayments ?? 0));
  const pendingRefunds = computed(() => Number(overview.value?.pendingRefunds ?? 0));
  const hasRisk = computed(() => closedButPaid.value > 0 || pendingPayments.value > 0 || pendingRefunds.value > 0);

  async function loadOverview() {
    overviewLoading.value = true;
    try {
      overview.value = await statsOverview(buildQuery());
    } catch (error: any) {
      createMessage.error(error?.message || '获取订单总览失败');
    } finally {
      overviewLoading.value = false;
    }
  }

  // ------------------------------------------------------------------
  // 状态分布（饼图）
  // ------------------------------------------------------------------

  const statusRef = ref<HTMLDivElement>();
  const { setOptions: setStatusOptions } = useECharts(statusRef);
  const statusList = ref<OrderStatsStatusVO[]>([]);
  const statusLoading = ref(false);

  async function loadStatus() {
    statusLoading.value = true;
    try {
      statusList.value = (await statsStatus(buildQuery())) || [];
      renderStatus();
    } catch (error: any) {
      createMessage.error(error?.message || '获取订单状态分布失败');
    } finally {
      statusLoading.value = false;
    }
  }

  function renderStatus() {
    const rows = statusList.value;
    // 名称一律用后端下发的 statusLabel，前端只负责配色
    const data: any[] = rows.map((row) => ({
      name: row.statusLabel || row.status || '未知',
      value: Number(row.orderCount ?? 0),
      amount: Number(row.amount ?? 0),
    }));
    const option: EChartsOption = {
      tooltip: {
        trigger: 'item',
        formatter: (params: any) => {
          const amount = params?.data?.amount;
          const amountText = amount === undefined ? '' : `<br/>金额合计：¥${formatAmount(amount)}`;
          return `${params.name}<br/>订单数：${params.value}（${params.percent}%）${amountText}`;
        },
      },
      legend: { bottom: 0 },
      series: [
        {
          name: '订单状态分布',
          type: 'pie',
          radius: ['42%', '68%'],
          center: ['50%', '45%'],
          avoidLabelOverlap: true,
          label: { formatter: '{b}: {c}' },
          data: data as any,
        },
      ],
    };
    setStatusOptions(option);
  }

  // ------------------------------------------------------------------
  // 趋势（折线）
  // ------------------------------------------------------------------

  const trendRef = ref<HTMLDivElement>();
  const { setOptions: setTrendOptions } = useECharts(trendRef);
  const trendList = ref<OrderStatsTrendVO[]>([]);
  const trendLoading = ref(false);

  async function loadTrend() {
    trendLoading.value = true;
    try {
      trendList.value = (await statsTrend(buildQuery())) || [];
      renderTrend();
    } catch (error: any) {
      createMessage.error(error?.message || '获取订单趋势失败');
    } finally {
      trendLoading.value = false;
    }
  }

  function renderTrend() {
    const rows = trendList.value;
    const dates = rows.map((row) => row.date);
    const option: EChartsOption = {
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0 },
      grid: { left: 8, right: 16, top: 24, bottom: 56, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: dates },
      yAxis: [
        { type: 'value', name: '单量/件数', minInterval: 1 },
        { type: 'value', name: '金额(元)', position: 'right' },
      ],
      series: [
        {
          name: '下单数',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          data: rows.map((row) => Number(row.orderCount ?? 0)),
          itemStyle: { color: '#d97706' },
        },
        {
          name: '支付单数',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          data: rows.map((row) => Number(row.paidCount ?? 0)),
          itemStyle: { color: '#0f172a' },
        },
        {
          name: '商品件数',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          data: rows.map((row) => Number(row.quantity ?? 0)),
          itemStyle: { color: '#059669' },
        },
        {
          name: 'GMV',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          yAxisIndex: 1,
          data: rows.map((row) => Number(row.gmv ?? 0)),
          itemStyle: { color: '#1677ff' },
        },
        {
          name: '实收',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          yAxisIndex: 1,
          data: rows.map((row) => Number(row.paidAmount ?? 0)),
          itemStyle: { color: '#13c2c2' },
        },
        {
          name: '退款',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          yAxisIndex: 1,
          data: rows.map((row) => Number(row.refundAmount ?? 0)),
          itemStyle: { color: '#cf1322' },
        },
        {
          name: '净收入',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          yAxisIndex: 1,
          data: rows.map((row) => Number(row.netAmount ?? 0)),
          itemStyle: { color: '#722ed1' },
        },
      ],
    };
    setTrendOptions(option);
  }

  // ------------------------------------------------------------------
  // 排行
  // ------------------------------------------------------------------

  const rankLimit = ref(10);
  const categoryLimit = ref(10);
  const rankLimitOptions = [
    { label: 'Top 10', value: 10 },
    { label: 'Top 20', value: 20 },
    { label: 'Top 50', value: 50 },
  ];

  const rankLoading = ref(false);
  const categoryLoading = ref(false);
  const productRankList = ref<Recordable[]>([]);
  const categoryRankList = ref<Recordable[]>([]);

  const productRankColumns = [
    { title: '排名', dataIndex: 'rank', width: 70 },
    { title: '商品 / 规格', dataIndex: 'product' },
    { title: '销量(件)', dataIndex: 'quantity', width: 100 },
    { title: '订单数', dataIndex: 'orderCount', width: 100 },
    { title: '销售额', dataIndex: 'salesAmount', width: 130 },
    { title: '毛利', dataIndex: 'grossProfit', width: 130 },
  ];

  const categoryRankColumns = [
    { title: '排名', dataIndex: 'rank', width: 70 },
    { title: '分类', dataIndex: 'categoryName', customRender: ({ text }: any) => text || '（无分类）' },
    { title: '销量(件)', dataIndex: 'quantity', width: 110 },
    { title: '订单数', dataIndex: 'orderCount', width: 110 },
    { title: '销售额', dataIndex: 'salesAmount', width: 140 },
    { title: '毛利', dataIndex: 'grossProfit', width: 140 },
  ];

  /** row-key 用组合键：商品/SKU/分类都可能出现同 rank 的空值行 */
  function withRankKey(rows: OrderStatsRankVO[]): Recordable[] {
    return (rows || []).map((row, index) => ({
      ...row,
      rankKey: `${row.rank ?? index + 1}-${row.productId || ''}-${row.skuId || ''}-${row.categoryName || ''}`,
    }));
  }

  async function loadRank() {
    rankLoading.value = true;
    try {
      // 排行条数是独立的 limit 参数（不是分页 pageSize）
      const res = await statsRankProduct({ ...buildQuery(), limit: rankLimit.value });
      productRankList.value = withRankKey(res || []);
    } catch (error: any) {
      createMessage.error(error?.message || '获取热销商品排行失败');
    } finally {
      rankLoading.value = false;
    }
  }

  async function loadCategoryRank() {
    categoryLoading.value = true;
    try {
      const res = await statsRankCategory({ ...buildQuery(), limit: categoryLimit.value });
      categoryRankList.value = withRankKey(res || []);
    } catch (error: any) {
      createMessage.error(error?.message || '获取热销分类排行失败');
    } finally {
      categoryLoading.value = false;
    }
  }

  // ------------------------------------------------------------------
  // 运维任务
  // ------------------------------------------------------------------

  const batchSize = ref(200);
  const taskLoading = ref<'' | 'close' | 'compensate'>('');
  const taskResult = ref<{ name: string; data: OrderTaskResult } | null>(null);

  /**
   * 批量任务二次确认。
   *
   * <p>旧版两个按钮直接执行：batchSize 默认 200、最高 1000，误点一次就会批量改
   * 订单/支付状态，而且没有任何撤销入口。现在：
   *  1) 一律先确认，写清"扫描 N 条 / 影响范围 / 不可撤销"；
   *  2) 超过 50 条时要求**手动输入条数**才能确认，避免"连点两下就执行"。
   */
  function confirmTask(type: 'close' | 'compensate', size: number): Promise<void> {
    const label = type === 'close' ? '超时关单' : '支付补单';
    const impact = type === 'close'
      ? `将扫描最多 ${size} 笔已过支付截止时间的待支付订单，关单前会先向渠道确认真实支付结果。`
      : `将扫描最多 ${size} 笔结果未知的支付单，逐笔向渠道查询并把结果回写到订单。`;
    const needTyping = size > 50;
    const typed = ref('');

    return new Promise<void>((resolve, reject) => {
      createConfirm({
        iconType: 'warning',
        title: `确认执行「${label}」任务？`,
        okText: '确认执行',
        cancelText: '取消',
        content: h('div', [
          h('p', impact),
          h('p', { style: 'color: #cf1322; font-weight: 600' }, '任务会批量改变订单/支付状态，且不可撤销。'),
          needTyping
            ? h('div', { style: 'margin-top: 8px' }, [
                h('p', `本次影响条数超过 50，请手动输入「${size}」以确认：`),
                h(Input, {
                  value: typed.value,
                  placeholder: String(size),
                  'onUpdate:value': (value: any) => {
                    typed.value = String(value ?? '');
                  },
                }),
              ])
            : null,
        ]),
        onOk: () => {
          if (needTyping && typed.value.trim() !== String(size)) {
            createMessage.warning(`请输入 ${size} 以确认执行`);
            // 返回 reject 让确认框保持打开
            return Promise.reject(new Error('confirm-mismatch'));
          }
          resolve();
          return undefined;
        },
        onCancel: () => reject(new Error('cancelled')),
      });
    });
  }

  async function runTask(type: 'close' | 'compensate') {
    if (!canManageAllStores.value) {
      createMessage.warning('仅总部/平台管理员可手动触发运维任务');
      return;
    }
    const size = Number(batchSize.value);
    if (!Number.isFinite(size) || size < 1) {
      createMessage.warning('请填写有效的单批处理条数');
      return;
    }
    try {
      await confirmTask(type, size);
    } catch {
      return;
    }
    taskLoading.value = type;
    try {
      const data = type === 'close' ? await runCloseTimeoutTask(size) : await runCompensateTask(size);
      taskResult.value = {
        name: type === 'close' ? '超时关单任务' : '支付补单任务',
        data: data || {},
      };
      const failed = Number(data?.failed ?? 0);
      if (failed > 0) {
        createMessage.warning(`任务完成，但有 ${failed} 条处理失败，请查看说明`);
      } else {
        createMessage.success(data?.message || '任务已执行完成');
      }
      // 任务会改变订单与支付状态，执行完必须刷新统计
      reloadAll();
    } finally {
      taskLoading.value = '';
    }
  }

  // ------------------------------------------------------------------
  // 统一刷新
  // ------------------------------------------------------------------

  function reloadAll() {
    loadOverview();
    loadStatus();
    loadTrend();
    loadRank();
    loadCategoryRank();
  }

  onMounted(() => {
    // 店铺下拉与统计互不依赖：并行发起。
    // 以前是 await loadStores() 之后才 reloadAll()，店铺接口慢时整页统计跟着白屏。
    void loadStores();
    reloadAll();
  });
</script>

<style lang="less" scoped>
  .risk-card {
    border: 1px solid #f0f0f0;

    .risk-title {
      font-weight: 600;
    }
  }

  .risk-card--danger {
    border-color: #ffa39e;
    background: #fff1f0;
  }

  .risk-item--danger {
    background: #fff1f0;
    border: 1px solid #ffa39e;
  }

  .risk-item--warn {
    background: #fffbe6;
    border: 1px solid #ffe58f;
  }

  .risk-hint {
    margin: 6px 0 0;
    font-size: 12px;
    line-height: 1.4;
    color: rgba(0, 0, 0, 0.45);
  }
</style>
