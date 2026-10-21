// utils/sanitize-html.ts
//
// 富文本白名单清洗（纯字符串实现，SSR / 客户端 / Node 自测都能直接跑）。
//
// 为什么不用 DOMPurify：本项目 npm 取包受限，新增依赖会破坏现有构建链路
// （契约第五节明确要求两层白名单自己实现，且不引入第三方清洗库）。
// 为什么不用 DOM API：清洗要在 SSR 阶段执行（服务端没有 window / document），
// 用 DOMParser 会让"渲染时清洗"在首屏 HTML 上直接失效。
//
// 契约：docs/商品详情-设计契约.md 第五节
//   · 允许标签：p br strong b em i u s ul ol li h2 h3 h4 blockquote a img span div
//               table thead tbody tr td th hr
//   · 允许属性：a[href,title,target,rel]、img[src,alt,width,height]、td/th[colspan,rowspan]
//   · 强制：a 加 rel="noopener noreferrer"、外链 target="_blank"；img 只允许 http(s):// 或 /
//   · 移除：script style iframe object embed form input link meta、所有 on* 事件、
//           javascript: / data: 协议
//
// 局限（如实标注，别把它当成密码学级保证）：这是"收敛风险"的白名单实现，
// 不是完整的 HTML 解析器。它按"标签 / 属性 / 协议"三层白名单裁剪，
// 未知标签只丢弃标签本身（内容文字继续保留并转义），危险容器标签连内容一起丢弃。
// 富文本的写入入口只有管理端登录用户，渲染端再过一遍是为了防历史脏数据。

/** 契约允许的标签（小写） */
const ALLOWED_TAGS = new Set([
  'p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'ul', 'ol', 'li',
  'h2', 'h3', 'h4', 'blockquote', 'a', 'img', 'span', 'div',
  'table', 'thead', 'tbody', 'tr', 'td', 'th', 'hr',
])

/** 允许出现自闭合形式的空元素（输出时统一成 `<br>` / `<hr>` / `<img …>`） */
const VOID_TAGS = new Set(['br', 'hr', 'img'])

/**
 * 连内容一起丢弃的容器标签。
 * 这些标签里的"文字"不是给人看的正文（脚本/样式/嵌入对象），保留下来会变成乱码文本，
 * 所以跳过到对应结束标签；找不到结束标签时直接丢弃剩余内容（宁缺勿错）。
 */
const DROP_TAG_WITH_CONTENT = new Set([
  'script', 'style', 'iframe', 'object', 'embed', 'form', 'input', 'link', 'meta',
  'textarea', 'select', 'option', 'button', 'svg', 'math', 'template', 'noscript',
  'title', 'head', 'frameset', 'frame', 'applet', 'base', 'video', 'audio', 'source',
])

/** 上面这批里属于"空元素"的：丢弃时不需要找结束标签（找了会把后文吃掉） */
const DROP_TAG_VOID = new Set(['input', 'link', 'meta', 'base', 'embed', 'source', 'frame'])

/** 每个标签允许的属性白名单；不在表里的标签一律不允许任何属性 */
const TAG_ATTRS: Record<string, string[]> = {
  a: ['href', 'title', 'target', 'rel'],
  img: ['src', 'alt', 'width', 'height'],
  td: ['colspan', 'rowspan'],
  th: ['colspan', 'rowspan'],
}

/** 需要剔除的控制字符（同时防 `java\tscript:` 这类用空白/控制符混淆的伪协议） */
// eslint-disable-next-line no-control-regex
const CONTROL_CHARS = /[\u0000-\u0020\u007f-\u009f]/g

const ATTR_PATTERN = /([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'<>`=]*)))?/g

interface ParsedTag {
  closing: boolean
  name: string
  attrsRaw: string
  selfClosing: boolean
  /** 标签结束（`>` 之后）的下标 */
  end: number
}

function escapeText(value: string): string {
  // 文本节点只需要挡住 `<` / `>`：`&` 保留，否则 `&nbsp;` 这类既有实体会被显示成字面量。
  // 实体不会被浏览器二次解析成标签，因此保留 `&` 不构成 XSS 入口。
  return value.replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function escapeAttr(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

/** 去掉控制字符后转小写，用于协议判断 */
function normalizeForProtocolCheck(value: string): string {
  return value.replace(CONTROL_CHARS, '').toLowerCase()
}

/** a[href] 协议白名单：相对地址 / #锚点 / http(s) / mailto / tel */
function isAllowedHref(value: string): boolean {
  const trimmed = value.trim()
  if (!trimmed) return false
  const probe = normalizeForProtocolCheck(trimmed)
  if (!probe) return false
  if (probe.startsWith('javascript:') || probe.startsWith('data:') || probe.startsWith('vbscript:')) {
    return false
  }
  const scheme = /^([a-z][a-z0-9+.-]*):/.exec(probe)
  if (!scheme) return true // 相对路径 / #锚点 / 查询串
  return ['http', 'https', 'mailto', 'tel'].includes(scheme[1] ?? '')
}

/** img[src] 协议白名单：只允许 http(s):// 或 `/` 开头（含 `//` 协议相对地址） */
function isAllowedImageSrc(value: string): boolean {
  const trimmed = value.trim()
  if (!trimmed) return false
  const probe = normalizeForProtocolCheck(trimmed)
  if (!probe) return false
  if (probe.startsWith('data:') || probe.startsWith('javascript:') || probe.startsWith('vbscript:')) {
    return false
  }
  if (probe.startsWith('/')) return true
  return /^https?:\/\//.test(probe)
}

/** 判断是否是绝对外链（http/https 或协议相对 //） */
function isExternalHref(value: string): boolean {
  const probe = normalizeForProtocolCheck(value)
  return /^https?:\/\//.test(probe) || probe.startsWith('//')
}

function isNumericAttr(value: string): boolean {
  return /^\d{1,4}$/.test(value.trim())
}

/** 解析原始属性串（已去掉标签名与结尾的 `/`） */
function parseAttrs(raw: string): Record<string, string> {
  const attrs: Record<string, string> = {}
  if (!raw) return attrs
  const re = new RegExp(ATTR_PATTERN.source, 'g')
  let match: RegExpExecArray | null
  while ((match = re.exec(raw)) !== null) {
    const name = (match[1] ?? '').toLowerCase()
    if (!name || name in attrs) continue
    attrs[name] = match[2] ?? match[3] ?? match[4] ?? ''
    // 防御性：零宽匹配会让 lastIndex 停住，强行前进一格
    if (re.lastIndex === match.index) re.lastIndex = match.index + 1
  }
  return attrs
}

/**
 * 从 `start`（指向 `<`）解析一个标签。
 * 属性值里的 `>` 不算标签结束（需要跟踪引号），否则 `<a title="a>b">` 会被截断。
 */
function parseTag(html: string, start: number): ParsedTag | null {
  let i = start + 1
  const closing = html[i] === '/'
  if (closing) i += 1

  const nameStart = i
  while (i < html.length && /[a-zA-Z0-9:_-]/.test(html[i] as string)) i += 1
  if (i === nameStart) return null
  const name = html.slice(nameStart, i).toLowerCase()

  const attrStart = i
  let quote: string | null = null
  while (i < html.length) {
    const ch = html[i] as string
    if (quote) {
      if (ch === quote) quote = null
      i += 1
      continue
    }
    if (ch === '"' || ch === "'") {
      quote = ch
      i += 1
      continue
    }
    if (ch === '>') {
      let attrsRaw = html.slice(attrStart, i)
      let selfClosing = false
      if (/\/\s*$/.test(attrsRaw)) {
        selfClosing = true
        attrsRaw = attrsRaw.replace(/\/\s*$/, '')
      }
      return { closing, name, attrsRaw, selfClosing, end: i + 1 }
    }
    i += 1
  }
  return null
}

/** 跳过某个容器标签的内容，返回结束标签之后的下标 */
function skipToClosingTag(html: string, from: number, tagName: string): number {
  const pattern = new RegExp(`</\\s*${tagName}\\s*>`, 'i')
  const rest = html.slice(from)
  const match = pattern.exec(rest)
  if (!match) return html.length
  return from + match.index + match[0].length
}

/** 渲染一个通过白名单的起始标签；返回 null 表示该标签整体不输出 */
function renderOpenTag(tag: ParsedTag): string | null {
  const name = tag.name
  if (!ALLOWED_TAGS.has(name)) return null
  if (name === 'br' || name === 'hr') return `<${name}>`

  const attrs = parseAttrs(tag.attrsRaw)

  if (name === 'img') {
    const src = (attrs.src ?? '').trim()
    // 没有合法 src 的 img 直接丢弃：留着只会产生碎图或空请求
    if (!src || !isAllowedImageSrc(src)) return null
    const parts = [`src="${escapeAttr(src)}"`]
    const alt = attrs.alt
    if (typeof alt === 'string' && alt.length) parts.push(`alt="${escapeAttr(alt)}"`)
    if (isNumericAttr(attrs.width ?? '')) parts.push(`width="${escapeAttr(attrs.width as string)}"`)
    if (isNumericAttr(attrs.height ?? '')) parts.push(`height="${escapeAttr(attrs.height as string)}"`)
    return `<img ${parts.join(' ')}>`
  }

  if (name === 'a') {
    const href = (attrs.href ?? '').trim()
    if (!href || !isAllowedHref(href)) return '<a>'
    const parts = [`href="${escapeAttr(href)}"`]
    const title = attrs.title
    if (typeof title === 'string' && title.length) parts.push(`title="${escapeAttr(title)}"`)
    // 契约：a 强制 rel；外链强制 target="_blank"
    parts.push('rel="noopener noreferrer"')
    if (isExternalHref(href) || (attrs.target ?? '').trim().toLowerCase() === '_blank') {
      parts.push('target="_blank"')
    }
    return `<a ${parts.join(' ')}>`
  }

  if (name === 'td' || name === 'th') {
    const parts: string[] = []
    if (isNumericAttr(attrs.colspan ?? '')) parts.push(`colspan="${escapeAttr(attrs.colspan as string)}"`)
    if (isNumericAttr(attrs.rowspan ?? '')) parts.push(`rowspan="${escapeAttr(attrs.rowspan as string)}"`)
    return parts.length ? `<${name} ${parts.join(' ')}>` : `<${name}>`
  }

  // 其余允许标签：不允许任何属性（style/class/id/on* 全部丢弃）
  return `<${name}>`
}

/**
 * 清洗富文本 HTML。
 *
 * 纯函数、无 DOM 依赖：同一份实现用于 SSR 与客户端，也便于 Node 自测。
 * 输入不是字符串（null / undefined / 对象）时返回空串，调用方按"无内容"处理。
 */
export function sanitizeHtml(input: unknown): string {
  if (typeof input !== 'string') return ''
  const html = input.trim()
  if (!html) return ''

  const output: string[] = []
  let i = 0

  while (i < html.length) {
    const lt = html.indexOf('<', i)
    if (lt < 0) {
      output.push(escapeText(html.slice(i)))
      break
    }
    if (lt > i) output.push(escapeText(html.slice(i, lt)))
    i = lt

    // 注释 / 条件注释：整段丢弃
    if (html.startsWith('<!--', i)) {
      const end = html.indexOf('-->', i + 4)
      i = end < 0 ? html.length : end + 3
      continue
    }
    // doctype / CDATA / 其它 <! 声明：整段丢弃
    if (html.startsWith('<!', i)) {
      const end = html.indexOf('>', i + 2)
      i = end < 0 ? html.length : end + 1
      continue
    }

    const tag = parseTag(html, i)
    if (!tag) {
      // 不是合法标签起始（例如正文里的孤立的 `<`）：按文本转义输出
      output.push('&lt;')
      i += 1
      continue
    }

    if (DROP_TAG_WITH_CONTENT.has(tag.name)) {
      // 危险容器：连内容一起丢弃；空元素 / 自闭合 / 结束标签只跳过标签本身
      if (!tag.closing && !tag.selfClosing && !DROP_TAG_VOID.has(tag.name)) {
        i = skipToClosingTag(html, tag.end, tag.name)
      } else {
        i = tag.end
      }
      continue
    }

    if (tag.closing) {
      if (ALLOWED_TAGS.has(tag.name) && !VOID_TAGS.has(tag.name)) {
        output.push(`</${tag.name}>`)
      }
      i = tag.end
      continue
    }

    const rendered = renderOpenTag(tag)
    if (rendered) output.push(rendered)
    i = tag.end
  }

  return output.join('')
}

/**
 * 去掉 HTML 标签后的纯文本（用于"去标签后为空"这类空内容判断）。
 * 先走 sanitizeHtml，再剥标签：这样 script/style 的**内容**也已经被丢掉，
 * 不会出现"看着是空、其实拿来判断的文本里混着脚本源码"的情况。
 */
export function stripHtml(input: unknown): string {
  const safe = sanitizeHtml(input)
  if (!safe) return ''
  return safe
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/\s+/g, ' ')
    .trim()
}
