// server/api/store/info.get.ts
// GET /api/store/info —— 当前店铺的公开信息（品牌名 / Logo / 主题）
//
// 数据来源：Java 后端 GET /shop/store/store-info（匿名接口，按 X-Store-Code 或域名解析店铺）
//
// 为什么需要它：
//   旧版把品牌名写死在前端 i18n 里，值还是脚手架占位符 "Nuxt Shop"；
//   而店铺端又是"一份代码部署多个站点"，品牌只能来自服务端配置/数据库。
//   现在 Header、Footer、首页 SEO 统一读这里。
//
// 降级策略：后端不可用时回退到 STORE_SITE_NAME（站点环境变量），
// 页面至少还能显示自己的站名；**绝不**回退到示例品牌。

interface StoreInfoResponse {
  code: string
  name: string
  logoUrl: string | null
  themeConfig: string | null
}

export default defineEventHandler(async (event): Promise<StoreInfoResponse> => {
  const config = useRuntimeConfig(event)
  const fallbackName = String(config.storeSiteName || '').trim()

  try {
    const api = createApiClient(event, { withAuth: false })
    const res = await api<Partial<StoreInfoResponse>>('/shop/store/store-info', { method: 'GET' })
    return {
      code: String(res?.code || ''),
      name: String(res?.name || '').trim() || fallbackName,
      logoUrl: res?.logoUrl || null,
      themeConfig: res?.themeConfig || null,
    }
  } catch (error: any) {
    // 后端不可用不应让整页 500：品牌信息缺失是可降级的
    console.warn('[store/info] 获取店铺信息失败，使用站点配置兜底：', error?.message || error)
    return { code: '', name: fallbackName, logoUrl: null, themeConfig: null }
  }
})
