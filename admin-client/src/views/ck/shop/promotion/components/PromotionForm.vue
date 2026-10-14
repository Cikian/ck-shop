<template>
  <a-spin :spinning="confirmLoading">
    <a-form
      ref="formRef"
      class="promo-form"
      :model="formData"
      :rules="rules"
      :label-col="labelCol"
      :wrapper-col="wrapperCol"
      name="ShopPromotionForm"
    >
      <!--
        force-render 是必须的：a-tabs 默认只渲染当前页签，
        非激活页签里的表单字段不存在 -> a-form.validate() 会「跳过」它们，
        表现为「店铺/编码没填也能保存成功」。这里让四个页签的字段始终在 DOM 上。
      -->
      <a-tabs v-model:activeKey="activeTab" class="promo-tabs">
        <!-- ① 基本信息 -->
        <a-tab-pane key="basic" tab="基本信息" force-render>
          <div class="panel">
            <a-row :gutter="24">
              <a-col :xs="24" :xl="12">
                <a-form-item label="所属店铺" name="storeId" id="ShopPromotionForm-storeId">
                  <a-select
                    v-model:value="formData.storeId"
                    placeholder="请选择活动所属店铺"
                    show-search
                    option-filter-prop="label"
                    :options="storeOptions"
                    :disabled="disabled"
                    @change="handleStoreChange"
                  />
                  <span class="field-hint">活动按店铺隔离：选商品、前台展示都以这个店铺为准</span>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :xl="12">
                <a-form-item label="活动编码" name="code" id="ShopPromotionForm-code">
                  <a-input v-model:value="formData.code" placeholder="例如 SECKILL-20261014，同店铺内唯一" allow-clear :disabled="disabled" />
                  <span class="field-hint">同一店铺内唯一（软删除的编码也算占用，避免恢复后冲突）</span>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :xl="12">
                <a-form-item label="活动类型" name="type" id="ShopPromotionForm-type">
                  <a-select v-model:value="formData.type" placeholder="请选择活动类型" :options="TYPE_OPTIONS" :disabled="disabled" />
                  <span class="field-hint">类型决定下方必须填哪个力度字段，也决定前台怎么算活动价</span>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :xl="12">
                <a-form-item label="排序" name="sortOrder" id="ShopPromotionForm-sortOrder">
                  <a-input-number v-model:value="formData.sortOrder" :min="0" :precision="0" style="width: 100%" :disabled="disabled" />
                  <span class="field-hint">数字越小越靠前（前台多条活动按它排序）</span>
                </a-form-item>
              </a-col>

              <!-- 类型联动：只显示当前类型需要的力度字段 -->
              <a-col v-if="formData.type === 'DIRECT'" :xs="24" :xl="12">
                <a-form-item label="直降金额" name="discountValue" id="ShopPromotionForm-discountValue">
                  <a-input-number
                    v-model:value="formData.discountValue"
                    class="full-width"
                    :min="0"
                    :precision="2"
                    placeholder="例如 20，表示立减 20 元"
                    :disabled="disabled"
                  />
                  <span class="field-hint">必须大于 0；单位是「元」</span>
                </a-form-item>
              </a-col>
              <a-col v-if="formData.type === 'PERCENT'" :xs="24" :xl="12">
                <a-form-item label="折扣率" name="discountValue" id="ShopPromotionForm-discountValue">
                  <a-input-number
                    v-model:value="formData.discountValue"
                    class="full-width"
                    :min="0.01"
                    :max="9.99"
                    :step="0.1"
                    :precision="2"
                    placeholder="例如 8.5"
                    :disabled="disabled"
                  />
                  <span class="field-hint">8.5 表示 <strong>8.5 折</strong>（取值范围 0～10，不含 0 和 10）</span>
                </a-form-item>
              </a-col>
              <a-col v-if="formData.type === 'FULL_CUT'" :xs="24" :xl="12">
                <a-form-item label="满减门槛" name="thresholdAmount" id="ShopPromotionForm-thresholdAmount">
                  <a-input-number
                    v-model:value="formData.thresholdAmount"
                    class="full-width"
                    :min="0"
                    :precision="2"
                    placeholder="例如 199，表示满 199 元参与"
                    :disabled="disabled"
                  />
                  <span class="field-hint">必须大于 0；单位是「元」</span>
                </a-form-item>
              </a-col>
              <a-col v-if="formData.type === 'SECKILL'" :xs="24" :xl="12">
                <a-form-item label="秒杀限量" name="seckillStock" id="ShopPromotionForm-seckillStock">
                  <a-input-number
                    v-model:value="formData.seckillStock"
                    class="full-width"
                    :min="1"
                    :precision="0"
                    placeholder="例如 100"
                    :disabled="disabled"
                  />
                  <span class="field-hint">秒杀必填且大于 0</span>
                </a-form-item>
              </a-col>
              <a-col v-if="formData.type === 'SECKILL'" :xs="24" :xl="12">
                <a-form-item label="每人限购" name="limitPerUser" id="ShopPromotionForm-limitPerUser">
                  <a-input-number
                    v-model:value="formData.limitPerUser"
                    class="full-width"
                    :min="0"
                    :precision="0"
                    placeholder="留空表示不限购"
                    :disabled="disabled"
                  />
                  <span class="field-hint">按用户维度限制本活动的购买件数</span>
                </a-form-item>
              </a-col>

              <a-col :xs="24" :xl="12">
                <a-form-item label="活动时间窗" name="startTime" id="ShopPromotionForm-startTime" :rules="timeRules">
                  <a-range-picker
                    v-model:value="timeRange"
                    class="full-width"
                    show-time
                    :format="TIME_FORMAT"
                    :placeholder="['开始时间', '结束时间']"
                    :disabled="disabled"
                    :disabled-date="disabledStartDate"
                    @change="syncTimeRange"
                  />
                  <span class="field-hint">开始时间必须早于结束时间；结束时间是前台倒计时的唯一依据</span>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :xl="12">
                <a-form-item label="状态" name="status" id="ShopPromotionForm-status">
                  <a-radio-group v-model:value="formData.status" button-style="solid" :disabled="disabled">
                    <a-radio-button value="Y">启用</a-radio-button>
                    <a-radio-button value="N">停用</a-radio-button>
                  </a-radio-group>
                  <span class="field-hint">只有「启用」且处于时间窗内的活动才会出现在前台</span>
                </a-form-item>
              </a-col>

              <a-col :span="24">
                <a-form-item label="落地页" name="linkUrl" id="ShopPromotionForm-linkUrl">
                  <a-input v-model:value="formData.linkUrl" placeholder="例如 /products，不含语言前缀" allow-clear :disabled="disabled" />
                  <span class="field-hint">不含语言前缀：前台会自动按当前语言拼成 /zh/products、/en/products</span>
                </a-form-item>
              </a-col>

              <a-col :span="24">
                <a-form-item label="秒杀条背景图" name="bgImage" id="ShopPromotionForm-bgImage">
                  <div class="bg-upload">
                    <j-image-upload v-model:value="formData.bgImage" biz-path="/shop/promotion" text="上传背景图" :disabled="disabled" />
                    <div class="bg-upload__note">
                      <p>为空时前台用品牌色渐变兜底（#FF4D6D → #FF8FA3）。</p>
                      <p>建议使用横向长图（约 1200×200），条内文字为白色，浅色图请自行压暗。</p>
                      <p>上传后可切到「效果预览」页签看实际效果。</p>
                    </div>
                  </div>
                </a-form-item>
              </a-col>
            </a-row>
          </div>
        </a-tab-pane>

        <!-- ② 多语言标题 -->
        <a-tab-pane key="content" tab="多语言标题" force-render>
          <div class="panel">
            <a-alert
              type="info"
              show-icon
              class="panel-alert"
              message="简体中文必填，其它语言可留空"
              description="前台按「当前语言 → 中文 → 主表标题」的顺序取值：英文/法文留空时，店铺端会回退显示中文，不会出现空白活动条。"
            />
            <a-tabs v-model:activeKey="activeLang" type="card" class="lang-tabs">
              <a-tab-pane v-for="lang in LANGUAGES" :key="lang.code">
                <template #tab>
                  <span>{{ lang.name }}</span>
                  <span v-if="lang.code === 'zh'" class="lang-tab-required">必填</span>
                  <Icon v-else-if="hasContent(lang.code)" icon="ant-design:check-outlined" class="lang-tab-ok" />
                </template>
                <a-row :gutter="24">
                  <a-col :span="24">
                    <a-form-item :label="`活动名称（${lang.name}）`">
                      <a-input
                        v-model:value="translationOf(lang.code).title"
                        :maxlength="100"
                        :placeholder="lang.code === 'zh' ? '例如 限时秒杀（必填）' : '可留空，前台回退中文'"
                        allow-clear
                        :disabled="disabled"
                      />
                      <span class="field-hint">显示在秒杀条左侧的主标题，100 字以内</span>
                    </a-form-item>
                  </a-col>
                  <a-col :span="24">
                    <a-form-item :label="`副标题（${lang.name}）`">
                      <a-input
                        v-model:value="translationOf(lang.code).subTitle"
                        :maxlength="255"
                        placeholder="一句话卖点，可留空"
                        allow-clear
                        :disabled="disabled"
                      />
                    </a-form-item>
                  </a-col>
                </a-row>
              </a-tab-pane>
            </a-tabs>
          </div>
        </a-tab-pane>

        <!-- ③ 关联商品 -->
        <a-tab-pane key="products" tab="关联商品" force-render>
          <div class="panel">
            <div class="panel-tools">
              <a-button
                v-auth="'shop-promotion:checkConflict'"
                preIcon="ant-design:warning-outlined"
                :disabled="disabled || !formData.products.length"
                @click="handleCheckConflict"
              >
                活动冲突检测
              </a-button>
              <span class="panel-tools__hint">检查这些商品在所选时间窗内是否已被其它活动占用（跨活动、跨类型都算冲突）</span>
            </div>
            <PromotionProductEditor
              ref="productEditorRef"
              v-model:products="formData.products"
              :store-id="formData.storeId"
              :store-label="storeLabel"
              :disabled="disabled"
            />
          </div>
        </a-tab-pane>

        <!-- ④ 效果预览 -->
        <a-tab-pane key="preview" tab="效果预览" force-render>
          <div class="panel">
            <PromotionPreview
              :title="zhTranslation.title"
              :sub-title="zhTranslation.subTitle"
              :bg-image="formData.bgImage"
              :start-time="formData.startTime"
              :end-time="formData.endTime"
              :link-url="formData.linkUrl"
            />
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-form>
  </a-spin>

  <!-- 冲突检测结果 -->
  <a-modal :open="conflictVisible" title="活动冲突检测结果" :width="920" :footer="null" @cancel="conflictVisible = false">
    <a-alert
      type="warning"
      show-icon
      class="panel-alert"
      :message="`检测到 ${conflictRows.length} 条冲突`"
      description="同一商品在同一时间段被多个活动覆盖时，前台的到手价会不确定。请调整时间窗、换商品，或确认这些活动可以共存。"
    />
    <a-table size="small" bordered rowKey="__key" :pagination="false" :data-source="conflictRows" :columns="conflictColumns">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'type'">{{ typeText(record.type) }}</template>
        <template v-else-if="column.key === 'range'">{{ record.startTime }} ~ {{ record.endTime }}</template>
      </template>
    </a-table>
  </a-modal>
</template>

<script lang="ts" setup>
  import { computed, nextTick, reactive, ref, watch } from 'vue';
  import dayjs from 'dayjs';
  import JImageUpload from '/@/components/Form/src/jeecg/components/JImageUpload.vue';
  import PromotionProductEditor from './PromotionProductEditor.vue';
  import PromotionPreview from './PromotionPreview.vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { checkConflict, queryById, saveOrUpdate, storeAll } from '../ShopPromotion.api';
  import { PROMOTION_LANGUAGES, PROMOTION_TIME_FORMAT, getTypeText, PROMOTION_TYPE_OPTIONS } from '../ShopPromotion.data';

  const props = withDefaults(defineProps<{ formDisabled?: boolean }>(), { formDisabled: false });

  const emit = defineEmits(['ok']);

  const { createMessage } = useMessage();

  const LANGUAGES = PROMOTION_LANGUAGES;
  const TYPE_OPTIONS = PROMOTION_TYPE_OPTIONS;
  const TIME_FORMAT = PROMOTION_TIME_FORMAT;
  const typeText = getTypeText;

  /** dayjs 实例类型：用 ReturnType 取，避免依赖 dayjs 的命名类型导出 */
  type DayjsLike = ReturnType<typeof dayjs>;

  const formRef = ref();
  const productEditorRef = ref();
  const confirmLoading = ref(false);
  const activeTab = ref('basic');
  const activeLang = ref('zh');
  const timeRange = ref<DayjsLike[] | null>(null);
  const storeOptions = ref<any[]>([]);

  const labelCol = reactive({ xs: { span: 24 }, sm: { span: 6 } });
  const wrapperCol = reactive({ xs: { span: 24 }, sm: { span: 16 } });

  const disabled = computed(() => props.formDisabled);

  const formData = reactive<Record<string, any>>({
    id: '',
    storeId: undefined,
    code: '',
    type: 'SECKILL',
    sortOrder: 0,
    startTime: null,
    endTime: null,
    discountValue: null,
    thresholdAmount: null,
    seckillStock: null,
    limitPerUser: null,
    linkUrl: '',
    bgImage: '',
    status: 'Y',
    // 一上来就铺满 zh/en/fr，避免渲染期为了取值而 push（会在 render 里改状态）
    translations: PROMOTION_LANGUAGES.map((lang) => ({ languageCode: lang.code, title: '', subTitle: '' })),
    products: [],
  });

  const storeLabel = computed(() => storeOptions.value.find((item) => item.value === formData.storeId)?.label || '未选择店铺');

  /* ------------------------------ 校验规则 ------------------------------ */

  /** 数字必须大于 0（空值交给 required 的提示，避免两条提示同时弹） */
  function greaterThanZero(_rule: any, value: any) {
    if (value === null || value === undefined || value === '') {
      return Promise.resolve();
    }
    return Number(value) > 0 ? Promise.resolve() : Promise.reject('必须大于 0');
  }

  /** 折扣率必须落在 (0, 10)：8.5 表示 8.5 折 */
  function percentInRange(_rule: any, value: any) {
    if (value === null || value === undefined || value === '') {
      return Promise.resolve();
    }
    const num = Number(value);
    return num > 0 && num < 10 ? Promise.resolve() : Promise.reject('折扣率必须在 0 到 10 之间（8.5 表示 8.5 折）');
  }

  const timeRules = [
    {
      required: true,
      validator: (_rule: any, _value: any) => {
        if (!formData.startTime || !formData.endTime) {
          return Promise.reject('请选择完整的活动时间窗');
        }
        if (!dayjs(formData.startTime).isBefore(dayjs(formData.endTime))) {
          return Promise.reject('开始时间必须早于结束时间');
        }
        return Promise.resolve();
      },
    },
  ];

  const rules = computed(() => {
    const base: Record<string, any> = {
      storeId: [{ required: true, message: '请选择所属店铺' }],
      code: [
        { required: true, message: '请输入活动编码' },
        { pattern: /^[A-Za-z0-9_-]+$/, message: '活动编码仅支持字母、数字、下划线和中划线' },
        { max: 50, message: '活动编码不超过 50 个字符' },
      ],
      type: [{ required: true, message: '请选择活动类型' }],
      linkUrl: [{ max: 500, message: '落地页不超过 500 个字符' }],
      bgImage: [{ max: 500, message: '背景图地址不超过 500 个字符' }],
    };
    if (formData.type === 'PERCENT') {
      base.discountValue = [{ required: true, message: '请输入折扣率' }, { validator: percentInRange }];
    } else if (formData.type === 'DIRECT') {
      base.discountValue = [{ required: true, message: '请输入直降金额' }, { validator: greaterThanZero }];
    } else if (formData.type === 'FULL_CUT') {
      base.thresholdAmount = [{ required: true, message: '请输入满减门槛' }, { validator: greaterThanZero }];
    } else if (formData.type === 'SECKILL') {
      base.seckillStock = [{ required: true, message: '请输入秒杀限量' }, { validator: greaterThanZero }];
      base.limitPerUser = [
        {
          validator: (_rule: any, value: any) => {
            if (value === null || value === undefined || value === '') {
              return Promise.resolve();
            }
            return Number(value) >= 0 ? Promise.resolve() : Promise.reject('每人限购不能为负数');
          },
        },
      ];
    }
    return base;
  });

  /* ------------------------------ 多语言 ------------------------------ */

  function translationOf(code: string) {
    const found = formData.translations.find((t: any) => t?.languageCode === code);
    if (found) {
      return found;
    }
    // 兜底：resetForm / syncTranslations 已经铺满三语，正常走不到这里
    const created = { languageCode: code, title: '', subTitle: '' };
    formData.translations.push(created);
    return created;
  }

  /** 预览用的中文文案（只读，不在 render 里创建数据） */
  const zhTranslation = computed<any>(() => formData.translations.find((t: any) => t?.languageCode === 'zh') || { title: '', subTitle: '' });

  function hasContent(code: string): boolean {
    const item = formData.translations.find((t: any) => t?.languageCode === code);
    return !!String(item?.title || '').trim() || !!String(item?.subTitle || '').trim();
  }

  /** 按固定的 zh/en/fr 重建翻译数组，已有内容保留 */
  function syncTranslations(seed?: any[]) {
    const source: any[] = Array.isArray(seed) ? seed : [];
    formData.translations = LANGUAGES.map((lang) => {
      const existed = source.find((t: any) => t?.languageCode === lang.code);
      return {
        languageCode: lang.code,
        title: existed?.title || '',
        subTitle: existed?.subTitle || '',
      };
    });
  }

  /** 提交用：zh 始终提交，其它语言有内容才提交 */
  function cleanTranslations() {
    return LANGUAGES.map((lang) => translationOf(lang.code))
      .filter((item: any) => item.languageCode === 'zh' || String(item.title || '').trim() || String(item.subTitle || '').trim())
      .map((item: any) => ({
        languageCode: item.languageCode,
        title: String(item.title || '').trim() || null,
        subTitle: String(item.subTitle || '').trim() || null,
      }));
  }

  /* ------------------------------ 时间窗 ------------------------------ */

  function syncTimeRange(value: any) {
    const [start, end] = value || [];
    formData.startTime = start ? dayjs(start).format(TIME_FORMAT) : null;
    formData.endTime = end ? dayjs(end).format(TIME_FORMAT) : null;
  }

  /** 开始时间不允许晚于已选结束时间（只是省事，真正的顺序校验在 timeRules 里） */
  function disabledStartDate(current: any): boolean {
    const end = timeRange.value?.[1];
    if (!end || !current) {
      return false;
    }
    return dayjs(current).valueOf() > dayjs(end).valueOf();
  }

  /* ------------------------------ 店铺与商品 ------------------------------ */

  async function loadStores() {
    try {
      const res = await storeAll();
      const records = Array.isArray(res) ? res : res?.records || [];
      storeOptions.value = records
        .filter((item: any) => !!item?.id)
        .map((item: any) => ({ label: `${item.name || item.id}（${item.code || '-'}）`, value: item.id }));
    } catch (e) {
      storeOptions.value = [];
    }
  }

  /**
   * 切换店铺：已关联的商品属于原店铺，后端会因「商品归属不符」拒绝保存。
   * 这里只做提示，不强行清空（用户可能是误点，撤回即可）。
   */
  function handleStoreChange() {
    if (formData.products?.length) {
      createMessage.warning('已切换所属店铺，请到「关联商品」页签核对商品归属（不属于该店铺的商品会被后端拒绝）');
    }
  }

  /* ------------------------------ 冲突检测 ------------------------------ */

  const conflictVisible = ref(false);
  const conflictRows = ref<any[]>([]);
  const conflictColumns = [
    { title: '活动编码', key: 'promotionCode', dataIndex: 'promotionCode', width: 170 },
    { title: '活动名称', key: 'promotionTitle', dataIndex: 'promotionTitle', width: 180, ellipsis: true },
    { title: '类型', key: 'type', width: 90 },
    { title: '时间窗', key: 'range', width: 300 },
    { title: '冲突商品', key: 'productTitle', dataIndex: 'productTitle', ellipsis: true },
    { title: '店铺', key: 'storeName', dataIndex: 'storeName', width: 140 },
  ];

  async function handleCheckConflict() {
    const productIds: string[] = productEditorRef.value?.getProductIds?.() || [];
    if (!productIds.length) {
      createMessage.warning('请先在下方添加关联商品，再做冲突检测');
      return;
    }
    if (!formData.startTime || !formData.endTime) {
      createMessage.warning('请先在「基本信息」里选择活动时间窗');
      return;
    }
    try {
      const res = await checkConflict({
        productIds: productIds.join(','),
        startTime: formData.startTime,
        endTime: formData.endTime,
        excludeId: formData.id || undefined,
      });
      const list = Array.isArray(res) ? res : res?.records || [];
      conflictRows.value = list.map((item: any, index: number) => ({
        ...item,
        __key: `${item.promotionId || 'p'}-${item.productId || 'x'}-${index}`,
      }));
      if (!list.length) {
        createMessage.success('未检测到冲突：这些商品在该时间窗内没有被其它活动占用');
        return;
      }
      conflictVisible.value = true;
    } catch (e) {
      createMessage.error('冲突检测失败');
    }
  }

  /* ------------------------------ 组装提交数据 ------------------------------ */

  function isBlank(value: any): boolean {
    return value === null || value === undefined || value === '';
  }

  /**
   * 关联商品转契约结构。
   * 必须剥掉行上的 `__key` 等展示字段：契约 DTO 只认
   * productId / skuId / promoPrice / stockLimit / sortOrder。
   */
  function toContractProducts() {
    const seen = new Set<string>();
    const result: any[] = [];
    (formData.products || []).forEach((row: any) => {
      if (!row?.productId) {
        return;
      }
      const skuId = row.skuId || null;
      const key = `${row.productId}::${skuId || 'spu'}`;
      if (seen.has(key)) {
        return;
      }
      seen.add(key);
      result.push({
        productId: row.productId,
        skuId,
        // 空值必须是 null（= 自动算），绝不能落成 0 元
        promoPrice: isBlank(row.promoPrice) ? null : row.promoPrice,
        stockLimit: isBlank(row.stockLimit) ? null : row.stockLimit,
        // 用去重后的下标，避免跳过重复行后 sortOrder 出现空洞
        sortOrder: result.length,
      });
    });
    return result;
  }

  function buildPayload() {
    const type = formData.type;
    const translations = cleanTranslations();
    // 主表 title/subTitle 是「默认/兜底文案」：列表页显示它、某语言没有翻译时回退到它。
    // 它们与 zh 翻译是同一份内容，这里显式带上（后端在缺失时也会从 zh 推导）——
    // 两边都填不会冲突，也不会因为后端版本差异而报「活动名称不能为空」。
    const zh = translations.find((item: any) => item.languageCode === 'zh') || { title: null, subTitle: null };
    return {
      id: formData.id || undefined,
      title: zh.title,
      subTitle: zh.subTitle,
      storeId: formData.storeId,
      code: String(formData.code || '').trim(),
      type,
      sortOrder: Number(formData.sortOrder) || 0,
      startTime: formData.startTime,
      endTime: formData.endTime,
      // 与类型无关的力度字段一律传 null，避免旧值残留在库里被前台误读
      discountValue: type === 'DIRECT' || type === 'PERCENT' ? formData.discountValue : null,
      thresholdAmount: type === 'FULL_CUT' ? formData.thresholdAmount : null,
      seckillStock: type === 'SECKILL' ? formData.seckillStock : null,
      limitPerUser: type === 'SECKILL' ? formData.limitPerUser : null,
      linkUrl: String(formData.linkUrl || '').trim() || null,
      bgImage: String(formData.bgImage || '').trim() || null,
      status: formData.status,
      translations,
      products: toContractProducts(),
    };
  }

  /* ------------------------------ 打开 / 加载 ------------------------------ */

  function resetForm(keepTab = false) {
    Object.assign(formData, {
      id: '',
      storeId: undefined,
      code: '',
      type: 'SECKILL',
      sortOrder: 0,
      startTime: null,
      endTime: null,
      discountValue: null,
      thresholdAmount: null,
      seckillStock: null,
      limitPerUser: null,
      linkUrl: '',
      bgImage: '',
      status: 'Y',
      translations: LANGUAGES.map((lang) => ({ languageCode: lang.code, title: '', subTitle: '' })),
      products: [],
    });
    timeRange.value = null;
    if (!keepTab) {
      activeTab.value = 'basic';
    }
    activeLang.value = 'zh';
  }

  /** 新增 */
  async function add() {
    resetForm();
    await loadStores();
    await nextTick();
    formRef.value?.clearValidate?.();
    syncTranslations([]);
  }

  /**
   * 编辑 / 详情
   * @param record 列表行（只用它的 id，详情以 queryById 为准）
   * @param options.keepTab 保存后的回填：保留当前页签
   */
  async function edit(record: any, options: { keepTab?: boolean } = {}) {
    resetForm(!!options.keepTab);
    await loadStores();
    await nextTick();
    formRef.value?.clearValidate?.();

    if (!record?.id) {
      syncTranslations([]);
      return;
    }
    confirmLoading.value = true;
    try {
      const detail = await queryById({ id: record.id });
      if (!detail) {
        createMessage.warning('未查询到活动详情');
        return;
      }
      Object.assign(formData, {
        id: detail.id,
        storeId: detail.storeId || undefined,
        code: detail.code || '',
        type: detail.type || 'SECKILL',
        sortOrder: detail.sortOrder ?? 0,
        startTime: detail.startTime || null,
        endTime: detail.endTime || null,
        discountValue: detail.discountValue ?? null,
        thresholdAmount: detail.thresholdAmount ?? null,
        seckillStock: detail.seckillStock ?? null,
        limitPerUser: detail.limitPerUser ?? null,
        linkUrl: detail.linkUrl || '',
        bgImage: detail.bgImage || '',
        status: detail.status || 'Y',
        products: (detail.products || []).map((row: any) => ({ ...row })),
      });
      // 详情里的 translations 可能只有 zh/en，按固定三语重建，缺的留空
      syncTranslations(detail.translations || []);
      // 主表 title 是兜底文案：zh 空时用它兜底，避免「库里有数据但表单看着空」
      if (detail.title && !translationOf('zh').title) {
        translationOf('zh').title = detail.title;
      }
      if (detail.subTitle && !translationOf('zh').subTitle) {
        translationOf('zh').subTitle = detail.subTitle;
      }
      timeRange.value = detail.startTime && detail.endTime ? [dayjs(detail.startTime), dayjs(detail.endTime)] : null;
      await nextTick();
      formRef.value?.clearValidate?.();
    } catch (e: any) {
      createMessage.error(e?.message || '活动详情加载失败');
    } finally {
      confirmLoading.value = false;
    }
  }

  /* ------------------------------ 提交 ------------------------------ */

  /** 后端返回的活动 id 判定：只接受「看起来像主键」的字符串，避免把提示语当 id */
  function looksLikeId(value: unknown): value is string {
    return typeof value === 'string' && /^[A-Za-z0-9_-]{8,}$/.test(value);
  }

  /**
   * 提交
   * @param continueEdit true = 保存并继续编辑（新增后据 result 回填 id，不关窗）
   */
  async function submitForm(continueEdit = false) {
    if (disabled.value) {
      return;
    }
    try {
      await formRef.value.validate();
    } catch (error) {
      activeTab.value = 'basic';
      createMessage.warning('请先补全基本信息中的必填项（含时间窗先后顺序）');
      return;
    }
    // 多语言：中文标题必填（契约：至少一条 translations 且必须包含 zh）
    if (!String(translationOf('zh').title || '').trim()) {
      activeTab.value = 'content';
      activeLang.value = 'zh';
      createMessage.warning('请填写简体中文的活动名称');
      return;
    }

    const payload = buildPayload();
    const rawCount = (formData.products || []).filter((row: any) => !!row?.productId).length;
    if (payload.products.length < rawCount) {
      createMessage.warning('存在重复关联的商品，保存时已自动去重，请保存后确认列表');
    }

    confirmLoading.value = true;
    try {
      const isCreate = !formData.id;
      // isTransformResponse:false -> 拿原始 Result，result 是活动 id（契约 2）
      const res = await saveOrUpdate(payload, !isCreate);
      if (!res?.success) {
        createMessage.warning(res?.message || '保存失败');
        return;
      }
      if (isCreate) {
        if (looksLikeId(res.result)) {
          formData.id = res.result;
        } else {
          // 拿不到主键：不能让用户以为还在编辑同一条，否则再次保存会重复创建
          createMessage.warning('已保存，但未取到活动 id，请关闭后从列表重新打开该活动继续编辑');
          emit('ok');
          return;
        }
      }
      createMessage.success(continueEdit ? '已保存，可继续编辑' : '保存成功');
      if (!continueEdit) {
        emit('ok');
      }
    } catch (e: any) {
      createMessage.error(e?.message || '保存失败');
    } finally {
      confirmLoading.value = false;
    }
  }

  // 类型变化时清掉不相关字段的校验状态，避免旧错误提示挂在新类型上
  watch(
    () => formData.type,
    () => {
      formRef.value?.clearValidate?.(['discountValue', 'thresholdAmount', 'seckillStock', 'limitPerUser']);
    }
  );

  defineExpose({ add, edit, submitForm });
</script>

<style lang="less" scoped>
  .promo-form {
    padding: 0 4px;
  }

  .promo-tabs {
    :deep(.ant-tabs-nav) {
      margin-bottom: 12px;
    }
  }

  .panel {
    padding: 4px 8px 8px;
  }

  .panel-alert {
    margin-bottom: 12px;
  }

  .panel-tools {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 12px;
    align-items: center;
    margin-bottom: 12px;
  }

  .panel-tools__hint {
    font-size: 12px;
    color: rgba(0, 0, 0, 0.45);
  }

  .full-width {
    width: 100%;
  }

  .field-hint {
    display: block;
    font-size: 12px;
    line-height: 1.6;
    color: rgba(0, 0, 0, 0.45);
  }

  .lang-tab-required {
    margin-left: 4px;
    font-size: 11px;
    color: #ff4d4f;
  }

  .lang-tab-ok {
    margin-left: 4px;
    color: #52c41a;
  }

  .bg-upload {
    display: flex;
    gap: 16px;
    align-items: flex-start;

    &__note {
      flex: 1;
      min-width: 0;

      p {
        margin: 0 0 4px;
        font-size: 12px;
        line-height: 1.7;
        color: rgba(0, 0, 0, 0.45);
      }
    }
  }
</style>
