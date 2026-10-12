// server/middleware/locale.ts
//
// 把「这次请求是什么语言」收敛到 event.context.locale，供 server/api/* 在
// 没有显式 ?lang= 时兜底（createApiClient 也会用 event.context.locale 决定 x-language 头）。
//
// 两个来源，按可信度排序：
//   1) URL 前缀：i18n strategy='prefix'，所有语言都带前缀（/zh/products、/en/products），
//      所以 /zh/... 这种请求的语言是最确定的。
//   2) i18n_redirected cookie：@nuxtjs/i18n 的浏览器语言探测 / 用户切换语言后写入，
//      只在请求路径没有语言前缀时才用它兜底（例如 /api/*、/ 根路径）。
// 认不出的值一律不写 event.context.locale，交给下游的 resolveStoreLang 回退默认语言。

import { STORE_SUPPORTED_LANGS } from '~~/types/store-locale'

export default defineEventHandler((event) => {
    // 取 pathname 的第一段，例如 /zh/products -> 'zh'、/api/products -> 'api'
    const firstSegment = getRequestURL(event).pathname.split('/').filter(Boolean)[0]?.toLowerCase()

    if (firstSegment && STORE_SUPPORTED_LANGS.includes(firstSegment)) {
        event.context.locale = firstSegment
        return
    }

    const cookieLocale = getCookie(event, 'i18n_redirected')
    if (cookieLocale) {
        event.context.locale = cookieLocale
    }
})
