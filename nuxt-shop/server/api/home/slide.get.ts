// server/api/home/slide.get.ts
// 店铺端首页轮播图（Nuxt BFF）：GET /api/home/slide
// 数据来源：Java 后端 GET /home/slide/store（方法级 @IgnoreAuth，按店铺隔离）
//
// 语言：显式 ?lang= 优先（HomeHeroBanner.vue 会带当前 locale），
//       其次 server/middleware/locale.ts 解析出的 event.context.locale，最后 en。

import { resolveStoreLang } from '~~/types/store-locale'

// 定义 Java 后端返回的轮播图项类型
export interface SlideItem {
  goods: string | null
  _id: string
  title: string | null
  pic_url: string
  targetUrl?: string | null
}

function cleanUrl(url: string | null | undefined): string {
  if (!url) return ''
  // 清理 URL 中可能存在的反引号、前后空格
  const value = url.replace(/`/g, '').trim()

  // ★ scheme 白名单。这个字段前端是直接绑到 `:href` / `<img src>` 上的
  //   （HomeHeroCarousel.vue），轮播图又是**后台配置**的内容：
  //   一旦填了 `javascript:alert(1)` 之类的 scheme，用户点一下就会在
  //   本站域名下执行脚本（存储型 XSS）。只放行 http(s) 与站内单斜杠相对路径。
  if (/^https?:\/\//i.test(value)) return value
  if (value.startsWith('/') && !value.startsWith('//')) return value
  return ''
}

export default defineEventHandler(async (event): Promise<SlideItem[]> => {
  const query = getQuery(event)
  const contextLocale = (event.context as Record<string, unknown>).locale

  // 与 server/api/products.get.ts 一致：显式 ?lang= 优先，其次中间件解析出的语言，最后 en。
  // 前端 HomeHeroBanner.vue 会带着当前 locale 请求，语言切换必须真的打到后端。
  const lang = resolveStoreLang(query.lang ?? contextLocale, 'en')

  const api = createApiClient(event, { withAuth: false })

  try {
    // 后端路径带 /store 后缀：/home/slide 是管理端写接口（需登录），
    // 店铺端免登录读走 /home/slide/store（方法级 @IgnoreAuth）。
    const data = await api<SlideItem[]>('/home/slide/store', {
      method: 'GET',
      query: { lang },
    })

    // 清理 URL 字段
    return (data || []).map(item => ({
      ...item,
      pic_url: cleanUrl(item.pic_url),
      targetUrl: cleanUrl(item.targetUrl),
    }))
  } catch (error: any) {
    throw createError({
      statusCode: error.statusCode || 500,
      statusMessage: error.statusMessage || '获取首页轮播图失败',
    })
  }
})