// server/middleware/analytics.ts
//
// 用户行为分析（Phase 1）· BFF 无感采集中间件。
//
// 挂在所有同源请求的最外层（文件名排序 analytics < csrf < locale），
// 只做一件事：把 page_view / product_view 事件**塞进内存队列后立即返回**，
// 批量异步 flush 到 Java 由 server/utils/analytics.ts 的队列完成（§5.1）。
//
// 采集口径（§4.1 / §5.1 / §7）：
//   - 仅 GET/HEAD 且 Accept 含 text/html 的页面导航才计一条 page_view；
//     /api/*、/_nuxt/* 静态资源、图片/脚本等一律跳过；
//   - 商品详情路径（/:locale/products/:id，id 即 slug）判定为 product_view；
//   - 来源/UA/语言/店铺/真实 IP/session/visitor 全部在服务端补全。
//
// 性能硬约束：这里**不 await 任何后端调用**，入队 O(1)，绝不阻塞响应。

import { collectPageView, enqueueEvents } from '../utils/analytics'

export default defineEventHandler((event) => {
  const trackEvent = collectPageView(event)
  if (trackEvent) {
    enqueueEvents([trackEvent])
  }
})
