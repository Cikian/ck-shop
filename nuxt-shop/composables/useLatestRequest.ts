// composables/useLatestRequest.ts
//
// 「只接受最后一次请求的结果」的小工具（latest-wins 防竞态）。
//
// 为什么需要它：本项目里有一批**手写**的取数函数（useOrderList / useFavoriteList /
// useCart.fetchCart …），它们直接 `await $fetch` 然后写自己的 ref。
// 用户快速连点分页/切换筛选时会有多个请求同时在途，先发的请求可能后返回，
// 于是"页码显示第 3 页、列表却是第 2 页的数据"。
// 用 useAsyncData 的页面不受影响（它有 key + dedupe），所以只给手写取数补。
//
// 用法：
//   const guard = useLatestRequest()          // 在 composable 内创建，随实例走
//   async function load() {
//     const isLatest = guard.begin()
//     const data = await fetchSomething()
//     if (!isLatest()) return                 // 已经有更新的请求了，丢弃这次结果
//     state.value = data
//   }
//
// ⚠️ 不要在模块顶层（文件作用域）创建 guard：模块在服务端是跨请求共享的，
//    两个并发 SSR 请求会互相把对方的请求判成"过期"，导致其中一个请求少写状态。
//    必须在 composable 函数体内创建（每次调用一份，SSR 下即每个请求一份）。

export interface LatestRequestGuard {
  /**
   * 开始一次新请求。
   * @returns 一个判定函数：返回 true 表示"我仍是最后一次请求"，可以写状态。
   */
  begin: () => () => boolean
  /** 主动作废所有在途请求（例如组件卸载、离开页面时） */
  invalidate: () => void
}

export function useLatestRequest(): LatestRequestGuard {
  let latest = 0

  return {
    begin() {
      const token = ++latest
      return () => token === latest
    },
    invalidate() {
      latest += 1
    },
  }
}
