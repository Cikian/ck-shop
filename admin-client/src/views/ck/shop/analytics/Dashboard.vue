<template>
  <div class="p-4">
    <!-- 筛选条件 -->
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

        <a-form-item label="趋势粒度">
          <a-select v-model:value="granularity" style="width: 110px" :options="granularityOptions" @change="loadTrend" />
        </a-form-item>

        <a-form-item>
          <a-button type="primary" :loading="loading" @click="reloadAll">刷新</a-button>
        </a-form-item>
      </a-form>

      <p class="mt-2 text-xs text-gray-400">
        统计区间：{{ rangeText }}　|　口径：漏斗各步（加购 / 下单 / 支付）从业务流水回填，与订单统计 GMV 口径一致；UV 在 hour 粒度为近似值。
      </p>
    </a-card>

    <!-- 趋势 -->
    <a-card :bordered="false" size="small" title="行为趋势" class="mb-4">
      <div ref="trendRef" style="height: 340px; width: 100%"></div>
      <a-empty v-if="!trendLoading && !trendList.length" description="该区间没有趋势数据" />
    </a-card>

    <!-- 转化漏斗 -->
    <a-card :bordered="false" size="small" title="转化漏斗" class="mb-4">
      <div ref="funnelRef" style="height: 340px; width: 100%"></div>
    </a-card>

    <!-- 商品排行 -->
    <a-card :bordered="false" size="small" title="商品排行（访问 / 加购 / 转化）">
      <template #extra>
        <a-select v-model:value="rankLimit" style="width: 110px" :options="rankLimitOptions" @change="loadRank" />
      </template>
      <a-table
        :columns="rankColumns"
        :data-source="rankList"
        :loading="rankLoading"
        :pagination="false"
        row-key="rankKey"
        size="small"
        :scroll="{ x: 1100 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'rank'">
            <a-tag :color="record.rank <= 3 ? 'orange' : 'default'">{{ record.rank }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'product'">
            <div class="truncate" style="max-width: 320px">{{ record.productTitle || record.productId || '（商品已删除）' }}</div>
          </template>
          <template v-else-if="column.dataIndex === 'avgDurationMs'">{{ formatDuration(record.avgDurationMs) }}</template>
          <template v-else-if="column.dataIndex === 'clickRate'">{{ formatRate(record.clickRate) }}</template>
          <template v-else-if="column.dataIndex === 'addCartRate'">{{ formatRate(record.addCartRate) }}</template>
          <template v-else-if="column.dataIndex === 'conversionRate'">{{ formatRate(record.conversionRate) }}</template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script lang="ts" setup>
  import { computed, onMounted, ref } from 'vue';
  import dayjs, { Dayjs } from 'dayjs';
  import type { EChartsOption } from 'echarts';
  import { useECharts } from '/@/hooks/web/useECharts';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { storeAll } from '../order/ShopOrder.api';
  import {
    getFunnel,
    getRankProduct,
    getTrend,
    type AnalyticsBaseQuery,
    type FunnelVO,
    type ProductRankVO,
    type TrendPointVO,
  } from '/@/api/ck/shopAnalytics';
  import { ANALYTICS_TIME_FORMAT, formatDuration, formatRate, normalizeFunnel } from './format';

  const { createMessage } = useMessage();

  type RangePreset = '1' | '7' | '30' | '90' | 'custom';

  const rangePreset = ref<RangePreset>('30');
  const customRange = ref<[Dayjs, Dayjs]>([dayjs().subtract(29, 'day'), dayjs()]);
  const query = ref<{ storeId?: string }>({ storeId: undefined });
  const granularity = ref<'hour' | 'day'>('day');
  const granularityOptions = [
    { label: '按日', value: 'day' },
    { label: '按小时', value: 'hour' },
  ];

  const storeOptions = ref<any[]>([]);
  const loading = ref(false);

  function buildBaseQuery(): AnalyticsBaseQuery {
    if (rangePreset.value === 'custom') {
      const [start, end] = customRange.value || [];
      return {
        storeId: query.value.storeId,
        startTime: start ? dayjs(start).startOf('day').format(ANALYTICS_TIME_FORMAT) : undefined,
        endTime: end ? dayjs(end).endOf('day').format(ANALYTICS_TIME_FORMAT) : undefined,
      };
    }
    const days = Number(rangePreset.value);
    return {
      storeId: query.value.storeId,
      startTime: dayjs()
        .subtract(days - 1, 'day')
        .startOf('day')
        .format(ANALYTICS_TIME_FORMAT),
      endTime: dayjs().endOf('day').format(ANALYTICS_TIME_FORMAT),
    };
  }

  const rangeText = computed(() => {
    const params = buildBaseQuery();
    return `${params.startTime || '-'} ~ ${params.endTime || '-'}`;
  });

  async function loadStores() {
    try {
      const res = await storeAll();
      const records = Array.isArray(res) ? res : res?.records || [];
      storeOptions.value = records.map((item: any) => ({ label: item.name || item.id, value: item.id }));
    } catch (e) {
      console.warn('加载店铺列表失败', e);
    }
  }

  // ------------------------------------------------------------------
  // 趋势
  // ------------------------------------------------------------------

  const trendRef = ref<HTMLDivElement>();
  const { setOptions: setTrendOptions } = useECharts(trendRef);
  const trendList = ref<TrendPointVO[]>([]);
  const trendLoading = ref(false);

  async function loadTrend() {
    trendLoading.value = true;
    try {
      trendList.value = (await getTrend({ ...buildBaseQuery(), granularity: granularity.value })) || [];
      renderTrend();
    } catch (error: any) {
      createMessage.error(error?.message || '获取行为趋势失败');
    } finally {
      trendLoading.value = false;
    }
  }

  function renderTrend() {
    const rows = trendList.value;
    // 单点/两点时开启 boundaryGap 把点居中，避免被压到左边缘几乎看不见
    const sparse = rows.length < 3;
    const option: EChartsOption = {
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0 },
      grid: { left: 8, right: 16, top: 24, bottom: 56, containLabel: true },
      xAxis: { type: 'category', boundaryGap: sparse ? true : false, data: rows.map((row) => row.time) },
      yAxis: [
        { type: 'value', name: '次数', minInterval: 1 },
        { type: 'value', name: '停留(ms)', position: 'right' },
      ],
      series: [
        { name: 'PV', type: 'line', smooth: true, showSymbol: true, symbolSize: 5, data: rows.map((row) => Number(row.pv ?? 0)), itemStyle: { color: '#d97706' } },
        { name: 'UV', type: 'line', smooth: true, showSymbol: true, symbolSize: 5, data: rows.map((row) => Number(row.uv ?? 0)), itemStyle: { color: '#0f172a' } },
        { name: '访客', type: 'line', smooth: true, showSymbol: true, symbolSize: 5, data: rows.map((row) => Number(row.visit ?? 0)), itemStyle: { color: '#059669' } },
        { name: '加购', type: 'line', smooth: true, showSymbol: true, symbolSize: 5, data: rows.map((row) => Number(row.addCartCount ?? 0)), itemStyle: { color: '#1677ff' } },
        { name: '下单', type: 'line', smooth: true, showSymbol: true, symbolSize: 5, data: rows.map((row) => Number(row.orderCount ?? 0)), itemStyle: { color: '#722ed1' } },
        { name: '支付', type: 'line', smooth: true, showSymbol: true, symbolSize: 5, data: rows.map((row) => Number(row.paidCount ?? 0)), itemStyle: { color: '#13c2c2' } },
        {
          name: '平均停留',
          type: 'line',
          smooth: true,
          showSymbol: true,
          symbolSize: 5,
          yAxisIndex: 1,
          data: rows.map((row) => Number(row.avgDurationMs ?? 0)),
          itemStyle: { color: 'var(--shop-error)' },
        },
      ],
    };
    setTrendOptions(option);
  }

  // ------------------------------------------------------------------
  // 漏斗
  // ------------------------------------------------------------------

  const funnelRef = ref<HTMLDivElement>();
  const { setOptions: setFunnelOptions } = useECharts(funnelRef);
  const funnel = ref<FunnelVO | null>(null);
  const funnelLoading = ref(false);

  async function loadFunnel() {
    funnelLoading.value = true;
    try {
      funnel.value = await getFunnel(buildBaseQuery());
      renderFunnel();
    } catch (error: any) {
      createMessage.error(error?.message || '获取转化漏斗失败');
    } finally {
      funnelLoading.value = false;
    }
  }

  function renderFunnel() {
    // 始终渲染固定 4 步（访问/加购/下单/支付），无数据时值为 0、转化率 0%
    const data = normalizeFunnel(funnel.value?.steps);
    const option: EChartsOption = {
      tooltip: {
        trigger: 'item',
        formatter: (params: any) => `${params.name}<br/>数量：${params.value}<br/>转化率：${formatRate(params.data?.rate)}`,
      },
      legend: { bottom: 0 },
      series: [
        {
          name: '转化漏斗',
          type: 'funnel',
          left: '8%',
          top: 16,
          bottom: 48,
          width: '84%',
          minSize: '15%',
          maxSize: '100%',
          sort: 'descending',
          gap: 2,
          label: { show: true, position: 'inside', formatter: '{b}: {c}' },
          itemStyle: { borderWidth: 1, borderColor: '#fff' },
          data,
        },
      ],
    };
    setFunnelOptions(option);
  }

  // ------------------------------------------------------------------
  // 商品排行
  // ------------------------------------------------------------------

  const rankLimit = ref(10);
  const rankLimitOptions = [
    { label: 'Top 10', value: 10 },
    { label: 'Top 20', value: 20 },
    { label: 'Top 50', value: 50 },
  ];

  const rankLoading = ref(false);
  const rankList = ref<Recordable[]>([]);

  const rankColumns = [
    { title: '排名', dataIndex: 'rank', width: 70 },
    { title: '商品', dataIndex: 'product' },
    { title: '访问量', dataIndex: 'viewCount', width: 90 },
    { title: '访客数', dataIndex: 'visitCount', width: 90 },
    { title: '平均停留', dataIndex: 'avgDurationMs', width: 110 },
    { title: '加购数', dataIndex: 'addCartCount', width: 90 },
    { title: '下单数', dataIndex: 'orderCount', width: 90 },
    { title: '支付数', dataIndex: 'paidCount', width: 90 },
    { title: '点击率', dataIndex: 'clickRate', width: 90 },
    { title: '加购率', dataIndex: 'addCartRate', width: 90 },
    { title: '转化率', dataIndex: 'conversionRate', width: 90 },
  ];

  function withRankKey(rows: ProductRankVO[]): Recordable[] {
    return (rows || []).map((row, index) => ({
      ...row,
      rank: index + 1,
      rankKey: `${index + 1}-${row.productId}`,
    }));
  }

  async function loadRank() {
    rankLoading.value = true;
    try {
      const res = await getRankProduct({ ...buildBaseQuery(), limit: rankLimit.value });
      rankList.value = withRankKey(res || []);
    } catch (error: any) {
      createMessage.error(error?.message || '获取商品排行失败');
    } finally {
      rankLoading.value = false;
    }
  }

  // ------------------------------------------------------------------
  // 统一刷新
  // ------------------------------------------------------------------

  function reloadAll() {
    loading.value = true;
    // 三块相互独立，串行/并行均可；这里并行发起，任一失败不阻塞其它
    Promise.allSettled([loadTrend(), loadFunnel(), loadRank()]).finally(() => {
      loading.value = false;
    });
  }

  onMounted(() => {
    void loadStores();
    reloadAll();
  });
</script>
