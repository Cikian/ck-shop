// app/utils/scrollScheduler.ts
//
// 全站共享的滚动调度器（清单 S23）。
//
// 背景：商品详情页有两个地方各自 `window.addEventListener('scroll')` + rAF：
//   · composables/useStickyAside.ts —— 动作条帘子的显示状态；
//   · components/product/DetailSections.vue —— 锚点高亮。
// 两套监听 + 两套 rAF 意味着**同一个滚动事件里要做两遍布局读取**
// （getBoundingClientRect 会强制同步重排），真机惯性滚动时明显掉帧。
//
// 现在：一个 passive 监听、一次 rAF，把所有注册的任务在同一帧里跑完。
// 布局读取仍然存在，但只读一轮，浏览器只需重排一次。
//
// 用法：
//   const stop = registerScrollTask(() => { ... })
//   onBeforeUnmount(stop)

type ScrollTask = () => void

const tasks = new Set<ScrollTask>()
let rafId = 0
let listening = false

function flush() {
  rafId = 0
  // 复制一份：任务里可能会注销自己（例如组件卸载）
  for (const task of Array.from(tasks)) {
    try {
      task()
    } catch {
      // 单个任务抛错不应该影响其它任务（否则一个 bug 会让整页滚动逻辑失效）
    }
  }
}

function onScroll() {
  if (rafId) return
  rafId = requestAnimationFrame(flush)
}

/**
 * 注册一个"滚动时执行"的任务，返回注销函数。
 * 同一个任务重复注册会被去重（Set 语义）。
 */
export function registerScrollTask(task: ScrollTask): () => void {
  if (!import.meta.client) return () => {}

  tasks.add(task)
  if (!listening) {
    window.addEventListener('scroll', onScroll, { passive: true })
    listening = true
  }

  return () => {
    tasks.delete(task)
    if (tasks.size === 0 && listening) {
      window.removeEventListener('scroll', onScroll)
      listening = false
      if (rafId) {
        cancelAnimationFrame(rafId)
        rafId = 0
      }
    }
  }
}
