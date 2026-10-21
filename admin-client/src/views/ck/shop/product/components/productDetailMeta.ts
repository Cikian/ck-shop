/**
 * 商品详情模块 / 规格参数的共享元数据与工具。
 *
 * 契约依据：docs/商品详情-设计契约.md
 * - 1.3 模块类型与 content JSON（含 radius / columns / itemMargin）
 * - 1.4 模块四方向间距（margin*，为「长图切割后无缝拼接」而加）
 * - 3.3 提交前的前端校验（含布局数值范围）
 * - 4.3 店铺端渲染细则（管理端预览保持同一套结构语义）
 * - 第五节 富文本双层清洗（这里提供管理端预览用的渲染期白名单清洗）
 *
 * 说明：只放纯函数/常量，不发请求，方便编辑器、预览、表单三处共用。
 */

/** 详情模块（前端结构：languageCode + blockType + content 对象 + 四方向间距） */
export interface DetailBlockItem {
  id?: string;
  languageCode: string;
  blockType: string;
  content: Record<string, any>;
  sortOrder?: number;
  /** 模块四方向间距（px，契约 1.4，默认 0；存表列，不在 content 里） */
  marginTop?: number;
  marginRight?: number;
  marginBottom?: number;
  marginLeft?: number;
  /** 仅前端使用的稳定 key，提交前必须剔除 */
  __uid?: string;
}

/** 规格参数 KV 行 */
export interface ProductParamItem {
  id?: string;
  languageCode: string;
  paramName: string;
  paramValue: string;
  sortOrder?: number;
  /** 仅前端使用的稳定 key，提交前必须剔除 */
  __uid?: string;
}

export interface BlockTypeMeta {
  value: string;
  label: string;
  desc: string;
  icon: string;
  color: string;
}

/** 每语言模块上限（契约 3.3） */
export const DETAIL_BLOCK_LIMIT = 50;
/** 每语言参数上限（契约 3.3） */
export const DETAIL_PARAM_LIMIT = 100;
/** 多图宫格单模块图片上限（契约 1.3：1–9 张） */
export const DETAIL_GRID_IMAGE_LIMIT = 9;

/** 模块四方向间距上限（契约 1.4：0–500） */
export const DETAIL_MARGIN_LIMIT = 500;
/** 图片圆角上限（契约 1.3：0–200） */
export const DETAIL_RADIUS_LIMIT = 200;
/** 宫格图片间距上限（契约 1.3：0–200） */
export const DETAIL_GRID_ITEM_MARGIN_LIMIT = 200;
/** 宫格列数范围（契约 1.3：1–6，默认 2） */
export const DETAIL_GRID_COLUMNS_MIN = 1;
export const DETAIL_GRID_COLUMNS_MAX = 6;
export const DETAIL_GRID_COLUMNS_DEFAULT = 2;
/** 编辑器里的间距快捷值（契约 3.1：0 / 8 / 16 / 24） */
export const DETAIL_MARGIN_QUICK_VALUES = [0, 8, 16, 24];

/** 四方向间距/图片间距的通用结构 */
export interface BoxEdges {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

/** 8 种模块类型：顺序即「新增模块」弹层里的展示顺序 */
export const DETAIL_BLOCK_TYPES: BlockTypeMeta[] = [
  { value: 'IMAGE', label: '整幅图片', desc: '一张通栏大图', icon: 'ant-design:picture-outlined', color: 'blue' },
  { value: 'IMAGE_GRID', label: '多图宫格', desc: '2 / 3 列多图', icon: 'ant-design:appstore-outlined', color: 'cyan' },
  { value: 'IMAGE_TEXT', label: '图文', desc: '左图右文或反之', icon: 'ant-design:layout-outlined', color: 'geekblue' },
  { value: 'TEXT', label: '文本段落', desc: '标题 + 纯文本', icon: 'ant-design:align-left-outlined', color: 'green' },
  { value: 'RICH_TEXT', label: '富文本', desc: '排版自由的图文', icon: 'ant-design:form-outlined', color: 'purple' },
  { value: 'VIDEO', label: '视频', desc: 'B 站 / YouTube / 直链', icon: 'ant-design:video-camera-outlined', color: 'orange' },
  { value: 'TITLE', label: '小节标题', desc: '详情内的分节标题', icon: 'ant-design:font-size-outlined', color: 'magenta' },
  { value: 'DIVIDER', label: '分隔线', desc: '纯装饰分隔', icon: 'ant-design:minus-outlined', color: 'gold' },
];

const BLOCK_TYPE_MAP: Record<string, BlockTypeMeta> = DETAIL_BLOCK_TYPES.reduce(
  (acc, item) => {
    acc[item.value] = item;
    return acc;
  },
  {} as Record<string, BlockTypeMeta>
);

const FALLBACK_BLOCK_META: BlockTypeMeta = {
  value: 'UNKNOWN',
  label: '未知模块',
  desc: '',
  icon: 'ant-design:question-circle-outlined',
  color: 'gold',
};

const VIDEO_PROVIDERS: Record<string, string> = {
  bilibili: 'B 站',
  youtube: 'YouTube',
  file: '视频直链',
};

/** 稳定唯一键（仅前端渲染/拖拽使用，提交时剔除） */
export function uid(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

export function isKnownBlockType(blockType: string): boolean {
  return !!BLOCK_TYPE_MAP[String(blockType || '').toUpperCase()];
}

export function blockTypeMeta(blockType: string): BlockTypeMeta {
  return BLOCK_TYPE_MAP[String(blockType || '').toUpperCase()] || FALLBACK_BLOCK_META;
}

export function videoProviderLabel(provider: string): string {
  return VIDEO_PROVIDERS[provider] || '视频';
}

function str(value: any): string {
  return value === null || value === undefined ? '' : String(value);
}

/** 把任意输入收敛成数字；空值/非数字返回 null（用于区分「缺省」与「非法」） */
function toFiniteNumber(value: any): number | null {
  if (value === null || value === undefined || value === '') return null;
  const num = Number(value);
  return Number.isFinite(num) ? num : null;
}

/**
 * 读取时夹取整数：缺失/非法 → fallback；超范围 → 夹到边界（契约 1.3：读取时按范围夹取）。
 * 注意与提交校验区分：校验阶段越界要拦下，不能静默夹取。
 */
export function clampInt(value: any, min: number, max: number, fallback: number): number {
  const num = toFiniteNumber(value);
  if (num === null) return fallback;
  return Math.min(max, Math.max(min, Math.round(num)));
}

/** 模块间距（0–500，缺省 0） */
export function clampBlockMargin(value: any): number {
  return clampInt(value, 0, DETAIL_MARGIN_LIMIT, 0);
}

/** 图片圆角（0–200，缺省 0） */
export function clampRadius(value: any): number {
  return clampInt(value, 0, DETAIL_RADIUS_LIMIT, 0);
}

/** 宫格列数（1–6，缺省 2） */
export function clampGridColumns(value: any): number {
  return clampInt(value, DETAIL_GRID_COLUMNS_MIN, DETAIL_GRID_COLUMNS_MAX, DETAIL_GRID_COLUMNS_DEFAULT);
}

/** 宫格内图片间距（0–200，缺省 0） */
export function clampItemMargin(value: any): number {
  return clampInt(value, 0, DETAIL_GRID_ITEM_MARGIN_LIMIT, 0);
}

/** 图片类模块：只有这些模块有「图片圆角」 */
export function isImageLikeBlock(blockType: string): boolean {
  return blockType === 'IMAGE' || blockType === 'IMAGE_GRID' || blockType === 'IMAGE_TEXT';
}

/** 模块四方向间距（统一按 0 兜底，避免模板里出现 undefined） */
export function blockMarginsOf(block: DetailBlockItem): BoxEdges {
  return {
    top: clampBlockMargin(block.marginTop),
    right: clampBlockMargin(block.marginRight),
    bottom: clampBlockMargin(block.marginBottom),
    left: clampBlockMargin(block.marginLeft),
  };
}

/** 图片圆角（非图片类模块恒为 0） */
export function imageRadiusOf(block: DetailBlockItem): number {
  return isImageLikeBlock(block.blockType) ? clampRadius(block.content?.radius) : 0;
}

/** 宫格列数（非网格恒为 0） */
export function gridColumnsOf(block: DetailBlockItem): number {
  return block.blockType === 'IMAGE_GRID' ? clampGridColumns(block.content?.columns) : 0;
}

/** 宫格内图片四方向间距（itemMargin，缺省全 0） */
export function gridItemMarginsOf(block: DetailBlockItem): BoxEdges {
  const source = block.content?.itemMargin;
  const itemMargin = source && typeof source === 'object' ? source : {};
  return {
    top: clampItemMargin(itemMargin.top),
    right: clampItemMargin(itemMargin.right),
    bottom: clampItemMargin(itemMargin.bottom),
    left: clampItemMargin(itemMargin.left),
  };
}

/** 读取时的 itemMargin 归一化（契约 1.3） */
export function normalizeItemMargin(source: any): BoxEdges {
  const raw = source && typeof source === 'object' ? source : {};
  return {
    top: clampItemMargin(raw.top),
    right: clampItemMargin(raw.right),
    bottom: clampItemMargin(raw.bottom),
    left: clampItemMargin(raw.left),
  };
}

// ---------------- 预览内联样式（与店铺端同规则，契约 4.3） ----------------

/** 模块最外层内联 margin（默认 0；容器不得再补统一间距） */
export function blockMarginStyle(block: DetailBlockItem): Record<string, string> {
  const margins = blockMarginsOf(block);
  return { margin: `${margins.top}px ${margins.right}px ${margins.bottom}px ${margins.left}px` };
}

/** 图片 border-radius 取 content.radius（缺省 0，即直角） */
export function imageRadiusStyle(block: DetailBlockItem): Record<string, string> {
  return { borderRadius: `${imageRadiusOf(block)}px` };
}

/** 宫格：CSS Grid repeat(columns, minmax(0, 1fr)) */
export function gridTemplateStyle(block: DetailBlockItem): Record<string, string> {
  return { gridTemplateColumns: `repeat(${gridColumnsOf(block)}, minmax(0, 1fr))` };
}

/** 宫格每一格的内联 margin（= itemMargin） */
export function gridItemMarginStyle(block: DetailBlockItem): Record<string, string> {
  const margins = gridItemMarginsOf(block);
  return { margin: `${margins.top}px ${margins.right}px ${margins.bottom}px ${margins.left}px` };
}

/** 把接口/旧数据里的 content 统一成对象（契约：接口上始终是对象；这里对字符串做兜底解析） */
function toContentObject(content: any): Record<string, any> {
  if (!content) return {};
  if (typeof content === 'string') {
    try {
      const parsed = JSON.parse(content);
      return parsed && typeof parsed === 'object' ? parsed : {};
    } catch (_e) {
      return {};
    }
  }
  return typeof content === 'object' ? content : {};
}

/** 每种模块的默认 content（新增模块、回填缺字段时补齐，保证 v-model 有落点） */
export function defaultBlockContent(blockType: string): Record<string, any> {
  switch (blockType) {
    case 'IMAGE':
      return { url: '', alt: '', radius: 0 };
    case 'IMAGE_GRID':
      return { columns: DETAIL_GRID_COLUMNS_DEFAULT, itemMargin: { top: 0, right: 0, bottom: 0, left: 0 }, radius: 0, images: [] };
    case 'IMAGE_TEXT':
      return { url: '', title: '', text: '', layout: 'left', alt: '', radius: 0 };
    case 'TEXT':
      return { title: '', text: '' };
    case 'RICH_TEXT':
      return { html: '' };
    case 'VIDEO':
      return { provider: 'bilibili', url: '', poster: '' };
    case 'TITLE':
      return { text: '', sub: '' };
    case 'DIVIDER':
      return {};
    default:
      return {};
  }
}

/** 用契约 1.3 的结构收敛 content，丢弃未知字段 */
export function normalizeBlockContent(blockType: string, content: any): Record<string, any> {
  const source = toContentObject(content);
  switch (blockType) {
    case 'IMAGE':
      return { url: str(source.url), alt: str(source.alt), radius: clampRadius(source.radius) };
    case 'IMAGE_GRID': {
      const images = Array.isArray(source.images)
        ? source.images
            .filter((image: any) => image && typeof image === 'object')
            .map((image: any) => ({ url: str(image.url), alt: str(image.alt) }))
            .slice(0, DETAIL_GRID_IMAGE_LIMIT)
        : [];
      return {
        columns: clampGridColumns(source.columns),
        itemMargin: normalizeItemMargin(source.itemMargin),
        radius: clampRadius(source.radius),
        images,
      };
    }
    case 'IMAGE_TEXT':
      return {
        url: str(source.url),
        title: str(source.title),
        text: str(source.text),
        layout: source.layout === 'right' ? 'right' : 'left',
        alt: str(source.alt),
        radius: clampRadius(source.radius),
      };
    case 'TEXT':
      return { title: str(source.title), text: str(source.text) };
    case 'RICH_TEXT':
      return { html: str(source.html) };
    case 'VIDEO':
      return {
        provider: VIDEO_PROVIDERS[source.provider] ? source.provider : 'bilibili',
        url: str(source.url),
        poster: str(source.poster),
      };
    case 'TITLE':
      return { text: str(source.text), sub: str(source.sub) };
    case 'DIVIDER':
      return {};
    default:
      return {};
  }
}

/** 新建一个模块（间距默认全 0，图片圆角默认 0） */
export function createDetailBlock(blockType: string, languageCode: string): DetailBlockItem {
  const type = isKnownBlockType(blockType) ? blockType : 'IMAGE';
  return {
    languageCode,
    blockType: type,
    content: defaultBlockContent(type),
    sortOrder: 0,
    marginTop: 0,
    marginRight: 0,
    marginBottom: 0,
    marginLeft: 0,
    __uid: uid('blk'),
  };
}

/**
 * 回填：把接口返回的模块行收敛成前端结构。
 * 未知 blockType 直接跳过（契约 1.3：读取时跳过，避免历史脏数据把页面搞崩）。
 * 四个 margin* 缺省/非法按 0，超范围按边界夹取（契约 1.4）。
 */
export function normalizeDetailBlock(raw: any, fallbackLanguage: string): DetailBlockItem | null {
  if (!raw || typeof raw !== 'object') return null;
  const blockType = str(raw.blockType).toUpperCase();
  if (!isKnownBlockType(blockType)) return null;
  return {
    id: raw.id || undefined,
    languageCode: str(raw.languageCode) || fallbackLanguage,
    blockType,
    content: normalizeBlockContent(blockType, raw.content),
    sortOrder: Number.isFinite(Number(raw.sortOrder)) ? Number(raw.sortOrder) : 0,
    marginTop: clampBlockMargin(raw.marginTop),
    marginRight: clampBlockMargin(raw.marginRight),
    marginBottom: clampBlockMargin(raw.marginBottom),
    marginLeft: clampBlockMargin(raw.marginLeft),
    __uid: uid('blk'),
  };
}

/** 回填：规格参数行（参数名/值都为空的行视为脏数据，跳过） */
export function normalizeProductParam(raw: any, fallbackLanguage: string): ProductParamItem | null {
  if (!raw || typeof raw !== 'object') return null;
  const paramName = str(raw.paramName);
  const paramValue = str(raw.paramValue);
  if (!paramName.trim() && !paramValue.trim()) return null;
  return {
    id: raw.id || undefined,
    languageCode: str(raw.languageCode) || fallbackLanguage,
    paramName,
    paramValue,
    sortOrder: Number.isFinite(Number(raw.sortOrder)) ? Number(raw.sortOrder) : 0,
    __uid: uid('prm'),
  };
}

// ---------------- 富文本清洗（管理端预览渲染期白名单） ----------------

const ALLOWED_TAGS = new Set([
  'p',
  'br',
  'strong',
  'b',
  'em',
  'i',
  'u',
  's',
  'ul',
  'ol',
  'li',
  'h2',
  'h3',
  'h4',
  'blockquote',
  'a',
  'img',
  'span',
  'div',
  'table',
  'thead',
  'tbody',
  'tr',
  'td',
  'th',
  'hr',
]);

const ALLOWED_ATTRS: Record<string, string[]> = {
  a: ['href', 'title', 'target', 'rel'],
  img: ['src', 'alt', 'width', 'height'],
  td: ['colspan', 'rowspan'],
  th: ['colspan', 'rowspan'],
};

/** 这些标签整块丢弃（连内容一起），避免把脚本/表单/外嵌控件渲染出来 */
const DROP_WITH_CONTENT = new Set([
  'script',
  'style',
  'iframe',
  'object',
  'embed',
  'form',
  'input',
  'button',
  'textarea',
  'select',
  'link',
  'meta',
  'noscript',
  'svg',
  'math',
  'template',
]);

const SAFE_PROTOCOL = /^(https?:\/\/|\/|#|mailto:|tel:)/i;

/**
 * 白名单清洗富文本 HTML。
 * 契约第五节要求：渲染时（店铺端 + 管理端预览）过一遍同一套白名单，
 * 防止历史脏数据 / 绕过接口直接写库。
 *
 * 局限：这是「收敛风险」而非密码学级保证（与契约说明一致）。
 */
export function sanitizeHtml(rawHtml: any): string {
  const html = str(rawHtml);
  if (!html.trim()) return '';
  if (typeof DOMParser === 'undefined') {
    // 非浏览器环境兜底：去掉标签，绝不原样输出未清洗 HTML
    return html.replace(/<[^>]*>/g, '');
  }
  const doc = new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html');
  sanitizeChildren(doc.body);
  return doc.body.innerHTML;
}

function sanitizeChildren(parent: Node): void {
  const doc = parent.ownerDocument;
  Array.from(parent.childNodes).forEach((node) => {
    if (node.nodeType === 3) return;
    if (node.nodeType !== 1) {
      parent.removeChild(node);
      return;
    }
    const el = node as Element;
    const tag = el.tagName.toLowerCase();
    if (DROP_WITH_CONTENT.has(tag)) {
      el.remove();
      return;
    }
    if (!ALLOWED_TAGS.has(tag)) {
      // 未知标签：保留其文字内容，去掉标签本身
      sanitizeChildren(el);
      const fragment = doc.createDocumentFragment();
      while (el.firstChild) {
        fragment.appendChild(el.firstChild);
      }
      parent.replaceChild(fragment, el);
      return;
    }
    const allowedAttrs = ALLOWED_ATTRS[tag] || [];
    Array.from(el.attributes).forEach((attr) => {
      const name = attr.name.toLowerCase();
      if (name.startsWith('on') || !allowedAttrs.includes(name)) {
        el.removeAttribute(attr.name);
        return;
      }
      const value = attr.value.trim();
      if (name === 'href' && !SAFE_PROTOCOL.test(value)) {
        el.removeAttribute(attr.name);
        return;
      }
      // 契约第五节：img 只允许 http(s):// 或 / 开头
      if (name === 'src' && !/^(https?:\/\/|\/)/i.test(value)) {
        el.removeAttribute(attr.name);
      }
    });
    if (tag === 'a' && el.getAttribute('href')) {
      el.setAttribute('rel', 'noopener noreferrer');
      if (/^https?:\/\//i.test(el.getAttribute('href') || '')) {
        el.setAttribute('target', '_blank');
      }
    }
    sanitizeChildren(el);
  });
}

/** 取富文本的纯文本（用于校验「去标签后是否为空」与折叠摘要） */
export function htmlToText(rawHtml: any): string {
  const html = str(rawHtml);
  if (!html.trim()) return '';
  if (typeof DOMParser === 'undefined') {
    return html
      .replace(/<[^>]*>/g, ' ')
      .replace(/&nbsp;/gi, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }
  const doc = new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html');
  return (doc.body.textContent || '').replace(/\s+/g, ' ').trim();
}

// ---------------- 视频解析（契约 4.3） ----------------

const BILIBILI_BVID = /(BV[0-9A-Za-z]{10})/;
const YOUTUBE_ID = /(?:youtu\.be\/|youtube(?:-nocookie)?\.com\/(?:watch\?(?:.*&)?v=|embed\/|shorts\/|v\/)|[?&]v=)([0-9A-Za-z_-]{11})/;

/** 从视频 URL 解析平台 ID；解析不出来返回空串（此时整块不渲染，不输出坏 iframe） */
export function parseVideoId(provider: string, url: string): string {
  const text = str(url).trim();
  if (!text) return '';
  if (provider === 'bilibili') {
    const matched = text.match(BILIBILI_BVID);
    return matched ? matched[1] : '';
  }
  if (provider === 'youtube') {
    const matched = text.match(YOUTUBE_ID);
    return matched ? matched[1] : '';
  }
  return '';
}

/** 预览里的 iframe 地址（bilibili / youtube）；解析失败或 file 类型返回空串 */
export function buildVideoEmbedUrl(provider: string, url: string): string {
  const id = parseVideoId(provider, url);
  if (!id) return '';
  if (provider === 'bilibili') {
    return `https://player.bilibili.com/player.html?bvid=${id}&page=1&high_quality=1&danmaku=0&autoplay=0`;
  }
  return `https://www.youtube-nocookie.com/embed/${id}`;
}

/** 视频模块的 URL 是否合法（契约 3.3） */
export function isVideoUrlValid(provider: string, url: string): boolean {
  const text = str(url).trim();
  if (!text) return false;
  if (provider === 'file') {
    return /^(https?:\/\/|\/)/i.test(text);
  }
  return !!parseVideoId(provider, text);
}

// ---------------- 摘要 / 校验 ----------------

/** 折叠卡片上的一行摘要 */
export function blockSummary(block: DetailBlockItem): string {
  const content = block.content || {};
  switch (block.blockType) {
    case 'IMAGE':
      return content.url ? '已选一张图片' : '未选择图片';
    case 'IMAGE_GRID': {
      const count = (content.images || []).filter((image: any) => str(image?.url).trim()).length;
      return `${clampGridColumns(content.columns)} 列 · ${count} 张图片`;
    }
    case 'IMAGE_TEXT':
      return htmlToText(content.title) || htmlToText(content.text) || (content.url ? '已选图片' : '未选择图片');
    case 'TEXT':
      return htmlToText(content.title) || htmlToText(content.text) || '空文本';
    case 'RICH_TEXT':
      return htmlToText(content.html) || '空富文本';
    case 'VIDEO': {
      const providerLabel = videoProviderLabel(content.provider);
      return content.url ? `${providerLabel} · 已填写链接` : `${providerLabel} · 未填写链接`;
    }
    case 'TITLE':
      return htmlToText(content.text) || '未填写标题';
    case 'DIVIDER':
      return '分隔线，无需配置';
    default:
      return '';
  }
}

/**
 * 折叠卡片上附带的布局摘要（契约 3.1「折叠态摘要可附带显示间距/圆角状态」）。
 * 全默认（间距 0、圆角 0、列数 2、图距 0）时返回空串，避免噪音。
 */
export function blockLayoutSummary(block: DetailBlockItem): string {
  const parts: string[] = [];
  const margins = blockMarginsOf(block);
  if (margins.top || margins.right || margins.bottom || margins.left) {
    parts.push(`间距 ${margins.top}/${margins.right}/${margins.bottom}/${margins.left}`);
  }
  if (isImageLikeBlock(block.blockType)) {
    const radius = imageRadiusOf(block);
    if (radius) {
      parts.push(`圆角 ${radius}`);
    }
  }
  if (block.blockType === 'IMAGE_GRID') {
    // 列数已包含在 blockSummary（「N 列 · M 张图片」）里，这里只补充图片间距
    const itemMargins = gridItemMarginsOf(block);
    if (itemMargins.top || itemMargins.right || itemMargins.bottom || itemMargins.left) {
      parts.push(`图距 ${itemMargins.top}/${itemMargins.right}/${itemMargins.bottom}/${itemMargins.left}`);
    }
  }
  return parts.join(' · ');
}

export interface DetailBlockError {
  index: number;
  block: DetailBlockItem;
  message: string;
}

const MARGIN_FIELD_LABELS: Record<string, string> = {
  marginTop: '上',
  marginRight: '右',
  marginBottom: '下',
  marginLeft: '左',
};

const ITEM_MARGIN_LABELS: Record<string, string> = {
  top: '上',
  right: '右',
  bottom: '下',
  left: '左',
};

/** 提交校验用：空值视为缺省（默认值），否则必须是范围内的整数 */
function isIntegerInRange(value: any, min: number, max: number): boolean {
  if (value === null || value === undefined || value === '') return true;
  const num = Number(value);
  return Number.isFinite(num) && Number.isInteger(num) && num >= min && num <= max;
}

/** 契约 3.3：布局数值范围校验（越界给出可执行的中文提示） */
function layoutErrorMessage(block: DetailBlockItem, label: string): string | null {
  const marginFields = ['marginTop', 'marginRight', 'marginBottom', 'marginLeft'];
  for (const field of marginFields) {
    if (!isIntegerInRange(block[field as keyof DetailBlockItem], 0, DETAIL_MARGIN_LIMIT)) {
      return `「${label}」模块间距（${MARGIN_FIELD_LABELS[field]}）必须是 0–${DETAIL_MARGIN_LIMIT} 的整数`;
    }
  }
  if (isImageLikeBlock(block.blockType) && !isIntegerInRange(block.content?.radius, 0, DETAIL_RADIUS_LIMIT)) {
    return `「${label}」图片圆角必须是 0–${DETAIL_RADIUS_LIMIT} 的整数`;
  }
  if (block.blockType === 'IMAGE_GRID') {
    if (!isIntegerInRange(block.content?.columns, DETAIL_GRID_COLUMNS_MIN, DETAIL_GRID_COLUMNS_MAX)) {
      return `「${label}」列数必须是 ${DETAIL_GRID_COLUMNS_MIN}–${DETAIL_GRID_COLUMNS_MAX} 的整数`;
    }
    const itemMargin = block.content?.itemMargin || {};
    for (const field of ['top', 'right', 'bottom', 'left']) {
      if (!isIntegerInRange(itemMargin[field], 0, DETAIL_GRID_ITEM_MARGIN_LIMIT)) {
        return `「${label}」图片间距（${ITEM_MARGIN_LABELS[field]}）必须是 0–${DETAIL_GRID_ITEM_MARGIN_LIMIT} 的整数`;
      }
    }
  }
  return null;
}

/** 契约 3.3：逐块校验，返回第一个错误（用于切页签 + 聚焦） */
export function findDetailBlockError(blocks: DetailBlockItem[]): DetailBlockError | null {
  for (let index = 0; index < blocks.length; index++) {
    const block = blocks[index];
    const content = block.content || {};
    const label = blockTypeMeta(block.blockType).label;
    const layoutMessage = layoutErrorMessage(block, label);
    if (layoutMessage) {
      return { index, block, message: layoutMessage };
    }
    if (block.blockType === 'IMAGE') {
      if (!str(content.url).trim()) {
        return { index, block, message: `「${label}」缺少图片，请上传或粘贴图片链接` };
      }
    } else if (block.blockType === 'IMAGE_GRID') {
      const images = (content.images || []).filter((image: any) => str(image?.url).trim());
      if (!images.length) {
        return { index, block, message: `「${label}」至少需要 1 张图片` };
      }
    } else if (block.blockType === 'IMAGE_TEXT') {
      if (!str(content.url).trim()) {
        return { index, block, message: `「${label}」缺少图片，请上传或粘贴图片链接` };
      }
    } else if (block.blockType === 'TEXT') {
      if (!str(content.title).trim() && !str(content.text).trim()) {
        return { index, block, message: '「文本段落」内容为空，请填写标题或正文' };
      }
    } else if (block.blockType === 'RICH_TEXT') {
      if (!htmlToText(content.html)) {
        return { index, block, message: '「富文本」内容为空，请填写内容' };
      }
    } else if (block.blockType === 'VIDEO') {
      const url = str(content.url).trim();
      if (!url) {
        return { index, block, message: '「视频」缺少链接' };
      }
      if (!isVideoUrlValid(content.provider, url)) {
        return {
          index,
          block,
          message:
            content.provider === 'file' ? '「视频」直链必须以 http(s):// 或 / 开头' : '「视频」无法从链接解析出视频 ID，请检查 B 站 / YouTube 链接',
        };
      }
    }
  }
  return null;
}

export interface DetailParamError {
  index: number;
  param: ProductParamItem;
  message: string;
}

/** 契约 3.3：参数名、参数值都必填 */
export function findParamError(params: ProductParamItem[]): DetailParamError | null {
  for (let index = 0; index < params.length; index++) {
    const param = params[index];
    const name = str(param.paramName).trim();
    const value = str(param.paramValue).trim();
    if (!name && !value) {
      return { index, param, message: `第 ${index + 1} 条参数还没填写参数名和参数值` };
    }
    if (!name) {
      return { index, param, message: `第 ${index + 1} 条参数缺少参数名` };
    }
    if (!value) {
      return { index, param, message: `第 ${index + 1} 条参数（${name}）缺少参数值` };
    }
  }
  return null;
}

// ---------------- 提交清洗（剔除前端字段，content 保持对象） ----------------

/**
 * 单个模块的提交结构：不带 __uid；多图宫格过滤空图；content 始终是对象。
 * 四个 margin* 是模块顶层字段（与 content 平级，契约 2.1），默认 0。
 */
export function detailBlockForSubmit(block: DetailBlockItem, languageCode: string, sortOrder: number): Record<string, any> {
  const content = normalizeBlockContent(block.blockType, block.content);
  // 编辑过程中留下的空图片行不提交（校验已保证至少有一张有效图）
  if (block.blockType === 'IMAGE_GRID' && Array.isArray(content.images)) {
    content.images = content.images.filter((image: any) => str(image.url).trim());
  }
  const margins = blockMarginsOf(block);
  const item: Record<string, any> = {
    languageCode,
    blockType: block.blockType,
    content,
    sortOrder,
    marginTop: margins.top,
    marginRight: margins.right,
    marginBottom: margins.bottom,
    marginLeft: margins.left,
  };
  if (block.id) {
    item.id = block.id;
  }
  return item;
}

/**
 * 单条参数的提交结构：契约 2.1 的 params 只有四个字段（不带 id / __uid），
 * 参数名、参数值去掉首尾空白；后端按整体替换语义处理。
 */
export function productParamForSubmit(param: ProductParamItem, languageCode: string, sortOrder: number): Record<string, any> {
  return {
    languageCode,
    paramName: str(param.paramName).trim(),
    paramValue: str(param.paramValue).trim(),
    sortOrder,
  };
}
