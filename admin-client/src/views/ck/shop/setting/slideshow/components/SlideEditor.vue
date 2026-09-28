<template>
  <section class="ed">
    <header class="ed__head">
      <div class="ed__head-main">
        <h2 class="ed__title">{{ form.desc || (isNew ? '新增轮播图' : '未填写描述') }}</h2>
        <span class="ed__meta">
          <template v-if="isNew">尚未保存</template>
          <template v-else>排序 {{ form.sort }} · {{ form.enabled ? '已启用' : '已禁用' }}</template>
        </span>
      </div>
      <span class="ed__status" :class="`is-${saveState}`">{{ statusText }}</span>
      <div class="ed__actions">
        <a-popconfirm v-if="!isNew" title="确定要删除这张轮播图吗？" ok-text="确认删除" cancel-text="取消" @confirm="emit('remove', record)">
          <a-button size="small" danger v-auth="'ck:shop:slideshow:delete'">删除</a-button>
        </a-popconfirm>
        <a-button v-else size="small" @click="emit('remove', record)">取消新增</a-button>
      </div>
    </header>

    <div class="ed__body">
      <!-- 前台效果预览 -->
      <div class="ed__block">
        <div class="ed__block-head">
          <span class="ed__block-title">前台效果预览</span>
          <div class="ed__langs">
            <button
              v-for="lang in languages"
              :key="lang.code"
              type="button"
              class="ed__lang"
              :class="{ 'is-on': lang.code === activeLang }"
              @click="activeLang = lang.code"
            >
              <i class="ed__lang-dot" :class="{ 'is-on': hasImage(lang.code) }"></i>
              {{ lang.name || lang.code }}
            </button>
          </div>
        </div>

        <div class="ed__hero">
          <img v-if="activeImage" :src="activeImage" alt="" class="ed__hero-img" />
          <div v-else class="ed__hero-empty">
            <Icon icon="ant-design:picture-outlined" :size="22" />
            <span>当前语言还没有图片</span>
          </div>
          <span v-if="activeTitle" class="ed__hero-title">{{ activeTitle }}</span>
        </div>
        <p class="ed__hint">图片按 2:1 展示（建议 1200×600），此处即为商城首页的实际裁切效果。</p>
      </div>

      <!-- 基础属性 -->
      <div class="ed__block">
        <div class="ed__block-head">
          <span class="ed__block-title">基础属性</span>
        </div>
        <div class="ed__grid">
          <div class="ed__field">
            <label>描述</label>
            <a-input v-model:value="form.desc" :maxlength="60" allow-clear placeholder="用于后台识别" @change="scheduleSave" />
          </div>
          <div class="ed__field">
            <label>关联商品</label>
            <a-input v-model:value="form.goods" allow-clear placeholder="商品编码，可留空" @change="scheduleSave" />
          </div>
          <div class="ed__field">
            <label>排序</label>
            <a-input-number v-model:value="form.sort" class="ed__number" :min="1" :max="9999" :precision="0" @change="scheduleSave" />
          </div>
          <div class="ed__field">
            <label>状态</label>
            <div class="ed__switch">
              <a-switch v-model:checked="form.enabled" @change="scheduleSave" />
              <span>{{ form.enabled ? '前台展示' : '前台隐藏' }}</span>
            </div>
          </div>
          <div class="ed__field ed__field--wide">
            <label>跳转链接</label>
            <a-input-group compact class="ed__url">
              <a-select v-model:value="form.protocol" class="ed__url-protocol" :options="protocolOptions" @change="scheduleSave" />
              <a-input
                v-model:value="form.targetUrl"
                class="ed__url-input"
                allow-clear
                placeholder="例如 www.cikian.cn/act/1，可留空"
                @change="scheduleSave"
              />
            </a-input-group>
          </div>
        </div>
      </div>

      <!-- 多语言内容 -->
      <div class="ed__block">
        <div class="ed__block-head">
          <span class="ed__block-title">多语言内容</span>
          <span class="ed__meta">{{ languages.length }} 种语言，未上传图片的语言前台回退英文素材</span>
        </div>

        <a-tabs v-model:activeKey="activeLang" class="ed__tabs">
          <a-tab-pane v-for="lang in languages" :key="lang.code">
            <template #tab>
              <span>{{ lang.name || lang.code }}</span>
              <Icon v-if="hasImage(lang.code)" icon="ant-design:check-outlined" class="ed__tab-ok" />
            </template>

            <div class="ed__lang-row">
              <div class="ed__lang-upload">
                <!-- JImageUpload 设置了 inheritAttrs:false，class 需要挂在外层容器上 -->
                <div class="ed__upload">
                  <JImageUpload
                    :key="`${lang.code}-${uploadKey}`"
                    :biz-path="BIZ_PATH"
                    v-model:value="contents[lang.code][`pic_url_${lang.code}`]"
                    :file-max="1"
                    text="上传图片"
                  />
                </div>
                <p class="ed__hint">建议 2:1（1200×600）</p>
              </div>
              <div class="ed__lang-field">
                <label>轮播标题</label>
                <a-input
                  :value="contents[lang.code][`title_${lang.code}`]"
                  :maxlength="60"
                  allow-clear
                  :placeholder="`${lang.name || lang.code} 标题，可留空`"
                  @change="(e) => handleTitleChange(lang.code, e)"
                />
                <p class="ed__hint">留空时该语言只展示图片，不影响其他语言。</p>
              </div>
            </div>
          </a-tab-pane>
        </a-tabs>
      </div>
    </div>
  </section>
</template>

<script lang="ts" name="SlideEditor" setup>
  import { computed, nextTick, reactive, ref } from 'vue';
  import JImageUpload from '/@/components/Form/src/jeecg/components/JImageUpload.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { getFileAccessHttpUrl } from '/@/utils/common/compUtils';
  import { saveOrUpdate, type LanguageItem, type SlideRecord } from '../SlideshowSetting.api';

  const BIZ_PATH = '/shop/home/slideshow';
  /** 输入停顿多久后自动保存 */
  const SAVE_DELAY = 700;

  const props = defineProps<{
    record: SlideRecord;
    languages: LanguageItem[];
    sortHint: number;
  }>();

  const emit = defineEmits<{
    (e: 'saved', payload: SlideRecord): void;
    (e: 'remove', record: SlideRecord): void;
  }>();

  const { createMessage } = useMessage();

  const activeLang = ref('');
  const uploadKey = ref(0);
  const saveState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle');
  /** 当前记录主键；为空表示尚未落库的新增草稿 */
  const currentId = ref('');

  const form = reactive({
    desc: '',
    goods: '',
    targetUrl: '',
    protocol: 'https://',
    sort: 1,
    enabled: true,
  });

  /** 各语言内容：{ zh: { pic_url_zh, title_zh } } */
  const contents = reactive<Record<string, Record<string, string>>>({});

  const protocolOptions = [
    { label: 'https://', value: 'https://' },
    { label: 'http://', value: 'http://' },
  ];

  let timer: ReturnType<typeof setTimeout> | null = null;
  /** 初始化期间不触发自动保存 */
  let ready = false;
  /** 是否有保存请求在执行中，以及期间是否又有新改动 */
  let saving = false;
  let pending = false;

  const isNew = computed(() => !currentId.value);

  const statusText = computed(() => {
    if (isNew.value) {
      return '填写后自动保存';
    }
    return { idle: '自动保存', saving: '保存中…', saved: '已保存', error: '保存失败' }[saveState.value];
  });

  function langUrl(code: string): string {
    return contents[code]?.[`pic_url_${code}`] || '';
  }

  function hasImage(code: string): boolean {
    return !!langUrl(code);
  }

  const activeImage = computed(() => {
    const url = langUrl(activeLang.value);
    return url ? getFileAccessHttpUrl(url) : '';
  });

  const activeTitle = computed(() => contents[activeLang.value]?.[`title_${activeLang.value}`] || '');

  /** 用父组件传入的记录初始化表单 */
  function initFromRecord() {
    ready = false;
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }

    const record = props.record || ({} as SlideRecord);
    currentId.value = record.id || '';

    form.desc = record.desc || '';
    form.goods = record.goods || '';
    form.sort = Number(record.sort) > 0 ? Number(record.sort) : props.sortHint;
    form.enabled = record.id ? record.enabled === 'Y' : true;

    const rawUrl = (record.targetUrl || '').trim();
    const matched = rawUrl.match(/^(https?):\/\//i);
    form.protocol = matched ? `${matched[1].toLowerCase()}://` : 'https://';
    form.targetUrl = matched ? rawUrl.slice(matched[0].length) : rawUrl;

    Object.keys(contents).forEach((key) => delete contents[key]);
    props.languages.forEach((lang) => {
      contents[lang.code] = {
        [`pic_url_${lang.code}`]: record?.[lang.code]?.[`pic_url_${lang.code}`] || '',
        [`title_${lang.code}`]: record?.[lang.code]?.[`title_${lang.code}`] || '',
      };
    });

    activeLang.value = props.languages[0]?.code || '';
    uploadKey.value += 1;
    saveState.value = 'idle';

    // 等本轮渲染与上传组件初始化完成后再打开自动保存，避免初始化触发保存
    nextTick(() => {
      ready = true;
    });
  }

  initFromRecord();

  function handleTitleChange(code: string, event: any) {
    contents[code][`title_${code}`] = event?.target?.value ?? '';
    scheduleSave();
  }

  /** 组装提交结构；编辑时 targetUrl 为空则不提交，保留后端原值 */
  function buildPayload() {
    const payload: any = {
      desc: form.desc.trim(),
      goods: form.goods.trim(),
      sort: Number(form.sort) || props.sortHint,
      enabled: form.enabled ? 'Y' : 'N',
    };
    if (currentId.value) {
      payload.id = currentId.value;
    }
    const targetUrl = form.targetUrl.trim();
    if (targetUrl) {
      payload.targetUrl = `${form.protocol}${targetUrl}`;
    }
    props.languages.forEach((lang) => {
      payload[lang.code] = {
        [`pic_url_${lang.code}`]: langUrl(lang.code) || null,
        [`title_${lang.code}`]: contents[lang.code]?.[`title_${lang.code}`] || null,
      };
    });
    return payload;
  }

  async function saveNow() {
    // 没有描述的新增草稿不落库，避免产生空记录
    if (!currentId.value && !form.desc.trim() && !props.languages.some((lang) => hasImage(lang.code))) {
      return;
    }
    if (saving) {
      pending = true;
      return;
    }
    saving = true;
    saveState.value = 'saving';
    try {
      const isCreate = !currentId.value;
      const res = await saveOrUpdate(buildPayload(), !isCreate);
      if (!res.success) {
        saveState.value = 'error';
        createMessage.warning(res.message || '保存失败');
        return;
      }
      saveState.value = 'saved';
      emit('saved', { ...props.record, ...buildPayload(), id: currentId.value });
    } catch (error) {
      saveState.value = 'error';
    } finally {
      saving = false;
      if (pending) {
        pending = false;
        scheduleSave();
      }
    }
  }

  /** 输入变化后延迟保存，避免逐字请求 */
  function scheduleSave() {
    if (!ready) {
      return;
    }
    if (timer) {
      clearTimeout(timer);
    }
    saveState.value = 'idle';
    timer = setTimeout(() => {
      timer = null;
      saveNow();
    }, SAVE_DELAY);
  }

  defineExpose({ saveNow });
</script>

<style lang="less" scoped>
  @ed-border: #f0f0f0;
  @ed-bg: #f5f5f5;
  @ed-primary: var(--j-global-primary-color, #1677ff);
  @ed-text: rgba(0, 0, 0, 0.88);
  @ed-sub: rgba(0, 0, 0, 0.45);

  .ed {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: #fff;
    border-radius: 8px;
  }

  .ed__head {
    display: flex;
    gap: 12px;
    align-items: center;
    padding: 12px 20px;
    border-bottom: 1px solid @ed-border;
  }

  .ed__head-main {
    display: flex;
    gap: 10px;
    align-items: baseline;
    min-width: 0;
  }

  .ed__title {
    max-width: 360px;
    overflow: hidden;
    font-size: 15px;
    font-weight: 600;
    color: @ed-text;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .ed__meta {
    font-size: 12px;
    color: @ed-sub;
  }

  .ed__status {
    margin-left: auto;
    font-size: 12px;
    color: @ed-sub;

    &.is-saved {
      color: #389e0d;
    }

    &.is-error {
      color: #ff4d4f;
    }
  }

  .ed__actions {
    display: flex;
    gap: 8px;
  }

  .ed__body {
    flex: 1;
    overflow-y: auto;
    padding: 14px 20px 18px;
  }

  .ed__block + .ed__block {
    padding-top: 14px;
    margin-top: 14px;
    border-top: 1px solid @ed-border;
  }

  .ed__block-head {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 12px;
    align-items: center;
    margin-bottom: 12px;
  }

  .ed__block-title {
    font-size: 14px;
    font-weight: 600;
    color: @ed-text;
  }

  /* ---------- 预览 ---------- */
  .ed__hero {
    position: relative;
    width: 100%;
    max-width: 400px;
    aspect-ratio: 2 / 1;
    overflow: hidden;
    background: @ed-bg;
    border: 1px solid @ed-border;
    border-radius: 8px;
  }

  .ed__hero-img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .ed__hero-empty {
    display: flex;
    flex-direction: column;
    gap: 6px;
    align-items: center;
    justify-content: center;
    height: 100%;
    font-size: 13px;
    color: rgba(0, 0, 0, 0.25);
  }

  .ed__hero-title {
    position: absolute;
    bottom: 12px;
    left: 12px;
    max-width: calc(100% - 24px);
    padding: 2px 8px;
    overflow: hidden;
    font-size: 13px;
    color: #fff;
    text-overflow: ellipsis;
    white-space: nowrap;
    background: rgba(0, 0, 0, 0.5);
    border-radius: 4px;
  }

  .ed__langs {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .ed__lang {
    display: inline-flex;
    gap: 6px;
    align-items: center;
    height: 26px;
    padding: 0 10px;
    font-size: 12px;
    color: @ed-sub;
    cursor: pointer;
    background: #fff;
    border: 1px solid #d9d9d9;
    border-radius: 4px;
    transition: all 0.2s;

    &:hover {
      color: @ed-primary;
      border-color: @ed-primary;
    }

    &.is-on {
      color: @ed-primary;
      background: #f0f7ff;
      border-color: @ed-primary;
    }
  }

  .ed__lang-dot {
    width: 6px;
    height: 6px;
    background: #d9d9d9;
    border-radius: 50%;

    &.is-on {
      background: #52c41a;
    }
  }

  /* ---------- 属性 ---------- */
  .ed__grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px 16px;
  }

  .ed__field {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;

    label {
      font-size: 12px;
      color: rgba(0, 0, 0, 0.65);
    }

    &--wide {
      grid-column: span 2;
    }
  }

  .ed__number {
    width: 100%;
  }

  .ed__switch {
    display: flex;
    gap: 10px;
    align-items: center;

    span {
      font-size: 12px;
      color: @ed-sub;
    }
  }

  .ed__url {
    display: flex;

    &-protocol {
      width: 100px;
    }

    &-input {
      flex: 1;
      min-width: 0;
    }
  }

  /* ---------- 多语言 ---------- */
  .ed__tabs {
    :deep(.ant-tabs-nav) {
      margin-bottom: 16px;
    }

    :deep(.ant-tabs-nav-wrap) {
      overflow-x: auto;
      scrollbar-width: thin;
    }
  }

  .ed__tab-ok {
    margin-left: 4px;
    color: #52c41a;
  }

  .ed__lang-row {
    display: grid;
    grid-template-columns: 260px minmax(0, 1fr);
    gap: 20px;
    align-items: start;
  }

  .ed__lang-field {
    display: flex;
    flex-direction: column;
    gap: 6px;

    label {
      font-size: 13px;
      color: rgba(0, 0, 0, 0.65);
    }
  }

  .ed__hint {
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 1.6;
    color: @ed-sub;
  }

  /* 上传区固定 2:1 */
  .ed__upload {
    :deep(.ant-upload-select) {
      width: 260px !important;
      height: 130px !important;
      margin: 0 !important;
      overflow: hidden;
      background: @ed-bg;
      border: 1px dashed #d9d9d9;
      border-radius: 6px;
      transition: border-color 0.2s;

      &:hover {
        border-color: @ed-primary;
      }
    }

    :deep(.ant-upload-list) {
      display: flex;
      gap: 8px;
      margin-top: 0;
    }

    :deep(.ant-upload-list-item-container),
    :deep(.ant-upload-list-item) {
      width: 260px !important;
      height: 130px !important;
      margin: 0 !important;
      overflow: hidden;
      border-radius: 6px;
    }

    :deep(img) {
      object-fit: cover;
    }
  }

  @media (max-width: 1100px) {
    .ed__lang-row {
      grid-template-columns: minmax(0, 1fr);
    }

    .ed__upload {
      :deep(.ant-upload-select),
      :deep(.ant-upload-list-item-container),
      :deep(.ant-upload-list-item) {
        width: 100% !important;
        height: auto !important;
        aspect-ratio: 2 / 1;
      }
    }
  }

  @media (max-width: 768px) {
    .ed__grid {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
