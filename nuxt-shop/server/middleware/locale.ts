// server/middleware/locale.ts
export default defineEventHandler((event) => {
    const cookieLocale = getCookie(event, 'i18n_redirected')
    if (cookieLocale) {
        event.context.locale = cookieLocale
        console.log("当前请求语言cookie")
        console.log(cookieLocale)
    }
})