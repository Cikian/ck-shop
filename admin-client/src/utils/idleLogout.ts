// 空闲自动登出（清单 B18）
//
// 背景：管理端的 token 存在 localStorage 里、有效期 7 天（JwtUtil.EXPIRE_TIME）。
// 共享电脑上"人走了但页面还开着"是最现实的风险：任何人回来都能直接操作后台，
// 而且 token 已经躺在 localStorage 里，XSS 也能随手取走。
//
// 这里做的是**缩短暴露窗口**：连续 30 分钟没有任何交互就主动登出。
//   · 只监听轻量事件（pointerdown/keydown/wheel/touchstart），passive 不阻塞滚动；
//   · 已登出（没有 token）时不做任何事，不会把登录页反复刷新；
//   · 登出失败也不重试：下一次路由守卫/接口 401 会兜住。
//
// 注意：这不是"token 失效"——服务端 token 仍然有效到过期。真正的解法是缩短
// 服务端 token 有效期（base-core 的 JwtUtil.EXPIRE_TIME，目前与店铺端共用 7 天，
// 改动会影响店铺端）或把管理端改成 BFF + httpOnly cookie，属于后续独立改造。

const DEFAULT_IDLE_TIMEOUT_MS = 30 * 60 * 1000

/** 只监听这几类"确实有人在操作"的事件，避免 mousemove 之类高频事件拖慢主线程 */
const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart'] as const

let started = false

export function setupIdleLogout(timeoutMs: number = DEFAULT_IDLE_TIMEOUT_MS): void {
  if (typeof window === 'undefined' || started) {
    return
  }
  started = true

  let timer: number | undefined

  const doLogout = async () => {
    // 动态导入：避免 main.ts 顶部静态引入 user store 形成循环依赖
    const { useUserStoreWithOut } = await import('/@/store/modules/user')
    const userStore = useUserStoreWithOut()
    if (!userStore.getToken) {
      return
    }
    try {
      // goLogin = true：登出后跳登录页，并把当前地址带在 redirect 上
      await userStore.logout(true);
    } catch {
      // 登出接口失败不影响本地清理（store 内部已清 token）
    }
  }

  const reset = () => {
    if (timer) {
      window.clearTimeout(timer)
    }
    timer = window.setTimeout(() => {
      void doLogout()
    }, timeoutMs)
  }

  for (const eventName of ACTIVITY_EVENTS) {
    window.addEventListener(eventName, reset, { passive: true })
  }
  reset()
}
