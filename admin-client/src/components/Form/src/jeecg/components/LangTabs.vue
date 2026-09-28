<template>
  <div class="lang-tabs-shell">
    <div class="lang-tabs-card" :style="containerStyle">
      <!-- AntD 原生风格标题：左侧细蓝条 + 简洁文字，不做任何夸张装饰 -->
      <div v-if="title" class="lang-tabs-header">
        <span class="lang-tabs-bar"></span>
        <span class="lang-tabs-title">{{ title }}</span>
      </div>
      <a-tabs v-model:activeKey="activeKey" :type="tabsType" class="lang-tabs-ant">
        <a-tab-pane v-for="item in mergedList" :key="item.languageCode" :tab="item.__langName">
          <div class="lang-tabs-pane-inner">
            <slot :item="getBindableItem(item)" :lang="currentLang(item.languageCode)" :languageCode="item.languageCode"></slot>
          </div>
        </a-tab-pane>
      </a-tabs>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { listLang } from '/@/api/common/api';

interface LangTabsProps {
  translations?: any[];
  defaultFields?: Record<string, any>;
  title?: string;
  tabsType?: 'line' | 'card' | 'editable-card';
  padding?: string | number;
  disabled?: boolean;
}

const props = withDefaults(defineProps<LangTabsProps>(), {
  translations: () => [],
  defaultFields: () => ({}),
  title: '',
  tabsType: 'card',
  padding: '0 20px',
  disabled: false,
});

const emit = defineEmits(['update:translations']);

const languageList = ref<any[]>([]);
const activeKey = ref('');
const localTranslations = reactive<any[]>([]);
// 跳过由自身 emit 触发的 props watch，防止循环
let skipPropsWatch = false;
// pending 队列：语言列表加载前到达的 translations 先暂存
let pendingMerge: any[] | null = null;

const containerStyle = computed(() => {
  if (!props.padding) return {};
  if (typeof props.padding === 'number') return { padding: `${props.padding}px` };
  return { padding: props.padding };
});

/**
 * 纯计算：把 localTranslations 与 languageList 组合出显示列表
 * 注意：computed 内禁止 mutate localTranslations，否则会引发 computed 无限重算
 */
const mergedList = computed(() => {
  if (languageList.value.length === 0) {
    return localTranslations.map((t) => ({ ...t, __langName: t.__langName || t.languageCode }));
  }
  return languageList.value.map((lang) => {
    const existing = localTranslations.find((t) => t.languageCode === lang.code);
    if (existing) {
      return { ...existing, __langName: lang.name || lang.code };
    }
    return null as any;
  }).filter(Boolean);
});

function getBindableItem(item: any) {
  // 返回 localTranslations 里的原始 reactive 对象，保证 v-model 可以写回
  const real = localTranslations.find((t) => t.languageCode === item.languageCode);
  return real || item;
}

function currentLang(code: string) {
  return languageList.value.find((l) => l.code === code) || { code, name: code };
}

async function loadLanguages() {
  const res = await listLang();
  const list = Array.isArray(res) ? res : res?.result || res?.data || [];
  languageList.value = list;

  // 根据语言列表初始化 localTranslations，缺的用 defaultFields 补齐
  list.forEach((lang: any) => {
    const idx = localTranslations.findIndex((t) => t.languageCode === lang.code);
    if (idx < 0) {
      const newItem: any = { languageCode: lang.code };
      Object.keys(props.defaultFields).forEach((key) => {
        const val = props.defaultFields[key];
        newItem[key] = typeof val === 'object' && val !== null ? JSON.parse(JSON.stringify(val)) : val;
      });
      localTranslations.push(newItem);
    }
  });

  // 如果语言还没加载完就收到了 mergeFrom/外部 translations，这里兑现
  if (pendingMerge) {
    doMerge(pendingMerge);
    pendingMerge = null;
  }

  if (list.length > 0 && !activeKey.value) {
    activeKey.value = list[0].code;
  }

  await nextTick();
  emitUp();
}

function emitUp() {
  skipPropsWatch = true;
  try {
    emit('update:translations', clean(localTranslations));
  } finally {
    // 保证在下一轮 tick 解锁（因为 Vue 会在当前或下一轮执行 props 更新回调）
    nextTick(() => {
      skipPropsWatch = false;
    });
  }
}

/**
 * 去除 __langName 等临时字段，返回提交给后端的纯净数据
 */
function clean(list?: any[]): any[] {
  const source = list ?? localTranslations;
  if (!Array.isArray(source)) return [];
  return source
    .filter((t) => !!t && !!t.languageCode)
    .map((t) => {
      const copy = { ...t };
      delete copy.__langName;
      return copy;
    });
}

/**
 * 内部合并：不触发额外 emitUp
 */
function doMerge(existingTranslations: any[]) {
  if (!Array.isArray(existingTranslations)) return;
  existingTranslations.forEach((et) => {
    const idx = localTranslations.findIndex((t) => t.languageCode === et.languageCode);
    if (idx >= 0) {
      Object.assign(localTranslations[idx], et);
    } else {
      const newItem = { ...et };
      localTranslations.push(newItem);
    }
  });
}

/**
 * 规范化：兼容后端返回的 JSON 字符串或真实数组两种形态
 */
function normalizeTranslations(val: any): any[] {
  if (Array.isArray(val)) return val;
  if (typeof val === 'string') {
    try {
      const parsed = JSON.parse(val);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  return [];
}

/**
 * 合并编辑时传入的翻译数据（通常在 queryById 之后调用）
 * 接受数组或 JSON 字符串两种形态，组件内部自动解析
 */
function mergeFrom(existingTranslations: any) {
  const list = normalizeTranslations(existingTranslations);
  if (list.length === 0) return;
  if (languageList.value.length === 0) {
    pendingMerge = list;
    return;
  }
  doMerge(list);
  emitUp();
}

/**
 * 重新加载语言列表并重置翻译数据
 */
async function refresh() {
  localTranslations.length = 0;
  activeKey.value = '';
  pendingMerge = null;
  await loadLanguages();
}

// 父组件 v-model:translations 回传同步（只处理外部发起的改动）
watch(
  () => props.translations,
  (val) => {
    if (skipPropsWatch) return;
    const list = normalizeTranslations(val);
    if (list.length === 0) return;
    if (languageList.value.length === 0) {
      pendingMerge = list;
      return;
    }
    const currentClean = clean(localTranslations);
    if (!sameData(currentClean, list)) {
      doMerge(list);
      emitUp();
    }
  },
  { deep: true, immediate: true }
);

// 内部用户输入变化时上报（防抖批量处理，避免每次按键都 emit）
let emitTimer: any = null;
watch(
  localTranslations,
  () => {
    if (emitTimer) clearTimeout(emitTimer);
    emitTimer = setTimeout(() => {
      emitTimer = null;
      emitUp();
    }, 50);
  },
  { deep: true }
);

function sameData(a: any[], b: any[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((item, i) => JSON.stringify(item) === JSON.stringify(b[i]));
}

onMounted(() => {
  loadLanguages();
});

defineExpose({
  refresh,
  clean,
  mergeFrom,
});
</script>

<style lang="less" scoped>
/* ============== 容器：零花活，和 Jeecg 表单字段排版对齐 ============== */
.lang-tabs-shell {
  width: 100%;
  margin: 14px 0 12px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC",
    "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

/* ============== 卡片：AntD 原生风格（白底 + 细边 + 8px 圆角） ============== */
.lang-tabs-card {
  position: relative;
  background: #ffffff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
  transition:
    border-color 220ms cubic-bezier(0.4, 0, 0.2, 1),
    box-shadow 220ms cubic-bezier(0.4, 0, 0.2, 1),
    transform 220ms cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    border-color: #d9d9d9;
    box-shadow:
      0 6px 16px 0 rgba(0, 0, 0, 0.04),
      0 3px 6px -4px rgba(0, 0, 0, 0.04),
      0 1px 2px 0 rgba(0, 0, 0, 0.03);
    transform: translateY(-0.5px);
  }
}

/* ============== 标题：AntD风格细蓝条 + 简洁文字 ============== */
.lang-tabs-header {
  display: flex;
  align-items: center;
  column-gap: 10px;
  padding: 18px 24px 14px;
  border-bottom: 1px solid transparent;
}

/* 左侧 3px 纯蓝条（AntD 原生主色 1677ff，无渐变） */
.lang-tabs-bar {
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: #1677ff;
  flex-shrink: 0;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.08);
  transition: height 220ms cubic-bezier(0.4, 0, 0.2, 1),
              box-shadow 220ms cubic-bezier(0.4, 0, 0.2, 1);
}
.lang-tabs-card:hover .lang-tabs-bar {
  height: 16px;
  box-shadow: 0 0 0 4px rgba(22, 119, 255, 0.12);
}

.lang-tabs-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.88);   /* AntD 标题色 */
  line-height: 1.4;
  letter-spacing: 0.1px;
}

/* ============== Tab 内容：细节过渡动画（横向轻滑+淡入） ============== */
.lang-tabs-pane-inner {
  padding: 6px 0 12px;
  animation: langTabsPaneIn 280ms cubic-bezier(0.22, 0.61, 0.36, 1) both;
  transform-origin: left center;
}

/* 关键细节动画 ============== */
@keyframes langTabsPaneIn {
  0% {
    opacity: 0;
    transform: translateX(8px);
    filter: blur(0.5px);
  }
  60% {
    filter: blur(0);
  }
  100% {
    opacity: 1;
    transform: translateX(0);
  }
}

/* ============== Ant Design Tabs：AntD 原生蓝统一配色 ============== */
:deep(.lang-tabs-ant) {
  .ant-tabs-nav {
    margin: 0 !important;
    padding: 0 24px;
    background: transparent;

    &::before {
      border-bottom: 1px solid #f0f0f0 !important;
      left: 24px !important;
      right: 24px !important;
      width: auto !important;
    }
  }

  .ant-tabs-tab {
    position: relative;
    padding: 10px 18px 12px !important;
    margin-right: 4px;
    font-size: 14px;
    font-weight: 400;
    color: rgba(0, 0, 0, 0.65);
    border-radius: 6px 6px 0 0;
    transition:
      color 180ms cubic-bezier(0.4, 0, 0.2, 1),
      background-color 180ms cubic-bezier(0.4, 0, 0.2, 1),
      font-weight 180ms cubic-bezier(0.4, 0, 0.2, 1);

    &::before {
      /* 细节：hover 时底部极细淡蓝发光 */
      content: "";
      position: absolute;
      left: 18px;
      right: 18px;
      bottom: 6px;
      height: 2px;
      border-radius: 2px;
      background: #1677ff;
      opacity: 0;
      transform: scaleX(0.2);
      transform-origin: left center;
      transition:
        opacity 180ms cubic-bezier(0.4, 0, 0.2, 1),
        transform 220ms cubic-bezier(0.22, 0.61, 0.36, 1);
    }

    &:hover {
      color: #1677ff;
      background-color: rgba(22, 119, 255, 0.04);
      &::before {
        opacity: 0.25;
        transform: scaleX(0.6);
      }
    }

    .ant-tabs-tab-remove {
      color: rgba(0, 0, 0, 0.45);
      transition: color 180ms cubic-bezier(0.4, 0, 0.2, 1);
      &:hover {
        color: #1677ff;
      }
    }
  }

  .ant-tabs-tab-active {
    color: #1677ff !important;
    font-weight: 500;
    background-color: #ffffff;

    &::after {
      display: none;
    }
  }

  /* Card 模式：AntD 原生蓝色激活卡边 */
  .ant-tabs-card {
    .ant-tabs-tab {
      border: 1px solid transparent !important;
      border-bottom: none !important;
      margin-right: 6px;
      background: transparent;
    }
    .ant-tabs-tab-active {
      border: 1px solid #bae0ff !important;
      border-bottom: 1px solid #ffffff !important;
      color: #1677ff !important;
      background: linear-gradient(180deg, #e6f4ff 0%, #ffffff 100%);
      box-shadow: 0 -2px 5px rgba(22, 119, 255, 0.07);
    }
  }

  /* 激活下划线：AntD 原生纯色蓝（#1677ff）+ 软边圆头 */
  .ant-tabs-ink-bar {
    height: 2px !important;
    border-radius: 2px 2px 0 0;
    background: #1677ff !important;
    transition:
      width 260ms cubic-bezier(0.22, 0.61, 0.36, 1),
      left 260ms cubic-bezier(0.22, 0.61, 0.36, 1) !important;
  }

  .ant-tabs-content-holder {
    padding: 12px 24px 4px;
    transition: padding 220ms ease;
  }

  .ant-tabs-tab-btn {
    cursor: pointer;
    user-select: none;
    outline: none;
  }

  .ant-tabs-tab-btn:focus-visible,
  .ant-tabs-tab-remove:focus-visible {
    outline: 2px solid #91caff;
    outline-offset: 3px;
    border-radius: 4px;
  }
}

/* ============== 响应式：小屏紧凑 ============== */
@media (max-width: 640px) {
  .lang-tabs-shell {
    margin: 12px 0 10px;
  }
  .lang-tabs-header {
    padding: 16px 16px 12px;
  }
  .lang-tabs-bar {
    height: 13px;
  }
  .lang-tabs-title {
    font-size: 13.5px;
  }
  :deep(.lang-tabs-ant) {
    .ant-tabs-nav {
      padding: 0 16px;
      &::before {
        left: 16px !important;
        right: 16px !important;
      }
    }
    .ant-tabs-tab {
      padding: 9px 12px 11px !important;
      font-size: 13px;
    }
    .ant-tabs-content-holder {
      padding: 12px 16px 4px;
    }
  }
}

/* ============== 减少动效偏好兼容 ============== */
@media (prefers-reduced-motion: reduce) {
  .lang-tabs-pane-inner {
    animation: none !important;
  }
  .lang-tabs-card,
  .lang-tabs-bar,
  :deep(.lang-tabs-ant .ant-tabs-tab),
  :deep(.lang-tabs-ant .ant-tabs-tab::before),
  :deep(.lang-tabs-ant .ant-tabs-tab-remove),
  :deep(.lang-tabs-ant .ant-tabs-ink-bar),
  :deep(.lang-tabs-ant .ant-tabs-content-holder) {
    transition: none !important;
  }
  .lang-tabs-card:hover {
    transform: none !important;
  }
  .lang-tabs-card:hover .lang-tabs-bar {
    height: 14px !important;
  }
  :deep(.lang-tabs-ant .ant-tabs-tab:hover::before) {
    opacity: 0 !important;
    transform: none !important;
  }
}
</style>
