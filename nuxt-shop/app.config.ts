// app.config.ts
//
// Nuxt UI 主题映射。
// 注意：本项目的品牌色定义在 app/assets/css/tailwind.css 的 @theme 里
// （--color-brand-* = 珊瑚玫红 #FF4D6D）。
// Nuxt UI v4 从 Tailwind 主题解析颜色名，但为了零构建风险，
// 这里映射到 Tailwind 内置的 rose（#f43f5e），与品牌色视觉接近；
// 自有组件一律直接使用 var(--color-brand-*) 取到精确色值。
export default defineAppConfig({
  ui: {
    colors: {
      primary: 'rose',
      neutral: 'stone',
    },
  },
})
