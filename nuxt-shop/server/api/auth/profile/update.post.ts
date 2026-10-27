// server/api/auth/profile/update.post.ts
// 前端请求：POST /api/auth/profile/update  { realname, phone }
// 对应转发到 Java 后端：POST {baseURL}/shop/account/profile/update（Header: X-Access-Token）
//
// 为什么需要这个 BFF 路由：
//   旧版「账户设置 - 保存修改」**没有后端接口**，只把内容写进 localStorage 里那份
//   用于显示的缓存（见 components/profile/SettingsPanel.vue 的历史注释）。
//   换设备/清缓存就变回原值 —— 用户看到的是"保存成功但其实没保存"。
//   现在由后端真写库，本路由只做转发与「只允许这两项」的白名单。
//
// 安全边界：
//   · token 从 httpOnly cookie 读（withAuth），前端拿不到也传不了；
//   · 请求体只透传 realname / phone 两个字段，**不转发**任何其它字段
//     （email 是登录标识、username 不可改，后端本来也不收，这里再收一次口子）。

import { resolveStoreLang } from '~~/types/store-locale'
import type { ShopProfile } from '../profile.get'

export default defineEventHandler(async (event): Promise<ShopProfile> => {
  const body = await readBody<{ realname?: unknown; phone?: unknown }>(event)

  const api = createApiClient(event, {
    withAuth: true,
    lang: resolveStoreLang(getQuery(event).lang, 'en'),
  })

  const result = await api<ShopProfile | null>('/shop/account/profile/update', {
    method: 'POST',
    body: {
      realname: body?.realname == null ? '' : String(body.realname),
      phone: body?.phone == null ? '' : String(body.phone),
    },
  })

  if (!result) {
    throw createError({ statusCode: 500, statusMessage: '保存失败，请稍后重试' })
  }

  return result
})
