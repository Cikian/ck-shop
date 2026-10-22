// composables/useStickyAside.ts
//
// 商品详情页「右侧信息栏吸顶 + 底部动作条对齐 + 图集高度」的浏览器测量逻辑。
//
// 口径来源：docs/商品页布局与底部动作条-设计说明.md 2.2 / 2.3 / 八 / 九
//   barTop  = innerHeight - 32 - barH            // 动作条（浮层底）顶边，桌面离底 32px
//   limit   = barTop - 16                        // 面板底边最多到这里
//   natural = 面板未吸顶时的自然位置（文档坐标 = scrollY 0 时的视口坐标，见 panelStaticTop）
//   panelH  = 面板内容高度
//   top     = natural + panelH <= limit ? natural : limit - panelH
//
// 第 2 轮新增：
//   · `--gallery-main-height`：图集主图高度 = 视口高 - 32 - 图集内容顶部（等价 100dvh-header-56），
//     保证主图底边与桌面动作条底边同在 viewport-32px；首帧由 Gallery 的 CSS 回退兜底；
//   · 动作条释放：`data-sticky-zone` 结束（= 推荐区顶部）后，动作条随页面向上滚动，不再固定；
//     回滚到 zone 内自动恢复固定。第 5 轮起**完全交给 CSS sticky**（见 StickyActionBar 文件头），
//     JS 不再逐帧写 transform —— 那样在真机惯性滚动下会慢半拍，表现为"抖一下 / 漂移"。
//
// 为什么放在 composable 而不是组件里：
//   1) 页面同时要改多处样式（吸顶 top、右列留白、动作条 left/width/平移、图集高度），
//      它们必须来自同一份测量，否则 resize 时会出现"条和列错位"；
//   2) ResizeObserver / resize / 语言切换 / 滚动（仅动作条释放）的时序只有一处，好维护也好清理。
//
// 关键约束：
//   · 面板 sticky 的 top 只在 **resize / ResizeObserver / 语言切换** 时重算；滚动时不重算；
//   · 动作条释放由 CSS sticky 负责；scroll 回调只剩一件廉价的事：切换帘子的 visibility
//     （布尔量，只在"钉住 ↔ 释放"翻转时写一次样式，滚动过程中不写）；
//   · 调用方负责 `position: sticky`（桌面）本身，本 composable 只给 `top`。

import type { Ref } from 'vue'
import { registerScrollTask } from '~~/app/utils/scrollScheduler'

export interface UseStickyAsideOptions {
  /** 右列外层（grid 子项，高度随整行）→ 用来量动作条的 left/width */
  asideRef: Ref<HTMLElement | null>
  /** 右列内部 sticky 面板 → 用来量内容高度 */
  panelRef: Ref<HTMLElement | null>
  /** 左列图集外层（含顶部间距）→ 用来算主图高度，保证底边与动作条同线 */
  galleryRef?: Ref<HTMLElement | null>
  /** sticky zone（两栏 grid + 动作条）→ 动作条滚到 zone 底部后释放 */
  zoneRef?: Ref<HTMLElement | null>
}

export interface UseStickyAsideReturn {
  /** 吸顶面板的内联样式（top） */
  panelStyle: Ref<Record<string, string>>
  /** 右列外层的附加样式（桌面：为动作条预留底部空间） */
  asideStyle: Ref<Record<string, string>>
  /** 页面根的附加样式（桌面：--gallery-main-height；移动端留白走 body） */
  pageStyle: Ref<Record<string, string>>
  /** 动作条的内联样式（只剩帘子可见性变量 `--action-mask-visibility`） */
  barStyle: Ref<Record<string, string>>
  /** 手动重算（语言切换等场景） */
  recompute: () => void
}

/** 桌面断点：与 Tailwind 的 lg 一致（1024px） */
const DESKTOP_MEDIA = '(min-width: 1024px)'
/** 面板与 header 之间的呼吸间距（面板底边与动作条顶边之间也用它） */
const GAP = 16
/**
 * 桌面动作条离底部距离：主图底边也落在 viewport-32px（设计说明 9.1）。
 * ⚠️ 必须与 StickyActionBar 的 `lg:bottom-8`（32px）一致，否则主图底边与浮层底错位。
 * （移动端的对应值是"安全区 + 8px"，由组件 CSS 决定，动作条释放公式不依赖这个常量。）
 */
const BAR_BOTTOM_OFFSET = 32
/**
 * 动作条释放后与 zone（两栏 grid = 商品主信息区）底部之间保留的视觉间距。
 * （第 5 轮：释放改由 CSS sticky 完成，"留白"由 zone 尾部自然产生，本常量不再参与计算。)
 */

export function useStickyAside(options: UseStickyAsideOptions): UseStickyAsideReturn {
  const { locale } = useI18n()

  const panelStyle = ref<Record<string, string>>({ top: 'var(--nav-total-height)' })
  const asideStyle = ref<Record<string, string>>({})
  const pageStyle = ref<Record<string, string>>({})
  /*
   * 动作条的水平/纵向定位全部由组件 CSS（sticky + 栅格 + 绝对定位的 bottom 偏移）负责，
   * JS 只在这里写一个变量：`--action-mask-visibility` —— 帘子（浮层底下方那块不透明奶油底）
   * 在动作条钉在视口底部时可读，释放后隐藏。SSR 首帧默认 visible，与初始"钉住"状态一致。
   */
  const barStyle = ref<Record<string, string>>({ '--action-mask-visibility': 'visible' })

  let rafId = 0
  let scrollRafId = 0
/** 共享滚动调度器的注销函数（清单 S23） */
let stopScrollTask: (() => void) | null = null
  let mounted = false
  const observers: ResizeObserver[] = []

  function headerHeight(): number {
    const header = document.querySelector('header')
    return header instanceof HTMLElement ? header.getBoundingClientRect().height : 0
  }

  /**
   * 量高要用**浮层底**（`[data-sticky-actions-row]`），不是动作条的 sticky 根节点：
   * 第 5 轮起根节点是 `sticky h-0`（高度 0，不占文档流），它的 offsetHeight 恒为 0，
   * 拿它算"动作条顶边/可用高度"会得出完全错误的数。用属性找元素，避免与组件引用耦合。
   */
  function actionBarElement(): HTMLElement | null {
    return document.querySelector<HTMLElement>('[data-sticky-actions-row]')
      ?? document.querySelector<HTMLElement>('[data-sticky-actions]')
  }

  function isDesktop(): boolean {
    return window.matchMedia(DESKTOP_MEDIA).matches
  }

  /**
   * 移动端为固定动作条预留滚动空间。
   *
   * 为什么动 body：页面根节点在 <main> 内，页脚是它的兄弟节点；只有给 body 加 padding-bottom，
   * 留白才会出现在页脚**之后**，滚到页底时页脚不会被固定动作条盖住。
   * 桌面端 / 卸载时清空，避免影响其它页面。
   */
  function setBodyBottomSpace(value: string | null) {
    if (!import.meta.client) return
    if (value) document.body.style.paddingBottom = value
    else document.body.style.removeProperty('padding-bottom')
  }

  /**
   * 图集主图高度：让主图底边落在 viewport-32px（= 桌面动作条浮层底的底边）。
   * 用「视口高 - 32 - 图集内容顶部（文档坐标）」而不是减 header：页面 pt-navbar 用的是设计 token，
   * 与实测 header 可能有 1~2px 差，直接量图集自身顶部才能保证 ±2px 的底边对齐。
   */
  function galleryMainHeight(): number {
    const viewportH = window.innerHeight
    const fallback = Math.max(320, viewportH - headerHeight() - 56)
    const gallery = options.galleryRef?.value
    if (!gallery) return fallback
    const rect = gallery.getBoundingClientRect()
    const paddingTop = Number.parseFloat(getComputedStyle(gallery).paddingTop) || 0
    // rect.top + scrollY = 元素在文档中的绝对顶部（与当前滚动位置无关）
    const contentTop = rect.top + window.scrollY + paddingTop
    return Math.max(320, Math.round(viewportH - BAR_BOTTOM_OFFSET - contentTop))
  }

  /**
   * 面板「未吸顶时」相对**文档顶部**的位置，也就是它静止时所在的位置。
   *
   * 为什么需要它：sticky 一旦吸住，getBoundingClientRect().top 就等于 top 值，
   * 量不出"自然位置"。而吸顶 top 只要取自然位置，"内容不高"时面板从第一个滚动像素起
   * 就钉住（位移恒为 0），不会出现"往下滚一点、右侧先往上挪几像素"的抖动。
   *
   * 怎么量（三条都必须满足，否则"滚动后重算"会算错）：
   *   ① 必须是**文档坐标**：getBoundingClientRect() 是视口坐标，要加 window.scrollY。
   *      少了它，页面已经滚动过一段时触发重算（resize / 语言切换 / 内容变化）会把 top
   *      算成「自然位置 − 当前滚动量」，面板会突然上跳一大截。
   *   ② **不能用 panel.offsetTop**：sticky 元素被顶走之后，offsetTop 反映的是它**当前**
   *      的位置（含吸顶位移）—— 实测滚动 300px 时它从 24 变成 396，直接算会把自然位置
   *      抬高 372px。所以改用「offsetParent 的文档顶部 + 上边框 + 上内边距」表示面板作为
   *      **首个子元素**时的静态位置（本页 panel 就是 aside 的唯一子元素）。
   *   ③ **也不能用 panel 的实时矩形做修正**：吸顶状态下 rect.top 恒定 → rect.top + scrollY
   *      会随滚动不断变大（不是文档坐标）。所以这里只认 ②。
   */
  function panelStaticTop(headerH: number): number {
    const fallback = headerH + GAP
    const panel = options.panelRef.value
    if (!panel) return fallback
    const parent = panel.offsetParent
    if (!(parent instanceof HTMLElement)) return fallback
    const parentStyle = getComputedStyle(parent)
    const borderTop = Number.parseFloat(parentStyle.borderTopWidth) || 0
    const paddingTop = Number.parseFloat(parentStyle.paddingTop) || 0
    const cssTop = parent.getBoundingClientRect().top + window.scrollY + borderTop + paddingTop
    // 兜底：首帧尚未布局（量到 0）或量到了 header 上方时，退回"header 下方 GAP"
    return cssTop > headerH ? cssTop : fallback
  }

  /**
   * 动作条帘子（浮层底与视口底之间那块不透明奶油底）的显示状态。
   *
   * 「动作条随页面上滚」本身不再由 JS 做（第 5 轮改成 CSS sticky，见 StickyActionBar 文件头）：
   * 曾经用 `fixed + translateY`，需要每帧跟 scroll 改 transform，真机惯性滚动时永远慢半拍，
   * 表现为「抖一下 / 轻微漂移」。现在浏览器负责定位，JS 只剩下这个"帘子要不要露出来"的开关。
   *
   * 判定：帘子被设计成正好覆盖「浮层底底边 → 视口底」这段空隙，所以
   *   · 动作条钉在视口底部时，帘子的下沿 = 视口底 → 露出来（挡住下方漏出的内容）；
   *   · 滚到 zone 底部、动作条随内容上滚后，帘子下沿跑到视口底上方 → 隐藏
   *     （否则那块奶油色会悬在内容中间，把文字切掉一截）。
   * 用 visibility 而不是 opacity：hidden 的元素既不绘制、也不吃点击。
   * 这个布尔量只在"钉住 ↔ 释放"切换时翻转一次，滚动过程中不写样式，因此不会有抖动。
   */
  function applyMaskState() {
    scrollRafId = 0
    if (!import.meta.client) return
    const mask = document.querySelector<HTMLElement>('[data-sticky-actions-mask]')
    if (!mask) return
    const pinned = mask.getBoundingClientRect().bottom >= window.innerHeight - 1
    const visibility = pinned ? 'visible' : 'hidden'
    if (barStyle.value['--action-mask-visibility'] !== visibility) {
      barStyle.value = { '--action-mask-visibility': visibility }
    }
  }

  function scheduleMaskState() {
    if (!import.meta.client) return
    if (scrollRafId) return
    scrollRafId = requestAnimationFrame(applyMaskState)
  }

  /** 真正做测量的地方：一次性算完所有尺寸，避免分别测量导致不一致 */
  function applyLayout() {
    rafId = 0
    if (!import.meta.client) return

    const headerH = headerHeight()
    const barEl = actionBarElement()
    const barH = barEl ? barEl.offsetHeight : 0
    const desktop = isDesktop()

    if (!desktop) {
      /*
       * 移动端：单列流动，不吸顶。
       * 动作条本身由 CSS sticky 负责"滚到推荐上方后随内容上滚"（第 5 轮），
       * 所以这里不再需要给 body 预留底部空间 —— 它不会永久压住页脚。
       */
      panelStyle.value = { top: '0px' }
      asideStyle.value = {}
      pageStyle.value = {}
      setBodyBottomSpace(null)
      applyMaskState()
      return
    }

    // 桌面端：右列 sticky + 动作条对齐右列（body 上不需要留白）
    setBodyBottomSpace(null)
    const panel = options.panelRef.value
    const panelH = panel ? panel.getBoundingClientRect().height : 0
    // 可用高度以「浮层底顶边」为下限（桌面离底 32px；barH 量的就是浮层底本身）
    const barTop = window.innerHeight - BAR_BOTTOM_OFFSET - barH
    // 面板底边最多到动作条顶边上方 GAP
    const panelBottomLimit = barTop - GAP
    /*
     * 吸顶 top：
     *   · 内容不高 → **直接取面板的自然位置**（不写 headerH + GAP）。
     *     原因：CSS 里右列的内边距（aside lg:pt-6）已经把面板放在 header 下方约 23px，
     *     而 headerH + GAP = 117 + 16 = 133 比自然位置（140）高 7px；
     *     用 133 当 top 时，页面只要往下滚 7px，面板就先被 sticky 往上顶 7px 再钉住
     *     —— 用户看到的就是"右侧也向上挪了一点点、抖一下"。
     *     取自然位置后，第一个滚动像素起就钉住，位移恒为 0；与 header 的间距由 CSS 决定。
     *   · 内容超长 → 先随页面滚动，滚到面板底边对齐动作条上方后再钉住（与上一版完全一致）。
     */
    const naturalTop = panelStaticTop(headerH)
    const top = naturalTop + panelH <= panelBottomLimit
      ? naturalTop
      : panelBottomLimit - panelH
    panelStyle.value = { top: `${Math.round(top)}px` }
    // 右列底部预留动作条占用高度：否则超长面板滚到 zone 尾时，最后一段会被 fixed 动作条盖住
    asideStyle.value = { paddingBottom: `${Math.round(barH + BAR_BOTTOM_OFFSET + 24)}px` }
    // 图集高度：主图底边与动作条浮层底底边同线（viewport-32）
    pageStyle.value = { '--gallery-main-height': `${galleryMainHeight()}px` }

    // 动作条水平/纵向位置都由 StickyActionBar 自身的 CSS（sticky + 栅格）决定，这里只同步帘子状态
    applyMaskState()
  }

  /** requestAnimationFrame 合并：resize 高频触发也只算一次 */
  function schedule() {
    if (!import.meta.client) return
    if (rafId) return
    rafId = requestAnimationFrame(applyLayout)
  }

  /**
   * 内容高度变化（规格切换、图片加载、语言切换）与 header 高度变化都要重新观察/测量。
   * 重新 observe 是幂等的：先断开旧的，避免 ResizeObserver 越积越多。
   */
  function observeTargets() {
    while (observers.length) observers.pop()?.disconnect()
    if (!import.meta.client || typeof ResizeObserver === 'undefined') return

    const targets = [
      options.panelRef.value,
      actionBarElement(),
      document.querySelector('header'),
      options.zoneRef?.value,
    ]
    for (const target of targets) {
      if (!(target instanceof HTMLElement)) continue
      const observer = new ResizeObserver(schedule)
      observer.observe(target)
      observers.push(observer)
    }
  }

  onMounted(async () => {
    mounted = true
    // 等 layout/header 真正挂载后再量，否则首帧 headerH 可能是 0
    await nextTick()
    observeTargets()
    applyLayout()
    window.addEventListener('resize', schedule)
    /*
     * 滚动改用全站共享调度器（清单 S23）：
     * 以前这里和 DetailSections 各自 addEventListener('scroll') + 各自 rAF，
     * 同一次滚动要做两轮布局读取。现在一次监听、一帧内跑完所有任务。
     */
    stopScrollTask = registerScrollTask(applyMaskState)
  })

  // 语言切换后文案长度变化 → 面板高度变化；规格切换由 ResizeObserver 兜住
  watch(locale, () => {
    if (!mounted) return
    nextTick(() => {
      observeTargets()
      schedule()
    })
  })

  // 面板/右列节点替换（数据从 pending 变 ready）时重新观察
  watch(options.panelRef, () => {
    if (!mounted) return
    nextTick(() => {
      observeTargets()
      schedule()
    })
  })

  onBeforeUnmount(() => {
    if (!import.meta.client) return
    window.removeEventListener('resize', schedule)
    stopScrollTask?.()
    if (rafId) cancelAnimationFrame(rafId)
    if (scrollRafId) cancelAnimationFrame(scrollRafId)
    while (observers.length) observers.pop()?.disconnect()
    // 离开商品页时清掉为移动端动作条预留的 body 留白
    setBodyBottomSpace(null)
  })

  return {
    panelStyle,
    asideStyle,
    pageStyle,
    barStyle,
    recompute: applyLayout,
  }
}
