// https://nuxt.com/docs/api/configuration/nuxt-config
// nuxt.config.ts
import tailwindcss from '@tailwindcss/vite'
// 语言清单的唯一来源：i18n.locales 由它生成，不再与 types/shop-product.ts 手工同步。
// 该文件零依赖，可以在配置加载阶段（jiti）安全 import。
import { STORE_DEFAULT_LANG, STORE_LOCALES } from './types/store-locale'

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',

  vite: {
    server: {
      allowedHosts: [
        // ⚠️ 这个域名是 CikPier frp 隧道（nuxt.frp.cikian.cn → 本机 3000）的入口。
        //
        // 但请注意：**dev server 经隧道访问时，客户端 JS 一定不会执行**。
        // Vite 开发模式把客户端入口/依赖暴露成带绝对文件路径的 URL，例如
        //   /_nuxt/E:/CProject/ck-shop/nuxt-shop/node_modules/nuxt/dist/app/entry.async.js
        // 而隧道前置的 nginx 会把任何含 `node_modules`（以及 `.nuxt` 这类点目录）
        // 的请求直接 404 掉 —— 结果：页面只剩 SSR 出来的 HTML，
        //   · <a> 链接（商品卡片、导航）能点（浏览器原生跳转）；
        //   · 所有 @click 按钮全部没反应（Vue 从未水合）。
        // 这正是「PC 端（localhost:3000）正常、手机端只有商品卡片能点」的根因。
        //
        // ✅ 手机/外网访问请用生产构建（资源是 /_nuxt/<hash>.js，不含 node_modules）：
        //      npm run build
        //      $env:PORT=3001; npm start        # 再把隧道 localPort 指到 3001
        // 详见 docs/店铺端移动端点击失效-根因与修复.md
        'nuxt.frp.cikian.cn'
      ],
    },
    plugins: [tailwindcss()],
  },

  css: ['~/assets/css/tailwind.css'],

  modules: ['@nuxtjs/i18n', '@nuxtjs/seo', '@nuxt/ui', '@nuxt/image'],
  i18n: {
    strategy: 'prefix', // 硬性要求:所有语言(包括默认语言)都带前缀,如 /en/, /zh/
    defaultLocale: STORE_DEFAULT_LANG,
    // 由 types/store-locale.ts 的 STORE_LOCALES 派生（唯一的语言清单来源）
    locales: STORE_LOCALES.map(locale => ({ ...locale })),
    langDir: 'locales/',
    lazy: true, // 按需加载语言包,减小首屏体积
    detectBrowserLanguage: {
      useCookie: true,
      cookieKey: 'i18n_redirected',
      redirectOn: 'root', // 只在根路径做自动跳转,避免用户手动切到别的语言后被强制重定向
    },
  },

  // ===== SEO 基础信息 =====
  site: {
    url: 'http://localhost:3000',
    name: 'Nuxt Shop Demo',
    description: 'A demo e-commerce storefront built with Nuxt 4',
    defaultLocale: 'en',
  },

  runtimeConfig: {
    apiBaseUrl: process.env.API_BASE_URL || 'http://127.0.0.1:9951/api',

    /**
     * 当前站点的店铺编码。
     *
     * 一个管理端、多个前台：每个前台站点在**服务端**配置自己的店铺编码，
     * BFF 调后端时通过 X-Store-Code 请求头带上它。这样：
     *   1) 商品链接分享到社交平台时**不带任何店铺参数**（域名本身就是店铺标识，链接更短）；
     *   2) 浏览器拿不到也改不了这个值，不存在前端传参被篡改的问题；
     *   3) 后端按编码过滤，A 店的商品不可能出现在 B 店。
     * 未配置时后端会按域名兜底，最后回退到默认店铺（仅便于本地开发）。
     */
    storeCode: process.env.STORE_CODE || '',

    aesKey: process.env.AES_KEY || '1234567890adbcde',
    aesIv: process.env.AES_IV || '1234567890hjlkew',

    signSecret: process.env.SIGN_SECRET || 'dd05f1c54d63749eda95f9fa6d49v442a',

    public: {
    },
  },

  fonts: {
    // 方案 A：把服务商切换为国内可正常访问的 bunny 源（推荐）
    provider: 'bunny',

    // 方案 B：如果不需要任何第三方字体，可以直接把 google 供应源关掉
    // providers: {
    //   google: false
    // }

    families: [
      {
        name: 'DM Sans',
        weights: [400, 500, 600, 700],
        styles: ['normal', 'italic'],
      }
    ]
  },

  eslint: {
    // 关键：开发和构建时不进行强制语法检查，只把错误留给编辑器显示
    checker: false
  },

  devtools: { enabled: false }
})