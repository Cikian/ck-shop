<!--
  国家 → 州省 → 城市 三段级联（收货地址表单用）

  为什么要有这个组件，而不是继续用三个裸文本框：
    · 地址层级必须能被服务端校验（省属于该国家、城市属于该省），只有"选项化"才做得到；
      裸文本框里用户打错一个字，后端只能整条拒掉；
    · 落库要存**当地语言**地名（契约 §1），选项里同时带着 name（访客语言）与 nameLocal（当地文字）。

  ── 当前交互（用户已拍板，三级同一套）───────────────────────────────────
  每一级都是**一个可输入的文本框**，不再是"按钮 + 浮层"和"手填文本框"两种形态：
    1) 聚焦 → 展开浮层，并全选已有文字（接着打字就是替换，不用先手动清空）；
    2) 输入 → 过滤结果（国家/州本地过滤，城市走后端远程搜索 + 300ms 防抖）；
    3) **有结果时必须点选** —— 只有点选才会写入编码；输入框随之显示该条目的译文名；
    4) **无结果时**：
       · 国家：只提示"没有匹配结果"，**不提供任何"使用输入值"入口**；
       · 省/州、城市：额外给一行明确动作 `使用 "{输入值}"`，点了才落成手填
         （该级编码清空、文字写入；省/州落成手填时连带清空城市并重拉全国城市列表）。
    5) 输入了文字但没点选就离开（失焦 / 点外部 / Esc）→ 输入框恢复成上次选中的名字
       （没有选中就恢复空），**绝不把用户敲的文字静默落库**。

  ── 三条"为什么"（改动前请先读）──────────────────────────────────────
  · **为什么有结果必须点选**：只有点选才有 cities.id / iso3166_2 / ISO alpha-2。
    没有编码，后端就无法校验层级、无法翻译、无法按国家算运费分区 —— 那这级等于没选。
  · **为什么国家不允许自由输入**：国家码是整条地址的根 —— 电话区号、邮编正则、
    运费分区、物流分区全挂在它上面。退化成一段文字，后面所有依赖国家的行为都会失效，
    而且用户打错一个字（"泰國"/"Thiland"）不会有任何提示。自贸区/工业园这类
    "数据集里没有的地方"都发生在**下一级**，国家的兜底价值远低于它带来的风险。
  · **为什么不接受"失焦即保存"**：静默落库会让地址变成一条无法校验、无法分区、
    无法翻译的脏字符串，而且用户根本不知道它已经被保存了（他以为自己只是在搜索）。
    宁可"白打一遍"，也不能悄悄写脏数据。

  ── 其它约定 ──────────────────────────────────────────────────────────
  · 某级处于"有文字但没有编码"的状态时，输入框右侧显示灰色 `手填` 徽标，
    底部出现"手动填写的地址无法自动校验"的提醒（由 isManual 控制）。
  · 某国没有州级数据（后端返回空数组）→ **跳过州省这一级**，直接进入城市；
    但"加载中"和"加载失败"都不算没有 —— 那时保留入口，让用户能重试。
  · 编辑回显：已存的 cityId 可能不在"人口前 200"里，seedCurrentCity 会把它补成一条
    合成选项，保证输入框与列表都能显示出原来的名字。
  · SSR 一致性：国家列表在 onMounted 之后才取（见 onMounted 注释），服务端与客户端
    首帧都是"空列表 + 占位符"，不会出现 hydration mismatch。

  为什么不用 USelectMenu 拼三个下拉：城市级是**远程搜索**（后端 limit 200），
  国家/州是本地全量过滤 —— 两套取数方式塞进同一个组件时，用同一套"输入框 + 结果列表"
  渲染才不会出现"国家能搜、城市搜了没反应"这类两级行为不一致的坑。
  样式全部沿用现有表单的 --color-* / --radius-* 变量，不引入任何新的 design token。
-->
<template>
  <div
    ref="rootRef"
    class="grid gap-3"
    :class="showStateLevel ? 'sm:grid-cols-3' : 'sm:grid-cols-2'"
    @keydown.escape.prevent="onEscape"
  >
    <!-- ① 国家 / 地区：可搜索；**没有**"使用输入值"入口（见文件头"为什么国家不允许自由输入"） -->
    <div class="relative min-w-0">
      <span :class="LABEL_CLASS">{{ t('address.country') }}</span>

      <!-- 内层 relative 既是浮层的定位父级，也是"手填"徽标的覆盖层 -->
      <div class="relative">
        <input
          v-model="countryInput"
          type="text"
          role="combobox"
          autocomplete="off"
          :class="INPUT_CLASS"
          :disabled="disabled"
          :placeholder="t('address.countryPlaceholder')"
          :aria-label="t('address.country')"
          :aria-expanded="openPanel === 'country'"
          aria-autocomplete="list"
          @input="onFieldInput('country', $event)"
          @focus="onFieldFocus('country', $event)"
          @blur="onFieldBlur"
          @keydown.enter.prevent
        >
        <span v-if="countryFreeText" :class="BADGE_CLASS">{{ t('address.manualBadge') }}</span>

        <div v-if="openPanel === 'country'" :class="PANEL_CLASS" :style="panelStyle">
          <ul :class="LIST_SCROLL_CLASS" role="listbox" :aria-label="t('address.country')">
            <li v-for="item in filteredCountries" :key="item.code">
              <!-- mousedown.prevent：点选项时不让输入框失焦，否则 blur 会先把面板关掉、点选丢失 -->
              <button
                type="button"
                role="option"
                :aria-selected="item.code === modelValue.countryCode"
                :class="optionClass(item.code === modelValue.countryCode)"
                @mousedown.prevent
                @click="selectCountry(item)"
              >
                <span class="min-w-0 flex-1 truncate">{{ regionLabel(item) }}</span>
                <!-- 当地文字并排显示：用户核对地址时认的是本国字，不是拉丁转写 -->
                <span
                  v-if="item.nameLocal && item.nameLocal !== regionLabel(item)"
                  class="max-w-[45%] shrink-0 truncate text-[12px] font-normal text-[var(--color-muted)]"
                >
                  {{ item.nameLocal }}
                </span>
              </button>
            </li>
          </ul>

          <p v-if="countriesError" :class="HINT_CLASS">
            {{ countriesError }}
            <button type="button" class="ml-1 cursor-pointer font-bold underline underline-offset-2" @mousedown.prevent @click="retryCountries">
              {{ t('common.retry') }}
            </button>
          </p>
          <p v-else-if="countriesLoading" :class="HINT_CLASS">{{ t('common.loading') }}</p>
          <!-- 国家这一级到此为止：只有提示，没有任何"使用输入值"入口 -->
          <p v-else-if="!filteredCountries.length" :class="HINT_CLASS">{{ t('address.noResultHint') }}</p>
        </div>
      </div>
    </div>

    <!--
      ② 省 / 州
      该国没有州级数据时整块不渲染；但"加载中"和"加载失败"都不算没有，那时保留入口让用户重试。
      还没选国家时保留一个**禁用态输入框**：让用户看得出"先选国家再到省/州"的顺序。
    -->
    <div v-if="showStateLevel" class="relative min-w-0">
      <span :class="LABEL_CLASS">{{ t('address.state') }}</span>

      <div class="relative">
        <input
          v-model="stateInput"
          type="text"
          role="combobox"
          autocomplete="off"
          :class="INPUT_CLASS"
          :disabled="disabled || !modelValue.countryCode"
          :placeholder="modelValue.countryCode ? t('address.statePlaceholder') : t('address.selectCountryFirst')"
          :aria-label="t('address.state')"
          :aria-expanded="openPanel === 'state'"
          aria-autocomplete="list"
          @input="onFieldInput('state', $event)"
          @focus="onFieldFocus('state', $event)"
          @blur="onFieldBlur"
          @keydown.enter.prevent
        >
        <span v-if="stateFreeText" :class="BADGE_CLASS">{{ t('address.manualBadge') }}</span>

        <div v-if="openPanel === 'state'" :class="PANEL_CLASS" :style="panelStyle">
          <ul :class="LIST_SCROLL_CLASS" role="listbox" :aria-label="t('address.state')">
            <li v-for="item in filteredStates" :key="item.code">
              <button
                type="button"
                role="option"
                :aria-selected="item.code === modelValue.provinceCode"
                :class="optionClass(item.code === modelValue.provinceCode)"
                @mousedown.prevent
                @click="selectState(item)"
              >
                <span class="min-w-0 flex-1 truncate">{{ regionLabel(item) }}</span>
                <span
                  v-if="item.nameLocal && item.nameLocal !== regionLabel(item)"
                  class="max-w-[45%] shrink-0 truncate text-[12px] font-normal text-[var(--color-muted)]"
                >
                  {{ item.nameLocal }}
                </span>
              </button>
            </li>
          </ul>

          <p v-if="statesError" :class="HINT_CLASS">
            {{ statesError }}
            <button type="button" class="ml-1 cursor-pointer font-bold underline underline-offset-2" @mousedown.prevent @click="retryStates">
              {{ t('common.retry') }}
            </button>
          </p>
          <p v-else-if="statesLoading" :class="HINT_CLASS">{{ t('common.loading') }}</p>
          <template v-else-if="!filteredStates.length">
            <p :class="HINT_CLASS">{{ t('address.noResultHint') }}</p>
            <!--
              无结果时才出现的**明确动作**：用户点它才把这段文字落成手填。
              自贸区、工业园、军事基地这类地址任何数据集都没有，没有这个入口就是功能倒退。
            -->
            <button v-if="canUseTypedState" type="button" :class="ACTION_CLASS" @mousedown.prevent @click="useTypedState">
              {{ t('address.useTypedValue', { value: stateInput.trim() }) }}
            </button>
          </template>
        </div>
      </div>
    </div>

    <!-- ③ 城市：远程搜索（后端 limit 200），无结果时可落成手填 -->
    <div class="relative min-w-0">
      <span :class="LABEL_CLASS">{{ t('address.city') }}</span>

      <div class="relative">
        <input
          v-model="cityInput"
          type="text"
          role="combobox"
          autocomplete="off"
          :class="INPUT_CLASS"
          :disabled="disabled || !modelValue.countryCode"
          :placeholder="modelValue.countryCode ? t('address.citySearchPlaceholder') : t('address.selectCountryFirst')"
          :aria-label="t('address.city')"
          :aria-expanded="openPanel === 'city'"
          aria-autocomplete="list"
          @input="onFieldInput('city', $event)"
          @focus="onFieldFocus('city', $event)"
          @blur="onFieldBlur"
          @keydown.enter.prevent
        >
        <span v-if="cityFreeText" :class="BADGE_CLASS">{{ t('address.manualBadge') }}</span>

        <div v-if="openPanel === 'city'" :class="PANEL_CLASS" :style="panelStyle">
          <!--
            城市列表不做本地过滤：后端的关键词搜索会连"任意语言的译文"一起匹配（契约 §3.3），
            前端再按 name/nameLocal 过滤一遍反而会把译文命中的结果藏掉。
          -->
          <ul :class="LIST_SCROLL_CLASS" role="listbox" :aria-label="t('address.city')">
            <li v-for="item in cityRows" :key="item.id">
              <button
                type="button"
                role="option"
                :aria-selected="item.id === modelValue.cityId"
                :class="optionClass(item.id === modelValue.cityId)"
                @mousedown.prevent
                @click="selectCity(item)"
              >
                <span class="min-w-0 flex-1 truncate">{{ regionLabel(item) }}</span>
                <span
                  v-if="item.nameLocal && item.nameLocal !== regionLabel(item)"
                  class="max-w-[45%] shrink-0 truncate text-[12px] font-normal text-[var(--color-muted)]"
                >
                  {{ item.nameLocal }}
                </span>
              </button>
            </li>
          </ul>

          <p v-if="citiesError" :class="HINT_CLASS">
            {{ citiesError }}
            <button type="button" class="ml-1 cursor-pointer font-bold underline underline-offset-2" @mousedown.prevent @click="retryCities">
              {{ t('common.retry') }}
            </button>
          </p>
          <p v-else-if="citiesLoading" :class="HINT_CLASS">{{ t('common.loading') }}</p>
          <template v-else-if="!cityRows.length">
            <!--
              区分两种"空"：
                · 该地区本来就没有城市级数据（如老挝/柬埔寨部分省份，上游只有省本身的记录）
                  → 说"没有匹配结果"会让人以为自己打错了字，实际是数据集没有；
                · 有关键词但没匹配上 → 才是"没有匹配结果"。
              判定：列表为空且输入框也为空 = 数据缺失；否则 = 搜索无果。
            -->
            <p :class="HINT_CLASS">
              {{ !cityInput.trim() ? t('address.cityNoDataHint') : t('address.noResultHint') }}
            </p>
            <button v-if="canUseTypedCity" type="button" :class="ACTION_CLASS" @mousedown.prevent @click="useTypedCity">
              {{ t('address.useTypedValue', { value: cityInput.trim() }) }}
            </button>
          </template>
          <!--
            结果被 limit 截断：明确告诉用户"还有更多，请输入关键词"，
            否则他会以为这个国家就这么些城市（菲律宾 4374 个里只显示了 200 个）。
          -->
          <p v-else-if="cityTruncated && !cityInput.trim()" :class="HINT_CLASS">
            {{ t('address.cityLimitHint') }}
          </p>
        </div>
      </div>
    </div>

    <!-- 手动填写的公共提醒：只有"有文字但无编码"时才出现，避免正常选择也弹这句 -->
    <p v-if="isManual" class="flex items-start gap-1.5 text-[12px] text-[var(--color-muted)] sm:col-span-full">
      <UIcon name="lucide:info" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      {{ t('address.manualInputHint') }}
    </p>
  </div>
</template>

<script setup lang="ts">
import type { AddressRegionSelection, RegionCityVO, RegionCountryVO, RegionStateVO } from '~~/types/shop-region'
import { createEmptyRegionSelection, isSameRegionSelection, regionLabel, regionMatchesKeyword } from '~~/types/shop-region'
import { readErrorMessage } from '~~/composables/useApiError'
import { useRegionOptions } from '~~/composables/useRegionOptions'

/**
 * 三级级联。
 *
 * 对外只暴露一个对象型 v-model（AddressRegionSelection）而不是三个码位：
 * 换国家必须把省/市**一起**清空（旧的 provinceCode 属于旧国家，后端会判"不属于该国家"），
 * 拆成三个 emit 会给父组件留下"国家已换、城市还是旧国家的"这种脏中间态。
 */
const props = withDefaults(defineProps<{
  /** 当前地区（含编码与文字；文字只在"手填"与回显兜底时使用） */
  modelValue: AddressRegionSelection
  /** 父组件提交中时整块禁用，避免"提交到一半又改了城市" */
  disabled?: boolean
}>(), {
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: AddressRegionSelection): void
}>()

const { t } = useI18n()
const {
  cityLimit,
  countries,
  countriesLoading,
  loadCountries,
  loadStates,
  loadCities,
  findCountry,
} = useRegionOptions()

/* ── 样式常量：三级共用一套，只写一遍，避免三处逐渐漂移 ────────────────── */
const LABEL_CLASS = 'mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]'
/** pr-16 是给右侧"手填"徽标预留的位置（徽标绝对定位在输入框里） */
const INPUT_CLASS = 'h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white pl-3 pr-16 text-sm text-[var(--color-ink)] placeholder:text-[var(--color-muted)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] disabled:cursor-not-allowed disabled:bg-[var(--color-surface-secondary)] disabled:text-[var(--color-muted)]'
const BADGE_CLASS = 'pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 rounded-full bg-[var(--color-line-soft)] px-2 py-0.5 text-[10px] font-bold text-[var(--color-ink-soft)]'
/**
 * 结果列表是**浮层**：相对输入框容器绝对定位，盖在下面的字段上，不占文档流。
 *
 * 为什么不是 `fixed`（试过，会整块消失）：
 *   UModal 的内容容器带 `-translate-x-1/2 -translate-y-1/2`，
 *   有 transform 的祖先会成为 fixed 后代的包含块 → fixed 不再相对视口，
 *   用视口坐标算出来的位置全错、还会被那个容器的 overflow-hidden 裁掉。
 *   详见 measurePanel() 的注释。
 *
 * 为什么不是 <Teleport to="body">：
 *   UModal 是 Reka UI 的 Dialog，内部有 FocusScope 焦点陷阱。
 *   teleport 出去就成了"弹层外"的元素，焦点会被抢回弹层，输入框敲不进字。
 *   留在弹层 DOM 内 + absolute，两个问题一起解决。
 *
 * 位置/宽度/最大高度由 measurePanel() 计算后绑在 :style 上（单位 px，相对本字段容器）。
 */
const PANEL_CLASS = 'absolute z-30 flex flex-col overflow-hidden rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white shadow-[0_10px_30px_rgba(15,23,42,0.14)]'
/**
 * 列表滚动区：滚动**能力**要保留（250 个国家不可能一次铺开），
 * 但滚动**条**按设计稿要求隐藏 —— 浮层里竖一条灰色轨道会和外框描边打架。
 * 用项目既有的 `.no-scrollbar`（app/assets/css/tailwind.css，商品横滑条等已在用），
 * 不另写一套任意值变体：同一个效果两种写法迟早会分叉。
 * 隐藏后滚轮、键盘上下键、触屏滑动都照常可用。
 */
const LIST_SCROLL_CLASS = 'no-scrollbar min-h-0 flex-1 overflow-y-auto py-1'
const HINT_CLASS = 'shrink-0 px-3 py-2.5 text-[12px] leading-relaxed text-[var(--color-muted)]'
/** 「使用 "{输入值}"」：只在无结果时出现的一行明确动作，视觉上要像可点的按钮而不像提示 */
const ACTION_CLASS = 'shrink-0 cursor-pointer border-t border-[var(--color-line-soft)] px-3 py-2.5 text-left text-[12px] font-bold text-[var(--color-brand-600)] hover:bg-[var(--color-brand-50)]'

/**
 * 下拉选项：选中态用品牌描边浅底，与 ProductSpecPicker 的选中语义保持一致。
 *
 * 行高**写死 `h-9`（36px）**，与下面的 OPTION_ROW_HEIGHT 常量严格对应：
 * 浮层高度要按整行对齐（滚动条隐藏后半截行很难看），而"量一次行高"在异步列表上不可靠
 * （城市列表是远程回来的，第一次 measurePanel 时 li 还不存在，量到 0 就跳过了对齐，
 * 数据到了又没人重量 —— 实测踩过）。改成固定行高后，对齐就变成纯算术，不依赖渲染时序。
 */
const OPTION_ROW_HEIGHT = 36
function optionClass(active: boolean): string {
  return active
    ? 'flex h-9 w-full cursor-pointer items-center gap-2 bg-[var(--color-brand-50)] px-3 text-left text-[13px] font-bold text-[var(--color-brand-700)]'
    : 'flex h-9 w-full cursor-pointer items-center gap-2 px-3 text-left text-[13px] text-[var(--color-ink-soft)] hover:bg-[var(--color-surface-secondary)]'
}

/* ── 组件状态：输入框值 / 过滤词 / 浮层开合 ─────────────────────────────
   输入框值（*Input）与过滤词（*Query）刻意分开：
     · 输入框在"没在打字"时要显示**选中项的名字**（失焦后必须还原成它）；
     · 过滤词只在聚焦/输入时才有意义，聚焦瞬间要清空 —— 否则拿着"泰国"去过滤，
       列表里只剩泰国一条，用户以为数据集只有这一个国家。 */
const openPanel = ref<'' | 'country' | 'state' | 'city'>('')
const countryInput = ref('')
const stateInput = ref('')
const cityInput = ref('')
const countryQuery = ref('')
const stateQuery = ref('')
const cityQuery = ref('')

/** 本地列表：州/城市与"当前选中的国家"强耦合，放组件内即可，没必要进全局 state */
const stateRows = ref<RegionStateVO[]>([])
const cityRows = ref<RegionCityVO[]>([])
/** 城市结果是否被 limit 截断（决定要不要提示"请输入关键词缩小范围"） */
const cityTruncated = ref(false)

const countriesError = ref('')
const statesError = ref('')
const citiesError = ref('')
const statesLoading = ref(false)
const citiesLoading = ref(false)

const mounted = ref(false)
const rootRef = ref<HTMLElement | null>(null)

/* ── 三级各自的"当前应显示的名字"：选中项 → 模型里的文字 → 空 ──────────
   第二档对应两种真实情况：
     · 手填（无编码但有文字）：输入框必须显示用户落下来的那段文字；
     · 编辑老地址：cityId 对应的城市可能不在"人口前 200"里，但地址本身带着当地文字的市名。 */
const countryLabel = computed(() => {
  const hit = findCountry(props.modelValue.countryCode)
  return hit ? regionLabel(hit) : props.modelValue.countryText.trim()
})

const stateLabel = computed(() => {
  const hit = stateRows.value.find(item => item.code === props.modelValue.provinceCode)
  return hit ? regionLabel(hit) : props.modelValue.province.trim()
})

const cityLabel = computed(() => {
  const hit = cityRows.value.find(item => item.id === props.modelValue.cityId)
  return hit ? regionLabel(hit) : props.modelValue.city.trim()
})

/* ── "手填"判定：有文字但没有编码 ───────────────────────────────────────
   编码才是可校验的东西；只有文字就等于用户自己写的自由文本。
   徽标与底部提醒都只认这一个口径，不要再各写一套判断。 */
const countryFreeText = computed(() => !props.modelValue.countryCode && !!props.modelValue.countryText.trim())
const stateFreeText = computed(() => !props.modelValue.provinceCode && !!props.modelValue.province.trim())
const cityFreeText = computed(() => !props.modelValue.cityId && !!props.modelValue.city.trim())
const isManual = computed(() => countryFreeText.value || stateFreeText.value || cityFreeText.value)

/**
 * 州省这一级要不要显示。
 *   · 还没选国家 → 显示（禁用态输入框）：让用户看得出"先选国家再到州省"的顺序；
 *   · 加载中 / 加载失败 → 显示：那不是"该国没有州级"，藏起来等于把重试入口也藏了；
 *   · 加载成功且为空 → **隐藏**（契约 §5.2：自动跳过这一级，直接进入城市）。
 */
const showStateLevel = computed(() => {
  if (!props.modelValue.countryCode) return true
  if (statesLoading.value || statesError.value) return true
  return stateRows.value.length > 0
})

/* ── 本地过滤（国家/州数据量小，直接前端过滤；城市走远程搜索，见模板注释） ── */
const filteredCountries = computed(() =>
  countries.value.filter(item => regionMatchesKeyword(item, countryQuery.value)))
const filteredStates = computed(() =>
  stateRows.value.filter(item => regionMatchesKeyword(item, stateQuery.value)))

/**
 * 省/城市能不能落成手填：只有"无结果 + 有输入 + 不是加载中/出错"三者同时成立时才给入口。
 * 加载中给入口是大坑：用户刚敲第一个字，远程搜索还没回来，他会看到"没有匹配结果"、
 * 顺手点下"使用输入值"，于是把一个**数据集里明明有**的城市存成手填。
 */
const canUseTypedState = computed(() =>
  !statesLoading.value && !statesError.value && !!stateInput.value.trim() && !filteredStates.value.length)
const canUseTypedCity = computed(() =>
  !citiesLoading.value && !citiesError.value && !!cityInput.value.trim() && !cityRows.value.length)

/* ── v-model 回写 ────────────────────────────────────────────────────────
   lastEmitted 用来区分「这次变化是我们自己发出去的」和「父组件从 AddressVO 重新填了表」：
   后者需要重新拉一次州/城市把名字对上，前者再拉一遍纯属浪费、还会把正在输入的内容冲掉。
   注意：**输入框里的文字永远不会触发 emitModel** —— 只有点选和「使用输入值」会。 */
let lastEmitted: AddressRegionSelection | null = null

function emitModel(patch: Partial<AddressRegionSelection>) {
  const next: AddressRegionSelection = { ...props.modelValue, ...patch }
  lastEmitted = next
  emit('update:modelValue', next)
}

/** 把三个输入框恢复成"当前应该显示的名字"，并清掉过滤词（失焦/取消时的唯一还原点） */
function syncInputsFromModel() {
  countryInput.value = countryLabel.value
  stateInput.value = stateLabel.value
  cityInput.value = cityLabel.value
  countryQuery.value = ''
  stateQuery.value = ''
  cityQuery.value = ''
}

/* ── 取数 ─────────────────────────────────────────────────────────────── */
function toErrorMessage(error: unknown): string {
  return readErrorMessage(error, t('address.loadErrorDesc'))
}

async function loadStatesSafe(countryCode: string): Promise<RegionStateVO[]> {
  statesError.value = ''
  statesLoading.value = true
  try {
    return await loadStates(countryCode)
  } catch (error) {
    // 失败与"该国没有州级"必须区分开：前者要保留入口 + 重试，后者才跳过这一级
    statesError.value = toErrorMessage(error)
    return []
  } finally {
    statesLoading.value = false
  }
}

/**
 * 编辑回显兜底：已选的 cityId 可能不在首屏 200 条里（菲律宾 4374 个城市里的绝大多数都不在）。
 * 这时把它当成一条合成选项补进列表，输入框与下拉才能显示原来的名字；
 * 用户一改关键词重新搜索，这条合成项自然消失，不会污染真实结果。
 */
function seedCurrentCity() {
  const { cityId, city } = props.modelValue
  if (!cityId || !city) return
  if (cityRows.value.some(item => item.id === cityId)) return
  cityRows.value = [{ id: cityId, name: city, nameLocal: null, population: null }, ...cityRows.value]
}

async function reloadCities(keyword: string) {
  citiesError.value = ''
  const country = props.modelValue.countryCode
  if (!country) {
    cityRows.value = []
    cityTruncated.value = false
    return
  }

  citiesLoading.value = true
  try {
    // 手填省/州时 provinceCode 是空的 —— 那个 code 后端不认识，传过去只会得到空列表
    const rows = await loadCities(country, props.modelValue.provinceCode, keyword)
    // 截断判定要在补合成项之前做，否则永远多出 1 条，判断失真
    cityTruncated.value = rows.length >= cityLimit
    cityRows.value = rows
  } catch (error) {
    citiesError.value = toErrorMessage(error)
    cityRows.value = []
    cityTruncated.value = false
  } finally {
    citiesLoading.value = false
  }
  seedCurrentCity()
}

async function retryCountries() {
  countriesError.value = ''
  try {
    await loadCountries(true)
  } catch (error) {
    countriesError.value = toErrorMessage(error)
  }
}

async function retryStates() {
  const country = props.modelValue.countryCode
  if (!country) return
  stateRows.value = await loadStatesSafe(country)
  await reloadCities(cityQuery.value)
}

async function retryCities() {
  await reloadCities(cityQuery.value)
}

/* ── 浮层几何 ────────────────────────────────────────────────────────────
 * 几个数字都来自实际踩到的问题，不是随手调的：
 *   · MIN_WIDTH 380：三级字段在 3 列网格里最窄只有 110px 左右，
 *     浮层若跟着输入框等宽，中文名 + 当地文字（"科特迪瓦 / Côte d'Ivoire"）必然被截成省略号；
 *   · MAX_HEIGHT 320：再高就会盖住下面的"区/县""详细地址"，用户失去上下文；
 *   · EDGE 8：贴边留白，浮层压到容器边缘时还能看出是个浮层。
 */
const PANEL_MIN_WIDTH = 380
const PANEL_MAX_HEIGHT = 320
const PANEL_EDGE = 8

/** 浮层几何：由 measurePanel() 算出，直接绑到 :style（单位 px，相对**输入框容器**） */
const panelStyle = ref<Record<string, string>>({})
/** 当前聚焦那一级的输入框 —— 作为浮层的定位锚点 */
let anchorEl: HTMLElement | null = null

/**
 * 找最近的纵向滚动容器（弹层 body 是 overflow-y-auto）。
 * 用它来界定"浮层最多能长到哪里"，而不是用视口 —— 见 measurePanel 的说明。
 */
function findScrollContainer(start: HTMLElement | null): HTMLElement | null {
  if (typeof window === 'undefined') return null
  let node = start?.parentElement ?? null
  while (node && node !== document.body) {
    const overflowY = window.getComputedStyle(node).overflowY
    if (overflowY === 'auto' || overflowY === 'scroll') return node
    node = node.parentElement
  }
  return null
}

/**
 * 算出浮层相对**输入框容器**的 left/top/width/max-height。
 *
 * ⚠️ 这里踩过一次坑，别再改回视口坐标：
 *   最初用的是 `position: fixed` + `getBoundingClientRect()` 的视口坐标，结果浮层完全不出现。
 *   原因：UModal 的内容容器带 `-translate-x-1/2 -translate-y-1/2`（transform），
 *   **有 transform 的祖先会成为 fixed 后代的包含块**，fixed 就不再相对视口了；
 *   算出来的视口坐标被当成相对那个 overflow-hidden 盒子的偏移，浮层直接被裁到看不见。
 *
 * 现在的做法：浮层 `absolute` 定位在输入框容器里（容器本身是 relative），
 * 坐标只依赖两个 rect 的**差值**（输入框 vs 滚动容器）。同一个 transform 会同时作用在
 * 两者身上，差值不变 —— 所以这个算法在有没有 transform 的弹层里都成立。
 *
 * 顺带的好处：浮层和输入框在同一个滚动容器里，容器滚动时两者一起走，
 * 不需要监听 scroll 重新定位（少一类"滚动后浮层与输入框错位"的 bug）。
 */
function measurePanel() {
  if (typeof window === 'undefined' || !anchorEl) return
  const wrap = anchorEl.parentElement
  if (!wrap) return

  const wrapRect = wrap.getBoundingClientRect()
  const container = findScrollContainer(anchorEl)
  // 没有滚动容器时退到视口（组件的另一种用法：直接放在页面里）
  const boundRect = container
    ? container.getBoundingClientRect()
    : { top: 0, bottom: window.innerHeight, left: 0, right: window.innerWidth, width: window.innerWidth, height: window.innerHeight }

  const usableWidth = boundRect.width - PANEL_EDGE * 2
  const width = Math.min(Math.max(wrapRect.width, Math.min(PANEL_MIN_WIDTH, usableWidth)), usableWidth)

  // 水平：默认与输入框左对齐；右侧会越出容器时整体左移，但仍不许越过容器左边界
  let left = 0
  if (wrapRect.left + width > boundRect.right - PANEL_EDGE) {
    left = boundRect.right - PANEL_EDGE - width - wrapRect.left
  }
  left = Math.max(left, boundRect.left + PANEL_EDGE - wrapRect.left)

  // 垂直：默认向下展开；下方空间不足 200px 且上方更宽裕时向上翻
  const spaceBelow = boundRect.bottom - wrapRect.bottom - PANEL_EDGE
  const spaceAbove = wrapRect.top - boundRect.top - PANEL_EDGE
  const openUp = spaceBelow < 200 && spaceAbove > spaceBelow
  /*
   * maxHeight **必须**取"该方向上的真实剩余空间"，不能加硬下限。
   * 踩过：之前写了 Math.max(160, ...)，向上展开时上方只剩 110px 也硬撑到 160，
   * 浮层顶部溢出滚动容器 → 被 overflow-y-auto 裁掉 → 搜索框整行看不见（截图复现过）。
   * 现在最小只到 96px（约能显示 2 条），空间真不够就压扁，绝不溢出。
   */
  const available = openUp ? spaceAbove : spaceBelow
  let maxHeight = Math.min(PANEL_MAX_HEIGHT, Math.max(96, available))

  /*
   * 高度对齐到"整行"。
   * 滚动条按设计要求隐藏了，于是"半截行"就成了最刺眼的瑕疵 —— 列表看起来像被裁坏
   * （用户截图里最后一行只露出半个字）。这里把浮层高度压到 OPTION_ROW_HEIGHT 的整数倍。
   *
   * 为什么用常量而不是量第一个 li 的高度：城市列表是远程回来的，measurePanel 第一次跑时
   * li 还不存在（量到 0 → 跳过对齐），数据到了之后也没有可靠的重量时机 —— 实测这么写不生效。
   * 现在选项行高由 optionClass 的 `h-9` 固定为 36px，对齐就变成纯算术，不依赖渲染时序。
   * 列表自身有 py-1（上下共 8px），对齐时把这份内边距让出来。
   */
  const LIST_PADDING_Y = 8
  const rows = Math.floor((maxHeight - LIST_PADDING_Y) / OPTION_ROW_HEIGHT)
  if (rows >= 1) maxHeight = rows * OPTION_ROW_HEIGHT + LIST_PADDING_Y

  panelStyle.value = openUp
    // 向上展开：bottom 相对容器底部，让浮层底边正好落在输入框上方 4px
    ? { left: `${left}px`, bottom: `${wrapRect.height - anchorEl.offsetTop + 4}px`, width: `${width}px`, maxHeight: `${maxHeight}px` }
    // 向下展开：top 取输入框自身底边（不是容器底边 —— 容器就是输入框，这里保持一致即可）
    : { left: `${left}px`, top: `${anchorEl.offsetTop + anchorEl.offsetHeight + 4}px`, width: `${width}px`, maxHeight: `${maxHeight}px` }
}

/* ── 交互：聚焦开浮层 / 输入过滤 / 离开还原 ───────────────────────────── */

/** 展开某一级的浮层并把输入框里的文字全选（接着打字就是替换，不用先手动清空） */
async function openLevelPanel(level: 'country' | 'state' | 'city', el: HTMLInputElement) {
  anchorEl = el
  // 过滤词清零：聚焦的瞬间要让用户看到**全量**列表，而不是被上一次的选中名过滤成一条
  if (level === 'country') countryQuery.value = ''
  if (level === 'state') stateQuery.value = ''
  if (level === 'city') {
    cityQuery.value = ''
    // 城市是远程搜索：重新打开时先保证首屏列表在（命中 useRegionOptions 的缓存，不会真的再打后端）
    void reloadCities('')
  }
  openPanel.value = level
  await nextTick()
  el.select()
  // 等浮层渲染出来再量：max-height 依赖可用空间，测量时机错了会先闪一下再跳位
  measurePanel()
}

function onFieldFocus(level: 'country' | 'state' | 'city', event: FocusEvent) {
  if (props.disabled) return
  // 国家没选时省/市是禁用态，理论上到不了这里；防御一下，避免出现"空列表浮层"
  if (level !== 'country' && !props.modelValue.countryCode) return
  const el = event.currentTarget as HTMLInputElement | null
  if (!el) return
  /*
   * 聚焦任何一级都先把三个输入框刷回"已提交的名字"。
   *
   * 这一步是安全线的一部分：从 A 级直接点到 B 级时，A 的 blur 因为"焦点还在组件内"被跳过，
   * 若不清一次，A 级里那段没点选的文字就留在框里了（用户会以为它已经生效）。
   * 聚焦本身就意味着"重新开始选"，所以从已提交值重来是合理的。
   */
  syncInputsFromModel()
  void openLevelPanel(level, el)
}

/**
 * 输入只更新过滤词，**不写模型**。
 * 这是"绝不静默落库"的实现点：没有点选，就没有任何编码/文字进入 v-model。
 */
function onFieldInput(level: 'country' | 'state' | 'city', event: Event) {
  const value = (event.target as HTMLInputElement | null)?.value ?? ''
  if (level === 'country') countryQuery.value = value
  else if (level === 'state') stateQuery.value = value
  else cityQuery.value = value
}

/*
 * 选项数量变化 → 重新量浮层高度。
 *
 * 为什么需要：城市是远程搜索、州列表按国家拉取，**首屏那次 measurePanel() 跑的时候列表往往还是空的**，
 * 量不到行高就跳过了"整行对齐"，等数据回来又没人再量 —— 结果最后一行露出半截（截图复现过）。
 * 这里只监听"数量"，不监听内容，所以打字过滤时也只会触发一次重量，不会形成回环
 * （measurePanel 只写 panelStyle，不改任何被监听的值）。
 */
watch(
  () => [openPanel.value, filteredCountries.value.length, filteredStates.value.length, cityRows.value.length] as const,
  async () => {
    if (!openPanel.value) return
    await nextTick()
    measurePanel()
  },
)

/**
 * 离开输入框：焦点落到浮层里的选项（或"使用输入值"）上时不算离开，其余情况一律还原。
 * 键盘 Tab 出去时 relatedTarget 可能为 null —— 那也要还原，否则脏文字会留在框里。
 */
function onFieldBlur(event: FocusEvent) {
  const next = event.relatedTarget as Node | null
  if (next && rootRef.value?.contains(next)) return
  closeAndRevert()
}

/** 关闭浮层并把三个输入框还原成"当前选中项的名字"（没有选中就是空） */
function closeAndRevert() {
  openPanel.value = ''
  syncInputsFromModel()
}

/** Esc：还原并让输入框失焦（这样再次点击输入框能重新聚焦、重新打开浮层） */
function onEscape() {
  if (!openPanel.value) return
  closeAndRevert()
  blurActiveField()
}

/**
 * 动作完成后让输入框失焦。
 *
 * 为什么必须失焦：浮层已经关掉，输入框却还留着焦点的话，用户接着打字只会改掉框里的文字
 * （没有浮层可点、也不会写模型），很容易误以为"已经改成新的了"。
 * 失焦后想再改就重新点一下输入框，浮层与"全选文本"都会回到初始态。
 *
 * 调用点一律包一层 nextTick：那时父组件的 v-model 已经刷新，
 * onFieldBlur → syncInputsFromModel 读到的才是**新的**名字而不是旧的。
 */
function blurActiveField() {
  const active = document.activeElement as HTMLElement | null
  if (active && rootRef.value?.contains(active)) active.blur()
}

/* ── 点选（唯一会写入编码的路径） ─────────────────────────────────────── */
async function selectCountry(item: RegionCountryVO) {
  openPanel.value = ''
  countryQuery.value = ''
  countryInput.value = regionLabel(item)
  void nextTick(blurActiveField)
  // 点的是当前已选的国家：上面已经把输入框刷成它的名字，什么模型都不用动
  if (item.code === props.modelValue.countryCode) return

  // 换国家 = 省/市的选择全部作废（契约 §3.4：cityId 与 provinceCode 都必须属于该国家）
  emitModel({ ...createEmptyRegionSelection(), countryCode: item.code })
  stateInput.value = ''
  cityInput.value = ''
  stateQuery.value = ''
  cityQuery.value = ''
  cityRows.value = []

  stateRows.value = await loadStatesSafe(item.code)
  await reloadCities('')
}

function selectState(item: RegionStateVO) {
  openPanel.value = ''
  stateQuery.value = ''
  stateInput.value = regionLabel(item)
  cityInput.value = ''
  cityQuery.value = ''
  void nextTick(blurActiveField)
  if (item.code === props.modelValue.provinceCode) return

  emitModel({ provinceCode: item.code, province: regionLabel(item), cityId: null, city: '' })
  void reloadCities('')
}

function selectCity(item: RegionCityVO) {
  openPanel.value = ''
  cityQuery.value = ''
  // 城市名取访客语言的名字落进表单；真正落库时后端会用 cities.native 覆盖它（契约 §3.4.4）
  cityInput.value = regionLabel(item)
  void nextTick(blurActiveField)
  if (item.id === props.modelValue.cityId) return

  emitModel({ cityId: item.id, city: regionLabel(item) })
}

/* ── 「使用 "{输入值}"」：无结果时才有的显式动作，点了才落成手填 ────────── */
function useTypedState() {
  const value = stateInput.value.trim()
  if (!value) return

  openPanel.value = ''
  stateQuery.value = ''
  // 省/州落成手填：编码清空 + 城市连带清空（旧城市是挂在该省下的，省变了城市就不成立了）
  emitModel({ provinceCode: '', province: value, cityId: null, city: '' })
  cityInput.value = ''
  cityQuery.value = ''
  void nextTick(blurActiveField)
  // 换回"全国"口径重新拉城市列表（provinceCode 已清空）
  void reloadCities('')
}

function useTypedCity() {
  const value = cityInput.value.trim()
  if (!value) return

  openPanel.value = ''
  cityQuery.value = ''
  // 手填城市：cityId 必须为 null，后端此时才采信文字（契约 §3.4.4）
  emitModel({ cityId: null, city: value })
  void nextTick(blurActiveField)
}

/* ── 城市关键词：防抖 ───────────────────────────────────────────────────
   城市表 15 万行，每敲一个字就打一次模糊查询既慢又会把后端打满；
   300ms 足够覆盖正常打字节奏，又不至于让用户觉得"卡住了"。 */
const CITY_SEARCH_DEBOUNCE_MS = 300
let citySearchTimer: ReturnType<typeof setTimeout> | null = null
watch(cityQuery, (value) => {
  // 浮层没开（例如失焦还原时把过滤词清零）就不该再查一次，避免无意义请求
  if (openPanel.value !== 'city') return
  if (citySearchTimer) clearTimeout(citySearchTimer)
  citySearchTimer = setTimeout(() => {
    void reloadCities(value)
  }, CITY_SEARCH_DEBOUNCE_MS)
})

/* ── 与父组件的同步 ───────────────────────────────────────────────────── */
async function hydrateFromModel() {
  if (!mounted.value) return

  countriesError.value = ''
  try {
    await loadCountries()
  } catch (error) {
    countriesError.value = toErrorMessage(error)
    return
  }

  if (!props.modelValue.countryCode) {
    stateRows.value = []
    cityRows.value = []
    cityTruncated.value = false
    return
  }

  stateRows.value = await loadStatesSafe(props.modelValue.countryCode)
  await reloadCities('')
}

/**
 * 父组件把整份地区换掉时（编辑弹层拉到最新详情、或从"新增"切到"编辑"）重新解析：
 * 按 code 反查名称，再把三个输入框刷成解析结果。自己 emit 出去的变化由 lastEmitted 挡掉。
 */
watch(() => props.modelValue, (value) => {
  if (!mounted.value) return
  if (lastEmitted && isSameRegionSelection(value, lastEmitted)) return
  lastEmitted = null
  openPanel.value = ''
  void (async () => {
    await hydrateFromModel()
    syncInputsFromModel()
  })()
}, { deep: true })

/* ── 点击外部收起面板 ────────────────────────────────────────────────────
   监听器只在 onMounted 里挂（服务端没有 document），卸载时摘掉，避免内存泄漏。 */
function onPointerDown(event: Event) {
  if (!openPanel.value) return
  const target = event.target as Node | null
  if (rootRef.value && target && rootRef.value.contains(target)) return
  closeAndRevert()
}

/**
 * 浮层是 absolute 定位、和输入框在同一个滚动容器里，容器滚动时两者一起走，
 * 所以**不需要**监听 scroll —— 只需要在视口尺寸变化时重算宽度与翻上/翻下。
 */
function onViewportChange() {
  if (openPanel.value) measurePanel()
}

/**
 * SSR 一致性：数据在 onMounted 之后才取。
 *
 * 国家列表若在 SSR 期就取好，服务端渲染出的是 250 条选项、客户端 hydrate 首帧却还没数据
 * （useState 的初始值在两端不同来源），会直接触发 hydration mismatch。
 * 放到挂载后再取，两端首帧都是"空列表 + 空输入框"，编辑回显则由 hydrateFromModel 补齐。
 */
onMounted(async () => {
  mounted.value = true
  document.addEventListener('pointerdown', onPointerDown)
  window.addEventListener('resize', onViewportChange)
  await hydrateFromModel()
  syncInputsFromModel()
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown)
  window.removeEventListener('resize', onViewportChange)
  if (citySearchTimer) clearTimeout(citySearchTimer)
})
</script>
