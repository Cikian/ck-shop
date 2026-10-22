<template>
  <div class="ppe">
    <!-- 活动价语义必须写在界面上：留空 ≠ 0 元 -->
    <a-alert
      type="info"
      show-icon
      class="ppe__alert"
      message="活动价留空表示「按活动类型自动算」，不是 0 元"
      description="留空时后端按活动类型（直降金额 / 折扣率 / 满减门槛 / 秒杀）自动计算到手价；只有需要「这个商品单独一个价」时才手填。限量为空表示不单独限制该商品的秒杀数量。"
    />

    <div class="ppe__toolbar">
      <a-button type="primary" ghost size="small" preIcon="ant-design:plus-outlined" :disabled="disabled || !storeId" @click="openPicker">
        添加商品
      </a-button>
      <span class="ppe__count">已关联 {{ rows.length }} 个商品</span>
      <span v-if="!storeId" class="ppe__warn">请先选择「所属店铺」，选商品会按该店铺过滤</span>
      <span v-else class="ppe__hint">只能选所属店铺的商品，跨店商品后端会拒绝</span>
    </div>

    <!-- 批量设置：选中行后才出现，避免常驻一排无用按钮 -->
    <div v-if="selectedKeys.length" class="ppe__bulk">
      <span class="ppe__bulk-label">批量设置（已选 {{ selectedKeys.length }} 行）</span>

      <a-input-number v-model:value="bulkPromoPrice" class="ppe__bulk-input" :min="0" :precision="2" placeholder="活动价" :disabled="disabled" />
      <a-button size="small" type="primary" ghost :disabled="disabled" @click="applyBulkPromoPrice">应用活动价</a-button>
      <a-button size="small" :disabled="disabled" @click="applyBulkPromoPriceEmpty">清空活动价（自动算）</a-button>

      <a-divider type="vertical" />

      <a-input-number v-model:value="bulkStockLimit" class="ppe__bulk-input" :min="0" :precision="0" placeholder="限量" :disabled="disabled" />
      <a-button size="small" type="primary" ghost :disabled="disabled" @click="applyBulkStockLimit">应用限量</a-button>
      <a-button size="small" :disabled="disabled" @click="applyBulkStockLimitEmpty">清空限量</a-button>
    </div>

    <a-table
      v-if="rows.length"
      size="small"
      bordered
      rowKey="__key"
      :pagination="false"
      :columns="columns"
      :data-source="rows"
      :row-selection="rowSelection"
      :scroll="{ x: 980 }"
    >
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.key === 'product'">
          <div class="ppe__product">
            <div class="ppe__product-name">{{ productName(record) }}</div>
            <div class="ppe__product-code">{{ productCode(record) }}</div>
            <div v-if="isForeign(record)" class="ppe__product-warn">
              <Icon icon="ant-design:warning-outlined" />
              该商品不在当前店铺的商品列表中，请确认归属后重新选择
            </div>
          </div>
        </template>

        <template v-else-if="column.key === 'sku'">
          {{ skuText(record) }}
        </template>

        <template v-else-if="column.key === 'price'">
          {{ currentPriceText(record) }}
        </template>

        <template v-else-if="column.key === 'promoPrice'">
          <a-input-number
            v-model:value="record.promoPrice"
            class="ppe__cell-number"
            size="small"
            :min="0"
            :precision="2"
            placeholder="留空自动算"
            :disabled="disabled"
          />
        </template>

        <template v-else-if="column.key === 'stockLimit'">
          <a-input-number
            v-model:value="record.stockLimit"
            class="ppe__cell-number"
            size="small"
            :min="0"
            :precision="0"
            placeholder="不限量"
            :disabled="disabled"
          />
        </template>

        <template v-else-if="column.key === 'sortOrder'">
          <div class="ppe__sort">
            <span class="ppe__sort-value">{{ index + 1 }}</span>
            <a-button size="small" type="text" :disabled="disabled || index === 0" title="上移" @click="moveRow(index, -1)">
              <Icon icon="ant-design:arrow-up-outlined" />
            </a-button>
            <a-button size="small" type="text" :disabled="disabled || index === rows.length - 1" title="下移" @click="moveRow(index, 1)">
              <Icon icon="ant-design:arrow-down-outlined" />
            </a-button>
          </div>
        </template>

        <template v-else-if="column.key === 'action'">
          <a-popconfirm title="确认移除该商品？" ok-text="移除" cancel-text="取消" :disabled="disabled" @confirm="removeRow(record)">
            <a-button size="small" type="link" danger :disabled="disabled">移除</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <a-empty v-else :image="simpleImage" description="还没有关联商品。可以先建活动、稍后再加商品" />

    <!-- 选商品弹窗 -->
    <a-modal
      :open="pickerVisible"
      title="添加商品"
      :width="920"
      :mask-closable="false"
      ok-text="加入关联"
      cancel-text="取消"
      @ok="confirmPick"
      @cancel="closePicker"
    >
      <a-alert
        type="info"
        show-icon
        class="ppe__alert"
        :message="`只列「${storeLabel}」的商品`"
        description="添加后按 SPU 关联（作用于该商品的全部规格）。同一商品重复添加没有意义，已在列表中的商品不可再选。"
      />

      <div class="ppe__picker-search">
        <a-input
          v-model:value="pickerKeyword"
          placeholder="按商品名称模糊搜索"
          allow-clear
          @press-enter="searchPicker"
          @change="onPickerKeywordChange"
        />
        <a-button type="primary" preIcon="ant-design:search-outlined" @click="searchPicker">搜索</a-button>
      </div>

      <a-alert
        v-if="pickerError"
        type="error"
        show-icon
        :message="pickerError"
        class="ppe__alert"
      >
        <template #action>
          <a-button size="small" @click="loadPickerRows">重试</a-button>
        </template>
      </a-alert>

      <a-table
        size="small"
        rowKey="id"
        bordered
        :loading="pickerLoading"
        :columns="pickerColumns"
        :data-source="pickerRows"
        :row-selection="pickerRowSelection"
        :pagination="pickerPagination"
        :scroll="{ x: 720, y: 320 }"
        @change="handlePickerTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'pickerProduct'">
            <div class="ppe__product">
              <div class="ppe__product-name">{{ record.title || record.spuCode || record.id }}</div>
              <div class="ppe__product-code">{{ record.spuCode || record.id }}</div>
            </div>
          </template>
          <template v-else-if="column.key === 'pickerCategory'">{{ record.categoryName || '-' }}</template>
          <template v-else-if="column.key === 'pickerPrice'">{{ formatPrice(record) }}</template>
          <template v-else-if="column.key === 'pickerStock'">{{ record.stock ?? '-' }}</template>
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script lang="ts" name="PromotionProductEditor" setup>
  import { computed, ref, watch } from 'vue';
  import { Empty } from 'ant-design-vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { productList, productQueryById, productSkuList } from '../ShopPromotion.api';

  /**
   * 关联商品编辑器。
   *
   * 数据归属：**唯一数据源是父组件传入的 products 数组**（v-model:products）。
   * 行上的 `__xxx` 字段只是列表展示用的缓存（商品名/现价/规格名），
   * 提交前由 PromotionForm 的 toContractProducts() 统一剥掉，契约 DTO 只认
   * productId / skuId / promoPrice / stockLimit / sortOrder 五个字段。
   */
  const props = withDefaults(
    defineProps<{
      /** 活动所属店铺 id：选商品按它过滤（契约：商品必须属于活动所在店铺） */
      storeId?: string;
      /** 店铺显示名，仅用于弹窗提示 */
      storeLabel?: string;
      products?: any[];
      disabled?: boolean;
    }>(),
    {
      storeId: '',
      storeLabel: '',
      products: () => [],
      disabled: false,
    }
  );

  const emit = defineEmits(['update:products']);

  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;
  const { createMessage } = useMessage();

  const columns = [
    { title: '商品', key: 'product', width: 280, ellipsis: true },
    { title: '规格', key: 'sku', width: 110 },
    { title: '现价', key: 'price', width: 100 },
    { title: '活动价（留空自动算）', key: 'promoPrice', width: 170 },
    { title: '限量', key: 'stockLimit', width: 130 },
    { title: '排序', key: 'sortOrder', width: 140 },
    { title: '操作', key: 'action', width: 80 },
  ];

  /** 已关联商品列表（父组件数组，直接读写行对象） */
  const rows = computed<any[]>(() => props.products || []);
  const selectedKeys = ref<string[]>([]);

  /** 商品 id -> 商品记录（含标题/编码/现价/分类/库存） */
  const productMap = ref<Record<string, any>>({});
  /** 当前店铺的商品是否已成功加载过（决定「归属异常」提示能不能显示） */
  const productsLoaded = ref(false);
  /** skuId -> 规格名（后端已有 SKU 级关联时展示用） */
  const skuNameMap = ref<Record<string, string>>({});

  const rowSelection = computed(() => ({
    selectedRowKeys: selectedKeys.value,
    onChange: (keys: string[]) => {
      selectedKeys.value = keys;
    },
  }));

  const existingProductIds = computed(() => new Set(rows.value.map((row) => row?.productId).filter(Boolean)));

  /* ------------------------------ 展示取值 ------------------------------ */

  /** 行键：契约禁止同一活动重复关联同一 (product_id, sku_id)，所以这两者天然唯一 */
  function rowKeyOf(row: any): string {
    return `${row?.productId || 'p'}::${row?.skuId || 'spu'}`;
  }

  /** 给父组件传进来的行补上稳定 rowKey（只补不覆盖） */
  function ensureRowKeys() {
    (props.products || []).forEach((row: any) => {
      if (row && !row.__key) {
        row.__key = rowKeyOf(row);
      }
    });
  }

  function productName(row: any): string {
    const product = productMap.value[row?.productId];
    return product?.title || product?.spuCode || `商品 ${row?.productId || '-'}`;
  }

  function productCode(row: any): string {
    const product = productMap.value[row?.productId];
    return product?.spuCode || row?.productId || '-';
  }

  function skuText(row: any): string {
    if (!row?.skuId) {
      return '全部规格';
    }
    return skuNameMap.value[row.skuId] || `规格 ${row.skuId}`;
  }

  function formatPrice(product: any): string {
    const min = product?.minPrice ?? product?.price;
    if (min === null || min === undefined || min === '') {
      return '-';
    }
    const max = product?.maxPrice ?? product?.price;
    const minText = Number(min);
    if (!Number.isFinite(minText)) {
      return '-';
    }
    if (max === null || max === undefined || max === '' || Number(max) === minText) {
      return `¥${minText.toFixed(2)}`;
    }
    return `¥${minText.toFixed(2)} ~ ¥${Number(max).toFixed(2)}`;
  }

  function currentPriceText(row: any): string {
    const product = productMap.value[row?.productId];
    if (!product) {
      return '-';
    }
    return formatPrice(product);
  }

  /**
   * 该行商品不在当前店铺商品列表中：店铺被改过、或商品已删除/调店。
   * 必须等店铺商品真的加载成功过再判断，否则加载期间会给每一行都闪一次「归属异常」。
   */
  function isForeign(row: any): boolean {
    return productsLoaded.value && !!row?.productId && !!props.storeId && !productMap.value[row.productId];
  }

  /* ------------------------------ 加载商品 ------------------------------ */

  /** 按当前店铺拉商品（选商品弹窗与行展示共用同一份数据） */
  async function loadStoreProducts() {
    productMap.value = {};
    productsLoaded.value = false;
    if (!props.storeId) {
      return;
    }
    try {
      const res = await productList({ storeId: props.storeId, pageNo: 1, pageSize: 500 });
      const records = res?.records || res || [];
      const map: Record<string, any> = {};
      records.forEach((item: any) => {
        if (item?.id) {
          map[item.id] = item;
        }
      });
      productMap.value = map;
      productsLoaded.value = true;
    } catch (e) {
      console.warn('加载店铺商品失败', e);
    }
    // 已关联的商品可能不在第一页 500 条里，按 id 再补一次，避免行上显示成裸 id
    await fillMissingProducts();
    await fillSkuNames();
  }

  /**
   * 已关联的商品可能不在店铺商品第一页（500 条）里，按 id 逐个补查，
   * 否则行上只能显示裸 productId。后端商品列表接口不接受 id 列表（ProductQuery 里没有 id），
   * 所以只能走 queryById。
   */
  async function fillMissingProducts() {
    const missing = Array.from(new Set(rows.value.map((row) => row?.productId).filter((id) => id && !productMap.value[id]))) as string[];
    if (!missing.length) {
      return;
    }
    const map = { ...productMap.value };
    // 上限 20：单个活动的关联商品数量有限，避免异常数据触发大量请求
    await Promise.all(
      missing.slice(0, 20).map(async (id) => {
        try {
          const detail = await productQueryById(id);
          if (detail?.id) {
            map[detail.id] = detail;
          }
        } catch (e) {
          // 兜底查询失败不影响编辑：行上会显示商品 id 与归属告警
          console.warn('按id补查商品失败', id, e);
        }
      })
    );
    productMap.value = map;
  }

  /** 已有的 SKU 级关联：拉一次 SKU 列表，把 skuId 翻成规格名 */
  async function fillSkuNames() {
    const skuIds = rows.value.map((row) => row?.skuId).filter(Boolean);
    if (!skuIds.length) {
      return;
    }
    const productIds = Array.from(
      new Set(
        rows.value
          .filter((row) => row?.skuId)
          .map((row) => row.productId)
          .filter(Boolean)
      )
    );
    const map = { ...skuNameMap.value };
    await Promise.all(
      productIds.map(async (productId) => {
        try {
          const res = await productSkuList({ id: productId });
          const list = Array.isArray(res) ? res : res?.records || [];
          list.forEach((sku: any) => {
            if (sku?.id) {
              map[sku.id] = sku.combinationName || sku.skuCode || sku.id;
            }
          });
        } catch (e) {
          console.warn('加载 SKU 组合失败', productId, e);
        }
      })
    );
    skuNameMap.value = map;
  }

  watch(
    () => props.storeId,
    () => {
      selectedKeys.value = [];
      loadStoreProducts();
    },
    { immediate: true }
  );

  // 父组件替换商品行（切换到另一条活动 / 保存后回填）时补 key 与名称
  watch(
    () => props.products,
    () => {
      ensureRowKeys();
      selectedKeys.value = [];
      fillMissingProducts();
      fillSkuNames();
    },
    { immediate: true }
  );

  /* ------------------------------ 增删改 ------------------------------ */

  /** 统一出口：按当前顺序重排 sortOrder 后交回父组件 */
  function emitProducts(list: any[]) {
    list.forEach((row: any, index: number) => {
      row.sortOrder = index;
    });
    emit('update:products', list);
  }

  function removeRow(row: any) {
    selectedKeys.value = selectedKeys.value.filter((key) => key !== row.__key);
    emitProducts(rows.value.filter((item) => item.__key !== row.__key));
  }

  function moveRow(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= rows.value.length) {
      return;
    }
    const list = [...rows.value];
    const [item] = list.splice(index, 1);
    list.splice(target, 0, item);
    emitProducts(list);
  }

  /* ------------------------------ 批量设置 ------------------------------ */

  const bulkPromoPrice = ref<any>(null);
  const bulkStockLimit = ref<any>(null);

  function selectedRows(): any[] {
    return rows.value.filter((row) => selectedKeys.value.includes(row.__key));
  }

  function applyBulkPromoPrice() {
    if (bulkPromoPrice.value === null || bulkPromoPrice.value === undefined || bulkPromoPrice.value === '') {
      createMessage.warning('请先输入活动价，或点「清空活动价（自动算）」');
      return;
    }
    selectedRows().forEach((row) => (row.promoPrice = bulkPromoPrice.value));
    createMessage.success(`已把活动价 ${bulkPromoPrice.value} 应用到 ${selectedKeys.value.length} 行`);
  }

  function applyBulkPromoPriceEmpty() {
    selectedRows().forEach((row) => (row.promoPrice = null));
    bulkPromoPrice.value = null;
    createMessage.success(`已把 ${selectedKeys.value.length} 行改为「自动算」`);
  }

  function applyBulkStockLimit() {
    if (bulkStockLimit.value === null || bulkStockLimit.value === undefined || bulkStockLimit.value === '') {
      createMessage.warning('请先输入限量，或点「清空限量」');
      return;
    }
    selectedRows().forEach((row) => (row.stockLimit = bulkStockLimit.value));
    createMessage.success(`已把限量 ${bulkStockLimit.value} 应用到 ${selectedKeys.value.length} 行`);
  }

  function applyBulkStockLimitEmpty() {
    selectedRows().forEach((row) => (row.stockLimit = null));
    bulkStockLimit.value = null;
    createMessage.success(`已清空 ${selectedKeys.value.length} 行的限量`);
  }

  /* ------------------------------ 选商品弹窗 ------------------------------ */

  const pickerVisible = ref(false);
  const pickerLoading = ref(false);
  const pickerRows = ref<any[]>([]);
  const pickerKeyword = ref('');
  const pickerSelected = ref<string[]>([]);
  /**
   * 跨页选中的商品对象（key = 商品 id）。
   *
   * 之前只存 key，confirmPick 又从当前页 pickerRows 里过滤 —— 上一页勾选的商品
   * 不在当前页数据里，会被静默丢掉，提示却仍说"已添加 N 个"。
   * 现在按 id 保存对象，翻页只增删当前页对应的条目。
   */
  const pickerSelectedMap = ref<Map<string, any>>(new Map());
  /** 弹窗加载失败原因：保留旧数据并显示后端 message，而不是静默空列表 */
  const pickerError = ref('');
  const pickerTotal = ref(0);
  const pickerPageNo = ref(1);
  const pickerPageSize = ref(10);

  let keywordTimer: ReturnType<typeof setTimeout> | null = null;

  const pickerColumns = [
    { title: '商品', key: 'pickerProduct', width: 300, ellipsis: true },
    { title: '分类', key: 'pickerCategory', width: 140 },
    { title: '售价', key: 'pickerPrice', width: 130 },
    { title: '库存', key: 'pickerStock', width: 100 },
  ];

  const pickerRowSelection = computed(() => ({
    selectedRowKeys: pickerSelected.value,
    onChange: (keys: string[], rows: any[]) => {
      pickerSelected.value = keys;
      const selectedSet = new Set(keys);
      const next = new Map<string, any>();
      // 先保留其它页已选中的对象（rows 只包含当前页）
      for (const [id, item] of pickerSelectedMap.value) {
        if (selectedSet.has(id)) {
          next.set(id, item);
        }
      }
      // 再用当前页最新数据覆盖/补充
      for (const row of rows) {
        if (selectedSet.has(row.id)) {
          next.set(row.id, row);
        }
      }
      pickerSelectedMap.value = next;
    },
    // 已在关联列表里的商品不可重复选（契约禁止同一活动重复关联同一商品）
    getCheckboxProps: (row: any) => ({ disabled: existingProductIds.value.has(row.id) }),
  }));

  const pickerPagination = computed(() => ({
    current: pickerPageNo.value,
    pageSize: pickerPageSize.value,
    total: pickerTotal.value,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '50'],
    showTotal: (total: number) => `共 ${total} 个商品`,
  }));

  async function loadPickerRows() {
    if (!props.storeId) {
      pickerRows.value = [];
      pickerTotal.value = 0;
      return;
    }
    pickerLoading.value = true;
    pickerError.value = '';
    try {
      const res = await productList({
        storeId: props.storeId,
        keyword: pickerKeyword.value || '',
        pageNo: pickerPageNo.value,
        pageSize: pickerPageSize.value,
      });
      pickerRows.value = res?.records || res || [];
      pickerTotal.value = Number(res?.total ?? pickerRows.value.length);
    } catch (e: any) {
      // 失败时保留上一页数据（翻页失败不该把用户正在看的列表清空），并显示后端 message
      pickerError.value = e?.message || '加载商品失败，请重试';
      createMessage.error(pickerError.value);
    } finally {
      pickerLoading.value = false;
    }
  }

  function openPicker() {
    if (!props.storeId) {
      createMessage.warning('请先选择「所属店铺」');
      return;
    }
    pickerSelected.value = [];
    pickerSelectedMap.value = new Map();
    pickerError.value = '';
    pickerKeyword.value = '';
    pickerPageNo.value = 1;
    pickerVisible.value = true;
    loadPickerRows();
  }

  function closePicker() {
    pickerVisible.value = false;
  }

  function searchPicker() {
    pickerPageNo.value = 1;
    loadPickerRows();
  }

  /** 输入停顿后再查，避免逐字打请求；回车/按钮仍可立即搜索 */
  function onPickerKeywordChange() {
    if (keywordTimer) {
      clearTimeout(keywordTimer);
    }
    keywordTimer = setTimeout(() => {
      keywordTimer = null;
      searchPicker();
    }, 500);
  }

  function handlePickerTableChange(pagination: any) {
    pickerPageNo.value = pagination?.current || 1;
    pickerPageSize.value = pagination?.pageSize || 10;
    loadPickerRows();
  }

  /** 把弹窗里勾选的商品加入关联列表（SPU 级：skuId 留空 = 作用于整个 SPU） */
  function confirmPick() {
    if (!pickerSelected.value.length) {
      createMessage.warning('请先勾选要添加的商品');
      return;
    }
    // 从跨页 Map 取对象：上一页勾选的商品也在里面，不会被当前页数据过滤掉
    const picked = pickerSelected.value
      .map((id) => pickerSelectedMap.value.get(id))
      .filter((item): item is any => !!item);
    if (!picked.length) {
      createMessage.warning('请先勾选要添加的商品');
      return;
    }
    const next = [...rows.value];
    picked.forEach((product: any) => {
      if (next.some((row) => row.productId === product.id && !row.skuId)) {
        return;
      }
      next.push({
        productId: product.id,
        skuId: null,
        promoPrice: null,
        stockLimit: null,
        sortOrder: next.length,
        __key: `${product.id}::spu`,
      });
    });
    // 顺手把弹窗拿到的商品写进映射，行上立刻有名字/现价，不用等下一次刷新
    const map = { ...productMap.value };
    picked.forEach((product: any) => {
      map[product.id] = product;
    });
    productMap.value = map;
    pickerVisible.value = false;
    emitProducts(next);
    createMessage.success(`已添加 ${picked.length} 个商品，记得点「保存」落库`);
  }

  /**
   * 供父组件在校验前调用：返回已关联商品 id（去重），用于「活动冲突检测」。
   */
  function getProductIds(): string[] {
    return Array.from(new Set(rows.value.map((row) => row.productId).filter(Boolean)));
  }

  defineExpose({ getProductIds, reload: loadStoreProducts });
</script>

<style lang="less" scoped>
  .ppe__alert {
    margin-bottom: 12px;
  }

  .ppe__toolbar {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 12px;
    align-items: center;
    margin-bottom: 10px;
  }

  .ppe__count {
    font-size: 13px;
    color: var(--shop-text-secondary);
  }

  .ppe__hint {
    font-size: 12px;
    color: var(--shop-text-secondary);
  }

  .ppe__warn {
    font-size: 12px;
    color: #d46b08;
  }

  .ppe__bulk {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 8px;
    align-items: center;
    padding: 8px 12px;
    margin-bottom: 10px;
    background: var(--shop-surface-muted);
    border: 1px solid var(--shop-border);
    border-radius: 8px;
  }

  .ppe__bulk-label {
    font-size: 13px;
    font-weight: 500;
    color: var(--shop-text-secondary);
  }

  .ppe__bulk-input {
    width: 130px;
  }

  .ppe__product-name {
    overflow: hidden;
    font-weight: 500;
    color: var(--shop-text);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .ppe__product-code {
    font-size: 12px;
    color: var(--shop-text-secondary);
  }

  .ppe__product-warn {
    margin-top: 2px;
    font-size: 12px;
    color: #d46b08;
  }

  .ppe__cell-number {
    width: 100%;
  }

  .ppe__sort {
    display: flex;
    gap: 2px;
    align-items: center;
  }

  .ppe__sort-value {
    min-width: 22px;
    color: var(--shop-text-secondary);
  }

  .ppe__picker-search {
    display: flex;
    gap: 8px;
    margin: 12px 0;
  }
</style>
