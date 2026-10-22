<template>
  <div ref="rootRef" class="detail-editor">
    <!-- 工具条：新增 / 复制到其它语言 -->
    <div class="detail-editor__toolbar">
      <a-space :size="8" wrap>
        <a-button
          v-if="!disabled"
          type="primary"
          ghost
          preIcon="ant-design:plus-outlined"
          :disabled="blocks.length >= blockLimit"
          @click="openCreate"
        >
          新增模块
        </a-button>
        <a-button v-if="!disabled" preIcon="ant-design:copy-outlined" :disabled="!blocks.length" @click="openCopy">复制到其它语言</a-button>
      </a-space>
      <span class="detail-editor__hint">
        共 <strong>{{ blocks.length }}</strong> / {{ blockLimit }} 个模块；顺序即店铺端展示顺序，可拖拽或用 ↑↓ 调整
      </span>
    </div>

    <a-alert
      v-if="!disabled && !blocks.length"
      type="info"
      show-icon
      class="detail-editor__alert"
      message="从模块开始搭建详情页"
      description="图片建议宽度 ≥ 1200px；长文用「富文本」，多图用「多图宫格」，视频支持 B 站 / YouTube / 直链 mp4。右侧预览与店铺端同顺序、同结构。"
    />

    <a-empty
      v-if="!blocks.length"
      :image="simpleImage"
      description="还没有详情模块，点「新增模块」开始；只想放一段简介时，可到「多语言内容 → 商品简介」填写"
    />

    <!-- vuedraggable v4 必须使用 #item 具名插槽，默认插槽不会被读取 -->
    <VueDraggable
      v-else
      v-model="orderedBlocks"
      :item-key="itemKey"
      :disabled="disabled"
      handle=".block-card__handle"
      :animation="160"
      ghost-class="block-card--ghost"
      class="detail-editor__list"
    >
      <template #item="{ element: block, index }">
        <div class="block-card" :class="{ 'is-expanded': isExpanded(block) }" :data-block-uid="block.__uid">
          <div class="block-card__head">
            <Icon
              icon="ant-design:holder-outlined"
              class="block-card__handle"
              :class="{ 'is-disabled': disabled }"
              title="按住拖动调整模块顺序"
              aria-hidden="true"
            />
            <button
              type="button"
              class="block-card__toggle"
              :aria-expanded="isExpanded(block) ? 'true' : 'false'"
              :aria-label="`${isExpanded(block) ? '收起' : '展开'}第 ${index + 1} 个模块（${typeLabel(block)}）`"
              @click="toggle(block)"
            >
              <a-tag :color="typeColor(block)" class="block-card__type">{{ typeLabel(block) }}</a-tag>
              <span class="block-card__summary">{{ blockSummary(block) }}</span>
              <span v-if="blockLayoutSummary(block)" class="block-card__layout">{{ blockLayoutSummary(block) }}</span>
              <Icon
                :icon="isExpanded(block) ? 'ant-design:up-outlined' : 'ant-design:down-outlined'"
                class="block-card__chevron"
                aria-hidden="true"
              />
            </button>
            <span class="block-card__index">#{{ index + 1 }}</span>
            <div v-if="!disabled" class="block-card__tools">
              <a-button
                type="text"
                size="small"
                :disabled="disabled || index === 0"
                title="上移一位"
                :aria-label="`将第 ${index + 1} 个模块上移`"
                @click="moveBlock(index, -1)"
              >
                <Icon icon="ant-design:arrow-up-outlined" />
              </a-button>
              <a-button
                type="text"
                size="small"
                :disabled="disabled || index === blocks.length - 1"
                title="下移一位"
                :aria-label="`将第 ${index + 1} 个模块下移`"
                @click="moveBlock(index, 1)"
              >
                <Icon icon="ant-design:arrow-down-outlined" />
              </a-button>
              <a-popconfirm v-if="!disabled" title="确认删除该模块？删除后不可撤销" ok-text="删除" cancel-text="取消" @confirm="removeBlock(index)">
                <a-button type="text" size="small" danger title="删除该模块" :aria-label="`删除第 ${index + 1} 个模块`">
                  <Icon icon="ant-design:delete-outlined" />
                </a-button>
              </a-popconfirm>
            </div>
          </div>

          <div v-if="isExpanded(block)" class="block-card__body">
            <!-- 布局属性（契约 1.3/1.4/3.1）：模块间距、图片圆角、宫格列数与图片间距 -->
            <div class="block-layout">
              <div class="block-field">
                <span class="block-field__label">模块间距</span>
                <div class="block-field__control">
                  <div class="edge-row">
                    <label v-for="edge in marginFields" :key="edge.key" class="edge-row__item">
                      <span class="edge-row__label">{{ edge.label }}</span>
                      <a-input-number
                        v-model:value="block[edge.key]"
                        class="edge-row__input"
                        size="small"
                        :min="0"
                        :max="marginLimit"
                        :precision="0"
                        :disabled="disabled"
                      />
                    </label>
                    <span v-if="!disabled" class="edge-row__quick">
                      <span class="edge-row__quick-label">快捷</span>
                      <a-button
                        v-for="value in marginQuickValues"
                        :key="value"
                        size="small"
                        :disabled="disabled"
                        :title="`四边都设为 ${value}px`"
                        :aria-label="`模块间距四边都设为 ${value} 像素`"
                        @click="applyBlockMargins(block, value)"
                      >
                        {{ value }}
                      </a-button>
                    </span>
                  </div>
                  <span class="block-field__hint">
                    默认 0（模块之间无留白，长图切割后可无缝拼接）；需要留白时设 16/24。单位 px，上限 {{ marginLimit }}。
                  </span>
                </div>
              </div>

              <div v-if="isImageLikeBlock(block.blockType)" class="block-field">
                <span class="block-field__label">图片圆角</span>
                <div class="block-field__control">
                  <span class="inline-number">
                    <a-input-number
                      v-model:value="block.content.radius"
                      class="layout-number"
                      size="small"
                      :min="0"
                      :max="radiusLimit"
                      :precision="0"
                      :disabled="disabled"
                    />
                    <span class="inline-number__unit">px</span>
                  </span>
                  <span class="block-field__hint">默认 0（直角，便于长图无缝拼接）；需要圆角时再设，上限 {{ radiusLimit }}。</span>
                </div>
              </div>

              <template v-if="block.blockType === 'IMAGE_GRID'">
                <div class="block-field">
                  <span class="block-field__label">列数</span>
                  <div class="block-field__control">
                    <a-radio-group v-model:value="block.content.columns" size="small" :disabled="disabled">
                      <a-radio-button :value="1">1 列</a-radio-button>
                      <a-radio-button :value="2">2 列</a-radio-button>
                      <a-radio-button :value="3">3 列</a-radio-button>
                    </a-radio-group>
                    <span class="inline-number">
                      <span class="inline-number__prefix">自定义</span>
                      <a-input-number
                        v-model:value="block.content.columns"
                        class="layout-number"
                        size="small"
                        :min="columnsMin"
                        :max="columnsMax"
                        :precision="0"
                        :disabled="disabled"
                      />
                    </span>
                    <span class="block-field__hint">
                      快捷 1/2/3，也可自定义 {{ columnsMin }}–{{ columnsMax }} 列；长图切割用「1 列」。移动端不会横向溢出。
                    </span>
                  </div>
                </div>
                <div class="block-field">
                  <span class="block-field__label">图片间距</span>
                  <div class="block-field__control">
                    <div class="edge-row">
                      <label v-for="edge in itemMarginFields" :key="edge.key" class="edge-row__item">
                        <span class="edge-row__label">{{ edge.label }}</span>
                        <a-input-number
                          v-model:value="block.content.itemMargin[edge.key]"
                          class="edge-row__input"
                          size="small"
                          :min="0"
                          :max="itemMarginLimit"
                          :precision="0"
                          :disabled="disabled"
                        />
                      </label>
                      <span v-if="!disabled" class="edge-row__quick">
                        <span class="edge-row__quick-label">快捷</span>
                        <a-button
                          v-for="value in marginQuickValues"
                          :key="value"
                          size="small"
                          :disabled="disabled"
                          :title="`图片四边间距都设为 ${value}px`"
                          :aria-label="`图片间距四边都设为 ${value} 像素`"
                          @click="applyGridItemMargins(block, value)"
                        >
                          {{ value }}
                        </a-button>
                      </span>
                    </div>
                    <span class="block-field__hint">
                      每格四周间距，默认 0；相邻两图间距 = 左格右值 + 右格左值。上限 {{ itemMarginLimit }}。列数 1 + 全 0 + 圆角 0 时多图上下紧贴。
                    </span>
                  </div>
                </div>
              </template>
            </div>

            <!-- ① 整幅图片 -->
            <div v-if="block.blockType === 'IMAGE'" class="block-fields">
              <div class="block-field">
                <span class="block-field__label">图片</span>
                <div class="block-field__control">
                  <div class="block-field__media">
                    <JImageUpload
                      :value="block.content.url"
                      biz-path="/shop/product"
                      text="上传图片"
                      :disabled="disabled"
                      @update:value="(val) => setImageUrl(block.content, val)"
                    />
                    <a-input
                      v-model:value="block.content.url"
                      class="block-field__url"
                      placeholder="或直接粘贴图片链接 https://…"
                      allow-clear
                      :maxlength="500"
                      :disabled="disabled"
                    />
                  </div>
                  <span class="block-field__hint">店铺端按 100% 宽度、等比展示，请避免使用带文字的窄图</span>
                </div>
              </div>
              <div class="block-field">
                <span class="block-field__label">图片说明</span>
                <div class="block-field__control">
                  <a-input
                    v-model:value="block.content.alt"
                    placeholder="选填，用于无障碍读屏与图片加载失败提示"
                    allow-clear
                    :maxlength="120"
                    :disabled="disabled"
                  />
                </div>
              </div>
            </div>

            <!-- ② 多图宫格 -->
            <div v-else-if="block.blockType === 'IMAGE_GRID'" class="block-fields">
              <div class="block-field">
                <span class="block-field__label">列数</span>
                <div class="block-field__control">
                  <a-radio-group v-model:value="block.content.columns" :disabled="disabled">
                    <a-radio-button :value="2">2 列</a-radio-button>
                    <a-radio-button :value="3">3 列</a-radio-button>
                  </a-radio-group>
                  <span class="block-field__hint">移动端自动收敛为 2 列，不会横向溢出</span>
                </div>
              </div>
              <div class="block-field">
                <span class="block-field__label"> 图片（{{ (block.content.images || []).length }} / {{ gridImageLimit }}） </span>
                <div class="block-field__control">
                  <p v-if="!block.content.images.length" class="block-field__empty">还没有图片，最多 {{ gridImageLimit }} 张</p>
                  <div v-for="(image, imageIndex) in block.content.images" :key="imageIndex" class="grid-image">
                    <JImageUpload
                      :value="image.url"
                      biz-path="/shop/product"
                      text="上传"
                      :disabled="disabled"
                      @update:value="(val) => setImageUrl(image, val)"
                    />
                    <div class="grid-image__fields">
                      <a-input
                        v-model:value="image.url"
                        size="small"
                        placeholder="图片链接 https://…"
                        allow-clear
                        :maxlength="500"
                        :disabled="disabled"
                      />
                      <a-input v-model:value="image.alt" size="small" placeholder="alt（选填）" allow-clear :maxlength="120" :disabled="disabled" />
                    </div>
                    <div v-if="!disabled" class="grid-image__tools">
                      <a-button
                        type="text"
                        size="small"
                        :disabled="disabled || imageIndex === 0"
                        title="上移一张"
                        :aria-label="`将第 ${imageIndex + 1} 张图片上移`"
                        @click="moveGridImage(block, imageIndex, -1)"
                      >
                        <Icon icon="ant-design:arrow-up-outlined" />
                      </a-button>
                      <a-button
                        type="text"
                        size="small"
                        :disabled="disabled || imageIndex === block.content.images.length - 1"
                        title="下移一张"
                        :aria-label="`将第 ${imageIndex + 1} 张图片下移`"
                        @click="moveGridImage(block, imageIndex, 1)"
                      >
                        <Icon icon="ant-design:arrow-down-outlined" />
                      </a-button>
                      <a-popconfirm
                        v-if="!disabled"
                        title="删除这张图片？"
                        ok-text="删除"
                        cancel-text="取消"
                        @confirm="removeGridImage(block, imageIndex)"
                      >
                        <a-button type="text" size="small" danger title="删除这张图片" :aria-label="`删除第 ${imageIndex + 1} 张图片`">
                          <Icon icon="ant-design:close-outlined" />
                        </a-button>
                      </a-popconfirm>
                    </div>
                  </div>
                  <a-button
                    v-if="!disabled"
                    type="dashed"
                    size="small"
                    block
                    preIcon="ant-design:plus-outlined"
                    :disabled="block.content.images.length >= gridImageLimit"
                    @click="addGridImage(block)"
                  >
                    添加图片
                  </a-button>
                </div>
              </div>
            </div>

            <!-- ③ 图文 -->
            <div v-else-if="block.blockType === 'IMAGE_TEXT'" class="block-fields">
              <div class="block-field">
                <span class="block-field__label">图片位置</span>
                <div class="block-field__control">
                  <a-radio-group v-model:value="block.content.layout" :disabled="disabled">
                    <a-radio-button value="left">图片在左</a-radio-button>
                    <a-radio-button value="right">图片在右</a-radio-button>
                  </a-radio-group>
                  <span class="block-field__hint">桌面端左右两栏（图 45%），移动端自动上下堆叠（图在上）</span>
                </div>
              </div>
              <div class="block-field">
                <span class="block-field__label">图片</span>
                <div class="block-field__control">
                  <div class="block-field__media">
                    <JImageUpload
                      :value="block.content.url"
                      biz-path="/shop/product"
                      text="上传图片"
                      :disabled="disabled"
                      @update:value="(val) => setImageUrl(block.content, val)"
                    />
                    <a-input
                      v-model:value="block.content.url"
                      class="block-field__url"
                      placeholder="或直接粘贴图片链接 https://…"
                      allow-clear
                      :maxlength="500"
                      :disabled="disabled"
                    />
                  </div>
                </div>
              </div>
              <div class="block-field">
                <span class="block-field__label">标题</span>
                <div class="block-field__control">
                  <a-input v-model:value="block.content.title" placeholder="选填，如 材质说明" allow-clear :maxlength="120" :disabled="disabled" />
                </div>
              </div>
              <div class="block-field">
                <span class="block-field__label">正文</span>
                <div class="block-field__control">
                  <a-textarea
                    v-model:value="block.content.text"
                    :rows="4"
                    placeholder="选填，按回车分段"
                    show-count
                    :maxlength="2000"
                    :disabled="disabled"
                  />
                </div>
              </div>
              <div class="block-field">
                <span class="block-field__label">图片说明</span>
                <div class="block-field__control">
                  <a-input v-model:value="block.content.alt" placeholder="选填，用于无障碍读屏" allow-clear :maxlength="120" :disabled="disabled" />
                </div>
              </div>
            </div>

            <!-- ④ 文本段落 -->
            <div v-else-if="block.blockType === 'TEXT'" class="block-fields">
              <div class="block-field">
                <span class="block-field__label">标题</span>
                <div class="block-field__control">
                  <a-input v-model:value="block.content.title" placeholder="选填，如 使用建议" allow-clear :maxlength="120" :disabled="disabled" />
                </div>
              </div>
              <div class="block-field">
                <span class="block-field__label">正文</span>
                <div class="block-field__control">
                  <a-textarea
                    v-model:value="block.content.text"
                    :rows="5"
                    placeholder="正文按回车分段，纯文本展示"
                    show-count
                    :maxlength="2000"
                    :disabled="disabled"
                  />
                </div>
              </div>
            </div>

            <!-- ⑤ 富文本 -->
            <div v-else-if="block.blockType === 'RICH_TEXT'" class="block-fields">
              <div class="block-field">
                <span class="block-field__label">富文本</span>
                <div class="block-field__control">
                  <div class="rich-editor">
                    <Tinymce v-model="block.content.html" :height="260" width="100%" :disabled="disabled" />
                  </div>
                  <span class="block-field__hint">支持图片、表格、链接；保存与预览都会做白名单清洗（脚本/事件属性会被移除）</span>
                </div>
              </div>
            </div>

            <!-- ⑥ 视频 -->
            <div v-else-if="block.blockType === 'VIDEO'" class="block-fields">
              <div class="block-field">
                <span class="block-field__label">视频来源</span>
                <div class="block-field__control">
                  <a-radio-group v-model:value="block.content.provider" :disabled="disabled">
                    <a-radio-button value="bilibili">B 站</a-radio-button>
                    <a-radio-button value="youtube">YouTube</a-radio-button>
                    <a-radio-button value="file">直链 mp4</a-radio-button>
                  </a-radio-group>
                </div>
              </div>
              <div class="block-field">
                <span class="block-field__label">视频链接</span>
                <div class="block-field__control">
                  <a-input
                    v-model:value="block.content.url"
                    :placeholder="videoUrlPlaceholder(block.content.provider)"
                    allow-clear
                    :maxlength="500"
                    :disabled="disabled"
                  />
                  <span class="block-field__hint" :class="{ 'is-warn': videoHint(block).type === 'warning' }">{{ videoHint(block).text }}</span>
                </div>
              </div>
              <div v-if="block.content.provider === 'file'" class="block-field">
                <span class="block-field__label">封面图</span>
                <div class="block-field__control">
                  <div class="block-field__media">
                    <JImageUpload
                      :value="block.content.poster"
                      biz-path="/shop/product"
                      text="上传封面"
                      :disabled="disabled"
                      @update:value="(val) => setImageUrl(block.content, val, 'poster')"
                    />
                    <a-input
                      v-model:value="block.content.poster"
                      class="block-field__url"
                      placeholder="选填，封面图链接 https://…"
                      allow-clear
                      :maxlength="500"
                      :disabled="disabled"
                    />
                  </div>
                  <span class="block-field__hint">直链视频加载前展示，B 站 / YouTube 使用平台封面</span>
                </div>
              </div>
            </div>

            <!-- ⑦ 小节标题 -->
            <div v-else-if="block.blockType === 'TITLE'" class="block-fields">
              <div class="block-field">
                <span class="block-field__label">标题</span>
                <div class="block-field__control">
                  <a-input v-model:value="block.content.text" placeholder="如 常见问题" allow-clear :maxlength="120" :disabled="disabled" />
                </div>
              </div>
              <div class="block-field">
                <span class="block-field__label">副标题</span>
                <div class="block-field__control">
                  <a-input
                    v-model:value="block.content.sub"
                    placeholder="选填，标题下方的一行说明"
                    allow-clear
                    :maxlength="120"
                    :disabled="disabled"
                  />
                </div>
              </div>
            </div>

            <!-- ⑧ 分隔线 -->
            <div v-else-if="block.blockType === 'DIVIDER'" class="block-fields">
              <a-alert type="info" show-icon message="分隔线无需配置内容，用于在视觉上隔开上下两个区块。" />
            </div>
          </div>
        </div>
      </template>
    </VueDraggable>

    <!-- 新增：类型选择 -->
    <a-modal v-model:open="createVisible" title="新增详情模块" width="680px" :ok-text="`添加「${selectedTypeLabel}」`" @ok="confirmCreate">
      <p class="create-modal__hint">选择模块类型后即可编辑内容，模块顺序可在列表里拖拽调整。</p>
      <div class="type-grid">
        <button
          v-for="option in blockTypeOptions"
          :key="option.value"
          type="button"
          class="type-card"
          :class="{ 'is-active': newType === option.value }"
          :aria-pressed="newType === option.value ? 'true' : 'false'"
          @click="newType = option.value"
        >
          <Icon :icon="option.icon" class="type-card__icon" aria-hidden="true" />
          <span class="type-card__label">{{ option.label }}</span>
          <span class="type-card__desc">{{ option.desc }}</span>
        </button>
      </div>
    </a-modal>

    <!-- 复制到其它语言 -->
    <a-modal v-model:open="copyVisible" title="复制到其它语言" ok-text="复制" :ok-button-props="{ disabled: !copyTarget }" @ok="confirmCopy">
      <p class="copy-modal__line">
        当前语言共有 <strong>{{ blocks.length }}</strong> 个模块，将把「结构 + 内容」整体复制到目标语言。
      </p>
      <a-select v-model:value="copyTarget" :options="copyTargetOptions" placeholder="选择目标语言" style="width: 100%" />
      <p class="copy-modal__warn">目标语言已有模块时会被整体覆盖，操作不可撤销；复制后仍可分别编辑。</p>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, ref } from 'vue';
  import { Empty } from 'ant-design-vue';
  import VueDraggable from 'vuedraggable';
  // 必须显式 import：JImageUpload 未全局注册，删掉后 <j-image-upload> 会静默渲染为空。
  import JImageUpload from '/@/components/Form/src/jeecg/components/JImageUpload.vue';
  import {
    DETAIL_BLOCK_TYPES,
    DETAIL_BLOCK_LIMIT,
    DETAIL_GRID_IMAGE_LIMIT,
    DETAIL_GRID_ITEM_MARGIN_LIMIT,
    DETAIL_GRID_COLUMNS_MIN,
    DETAIL_GRID_COLUMNS_MAX,
    DETAIL_MARGIN_LIMIT,
    DETAIL_MARGIN_QUICK_VALUES,
    DETAIL_RADIUS_LIMIT,
    blockLayoutSummary,
    blockSummary,
    blockTypeMeta,
    createDetailBlock,
    isImageLikeBlock,
    parseVideoId,
    uid,
    type DetailBlockItem,
  } from './productDetailMeta';

  /**
   * 商品详情模块编辑器（一种语言一份）
   *
   * 数据流：props.blocks 是「当前语言、已排序」的模块数组，元素与 formData.detailBlocks 共享同一批对象。
   * - 内容字段直接 v-model 改对象（实时预览因此无需额外同步）；
   * - 结构变化（增/删/排序/拖拽）通过 update:blocks 交回父组件，父组件负责合并回扁平数组。
   */
  const props = withDefaults(
    defineProps<{
      blocks: DetailBlockItem[];
      languageCode: string;
      /** 启用语言列表，用于「复制到其它语言」 */
      languages?: any[];
      disabled?: boolean;
    }>(),
    {
      blocks: () => [],
      languages: () => [],
      disabled: false,
    }
  );

  const emit = defineEmits(['update:blocks', 'copy']);

  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;
  const blockTypeOptions = DETAIL_BLOCK_TYPES;
  const blockLimit = DETAIL_BLOCK_LIMIT;
  const gridImageLimit = DETAIL_GRID_IMAGE_LIMIT;

  // 布局属性的范围与快捷值（契约 1.3 / 1.4 / 3.1）
  const marginLimit = DETAIL_MARGIN_LIMIT;
  const radiusLimit = DETAIL_RADIUS_LIMIT;
  const itemMarginLimit = DETAIL_GRID_ITEM_MARGIN_LIMIT;
  const columnsMin = DETAIL_GRID_COLUMNS_MIN;
  const columnsMax = DETAIL_GRID_COLUMNS_MAX;
  const marginQuickValues = DETAIL_MARGIN_QUICK_VALUES;
  /** 模块间距：四个方向绑定 block 顶层的 margin* */
  const marginFields = [
    { key: 'marginTop', label: '上' },
    { key: 'marginRight', label: '右' },
    { key: 'marginBottom', label: '下' },
    { key: 'marginLeft', label: '左' },
  ] as const;
  /** 宫格图片间距：绑定 block.content.itemMargin.* */
  const itemMarginFields = [
    { key: 'top', label: '上' },
    { key: 'right', label: '右' },
    { key: 'bottom', label: '下' },
    { key: 'left', label: '左' },
  ] as const;

  const rootRef = ref<HTMLElement | null>(null);
  /** 展开状态按 __uid 记，切换语言/增删模块都不会串 */
  const expandedUids = ref<Record<string, boolean>>({});

  const itemKey = (item: DetailBlockItem) => item.__uid || item.blockType || 'block';

  /** v-model 用：拖拽结束由 vuedraggable 交回新数组，这里统一 emit 给父组件 */
  const orderedBlocks = computed<DetailBlockItem[]>({
    get: () => props.blocks,
    set: (next) => commit(next),
  });

  /** 提交当前语言的新顺序（sortOrder 由顺序推导） */
  function commit(next: DetailBlockItem[]) {
    emit(
      'update:blocks',
      next.map((block, index) => ({ ...block, __uid: block.__uid || uid('blk'), sortOrder: index }))
    );
  }

  function isExpanded(block: DetailBlockItem): boolean {
    return !!expandedUids.value[block.__uid || ''];
  }

  function toggle(block: DetailBlockItem) {
    const key = block.__uid || '';
    expandedUids.value = { ...expandedUids.value, [key]: !expandedUids.value[key] };
  }

  function typeLabel(block: DetailBlockItem): string {
    return blockTypeMeta(block.blockType).label;
  }

  function typeColor(block: DetailBlockItem): string {
    return blockTypeMeta(block.blockType).color;
  }

  function setImageUrl(target: Record<string, any>, value: any, field = 'url') {
    target[field] = typeof value === 'string' ? value : '';
  }

  // ---------------- 布局快捷值（契约 3.1） ----------------
  /** 模块间距快捷：四边同时设为同一值（默认 0 即无缝拼接） */
  function applyBlockMargins(block: DetailBlockItem, value: number) {
    if (props.disabled) return;
    block.marginTop = value;
    block.marginRight = value;
    block.marginBottom = value;
    block.marginLeft = value;
  }

  /** 宫格图片间距快捷：每格四边同时设为同一值 */
  function applyGridItemMargins(block: DetailBlockItem, value: number) {
    if (props.disabled) return;
    const itemMargin = block.content.itemMargin || (block.content.itemMargin = { top: 0, right: 0, bottom: 0, left: 0 });
    itemMargin.top = value;
    itemMargin.right = value;
    itemMargin.bottom = value;
    itemMargin.left = value;
  }

  // ---------------- 新增模块 ----------------
  const createVisible = ref(false);
  const newType = ref('IMAGE');
  const selectedTypeLabel = computed(() => blockTypeMeta(newType.value).label);

  function openCreate() {
    newType.value = 'IMAGE';
    createVisible.value = true;
  }

  function confirmCreate() {
    if (props.disabled) return;
    if (props.blocks.length >= blockLimit) {
      return;
    }
    const block = createDetailBlock(newType.value, props.languageCode);
    commit([...props.blocks, block]);
    expandedUids.value = { ...expandedUids.value, [block.__uid || '']: true };
    createVisible.value = false;
    nextTick(() => {
      const el = rootRef.value?.querySelector(`[data-block-uid="${block.__uid}"]`) as HTMLElement | null;
      el?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    });
  }

  // ---------------- 删除 / 排序 ----------------
  function removeBlock(index: number) {
    if (props.disabled) return;
    commit(props.blocks.filter((_block, i) => i !== index));
  }

  function moveBlock(index: number, delta: number) {
    if (props.disabled) return;
    const target = index + delta;
    if (target < 0 || target >= props.blocks.length) return;
    const next = props.blocks.slice();
    const [moved] = next.splice(index, 1);
    next.splice(target, 0, moved);
    commit(next);
  }

  // ---------------- 宫格图片 ----------------
  function addGridImage(block: DetailBlockItem) {
    const images = block.content.images || (block.content.images = []);
    if (images.length >= gridImageLimit) return;
    images.push({ url: '', alt: '' });
  }

  function moveGridImage(block: DetailBlockItem, index: number, delta: number) {
    const images = block.content.images || [];
    const target = index + delta;
    if (target < 0 || target >= images.length) return;
    const [moved] = images.splice(index, 1);
    images.splice(target, 0, moved);
  }

  function removeGridImage(block: DetailBlockItem, index: number) {
    (block.content.images || []).splice(index, 1);
  }

  // ---------------- 视频提示 ----------------
  function videoUrlPlaceholder(provider: string): string {
    if (provider === 'bilibili') return '粘贴 B 站视频页链接，如 https://www.bilibili.com/video/BV1xx411c7mD';
    if (provider === 'youtube') return '粘贴 YouTube 链接，如 https://www.youtube.com/watch?v=xxxxxxxxxxx';
    return '直链地址，如 https://cdn.example.com/demo.mp4';
  }

  function videoHint(block: DetailBlockItem): { type: 'success' | 'warning'; text: string } {
    const provider = block.content.provider;
    const url = String(block.content.url || '').trim();
    if (!url) return { type: 'warning', text: '请填写视频链接' };
    if (provider === 'file') {
      return /^(https?:\/\/|\/)/i.test(url)
        ? { type: 'success', text: '直链格式正确' }
        : { type: 'warning', text: '直链必须以 http(s):// 或 / 开头' };
    }
    const videoId = parseVideoId(provider, url);
    return videoId ? { type: 'success', text: `已识别视频 ID：${videoId}` } : { type: 'warning', text: '无法从链接解析出视频 ID，保存前请检查链接' };
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

  /** 提交校验失败时由父组件调用：展开并聚焦到出问题的模块 */
  function focusBlock(blockUid?: string) {
    if (!blockUid) return;
    expandedUids.value = { ...expandedUids.value, [blockUid]: true };
    nextTick(() => {
      const el = rootRef.value?.querySelector(`[data-block-uid="${blockUid}"]`) as HTMLElement | null;
      if (!el) return;
      el.scrollIntoView({ block: 'center', behavior: 'smooth' });
      const focusable = el.querySelector('input, textarea, [contenteditable="true"]') as HTMLElement | null;
      focusable?.focus({ preventScroll: true });
    });
  }

  defineExpose({ focusBlock });
</script>

<style lang="less" scoped>
  .detail-editor {
    &__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
      margin-bottom: 14px;
    }

    &__hint {
      color: var(--shop-text-secondary);
      font-size: 12px;
      line-height: 1.6;

      strong {
        color: var(--shop-primary);
        font-weight: 600;
      }
    }

    &__alert {
      margin-bottom: 12px;
    }

    &__list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
  }

  .block-card {
    background: var(--shop-surface);
    border: 1px solid var(--shop-border);
    border-radius: 10px;
    overflow: hidden;
    transition:
      border-color 180ms ease,
      box-shadow 180ms ease;

    &:hover {
      border-color: var(--shop-border-strong);
      box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
    }

    &.is-expanded {
      border-color: #91caff;
      box-shadow: 0 0 0 1px rgba(22, 119, 255, 0.08);
    }

    &--ghost {
      opacity: 0.5;
      border-style: dashed;
    }

    &__head {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px 8px 10px;
      background: var(--shop-surface-muted);
      border-bottom: 1px solid transparent;

      .is-expanded > & {
        border-bottom-color: var(--shop-border);
      }
    }

    &__handle {
      flex: none;
      color: var(--shop-text-tertiary);
      cursor: grab;

      &.is-disabled {
        cursor: not-allowed;
        opacity: 0.4;
      }

      &:active {
        cursor: grabbing;
      }
    }

    &__toggle {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1 1 auto;
      min-width: 0;
      padding: 4px 6px;
      border: 0;
      border-radius: 6px;
      background: transparent;
      text-align: left;
      cursor: pointer;

      &:hover {
        background: var(--shop-surface-hover);
      }

      &:focus-visible {
        outline: 2px solid var(--shop-primary);
        outline-offset: 1px;
      }
    }

    &__type {
      flex: none;
      margin-inline-end: 0;
    }

    &__summary {
      flex: 1 1 auto;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 13px;
      color: var(--shop-text-secondary);
    }

    /* 折叠态附带的布局摘要（间距/圆角/列数），便于扫一眼是否有留白 */
    &__layout {
      flex: 0 1 auto;
      max-width: 40%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      padding: 0 6px;
      border-radius: 4px;
      background: #f0f7ff;
      color: var(--shop-primary);
      font-size: 11px;
      line-height: 18px;
    }

    &__chevron {
      flex: none;
      color: var(--shop-text-secondary);
      font-size: 12px;
    }

    &__index {
      flex: none;
      color: var(--shop-text-tertiary);
      font-size: 12px;
      font-variant-numeric: tabular-nums;
    }

    &__tools {
      flex: none;
      display: flex;
      align-items: center;
      gap: 2px;
    }

    &__body {
      padding: 16px 18px 6px;
    }
  }

  .block-fields {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  /* 布局属性区：与内容字段用虚线分隔，视觉上先看内容再看间距/圆角 */
  .block-layout {
    display: flex;
    flex-direction: column;
    gap: 14px;
    margin-bottom: 16px;
    padding-bottom: 16px;
    border-bottom: 1px dashed var(--shop-border);
  }

  .edge-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;

    &__item {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin: 0;
      font-weight: 400;
    }

    &__label {
      color: var(--shop-text-secondary);
      font-size: 12px;
    }

    &__input {
      width: 74px;
    }

    &__quick {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin-left: 4px;
      padding-left: 10px;
      border-left: 1px solid var(--shop-border);
    }

    &__quick-label {
      color: var(--shop-text-tertiary);
      font-size: 12px;
    }
  }

  .inline-number {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-right: 8px;

    &__prefix {
      color: var(--shop-text-secondary);
      font-size: 12px;
    }

    &__unit {
      color: var(--shop-text-secondary);
      font-size: 12px;
    }
  }

  .layout-number {
    width: 86px;
  }

  .block-field {
    display: flex;
    align-items: flex-start;
    gap: 12px;

    &__label {
      flex: 0 0 84px;
      padding-top: 5px;
      text-align: right;
      font-size: 13px;
      color: var(--shop-text-secondary);
      line-height: 1.5;
    }

    &__control {
      flex: 1 1 auto;
      min-width: 0;
    }

    &__media {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      flex-wrap: wrap;
    }

    &__url {
      flex: 1 1 260px;
      min-width: 0;
      margin-top: 4px;
    }

    &__hint {
      display: block;
      margin-top: 6px;
      font-size: 12px;
      line-height: 1.6;
      color: var(--shop-text-secondary);

      &.is-warn {
        color: #d46b08;
      }
    }

    &__empty {
      margin: 0 0 10px;
      padding: 14px;
      border: 1px dashed var(--shop-border-strong);
      border-radius: 8px;
      text-align: center;
      color: var(--shop-text-tertiary);
      font-size: 12px;
    }
  }

  .grid-image {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 10px;
    margin-bottom: 10px;
    background: var(--shop-surface-muted);
    border: 1px solid var(--shop-border);
    border-radius: 8px;

    &__fields {
      flex: 1 1 auto;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding-top: 2px;
    }

    &__tools {
      flex: none;
      display: flex;
      align-items: center;
      gap: 2px;
    }
  }

  .rich-editor {
    /* Tinymce 容器需要明确宽度，否则异步加载后会塌成 0 */
    width: 100%;
    min-height: 260px;
  }

  .create-modal__hint {
    margin: 0 0 14px;
    color: var(--shop-text-secondary);
    font-size: 12px;
  }

  .type-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px;
  }

  .type-card {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    padding: 12px;
    border: 1px solid var(--shop-border);
    border-radius: 8px;
    background: var(--shop-surface);
    text-align: left;
    cursor: pointer;
    transition:
      border-color 160ms ease,
      box-shadow 160ms ease;

    &:hover {
      border-color: #91caff;
    }

    &:focus-visible {
      outline: 2px solid var(--shop-primary);
      outline-offset: 1px;
    }

    &.is-active {
      border-color: var(--shop-primary);
      box-shadow: 0 0 0 1px rgba(22, 119, 255, 0.2);
      background: #f0f7ff;
    }

    &__icon {
      font-size: 18px;
      color: var(--shop-primary);
    }

    &__label {
      font-size: 13px;
      font-weight: 500;
      color: var(--shop-text);
    }

    &__desc {
      font-size: 12px;
      color: var(--shop-text-secondary);
      line-height: 1.5;
    }
  }

  .copy-modal__line {
    margin: 0 0 12px;
    font-size: 13px;
    color: var(--shop-text-secondary);
  }

  .copy-modal__warn {
    margin: 12px 0 0;
    font-size: 12px;
    line-height: 1.6;
    color: #d46b08;
  }

  @media (max-width: 900px) {
    .type-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .block-field {
      flex-direction: column;
      gap: 6px;

      &__label {
        flex: none;
        text-align: left;
        padding-top: 0;
      }
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .block-card,
    .type-card {
      transition: none !important;
    }
  }
</style>
