// app.config.ts
//
// Nuxt UI 主题映射。
// 本项目的品牌色定义在 app/assets/css/tailwind.css 的 @theme 里
// （--color-brand-* = 珊瑚玫红 #FF4D6D）。
//
// primary 指向 **brand**：@nuxt/ui 的颜色解析器（runtime/plugins/colors.js）
// 会生成 `--ui-color-primary-*: var(--color-brand-*)`，
// 于是 Nuxt UI 组件（button/toast/modal/input…）的 primary 与自有组件同源。
//
// ⚠️ 之前这里写的是 'rose'（#f43f5e）：它只影响 --ui-primary 等运行时变量，
// 而 tailwind.css 又把 --color-primary-* 覆盖成了墨黑阶梯，导致
// 「品牌珊瑚 / rose / 墨黑」三套 primary 并存。现在 tailwind.css 里的
// --color-primary-* 也只是 var(--color-brand-*) 的别名，两处语义一致；
// 不要再改回某个 Tailwind 内置色名（那会让品牌色重新分叉）。
export default defineAppConfig({
  ui: {
    colors: {
      primary: 'brand',
      neutral: 'stone',
    },
  },
})
