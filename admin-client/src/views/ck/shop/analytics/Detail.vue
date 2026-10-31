<template>
  <div class="p-4">
    <!-- 筛选条件（各分布 Tab 共用） -->
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
            @change="onFilterChange"
          />
        </a-form-item>

        <a-form-item label="统计区间">
          <a-radio-group v-model:value="rangePreset" button-style="solid" @change="onFilterChange">
            <a-radio-button value="1">今日</a-radio-button>
            <a-radio-button value="7">近 7 天</a-radio-button>
            <a-radio-button value="30">近 30 天</a-radio-button>
            <a-radio-button value="90">近 90 天</a-radio-button>
            <a-radio-button value="custom">自定义</a-radio-button>
          </a-radio-group>
        </a-form-item>

        <a-form-item v-if="rangePreset === 'custom'" label="日期">
          <a-range-picker v-model:value="customRange" :allow-clear="false" @change="onFilterChange" />
        </a-form-item>

        <a-form-item>
          <a-button type="primary" @click="onFilterChange">刷新</a-button>
        </a-form-item>
      </a-form>

      <p class="mt-2 text-xs text-gray-400">
        统计区间：{{ rangeText }}　|　分布类接口按 summary 聚合（hour/day），明细走 ck_shop_track_event 分页；IP 为原始值（本期不脱敏）。
      </p>
    </a-card>

    <a-card :bordered="false">
      <a-tabs v-model:activeKey="activeTab" @change="onTabChange">
        <!-- ============ 地域分布 ============ -->
        <a-tab-pane key="region" tab="地域分布">
          <a-space class="mb-3">
            <span>下钻级别：</span>
            <a-select v-model:value="regionLevel" style="width: 120px" :options="regionLevelOptions" @change="loadRegion" />
          </a-space>
          <a-row :gutter="[16, 16]">
            <a-col :xs="24" :xl="10">
              <div class="chart-wrap">
                <div ref="regionChartRef" style="height: 320px; width: 100%"></div>
                <div v-if="!regionLoading && !regionList.length" class="chart-empty">
                  <a-empty description="暂无地域数据" />
                </div>
              </div>
            </a-col>
            <a-col :xs="24" :xl="14">
              <a-table :columns="regionColumns" :data-source="regionList" :loading="regionLoading" :pagination="false" row-key="_key" size="small" :scroll="{ x: 640 }" />
            </a-col>
          </a-row>
        </a-tab-pane>

        <!-- ============ 来源分布 ============ -->
        <a-tab-pane key="source" tab="来源分布">
          <a-row :gutter="[16, 16]">
            <a-col :xs="24" :xl="10">
              <div ref="sourceChartRef" style="height: 320px; width: 100%"></div>
              <a-empty v-if="!sourceLoading && !sourceList.length" description="该区间没有来源数据" />
            </a-col>
            <a-col :xs="24" :xl="14">
              <a-table :columns="sourceColumns" :data-source="sourceList" :loading="sourceLoading" :pagination="false" row-key="_key" size="small" />
            </a-col>
          </a-row>
        </a-tab-pane>

        <!-- ============ 设备分布 ============ -->
        <a-tab-pane key="device" tab="设备分布">
          <a-row :gutter="[16, 16]">
            <a-col :xs="24" :xl="10">
              <div ref="deviceChartRef" style="height: 320px; width: 100%"></div>
              <a-empty v-if="!deviceLoading && !deviceList.length" description="该区间没有设备数据" />
            </a-col>
            <a-col :xs="24" :xl="14">
              <a-table :columns="deviceColumns" :data-source="deviceList" :loading="deviceLoading" :pagination="false" row-key="_key" size="small" :scroll="{ x: 560 }" />
            </a-col>
          </a-row>
        </a-tab-pane>

        <!-- ============ 页面排行 ============ -->
        <a-tab-pane key="page" tab="页面排行">
          <a-table
            :columns="pageColumns"
            :data-source="pageList"
            :loading="pageLoading"
            :pagination="false"
            row-key="_key"
            size="small"
            :scroll="{ x: 720 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'avgDurationMs'">{{ formatDuration(record.avgDurationMs) }}</template>
              <template v-else-if="column.dataIndex === 'bounceRate'">{{ formatRate(record.bounceRate) }}</template>
            </template>
          </a-table>
          <a-empty v-if="!pageLoading && !pageList.length" description="该区间没有页面数据" />
        </a-tab-pane>

        <!-- ============ 事件明细 ============ -->
        <a-tab-pane key="events" tab="事件明细">
          <a-space wrap class="mb-3">
            <a-select
              v-model:value="eventsFilter.eventName"
              :options="EVENT_OPTIONS"
              placeholder="全部事件"
              style="width: 160px"
              allow-clear
              @change="reloadEvents"
            />
            <a-input
              v-model:value="eventsFilter.keyword"
              placeholder="访客 / 用户 / 会话 / 页面路径"
              style="width: 240px"
              allow-clear
              @press-enter="reloadEvents"
            />
            <a-button type="primary" :loading="eventsLoading" @click="reloadEvents">查询</a-button>
            <a-button v-auth="'shop-analytics:export'" :loading="exportLoading" @click="exportEvents">导出 CSV</a-button>
          </a-space>

          <a-table
            :columns="eventsColumns"
            :data-source="eventsPage.records"
            :loading="eventsLoading"
            :pagination="eventsPagination"
            row-key="id"
            size="small"
            :scroll="{ x: 1600 }"
            @change="onEventsTableChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'eventName'">
                <a-tag>{{ EVENT_LABELS[record.eventName] || record.eventName }}</a-tag>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script lang="ts" setup>
  import { nextTick, onMounted, reactive, ref } from 'vue';
  import dayjs, { Dayjs } from 'dayjs';
  import type { EChartsOption } from 'echarts';
  import { useECharts } from '/@/hooks/web/useECharts';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { storeAll } from '../order/ShopOrder.api';
  import {
    getDevice,
    getEvents,
    getPage,
    getRegion,
    getSource,
    type AnalyticsBaseQuery,
    type AnalyticsPageResult,
    type EventsQuery,
    type TrackEventVO,
  } from '/@/api/ck/shopAnalytics';
  import { ANALYTICS_TIME_FORMAT, formatDuration, formatRate, regionLabel } from './format';

  const { createMessage } = useMessage();

  // ------------------------------------------------------------------
  // 事件名（契约 §4.1 受控枚举；仅前端展示与筛选，不做业务判断）
  // ------------------------------------------------------------------

  const EVENT_OPTIONS = [
    { label: '页面浏览', value: 'page_view' },
    { label: '商品浏览', value: 'product_view' },
    { label: '离开页面', value: 'page_leave' },
    { label: '商品曝光', value: 'product_expose' },
    { label: '商品点击', value: 'product_click' },
    { label: '搜索', value: 'search' },
    { label: '加购', value: 'add_to_cart' },
    { label: '下单', value: 'order_create' },
    { label: '支付', value: 'order_paid' },
  ];
  const EVENT_LABELS: Record<string, string> = Object.fromEntries(EVENT_OPTIONS.map((o) => [o.value, o.label]));

  // ------------------------------------------------------------------
  // 查询条件
  // ------------------------------------------------------------------

  type RangePreset = '1' | '7' | '30' | '90' | 'custom';

  const rangePreset = ref<RangePreset>('30');
  const customRange = ref<[Dayjs, Dayjs]>([dayjs().subtract(29, 'day'), dayjs()]);
  const query = ref<{ storeId?: string }>({ storeId: undefined });

  const storeOptions = ref<any[]>([]);

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

  const rangeText = ref('');
  function updateRangeText() {
    const params = buildBaseQuery();
    rangeText.value = `${params.startTime || '-'} ~ ${params.endTime || '-'}`;
  }

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
  // Tab 切换：懒加载，图表只在激活后渲染（避免 display:none 下 echarts 0 高度）
  // ------------------------------------------------------------------

  const activeTab = ref('region');

  function onTabChange() {
    nextTick(() => reloadActive());
  }

  function onFilterChange() {
    updateRangeText();
    reloadActive();
  }

  function reloadActive() {
    switch (activeTab.value) {
      case 'region':
        loadRegion();
        break;
      case 'source':
        loadSource();
        break;
      case 'device':
        loadDevice();
        break;
      case 'page':
        loadPage();
        break;
      case 'events':
        reloadEvents();
        break;
    }
  }

  // ------------------------------------------------------------------
  // 地域
  // ------------------------------------------------------------------

  const regionLevel = ref<'country' | 'province' | 'city'>('country');
  const regionLevelOptions = [
    { label: '国家', value: 'country' },
    { label: '省/州', value: 'province' },
    { label: '城市', value: 'city' },
  ];

  const regionChartRef = ref<HTMLDivElement>();
  const { setOptions: setRegionChartOptions } = useECharts(regionChartRef);
  const regionList = ref<Recordable[]>([]);
  const regionLoading = ref(false);

  const regionColumns = [
    { title: '国家', dataIndex: 'countryName', customRender: ({ text }: any) => text || '未知' },
    { title: '省/州', dataIndex: 'provinceName', customRender: ({ text }: any) => text || '-' },
    { title: '城市', dataIndex: 'cityName', customRender: ({ text }: any) => text || '-' },
    { title: 'PV', dataIndex: 'pv', width: 100 },
    { title: 'UV', dataIndex: 'uv', width: 100 },
    { title: 'IP', dataIndex: 'ip', width: 100 },
  ];

  async function loadRegion() {
    regionLoading.value = true;
    try {
      const res = await getRegion({ ...buildBaseQuery(), level: regionLevel.value });
      regionList.value = (res || []).map((row, index) => ({ ...row, _key: `r-${index}` }));
      renderRegionChart();
    } catch (error: any) {
      createMessage.error(error?.message || '获取地域分布失败');
    } finally {
      regionLoading.value = false;
    }
  }

  function renderRegionChart() {
    const rows = regionList.value.slice(0, 15);
    const option: EChartsOption = {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 8, right: 24, top: 8, bottom: 8, containLabel: true },
      xAxis: { type: 'value', name: 'PV' },
      yAxis: { type: 'category', inverse: true, data: rows.map((row) => regionLabel(row)) },
      series: [
        {
          type: 'bar',
          barMaxWidth: 18,
          data: rows.map((row) => Number(row.pv ?? 0)),
          itemStyle: { color: 'var(--shop-primary)' },
        },
      ],
    };
    setRegionChartOptions(option);
  }

  // ------------------------------------------------------------------
  // 来源
  // ------------------------------------------------------------------

  const sourceChartRef = ref<HTMLDivElement>();
  const { setOptions: setSourceChartOptions } = useECharts(sourceChartRef);
  const sourceList = ref<Recordable[]>([]);
  const sourceLoading = ref(false);

  const sourceColumns = [
    { title: '来源', dataIndex: 'source', customRender: ({ text }: any) => text || '-' },
    { title: '媒介', dataIndex: 'medium', customRender: ({ text }: any) => text || '-' },
    { title: '活动', dataIndex: 'campaign', customRender: ({ text }: any) => text || '-' },
    { title: 'PV', dataIndex: 'pv', width: 100 },
    { title: 'UV', dataIndex: 'uv', width: 100 },
  ];

  async function loadSource() {
    sourceLoading.value = true;
    try {
      const res = await getSource(buildBaseQuery());
      sourceList.value = (res || []).map((row, index) => ({ ...row, _key: `s-${index}` }));
      renderSourceChart();
    } catch (error: any) {
      createMessage.error(error?.message || '获取来源分布失败');
    } finally {
      sourceLoading.value = false;
    }
  }

  function renderSourceChart() {
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
          data: sourceList.value.map((row) => ({ name: row.source || '未知', value: Number(row.pv ?? 0) })),
        },
      ],
    };
    setSourceChartOptions(option);
  }

  // ------------------------------------------------------------------
  // 设备
  // ------------------------------------------------------------------

  const deviceChartRef = ref<HTMLDivElement>();
  const { setOptions: setDeviceChartOptions } = useECharts(deviceChartRef);
  const deviceList = ref<Recordable[]>([]);
  const deviceLoading = ref(false);

  const deviceColumns = [
    { title: '设备类型', dataIndex: 'deviceType', customRender: ({ text }: any) => text || '-' },
    { title: '操作系统', dataIndex: 'os', customRender: ({ text }: any) => text || '-' },
    { title: '浏览器', dataIndex: 'browser', customRender: ({ text }: any) => text || '-' },
    { title: '语言', dataIndex: 'lang', customRender: ({ text }: any) => text || '-' },
    { title: 'PV', dataIndex: 'pv', width: 100 },
    { title: 'UV', dataIndex: 'uv', width: 100 },
  ];

  function deviceLabel(row: Recordable): string {
    return row.deviceType || row.os || row.browser || row.lang || '未知';
  }

  async function loadDevice() {
    deviceLoading.value = true;
    try {
      const res = await getDevice(buildBaseQuery());
      deviceList.value = (res || []).map((row, index) => ({ ...row, _key: `d-${index}` }));
      renderDeviceChart();
    } catch (error: any) {
      createMessage.error(error?.message || '获取设备分布失败');
    } finally {
      deviceLoading.value = false;
    }
  }

  function renderDeviceChart() {
    const map = new Map<string, number>();
    deviceList.value.forEach((row) => {
      const name = deviceLabel(row);
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
    setDeviceChartOptions(option);
  }

  // ------------------------------------------------------------------
  // 页面排行
  // ------------------------------------------------------------------

  const pageList = ref<Recordable[]>([]);
  const pageLoading = ref(false);

  const pageColumns = [
    { title: '页面路径', dataIndex: 'pagePath', ellipsis: true },
    { title: 'PV', dataIndex: 'pv', width: 100 },
    { title: 'UV', dataIndex: 'uv', width: 100 },
    { title: '平均停留', dataIndex: 'avgDurationMs', width: 110 },
    { title: '跳出率', dataIndex: 'bounceRate', width: 100 },
  ];

  async function loadPage() {
    pageLoading.value = true;
    try {
      const res = await getPage(buildBaseQuery());
      pageList.value = (res || []).map((row, index) => ({ ...row, _key: `p-${index}` }));
    } catch (error: any) {
      createMessage.error(error?.message || '获取页面排行失败');
    } finally {
      pageLoading.value = false;
    }
  }

  // ------------------------------------------------------------------
  // 事件明细
  // ------------------------------------------------------------------

  const eventsFilter = reactive<{ eventName?: string; keyword?: string }>({ eventName: undefined, keyword: undefined });
  const eventsLoading = ref(false);
  const exportLoading = ref(false);
  const eventsPage = ref<AnalyticsPageResult<TrackEventVO>>({ records: [], total: 0, size: 20, current: 1, pages: 0 });

  const eventsColumns = [
    { title: '事件时间', dataIndex: 'eventTime', width: 170 },
    { title: '事件', dataIndex: 'eventName', width: 120 },
    { title: '访客ID', dataIndex: 'visitorId', width: 150, ellipsis: true },
    { title: '用户ID', dataIndex: 'userId', width: 130, ellipsis: true },
    { title: '店铺', dataIndex: 'storeId', width: 150, ellipsis: true },
    { title: '商品ID', dataIndex: 'productId', width: 150, ellipsis: true },
    { title: '页面路径', dataIndex: 'pagePath', ellipsis: true },
    { title: '国家', dataIndex: 'countryName', width: 90 },
    { title: '城市', dataIndex: 'cityName', width: 100 },
    { title: '设备', dataIndex: 'deviceType', width: 90 },
    { title: 'IP', dataIndex: 'ip', width: 130 },
  ];

  const eventsPagination = {
    current: 1,
    pageSize: 20,
    total: 0,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  };

  function buildEventsQuery(): EventsQuery {
    return {
      ...buildBaseQuery(),
      eventName: eventsFilter.eventName,
      keyword: eventsFilter.keyword,
    };
  }

  async function loadEvents(pageNo = 1, pageSize = eventsPage.value.size) {
    eventsLoading.value = true;
    try {
      const result = await getEvents({ ...buildEventsQuery(), pageNo, pageSize });
      eventsPage.value = result || { records: [], total: 0, size: pageSize, current: pageNo, pages: 0 };
      // 同步表格分页状态
      Object.assign(eventsPagination, {
        current: eventsPage.value.current,
        pageSize: eventsPage.value.size,
        total: eventsPage.value.total,
      });
    } catch (error: any) {
      createMessage.error(error?.message || '获取事件明细失败');
    } finally {
      eventsLoading.value = false;
    }
  }

  function reloadEvents() {
    loadEvents(1, eventsPage.value.size);
  }

  function onEventsTableChange(pagination: any) {
    loadEvents(pagination?.current, pagination?.pageSize);
  }

  // ------------------------------------------------------------------
  // 导出（客户端 CSV，复用 /events 取大页；契约未提供独立导出接口）
  // ------------------------------------------------------------------

  function downloadCsv(filename: string, headers: string[], rows: (string | number)[][]) {
    const escape = (v: any) => {
      const s = v === null || v === undefined ? '' : String(v);
      return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    };
    const lines = [headers.map(escape).join(','), ...rows.map((row) => row.map(escape).join(','))];
    const csv = '\uFEFF' + lines.join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function exportEvents() {
    exportLoading.value = true;
    try {
      // 契约 §6.1 标注 /events 为「导出用」，这里取单页上限 1000 条导出
      const res = await getEvents({ ...buildEventsQuery(), pageNo: 1, pageSize: 1000 });
      const rows = res?.records || [];
      if (!rows.length) {
        createMessage.warning('没有可导出的数据');
        return;
      }
      const headers = ['事件时间', '事件', '访客ID', '用户ID', '店铺', '商品ID', '页面路径', '国家', '省', '城市', '设备', '浏览器', '语言', 'IP', '停留(ms)'];
      const data = rows.map((r) => [
        r.eventTime,
        r.eventName,
        r.visitorId,
        r.userId ?? '',
        r.storeId ?? '',
        r.productId ?? '',
        r.pagePath ?? '',
        r.countryName ?? '',
        r.provinceName ?? '',
        r.cityName ?? '',
        r.deviceType ?? '',
        r.browser ?? '',
        r.lang ?? '',
        r.ip ?? '',
        r.durationMs ?? '',
      ]);
      downloadCsv(`行为明细_${dayjs().format('YYYYMMDDHHmmss')}.csv`, headers, data);
      createMessage.success(`已导出 ${rows.length} 条`);
    } catch (error: any) {
      createMessage.error(error?.message || '导出失败');
    } finally {
      exportLoading.value = false;
    }
  }

  // ------------------------------------------------------------------
  // 初始化
  // ------------------------------------------------------------------

  onMounted(() => {
    updateRangeText();
    void loadStores();
    // 默认激活「地域分布」Tab，直接加载
    loadRegion();
  });
</script>

<style lang="less" scoped>
  .chart-wrap {
    position: relative;
  }

  .chart-empty {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
  }
</style>
