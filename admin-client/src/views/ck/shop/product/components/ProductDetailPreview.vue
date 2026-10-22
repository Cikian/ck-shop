<template>
  <div class="detail-preview">
    <div class="detail-preview__head">
      <div class="detail-preview__title">
        <Icon icon="ant-design:eye-outlined" aria-hidden="true" />
        <span>实时预览</span>
        <span v-if="langName" class="detail-preview__lang">{{ langName }}</span>
      </div>
      <a-radio-group v-model:value="deviceModel" size="small" button-style="solid">
        <a-radio-button value="desktop">桌面</a-radio-button>
        <a-radio-button value="mobile">移动 390px</a-radio-button>
      </a-radio-group>
    </div>
    <p class="detail-preview__tip">与店铺端同顺序：规格参数 → 商品详情；无数据的区块不会显示。</p>

    <!-- 模拟店铺端内容列：桌面 100%（最宽 880），移动 390 -->
    <div class="detail-preview__frame" :class="device === 'mobile' ? 'is-mobile' : 'is-desktop'">
      <div class="pv">
        <!-- 规格参数：非空才渲染（与店铺端一致） -->
        <section v-if="params.length" class="pv-section">
          <h2 class="pv-section__title">
            <span class="pv-section__bar" aria-hidden="true"></span>
            规格参数
          </h2>
          <dl class="pv-params">
            <div v-for="(param, index) in params" :key="index" class="pv-params__row">
              <dt class="pv-params__name">{{ param.paramName || '—' }}</dt>
              <dd class="pv-params__value">{{ param.paramValue || '—' }}</dd>
            </div>
          </dl>
        </section>

        <!-- 商品详情：简介 + 模块 -->
        <section class="pv-section">
          <h2 class="pv-section__title">
            <span class="pv-section__bar" aria-hidden="true"></span>
            商品详情
          </h2>

          <div v-if="safeDescription" class="pv-intro" v-html="safeDescription"></div>

          <div v-if="blocks.length" class="pv-blocks">
            <!-- 每个模块最外层用内联 margin（默认 0）；容器不加 gap/space-y，长图切割才能严丝合缝 -->
            <div v-for="(block, index) in blocks" :key="block.__uid || index" class="pv-block" :style="blockMarginStyle(block)">
              <!-- 整幅图片 -->
              <figure v-if="block.blockType === 'IMAGE'" class="pv-image">
                <button
                  type="button"
                  class="pv-image__btn"
                  :aria-label="`放大查看：${imageAlt(block, index)}`"
                  @click="openLightbox([{ url: block.content.url, alt: imageAlt(block, index) }], 0, $event)"
                >
                  <img :src="block.content.url" :alt="imageAlt(block, index)" :style="imageRadiusStyle(block)" loading="lazy" decoding="async" />
                </button>
              </figure>

              <!-- 多图宫格：CSS Grid repeat(columns, minmax(0,1fr)) + 每格内联 itemMargin -->
              <div v-else-if="block.blockType === 'IMAGE_GRID'" class="pv-grid" :style="gridTemplateStyle(block)">
                <button
                  v-for="(image, imageIndex) in gridImages(block)"
                  :key="imageIndex"
                  type="button"
                  class="pv-grid__item"
                  :style="gridItemMarginStyle(block)"
                  :aria-label="`放大查看第 ${imageIndex + 1} 张图片`"
                  @click="openLightbox(gridImages(block), imageIndex, $event)"
                >
                  <img
                    :src="image.url"
                    :alt="image.alt || `${productTitle || '商品'}详情图 ${imageIndex + 1}`"
                    :style="imageRadiusStyle(block)"
                    loading="lazy"
                    decoding="async"
                  />
                </button>
              </div>

              <!-- 图文 -->
              <div v-else-if="block.blockType === 'IMAGE_TEXT'" class="pv-imagetext" :class="{ 'is-image-right': block.content.layout === 'right' }">
                <button
                  type="button"
                  class="pv-imagetext__media"
                  :aria-label="`放大查看：${imageAlt(block, index)}`"
                  @click="openLightbox([{ url: block.content.url, alt: imageAlt(block, index) }], 0, $event)"
                >
                  <img :src="block.content.url" :alt="imageAlt(block, index)" :style="imageRadiusStyle(block)" loading="lazy" decoding="async" />
                </button>
                <div class="pv-imagetext__body">
                  <h3 v-if="block.content.title" class="pv-imagetext__title">{{ block.content.title }}</h3>
                  <p v-for="(line, lineIndex) in paragraphs(block.content.text)" :key="lineIndex" class="pv-paragraph">{{ line }}</p>
                </div>
              </div>

              <!-- 文本段落 -->
              <div v-else-if="block.blockType === 'TEXT'" class="pv-text">
                <h3 v-if="block.content.title" class="pv-text__title">{{ block.content.title }}</h3>
                <p v-for="(line, lineIndex) in paragraphs(block.content.text)" :key="lineIndex" class="pv-paragraph">{{ line }}</p>
              </div>

              <!-- 富文本（渲染前白名单清洗，契约第五节） -->
              <div v-else-if="block.blockType === 'RICH_TEXT'" class="pv-richtext" v-html="sanitizeHtml(block.content.html)"></div>

              <!-- 视频 -->
              <div v-else-if="block.blockType === 'VIDEO'" class="pv-video">
                <template v-if="block.content.provider === 'file'">
                  <video
                    v-if="isVideoUrlValid('file', block.content.url)"
                    class="pv-video__player"
                    :src="block.content.url"
                    :poster="block.content.poster || undefined"
                    controls
                    preload="metadata"
                    playsinline
                  ></video>
                </template>
                <div v-else-if="buildVideoEmbedUrl(block.content.provider, block.content.url)" class="pv-video__frame">
                  <iframe
                    :src="buildVideoEmbedUrl(block.content.provider, block.content.url)"
                    :title="`${videoProviderLabel(block.content.provider)} 视频`"
                    allowfullscreen
                    referrerpolicy="no-referrer"
                    loading="lazy"
                    frameborder="0"
                  ></iframe>
                </div>
              </div>

              <!-- 小节标题 -->
              <div v-else-if="block.blockType === 'TITLE'" class="pv-title">
                <h2 class="pv-title__text">{{ block.content.text || '小节标题' }}</h2>
                <p v-if="block.content.sub" class="pv-title__sub">{{ block.content.sub }}</p>
              </div>

              <!-- 分隔线 -->
              <hr v-else-if="block.blockType === 'DIVIDER'" class="pv-divider" />
            </div>
          </div>

          <p v-if="!blocks.length && !safeDescription" class="pv-empty">暂无商品描述</p>
        </section>
      </div>
    </div>

    <!-- 简易灯箱：与店铺端体验一致（遮罩 / 左右切换 / Esc / 滚动锁定 / 关闭后焦点回到触发图） -->
    <Teleport to="body">
      <div v-if="lightbox.visible" class="pv-lightbox" role="dialog" aria-modal="true" aria-label="图片预览" @click.self="closeLightbox">
        <button ref="closeBtnRef" type="button" class="pv-lightbox__close" aria-label="关闭图片预览" @click="closeLightbox">
          <Icon icon="ant-design:close-outlined" />
        </button>
        <button v-if="lightbox.images.length > 1" type="button" class="pv-lightbox__nav is-prev" aria-label="上一张" @click.stop="stepLightbox(-1)">
          <Icon icon="ant-design:left-outlined" />
        </button>
        <img v-if="currentImage.url" class="pv-lightbox__img" :src="currentImage.url" :alt="currentImage.alt" />
        <button v-if="lightbox.images.length > 1" type="button" class="pv-lightbox__nav is-next" aria-label="下一张" @click.stop="stepLightbox(1)">
          <Icon icon="ant-design:right-outlined" />
        </button>
        <div v-if="lightbox.images.length > 1" class="pv-lightbox__counter"> {{ lightbox.index + 1 }} / {{ lightbox.images.length }} </div>
      </div>
    </Teleport>
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue';
  import {
    blockMarginStyle,
    buildVideoEmbedUrl,
    gridItemMarginStyle,
    gridTemplateStyle,
    imageRadiusStyle,
    isVideoUrlValid,
    sanitizeHtml,
    videoProviderLabel,
    type DetailBlockItem,
    type ProductParamItem,
  } from './productDetailMeta';

  /**
   * 商品详情实时预览（规格参数 + 商品详情）
   *
   * 保真要求（契约 3.2 / 4.3）：与店铺端同一套渲染规则 ——
   * 每个模块最外层内联 margin（默认 0，容器无 gap）、图片 border-radius 取 content.radius（默认 0）、
   * 宫格 repeat(columns, minmax(0,1fr)) 且每格内联 itemMargin、图文桌面两栏 / 移动堆叠、视频 16:9。
   * 宽度按 390 / 桌面内容列模拟；这里只渲染，不修改任何数据，只读模式下同样可用。
   */
  const props = withDefaults(
    defineProps<{
      blocks?: DetailBlockItem[];
      params?: ProductParamItem[];
      /** 当前语言的「商品简介」（description） */
      description?: string;
      /** 当前语言的商品标题，用于图片 alt 兜底 */
      productTitle?: string;
      /** 当前语言显示名 */
      langName?: string;
      device?: 'desktop' | 'mobile';
    }>(),
    {
      blocks: () => [],
      params: () => [],
      description: '',
      productTitle: '',
      langName: '',
      device: 'desktop',
    }
  );

  const emit = defineEmits(['update:device']);

  const deviceModel = computed<'desktop' | 'mobile'>({
    get: () => props.device || 'desktop',
    set: (value) => emit('update:device', value),
  });

  const safeDescription = computed(() => sanitizeHtml(props.description));

  function paragraphs(text: any): string[] {
    return String(text || '')
      .split(/\n/)
      .map((line) => line.trim())
      .filter(Boolean);
  }

  function imageAlt(block: DetailBlockItem, index: number): string {
    const alt = String(block.content?.alt || '').trim();
    if (alt) return alt;
    const title = String(props.productTitle || '').trim();
    return title ? `${title} 详情图 ${index + 1}` : `商品详情图 ${index + 1}`;
  }

  function gridImages(block: DetailBlockItem): { url: string; alt: string }[] {
    return (block.content?.images || [])
      .filter((image: any) => String(image?.url || '').trim())
      .map((image: any) => ({ url: String(image.url), alt: String(image.alt || '').trim() }));
  }

  // ---------------- 简易灯箱 ----------------
  interface LightboxImage {
    url: string;
    alt: string;
  }

  const closeBtnRef = ref<any>(null);
  const lightbox = reactive<{ visible: boolean; images: LightboxImage[]; index: number }>({
    visible: false,
    images: [],
    index: 0,
  });
  const currentImage = computed<LightboxImage>(() => lightbox.images[lightbox.index] || { url: '', alt: '' });

  let lastTrigger: HTMLElement | null = null;
  let previousBodyOverflow = '';

  function openLightbox(images: LightboxImage[], index: number, event?: MouseEvent) {
    const valid = (images || []).filter((image) => image && image.url);
    if (!valid.length) return;
    lastTrigger = (event?.currentTarget as HTMLElement) || null;
    lightbox.images = valid;
    lightbox.index = Math.min(Math.max(index, 0), valid.length - 1);
    lightbox.visible = true;
  }

  function closeLightbox() {
    lightbox.visible = false;
  }

  function stepLightbox(delta: number) {
    const total = lightbox.images.length;
    if (total < 2) return;
    lightbox.index = (lightbox.index + delta + total) % total;
  }

  function onLightboxKeydown(event: KeyboardEvent) {
    if (!lightbox.visible) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      closeLightbox();
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      stepLightbox(-1);
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      stepLightbox(1);
    }
  }

  watch(
    () => lightbox.visible,
    (visible) => {
      if (visible) {
        previousBodyOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        window.addEventListener('keydown', onLightboxKeydown);
        nextTick(() => closeBtnRef.value?.focus?.());
      } else {
        document.body.style.overflow = previousBodyOverflow;
        window.removeEventListener('keydown', onLightboxKeydown);
        // 关闭后焦点回到触发图片（契约 4.4）
        nextTick(() => lastTrigger?.focus?.());
      }
    }
  );

  onBeforeUnmount(() => {
    window.removeEventListener('keydown', onLightboxKeydown);
    if (lightbox.visible) {
      document.body.style.overflow = previousBodyOverflow;
    }
  });
</script>

<style lang="less" scoped>
  .detail-preview {
    background: var(--shop-surface);
    border: 1px solid var(--shop-border);
    border-radius: 12px;
    padding: 14px;
    position: sticky;
    /* 让开抽屉顶部吸顶的页签条（.product-tabs 的 nav ≈ 46px） */
    top: 56px;

    &__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      flex-wrap: wrap;
    }

    &__title {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      font-weight: 600;
      color: var(--shop-text);
    }

    &__lang {
      padding: 0 6px;
      border-radius: 4px;
      background: #f0f7ff;
      color: var(--shop-primary);
      font-size: 12px;
      font-weight: 400;
      line-height: 18px;
    }

    &__tip {
      margin: 8px 0 12px;
      font-size: 12px;
      line-height: 1.6;
      color: var(--shop-text-secondary);
    }

    /* 预览视口：滚动只发生在这一层，外部表单滚动不受影响 */
    &__frame {
      border: 1px solid var(--shop-border);
      border-radius: 10px;
      background: var(--shop-surface);
      overflow-y: auto;
      max-height: calc(100vh - 300px);

      &.is-desktop {
        width: 100%;
      }

      &.is-mobile {
        width: 390px;
        max-width: 100%;
        margin: 0 auto;
      }
    }
  }

  .pv {
    padding: 20px 22px 6px;
    font-size: 14px;
    line-height: 1.7;
    color: rgba(0, 0, 0, 0.72);
  }

  .pv-section {
    padding-bottom: 22px;

    & + & {
      border-top: 1px solid var(--shop-border);
      padding-top: 20px;
    }

    &__title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 14px;
      font-size: 16px;
      font-weight: 700;
      color: var(--shop-text);
    }

    &__bar {
      width: 4px;
      height: 16px;
      border-radius: 2px;
      background: var(--shop-primary);
    }
  }

  .pv-params {
    margin: 0;
    padding: 4px 14px;
    background: #f7f8fa;
    border-radius: 8px;

    &__row {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
      padding: 9px 0;
      border-bottom: 1px solid var(--shop-border);

      &:last-child {
        border-bottom: 0;
      }
    }

    &__name {
      flex: 0 0 96px;
      font-size: 13px;
      color: var(--shop-text-secondary);
      word-break: break-word;
    }

    &__value {
      flex: 1 1 160px;
      margin: 0;
      min-width: 0;
      font-size: 13px;
      font-weight: 500;
      color: var(--shop-text);
      word-break: break-word;
    }
  }

  .pv-intro {
    margin-bottom: 8px;
    color: rgba(0, 0, 0, 0.72);

    :deep(img) {
      max-width: 100%;
      height: auto;
      /* 默认无圆角：与店铺端一致 */
      border-radius: 0;
    }

    :deep(a) {
      color: var(--shop-primary);
    }

    :deep(p) {
      margin: 0 0 10px;
    }
  }

  /*
   * 契约 4.3：容器不得再加 gap / space-y / mt-* 之类的统一间距，
   * 模块之间的留白全部由每个模块最外层的内联 margin 决定（默认 0）。
   * 这里刻意保持普通块级容器（不改为 flex/grid），让相邻模块 margin 的合并规则
   * 与店铺端普通 div 渲染一致；长图切割（margin 全 0）时自然严丝合缝。
   */

  .pv-block {
    min-width: 0;
  }

  .pv-image {
    margin: 0;

    &__btn {
      display: block;
      width: 100%;
      padding: 0;
      border: 0;
      background: transparent;
      cursor: zoom-in;

      &:focus-visible {
        outline: 2px solid var(--shop-primary);
        outline-offset: 2px;
      }

      img {
        display: block;
        width: 100%;
        height: auto;
      }
    }
  }

  /* 宫格：列数与图片间距全部走内联样式（gridTemplateStyle / gridItemMarginStyle） */
  .pv-grid {
    display: grid;

    &__item {
      display: block;
      padding: 0;
      border: 0;
      background: transparent;
      cursor: zoom-in;

      &:focus-visible {
        outline: 2px solid var(--shop-primary);
        outline-offset: 2px;
      }

      img {
        display: block;
        width: 100%;
        height: auto;
      }
    }
  }

  .pv-imagetext {
    display: flex;
    align-items: flex-start;
    gap: 18px;

    /* layout=right 时图片在右（桌面端）；移动端永远图在上 */
    &.is-image-right {
      flex-direction: row-reverse;
    }

    &__media {
      flex: 0 0 45%;
      padding: 0;
      border: 0;
      background: transparent;
      cursor: zoom-in;

      &:focus-visible {
        outline: 2px solid var(--shop-primary);
        outline-offset: 2px;
      }

      img {
        display: block;
        width: 100%;
        height: auto;
      }
    }

    &__body {
      flex: 1 1 auto;
      min-width: 0;
      padding-top: 2px;
    }

    &__title {
      margin: 0 0 8px;
      font-size: 16px;
      font-weight: 700;
      color: var(--shop-text);
    }
  }

  .pv-text {
    &__title {
      margin: 0 0 8px;
      font-size: 16px;
      font-weight: 700;
      color: var(--shop-text);
    }
  }

  .pv-paragraph {
    margin: 0 0 10px;
    line-height: 1.8;
    word-break: break-word;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .pv-richtext {
    line-height: 1.8;
    word-break: break-word;

    :deep(p) {
      margin: 0 0 10px;
    }

    :deep(img) {
      max-width: 100%;
      height: auto;
      /* 默认无圆角：与店铺端一致（富文本里的图片不额外加圆角） */
      border-radius: 0;
    }

    :deep(a) {
      color: var(--shop-primary);
    }

    :deep(h2),
    :deep(h3),
    :deep(h4) {
      margin: 16px 0 8px;
      font-weight: 700;
      color: var(--shop-text);
    }

    :deep(ul),
    :deep(ol) {
      padding-left: 20px;
    }

    :deep(table) {
      display: block;
      width: 100%;
      overflow-x: auto;
      border-collapse: collapse;
    }

    :deep(th),
    :deep(td) {
      padding: 6px 10px;
      border: 1px solid var(--shop-border);
    }
  }

  .pv-video {
    &__frame {
      position: relative;
      width: 100%;
      padding-top: 56.25%;
      border-radius: 10px;
      overflow: hidden;
      background: #000;

      iframe {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        border: 0;
      }
    }

    &__player {
      display: block;
      width: 100%;
      border-radius: 10px;
      background: #000;
    }
  }

  .pv-title {
    &__text {
      margin: 0 0 6px;
      font-size: 17px;
      font-weight: 700;
      color: var(--shop-text);
    }

    &__sub {
      margin: 0;
      font-size: 13px;
      color: var(--shop-text-secondary);
    }
  }

  .pv-divider {
    margin: 4px 0;
    border: 0;
    border-top: 1px solid var(--shop-border);
  }

  .pv-empty {
    margin: 0;
    padding: 24px 0;
    text-align: center;
    font-size: 13px;
    color: var(--shop-text-tertiary);
  }

  /* 移动视口：图文堆叠（图在上）、内边距收窄；宫格列数按配置走 repeat(minmax(0,1fr))，不会横向溢出 */
  .detail-preview__frame.is-mobile {
    .pv {
      padding: 16px 14px 4px;
    }

    .pv-imagetext {
      flex-direction: column;
      gap: 10px;

      &.is-image-right {
        flex-direction: column;
      }

      &__media {
        flex: 1 1 auto;
        width: 100%;
      }
    }

    .pv-params__name {
      flex-basis: 80px;
    }
  }

  /* ---------------- 灯箱 ---------------- */
  .pv-lightbox {
    position: fixed;
    inset: 0;
    z-index: 1100;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
    background: rgba(0, 0, 0, 0.78);

    &__img {
      max-width: min(92vw, 1100px);
      max-height: 86vh;
      border-radius: 8px;
      background: var(--shop-surface);
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
    }

    &__close {
      position: absolute;
      top: 16px;
      right: 20px;
      width: 40px;
      height: 40px;
      border: 0;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.16);
      color: #fff;
      font-size: 18px;
      cursor: pointer;

      &:hover {
        background: rgba(255, 255, 255, 0.28);
      }

      &:focus-visible {
        outline: 2px solid #fff;
        outline-offset: 2px;
      }
    }

    &__nav {
      position: absolute;
      top: 50%;
      transform: translateY(-50%);
      width: 44px;
      height: 44px;
      border: 0;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.16);
      color: #fff;
      font-size: 18px;
      cursor: pointer;

      &.is-prev {
        left: 20px;
      }

      &.is-next {
        right: 20px;
      }

      &:hover {
        background: rgba(255, 255, 255, 0.28);
      }

      &:focus-visible {
        outline: 2px solid #fff;
        outline-offset: 2px;
      }
    }

    &__counter {
      position: absolute;
      bottom: 20px;
      left: 50%;
      transform: translateX(-50%);
      padding: 2px 12px;
      border-radius: 12px;
      background: rgba(0, 0, 0, 0.5);
      color: #fff;
      font-size: 12px;
      letter-spacing: 1px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .pv-image__btn,
    .pv-grid__item,
    .pv-imagetext__media {
      transition: none !important;
    }
  }
</style>
