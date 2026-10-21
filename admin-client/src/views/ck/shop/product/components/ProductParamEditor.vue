<template>
  <div ref="rootRef" class="param-editor">
    <!-- 工具条 -->
    <div class="param-editor__toolbar">
      <a-space :size="8" wrap>
        <a-button v-if="!disabled" type="primary" ghost preIcon="ant-design:plus-outlined" :disabled="params.length >= limit" @click="addParam">
          添加参数
        </a-button>
        <a-button v-if="!disabled" preIcon="ant-design:import-outlined" :disabled="!specs.length" @click="generateFromSpecs">
          从规格配置生成
        </a-button>
        <a-button v-if="!disabled" preIcon="ant-design:copy-outlined" :disabled="!params.length" @click="openCopy">复制到其它语言</a-button>
      </a-space>
      <span class="param-editor__hint">
        共 <strong>{{ params.length }}</strong> / {{ limit }} 条；参数名和参数值都必填，顺序即店铺端展示顺序
      </span>
    </div>

    <a-alert
      v-if="!disabled && !params.length"
      type="info"
      show-icon
      class="param-editor__alert"
      message="一行 = 一个参数名 + 一个参数值"
      description="例如「屏幕尺寸 / 6.7 英寸」。也可以点「从规格配置生成」，把「规格配置」页签里的规格名与可选值一键追加进来（重名会跳过）。"
    />

    <a-empty v-if="!params.length" :image="simpleImage" description="还没有规格参数，点「添加参数」手动录入，或用「从规格配置生成」" />

    <div v-else class="param-list">
      <div
        v-for="(param, index) in params"
        :key="param.__uid || index"
        class="param-row"
        :class="{ 'is-invalid': !String(param.paramName || '').trim() || !String(param.paramValue || '').trim() }"
        :data-param-uid="param.__uid"
      >
        <span class="param-row__index">{{ index + 1 }}</span>
        <a-input
          v-model:value="param.paramName"
          class="param-row__name"
          placeholder="参数名，如 屏幕尺寸"
          allow-clear
          :maxlength="100"
          :disabled="disabled"
        />
        <a-input
          v-model:value="param.paramValue"
          class="param-row__value"
          placeholder="参数值，如 6.7 英寸"
          allow-clear
          :maxlength="500"
          :disabled="disabled"
        />
        <div v-if="!disabled" class="param-row__tools">
          <a-button
            type="text"
            size="small"
            :disabled="disabled || index === 0"
            title="上移一行"
            :aria-label="`将第 ${index + 1} 条参数上移`"
            @click="moveParam(index, -1)"
          >
            <Icon icon="ant-design:arrow-up-outlined" />
          </a-button>
          <a-button
            type="text"
            size="small"
            :disabled="disabled || index === params.length - 1"
            title="下移一行"
            :aria-label="`将第 ${index + 1} 条参数下移`"
            @click="moveParam(index, 1)"
          >
            <Icon icon="ant-design:arrow-down-outlined" />
          </a-button>
          <a-popconfirm v-if="!disabled" title="删除这条参数？" ok-text="删除" cancel-text="取消" @confirm="removeParam(index)">
            <a-button type="text" size="small" danger title="删除这条参数" :aria-label="`删除第 ${index + 1} 条参数`">
              <Icon icon="ant-design:delete-outlined" />
            </a-button>
          </a-popconfirm>
        </div>
      </div>
    </div>

    <a-alert
      v-if="incompleteCount"
      type="warning"
      show-icon
      class="param-editor__alert"
      :message="`还有 ${incompleteCount} 行未填完整，保存时会被拦下；参数名和参数值都不能为空`"
    />

    <!-- 复制到其它语言 -->
    <a-modal v-model:open="copyVisible" title="复制到其它语言" ok-text="复制" :ok-button-props="{ disabled: !copyTarget }" @ok="confirmCopy">
      <p class="copy-modal__line">
        当前语言共有 <strong>{{ params.length }}</strong> 条参数，将整体复制到目标语言。
      </p>
      <a-select v-model:value="copyTarget" :options="copyTargetOptions" placeholder="选择目标语言" style="width: 100%" />
      <p class="copy-modal__warn">目标语言已有参数时会被整体覆盖，操作不可撤销；复制后仍可分别编辑。</p>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, ref } from 'vue';
  import { Empty } from 'ant-design-vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { DETAIL_PARAM_LIMIT, uid, type ProductParamItem } from './productDetailMeta';

  /**
   * 规格参数 KV 行编辑器（一种语言一份）
   *
   * 数据流与详情编辑器一致：文本直接 v-model 改对象（父组件与预览共享同一批对象），
   * 结构变化（新增/删除/排序）通过 update:params 交回父组件合并。
   */
  const props = withDefaults(
    defineProps<{
      params: ProductParamItem[];
      languageCode: string;
      /** 启用语言列表，用于「复制到其它语言」 */
      languages?: any[];
      /** 当前 formData.specs，用于「从规格配置生成」 */
      specs?: any[];
      disabled?: boolean;
    }>(),
    {
      params: () => [],
      languages: () => [],
      specs: () => [],
      disabled: false,
    }
  );

  const emit = defineEmits(['update:params', 'copy']);

  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;
  const { createMessage } = useMessage();
  const limit = DETAIL_PARAM_LIMIT;

  const rootRef = ref<HTMLElement | null>(null);

  function commit(next: ProductParamItem[]) {
    emit(
      'update:params',
      next.map((param, index) => ({ ...param, __uid: param.__uid || uid('prm'), sortOrder: index }))
    );
  }

  const incompleteCount = computed(
    () => props.params.filter((param) => !String(param.paramName || '').trim() || !String(param.paramValue || '').trim()).length
  );

  function addParam() {
    if (props.disabled) return;
    if (props.params.length >= limit) {
      createMessage.warning(`每种语言最多 ${limit} 条参数`);
      return;
    }
    const param: ProductParamItem = {
      languageCode: props.languageCode,
      paramName: '',
      paramValue: '',
      sortOrder: props.params.length,
      __uid: uid('prm'),
    };
    commit([...props.params, param]);
    nextTick(() => focusRow(param.__uid));
  }

  function removeParam(index: number) {
    if (props.disabled) return;
    commit(props.params.filter((_param, i) => i !== index));
  }

  function moveParam(index: number, delta: number) {
    if (props.disabled) return;
    const target = index + delta;
    if (target < 0 || target >= props.params.length) return;
    const next = props.params.slice();
    const [moved] = next.splice(index, 1);
    next.splice(target, 0, moved);
    commit(next);
  }

  /**
   * 从规格配置生成：规格名 → 参数名，可选值用「 / 」连接 → 参数值。
   * 不覆盖已有行；参数名重复（含同批规格之间重复）跳过；没有可选值的规格也跳过。
   */
  function generateFromSpecs() {
    if (props.disabled) return;
    if (!props.specs.length) {
      createMessage.info('「规格配置」页签还没有规格，无法生成');
      return;
    }
    const existingNames = new Set(props.params.map((param) => String(param.paramName || '').trim()).filter(Boolean));
    let duplicateSkipped = 0;
    let emptySkipped = 0;
    const appended: ProductParamItem[] = [];
    props.specs.forEach((spec: any) => {
      const name = String(spec?.name || '').trim();
      if (!name) {
        emptySkipped++;
        return;
      }
      if (existingNames.has(name)) {
        duplicateSkipped++;
        return;
      }
      const values = (spec?.values || []).map((value: any) => String(value?.value || '').trim()).filter(Boolean);
      if (!values.length) {
        emptySkipped++;
        return;
      }
      existingNames.add(name);
      appended.push({
        languageCode: props.languageCode,
        paramName: name,
        paramValue: values.join(' / '),
        sortOrder: props.params.length + appended.length,
        __uid: uid('prm'),
      });
    });
    if (!appended.length) {
      createMessage.info('没有可生成的参数：参数名都已存在，或规格还没有可选值');
      return;
    }
    if (props.params.length + appended.length > limit) {
      createMessage.warning(`生成后将超过 ${limit} 条上限，请先删除部分参数再试`);
      return;
    }
    commit([...props.params, ...appended]);
    const skipped: string[] = [];
    if (duplicateSkipped) skipped.push(`${duplicateSkipped} 个重名`);
    if (emptySkipped) skipped.push(`${emptySkipped} 个无名称/无值`);
    createMessage.success(`已生成 ${appended.length} 条参数${skipped.length ? `，跳过 ${skipped.join('、')}` : ''}`);
  }

  // ---------------- 复制到其它语言 ----------------
  const copyVisible = ref(false);
  const copyTarget = ref<string | undefined>(undefined);

  const copyTargetOptions = computed(() =>
    (props.languages || [])
      .filter((lang: any) => lang && lang.code && lang.code !== props.languageCode)
      .map((lang: any) => ({ label: lang.name || lang.code, value: lang.code }))
  );

  function openCopy() {
    copyTarget.value = undefined;
    copyVisible.value = true;
  }

  function confirmCopy() {
    if (!copyTarget.value) return;
    emit('copy', copyTarget.value);
    copyVisible.value = false;
  }

  // ---------------- 聚焦 ----------------
  function focusRow(rowUid?: string) {
    if (!rowUid) return;
    nextTick(() => {
      const row = rootRef.value?.querySelector(`[data-param-uid="${rowUid}"]`) as HTMLElement | null;
      if (!row) return;
      row.scrollIntoView({ block: 'center', behavior: 'smooth' });
      const inputs = Array.from(row.querySelectorAll('input')) as HTMLInputElement[];
      const emptyInput = inputs.find((input) => !input.value.trim()) || inputs[0];
      emptyInput?.focus({ preventScroll: true });
    });
  }

  /** 提交校验失败时由父组件调用：聚焦第一条没填完整的参数行 */
  function focusFirstInvalid(): boolean {
    const index = props.params.findIndex((param) => !String(param.paramName || '').trim() || !String(param.paramValue || '').trim());
    if (index < 0) return false;
    focusRow(props.params[index].__uid);
    return true;
  }

  defineExpose({ focusFirstInvalid });
</script>

<style lang="less" scoped>
  .param-editor {
    &__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
      margin-bottom: 14px;
    }

    &__hint {
      color: rgba(0, 0, 0, 0.45);
      font-size: 12px;
      line-height: 1.6;

      strong {
        color: #1677ff;
        font-weight: 600;
      }
    }

    &__alert {
      margin-bottom: 12px;
    }
  }

  .param-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .param-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 10px;
    background: #fff;
    border: 1px solid #f0f0f0;
    border-radius: 8px;
    transition:
      border-color 180ms ease,
      box-shadow 180ms ease;

    &:hover {
      border-color: #d9d9d9;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
    }

    &.is-invalid {
      border-color: #ffccc7;
      background: #fffafa;
    }

    &__index {
      flex: 0 0 26px;
      text-align: center;
      color: rgba(0, 0, 0, 0.35);
      font-size: 12px;
      font-variant-numeric: tabular-nums;
    }

    &__name {
      flex: 0 1 200px;
      min-width: 120px;
    }

    &__value {
      flex: 1 1 auto;
      min-width: 0;
    }

    &__tools {
      flex: none;
      display: flex;
      align-items: center;
      gap: 2px;
    }
  }

  .copy-modal__line {
    margin: 0 0 12px;
    font-size: 13px;
    color: rgba(0, 0, 0, 0.65);
  }

  .copy-modal__warn {
    margin: 12px 0 0;
    font-size: 12px;
    line-height: 1.6;
    color: #d46b08;
  }

  @media (max-width: 900px) {
    .param-row {
      flex-wrap: wrap;

      &__index {
        flex: 0 0 auto;
      }

      &__name,
      &__value {
        flex: 1 1 100%;
      }

      &__tools {
        margin-left: auto;
      }
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .param-row {
      transition: none !important;
    }
  }
</style>
