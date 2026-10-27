// server/api/auth/avatar.post.ts
// 前端请求：POST /api/auth/avatar  { avatar: '/avatars/preset-3.webp' | '' }
// 对应转发到 Java 后端：POST {baseURL}/shop/account/profile/avatar
//
// 用途：选择**预设头像**或**恢复默认**（空串）。
//
// 为什么自定义上传不走这里：上传是 multipart，另有
// /api/auth/avatar/upload（BFF 先用 sharp 裁成 512×512 webp 再转发）。
//
// 白名单在**后端**（ShopAccountConstant.PRESET_AVATARS）：本路由不做二次判断，
// 只把用户的选择原样交给后端校验 —— 前端提示用的是同一份约定，
// 但真正的门禁必须在服务端，否则改一个请求体就能把任意 URL 写进 sys_user.avatar。

import { resolveStoreLang } from '~~/types/store-locale'
import type { ShopProfile } from './profile.get'

export default defineEventHandler(async (event): Promise<ShopProfile> => {
  const body = await readBody<{ avatar?: unknown }>(event)

  const api = createApiClient(event, {
    withAuth: true,
    lang: resolveStoreLang(getQuery(event).lang, 'en'),
  })

  const result = await api<ShopProfile | null>('/shop/account/profile/avatar', {
    method: 'POST',
    body: { avatar: body?.avatar == null ? '' : String(body.avatar) },
  })

  if (!result) {
    throw createError({ statusCode: 500, statusMessage: '头像保存失败，请稍后重试' })
  }

  return result
})
