<template>
  <div class="spec-editor">
    <!-- 工具条 -->
    <div class="spec-editor__toolbar">
      <a-space :size="8" wrap>
        <a-button type="primary" ghost preIcon="ant-design:plus-outlined" :disabled="disabled" @click="addSpec">添加规格</a-button>
        <a-button preIcon="ant-design:import-outlined" :disabled="disabled || !presetOptions.length" @click="presetVisible = true">从预设库导入</a-button>
      </a-space>
      <span class="spec-editor__hint">
        每个规格一个可选值列表；<strong>规格值顺序</strong>决定店铺端规格选择器与组合的唯一键顺序，可拖拽调整。
        规格值不需要配图——图片配在具体 SKU 上更准确。
      </span>
    </div>

    <a-empty v-if="!specs.length" :image="simpleImage" description="还没有规格，点「添加规格」或从预设库导入" />

    <!-- vuedraggable v4 必须使用 #item 插槽，默认插槽不会被读取 -->
    <VueDraggable
      :list="localSpecs"
      :item-key="(item) => item.__uid"
      :disabled="disabled"
      handle=".spec-card__handle"
      :animation="180"
      ghost-class="spec-card--ghost"
      class="spec-editor__list"
      @end="onDragEnd"
    >
      <template #item="{ element: spec, index: specIndex }">
        <div class="spec-card">
          <div class="spec-card__head">
            <Icon icon="ant-design:holder-outlined" class="spec-card__handle" :class="{ 'is-disabled': disabled }" title="拖拽调整规格顺序" />
            <a-input
              v-model:value="spec.name"
              class="spec-card__name"
              placeholder="规格名，如 颜色 / 内存 / 存储"
              :disabled="disabled"
              allow-clear
              @blur="onSpecMetaChange"
            />
            <a-tag v-if="spec.presetCode" color="blue" class="spec-card__preset">预设：{{ spec.presetCode }}</a-tag>
            <span class="spec-card__count">{{ (spec.values || []).length }} 个可选值</span>
            <!--
              删除规格会级联移除所有引用它的 SKU 组合（价格/库存/编码一起丢），
              必须先把影响条数说清楚再让用户确认，绝不一点就删。
            -->
            <a-popconfirm
              :title="`删除规格「${spec.name || '未命名'}」？`"
              :description="specDeleteImpact(spec)"
              ok-text="确认删除"
              cancel-text="取消"
              :disabled="disabled"
              @confirm="removeSpec(specIndex)"
            >
              <a-button type="text" danger size="small" :disabled="disabled">
                <Icon icon="ant-design:delete-outlined" />
              </a-button>
            </a-popconfirm>
          </div>

          <!-- 规格值：拖拽排序 + 配图，同样必须用 #item 插槽 -->
          <VueDraggable
            :list="spec.values"
            :item-key="(item) => item.__uid"
            :disabled="disabled"
            handle=".spec-value__handle"
            :animation="160"
            ghost-class="spec-value--ghost"
            class="spec-value-list"
            @end="onDragEnd"
          >
            <template #item="{ element: value, index: valueIndex }">
              <div class="spec-value">
                <Icon icon="ant-design:holder-outlined" class="spec-value__handle" :class="{ 'is-disabled': disabled }" title="拖拽调整可选值顺序" />
                <a-input
                  v-model:value="value.value"
                  class="spec-value__input"
                  placeholder="可选值，如 白色"
                  size="small"
                  :disabled="disabled"
                  allow-clear
                  @blur="onSpecMetaChange"
                />
                <a-popconfirm
                  :title="`删除可选值「${value.value || '未命名'}」？`"
                  :description="valueDeleteImpact(spec, value)"
                  ok-text="确认删除"
                  cancel-text="取消"
                  :disabled="disabled"
                  @confirm="removeValue(spec, valueIndex)"
                >
                  <a-button type="text" size="small" danger :disabled="disabled">
                    <Icon icon="ant-design:close-outlined" />
                  </a-button>
                </a-popconfirm>
              </div>
            </template>
          </VueDraggable>

          <a-button
            type="dashed"
            size="small"
            block
            class="spec-value-add"
            preIcon="ant-design:plus-outlined"
            :disabled="disabled"
            @click="addValue(spec)"
          >
            添加可选值
          </a-button>
        </div>
      </template>
    </VueDraggable>

    <a-alert
      v-if="duplicateNames.length"
      type="warning"
      show-icon
      class="spec-editor__alert"
      :message="`存在重复的规格名：${duplicateNames.join('、')}，请修改后再保存`"
    />

    <!-- 预设库导入 -->
    <a-modal v-model:open="presetVisible" title="从预设库导入规格" width="620px" :footer="null" destroy-on-close>
      <a-spin :spinning="presetLoading">
        <a-empty v-if="!presetOptions.length" :image="simpleImage" description="预设库暂无数据，可先在数据库 ck_shop_options 中维护" />
        <div v-else class="preset-list">
          <label v-for="item in presetOptions" :key="item.id" class="preset-item">
            <a-checkbox v-model:checked="item.__checked" :disabled="disabled" />
            <div class="preset-item__body">
              <div class="preset-item__title">
                {{ item.optionName || item.optionCode }}
                <span class="preset-item__code">{{ item.optionCode }}</span>
              </div>
              <div class="preset-item__values">{{ parsePresetValues(item).join('、') || '（无预设值）' }}</div>
            </div>
          </label>
        </div>
      </a-spin>
      <div class="preset-footer">
        <a-button @click="presetVisible = false">取消</a-button>
        <a-button type="primary" :disabled="!checkedPresetCount" @click="applyPresets">导入选中（{{ checkedPresetCount }}）</a-button>
      </div>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { Empty } from 'ant-design-vue';
  import VueDraggable from 'vuedraggable';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { optionsAll } from '../ShopProduct.api';

  /**
   * 规格编辑器
   * - 规格与可选值都支持拖拽排序（顺序即提交顺序，决定 combinationKey 的拼接顺序）
   * - 支持从 ck_shop_options 预设库一键导入规格及可选值
   * - 规格值不再配图：图片配在具体 SKU 上更准确（SKU 组合页签已支持）
   */
  interface SpecValueItem {
    id?: string;
    tempId?: string;
    value: string;
    sortOrder?: number;
    __uid: string;
  }

  interface SpecItem {
    id?: string;
    name: string;
    presetCode?: string;
    sortOrder?: number;
    isRequired?: string;
    values: SpecValueItem[];
    __uid: string;
  }

  const props = withDefaults(
    defineProps<{
      specs: SpecItem[];
      /** 当前 SKU 组合：只用于"删除规格/规格值会影响几个组合"的确认提示 */
      combinations?: any[];
      disabled?: boolean;
    }>(),
    {
      specs: () => [],
      combinations: () => [],
      disabled: false,
    }
  );

  const emit = defineEmits(['update:specs', 'change']);

  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;
  const { createMessage } = useMessage();

  const localSpecs = ref<SpecItem[]>([]);

  /** 稳定唯一键：编辑已有数据时优先使用后端主键，新增时用随机串 */
  function uid(prefix: string) {
    return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
  }

  function normalize(list: any[]): SpecItem[] {
    return (list || []).map((spec) => ({
      ...spec,
      __uid: spec.__uid || uid('spec'),
      values: (spec.values || []).map((value: any) => ({
        ...value,
        __uid: value.__uid || value.id || uid('val'),
      })),
    }));
  }

  /** 内容签名：用于判断外部规格数据是否真的变了（引用比较不可靠） */
  function signature(list: any[]): string {
    return JSON.stringify(
      (list || []).map((spec: any) => ({
        id: spec?.id,
        name: spec?.name,
        values: (spec?.values || []).map((v: any) => ({ id: v?.id, value: v?.value })),
      }))
    );
  }

  let lastIncomingSignature = '';

  watch(
    () => props.specs,
    (val) => {
      // 用内容签名判断，而不是引用比较：
      // 父组件 Object.assign / 复用响应式代理时引用可能不变，引用比较会导致规格不刷新，
      // 表现为"编辑时规格配置显示为空"。
      const next = signature(val);
      if (next === lastIncomingSignature) {
        return;
      }
      lastIncomingSignature = next;
      localSpecs.value = normalize(val);
    },
    { immediate: true, deep: false }
  );

  const duplicateNames = computed(() => {
    const seen = new Map<string, number>();
    localSpecs.value.forEach((spec) => {
      const name = (spec.name || '').trim();
      if (!name) return;
      seen.set(name, (seen.get(name) || 0) + 1);
    });
    return [...seen.entries()].filter(([, count]) => count > 1).map(([name]) => name);
  });

  /**
   * 同步给父组件：去掉内部临时字段，重排 sortOrder。
   *
   * 关键：每个规格值都要带一个**稳定标识 tempId**。
   * 原因：新增商品时规格值还没有后端主键，SKU 组合要靠这个标识去引用它；
   * 若这里输出 undefined，SKU 编辑器会退化成 "规格名::值" 这种脆弱的兜底键，
   * 后端解析不到就会导致"保存成功但 SKU 组合为空"。
   * 已有后端主键时优先回传主键，保证与详情接口返回的 specValueIds 完全对齐。
   */
  function sync(notify = true) {
    const clean = localSpecs.value.map((spec, specIndex) => ({
      id: spec.id,
      name: spec.name,
      presetCode: spec.presetCode,
      isRequired: spec.isRequired || 'Y',
      sortOrder: specIndex,
      values: (spec.values || []).map((value, valueIndex) => {
        const stableKey = value.id || value.tempId || value.__uid;
        // 把稳定标识回写到本地对象，后续 sync 不会漂移
        value.tempId = stableKey;
        return {
          id: value.id,
          tempId: stableKey,
          value: value.value,
          sortOrder: valueIndex,
        };
      }),
    }));
    emit('update:specs', clean);
    // 记住刚同步出去的内容，避免父级回传同一份数据时又把本地状态重建一遍
    lastIncomingSignature = signature(clean);
    if (notify) {
      emit('change', clean);
    }
  }

  function addSpec() {
    localSpecs.value.push({
      name: '',
      isRequired: 'Y',
      values: [],
      __uid: uid('spec'),
    });
    sync();
  }

  /**
   * 一个规格值可能被组合用三种键引用：后端主键 / 稳定 tempId / 本地 __uid。
   * 全部收集起来，避免"删了但算不出影响条数"。
   */
  function valueKeys(value: any): string[] {
    return [value?.id, value?.tempId, value?.__uid].filter((key) => !!key).map((key) => String(key));
  }

  /** 有多少个 SKU 组合引用了这些规格值 */
  function combosReferencing(keys: Set<string>): number {
    if (!keys.size) {
      return 0;
    }
    return (props.combinations || []).filter((combo: any) =>
      (combo?.specValueRefs || []).some((ref: string) => keys.has(ref))
    ).length;
  }

  function specDeleteImpact(spec: SpecItem): string {
    const keys = new Set<string>();
    (spec.values || []).forEach((value) => valueKeys(value).forEach((key) => keys.add(key)));
    const count = combosReferencing(keys);
    return count > 0
      ? `将同时移除引用它的 ${count} 个 SKU 组合，价格、库存与 SKU 编码会一起丢失。`
      : '当前没有 SKU 组合引用该规格。';
  }

  function valueDeleteImpact(_spec: SpecItem, value: SpecValueItem): string {
    const count = combosReferencing(new Set(valueKeys(value)));
    return count > 0 ? `将同时移除引用它的 ${count} 个 SKU 组合。` : '当前没有 SKU 组合引用该可选值。';
  }

  function removeSpec(index: number) {
    const spec = localSpecs.value[index];
    if (!spec) return;
    localSpecs.value.splice(index, 1);
    sync();
  }

  function addValue(spec: SpecItem) {
    spec.values = spec.values || [];
    spec.values.push({ value: '', sortOrder: spec.values.length, __uid: uid('val') });
    sync();
  }

  function removeValue(spec: SpecItem, index: number) {
    spec.values.splice(index, 1);
    sync();
  }

  function onSpecMetaChange() {
    sync();
  }

  function onDragEnd() {
    sync();
  }

  // ---------------- 预设库 ----------------
  const presetVisible = ref(false);
  const presetLoading = ref(false);
  const presetOptions = ref<any[]>([]);

  function parsePresetValues(item: any): string[] {
    const raw = item?.optionValue;
    if (!raw) return [];
    return String(raw)
      .split(/[,，、|]/)
      .map((v) => v.trim())
      .filter(Boolean);
  }

  const checkedPresetCount = computed(() => presetOptions.value.filter((item) => item.__checked).length);

  async function openPreset() {
    presetVisible.value = true;
    if (presetOptions.value.length) return;
    presetLoading.value = true;
    try {
      const res = await optionsAll();
      const list = Array.isArray(res) ? res : res?.records || [];
      presetOptions.value = list.map((item: any) => ({ ...item, __checked: false }));
    } catch (e) {
      createMessage.warning('预设库加载失败');
    } finally {
      presetLoading.value = false;
    }
  }

  function applyPresets() {
    const picked = presetOptions.value.filter((item) => item.__checked);
    if (!picked.length) return;
    picked.forEach((item) => {
      const name = item.optionName || item.optionCode;
      const values = parsePresetValues(item);
      localSpecs.value.push({
        name,
        presetCode: item.optionCode,
        isRequired: 'Y',
        values: values.map((value, index) => ({
          value,
          sortOrder: index,
          __uid: uid('val'),
        })),
        __uid: uid('spec'),
      });
    });
    presetOptions.value.forEach((item) => (item.__checked = false));
    presetVisible.value = false;
    sync();
    createMessage.success(`已导入 ${picked.length} 个规格`);
  }

  // 打开预设库时自动拉取
  watch(presetVisible, (val) => {
    if (val) {
      openPreset();
    }
  });

  defineExpose({ sync });
</script>

<style lang="less" scoped>
  .spec-editor {
    &__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
      margin-bottom: 12px;
    }

    &__hint {
      color: rgba(0, 0, 0, 0.45);
      font-size: 12px;
    }

    &__list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    &__alert {
      margin-top: 12px;
    }
  }

  .spec-card {
    border: 1px solid #f0f0f0;
    border-radius: 8px;
    padding: 12px 14px 10px;
    background: #fff;
    transition:
      border-color 200ms ease,
      box-shadow 200ms ease;

    &:hover {
      border-color: #d9d9d9;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
    }

    &--ghost {
      opacity: 0.5;
      border-style: dashed;
    }

    &__head {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 10px;
    }

    &__handle {
      color: rgba(0, 0, 0, 0.3);
      cursor: grab;
      flex: none;

      &.is-disabled {
        cursor: not-allowed;
        opacity: 0.4;
      }

      &:active {
        cursor: grabbing;
      }
    }

    &__name {
      max-width: 260px;
    }

    &__preset {
      margin-inline-end: 0;
    }

    &__count {
      margin-left: auto;
      color: rgba(0, 0, 0, 0.45);
      font-size: 12px;
    }
  }

  .spec-value-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .spec-value {
    display: flex;
    align-items: center;
    gap: 4px;
    border: 1px solid #f0f0f0;
    border-radius: 6px;
    padding: 4px 4px 4px 6px;
    background: #fafafa;
    transition: background-color 180ms ease;

    &:hover {
      background: #fff;
    }

    &--ghost {
      opacity: 0.5;
      border-style: dashed;
    }

    &__handle {
      color: rgba(0, 0, 0, 0.3);
      cursor: grab;
      flex: none;

      &.is-disabled {
        cursor: not-allowed;
        opacity: 0.4;
      }
    }

    &__input {
      width: 160px;
    }
  }

  .spec-value-add {
    margin-top: 10px;
  }

  .preset-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 380px;
    overflow-y: auto;
  }

  .preset-item {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 8px 10px;
    border: 1px solid #f0f0f0;
    border-radius: 6px;
    cursor: pointer;
    transition: background-color 160ms ease;

    &:hover {
      background: #fafafa;
    }

    &__body {
      min-width: 0;
    }

    &__title {
      font-weight: 500;
      color: rgba(0, 0, 0, 0.88);
    }

    &__code {
      margin-left: 6px;
      font-size: 12px;
      font-weight: 400;
      color: rgba(0, 0, 0, 0.45);
    }

    &__values {
      font-size: 12px;
      color: rgba(0, 0, 0, 0.65);
      word-break: break-all;
    }
  }

  .preset-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 16px;
  }
</style>
