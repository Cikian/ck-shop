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
          <a-radio-group v-model:value="rangePreset" button-style="solid" @change="onPresetChange">
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

        <a-form-item label="关键词">
          <a-input
            v-model:value="query.keyword"
            placeholder="商品标题 / SPU / 用户账号"
            style="width: 220px"
            allow-clear
            @press-enter="reloadAll"
          />
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="loading" @click="reloadAll">查询</a-button>
            <a-button @click="resetQuery">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <p v-if="overview" class="mt-2 text-xs text-gray-400">
        统计区间：{{ overview.rangeStart }} ~ {{ overview.rangeEnd }}
        （加购类指标为区间内数据；"当前购物车"为此刻快照，不受区间影响）
      </p>
    </a-card>

    <!-- 总览 -->
    <a-row :gutter="[16, 16]" class="mb-4">
      <a-col v-for="card in overviewCards" :key="card.title" :xs="24" :sm="12" :md="8" :xl="4">
        <a-card :bordered="false" size="small">
          <a-statistic :title="card.title" :value="card.value" :suffix="card.suffix" :precision="card.precision" />
          <p class="mt-1 text-xs text-gray-400">{{ card.hint }}</p>
        </a-card>
      </a-col>
    </a-row>

    <!-- 趋势 -->
    <a-card :bordered="false" title="加购趋势（按日）" class="mb-4">
      <div ref="trendRef" style="height: 320px; width: 100%"></div>
      <a-empty v-if="!trendLoading && !trendList.length" description="该区间没有加购数据" />
    </a-card>

    <!-- 热度排行 -->
    <a-card :bordered="false" title="商品 / SKU 加购热度排行" class="mb-4">
      <template #extra>
        <a-select v-model:value="rankLimit" style="width: 110px" :options="rankLimitOptions" @change="loadRank" />
      </template>
      <a-table
        :columns="rankColumns"
        :data-source="rankList"
        :loading="rankLoading"
        :pagination="false"
        row-key="rank"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'rank'">
            <a-tag :color="record.rank <= 3 ? 'orange' : 'default'">{{ record.rank }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'product'">
            <div class="flex items-center gap-2">
              <a-avatar shape="square" :size="36" :src="record.coverUrl || undefined">
                <template #icon><shopping-outlined /></template>
              </a-avatar>
              <div class="min-w-0">
                <div class="truncate" style="max-width: 320px">
                  <!-- 商品被物理删除后历史加购流水仍在：给出明确解释，
                       而不是显示一个让人以为"数据坏了"的空标题 -->
                  {{ record.title || record.spuCode || '（商品已删除）' }}
                  <a-tag v-if="record.productStatus === 'N'" color="red" class="ml-1">已下架</a-tag>
                </div>
                <div class="text-xs text-gray-400">
                  {{ record.spuCode || '—' }}
                  <span v-if="record.skuName"> · {{ record.skuName }}</span>
                </div>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'heat'">
            <a-progress :percent="Number(record.heat) || 0" size="small" :show-info="false" stroke-color="#d97706" />
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 加购明细 -->
    <a-card :bordered="false" title="加购明细">
      <a-table
        :columns="eventColumns"
        :data-source="eventPage.records"
        :loading="eventLoading"
        :pagination="eventPagination"
        row-key="id"
        size="small"
        @change="onEventTableChange"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { ShoppingOutlined } from '@ant-design/icons-vue';
  import dayjs, { Dayjs } from 'dayjs';
  import type { EChartsOption } from 'echarts';
  import { useECharts } from '/@/hooks/web/useECharts';
  import { useMessage } from '/@/hooks/web/useMessage';
  import {
    getEventPage,
    getOverview,
    getRank,
    getStoreOptions,
    getTrend,
    type CartStatsEventPage,
    type CartStatsOverview,
    type CartStatsQuery,
    type CartStatsRank,
    type CartStatsTrend,
    type CartStoreOption,
  } from './ShopCartStats.api';

  // ------------------------------------------------------------------
  // 查询条件
  // ------------------------------------------------------------------

  const { createMessage } = useMessage();

  const rangePreset = ref<'1' | '7' | '30' | '90' | 'custom'>('30');
  const customRange = ref<[Dayjs, Dayjs]>([dayjs().subtract(6, 'day'), dayjs()]);

  const query = reactive<CartStatsQuery>({
    storeId: undefined,
    keyword: undefined,
    days: 30,
  });

  /** 把界面上的"快捷区间 / 自定义日期"翻译成后端参数（后端只认 days 或 startDate+endDate） */
  function buildQuery(): CartStatsQuery {
    if (rangePreset.value === 'custom') {
      return {
        storeId: query.storeId,
        keyword: query.keyword,
        startDate: customRange.value[0].format('YYYY-MM-DD'),
        endDate: customRange.value[1].format('YYYY-MM-DD'),
      };
    }
    return {
      storeId: query.storeId,
      keyword: query.keyword,
      days: Number(rangePreset.value),
    };
  }

  function onPresetChange() {
    // 切到自定义时沿用当前自定义日期，不重置用户已经选好的值
    reloadAll();
  }

  function resetQuery() {
    query.storeId = undefined;
    query.keyword = undefined;
    rangePreset.value = '30';
    reloadAll();
  }

  // ------------------------------------------------------------------
  // 店铺下拉
  // ------------------------------------------------------------------

  const storeList = ref<CartStoreOption[]>([]);
  const storeOptions = computed(() => storeList.value.map((item) => ({ label: item.name, value: item.id })));

  async function loadStores() {
    try {
      storeList.value = (await getStoreOptions()) || [];
    } catch (error: any) {
      createMessage.error(error?.message || '获取店铺列表失败');
    }
  }

  // ------------------------------------------------------------------
  // 总览
  // ------------------------------------------------------------------

  const loading = ref(false);
  const overview = ref<CartStatsOverview | null>(null);

  const overviewCards = computed(() => {
    const data = overview.value;
    return [
      { title: '加购次数', value: data?.addTimes ?? 0, hint: '点击"加入购物车"的动作次数', precision: 0 },
      { title: '加购件数', value: data?.addQuantity ?? 0, hint: '上述动作携带的商品件数', precision: 0 },
      { title: '加购用户数', value: data?.addUsers ?? 0, hint: '去重后的加购人数', precision: 0 },
      { title: '涉及商品数', value: data?.addProducts ?? 0, hint: '被加购过的商品（去重）', precision: 0 },
      { title: '人均加购件数', value: data?.quantityPerUser ?? 0, hint: '加购件数 ÷ 加购人数', precision: 2 },
      { title: '当前购物车件数', value: data?.cartQuantity ?? 0, hint: `此刻快照：${data?.cartItems ?? 0} 条 / ${data?.cartUsers ?? 0} 人`, precision: 0 },
    ];
  });

  async function loadOverview() {
    loading.value = true;
    try {
      overview.value = await getOverview(buildQuery());
    } catch (error: any) {
      createMessage.error(error?.message || '获取加购总览失败');
    } finally {
      loading.value = false;
    }
  }

  // ------------------------------------------------------------------
  // 趋势
  // ------------------------------------------------------------------

  const trendRef = ref<HTMLDivElement>();
  const { setOptions } = useECharts(trendRef);
  const trendList = ref<CartStatsTrend[]>([]);
  const trendLoading = ref(false);

  async function loadTrend() {
    trendLoading.value = true;
    try {
      trendList.value = (await getTrend(buildQuery())) || [];
      renderTrend();
    } catch (error: any) {
      createMessage.error(error?.message || '获取加购趋势失败');
    } finally {
      trendLoading.value = false;
    }
  }

  function renderTrend() {
    const rows = trendList.value;
    const option: EChartsOption = {
      tooltip: { trigger: 'axis' },
      legend: { data: ['加购次数', '加购件数', '加购用户数'], bottom: 0 },
      grid: { left: 8, right: 16, top: 24, bottom: 44, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: rows.map((item) => item.statDate) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: '加购次数',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          data: rows.map((item) => item.addTimes),
          itemStyle: { color: '#d97706' },
          areaStyle: { opacity: 0.08 },
        },
        {
          name: '加购件数',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          data: rows.map((item) => item.addQuantity),
          itemStyle: { color: '#0f172a' },
        },
        {
          name: '加购用户数',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          data: rows.map((item) => item.addUsers),
          itemStyle: { color: '#059669' },
        },
      ],
    };
    setOptions(option);
  }

  // ------------------------------------------------------------------
  // 排行
  // ------------------------------------------------------------------

  const rankList = ref<CartStatsRank[]>([]);
  const rankLoading = ref(false);
  const rankLimit = ref(10);
  const rankLimitOptions = [
    { label: 'Top 10', value: 10 },
    { label: 'Top 20', value: 20 },
    { label: 'Top 50', value: 50 },
  ];

  const rankColumns = [
    { title: '排名', dataIndex: 'rank', width: 70 },
    { title: '商品 / 规格', dataIndex: 'product' },
    { title: '加购次数', dataIndex: 'addTimes', width: 100, sorter: (a: CartStatsRank, b: CartStatsRank) => a.addTimes - b.addTimes },
    { title: '加购件数', dataIndex: 'addQuantity', width: 100 },
    { title: '加购人数', dataIndex: 'addUsers', width: 100 },
    { title: '相对热度', dataIndex: 'heat', width: 140 },
    { title: '最近加购', dataIndex: 'lastAddTime', width: 170 },
  ];

  async function loadRank() {
    rankLoading.value = true;
    try {
      rankList.value = (await getRank({ ...buildQuery(), limit: rankLimit.value })) || [];
    } catch (error: any) {
      createMessage.error(error?.message || '获取热度排行失败');
    } finally {
      rankLoading.value = false;
    }
  }

  // ------------------------------------------------------------------
  // 明细
  // ------------------------------------------------------------------

  const eventLoading = ref(false);
  const eventPage = ref<CartStatsEventPage>({ records: [], total: 0, size: 20, current: 1, pages: 0 });

  const eventColumns = [
    { title: '时间', dataIndex: 'createTime', width: 170 },
    { title: '用户', dataIndex: 'username', width: 120 },
    { title: '店铺', dataIndex: 'storeName', width: 140 },
    { title: '商品', dataIndex: 'title', ellipsis: true },
    { title: '规格', dataIndex: 'skuName', width: 160 },
    { title: '数量', dataIndex: 'quantity', width: 80 },
    { title: '动作', dataIndex: 'eventType', width: 90 },
    { title: '来源', dataIndex: 'source', width: 90 },
  ];

  const eventPagination = computed(() => ({
    current: eventPage.value.current,
    pageSize: eventPage.value.size,
    total: eventPage.value.total,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }));

  async function loadEvents(pageNo = eventPage.value.current, pageSize = eventPage.value.size) {
    eventLoading.value = true;
    try {
      const result = await getEventPage({ ...buildQuery(), pageNo, pageSize });
      eventPage.value = result || { records: [], total: 0, size: pageSize, current: pageNo, pages: 0 };
    } catch (error: any) {
      createMessage.error(error?.message || '获取加购明细失败');
    } finally {
      eventLoading.value = false;
    }
  }

  function onEventTableChange(pagination: any) {
    loadEvents(pagination?.current, pagination?.pageSize);
  }

  // ------------------------------------------------------------------
  // 统一刷新
  // ------------------------------------------------------------------

  function reloadAll() {
    loadOverview();
    loadTrend();
    loadRank();
    loadEvents(1, eventPage.value.size);
  }

  onMounted(async () => {
    await loadStores();
    reloadAll();
  });
</script>
