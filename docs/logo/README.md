# Lautira Logo

## 品牌

**Lautira** · /laʊˈtɪərə/

Laut（印尼语 / 马来语：海）+ Tira（梵语：岸）。
图形是一条从海面过渡到岸线的连续线，末端一点是抵达；字母 L 是首字母。

## 颜色

| 名称 | Hex | 用途 |
|---|---|---|
| Coral | `#FF4D6D` | 图形标渐变起点 / 主品牌色 |
| Amber | `#FFB03A` | 图形标渐变终点 / 点 |
| Ink | `#1F1A1C` | 字标 / 深色底 |
| Cream | `#FFF6F2` | 浅色底 |

## 结构比例

- 图形标高 : 字标高 = **264 : 163 = 1.618（φ）**
- 点径 **40** = 笔画宽 66 ÷ φ
- 点距 **25** = 点径 ÷ φ
- 图形标外框 **327 : 264 ≈ √φ**
- App icon **512 : 316 = 1.618**
- 横版间距 38、外边距 24、竖版间距 62

## 文件

```
svg/
  lautira-lockup.svg        横向 lockup（浅色底主用）
  lautira-stacked.svg       竖版 lockup
  lautira-mark.svg          图形标（透明）
  lautira-lockup-white.svg  横向 · 白色反白（深色底）
  lautira-lockup-ink.svg    横向 · 墨色单色（印刷）
  lautira-mark-white.svg    图形标 · 白色
  lautira-app-icon.svg      App icon 512
  favicon.svg               favicon（与 App icon 同源）

png/
  lautira-lockup.png        1006 × 312   （@2x / @4x 同名后缀）
  lautira-stacked.png       641 × 537
  lautira-mark.png          375 × 312
  lautira-lockup-white.png  1006 × 312
  lautira-lockup-ink.png    1006 × 312
  lautira-mark-white.png    375 × 312
  lautira-app-icon-512/1024/2048/180/64/32.png
  favicon-32/64.png
  preview-white-lockup.png  深色底预览

lautira-concept-board.png   系统板
lautira-header-preview.png  Header 预览
lautira-on-surfaces.png     多底色预览
edge-before-after.png       边缘修复前后（3×）
source/lautira-source.png   生图原稿（参考）
```

## 用法

浅色底：

```html
<img src="/logo/lautira-lockup.svg" alt="Lautira" height="32">
```

深色底：

```html
<img src="/logo/lautira-lockup-white.svg" alt="Lautira" height="32">
```

- Header 建议高度 **32–40px**；整条 lockup 宽度约 103–129px。
- 最小尺寸：图形标 24px 高；favicon 32px。
- 净空：四周至少留出「点径」的 1×（40/264 ≈ 15% 标记高度）。
- 不要把旧 PNG 再拿去描摹；**以 SVG 为准**，PNG 都是从 SVG 导出的。

## 技术

- 图形标：2 条曲线（L 浪 + 点）
- 字标：10 条曲线，已转曲，不依赖字体
- 渐变：coral → amber，SVG `linearGradient`
- 所有 PNG 由 SVG 4× 渲染后 LANCZOS 下采样，边缘连续
