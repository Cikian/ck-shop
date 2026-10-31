<template>
  <div class="big-screen">
    <!-- 头部：标题 + 刷新 + 视角提示 -->
    <div class="bs-header">
      <div class="flex items-center gap-2">
        <span class="bs-title">用户行为 · 实时大屏</span>
        <a-badge status="processing" text="实时" />
        <a-tag v-if="canViewAll" color="gold">全局汇总视角</a-tag>
        <a-tag v-else color="blue">可见店铺范围</a-tag>
      </div>
      <a-space>
        <span class="text-xs text-gray-400">最近刷新：{{ lastRefreshText }}</span>
        <a-button size="small" :loading="loading" @click="refreshAll">刷新</a-button>
      </a-space>
    </div>

    <!-- 顶部 7 个 KPI（压缩高度） -->
    <div class="bs-kpis">
      <div v-for="card in kpiCards" :key="card.title" class="bs-kpi">
        <div class="bs-kpi-title">
          {{ card.title }}
          <span v-if="card.approx" class="approx-tag">近似</span>
        </div>
        <div class="bs-kpi-value">{{ card.text }}</div>
      </div>
    </div>

    <!-- 面板网格 -->
    <div class="bs-grid">
      <a-card :bordered="false" size="small" class="bs-panel bs-trend" title="今日趋势（按小时）">
        <div ref="trendRef" class="bs-chart"></div>
        <div v-if="!trendList.length" class="chart-empty"><a-empty description="暂无趋势数据" /></div>
      </a-card>

      <a-card :bordered="false" size="small" class="bs-panel bs-funnel" title="转化漏斗">
        <div ref="funnelRef" class="bs-chart"></div>
      </a-card>

      <a-card :bordered="false" size="small" class="bs-panel bs-region" title="地域 Top（国家）">
        <div ref="regionRef" class="bs-chart"></div>
        <div v-if="!regionList.length" class="chart-empty"><a-empty description="暂无地域数据" /></div>
      </a-card>

      <a-card :bordered="false" size="small" class="bs-panel bs-rank" title="商品 Top（访问量）">
        <div class="rank-list">
          <div v-for="(item, index) in rankList" :key="item.productId" class="rank-item">
            <span class="rank-no" :class="{ 'top3': index < 3 }">{{ index + 1 }}</span>
            <span class="rank-name" :title="item.productTitle || item.productId">{{ item.productTitle || item.productId || '未知商品' }}</span>
            <span class="rank-val">{{ formatNumber(item.viewCount) }}</span>
          </div>
        </div>
        <div v-if="!rankList.length" class="chart-empty"><a-empty description="暂无商品数据" /></div>
      </a-card>

      <a-card :bordered="false" size="small" class="bs-panel bs-device" title="设备分布">
        <div ref="deviceRef" class="bs-chart"></div>
        <div v-if="!deviceList.length" class="chart-empty"><a-empty description="暂无设备数据" /></div>
      </a-card>
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { computed, onMounted, onUnmounted, ref } from 'vue';
  import dayjs from 'dayjs';
  import type { EChartsOption } from 'echarts';
  import { useECharts } from '/@/hooks/web/useECharts';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { usePermission } from '/@/hooks/web/usePermission';
  import {
    getDevice,
    getFunnel,
    getRankProduct,
    getRealtimeOverview,
    getRegion,
    getTrend,
    type AnalyticsBaseQuery,
    type FunnelVO,
    type ProductRankVO,
    type RealtimeOverviewVO,
    type TrendPointVO,
  } from '/@/api/ck/shopAnalytics';
  import { ANALYTICS_TIME_FORMAT, formatAmount, formatNumber, formatRate, normalizeFunnel, regionLabel } from './format';

  const { createMessage } = useMessage();
  const { hasPermission } = usePermission();

  /** 超管具备全局汇总视角（契约 §6.2）；后端据此绕过 sys_org_code 范围 */
  const canViewAll = computed(() => hasPermission('shop-analytics:all'));

  const loading = ref(false);
  const overview = ref<RealtimeOverviewVO | null>(null);
  const lastRefreshText = ref(dayjs().format('HH:mm:ss'));

  const trendList = ref<TrendPointVO[]>([]);
  const funnel = ref<FunnelVO | null>(null);
  const regionList = ref<Recordable[]>([]);
  const rankList = ref<ProductRankVO[]>([]);
  const deviceList = ref<Recordable[]>([]);

  // ------------------------------------------------------------------
  // KPI（7 个，压缩高度；UV 标「近似」）
  // ------------------------------------------------------------------

  const kpiCards = computed(() => {
    const d = overview.value;
    return [
      { title: '在线人数', text: formatNumber(d?.onlineCount), approx: false },
      { title: '今日 PV', text: formatNumber(d?.todayPv), approx: false },
      { title: '今日 UV', text: formatNumber(d?.todayUv), approx: true },
      { title: '今日 IP', text: formatNumber(d?.todayIp), approx: false },
      { title: '今日访客', text: formatNumber(d?.todayVisit), approx: false },
      { title: '今日订单', text: formatNumber(d?.todayOrderCount), approx: false },
      { title: '今日实收', text: `¥${formatAmount(d?.todayPaidAmount)}`, approx: false },
    ];
  });

  /** 今日累计口径（大屏各面板统一用「今日」） */
  function todayQuery(): AnalyticsBaseQuery {
    return {
      startTime: dayjs().startOf('day').format(ANALYTICS_TIME_FORMAT),
      endTime: dayjs().endOf('day').format(ANALYTICS_TIME_FORMAT),
    };
  }

  // ------------------------------------------------------------------
  // 实时总览
  // ------------------------------------------------------------------

  async function loadOverview() {
    loading.value = true;
    try {
      overview.value = await getRealtimeOverview();
      lastRefreshText.value = dayjs().format('HH:mm:ss');
    } catch (error: any) {
      createMessage.error(error?.message || '获取实时大盘失败');
    } finally {
      loading.value = false;
    }
  }

  // ------------------------------------------------------------------
  // 趋势 sparkline
  // ------------------------------------------------------------------

  const trendRef = ref<HTMLDivElement>();
  const { setOptions: setTrendOptions } = useECharts(trendRef);

  async function loadTrend() {
    try {
      trendList.value = (await getTrend({ ...todayQuery(), granularity: 'hour' })) || [];
      renderTrend();
    } catch (error: any) {
      createMessage.error(error?.message || '获取行为趋势失败');
    }
  }

  function renderTrend() {
    const rows = trendList.value;
    // 单点/两点时开启 boundaryGap，把点居中，避免被压到左边缘几乎看不见
    const sparse = rows.length < 3;
    const option: EChartsOption = {
      tooltip: { trigger: 'axis' },
      legend: { show: true, top: 0, textStyle: { fontSize: 11 } },
      grid: { left: 8, right: 8, top: 28, bottom: 8, containLabel: true },
      xAxis: { type: 'category', boundaryGap: sparse ? true : false, data: rows.map((row) => row.time) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: 'PV', type: 'line', smooth: true, showSymbol: true, symbolSize: 5, data: rows.map((row) => Number(row.pv ?? 0)), itemStyle: { color: '#d97706' } },
        { name: 'UV', type: 'line', smooth: true, showSymbol: true, symbolSize: 5, data: rows.map((row) => Number(row.uv ?? 0)), itemStyle: { color: '#0f172a' } },
        { name: '下单', type: 'line', smooth: true, showSymbol: true, symbolSize: 5, data: rows.map((row) => Number(row.orderCount ?? 0)), itemStyle: { color: '#722ed1' } },
        { name: '支付', type: 'line', smooth: true, showSymbol: true, symbolSize: 5, data: rows.map((row) => Number(row.paidCount ?? 0)), itemStyle: { color: '#13c2c2' } },
      ],
    };
    setTrendOptions(option);
  }

  // ------------------------------------------------------------------
  // 转化漏斗（始终渲染 4 步，缺步补 0）
  // ------------------------------------------------------------------

  const funnelRef = ref<HTMLDivElement>();
  const { setOptions: setFunnelOptions } = useECharts(funnelRef);

  async function loadFunnel() {
    try {
      funnel.value = await getFunnel(todayQuery());
      renderFunnel();
    } catch (error: any) {
      createMessage.error(error?.message || '获取转化漏斗失败');
    }
  }

  function renderFunnel() {
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
          left: '6%',
          top: 12,
          bottom: 40,
          width: '88%',
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
  // 地域 Top（国家）
  // ------------------------------------------------------------------

  const regionRef = ref<HTMLDivElement>();
  const { setOptions: setRegionOptions } = useECharts(regionRef);

  async function loadRegion() {
    try {
      const res = await getRegion({ ...todayQuery(), level: 'country' });
      regionList.value = (res || []).map((row, index) => ({ ...row, _key: `r-${index}` }));
      renderRegion();
    } catch (error: any) {
      createMessage.error(error?.message || '获取地域分布失败');
    }
  }

  function renderRegion() {
    const rows = regionList.value.slice(0, 10);
    const option: EChartsOption = {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 8, right: 20, top: 8, bottom: 8, containLabel: true },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', inverse: true, data: rows.map((row) => regionLabel(row)) },
      series: [
        {
          type: 'bar',
          barMaxWidth: 16,
          data: rows.map((row) => Number(row.pv ?? 0)),
          itemStyle: { color: 'var(--shop-primary)' },
        },
      ],
    };
    setRegionOptions(option);
  }

  // ------------------------------------------------------------------
  // 商品 Top（访问量）
  // ------------------------------------------------------------------

  async function loadRank() {
    try {
      rankList.value = (await getRankProduct({ ...todayQuery(), limit: 8 })) || [];
    } catch (error: any) {
      createMessage.error(error?.message || '获取商品排行失败');
    }
  }

  // ------------------------------------------------------------------
  // 设备分布
  // ------------------------------------------------------------------

  const deviceRef = ref<HTMLDivElement>();
  const { setOptions: setDeviceOptions } = useECharts(deviceRef);

  async function loadDevice() {
    try {
      const res = await getDevice(todayQuery());
      deviceList.value = (res || []).map((row, index) => ({ ...row, _key: `d-${index}` }));
      renderDevice();
    } catch (error: any) {
      createMessage.error(error?.message || '获取设备分布失败');
    }
  }

  function renderDevice() {
    const map = new Map<string, number>();
    deviceList.value.forEach((row) => {
      const name = row.deviceType || row.os || row.browser || row.lang || '未知';
      map.set(name, (map.get(name) || 0) + Number(row.pv ?? 0));
    });
    const data = Array.from(map.entries()).map(([name, value]) => ({ name, value }));
    const option: EChartsOption = {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['38%', '66%'],
          center: ['50%', '45%'],
          avoidLabelOverlap: true,
          label: { formatter: '{b}: {c}' },
          data,
        },
      ],
    };
    setDeviceOptions(option);
  }

  // ------------------------------------------------------------------
  // 刷新策略：总览 30s；面板 5min（契约 §6.1）
  // ------------------------------------------------------------------

  function loadPanels() {
    void Promise.allSettled([loadTrend(), loadFunnel(), loadRegion(), loadRank(), loadDevice()]);
  }

  function refreshAll() {
    loadOverview();
    loadPanels();
  }

  let overviewTimer: number | undefined;
  let panelsTimer: number | undefined;

  onMounted(() => {
    loadOverview();
    loadPanels();
    overviewTimer = window.setInterval(loadOverview, 30_000);
    panelsTimer = window.setInterval(loadPanels, 300_000);
  });

  onUnmounted(() => {
    if (overviewTimer !== undefined) {
      window.clearInterval(overviewTimer);
      overviewTimer = undefined;
    }
    if (panelsTimer !== undefined) {
      window.clearInterval(panelsTimer);
      panelsTimer = undefined;
    }
  });
</script>

<style lang="less" scoped>
  .big-screen {
    height: calc(100vh - 120px);
    min-height: 620px;
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 12px;
    overflow: auto;
  }

  .bs-header {
    flex: none;
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px;
    padding: 8px 12px;
    background: #fff;
    border: 1px solid var(--shop-border, #f0f0f0);
    border-radius: 8px;
  }

  .bs-title {
    font-size: 15px;
    font-weight: 600;
  }

  .bs-kpis {
    flex: none;
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 12px;
  }

  .bs-kpi {
    padding: 8px 12px;
    background: #fff;
    border: 1px solid var(--shop-border, #f0f0f0);
    border-radius: 8px;
  }

  .bs-kpi-title {
    font-size: 12px;
    color: var(--shop-text-secondary, #8c8c8c);
  }

  .bs-kpi-value {
    margin-top: 4px;
    font-size: 22px;
    font-weight: 700;
    line-height: 1.2;
    font-variant-numeric: tabular-nums;
  }

  .approx-tag {
    display: inline-block;
    margin-left: 4px;
    padding: 0 4px;
    font-size: 11px;
    line-height: 16px;
    color: #d46b08;
    background: var(--shop-warning-soft, #fff7e6);
    border-radius: 3px;
  }

  .bs-grid {
    flex: 1;
    min-height: 0;
    display: grid;
    grid-template-columns: repeat(12, minmax(0, 1fr));
    grid-template-rows: repeat(2, minmax(260px, 1fr));
    gap: 12px;
  }

  .bs-panel {
    margin: 0;
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow: hidden;

    :deep(.ant-card-head) {
      min-height: 38px;
      padding: 0 12px;
    }

    :deep(.ant-card-body) {
      position: relative;
      flex: 1;
      min-height: 0;
      padding: 12px;
    }
  }

  .bs-trend {
    grid-column: span 7;
  }

  .bs-funnel {
    grid-column: span 5;
  }

  .bs-region {
    grid-column: span 4;
  }

  .bs-rank {
    grid-column: span 4;
  }

  .bs-device {
    grid-column: span 4;
  }

  .bs-chart {
    position: absolute;
    inset: 12px;
  }

  .rank-list {
    position: absolute;
    inset: 12px;
    overflow: auto;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .rank-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
  }

  .rank-no {
    flex: none;
    width: 20px;
    height: 20px;
    line-height: 20px;
    text-align: center;
    border-radius: 4px;
    background: #f0f0f0;
    color: #595959;
  }

  .rank-no.top3 {
    background: #fff7e6;
    color: #d46b08;
    font-weight: 600;
  }

  .rank-name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .rank-val {
    flex: none;
    font-variant-numeric: tabular-nums;
    color: var(--shop-text-secondary, #8c8c8c);
  }

  .chart-empty {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  @media (max-width: 1199px) {
    .big-screen {
      height: auto;
      min-height: 100vh;
    }

    .bs-kpis {
      grid-template-columns: repeat(4, 1fr);
    }

    .bs-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
      grid-template-rows: none;
      grid-auto-rows: 300px;
    }

    .bs-trend,
    .bs-funnel,
    .bs-region,
    .bs-rank,
    .bs-device {
      grid-column: span 1;
    }
  }

  @media (max-width: 767px) {
    .bs-kpis {
      grid-template-columns: repeat(2, 1fr);
    }

    .bs-grid {
      grid-template-columns: 1fr;
      grid-auto-rows: 280px;
    }
  }
</style>
