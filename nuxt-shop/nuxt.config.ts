// https://nuxt.com/docs/api/configuration/nuxt-config
// nuxt.config.ts
import tailwindcss from '@tailwindcss/vite'

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',

  vite: {
    plugins: [tailwindcss()],
  },

  css: ['~/assets/css/tailwind.css'],

  modules: ['@nuxtjs/i18n', '@nuxtjs/seo', '@nuxt/ui', '@nuxt/image'],
  i18n: {
    strategy: 'prefix', // 硬性要求:所有语言(包括默认语言)都带前缀,如 /en/, /zh/
    defaultLocale: 'en',
    locales: [
      { code: 'en', language: 'en-US', name: 'English', file: 'en.json', icon: '🇺️🇸️' },
      { code: 'zh', language: 'zh-CN', name: '中文', file: 'zh.json', icon: '🇨️🇳️' },
      { code: 'fr', language: 'fr-FR', name: 'Français', file: 'fr.json', icon: '🇫️🇷️' }
    ],
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