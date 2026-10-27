// server/api/auth/avatar/upload.post.ts
// 前端请求：POST /api/auth/avatar/upload（multipart/form-data，字段名 file）
// 对应转发到 Java 后端：POST {baseURL}/shop/account/profile/avatar/upload
//
// 这里的 sharp 处理不是"顺手优化"，而是头像功能的正确性要求：
//   · 手机直出的照片动辄 4000×3000 / 5MB，原样上传会让 OSS 里堆满几 MB 的"头像"，
//     首屏/评论/评价抽屉里每张头像都要下载一遍；
//   · 全部统一裁成 **512×512 正方形**：前端头像框是正圆，非正方形图裁成圆形后
//     会把脸/主体切掉一半，与其让用户自己调，不如服务端按中心裁切 + 输出 webp
//     （512 足够 2x 屏 256px 的显示尺寸）。
//
// 尺寸/类型校验分两层：
//   · 这一层：8MB 上限（原图），并用 sharp 解码失败来识别"不是图片"；
//   · 后端：2MB 上限 + 文件头魔数（接口可被直接调用，不能只信任 BFF）。

import sharp from 'sharp'
import { resolveStoreLang } from '~~/types/store-locale'
import type { ShopProfile } from '../profile.get'

/** 原图上限：手机原图一般 2-8MB，给足空间但不要无上限 */
const MAX_UPLOAD_BYTES = 8 * 1024 * 1024
/** 输出头像边长（2x 屏下 256px 的显示尺寸） */
const AVATAR_SIZE = 512

export default defineEventHandler(async (event): Promise<ShopProfile> => {
  const parts = await readMultipartFormData(event)
  const filePart = parts?.find(part => part.name === 'file' && part.data?.length)

  if (!filePart?.data?.length) {
    throw createError({ statusCode: 400, statusMessage: '请选择要上传的图片' })
  }
  if (filePart.data.length > MAX_UPLOAD_BYTES) {
    throw createError({ statusCode: 413, statusMessage: '图片体积过大，请控制在 8MB 以内' })
  }

  let normalized: Buffer
  try {
    normalized = await sharp(filePart.data)
      .rotate() // 按 EXIF 方向摆正，否则竖拍照片会躺倒
      .resize(AVATAR_SIZE, AVATAR_SIZE, { fit: 'cover', position: 'centre' })
      .webp({ quality: 82 })
      .toBuffer()
  } catch {
    // 解不出来 = 不是位图（或文件损坏）。这里吞掉 sharp 的原始报错，
    // 给用户一句能看懂的话，也避免把内部堆栈暴露到响应里。
    throw createError({ statusCode: 415, statusMessage: '无法识别该图片，请换一张 JPG / PNG 图片' })
  }

  const form = new FormData()
  form.append('file', new Blob([new Uint8Array(normalized)], { type: 'image/webp' }), 'avatar.webp')

  const api = createApiClient(event, {
    withAuth: true,
    lang: resolveStoreLang(getQuery(event).lang, 'en'),
    // 上传要等 OSS 回写，10s 的默认写超时偶尔不够
    timeoutMs: 20000,
  })

  const result = await api<ShopProfile | null>('/shop/account/profile/avatar/upload', {
    method: 'POST',
    body: form,
  })

  if (!result) {
    throw createError({ statusCode: 500, statusMessage: '头像上传失败，请稍后重试' })
  }

  return result
})
