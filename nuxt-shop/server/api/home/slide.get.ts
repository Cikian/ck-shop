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
  return url.replace(/`/g, '').trim()
}

export default defineEventHandler(async (event): Promise<SlideItem[]> => {
  const api = createApiClient(event, { withAuth: false })

  try {
    // 显式为 api<T> 传入泛型类型 SlideItem[]
    const data = await api<SlideItem[]>('/home/slide', {
      method: 'GET',
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