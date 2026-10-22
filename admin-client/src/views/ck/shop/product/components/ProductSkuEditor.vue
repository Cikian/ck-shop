<template>
  <div class="sku-editor">
    <!-- 工具条 -->
    <div class="sku-editor__toolbar">
      <a-space :size="8" wrap>
        <a-button type="primary" preIcon="ant-design:plus-outlined" :disabled="disabled || !specReady" @click="toggleAddPanel">
          单条新增组合
        </a-button>
        <a-button preIcon="ant-design:thunderbolt-outlined" :disabled="disabled || !specReady" @click="generateCartesian">
          一键生成全部组合
        </a-button>
        <a-tooltip :title="specReady ? '按当前规格值与已保存的组合对齐' : '请先在上一步配置规格及可选值'">
          <a-button preIcon="ant-design:sync-outlined" :disabled="disabled || !combinations.length" @click="regenerateMissing">
            仅补齐新增组合
          </a-button>
        </a-tooltip>
      </a-space>
      <div class="sku-editor__stats">
        <span
          >组合 <strong>{{ combinations.length }}</strong> / 应生成 <strong>{{ expectedCount }}</strong></span
        >
        <a-divider type="vertical" />
        <span
          >在售 <strong>{{ saleableCount }}</strong></span
        >
        <a-divider type="vertical" />
        <span
          >库存合计 <strong>{{ stockTotal }}</strong></span
        >
      </div>
    </div>

    <a-alert v-if="!specReady" type="info" show-icon message="请先在「规格配置」页签设置规格及可选值，再生成 SKU 组合。" />

    <template v-else>
      <!-- 单条新增：日常最高频的操作，选好每个规格的值 + 填库存即可加入表格 -->
      <div v-if="addPanelVisible" class="sku-add-panel">
        <div class="sku-add-panel__title">
          <Icon icon="ant-design:plus-circle-outlined" />
          新增一条规格组合
        </div>
        <div class="sku-add-panel__row">
          <div v-for="spec in normalizedSpecs" :key="spec.id || spec.name" class="sku-add-panel__field">
            <label class="sku-add-panel__label">{{ spec.name }}</label>
            <a-select
              v-model:value="newCombo[spec.id || spec.name]"
              class="sku-add-panel__select"
              size="small"
              :placeholder="`选择${spec.name}`"
              :options="spec.values.map((v) => ({ label: v.value, value: valueId(spec, v) }))"
              :disabled="disabled"
            />
          </div>
          <div class="sku-add-panel__field">
            <label class="sku-add-panel__label">售价</label>
            <a-input-number
              v-model:value="newComboPrice"
              class="sku-add-panel__number"
              size="small"
              :min="0"
              :precision="2"
              placeholder="留空回退基准价"
              :disabled="disabled"
            />
          </div>
          <div class="sku-add-panel__field">
            <label class="sku-add-panel__label">库存</label>
            <a-input-number v-model:value="newComboStock" class="sku-add-panel__number" size="small" :min="0" :precision="0" :disabled="disabled" />
          </div>
          <div class="sku-add-panel__actions">
            <a-button type="primary" size="small" :disabled="disabled" @click="addSingleCombination">加入表格</a-button>
            <a-button size="small" :disabled="disabled" @click="addPanelVisible = false">收起</a-button>
          </div>
        </div>
        <div class="sku-add-panel__hint"> 同一组规格值只能存在一条组合；重复时会提示，已存在的组合请直接在下方表格里改。 </div>
      </div>
      <a-alert
        v-if="combinations.length && combinations.length < expectedCount"
        type="warning"
        show-icon
        class="sku-editor__alert"
        :message="`已生成 ${combinations.length} 个组合，少于全部 ${expectedCount} 个组合`"
        description="少的那些组合就是「不存在的组合」：勾选后点「删除选中」，或直接点行末的删除按钮移除；店铺端会把对应规格值置灰不可选。"
      />

      <!-- 批量操作条 -->
      <div v-if="combinations.length" class="sku-editor__bulk">
        <span class="sku-editor__bulk-label">批量填充</span>
        <a-select v-model:value="bulkField" class="sku-editor__bulk-field" :options="bulkFieldOptions" :disabled="disabled" />
        <a-input-number
          v-if="bulkField === 'price'"
          v-model:value="bulkNumber"
          class="sku-editor__bulk-input"
          :min="0"
          :precision="2"
          placeholder="输入价格"
          :disabled="disabled"
        />
        <a-input-number
          v-else-if="bulkField === 'stock'"
          v-model:value="bulkNumber"
          class="sku-editor__bulk-input"
          :min="0"
          :precision="0"
          placeholder="输入库存"
          :disabled="disabled"
        />
        <a-input
          v-else
          v-model:value="bulkText"
          class="sku-editor__bulk-input"
          :placeholder="bulkField === 'skuCode' ? '编码前缀' : '图片URL'"
          :disabled="disabled"
        />
        <a-button type="primary" ghost :disabled="disabled" @click="applyBulk">应用到全部组合</a-button>
        <a-divider type="vertical" />
        <a-button size="small" :disabled="disabled" @click="setAllSaleable('Y')">全部标记可售</a-button>
        <a-button size="small" :disabled="disabled" @click="setAllSaleable('N')">全部标记不发售</a-button>
        <template v-if="selectedKeys.length">
          <a-divider type="vertical" />
          <a-popconfirm
            :title="`确认删除选中的 ${selectedKeys.length} 个组合？`"
            description="删除后店铺端会把对应规格值置灰、该 SKU 不再售卖。"
            ok-text="删除"
            cancel-text="取消"
            @confirm="removeSelected"
          >
            <a-button size="small" danger :disabled="disabled">删除选中（{{ selectedKeys.length }}）</a-button>
          </a-popconfirm>
        </template>
      </div>

      <a-table
        v-if="combinations.length"
        size="small"
        rowKey="__rowKey"
        bordered
        :pagination="pagination"
        :columns="tableColumns"
        :row-selection="rowSelection"
        :data-source="rows"
        :row-class-name="rowClassName"
      >
        <template #bodyCell="{ column, record }">
          <!-- 规格值列 -->
          <template v-if="column.__specValue">
            <a-select
              v-model:value="record.__refs[column.__specId]"
              size="small"
              class="sku-editor__cell-select"
              :disabled="disabled"
              :options="column.__options"
              @change="onRefChange(record)"
            />
          </template>
          <template v-else-if="column.dataIndex === 'skuCode'">
            <a-input v-model:value="record.skuCode" size="small" placeholder="可留空" :disabled="disabled" @change="onCellChange" />
          </template>
          <template v-else-if="column.dataIndex === 'price'">
            <a-input-number
              v-model:value="record.price"
              size="small"
              class="sku-editor__cell-number"
              :min="0"
              :precision="2"
              :disabled="disabled"
              placeholder="回退基准价"
              @change="onCellChange"
            />
          </template>
          <template v-else-if="column.dataIndex === 'stock'">
            <a-input-number
              v-model:value="record.stock"
              size="small"
              class="sku-editor__cell-number"
              :min="0"
              :precision="0"
              :disabled="disabled"
              @change="onCellChange"
            />
          </template>
          <template v-else-if="column.dataIndex === 'imageUrl'">
            <!--
              用弹层承载上传组件：
              1) 行内只放一个小按钮，行高完全不变；
              2) 复用与主图/图集同一个 JImageUpload，用户不必手填 URL。
            -->
            <a-popover trigger="click" placement="bottomRight" overlay-class-name="sku-img-popover">
              <template #content>
                <div class="sku-editor__upload-pop">
                  <j-image-upload
                    :value="record.imageUrl"
                    biz-path="/shop/product/sku"
                    text="上传SKU图"
                    :disabled="disabled"
                    @update:value="onImageChange(record, $event)"
                  />
                  <div class="sku-editor__upload-hint">建议使用 1:1 方图；留空时店铺端回退商品主图</div>
                </div>
              </template>
              <a-button size="small" class="sku-editor__image-btn" :disabled="disabled">
                <img v-if="record.imageUrl" :src="record.imageUrl" class="sku-editor__thumb" alt="SKU配图" loading="lazy" />
                <span v-else class="sku-editor__image-btn-text">上传</span>
              </a-button>
            </a-popover>
          </template>
          <template v-else-if="column.dataIndex === 'saleable'">
            <a-switch
              :checked="record.saleable !== 'N'"
              checked-children="可售"
              un-checked-children="不发售"
              size="small"
              :disabled="disabled"
              @change="
                (checked) => {
                  record.saleable = checked ? 'Y' : 'N';
                  onCellChange();
                }
              "
            />
          </template>
          <template v-else-if="column.dataIndex === '__action'">
            <a-popconfirm
              title="确认删除该组合？"
              description="删除后店铺端会把对应规格值置灰、该 SKU 不再售卖。"
              ok-text="删除"
              cancel-text="取消"
              placement="topRight"
              @confirm="removeRow(record)"
            >
              <a-button type="text" size="small" danger :disabled="disabled" title="删除该组合">
                <Icon icon="ant-design:delete-outlined" />
              </a-button>
            </a-popconfirm>
          </template>
          <template v-else>
            <span>{{ record.__name }}</span>
          </template>
        </template>
      </a-table>

      <a-empty v-else :image="simpleImage" description="还没有组合，点「一键生成全部组合」按规格值做笛卡尔积，或用「单条新增组合」逐条添加" />
    </template>
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, ref, watch } from 'vue';
  import { Empty } from 'ant-design-vue';
  // 必须显式 import：模板用的是 <j-image-upload>，它并未全局注册
  import JImageUpload from '/@/components/Form/src/jeecg/components/JImageUpload.vue';
  import { useMessage } from '/@/hooks/web/useMessage';

  /**
   * SKU 规格组合编辑器
   * - 组合键：按「规格顺序」把每个规格选中的规格值 id 用 "-" 连接（与后端一致）
   * - 规格值不能重复选（同一规格选两次会导致组合键歧义）
   * - 支持批量填充价格 / 库存 / 编码 / 图片，支持整行标记"不发售"（不存在的组合直接删行即可）
   */
  interface SpecValueItem {
    id?: string;
    tempId?: string;
    value: string;
    imageUrl?: string;
  }

  interface SpecItem {
    id?: string;
    name: string;
    values: SpecValueItem[];
  }

  interface ComboRow {
    __rowKey: string;
    __refs: Record<string, string>;
    __name: string;
    id?: string;
    skuCode?: string;
    price?: number | string | null;
    stock?: number | null;
    imageUrl?: string;
    saleable?: string;
  }

  const props = withDefaults(
    defineProps<{
      specs: SpecItem[];
      combinations: any[];
      basePrice?: number | string | null;
      disabled?: boolean;
    }>(),
    {
      specs: () => [],
      combinations: () => [],
      basePrice: null,
      disabled: false,
    }
  );

  const emit = defineEmits(['update:combinations', 'change']);

  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;
  const { createMessage } = useMessage();

  const rows = ref<ComboRow[]>([]);
  const pageNo = ref(1);
  const pageSize = ref(10);
  /** 勾选的组合行（行键为 __rowKey），用于批量删除 */
  const selectedKeys = ref<string[]>([]);

  const rowSelection = computed(() => ({
    selectedRowKeys: selectedKeys.value,
    onChange: (keys: string[]) => {
      selectedKeys.value = keys;
    },
    // 注意：脚本作用域里没有裸的 disabled，必须走 props（模板里才有自动解包）
    getCheckboxProps: (row: ComboRow) => ({ disabled: props.disabled, name: row.__name }),
  }));

  /**
   * 交给 a-table 自己做分页（data-source 传完整 rows）。
   * 之前是"手动 slice + pagination"两条路并存：slice 后的数组长度只有一页，
   * AntD 据此算出总数为 10，于是永远只有 1 页——这就是"改了每页条数却不能翻页"的原因。
   */
  const pagination = computed(() => ({
    current: pageNo.value,
    pageSize: pageSize.value,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '50', '100'],
    showTotal: (total: number, range: [number, number]) => `第 ${range[0]}-${range[1]} 条 / 共 ${total} 个组合`,
    onChange: (page: number, size: number) => {
      pageNo.value = size !== pageSize.value ? 1 : page;
      pageSize.value = size;
    },
  }));

  /** 规格值稳定 id：优先后端主键，其次临时主键，再其次"值"文本 */
  function valueId(spec: SpecItem, value: SpecValueItem): string {
    return value.id || value.tempId || `${spec.name || 'spec'}::${value.value}`;
  }

  const normalizedSpecs = computed(() =>
    (props.specs || [])
      .map((spec) => ({
        ...spec,
        values: (spec.values || []).filter((v) => (v.value || '').trim() !== ''),
      }))
      .filter((spec) => (spec.name || '').trim() !== '' && spec.values.length > 0)
  );

  const specReady = computed(() => normalizedSpecs.value.length > 0);

  const expectedCount = computed(() => normalizedSpecs.value.reduce((acc, spec) => acc * spec.values.length, 1));

  const saleableCount = computed(() => rows.value.filter((r) => r.saleable !== 'N').length);

  const stockTotal = computed(() => rows.value.filter((r) => r.saleable !== 'N').reduce((acc, r) => acc + (Number(r.stock) || 0), 0));

  const tableColumns = computed(() => {
    const specColumns = normalizedSpecs.value.map((spec) => ({
      title: spec.name,
      dataIndex: `spec_${spec.id || spec.name}`,
      key: `spec_${spec.id || spec.name}`,
      width: 118,
      __specValue: true,
      __specId: spec.id || spec.name,
      __options: spec.values.map((value) => ({
        label: value.value,
        value: valueId(spec, value),
      })),
    }));
    /*
     * 列宽刻意压紧，让所有列在面板内一次放下（不再出现横向滚动条）：
     * 3 个规格列 ×118 + 组合 150 + 编码 140 + 售价 116 + 库存 88 + 配图 84 + 状态 96 + 操作 56 ≈ 1084px。
     */
    return [
      ...specColumns,
      { title: '组合', dataIndex: '__name', key: '__name', width: 150, ellipsis: true, align: 'center' },
      { title: 'SKU编码', dataIndex: 'skuCode', key: 'skuCode', width: 140, align: 'center' },
      { title: '售价', dataIndex: 'price', key: 'price', width: 116, align: 'center' },
      { title: '库存', dataIndex: 'stock', key: 'stock', width: 116, align: 'center' },
      { title: 'SKU配图', dataIndex: 'imageUrl', key: 'imageUrl', width: 84, align: 'center' },
      { title: '售卖状态', dataIndex: 'saleable', key: 'saleable', width: 96, align: 'center' },
      { title: '操作', dataIndex: '__action', key: '__action', width: 56, align: 'center' },
    ];
  });

  function rowClassName(record: ComboRow) {
    return record.saleable === 'N' ? 'sku-row-unsaleable' : '';
  }

  /** 一行组合的展示名 + 组合键 */
  function describe(specs: SpecItem[], refs: Record<string, string>) {
    const parts: string[] = [];
    const keys: string[] = [];
    specs.forEach((spec) => {
      const specKey = spec.id || spec.name;
      const vid = refs[specKey];
      const value = (spec.values || []).find((v) => valueId(spec, v) === vid);
      parts.push(value?.value || '');
      keys.push(vid || '');
    });
    return { name: parts.join(' / '), key: keys.join('-') };
  }

  function rebuildRowsFromProps() {
    // 行被整体重建，旧的勾选键已失效，必须清空
    selectedKeys.value = [];
    const specs = normalizedSpecs.value;
    if (!specs.length) {
      rows.value = [];
      return;
    }
    rows.value = (props.combinations || [])
      .map((combo) => {
        const refs: Record<string, string> = {};
        const sourceRefs: string[] = combo.specValueRefs || combo.specValueIds || [];
        specs.forEach((spec, index) => {
          const specKey = spec.id || spec.name;
          const byIndex = sourceRefs[index];
          const hit = spec.values.some((v) => valueId(spec, v) === byIndex);
          refs[specKey] = hit ? byIndex : valueId(spec, spec.values[0]);
        });
        const { name, key } = describe(specs, refs);
        return {
          __rowKey: key || combo.id || `row_${Math.random().toString(36).slice(2, 8)}`,
          __refs: refs,
          __name: name,
          id: combo.id,
          skuCode: combo.skuCode || '',
          price: combo.price ?? null,
          stock: combo.stock ?? 0,
          imageUrl: combo.imageUrl || '',
          saleable: combo.saleable || 'Y',
        } as ComboRow;
      })
      .filter((row) => !!row.__rowKey);
    // 每次按 props 重建后，同步一次结构快照，作为"规格是否增长"的基线
    prevSpecStructure = buildStructure();
  }

  /**
   * 本地改动标记。
   * sync() 会把组合 emit 给父组件，父组件更新 props 后又会触发下面的 watch；
   * 若不抑制，重建 rows 会打断正在输入的内容，严重时往复循环。
   *
   * 注意：必须声明在下面那个 immediate 的 watch **之前**。
   * 该 watch 立即执行一次，若变量在其后才用 let 声明，会因暂时性死区（TDZ）抛
   * "Cannot access before initialization"，导致整个组件 setup 失败、页签直接空白。
   */
  let syncingLocal = false;

  /**
   * 上一次的规格结构快照：specKey -> 该规格的可选值 id 集合。
   * 用来判断规格是否"增长"（新增规格 / 新增可选值），只有增长时才自动扩展组合。
   *
   * 同样必须声明在下面那个 immediate 的 watch 之前：该 watch 会（经 rebuildRowsFromProps）
   * 读写这个变量，声明在后会因 TDZ 让整个组件 setup 失败。
   */
  let prevSpecStructure: Map<string, Set<string>> = new Map();

  watch(
    () => props.combinations,
    () => {
      // 本地改动（表格内联编辑、批量填充等）刚 emit 出去时会回传一个新数组，
      // 此时不能重建 rows，否则会打断正在输入的内容、甚至形成往复循环。
      if (syncingLocal) {
        return;
      }
      rebuildRowsFromProps();
    },
    { immediate: true, deep: false }
  );

  // 规格结构变化时：只有"增长"（新增规格 / 新增可选值）才自动补组合，其余情况只做对齐与去重
  watch(
    () => normalizedSpecs.value.map((s) => `${s.id || s.name}:${s.values.map((v) => valueId(s, v)).join(',')}`).join('|'),
    () => {
      const current = buildStructure();
      const expanded = expandForNewStructure(prevSpecStructure);
      prevSpecStructure = current;
      if (expanded > 0) {
        createMessage.info(`规格已变化，自动补出 ${expanded} 个组合，当前共 ${rows.value.length} 个；请删除不存在的组合`);
      }
      sync();
    }
  );

  /** 当前规格结构快照：specKey -> 该规格可选值的 id 集合 */
  function buildStructure(): Map<string, Set<string>> {
    const map = new Map<string, Set<string>>();
    for (const spec of normalizedSpecs.value) {
      map.set(spec.id || spec.name, new Set(spec.values.map((v) => valueId(spec, v))));
    }
    return map;
  }

  /**
   * 让已存在的组合与"增长了的"规格结构对齐。
   *
   * 背景（真 bug，曾导致 SKU 全部消失）：
   * 新增一个规格后，已有行的 __refs 里没有这一维的值，组合键不完整；
   * 旧版 sync() 会把"任一分量为空"的行**直接过滤掉**，18 条组合一次性全部消失。
   *
   * 正确语义：规格是一个**维度**，维度增长时应把已有组合按新取值展开（笛卡尔积），
   * 并保留原行的价格 / 库存 / 配图 / 状态，之后由用户删掉不存在的组合。
   * 只在"增长"时展开——改名、删除规格值都不触发，避免把用户已删掉的组合又复活。
   *
   * @returns 自动补出的组合条数
   */
  function expandForNewStructure(before: Map<string, Set<string>>): number {
    const specs = normalizedSpecs.value;
    if (!specs.length || !rows.value.length) {
      return 0;
    }
    let result = rows.value;
    let expanded = 0;

    for (const spec of specs) {
      const specKey = spec.id || spec.name;
      const oldValues = before.get(specKey);

      if (!oldValues) {
        // 全新规格：只有当确有行缺这一维时才整维展开
        if (!result.some((row) => !row.__refs[specKey])) {
          continue;
        }
      } else {
        // 已有规格：只为新增的取值补行
        const hasNewValue = spec.values.some((v) => !oldValues.has(valueId(spec, v)));
        if (!hasNewValue) {
          continue;
        }
      }

      const addedValues = oldValues ? spec.values.filter((v) => !oldValues.has(valueId(spec, v))) : spec.values;
      const next: ComboRow[] = [];
      for (const row of result) {
        const currentRef = row.__refs[specKey];
        if (!currentRef) {
          // 缺这一维：按该规格全部取值分支。
          // 主键只留在第一支上——它对应库里已有的那行，后端按主键匹配后原地更新，从而保住 SKU 主键。
          spec.values.forEach((value, valueIndex) => {
            const refs = { ...row.__refs, [specKey]: valueId(spec, value) };
            const { name, key } = describe(specs, refs);
            next.push({
              ...row,
              __rowKey: key,
              __refs: refs,
              __name: name,
              id: valueIndex === 0 ? row.id : undefined,
            } as ComboRow);
            expanded++;
          });
        } else {
          next.push(row);
          // 已有这一维，但该规格新增了取值：照抄本行价格/库存等，补出对应组合
          for (const value of addedValues) {
            const vid = valueId(spec, value);
            if (vid === currentRef) {
              continue;
            }
            const refs = { ...row.__refs, [specKey]: vid };
            const { name, key } = describe(specs, refs);
            next.push({ ...row, __rowKey: key, __refs: refs, __name: name, id: undefined } as ComboRow);
            expanded++;
          }
        }
      }
      result = next;
    }

    // 重算行键并去重：删掉某个规格后，原本靠该规格区分的行可能变得完全相同
    const seen = new Set<string>();
    const deduped: ComboRow[] = [];
    for (const row of result) {
      const { name, key } = describe(specs, row.__refs);
      const rowKey = key || row.__rowKey;
      if (seen.has(rowKey)) {
        continue;
      }
      seen.add(rowKey);
      deduped.push({ ...row, __rowKey: rowKey, __name: name });
    }

    rows.value = deduped;
    selectedKeys.value = [];
    clampPage();
    return expanded;
  }

  /** 表格里的内联编辑（编码/售价/库存/售卖状态）变化后立即回传父组件 */
  function onCellChange() {
    sync();
  }

  /** 删除后把当前页夹回有效范围，避免停在空白页 */
  function clampPage() {
    const maxPage = Math.max(1, Math.ceil(rows.value.length / pageSize.value));
    if (pageNo.value > maxPage) {
      pageNo.value = maxPage;
    }
  }

  /** 删除单条组合：仅从本地集合移除，保存时由后端增量逻辑真正删掉该行 */
  function removeRow(row: ComboRow) {
    rows.value = rows.value.filter((item) => item.__rowKey !== row.__rowKey);
    selectedKeys.value = selectedKeys.value.filter((key) => key !== row.__rowKey);
    clampPage();
    sync();
    createMessage.success(`已删除组合「${row.__name}」`);
  }

  /** 批量删除勾选的组合 */
  function removeSelected() {
    const keys = new Set(selectedKeys.value);
    if (!keys.size) {
      return;
    }
    const removed = rows.value.filter((item) => keys.has(item.__rowKey)).length;
    rows.value = rows.value.filter((item) => !keys.has(item.__rowKey));
    selectedKeys.value = [];
    clampPage();
    sync();
    createMessage.success(`已删除 ${removed} 个组合`);
  }

  /** SKU 配图上传完成：写回该行并同步给父组件 */
  function onImageChange(row: ComboRow, val: any) {
    row.imageUrl = typeof val === 'string' ? val : '';
    sync();
  }

  /** 同步给父组件（去内部字段，重建组合键） */
  function sync(notify = true) {
    const specs = normalizedSpecs.value;
    /*
     * 防御：本地行还是空的，但父级已经有组合 —— 说明规格/组合尚未装载完成，
     * 此时上报空数组会把父级已加载的组合直接清掉（与"新增规格导致 SKU 全没了"是同一类事故）。
     * 宁可不上报，等真实的 rows 建好之后再说。
     */
    if (!rows.value.length && (props.combinations || []).length) {
      return;
    }
    /*
     * 另外这里**刻意不做"任一分量为空就丢掉整行"的过滤**。
     * 旧版写成 .filter(specValueRefs.every(Boolean))，结果新增一个规格后，
     * 已有行缺少新规格的值 → 全部被过滤 → 上报空数组 → SKU 组合集体消失（数据丢失）。
     * 现在一律原样上报，不完整的行由提交前校验拦下并明确提示，绝不静默丢弃。
     */
    const payload = rows.value.map((row) => {
      const { name, key } = describe(specs, row.__refs);
      return {
        id: row.id,
        skuCode: row.skuCode || null,
        combinationKey: key,
        combinationName: name,
        specValueRefs: specs.map((spec) => row.__refs[spec.id || spec.name] || null),
        price: row.price === '' || row.price === undefined ? null : row.price,
        stock: Number(row.stock) || 0,
        imageUrl: row.imageUrl || null,
        saleable: row.saleable || 'Y',
      };
    });

    emit('update:combinations', payload);
    syncingLocal = true;
    nextTick(() => {
      syncingLocal = false;
    });
    if (notify) {
      emit('change', payload);
    }
  }

  /** 生成完整笛卡尔积 */
  function generateCartesian() {
    const specs = normalizedSpecs.value;
    if (!specs.length) return;
    const existing = new Map(rows.value.map((row) => [row.__rowKey, row]));
    const result: ComboRow[] = [];

    const walk = (index: number, refs: Record<string, string>) => {
      if (index === specs.length) {
        const { name, key } = describe(specs, refs);
        const old = existing.get(key);
        result.push(
          old
            ? { ...old, __rowKey: key, __name: name, __refs: { ...refs } }
            : {
                __rowKey: key,
                __refs: { ...refs },
                __name: name,
                skuCode: '',
                price: null,
                stock: 0,
                imageUrl: '',
                saleable: 'Y',
              }
        );
        return;
      }
      const spec = specs[index];
      const specKey = spec.id || spec.name;
      spec.values.forEach((value) => {
        walk(index + 1, { ...refs, [specKey]: valueId(spec, value) });
      });
    };
    walk(0, {});

    rows.value = result;
    pageNo.value = 1;
    selectedKeys.value = [];
    sync();
    createMessage.success(`已生成 ${result.length} 个组合`);
  }

  /** 只补齐缺失的组合，保留已有行的价格 / 库存 / 状态 */
  function regenerateMissing() {
    const specs = normalizedSpecs.value;
    if (!specs.length) return;
    const existing = new Map(rows.value.map((row) => [row.__rowKey, row]));
    const before = existing.size;
    const result: ComboRow[] = [];
    const walk = (index: number, refs: Record<string, string>) => {
      if (index === specs.length) {
        const { name, key } = describe(specs, refs);
        const old = existing.get(key);
        result.push(
          old
            ? { ...old, __rowKey: key, __name: name, __refs: { ...refs } }
            : { __rowKey: key, __refs: { ...refs }, __name: name, skuCode: '', price: null, stock: 0, imageUrl: '', saleable: 'Y' }
        );
        return;
      }
      const spec = specs[index];
      const specKey = spec.id || spec.name;
      spec.values.forEach((value) => walk(index + 1, { ...refs, [specKey]: valueId(spec, value) }));
    };
    walk(0, {});
    rows.value = result;
    sync();
    createMessage.success(`补齐完成，新增 ${result.length - before} 个组合`);
  }

  /** 手工改了某个规格的下拉值：重算组合键；若与其它行重复则回滚，避免界面与数据不一致 */
  function onRefChange(row: ComboRow) {
    const specs = normalizedSpecs.value;
    const { name, key } = describe(specs, row.__refs);
    const duplicated = rows.value.some((item) => item !== row && item.__rowKey === key);
    if (duplicated) {
      createMessage.warning(`组合「${name || key}」已存在，请选择不同的规格值`);
      // 回滚为原来的规格值组合
      row.__refs = refsFromKey(specs, row.__rowKey);
      return;
    }
    row.__rowKey = key;
    row.__name = name;
    sync();
  }

  /**
   * 由组合键还原 { 规格key: 规格值id }。
   * 组合键是"规格值id按规格顺序用 - 连接"，但 id 本身不含 -，所以可直接按 - 拆分。
   */
  function refsFromKey(specs: SpecItem[], key: string): Record<string, string> {
    const parts = (key || '').split('-');
    const refs: Record<string, string> = {};
    specs.forEach((spec, index) => {
      refs[spec.id || spec.name] = parts[index] || '';
    });
    return refs;
  }

  // ---------------- 单条新增组合 ----------------
  const addPanelVisible = ref(false);
  const newCombo = ref<Record<string, string>>({});
  const newComboPrice = ref<number | null>(null);
  const newComboStock = ref<number | null>(0);

  function toggleAddPanel() {
    addPanelVisible.value = !addPanelVisible.value;
    if (addPanelVisible.value) {
      resetNewCombo();
    }
  }

  /** 默认选中每个规格的第一个可选值，减少点击次数 */
  function resetNewCombo() {
    const draft: Record<string, string> = {};
    normalizedSpecs.value.forEach((spec) => {
      const specKey = spec.id || spec.name;
      const first = spec.values[0];
      if (first) {
        draft[specKey] = valueId(spec, first);
      }
    });
    newCombo.value = draft;
    newComboPrice.value = null;
    newComboStock.value = 0;
  }

  /** 把面板里选好的一条组合加入表格 */
  function addSingleCombination() {
    const specs = normalizedSpecs.value;
    if (!specs.length) return;

    const refs: Record<string, string> = {};
    for (const spec of specs) {
      const specKey = spec.id || spec.name;
      const picked = newCombo.value[specKey];
      if (!picked) {
        createMessage.warning(`请选择「${spec.name}」`);
        return;
      }
      refs[specKey] = picked;
    }

    const { name, key } = describe(specs, refs);
    if (rows.value.some((row) => row.__rowKey === key)) {
      createMessage.warning(`组合「${name}」已存在，请直接在上方表格里修改它`);
      return;
    }

    const row: ComboRow = {
      __rowKey: key,
      __refs: { ...refs },
      __name: name,
      skuCode: '',
      price: newComboPrice.value === null || newComboPrice.value === undefined ? null : newComboPrice.value,
      stock: Number(newComboStock.value) || 0,
      imageUrl: '',
      saleable: 'Y',
    };

    // 追加到表格末尾并跳到最后一页，让用户立刻看到新增的行
    rows.value.push(row);
    pageNo.value = Math.max(1, Math.ceil(rows.value.length / pageSize.value));
    sync();
    createMessage.success(`已加入组合「${name}」`);

    // 重置以便连续录入下一条
    resetNewCombo();
  }

  // ---------------- 批量填充 ----------------
  const bulkField = ref<'price' | 'stock' | 'skuCode' | 'imageUrl'>('price');
  const bulkNumber = ref<number | null>(null);
  const bulkText = ref('');

  const bulkFieldOptions = [
    { label: '售价', value: 'price' },
    { label: '库存', value: 'stock' },
    { label: 'SKU编码前缀', value: 'skuCode' },
    { label: 'SKU配图URL', value: 'imageUrl' },
  ];

  function applyBulk() {
    if (!rows.value.length) return;
    if (bulkField.value === 'price') {
      if (bulkNumber.value === null || bulkNumber.value === undefined) {
        createMessage.warning('请输入要填充的售价');
        return;
      }
      rows.value.forEach((row) => (row.price = bulkNumber.value));
    } else if (bulkField.value === 'stock') {
      if (bulkNumber.value === null || bulkNumber.value === undefined) {
        createMessage.warning('请输入要填充的库存');
        return;
      }
      rows.value.forEach((row) => (row.stock = bulkNumber.value));
    } else if (bulkField.value === 'skuCode') {
      const prefix = (bulkText.value || '').trim();
      if (!prefix) {
        createMessage.warning('请输入 SKU 编码前缀');
        return;
      }
      rows.value.forEach((row, index) => {
        row.skuCode = `${prefix}-${String(index + 1).padStart(3, '0')}`;
      });
    } else {
      if (!bulkText.value) {
        createMessage.warning('请输入图片URL');
        return;
      }
      rows.value.forEach((row) => (row.imageUrl = bulkText.value));
    }
    sync();
    createMessage.success('批量填充完成');
  }

  function setAllSaleable(flag: 'Y' | 'N') {
    rows.value.forEach((row) => (row.saleable = flag));
    sync();
    createMessage.success(flag === 'Y' ? '已全部标记为可售' : '已全部标记为不发售');
  }

  defineExpose({ generateCartesian, sync });
</script>

<style lang="less" scoped>
  .sku-editor {
    &__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
      margin-bottom: 12px;
    }

    &__stats {
      color: var(--shop-text-secondary);
      font-size: 13px;

      strong {
        color: var(--shop-text);
      }
    }

    &__alert {
      margin-bottom: 12px;
    }

    &__bulk {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      padding: 10px 12px;
      margin-bottom: 12px;
      background: var(--shop-surface-muted);
      border: 1px solid var(--shop-border);
      border-radius: 8px;
    }

    &__bulk-label {
      font-weight: 500;
      color: var(--shop-text);
    }

    &__bulk-field {
      min-width: 150px;
    }

    &__bulk-input {
      width: 150px;
    }

    &__cell-select {
      width: 100%;
    }

    &__cell-number {
      width: 100%;
    }

    /* SKU 配图：行内只放一个小按钮，上传组件放在弹层里，因此行高不受影响 */
    &__image-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 36px;
      height: 26px;
      padding: 0 6px;
    }

    &__image-btn-text {
      font-size: 12px;
    }

    &__thumb {
      width: 22px;
      height: 22px;
      object-fit: cover;
      border-radius: 3px;
      border: 1px solid var(--shop-border);
      flex: none;
      display: block;
    }

    &__upload-pop {
      width: 200px;
    }

    &__upload-hint {
      margin-top: 8px;
      font-size: 12px;
      line-height: 1.6;
      color: var(--shop-text-secondary);
    }

    :deep(.sku-row-unsaleable) {
      background-color: var(--shop-surface-muted);

      td {
        color: var(--shop-text-secondary);
      }
    }
  }

  /* 单条新增组合面板：日常最高频操作，做成一行内联表单 */
  .sku-add-panel {
    padding: 12px 14px;
    margin-bottom: 12px;
    background: #f6faff;
    border: 1px solid #d6e8ff;
    border-radius: 8px;

    &__title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 600;
      color: var(--shop-text);
      margin-bottom: 10px;

      .anticon {
        color: var(--shop-primary);
      }
    }

    &__row {
      display: flex;
      align-items: flex-end;
      gap: 10px;
      flex-wrap: wrap;
    }

    &__field {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    &__label {
      font-size: 12px;
      color: var(--shop-text-secondary);
    }

    &__select {
      min-width: 130px;
    }

    &__number {
      width: 130px;
    }

    &__actions {
      display: flex;
      gap: 8px;
      margin-left: auto;
    }

    &__hint {
      margin-top: 8px;
      font-size: 12px;
      color: var(--shop-text-secondary);
    }
  }
</style>
