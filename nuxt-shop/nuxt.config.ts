// https://nuxt.com/docs/api/configuration/nuxt-config
// nuxt.config.ts
import tailwindcss from '@tailwindcss/vite'
// 语言清单的唯一来源：i18n.locales 由它生成，不再与 types/shop-product.ts 手工同步。
// 该文件零依赖，可以在配置加载阶段（jiti）安全 import。
import { STORE_DEFAULT_LANG, STORE_LOCALES } from './types/store-locale'

/**
 * 密钥**必须**来自环境变量，缺失或仍是示例值时直接报错。
 *
 * 为什么不做兜底：以前这里写的是
 *   signSecret: process.env.SIGN_SECRET || 'dd05f1c5...'
 * 这个默认值就是 JeecgBoot 开源仓库里的公开密钥。一旦某个环境漏配 .env，
 * 服务会**静默**用公开密钥启动 —— 任何人都能伪造 sign/timestamp/version 头，
 * 直接绕过 BFF 与后端之间的信任边界，而且页面上看不出任何异常。
 * 加密密钥（AES_KEY/AES_IV）同理：密码"加密"会退化成可逆的公开变换。
 * 所以这里宁可启动失败，也不要带着公开密钥上线。
 */
const DEMO_SECRET_VALUES = new Set([
  'change-me',
  '1234567890adbcde', // JeecgBoot 开源默认 AES key
  '1234567890hjlkew', // JeecgBoot 开源默认 AES iv
  'dd05f1c54d63749eda95f9fa6d49v442a', // JeecgBoot 开源默认签名密钥
])

// `nuxt prepare` 由 package.json 的 postinstall 触发，此时（例如刚 clone 完还没建 .env）
// 不应该因为缺密钥就让 npm install 失败，否则新人根本装不上依赖。
// 只有 prepare 会被放行；dev / build / start 一律强制校验。
const isPrepareOnly = process.argv.some(arg => arg.includes('prepare'))

/**
 * 显式豁免开关：只允许写在**被 gitignore 的 .env** 里。
 *
 * 现实约束：本项目当前开发环境（nuxt-shop/.env 与 ck-server/.env）用的就是
 * JeecgBoot 的开源默认密钥，而 AES key 在 Java 侧是代码内常量、签名密钥在
 * 后端 .env 里 —— 轮换必须前后端同时改并重启后端，不能由前端单方面完成。
 * 所以这里留一个"知情豁免"口子：默认拒绝，只有在本地 .env 里显式写下
 * ALLOW_INSECURE_DEMO_SECRETS=true 才放行，并且每次启动都打醒目告警。
 * 这样仓库里不再有任何可用的默认密钥，而本地开发环境不会被立刻打断。
 */
const ALLOW_INSECURE_DEMO_SECRETS = (process.env.ALLOW_INSECURE_DEMO_SECRETS || '').toLowerCase() === 'true'

function requireSecret(name: string): string {
  const value = (process.env[name] || '').trim()

  if (!value) {
    if (isPrepareOnly) {
      return `prepare-only-${name.toLowerCase()}`
    }
    throw new Error(
      `[nuxt.config] 环境变量 ${name} 未配置，已拒绝启动。\n`
      + `  该值必须与 Java 后端一致：请复制 .env.example 为 .env 后填写（见 README「环境变量」一节）。`,
    )
  }

  if (DEMO_SECRET_VALUES.has(value)) {
    if (isPrepareOnly) {
      return value
    }
    if (!ALLOW_INSECURE_DEMO_SECRETS) {
      throw new Error(
        `[nuxt.config] 环境变量 ${name} 仍是 JeecgBoot 开源默认值，已拒绝启动。\n`
        + `  · 生产/测试环境：必须换成与后端一致的私有值。\n`
        + `  · 仅本地开发：若暂时无法与后端一起轮换，可在 .env（已被 gitignore）中显式设置\n`
        + `    ALLOW_INSECURE_DEMO_SECRETS=true 后再启动 —— 这只表示"我知道自己在用公开密钥"。`,
      )
    }
    console.warn(
      `[nuxt.config] ⚠️ ${name} 正在使用 JeecgBoot 开源默认值（已通过 ALLOW_INSECURE_DEMO_SECRETS 放行）。`
      + `任何能读到开源仓库的人都能伪造签名/解密口令，禁止用于生产。`,
    )
  }

  return value
}

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
  // 每个前台站点（一个店铺）在部署时用自己的域名/站名，否则多店铺共用一份
  // 'localhost:3000' 会产出错误的 canonical / sitemap / OG 链接。
  site: {
    url: process.env.STORE_SITE_URL || 'http://localhost:3000',
    name: process.env.STORE_SITE_NAME || 'Cikian Shop',
    description: process.env.STORE_SITE_DESCRIPTION || 'Cikian Shop 多店铺商城',
    // 与 i18n.defaultLocale 同源，避免两处各写一份默认语言
    defaultLocale: STORE_DEFAULT_LANG,
  },

  runtimeConfig: {
    apiBaseUrl: process.env.API_BASE_URL || 'http://127.0.0.1:9951/api',

    /** 调用 Java 后端的超时（毫秒）。没有它时后端卡住会把 Nitro 的 SSR 请求一起挂死。 */
    apiTimeoutMs: Number(process.env.API_TIMEOUT_MS || 10000),

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

    /**
     * 本站对外访问的 origin（如 https://shop.example.com）。
     *
     * 用途：Google OAuth 的 redirect_uri 必须是一个**确定**的地址。
     * 留空时才回退到「本次请求的 origin」；而 getRequestURL() 会采信
     * Host / X-Forwarded-Host，若前置代理没有校验 Host，攻击者可以伪造它，
     * 让 BFF 拿着一份攻击者指定的 redirect_uri 去换 code。
     * 生产环境**建议显式配置**本项。
     */
    publicOrigin: process.env.STORE_PUBLIC_ORIGIN || '',

    /**
     * httpOnly 登录 cookie 是否加 Secure 标记。
     *
     * 默认 **true**（安全优先）：Secure cookie 不会在明文 HTTP 上发送，
     * 避免令牌在网络中被顺手读走。只有在「用 http 隧道 / 局域网 IP 调试」
     * 这种非 HTTPS 场景才用 COOKIE_SECURE=false 显式关闭 —— 注意此时
     * 浏览器（Chrome/Firefox）会拒收 Secure cookie，登录会直接失效，
     * 所以这是一个必须由部署者明确知晓的开关，不再依赖 NODE_ENV 是否被设置。
     */
    cookieSecure: (process.env.COOKIE_SECURE ?? 'true').toLowerCase() !== 'false',

    // ★ 密钥只从环境变量取，缺失或仍是示例值直接报错（见文件顶部 requireSecret）
    aesKey: requireSecret('AES_KEY'),
    aesIv: requireSecret('AES_IV'),
    signSecret: requireSecret('SIGN_SECRET'),

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