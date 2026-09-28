<template>
  <div class="ws">
    <!-- 左：轮播图列表 -->
    <aside class="ws-list">
      <header class="ws-list__head">
        <span class="ws-list__title">轮播图</span>
        <span class="ws-list__count">{{ listData.length }} 张</span>
        <a-button type="link" size="small" class="ws-list__add" v-auth="'ck:shop:slideshow:add'" @click="handleAdd">
          <template #icon><Icon icon="ant-design:plus-outlined" /></template>
          新增
        </a-button>
      </header>

      <div class="ws-list__search">
        <a-input v-model:value="keyword" size="small" placeholder="搜索描述 / 商品 / 链接" allow-clear />
        <a-tooltip title="按住左侧把手拖动即可调整顺序，松手自动保存">
          <Icon icon="ant-design:question-circle-outlined" class="ws-list__help" />
        </a-tooltip>
      </div>

      <div class="ws-list__body">
        <!-- vuedraggable v4 必须使用 #item 插槽；搜索状态下禁用拖拽，避免顺序与筛选结果不一致 -->
        <vue-draggable
          :list="listData"
          :item-key="itemKeyOf"
          :animation="180"
          :disabled="!!keyword || dragDisabled"
          handle=".ws-item__handle"
          ghost-class="ws-item--ghost"
          chosen-class="ws-item--chosen"
          @end="handleDragEnd"
        >
          <template #item="{ element: item }">
            <div
              class="ws-item"
              :class="{ 'is-active': isActive(item), 'is-draft': !item.id, 'is-off': item.id && item.enabled !== 'Y' }"
              :data-slide-id="item.id || ''"
              @click="selectItem(item)"
            >
              <span class="ws-item__handle" :class="{ 'ws-item__handle--draft': !item.id }" title="拖动排序">
                <Icon icon="ant-design:holder-outlined" />
              </span>
              <div class="ws-item__thumb" :style="thumbStyle(item)">
                <span v-if="!getThumb(item)" class="ws-item__thumb-empty">无图</span>
              </div>
              <div class="ws-item__main">
                <div class="ws-item__title">{{ item.desc || (item.id ? '未填写描述' : '新增轮播图') }}</div>
                <div class="ws-item__meta">
                  <span
                    v-for="lang in languages"
                    :key="lang.code"
                    class="ws-dot"
                    :class="{ 'is-on': !!getLangImage(item, lang.code) }"
                    :title="`${lang.name}${getLangImage(item, lang.code) ? '：已配置' : '：未配置'}`"
                  >
                    {{ lang.code.slice(0, 2).toUpperCase() }}
                  </span>
                  <span v-if="item.id && item.enabled !== 'Y'" class="ws-item__off">已禁用</span>
                </div>
              </div>
              <span class="ws-item__sort">{{ item.id ? toSortValue(item.sort) : '新' }}</span>
            </div>
          </template>
        </vue-draggable>

        <a-empty v-if="!filteredData.length" :image="simpleImage" :description="keyword ? '没有匹配的轮播图' : '还没有轮播图'" />
      </div>
    </aside>

    <!-- 右：编辑区 -->
    <SlideEditor
      v-if="active"
      :key="active.id || DRAFT_KEY"
      :record="active"
      :languages="languages"
      :sort-hint="listData.length + 1"
      @saved="handleSaved"
      @remove="handleDelete"
    />
    <div v-else class="ws-blank">
      <a-empty :image="simpleImage" description="从左侧选择一条轮播图，或新增一条" />
    </div>
  </div>
</template>

<script lang="ts" name="ck.shop-SlideshowSetting" setup>
  import { computed, onMounted, ref } from 'vue';
  import { Empty } from 'ant-design-vue';
  import VueDraggable from 'vuedraggable';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { listLang } from '/@/api/common/api';
  import { getFileAccessHttpUrl } from '/@/utils/common/compUtils';
  import { deleteOne, list, saveSort, type LanguageItem, type SlideRecord } from './SlideshowSetting.api';
  import SlideEditor from './components/SlideEditor.vue';

  /** 新增占位草稿的固定 key */
  const DRAFT_KEY = '__draft__';
  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;

  const { createMessage } = useMessage();

  const listData = ref<SlideRecord[]>([]);
  const languages = ref<LanguageItem[]>([]);
  const loading = ref(false);
  const keyword = ref('');
  /** 排序保存中禁用再次拖动 */
  const dragDisabled = ref(false);
  /** 刚结束拖拽，用于屏蔽随之而来的 click */
  let justDragged = false;
  /** 当前选中的记录，id 为空表示尚未保存的草稿 */
  const active = ref<SlideRecord | null>(null);

  const filteredData = computed(() => {
    const kw = keyword.value.trim().toLowerCase();
    if (!kw) {
      return listData.value;
    }
    return listData.value.filter((item) => [item.desc, item.goods, item.targetUrl].filter(Boolean).join(' ').toLowerCase().includes(kw));
  });

  /** 拖拽用列表：草稿置顶不参与排序，其余按 sort 升序；这里必须返回原数组引用 */
  function itemKeyOf(item: SlideRecord) {
    return item.id || DRAFT_KEY;
  }

  function toSortValue(sort?: number | null): number {
    const num = Number(sort);
    return Number.isFinite(num) ? num : Number.MAX_SAFE_INTEGER;
  }

  function getLangImage(record: SlideRecord, langCode: string): string {
    const url = record?.[langCode]?.[`pic_url_${langCode}`];
    return typeof url === 'string' ? url.trim() : '';
  }

  /** 列表缩略图：优先第一种语言，其次英文，最后扫描任意语言 */
  function getThumb(record: SlideRecord): string {
    const codes = [...languages.value.map((lang) => lang.code), 'en'];
    for (const code of codes) {
      const url = getLangImage(record, code);
      if (url) {
        return getFileAccessHttpUrl(url);
      }
    }
    return '';
  }

  function thumbStyle(record: SlideRecord) {
    const url = getThumb(record);
    return url ? { backgroundImage: `url(${url})` } : {};
  }

  function isActive(item: SlideRecord) {
    return !!active.value && (active.value.id || DRAFT_KEY) === (item.id || DRAFT_KEY);
  }

  /** 拖拽结束后浏览器还会补一次 click，这里屏蔽掉，避免误切换选中项 */
  function selectItem(item: SlideRecord) {
    if (justDragged) {
      return;
    }
    active.value = item;
  }

  /** 新增：插入本地草稿并选中，首次保存时才真正创建 */
  function handleAdd() {
    const draft: SlideRecord = {
      id: '',
      desc: '',
      goods: '',
      targetUrl: '',
      enabled: 'Y',
      sort: listData.value.length + 1,
    };
    languages.value.forEach((lang) => {
      draft[lang.code] = { [`pic_url_${lang.code}`]: '', [`title_${lang.code}`]: '' };
    });
    keyword.value = '';
    listData.value = [draft, ...listData.value];
    active.value = draft;
  }

  /** 保存成功：用后端返回的最新列表刷新，并保持选中项 */
  async function handleSaved(payload: SlideRecord) {
    const selectId = payload.id;
    await loadData(selectId);
  }

  async function handleDelete(record: SlideRecord) {
    if (!record.id) {
      // 草稿未保存，直接丢弃
      listData.value = listData.value.filter((item) => item.id);
      active.value = listData.value[0] || null;
      return;
    }
    try {
      await deleteOne(record.id);
      createMessage.success('删除成功');
      await loadData();
    } catch (error) {
      // 请求拦截器已提示具体错误
    }
  }

  /**
   * 拖拽结束：把新顺序写回 sort。
   * @end 触发时 vuedraggable 已经把新顺序写回 listData，DOM 也同步完成，
   * 因此这里以 DOM 顺序为权威（它就是用户刚刚拖出来的顺序），重新编号后提交。
   * 不要按 oldIndex/newIndex 再搬一次，也不要用 listData 做前后对比（它此时已是新顺序）。
   */
  async function handleDragEnd() {
    justDragged = true;
    // 拖拽后会紧接着触发 click，延后解除屏蔽
    setTimeout(() => {
      justDragged = false;
    }, 300);

    await persistOrder(getDisplayOrder());
  }

  /**
   * 取列表当前的真实展示顺序（按 DOM 顺序，包含未保存的新增行）。
   * 必须返回全部行：若把新增行剔除，后面的行会整体前移，编号就会错位、
   * 甚至把新增行从列表中弄丢。
   */
  function getDisplayOrder(): SlideRecord[] {
    const byId = new Map(listData.value.filter((item) => item.id).map((item) => [item.id, item]));
    const ordered: SlideRecord[] = [];

    Array.from(document.querySelectorAll('.ws-list__body [data-draggable]')).forEach((el) => {
      const id = (el as HTMLElement).getAttribute('data-slide-id') || '';
      const record = id ? byId.get(id) : listData.value.find((item) => !item.id);
      if (record && !ordered.includes(record)) {
        ordered.push(record);
        if (id) {
          byId.delete(id);
        }
      }
    });

    // DOM 读取异常时兜底：补上尚未纳入的行
    listData.value.forEach((item) => {
      if (!ordered.includes(item)) {
        ordered.push(item);
      }
    });
    return ordered;
  }

  /** 按给定顺序重新编号并保存；编号无变化时不发请求，重复调用也安全 */
  async function persistOrder(ordered: SlideRecord[]) {
    if (dragDisabled.value) {
      return;
    }
    const saved = ordered.filter((item) => item.id);
    if (saved.length < 2) {
      return;
    }

    // 只有编号真的需要变化时才提交
    const sortChanged = saved.some((item, index) => Number(item.sort) !== index + 1);
    if (!sortChanged) {
      return;
    }

    dragDisabled.value = true;
    // 原地更新编号并同步列表顺序，避免替换数组引用（会丢掉未保存的新增行、并让编辑器重建）
    saved.forEach((item, index) => {
      item.sort = index + 1;
    });
    listData.value = [...ordered];
    try {
      const res = await saveSort(saved.map((item, index) => ({ id: item.id, sort: index + 1 })));
      if (!res.success) {
        throw new Error(res.message || '顺序保存失败');
      }
      createMessage.success('顺序已保存');
    } catch (error) {
      createMessage.error('顺序保存失败，已还原');
      await loadData(active.value?.id);
    } finally {
      dragDisabled.value = false;
    }
  }

  async function loadLanguages() {
    try {
      const res = await listLang();
      const result = Array.isArray(res) ? res : (res as any)?.result || [];
      languages.value = result.filter((item: LanguageItem) => item?.code);
    } catch (error) {
      languages.value = [];
    }
    if (!languages.value.length) {
      languages.value = [{ id: 'en', code: 'en', language: 'en-US', name: 'English', enabled: 'Y', sort: 1 }];
    }
  }

  async function loadData(selectId?: string) {
    loading.value = true;
    try {
      const res = await list();
      const result = Array.isArray(res) ? res : (res as any)?.result || [];
      listData.value = (result as SlideRecord[]).filter((item) => item && item.id).sort((a, b) => toSortValue(a.sort) - toSortValue(b.sort));

      const nextId = selectId || active.value?.id;
      const matched = listData.value.find((item) => item.id === nextId);
      active.value = matched || listData.value[0] || null;
    } catch (error) {
      // 请求拦截器已经弹出后端返回的错误信息
      listData.value = [];
      active.value = null;
    } finally {
      loading.value = false;
    }
  }

  onMounted(async () => {
    await loadLanguages();
    await loadData();
  });
</script>

<style lang="less" scoped>
  @ws-border: #f0f0f0;
  @ws-bg: #f5f5f5;
  @ws-primary: var(--j-global-primary-color, #1677ff);
  @ws-text: rgba(0, 0, 0, 0.88);
  @ws-sub: rgba(0, 0, 0, 0.45);

  .ws {
    display: grid;
    grid-template-columns: 300px minmax(0, 1fr);
    gap: 12px;
    /* 让工作台自适应主内容区高度，避免被多语言区域顶出屏幕 */
    height: calc(100dvh - 200px);
    min-height: 460px;
  }

  /* ---------- 左栏 ---------- */
  .ws-list {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: #fff;
    border-radius: 8px;
  }

  .ws-list__head {
    display: flex;
    gap: 6px;
    align-items: center;
    padding: 12px 12px 10px;
  }

  .ws-list__title {
    font-size: 14px;
    font-weight: 600;
    color: @ws-text;
  }

  .ws-list__count {
    font-size: 12px;
    color: @ws-sub;
  }

  .ws-list__add {
    height: 22px;
    padding: 0 4px;
    margin-left: auto;
  }

  .ws-list__search {
    display: flex;
    gap: 6px;
    align-items: center;
    padding: 0 12px 10px;
    border-bottom: 1px solid @ws-border;
  }

  .ws-list__help {
    flex: none;
    color: rgba(0, 0, 0, 0.25);
    cursor: help;
  }

  .ws-list__body {
    flex: 1;
    overflow-y: auto;
    padding: 6px;
  }

  .ws-item {
    position: relative;
    display: flex;
    gap: 8px;
    align-items: center;
    padding: 8px 8px 8px 2px;
    cursor: pointer;
    border-radius: 6px;
    transition: background 0.2s;

    &:hover {
      background: #fafafa;

      .ws-item__handle {
        opacity: 1;
      }
    }

    &.is-active {
      background: #e6f4ff;
    }

    &.is-draft {
      padding-left: 8px;
      border: 1px dashed @ws-border;
    }

    &.is-off .ws-item__title {
      color: @ws-sub;
    }
  }

  /* 拖动把手：hover 才显现，避免视觉噪音 */
  .ws-item__handle {
    display: flex;
    flex: none;
    align-items: center;
    justify-content: center;
    width: 14px;
    color: rgba(0, 0, 0, 0.25);
    cursor: grab;
    opacity: 0;
    transition: opacity 0.2s;

    &:active {
      cursor: grabbing;
    }

    /* 未保存的新增行：一直显示把手，方便先把位置摆好再填写 */
    &--draft {
      opacity: 1;
    }
  }

  /* 拖拽中的占位与跟随元素 */
  .ws-item--ghost {
    background: #f0f7ff;
    border: 1px dashed @ws-primary;
    opacity: 0.6;
  }

  .ws-item--chosen {
    background: #e6f4ff;
  }

  .ws-item__thumb {
    flex: none;
    width: 72px;
    height: 40px;
    background-color: @ws-bg;
    background-position: center;
    background-size: cover;
    border: 1px solid @ws-border;
    border-radius: 4px;
    /* 背景图会被浏览器当作可拖拽元素，屏蔽掉以免与排序冲突 */
    -webkit-user-drag: none;
  }

  .ws-item__thumb-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    font-size: 11px;
    color: rgba(0, 0, 0, 0.25);
  }

  .ws-item__main {
    flex: 1;
    min-width: 0;
  }

  .ws-item__title {
    overflow: hidden;
    font-size: 13px;
    color: @ws-text;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .ws-item__meta {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    align-items: center;
    margin-top: 4px;
  }

  .ws-dot {
    padding: 0 4px;
    font-size: 11px;
    line-height: 16px;
    color: rgba(0, 0, 0, 0.25);
    background: #fafafa;
    border: 1px solid @ws-border;
    border-radius: 3px;

    &.is-on {
      color: #389e0d;
      background: #f6ffed;
      border-color: #b7eb8f;
    }
  }

  .ws-item__off {
    font-size: 11px;
    color: @ws-sub;
  }

  .ws-item__sort {
    flex: none;
    font-size: 12px;
    color: @ws-sub;
  }

  /* ---------- 右栏空白态 ---------- */
  .ws-blank {
    display: flex;
    align-items: center;
    justify-content: center;
    background: #fff;
    border-radius: 8px;
  }

  @media (max-width: 1100px) {
    .ws {
      grid-template-columns: 260px minmax(0, 1fr);
    }
  }

  @media (max-width: 900px) {
    .ws {
      grid-template-columns: minmax(0, 1fr);
      height: auto;
    }

    .ws-list__body {
      max-height: 260px;
    }
  }
</style>
