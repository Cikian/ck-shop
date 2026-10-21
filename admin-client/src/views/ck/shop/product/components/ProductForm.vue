<template>
  <a-drawer
    :open="visible"
    :title="drawerTitle"
    :width="drawerWidth"
    placement="right"
    :body-style="{ padding: '0', background: '#f7f8fa' }"
    :mask-closable="false"
    destroy-on-close
    @close="handleCancel"
  >
    <a-spin :spinning="confirmLoading">
      <a-tabs v-model:activeKey="activeTab" class="product-tabs">
        <!-- ① 基本信息 -->
        <a-tab-pane key="basic" tab="基本信息">
          <div class="panel">
            <a-form
              ref="basicFormRef"
              :model="formData"
              :rules="rules"
              class="panel-form form-grid"
            >
              <a-row :gutter="24">
                <a-col :xs="24" :xl="12">
                  <a-form-item label="SPU编码" name="spuCode">
                    <a-input v-model:value="formData.spuCode" placeholder="留空自动生成" allow-clear :disabled="disabled" />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :xl="12">
                  <a-form-item label="分类" name="categoryId">
                    <a-select
                      v-model:value="formData.categoryId"
                      placeholder="请选择分类"
                      allow-clear
                      show-search
                      option-filter-prop="label"
                      :options="categoryOptions"
                      :disabled="disabled"
                    />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :xl="12">
                  <a-form-item label="所属店铺" name="sysOrgCode">
                    <!--
                      运营（无 shop-store:manage-all）：归属由后端按操作人部门强制决定，
                      这里只读展示；查不到店铺映射时 Select 会直接显示 org_code 原文。
                    -->
                    <template v-if="!canManageStoreOrg">
                      <a-select :value="formData.sysOrgCode" :options="orgStoreOptions" :disabled="true" placeholder="未设置" />
                      <span class="field-hint">归属由账号所在部门决定，如需调整请联系管理员</span>
                    </template>
                    <template v-else>
                      <a-select
                        v-model:value="formData.sysOrgCode"
                        placeholder="请选择所属店铺"
                        show-search
                        option-filter-prop="label"
                        :options="orgStoreOptions"
                        :disabled="disabled"
                      />
                      <span class="field-hint">商品必须属于一个店铺（归属不可置空）；切换后它只在新店铺的前台可见</span>
                    </template>
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :xl="12">
                  <a-form-item label="基准价格" name="price">
                    <a-input-number v-model:value="formData.price" :min="0" :precision="2" style="width: 100%" :disabled="disabled" placeholder="SKU 未单独定价时回退此价格" />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :xl="12">
                  <a-form-item label="单位" name="unit">
                    <a-input v-model:value="formData.unit" placeholder="如 件 / 台 / 盒" allow-clear :disabled="disabled" />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :xl="12">
                  <a-form-item label="排序" name="sortOrder">
                    <a-input-number v-model:value="formData.sortOrder" :min="0" :precision="0" style="width: 100%" :disabled="disabled" />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :xl="12">
                  <a-form-item label="上架状态" name="status">
                    <a-radio-group v-model:value="formData.status" button-style="solid" :disabled="disabled">
                      <a-radio-button value="Y">上架</a-radio-button>
                      <a-radio-button value="N">下架</a-radio-button>
                    </a-radio-group>
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :xl="12">
                  <a-form-item label="首页推荐" name="isRecommend">
                    <a-switch v-model:checked="recommendChecked" checked-children="推荐" un-checked-children="不推荐" :disabled="disabled" />
                    <span class="field-hint">推荐商品会出现在店铺端首页「推荐商品」区</span>
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :xl="12">
                  <a-form-item label="上游来源" name="upstreamSource">
                    <template v-if="disabled">
                      <span v-if="upstreamSourceText">{{ upstreamSourceText }}</span>
                      <span v-else class="field-hint">未填写</span>
                    </template>
                    <template v-else>
                      <a-select
                        v-model:value="formData.upstreamSource"
                        placeholder="选择商品的上游货源"
                        allow-clear
                        show-search
                        option-filter-prop="label"
                        :options="upstreamOptions"
                      />
                      <span v-if="!upstreamOptions.length" class="field-hint">
                        字典 shop_upstream_source 暂无选项，可在 系统管理 → 数据字典 维护
                      </span>
                    </template>
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :xl="12">
                  <a-form-item label="上游链接" name="upstreamUrl">
                    <!-- 详情（只读）模式：直接渲染成可点击的链接，方便跳去上游核对 -->
                    <template v-if="disabled">
                      <a
                        v-if="upstreamHref"
                        :href="upstreamHref"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="upstream-link"
                        :title="upstreamHref"
                      >
                        <Icon icon="ant-design:link-outlined" />
                        <span class="upstream-link__text">{{ formData.upstreamUrl }}</span>
                      </a>
                      <span v-else class="field-hint">未填写</span>
                    </template>
                    <template v-else>
                      <a-input v-model:value="formData.upstreamUrl" placeholder="粘贴上游商品链接，如 item.taobao.com/xxx" allow-clear />
                      <span v-if="upstreamHref" class="field-hint">
                        <a :href="upstreamHref" target="_blank" rel="noopener noreferrer">打开链接</a>
                      </span>
                      <span v-else class="field-hint">不填协议会自动补 https://，保证详情页可点击跳转</span>
                    </template>
                  </a-form-item>
                </a-col>
                <a-col :span="24">
                  <a-form-item label="商品标签" name="tagIds">
                    <a-select
                      v-model:value="formData.tagIds"
                      mode="multiple"
                      placeholder="选择标签，用于列表筛选与店铺端展示"
                      allow-clear
                      show-search
                      option-filter-prop="label"
                      :options="tagOptions"
                      :disabled="disabled"
                    />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-form>

            <div class="readonly-summary">
              <a-descriptions size="small" :column="{ xs: 1, sm: 2, xl: 4 }" bordered>
                <a-descriptions-item label="总库存">
                  <a-tag v-if="summaryStock <= 0" color="red">无货</a-tag>
                  <a-tag v-else color="green">{{ summaryStock }}</a-tag>
                  <span class="field-hint">由 SKU 组合自动汇总，不可直接编辑</span>
                </a-descriptions-item>
                <a-descriptions-item label="售价区间">{{ priceRangeText }}</a-descriptions-item>
                <a-descriptions-item label="规格数">{{ formData.specs.length }}</a-descriptions-item>
                <a-descriptions-item label="组合数">{{ formData.combinations.length }}</a-descriptions-item>
              </a-descriptions>
            </div>
          </div>
        </a-tab-pane>

        <!-- ② 商品媒体 -->
        <a-tab-pane key="media" tab="商品媒体">
          <div class="panel">
            <div class="panel-section">
              <div class="panel-section__head">
                <span class="panel-section__title">主图</span>
                <span class="panel-section__desc">列表与店铺端卡片优先使用翻译封面，其次使用主图</span>
              </div>
              <div class="media-lead">
                <j-image-upload v-model:value="formData.mainImage" biz-path="/shop/product" text="上传主图" :disabled="disabled" />
                <div class="media-lead__note">
                  <p class="media-lead__line">建议使用 1:1 方图，长边不小于 800px。</p>
                  <p class="media-lead__line">店铺端商品列表、购物车等处都会用这张图。</p>
                </div>
              </div>
            </div>

            <div class="panel-section">
              <div class="panel-section__head">
                <span class="panel-section__title">商品图集</span>
                <span class="panel-section__desc">
                  共 {{ formData.images.length }} 张；拖动或使用 ↑↓ 调整顺序，标为「主图」的一张用于详情页默认展示
                </span>
                <a-button type="primary" ghost size="small" preIcon="ant-design:plus-outlined" :disabled="disabled" @click="addImage">
                  添加图片
                </a-button>
              </div>

              <a-empty v-if="!formData.images.length" :image="simpleImage" description="还没有图集图片，点右上角「添加图片」开始" />

              <!-- vuedraggable v4 必须使用 #item 插槽，默认插槽不会被读取 -->
              <VueDraggable
                v-else
                :list="formData.images"
                :item-key="(item) => item.__uid"
                :disabled="disabled"
                handle=".gallery-tile__handle"
                :animation="160"
                ghost-class="gallery-tile--ghost"
                class="gallery-grid"
                @end="onGalleryDragEnd"
              >
                <template #item="{ element: image, index }">
                  <div class="gallery-tile" :class="{ 'is-main': galleryMainIndex === index }">
                    <!-- 缩略图本身即编辑入口：弹层里放上传组件与链接输入，避免常驻一个长 URL 输入框 -->
                    <a-popover trigger="click" placement="bottom" overlay-class-name="gallery-popover">
                      <template #content>
                        <div class="gallery-pop__body">
                          <j-image-upload
                            :value="image.url"
                            biz-path="/shop/product"
                            text="上传图片"
                            :disabled="disabled"
                            @update:value="(val) => setImageUrl(image, val)"
                          />
                          <div class="gallery-pop__sep">或直接粘贴图片链接</div>
                          <a-input
                            :value="image.url"
                            size="small"
                            placeholder="https://…"
                            allow-clear
                            :disabled="disabled"
                            @update:value="(val) => setImageUrl(image, val)"
                          />
                        </div>
                      </template>
                      <button
                        type="button"
                        class="gallery-tile__media"
                        :disabled="disabled"
                        :aria-label="image.url ? `替换第 ${index + 1} 张图片` : `为第 ${index + 1} 张上传图片`"
                      >
                        <img v-if="image.url" :src="image.url" :alt="`商品图 ${index + 1}`" loading="lazy" decoding="async" />
                        <span v-else class="gallery-tile__placeholder">
                          <Icon icon="ant-design:plus-outlined" />
                          <em>上传图片</em>
                        </span>
                        <span class="gallery-tile__overlay">{{ image.url ? '点击替换 / 粘贴链接' : '点击上传' }}</span>
                      </button>
                    </a-popover>

                    <span class="gallery-tile__badge">{{ index + 1 }}</span>

                    <div class="gallery-tile__tools">
                      <Icon
                        icon="ant-design:holder-outlined"
                        class="gallery-tile__handle"
                        :class="{ 'is-disabled': disabled }"
                        title="按住拖动调整顺序"
                      />
                      <a-popconfirm
                        title="确认删除这张图片？"
                        ok-text="删除"
                        cancel-text="取消"
                        :disabled="disabled"
                        @confirm="removeImage(index)"
                      >
                        <a-button type="text" size="small" danger :disabled="disabled" :title="`删除第 ${index + 1} 张图片`">
                          <Icon icon="ant-design:delete-outlined" />
                        </a-button>
                      </a-popconfirm>
                    </div>

                    <div class="gallery-tile__foot">
                      <!-- 主图只能有一张，用单选语义；同时保证始终有一张被标为主图 -->
                      <a-radio :checked="galleryMainIndex === index" :disabled="disabled" @change="setMainImage(index, true)">主图</a-radio>
                      <span class="gallery-tile__move">
                        <a-button
                          type="text"
                          size="small"
                          :disabled="disabled || index === 0"
                          title="前移一位"
                          :aria-label="`将第 ${index + 1} 张图片前移`"
                          @click="moveImage(index, -1)"
                        >
                          <Icon icon="ant-design:arrow-up-outlined" />
                        </a-button>
                        <a-button
                          type="text"
                          size="small"
                          :disabled="disabled || index === formData.images.length - 1"
                          title="后移一位"
                          :aria-label="`将第 ${index + 1} 张图片后移`"
                          @click="moveImage(index, 1)"
                        >
                          <Icon icon="ant-design:arrow-down-outlined" />
                        </a-button>
                      </span>
                    </div>
                  </div>
                </template>
              </VueDraggable>
            </div>
          </div>
        </a-tab-pane>

        <!-- ③ 多语言内容 -->
        <a-tab-pane key="content" tab="多语言内容">
          <div class="panel">
            <div class="panel-section">
              <div class="panel-section__head">
                <span class="panel-section__title">语言内容</span>
                <span class="panel-section__desc">
                  中文标题必填；其它语言留空时店铺端会回退显示中文内容。
                </span>
              </div>

              <!--
                这里刻意不再使用 LangTabs：
                该组件同时服务分类/标签，内部有一套"外部 props 与本地副本互相同步"的机制，
                在商品这种"编辑时一次性整表回填"的场景下反复出现回填不显示、保存后丢失的问题。
                商品表单本来就只维护一个商品的文案，直接自己持有语言状态最稳。
              -->
              <a-tabs v-model:activeKey="activeLang" type="card" class="product-lang-tabs">
                <a-tab-pane v-for="lang in languages" :key="lang.code" :tab="lang.name || lang.code">
                  <!-- 左：文案主体；右：封面。两列铺开，避免文字区被拉得过宽 -->
                  <div class="lang-pane">
                    <div class="lang-pane__main">
                      <a-form
                        :key="`${lang.code}-${langFormKey}`"
                        class="lang-form form-grid"
                      >
                        <a-form-item label="商品标题">
                          <a-input
                            v-model:value="translationOf(lang.code).title"
                            placeholder="该语言下的商品标题"
                            allow-clear
                            :disabled="disabled"
                          />
                        </a-form-item>
                        <a-form-item label="副标题">
                          <a-input
                            v-model:value="translationOf(lang.code).subTitle"
                            placeholder="一句卖点描述，显示在标题下方"
                            allow-clear
                            :disabled="disabled"
                          />
                        </a-form-item>
                        <a-form-item label="商品简介">
                          <a-textarea
                            v-model:value="translationOf(lang.code).description"
                            :rows="8"
                            placeholder="简短介绍，展示在店铺端详情顶部；长图文请用「商品详情」页签"
                            :disabled="disabled"
                          />
                          <span class="field-hint field-hint--block">
                            只放一两句卖点；图片、分节、富文本等长图文请到「商品详情」页签用模块搭建
                          </span>
                        </a-form-item>
                      </a-form>
                    </div>

                    <div class="lang-pane__side">
                      <div class="lang-pane__side-title">语言封面</div>
                      <j-image-upload
                        v-model:value="translationOf(lang.code).coverUrl"
                        biz-path="/shop/product"
                        text="上传封面"
                        :disabled="disabled"
                      />
                      <p class="lang-pane__side-hint">未上传时，店铺端回退使用商品主图</p>
                    </div>
                  </div>

                  <!-- SEO 单独成块：低频字段不与主文案抢注意力 -->
                  <div class="lang-seo">
                    <div class="lang-seo__head">
                      <Icon icon="ant-design:global-outlined" />
                      SEO（选填）
                    </div>
                    <a-form :key="`${lang.code}-seo-${langFormKey}`" class="lang-form form-grid">
                      <a-row :gutter="20">
                        <a-col :xs="24" :xl="12">
                          <a-form-item label="SEO标题">
                            <a-input v-model:value="translationOf(lang.code).seoTitle" allow-clear :disabled="disabled" />
                          </a-form-item>
                        </a-col>
                        <a-col :xs="24" :xl="12">
                          <a-form-item label="SEO关键词">
                            <a-input v-model:value="translationOf(lang.code).seoKeywords" allow-clear :disabled="disabled" />
                          </a-form-item>
                        </a-col>
                      </a-row>
                      <a-form-item label="SEO描述">
                        <a-textarea v-model:value="translationOf(lang.code).seoDescription" :rows="3" :disabled="disabled" />
                      </a-form-item>
                    </a-form>
                  </div>
                </a-tab-pane>
              </a-tabs>
            </div>
          </div>
        </a-tab-pane>

        <!-- ④ 商品详情（结构化模块编辑器 + 实时预览） -->
        <a-tab-pane key="detail" tab="商品详情">
          <div class="panel panel--workspace">
            <a-alert
              type="info"
              show-icon
              class="panel-alert"
              message="商品详情按「模块」搭建，保存后店铺端按这里的顺序展示"
              description="左侧编辑模块，右侧实时预览；不同语言的内容相互独立，可用「复制到其它语言」快速复用。老商品没有模块时，店铺端仍按「商品简介」展示，不受影响。"
            />

            <!-- 详情页签使用独立的 activeDetailLang，避免与「多语言内容」的 activeLang 抢状态 -->
            <a-tabs v-model:activeKey="activeDetailLang" type="card" class="product-lang-tabs">
              <a-tab-pane v-for="lang in languages" :key="lang.code" :tab="lang.name || lang.code">
                <div class="detail-workspace">
                  <div class="detail-workspace__editor">
                    <ProductDetailEditor
                      :ref="(el) => setDetailEditorRef(lang.code, el)"
                      :blocks="detailBlocksOf(lang.code)"
                      :language-code="lang.code"
                      :languages="languages"
                      :disabled="disabled"
                      @update:blocks="(list) => setDetailBlocksOf(lang.code, list)"
                      @copy="(target) => copyDetailBlocks(lang.code, target)"
                    />
                  </div>
                  <div class="detail-workspace__preview" :style="{ width: previewColumnWidth }">
                    <ProductDetailPreview
                      :blocks="detailBlocksOf(lang.code)"
                      :params="paramsOf(lang.code)"
                      :description="translationDescriptionOf(lang.code)"
                      :product-title="translationTitleOf(lang.code)"
                      :lang-name="lang.name || lang.code"
                      :device="detailDevice"
                      @update:device="detailDevice = $event"
                    />
                  </div>
                </div>
              </a-tab-pane>
            </a-tabs>
          </div>
        </a-tab-pane>

        <!-- ⑤ 规格参数（展示用 KV，与「规格配置」的销售维度不同） -->
        <a-tab-pane key="params" tab="规格参数">
          <div class="panel panel--workspace">
            <a-alert
              type="info"
              show-icon
              class="panel-alert"
              message="规格参数是展示用的 Key-Value，不影响 SKU 选择与价格"
              description="例如「屏幕尺寸 / 6.7 英寸」。店铺端在「商品详情」上方整块展示；没有参数时该区块不显示（老商品沿用原有的自动行）。"
            />

            <a-tabs v-model:activeKey="activeParamLang" type="card" class="product-lang-tabs">
              <a-tab-pane v-for="lang in languages" :key="lang.code" :tab="lang.name || lang.code">
                <div class="detail-workspace">
                  <div class="detail-workspace__editor">
                    <ProductParamEditor
                      :ref="(el) => setParamEditorRef(lang.code, el)"
                      :params="paramsOf(lang.code)"
                      :language-code="lang.code"
                      :languages="languages"
                      :specs="formData.specs"
                      :disabled="disabled"
                      @update:params="(list) => setParamsOf(lang.code, list)"
                      @copy="(target) => copyParams(lang.code, target)"
                    />
                  </div>
                  <div class="detail-workspace__preview" :style="{ width: previewColumnWidth }">
                    <ProductDetailPreview
                      :blocks="detailBlocksOf(lang.code)"
                      :params="paramsOf(lang.code)"
                      :description="translationDescriptionOf(lang.code)"
                      :product-title="translationTitleOf(lang.code)"
                      :lang-name="lang.name || lang.code"
                      :device="detailDevice"
                      @update:device="detailDevice = $event"
                    />
                  </div>
                </div>
              </a-tab-pane>
            </a-tabs>
          </div>
        </a-tab-pane>

        <!-- ⑥ 规格配置 -->
        <a-tab-pane key="specs" tab="规格配置">
          <div class="panel">
            <a-alert
              type="info"
              show-icon
              class="panel-alert"
              message="规格决定客户能怎么选"
              description="例：颜色（白色/红色/黑色）× 内存（8G/16G）× 存储（256G/512G/1T）共 18 种组合。配置完请到「SKU 组合」页签生成组合，并删掉不存在的组合（例如白色 8G 1T）。"
            />
            <ProductSpecEditor v-model:specs="formData.specs" :disabled="disabled" @change="handleSpecsChange" />
          </div>
        </a-tab-pane>

        <!-- ⑦ SKU 组合 -->
        <a-tab-pane key="sku" tab="SKU 组合">
          <div class="panel">
            <a-alert
              type="info"
              show-icon
              class="panel-alert"
              message="每个组合一行，独立价格与库存"
              description="不存在的组合请直接删除该行，或把「售卖状态」切为不发售；店铺端会把这两种情况对应的规格值置灰，客户无法选中。"
            />
            <ProductSkuEditor
              v-model:combinations="formData.combinations"
              :specs="formData.specs"
              :base-price="formData.price"
              :disabled="disabled"
            />
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-spin>

    <template #footer>
      <div class="drawer-footer">
        <span v-if="!disabled" class="drawer-footer__hint">
          <Icon icon="ant-design:info-circle-outlined" />
          保存时会按当前规格与组合全量覆盖
        </span>
        <a-space>
          <a-button @click="handleCancel">取消</a-button>
          <a-button v-if="!disabled" :loading="confirmLoading" @click="submitForm(false)">保存</a-button>
          <a-button v-if="!disabled" type="primary" :loading="confirmLoading" @click="submitForm(true)">保存并继续编辑</a-button>
        </a-space>
      </div>
    </template>
  </a-drawer>
</template>

<script lang="ts" setup>
  import { computed, nextTick, reactive, ref } from 'vue';
  import { Empty } from 'ant-design-vue';
  import VueDraggable from 'vuedraggable';
  // 必须显式 import：模板里用的是 <j-image-upload>，靠这个 import 才能被 SFC 编译器解析。
  // 只依赖全局注册是不可靠的（删掉 import 后组件会静默渲染成空）。
  import JImageUpload from '/@/components/Form/src/jeecg/components/JImageUpload.vue';
  import ProductSpecEditor from './ProductSpecEditor.vue';
  import ProductSkuEditor from './ProductSkuEditor.vue';
  import ProductDetailEditor from './ProductDetailEditor.vue';
  import ProductDetailPreview from './ProductDetailPreview.vue';
  import ProductParamEditor from './ProductParamEditor.vue';
  import {
    DETAIL_BLOCK_LIMIT,
    DETAIL_PARAM_LIMIT,
    detailBlockForSubmit,
    findDetailBlockError,
    findParamError,
    normalizeDetailBlock,
    normalizeProductParam,
    productParamForSubmit,
  } from './productDetailMeta';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { listLang } from '/@/api/common/api';
  import { initDictOptions } from '/@/utils/dict';
  import { categoryList, queryById, saveOrUpdate, tagAll } from '../ShopProduct.api';
  import { all as storeAll } from '../../store/ShopStore.api';

  const props = withDefaults(defineProps<{ formDisabled?: boolean }>(), { formDisabled: false });

  const emit = defineEmits(['ok']);

  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;
  const { createMessage, createConfirm } = useMessage();
  const { hasPermission } = usePermission();

  const visible = ref(false);
  /** 只读详情模式：由 ProductModal 通过 setReadonly 控制 */
  const disableSubmit = ref(false);
  const confirmLoading = ref(false);
  const activeTab = ref('basic');
  const basicFormRef = ref();

  const drawerWidth = '86%';

  const categoryOptions = ref<any[]>([]);
  const tagOptions = ref<any[]>([]);
  /** 上游来源选项（来自系统字典 shop_upstream_source） */
  const upstreamOptions = ref<any[]>([]);
  /** 所属店铺选项：value = 店铺绑定的部门 org_code（/shop/store/all 的 departOrgCode） */
  const orgStoreOptions = ref<any[]>([]);

  /** 只有管理员/总部角色有 shop-store:manage-all，能改商品归属；运营只能看 */
  const canManageStoreOrg = computed(() => hasPermission('shop-store:manage-all'));

  /** 启用的语言列表（来自 /sys/lang，只返回 enabled='Y' 的） */
  const languages = ref<any[]>([]);
  const activeLang = ref('');
  /**
   * 「商品详情」「规格参数」各自持有独立的语言状态：
   * 两个新页签和老「多语言内容」页签互不同步，避免切页签时互相把 activeLang 改掉。
   */
  const activeDetailLang = ref('');
  const activeParamLang = ref('');
  /** 预览设备宽度（详情/参数页签共用，桌面 880 / 移动 390） */
  const detailDevice = ref<'desktop' | 'mobile'>('desktop');
  /** 校验失败时按语言聚焦到编辑器实例 */
  const detailEditorRefs = new Map<string, any>();
  const paramEditorRefs = new Map<string, any>();
  /** 切换商品时用它强制重建语言表单，避免上一份校验状态残留 */
  const langFormKey = ref(0);

  const emptyTranslation = {
    title: '',
    subTitle: '',
    description: '',
    seoTitle: '',
    seoKeywords: '',
    seoDescription: '',
    coverUrl: '',
  };

  function uid(prefix: string) {
    return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
  }

  /**
   * 取某个语言的文案对象（不存在则按语言列表补齐后返回），
   * 由模板直接 v-model，避免"本地副本 ↔ 外部 props"两套状态互相覆盖。
   */
  function translationOf(code: string) {
    let item = formData.translations.find((t: any) => t.languageCode === code);
    if (!item) {
      item = { languageCode: code, ...JSON.parse(JSON.stringify(emptyTranslation)) };
      formData.translations.push(item);
    }
    return item;
  }

  /** 按启用的语言重建/对齐文案数组，已有内容保留 */
  function syncTranslations(seed?: any[]) {
    const source: any[] = Array.isArray(seed) ? seed : [];
    const next = languages.value.map((lang) => {
      const existed = source.find((t: any) => t && t.languageCode === lang.code);
      return existed
        ? { ...JSON.parse(JSON.stringify(emptyTranslation)), ...existed, languageCode: lang.code }
        : { languageCode: lang.code, ...JSON.parse(JSON.stringify(emptyTranslation)) };
    });
    formData.translations = next;
  }

  /** 提交前清理：去掉无语言代码的行、保留原始字段 */
  function cleanTranslations() {
    return (formData.translations || [])
      .filter((item: any) => item && item.languageCode)
      .map((item: any) => {
        const copy = { ...item };
        delete copy.__uid;
        return copy;
      });
  }

  const formData = reactive<Record<string, any>>({
    id: '',
    spuCode: '',
    categoryId: undefined,
    // 所属店铺：值 = 店铺绑定的部门 org_code；留空则由后端按操作人部门自动归属
    sysOrgCode: undefined,
    upstreamSource: undefined,
    upstreamUrl: '',
    price: null,
    unit: '件',
    sortOrder: 0,
    status: 'Y',
    isRecommend: 'N',
    mainImage: '',
    tagIds: [],
    translations: [],
    images: [],
    specs: [],
    combinations: [],
    /** 商品详情模块：扁平数组，元素形如 { languageCode, blockType, content, sortOrder, __uid } */
    detailBlocks: [],
    /** 规格参数 KV：扁平数组，元素形如 { languageCode, paramName, paramValue, sortOrder, __uid } */
    params: [],
  });

  const rules = {
    spuCode: [{ max: 36, message: 'SPU编码不超过36个字符' }],
    price: [
      { required: true, message: '请输入基准价格' },
      {
        validator: (_rule: any, value: any) => {
          if (value === null || value === undefined || value === '') return Promise.resolve();
          return Number(value) >= 0 ? Promise.resolve() : Promise.reject('价格不能为负数');
        },
      },
    ],
    unit: [{ max: 20, message: '单位不超过20个字符' }],
  };

  const disabled = computed(() => props.formDisabled || disableSubmit.value);

  /** 供父组件切换到只读详情模式 */
  function setReadonly(val: boolean) {
    disableSubmit.value = !!val;
  }

  /** 语言显示名（复制提示、校验提示用） */
  function langDisplayName(code: string): string {
    const hit = languages.value.find((lang: any) => lang && lang.code === code);
    return hit?.name || code;
  }

  function translationDescriptionOf(code: string): string {
    const hit = formData.translations.find((item: any) => item && item.languageCode === code);
    return hit?.description || '';
  }

  function translationTitleOf(code: string): string {
    const hit = formData.translations.find((item: any) => item && item.languageCode === code);
    return hit?.title || '';
  }

  // ---------- 商品详情 / 规格参数：按语言读写扁平数组 ----------
  /**
   * 当前语言的模块（已按 sortOrder 排序）。
   * 注意：返回的是同一个对象引用数组，编辑器里的 v-model 会直接改到 formData，预览因此实时生效。
   */
  function detailBlocksOf(code: string) {
    return (formData.detailBlocks || [])
      .filter((block: any) => block && block.languageCode === code)
      .sort((a: any, b: any) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  }

  /** 结构变化（增删/排序）回写：只替换当前语言，其它语言原样保留 */
  function setDetailBlocksOf(code: string, list: any[]) {
    const others = (formData.detailBlocks || []).filter((block: any) => block.languageCode !== code);
    formData.detailBlocks = [...others, ...(list || []).map((block: any, index: number) => ({ ...block, languageCode: code, sortOrder: index }))];
  }

  function paramsOf(code: string) {
    return (formData.params || [])
      .filter((param: any) => param && param.languageCode === code)
      .sort((a: any, b: any) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  }

  function setParamsOf(code: string, list: any[]) {
    const others = (formData.params || []).filter((param: any) => param.languageCode !== code);
    formData.params = [...others, ...(list || []).map((param: any, index: number) => ({ ...param, languageCode: code, sortOrder: index }))];
  }

  /**
   * 预览列宽：移动 390 + 内边距 ≈ 430；桌面按店铺端内容列 880（契约 3.2）。
   *
   * 桌面宽度用 `min(820px, 45%)` 而不是写死 880px：
   * 抽屉宽度是 86% 视口，在 1600px 屏幕上内容区约 1330px，
   * 520(编辑最小宽) + 880 + 20(gap) 会换行 —— 预览就跑到编辑区下方，
   * 违背「编辑时在旁边实时看效果」的需求（实测截图确认过）。
   * 改成百分比后，1280 以上都能并排；更窄时仍按响应式换行，不做横向挤压。
   */
  const previewColumnWidth = computed(() => (detailDevice.value === 'mobile' ? '430px' : 'min(820px, 45%)'));

  // ---------- 编辑器实例（校验失败时聚焦） ----------
  function setDetailEditorRef(code: string, instance: any) {
    if (instance) {
      detailEditorRefs.set(code, instance);
    } else {
      detailEditorRefs.delete(code);
    }
  }

  function setParamEditorRef(code: string, instance: any) {
    if (instance) {
      paramEditorRefs.set(code, instance);
    } else {
      paramEditorRefs.delete(code);
    }
  }

  // ---------- 复制到其它语言（覆盖前二次确认，契约 3.1） ----------
  function copyDetailBlocks(sourceCode: string, targetCode: string) {
    if (!targetCode || targetCode === sourceCode) {
      return;
    }
    const source = detailBlocksOf(sourceCode);
    if (!source.length) {
      createMessage.warning('当前语言还没有可复制的详情模块');
      return;
    }
    const existed = (formData.detailBlocks || []).filter((block: any) => block.languageCode === targetCode).length;
    const sourceName = langDisplayName(sourceCode);
    const targetName = langDisplayName(targetCode);
    const doCopy = () => {
      const cloned = source.map((block: any, index: number) => ({
        languageCode: targetCode,
        blockType: block.blockType,
        // content 深拷贝，连同 radius / itemMargin / columns 一起复制
        content: JSON.parse(JSON.stringify(block.content || {})),
        sortOrder: index,
        // 四方向间距是模块顶层字段，必须一起复制（契约 3.1）
        marginTop: block.marginTop ?? 0,
        marginRight: block.marginRight ?? 0,
        marginBottom: block.marginBottom ?? 0,
        marginLeft: block.marginLeft ?? 0,
        __uid: uid('blk'),
      }));
      formData.detailBlocks = [...(formData.detailBlocks || []).filter((block: any) => block.languageCode !== targetCode), ...cloned];
      activeDetailLang.value = targetCode;
      createMessage.success(`已把${sourceName}的 ${source.length} 个模块复制到${targetName}`);
    };
    if (!existed) {
      doCopy();
      return;
    }
    createConfirm({
      iconType: 'warning',
      title: '覆盖目标语言的商品详情？',
      content: `${targetName}已有 ${existed} 个模块，复制后将整体覆盖为${sourceName}的 ${source.length} 个模块，且不可撤销。`,
      okText: '覆盖复制',
      cancelText: '取消',
      onOk: () => doCopy(),
    });
  }

  function copyParams(sourceCode: string, targetCode: string) {
    if (!targetCode || targetCode === sourceCode) {
      return;
    }
    const source = paramsOf(sourceCode);
    if (!source.length) {
      createMessage.warning('当前语言还没有可复制的规格参数');
      return;
    }
    const existed = (formData.params || []).filter((param: any) => param.languageCode === targetCode).length;
    const sourceName = langDisplayName(sourceCode);
    const targetName = langDisplayName(targetCode);
    const doCopy = () => {
      const cloned = source.map((param: any, index: number) => ({
        languageCode: targetCode,
        paramName: param.paramName,
        paramValue: param.paramValue,
        sortOrder: index,
        __uid: uid('prm'),
      }));
      formData.params = [...(formData.params || []).filter((param: any) => param.languageCode !== targetCode), ...cloned];
      activeParamLang.value = targetCode;
      createMessage.success(`已把${sourceName}的 ${source.length} 条参数复制到${targetName}`);
    };
    if (!existed) {
      doCopy();
      return;
    }
    createConfirm({
      iconType: 'warning',
      title: '覆盖目标语言的规格参数？',
      content: `${targetName}已有 ${existed} 条参数，复制后将整体覆盖为${sourceName}的 ${source.length} 条，且不可撤销。`,
      okText: '覆盖复制',
      cancelText: '取消',
      onOk: () => doCopy(),
    });
  }

  // ---------- 提交前校验（契约 3.3） ----------
  /** 已启用语言 + 数据里出现过的其它语言（禁用语言的内容原样保留提交，不参与校验） */
  function detailLanguageCodes(): string[] {
    const codes: string[] = languages.value.map((lang: any) => lang?.code).filter(Boolean);
    (formData.detailBlocks || []).forEach((block: any) => {
      if (block?.languageCode && !codes.includes(block.languageCode)) {
        codes.push(block.languageCode);
      }
    });
    return codes;
  }

  function paramLanguageCodes(): string[] {
    const codes: string[] = languages.value.map((lang: any) => lang?.code).filter(Boolean);
    (formData.params || []).forEach((param: any) => {
      if (param?.languageCode && !codes.includes(param.languageCode)) {
        codes.push(param.languageCode);
      }
    });
    return codes;
  }

  /** 返回第一个问题（只校验当前启用的语言，避免禁用语言的历史数据把人锁死） */
  function findDetailBlocksIssue(): { languageCode: string; message: string; blockUid?: string } | null {
    for (const lang of languages.value) {
      const code = lang?.code;
      if (!code) {
        continue;
      }
      const blocks = detailBlocksOf(code);
      if (blocks.length > DETAIL_BLOCK_LIMIT) {
        return {
          languageCode: code,
          message: `${langDisplayName(code)}的「商品详情」有 ${blocks.length} 个模块，超过 ${DETAIL_BLOCK_LIMIT} 个上限，请精简后再保存`,
        };
      }
      const error = findDetailBlockError(blocks);
      if (error) {
        return {
          languageCode: code,
          blockUid: error.block?.__uid,
          message: `${langDisplayName(code)}：第 ${error.index + 1} 个模块${error.message}`,
        };
      }
    }
    return null;
  }

  function findParamsIssue(): { languageCode: string; message: string } | null {
    for (const lang of languages.value) {
      const code = lang?.code;
      if (!code) {
        continue;
      }
      const params = paramsOf(code);
      if (params.length > DETAIL_PARAM_LIMIT) {
        return {
          languageCode: code,
          message: `${langDisplayName(code)}的「规格参数」有 ${params.length} 条，超过 ${DETAIL_PARAM_LIMIT} 条上限，请精简后再保存`,
        };
      }
      const error = findParamError(params);
      if (error) {
        return { languageCode: code, message: `${langDisplayName(code)}：${error.message}` };
      }
    }
    return null;
  }

  // ---------- 提交 payload（content 传对象，剔除 __uid） ----------
  function buildDetailBlocksPayload() {
    const payload: any[] = [];
    detailLanguageCodes().forEach((code) => {
      detailBlocksOf(code).forEach((block: any, index: number) => {
        payload.push(detailBlockForSubmit(block, code, index));
      });
    });
    return payload;
  }

  function buildParamsPayload() {
    const payload: any[] = [];
    paramLanguageCodes().forEach((code) => {
      paramsOf(code).forEach((param: any, index: number) => {
        payload.push(productParamForSubmit(param, code, index));
      });
    });
    return payload;
  }

  const recommendChecked = computed({
    get: () => formData.isRecommend === 'Y',
    set: (val: boolean) => {
      formData.isRecommend = val ? 'Y' : 'N';
    },
  });

  const drawerTitle = computed(() => (disabled.value ? '商品详情' : formData.id ? '编辑商品' : '新增商品'));

  const summaryStock = computed(() =>
    formData.combinations.filter((item: any) => item.saleable !== 'N').reduce((acc: number, item: any) => acc + (Number(item.stock) || 0), 0)
  );

  const priceRangeText = computed(() => {    const prices = formData.combinations
      .filter((item: any) => item.saleable !== 'N')
      .map((item: any) => (item.price === null || item.price === undefined || item.price === '' ? Number(formData.price) : Number(item.price)))
      .filter((n: number) => Number.isFinite(n));
    if (!prices.length) {
      return formData.price === null || formData.price === undefined ? '-' : `¥${Number(formData.price).toFixed(2)}`;
    }
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    return min === max ? `¥${min.toFixed(2)}` : `¥${min.toFixed(2)} ~ ¥${max.toFixed(2)}`;
  });

  /**
   * 上游链接规范化：缺少协议时补 https://。
   * 目的只有一个——保证详情页里这个链接**一定能点开**（没有协议的 href 会被当成相对路径）。
   */
  function normalizeUpstreamUrl(raw: any): string {
    const url = String(raw ?? '').trim();
    if (!url) {
      return '';
    }
    return /^https?:\/\//i.test(url) ? url : `https://${url}`;
  }

  const upstreamHref = computed(() => normalizeUpstreamUrl(formData.upstreamUrl));

  /** 上游来源的当前显示文案（详情页只读展示用） */
  const upstreamSourceText = computed(() => {
    const hit = upstreamOptions.value.find((item: any) => item.value === formData.upstreamSource);
    return hit?.label || formData.upstreamSource || '';
  });

  /**
   * 图集默认主图：库里可能存在"没有任何一张标为主图"的历史数据，
   * 这种情况下把第一张视作主图，保证界面上永远有且只有一张被标记。
   */
  const galleryMainIndex = computed(() => {
    const marked = formData.images.findIndex((image: any) => image.isMain === 'Y');
    if (marked >= 0) {
      return marked;
    }
    return formData.images.length ? 0 : -1;
  });

  function addImage() {
    formData.images.push({
      url: '',
      isMain: formData.images.length === 0 ? 'Y' : 'N',
      sortOrder: formData.images.length,
      __uid: uid('img'),
    });
  }

  /** 弹层里改动图片地址（上传组件与手工粘贴链接都走这里） */
  function setImageUrl(image: any, val: any) {
    image.url = typeof val === 'string' ? val : '';
  }

  function removeImage(index: number) {
    const removed = formData.images.splice(index, 1)[0];
    // 删掉主图时自动把第一张设为主图
    if (removed?.isMain === 'Y' && formData.images.length) {
      formData.images[0].isMain = 'Y';
    }
    reindexImages();
  }

  /**
   * 前移 / 后移一位。
   * WCAG 2.2 AA 的「Dragging Movements」要求：作者提供的拖拽操作必须有单指针替代方式，
   * 因此拖拽之外必须同时提供按钮；键盘用户也能靠它完成排序。
   */
  function moveImage(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= formData.images.length) {
      return;
    }
    const list = formData.images;
    const [item] = list.splice(index, 1);
    list.splice(target, 0, item);
    reindexImages();
  }

  function setMainImage(index: number, _checked: boolean) {
    // 主图是单选语义：点谁谁成为主图，其余一律取消
    formData.images.forEach((image: any, i: number) => {
      image.isMain = i === index ? 'Y' : 'N';
    });
  }

  function onGalleryDragEnd() {
    reindexImages();
  }

  function reindexImages() {
    formData.images.forEach((image: any, index: number) => {
      image.sortOrder = index;
    });
  }

  /** 规格变化时，如果组合引用了已删除的规格值，做一次清理提示 */
  function handleSpecsChange() {
    const validValueIds = new Set<string>();
    formData.specs.forEach((spec: any) => {
      (spec.values || []).forEach((value: any) => {
        if (value.id) validValueIds.add(value.id);
        if (value.tempId) validValueIds.add(value.tempId);
      });
    });
    if (!formData.combinations.length || !validValueIds.size) {
      return;
    }
    const before = formData.combinations.length;
    formData.combinations = formData.combinations.filter((combo: any) =>
      (combo.specValueRefs || []).every((ref: string) => validValueIds.has(ref))
    );
    const removed = before - formData.combinations.length;
    if (removed > 0) {
      createMessage.warning(`规格值发生变化，已自动移除 ${removed} 个失效组合，请到「SKU 组合」页签确认`);
    }
  }

  /** 加载下拉选项 */
  async function loadOptions() {
    try {
      const [categoryRes, tagRes, upstreamRes, storeRes] = await Promise.all([
        categoryList({ pageNo: 1, pageSize: 500, isShow: 'Y' }),
        tagAll(),
        // 上游来源取自系统字典：系统管理 → 数据字典 → shop_upstream_source
        initDictOptions('shop_upstream_source').catch(() => []),
        // 所属店铺：启用中的店铺
        storeAll().catch(() => []),
      ]);
      const categoryRecords = categoryRes?.records || categoryRes || [];
      categoryOptions.value = categoryRecords.map((item: any) => ({ label: item.name || item.id, value: item.id }));
      const tagRecords = tagRes || [];
      tagOptions.value = tagRecords.map((item: any) => ({ label: pickTranslationName(item) || item.code, value: item.id }));
      upstreamOptions.value = (upstreamRes || []).map((item: any) => ({
        label: item?.text ?? item?.label ?? item?.value,
        value: item?.value,
      }));
      // 只列绑定了部门的店铺：商品归属提交的是部门 org_code，没绑部门的店铺无法承载归属
      orgStoreOptions.value = (storeRes || [])
        .filter((item: any) => !!item?.departOrgCode)
        .map((item: any) => ({
          label: `${item.name}（${item.code}）`,
          value: item.departOrgCode,
        }));
    } catch (e) {
      console.warn('加载下拉选项失败', e);
    }
  }

  /** 加载启用的语言列表（只需成功一次） */
  async function loadLanguages() {
    if (languages.value.length) {
      return;
    }
    try {
      const res = await listLang();
      const list = Array.isArray(res) ? res : res?.result || res?.data || [];
      languages.value = list.filter((lang: any) => lang && lang.code);
      if (!activeLang.value && languages.value.length) {
        activeLang.value = languages.value[0].code;
      }
    } catch (e) {
      console.warn('加载语言列表失败', e);
    }
  }

  function pickTranslationName(record: any): string {
    const list = record?.translations;
    if (!Array.isArray(list) || !list.length) return '';
    const zh = list.find((t: any) => t.languageCode === 'zh' || t.languageCode === 'zh-CN');
    return (zh || list[0])?.name || '';
  }

  /** 重置为空白表单；keepTab=true 时保留当前页签（保存后回填场景） */
  function resetForm(keepTab = false) {
    Object.assign(formData, {
      id: '',
      spuCode: '',
      categoryId: undefined,
      sysOrgCode: undefined,
      upstreamSource: undefined,
      upstreamUrl: '',
      price: null,
      unit: '件',
      sortOrder: 0,
      status: 'Y',
      isRecommend: 'N',
      mainImage: '',
      tagIds: [],
      translations: [],
      images: [],
      specs: [],
      combinations: [],
      detailBlocks: [],
      params: [],
    });
    activeDetailLang.value = '';
    activeParamLang.value = '';
    detailDevice.value = 'desktop';
    if (!keepTab) {
      activeTab.value = 'basic';
    }
  }

  /** 新增 */
  async function add() {
    resetForm();
    visible.value = true;
    await Promise.all([loadOptions(), loadLanguages()]);
    await nextTick();
    basicFormRef.value?.clearValidate?.();
    // 用当前启用语言铺一遍空白文案，表单即可直接编辑
    syncTranslations([]);
    activeLang.value = languages.value[0]?.code || '';
    activeDetailLang.value = languages.value[0]?.code || '';
    activeParamLang.value = languages.value[0]?.code || '';
    langFormKey.value++;
  }

  /** 编辑 / 详情。options.keepTab=true 用于保存后的回填，避免把用户踢回"基本信息" */
  async function edit(record: any, options: { keepTab?: boolean } = {}) {
    resetForm(!!options.keepTab);
    visible.value = true;
    await Promise.all([loadOptions(), loadLanguages()]);
    await nextTick();
    basicFormRef.value?.clearValidate?.();

    if (!record?.id) {
      syncTranslations([]);
      activeDetailLang.value = languages.value[0]?.code || '';
      activeParamLang.value = languages.value[0]?.code || '';
      langFormKey.value++;
      return;
    }
    confirmLoading.value = true;
    try {
      const detail = await queryById({ id: record.id });
      if (!detail) {
        createMessage.warning('未查询到商品详情');
        return;
      }
      /** 回填时没有 languageCode 的历史行统一落到第一个启用语言 */
      const primaryLanguageCode = languages.value[0]?.code || 'zh';
      Object.assign(formData, {
        id: detail.id,
        spuCode: detail.spuCode || '',
        categoryId: detail.categoryId || undefined,
        // 所属店铺：后端 join 出来的归属列，直接落在 ProductDetailVO 上
        sysOrgCode: detail.sysOrgCode || undefined,
        upstreamSource: detail.upstreamSource || undefined,
        upstreamUrl: detail.upstreamUrl || '',
        price: detail.price ?? null,
        unit: detail.unit || '件',
        sortOrder: detail.sortOrder ?? 0,
        status: detail.status || 'Y',
        isRecommend: detail.isRecommend || 'N',
        mainImage: detail.mainImage || '',
        tagIds: detail.tagIds || [],
        translations: [],
        images: (detail.images || []).map((image: any) => ({
          url: image.url,
          isMain: image.isMain || 'N',
          sortOrder: image.sortOrder ?? 0,
          __uid: uid('img'),
        })),
        specs: (detail.specs || []).map((spec: any) => ({
          id: spec.id,
          name: spec.name,
          presetCode: spec.presetCode,
          sortOrder: spec.sortOrder ?? 0,
          isRequired: spec.isRequired || 'Y',
          values: (spec.values || []).map((value: any) => ({
            id: value.id,
            value: value.value,
            sortOrder: value.sortOrder ?? 0,
          })),
        })),
        combinations: (detail.skus || []).map((sku: any) => ({
          id: sku.id,
          skuCode: sku.skuCode || '',
          combinationKey: sku.combinationKey,
          combinationName: sku.combinationName,
          specValueRefs: sku.specValueIds || [],
          price: sku.price ?? null,
          stock: sku.stock ?? 0,
          imageUrl: sku.imageUrl || '',
          saleable: sku.saleable || 'Y',
        })),
        /*
         * 商品详情 / 规格参数：后端返回 content 是 JSON 对象（不是字符串），直接存对象。
         * normalizeXxx 会把契约 1.3 的字段补齐、跳过未知 blockType，并补前端 __uid。
         */
        detailBlocks: (detail.detailBlocks || [])
          .map((block: any) => normalizeDetailBlock(block, primaryLanguageCode))
          .filter((block: any) => !!block),
        params: (detail.params || [])
          .map((param: any) => normalizeProductParam(param, primaryLanguageCode))
          .filter((param: any) => !!param),
      });
      reindexImages();
      // 按启用语言回填文案（直接写进 formData，模板 v-model 读写同一份数据）
      syncTranslations(detail.translations || []);
      // 中文缺失时用详情标题兜底，避免"库里有数据但表单看着空"
      if (detail.title) {
        const primary = formData.translations.find((t: any) => t.languageCode === 'zh')
          || formData.translations.find((t: any) => t.languageCode === 'en')
          || formData.translations[0];
        if (primary && !String(primary.title || '').trim()) {
          primary.title = detail.title;
        }
      }
      activeLang.value = languages.value[0]?.code || '';
      activeDetailLang.value = languages.value[0]?.code || '';
      activeParamLang.value = languages.value[0]?.code || '';
      await nextTick();
      langFormKey.value++;
    } catch (e: any) {
      createMessage.error(e?.message || '商品详情加载失败');
    } finally {
      confirmLoading.value = false;
    }
  }

  /** 校验并提交 */
  async function submitForm(continueEdit: boolean) {
    if (disabled.value) {
      return;
    }
    try {
      await basicFormRef.value.validate();
    } catch (error: any) {
      activeTab.value = 'basic';
      createMessage.warning('请先补全基本信息中的必填项');
      return;
    }

    // 多语言：至少填写一种语言的标题
    const translations = cleanTranslations();
    const filledCount = translations.filter((item: any) => (item.title || '').trim() !== '').length;
    if (!filledCount) {
      activeTab.value = 'content';
      createMessage.warning('请至少为一种语言填写商品标题');
      return;
    }

    // 规格校验：规格名必填、可选值至少有 1 个、同一规格内值不重复
    const specs = (formData.specs || []).map((spec: any, specIndex: number) => ({
      id: spec.id,
      name: (spec.name || '').trim(),
      presetCode: spec.presetCode,
      isRequired: spec.isRequired || 'Y',
      sortOrder: specIndex,
      values: (spec.values || [])
        .filter((value: any) => (value.value || '').trim() !== '')
        .map((value: any, valueIndex: number) => ({
          id: value.id,
          // 稳定标识：有后端主键用主键，否则复用编辑器给的 tempId/__uid，最后才兜底生成
          tempId: value.id || value.tempId || value.__uid || uid('tmpval'),
          value: (value.value || '').trim(),
          sortOrder: valueIndex,
        })),
    }));

    const nameless = specs.filter((spec: any) => !spec.name);
    if (nameless.length) {
      activeTab.value = 'specs';
      createMessage.warning('存在未填写名称的规格');
      return;
    }
    const emptySpec = specs.filter((spec: any) => spec.values.length === 0);
    if (emptySpec.length) {
      activeTab.value = 'specs';
      createMessage.warning(`规格「${emptySpec.map((s: any) => s.name).join('、')}」还没有可选值`);
      return;
    }
    const duplicateSpecName = specs.map((s: any) => s.name).find((name: string, index: number, arr: string[]) => arr.indexOf(name) !== index);
    if (duplicateSpecName) {
      activeTab.value = 'specs';
      createMessage.warning(`规格名重复：${duplicateSpecName}`);
      return;
    }
    for (const spec of specs) {
      const texts = spec.values.map((value: any) => value.value);
      if (new Set(texts).size !== texts.length) {
        activeTab.value = 'specs';
        createMessage.warning(`规格「${spec.name}」存在重复的可选值`);
        return;
      }
    }
    // 新增的规格值补稳定临时主键，供组合引用（后端按 tempId 换真实主键）
    specs.forEach((spec: any) => {
      spec.values.forEach((value: any) => {
        if (!value.tempId) {
          value.tempId = value.id || uid('tmpval');
        }
      });
    });

    /**
     * 组合引用归一化。
     * SKU 编辑器历史上可能把引用写成了 "规格名::值" 兜底形态（当规格值没有 id/tempId 时），
     * 这里统一翻译成稳定 tempId，避免后端解析不到而整批丢弃组合。
     */
    const refToTempId = new Map<string, string>();
    specs.forEach((spec: any) => {
      spec.values.forEach((value: any) => {
        if (value.id) {
          refToTempId.set(value.id, value.tempId);
        }
        refToTempId.set(value.tempId, value.tempId);
        refToTempId.set(`${spec.name}::${value.value}`, value.tempId);
      });
    });

    const combinations = (formData.combinations || [])
      .map((combo: any) => {
        const rawRefs: string[] = combo.specValueRefs || [];
        const normalizedRefs = rawRefs.map((ref) => refToTempId.get(ref) ?? ref);
        // 按规格顺序取出每个规格被选中的值（编辑器写引用时就是按规格顺序，这里按 tempId 再定位一次，避免顺序漂移）
        const picked = specs.map((spec: any, specIndex: number) => {
          const byIndex = spec.values.find((value: any) => value.tempId === normalizedRefs[specIndex]);
          if (byIndex) return byIndex;
          return spec.values.find((value: any) => normalizedRefs.includes(value.tempId));
        });
        return {
          id: combo.id,
          skuCode: combo.skuCode || null,
          combinationKey: picked.map((value: any) => value?.tempId || '').join('-'),
          combinationName: picked.map((value: any) => value?.value || '').join('/'),
          specValueRefs: picked.map((value: any) => value?.tempId || ''),
          price: combo.price === '' || combo.price === undefined ? null : combo.price,
          stock: Number(combo.stock) || 0,
          imageUrl: combo.imageUrl || null,
          saleable: combo.saleable || 'Y',
        };
      })
      .filter(
        (combo: any) =>
          (combo.specValueRefs || []).length === specs.length
          && specs.length > 0
          && combo.specValueRefs.every(Boolean)
      );

    if (specs.length && !combinations.length) {
      activeTab.value = 'sku';
      createMessage.warning('已配置规格，但没有任何 SKU 组合。请至少保留一个组合，或用「一键生成全部组合」重新生成');
      return;
    }

    // 组合必须覆盖到每一个规格：新增规格后会缺一维，这里明确拦住并给出可执行提示
    if (specs.length && combinations.length) {
      const incomplete = combinations.filter((combo: any) => {
        const refs = combo?.specValueRefs || [];
        return refs.length !== specs.length || refs.some((ref: any) => !ref);
      });
      if (incomplete.length) {
        activeTab.value = 'sku';
        createMessage.warning(
          `有 ${incomplete.length} 个组合缺少规格值（多因新增了规格）。请补齐这些行，或删除它们后重新生成`
        );
        return;
      }
    }

    // 商品详情（契约 3.3）：图片必须有 url、文本/富文本不能全空、视频链接可解析、每语言 ≤ 50 个
    const detailIssue = findDetailBlocksIssue();
    if (detailIssue) {
      activeTab.value = 'detail';
      activeDetailLang.value = detailIssue.languageCode;
      createMessage.warning(detailIssue.message);
      await nextTick();
      await nextTick();
      detailEditorRefs.get(detailIssue.languageCode)?.focusBlock?.(detailIssue.blockUid);
      return;
    }

    // 规格参数（契约 3.3）：参数名、参数值必填，每语言 ≤ 100 条
    const paramIssue = findParamsIssue();
    if (paramIssue) {
      activeTab.value = 'params';
      activeParamLang.value = paramIssue.languageCode;
      createMessage.warning(paramIssue.message);
      await nextTick();
      await nextTick();
      paramEditorRefs.get(paramIssue.languageCode)?.focusFirstInvalid?.();
      return;
    }

    const payload: Record<string, any> = {
      id: formData.id || undefined,
      spuCode: formData.spuCode || undefined,
      categoryId: formData.categoryId || null,
      // 所属店铺：不传时由后端按操作人部门自动归属（运营只能如此）；商品归属不可为空
      sysOrgCode: formData.sysOrgCode || undefined,
      upstreamSource: formData.upstreamSource || null,
      upstreamUrl: normalizeUpstreamUrl(formData.upstreamUrl) || null,
      price: formData.price,
      unit: formData.unit,
      sortOrder: formData.sortOrder ?? 0,
      status: formData.status,
      isRecommend: formData.isRecommend,
      mainImage: formData.mainImage || null,
      tagIds: formData.tagIds || [],
      translations,
      images: (formData.images || [])
        .filter((image: any) => (image.url || '').trim() !== '')
        .map((image: any, index: number) => ({
          url: image.url.trim(),
          isMain: image.isMain || (index === 0 ? 'Y' : 'N'),
          sortOrder: index,
        })),
      specs,
      combinations,
      // 商品详情 / 规格参数：整体替换语义，content 传对象；空数组表示清空该商品全部语言
      detailBlocks: buildDetailBlocksPayload(),
      params: buildParamsPayload(),
    };
    // 确保拖拽顺序被提交（后端也按数组顺序重排，双保险）
    payload.specs.forEach((spec: any, specIndex: number) => (spec.sortOrder = specIndex));

    confirmLoading.value = true;
    let keepLoading = false;
    try {
      const isCreate = !formData.id;
      const res = await saveOrUpdate(payload, !isCreate);
      if (!res?.success) {
        createMessage.warning(res?.message || '保存失败');
        return;
      }
      createMessage.success(res.message || '保存成功');

      if (!continueEdit) {
        visible.value = false;
        emit('ok');
        return;
      }

      /*
       * 「保存并继续编辑」不再重新拉取详情，原因有二：
       * 1) 回填必须先 resetForm() 清空表单，一旦详情请求失败，用户看到的就是
       *    "提示保存成功、但商品信息全空了"（需要关掉重开才恢复）；
       * 2) 页面需要展示的汇总值（总库存、价格区间）本来就由 formData 本地算出，
       *    并不依赖服务端回传，所以这次请求没有必须存在的理由。
       * 保持表单原样，用户可以直接接着改。
       */
      if (isCreate) {
        // 新建时必须拿到主键，否则再次保存会重复创建。
        // 只接受"看起来像主键"的返回值，避免把提示语（如"添加成功！"）当成 id 去查详情。
        const result = res?.result;
        const looksLikeId = typeof result === 'string' && /^[A-Za-z0-9_-]{8,}$/.test(result);
        if (looksLikeId) {
          formData.id = result as string;
        } else {
          // 拿不到可靠主键：关闭抽屉并刷新列表，避免用户误以为还在编辑同一个商品
          visible.value = false;
          emit('ok');
          createMessage.info('已保存。请重新打开该商品继续编辑');
          return;
        }
      }
      // 编辑场景：主键本来就在表单里，无需任何回填
    } catch (e: any) {
      createMessage.error(e?.message || '保存失败');
    } finally {
      if (!keepLoading) {
        confirmLoading.value = false;
      }
    }
  }

  function handleCancel() {
    visible.value = false;
  }

  defineExpose({ add, edit, disableSubmit, setReadonly });
</script>

<style lang="less" scoped>
  .product-tabs {
    :deep(.ant-tabs-nav) {
      margin: 0;
      padding: 0 20px;
      background: #fff;
      position: sticky;
      top: 0;
      z-index: 5;
      border-bottom: 1px solid #f0f0f0;
    }
  }

  .panel {
    /* 面板整体给足宽度：SKU 组合表格列多，收得太窄会出现横向滚动条 */
    max-width: 1360px;
    margin: 0 auto;
    padding: 20px 24px 32px;
    background: #f7f8fa;
  }

  /* 「商品详情」「规格参数」页签：左编辑 + 右预览需要更宽的工作区 */
  .panel--workspace {
    max-width: 1680px;
  }

  .detail-workspace {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 20px;

    &__editor {
      flex: 1 1 440px;
      min-width: 0;
      max-width: 100%;
    }

    &__preview {
      flex: 0 1 auto;
      min-width: 360px;
      max-width: 100%;
      /* 拉伸到与左列同高，右侧预览才能在自己的容器内 sticky 吸顶 */
      align-self: stretch;
    }

    /* 窄屏（抽屉被压窄到放不下两列）时整列堆叠，避免两块都被挤到不可用。
       阈值推导：内容宽 ≈ 0.86 × 视口 - 88；并排需要 440 + 20 + 0.45×内容宽 ≤ 内容宽，
       即内容宽 ≥ 836 → 视口 ≥ 1074，因此取 1080。 */
    @media (max-width: 1080px) {
      &__preview {
        min-width: 0;
        width: 100% !important;
      }
    }
  }

  /*
   * 可读性约束只加在"表单"上，不加在表格上：
   * 大屏全宽的输入框行长会远超可读范围（建议 65-75 字符），但表格需要全部宽度。
   * 基本信息页里的"表单卡片"与下方"汇总卡片"必须同宽同边距，否则视觉上会错位。
   */
  .panel-form {
    max-width: 1000px;
    margin-left: auto;
    margin-right: auto;
  }

  .panel-form {
    background: #fff;
    border: 1px solid #f0f0f0;
    border-radius: 10px;
    padding: 24px 24px 4px;
  }

  /* 固定标签宽度：避免用百分比后标签随屏宽漂移，输入框起点对不齐 */
  .form-grid {
    :deep(.ant-form-item-label) {
      width: 104px;
      flex: 0 0 104px;
      text-align: left;
    }

    :deep(.ant-form-item-label > label) {
      color: rgba(0, 0, 0, 0.65);
      font-size: 13px;
    }

    :deep(.ant-form-item-control) {
      flex: 1 1 auto;
      min-width: 0;
    }

    :deep(.ant-form-item) {
      margin-bottom: 20px;
    }
  }

  /* 多语言：左文案 + 右封面，两列铺开 */
  .lang-pane {
    display: flex;
    gap: 28px;
    align-items: flex-start;
    max-width: 1010px;

    &__main {
      flex: 1 1 auto;
      min-width: 0;
    }

    &__side {
      flex: 0 0 190px;
      padding: 14px 16px;
      background: #fafafa;
      border: 1px solid #f0f0f0;
      border-radius: 10px;
    }

    &__side-title {
      font-size: 13px;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.65);
      margin-bottom: 10px;
    }

    &__side-hint {
      margin: 10px 0 0;
      font-size: 12px;
      line-height: 1.6;
      color: rgba(0, 0, 0, 0.45);
    }
  }

  /* SEO 区块：弱化处理，不与主文案抢注意力 */
  .lang-seo {
    margin-top: 8px;
    padding: 16px 18px 0;
    max-width: 1010px;
    background: #fafafa;
    border: 1px solid #f0f0f0;
    border-radius: 10px;

    &__head {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 14px;
      font-size: 13px;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.65);

      .anticon {
        color: rgba(0, 0, 0, 0.45);
      }
    }

    :deep(.ant-form-item-label) {
      width: 84px;
      flex: 0 0 84px;
    }
  }

  /* 窄屏：封面列下移，避免挤压文案 */
  @media (max-width: 768px) {
    .lang-pane {
      flex-direction: column;
      gap: 16px;

      &__side {
        flex: 1 1 auto;
        width: 100%;
      }
    }

    .form-grid {
      :deep(.ant-form-item-label) {
        width: auto;
        flex: 0 0 auto;
        text-align: left;
      }

      :deep(.ant-form-item) {
        flex-direction: column;
        align-items: flex-start;
      }

      :deep(.ant-form-item-control) {
        width: 100%;
      }
    }
  }

  .panel-section {
    background: #fff;
    border: 1px solid #f0f0f0;
    border-radius: 10px;
    padding: 20px 24px;
    margin-bottom: 16px;

    &__head {
      display: flex;
      align-items: baseline;
      gap: 10px;
      margin-bottom: 18px;
      flex-wrap: wrap;
      padding-bottom: 12px;
      border-bottom: 1px solid #f5f5f5;
    }

    &__title {
      position: relative;
      padding-left: 10px;
      font-size: 14px;
      font-weight: 600;
      color: rgba(0, 0, 0, 0.88);

      /* 左侧细蓝条：沿用 AntD 主色，和 LangTabs 的视觉语言一致 */
      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 13px;
        border-radius: 2px;
        background: #1677ff;
      }
    }

    &__desc {
      color: rgba(0, 0, 0, 0.45);
      font-size: 12px;
      margin-right: auto;
      line-height: 1.6;
    }
  }

  .panel-alert {
    margin-bottom: 16px;
  }

  .field-hint {
    margin-left: 8px;
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  /* 输入框下方整行提示（如「商品简介」的说明） */
  .field-hint--block {
    display: block;
    margin: 6px 0 0;
    line-height: 1.6;
  }

  /* 详情页的上游链接：一行内可点击，过长时省略号收起 */
  .upstream-link {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    max-width: 100%;

    &__text {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .readonly-summary {
    max-width: 1000px;
    margin: 16px auto 0;
    background: #fff;
    border: 1px solid #f0f0f0;
    border-radius: 10px;
    padding: 16px;
  }

  /* ---------- 商品媒体 ---------- */

  /* 主图：左侧上传器 + 右侧说明，避免"一张孤零零的卡片占满一整块" */
  .media-lead {
    display: flex;
    align-items: flex-start;
    gap: 20px;

    &__note {
      flex: 1;
      min-width: 0;
      padding-top: 4px;
    }

    &__line {
      margin: 0 0 6px;
      font-size: 12px;
      line-height: 1.7;
      color: rgba(0, 0, 0, 0.45);
    }
  }

  /*
   * 图集用缩略图网格，而不是"一行一张"的高瘦列表：
   * 每张图固定为正方形缩略图，图片本身再大也不会撑高整行，
   * 一屏能看完全部图片，排序/删除/设主图都在卡片内就地完成。
   */
  .gallery-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(158px, 1fr));
    gap: 16px;
  }

  .gallery-tile {
    position: relative;
    display: flex;
    flex-direction: column;
    background: #fff;
    border: 1px solid #f0f0f0;
    border-radius: 10px;
    overflow: hidden;
    transition: border-color 180ms ease, box-shadow 180ms ease;

    &:hover {
      border-color: #d9d9d9;
      box-shadow: 0 2px 10px rgba(0, 0, 0, 0.07);
    }

    /* 当前主图：整卡描边强调，一眼可辨 */
    &.is-main {
      border-color: #1677ff;
      box-shadow: 0 0 0 1px rgba(22, 119, 255, 0.25);
    }

    &__media {
      position: relative;
      display: block;
      width: 100%;
      /* 强制正方形：缩略图不随原图长宽比变形，也不会撑高行 */
      aspect-ratio: 1 / 1;
      padding: 0;
      border: 0;
      background: #fafafa;
      cursor: pointer;
      overflow: hidden;

      &:focus-visible {
        outline: 2px solid #1677ff;
        outline-offset: -2px;
      }

      &:disabled {
        cursor: not-allowed;
      }

      img {
        display: block;
        width: 100%;
        height: 100%;
        max-width: 100%;
        object-fit: cover;
      }
    }

    &__placeholder {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 6px;
      height: 100%;
      color: rgba(0, 0, 0, 0.35);
      font-size: 20px;

      em {
        font-size: 12px;
        font-style: normal;
      }
    }

    &__overlay {
      position: absolute;
      right: 0;
      bottom: 0;
      left: 0;
      padding: 14px 8px 6px;
      font-size: 12px;
      color: #fff;
      background: linear-gradient(transparent, rgba(0, 0, 0, 0.55));
      opacity: 0;
      transition: opacity 180ms ease;
      pointer-events: none;
    }

    &__media:hover &__overlay,
    &__media:focus-visible &__overlay {
      opacity: 1;
    }

    &__badge {
      position: absolute;
      top: 6px;
      left: 6px;
      min-width: 20px;
      height: 20px;
      padding: 0 5px;
      border-radius: 10px;
      background: rgba(0, 0, 0, 0.55);
      color: #fff;
      font-size: 12px;
      line-height: 20px;
      text-align: center;
      pointer-events: none;
    }

    &__main-tag {
      display: none;
    }

    /*
     * 拖拽手柄与删除：常态半可见，悬停/聚焦时完全显现。
     * 不做成"仅悬停可见"——触屏与鼠标用户会因此找不到按钮；
     * 键盘 Tab 进来时靠 :focus-within 显现。
     */
    &__tools {
      position: absolute;
      top: 4px;
      right: 4px;
      display: flex;
      align-items: center;
      gap: 2px;
      opacity: 0.55;
      transition: opacity 180ms ease;
    }

    &:hover &__tools,
    &:focus-within &__tools {
      opacity: 1;
    }

    &__handle {
      padding: 4px;
      border-radius: 6px;
      background: rgba(255, 255, 255, 0.9);
      color: rgba(0, 0, 0, 0.45);
      cursor: grab;

      &.is-disabled {
        cursor: not-allowed;
        opacity: 0.4;
      }
    }

    &__foot {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 4px;
      padding: 4px 6px 4px 10px;
      border-top: 1px solid #f5f5f5;

      :deep(.ant-radio-wrapper) {
        font-size: 12px;
        color: rgba(0, 0, 0, 0.65);
      }
    }

    &__move {
      display: inline-flex;
      align-items: center;
    }

    &--ghost {
      opacity: 0.4;
      border-style: dashed;
    }
  }

  /* 图集编辑弹层 */
  .gallery-pop__body {
    width: 220px;
  }

  .gallery-pop__sep {
    margin: 10px 0 6px;
    font-size: 12px;
    color: rgba(0, 0, 0, 0.45);
  }

  /* 语言页签：卡片式，与抽屉内其它区块视觉一致 */
  .product-lang-tabs {
    :deep(.ant-tabs-nav) {
      margin-bottom: 18px;
    }

    :deep(.ant-tabs-tab) {
      padding: 7px 18px;
      transition: color 180ms ease, background-color 180ms ease;
    }

    :deep(.ant-tabs-content-holder) {
      // 页签内容不再套一层内边距，交由内部区块控制，避免双重留白
      padding: 0;
    }
  }

  .lang-form {
    padding: 2px 0 0;
  }

  /* 尊重"减少动态效果"偏好 */
  @media (prefers-reduced-motion: reduce) {
    .product-lang-tabs :deep(.ant-tabs-tab),
    .gallery-tile,
    .gallery-tile__overlay,
    .gallery-tile__tools,
    .panel-section__title::before {
      transition: none !important;
    }
  }

  .drawer-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 0 4px;

    &__hint {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      color: rgba(0, 0, 0, 0.45);
      font-size: 12px;
      line-height: 1.5;
    }
  }

  /* 抽屉标题区：与内容区留白节奏对齐 */
  :deep(.ant-drawer-header) {
    padding: 14px 24px;
    border-bottom: 1px solid #f0f0f0;
  }

  :deep(.ant-drawer-footer) {
    padding: 12px 24px;
    border-top: 1px solid #f0f0f0;
    background: #fff;
  }
</style>
