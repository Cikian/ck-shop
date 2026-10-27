# 页面设计说明：个人中心（/profile）

> 适用范围：`nuxt-shop/app/pages/profile.vue` 及其 `app/components/profile/*`。
> 更新时间：2026-10-27（第三轮：只读优先 + 精简重复）
>
> ⚠️ **本文件覆盖 `../MASTER.md`。** MASTER 里记录的
> 「E-commerce Luxury / Liquid Glass / #1C1917 + #CA8A04」是一版被弃用的方向，
> 站点实际采用的是「珊瑚玫红 × 奶油白」国内电商风，**唯一真相来源是
> `app/assets/css/tailwind.css` 里的 `@theme` 令牌**（定稿见 `docs/store-redesign/index.html` 第 14 张）。
> 本文件只描述个人中心这一页的页面级约定。

---

## 1. 设计令牌（只用站点 token，不写裸色值）

| 用途 | Token | 值 |
|---|---|---|
| 页面底色 | `--color-cream` | `#FFF6F2` |
| 卡片底色 | `--color-surface` | `#FFFFFF`（`.ui-card` 自带 20px 圆角 + `--shadow-card`） |
| 主色 / 数字强调 | `--color-brand-600` `#E63E5C` | 账户概览数字、可交互强调 |
| 实心按钮（白字） | `--color-brand-cta` `#C42F4A` | 对比度 5.45:1，**不要**用 `brand-500`（3.21:1） |
| 主文字 / 次文字 | `--color-ink` `#1F1A1C` / `--color-ink-soft` `#4A4244` | |
| 辅助文字（12–13px） | `--color-muted` `#6B6269` | 白底 5.87:1、奶油底 5.51:1 |
| 轻底块（概览格 / 头像行） | `--color-surface-secondary` | `#FDF2EE` |
| 分隔线 | `--color-line` / `--color-line-soft` | |
| 点缀 | `--color-accent-100/200` | 只做柔和晕染，不做大面积底色 |

圆角：卡片 `--radius-card`(20px)、内层块 `--radius-card-sm`(14px)、按钮 `999px`。
动效：`--duration-base`(250ms) + `--ease-standard`，且必须带 `motion-reduce:transition-none`。

## 2. 页面骨架

```
无视觉页头：h1 只保留 sr-only（读屏/文档结构用）

根节点：flex min-h-[calc(100vh-var(--nav-total-height))] flex-col + pt-navbar
        （**不要** min-h-screen：布局的 main 已是 flex-1，再叠 100vh 会多出一屏可滚的空白）

≥1024px   grid: minmax(260px,300px) + minmax(0,1fr)，lg:items-stretch
          左栏 = 身份卡 + 分区导航（lg:sticky + lg:self-start，top = nav 高度 + 24px）
          右栏 = 一张内容卡（ui-card flex flex-1 flex-col：撑满可用高度，空白留在卡内）
<1024px   单列：身份卡 → 分区导航（横滑药丸）→ 内容卡
```

- 左栏 `aside` **必须**带 `min-w-0`（内部横滑导航的 min-content 会把整列撑宽，见 §6）。
- 左栏同时要 `lg:self-start`：网格改成 `items-stretch` 后不写会让 sticky 失效。
- 身份卡内容在 `<1024` 时限宽 `max-w-[420px] mx-auto`，避免 700px 宽的实心药丸。
- 卡片间距 `gap-4`（lg `gap-5`），与全站 4/8 节奏一致。
- 空态（订单 / 收藏 / 地址）要 `flex-1` 垂直居中，不要在伸展后的白卡里顶着上沿。

## 3. 组件规格

### 身份卡（唯一头像入口）
- 头像 92px 正圆、`ring-4 ring-surface` + 柔和投影；hover/focus 出相机遮罩；点击即打开「更换头像」弹层。
  **这是全页唯一改头像的入口**（面板里不再有「更换头像」按钮）。
- 顶部装饰：`bg-gradient-to-b from-brand-100 to-transparent`（150px）+ 右上 `accent-200/50 blur-2xl` 光斑。
  不要用「固定高度纯色矩形」——会在卡片上留下一条硬边。
- 内容顺序：头像 → 昵称 → 邮箱 → 「编辑资料」(`h-11` 整行) → 「退出登录」（12.5px 中性文字链）。
- **不展示注册时间**（需求方明确要求）；也不放任何"点头像即可更换"式提示。

### 分区导航（WAI-ARIA tabs）
- 一份 DOM、两种排布：`<1024` 横滑药丸（选中 = `brand-cta` 实心白字），`≥1024` 竖排卡片行（选中 = `brand-50` 底 + `brand-700` 字 + `brand-200` 描边）。
- 必须保留：`role="tablist"`、`role="tab"`、`aria-selected`、`aria-controls`、`tabindex`（仅选中项为 0）。
- 键盘：← → ↑ ↓ 循环、Home / End 跳首尾，焦点跟随选中项。
- 导航**不放数量徽标**（数量由账户概览表达）。

### 内容卡
- 表头：`17px font-black` 标题 + `12.5px` 一句说明 + 该分区**唯一**的主操作（同基线、`border-b border-line-soft`）。
  「编辑资料」**不在表头**（资料卡上已有，禁止同屏两个同文案主按钮）；表头只有收货地址的「新增地址」。
- 面板切换用 `<Transition name="fade" mode="out-in">`，面板容器 `role="tabpanel"` + `aria-labelledby`。
- 面板内部骨架条件一律用 `!loaded`（不能用 `loading && !list.length`，否则 hydration mismatch）。

### 账户资料：只读视图（默认）
四段，段间用 `border-t border-line-soft` 分隔，每段一个 `13px font-black` 小标题 + lucide 图标：

1. **账户概览**：3 格（订单 / 收藏 / 收货地址）。结构 = 22px `font-black` 数字（`brand-600`）+ 12px 语义名 + 右侧 32px 圆形图标；底色 `surface-secondary`，`sm` 以上显示图标。三格都是 `<button>`，点击直达对应分区。
2. **个人资料**：`dl` 两列（`sm:grid-cols-2`）——昵称 / 手机号 / 邮箱（邮箱跨两列）。标签 12.5px `muted`，值 15px `font-bold` `ink`；空值用 `muted` 的「未填写」。
3. **默认收货地址**：有则显示「收货人 + 电话 + 标签 + 完整地址」，无则一句「还没有默认收货地址」；右上角「管理地址」跳收货地址分区。
4. **账户密码**：一行标题 + 说明 + 右侧「修改密码 / 设置密码」按钮；**表单默认收起**，点开才出现。

### 账户资料：编辑模式
- 由页面的 `editingProfile` 单向下发（`v-model:editing`），入口只有资料卡的「编辑资料」。
- 表单：昵称（必填，空时字段级报错）、手机号（选填 + 一句提示）、邮箱（`readonly`，**白底、无特殊背景色**，靠锁图标说明）。
- 底部「保存修改」（不可保存时是 ghost 态）+「取消」；保存成功与取消都 `emit('update:editing', false)` 退回只读。
- 切换分区、浏览器前进/后退都会把 `editingProfile` 复位为 false。

### 按钮禁用态
- 禁用不用「brand + opacity-45」（半透明珊瑚会被读成 loading），改 `ui-pill-ghost` + `text-muted`。
- 所有可点元素：`cursor-pointer`；焦点：全局 `*:focus-visible` 已有 `2px brand-500` 描边。

## 4. 文案规则

- **不要写解释交互的提示文字**（"点头像即可更换"、"上传自己的照片，或从预设插画里挑一个"）。
- 标签用用户视角的词：**昵称**（不是"真实姓名"）、邮箱、手机号。
- 分区说明只描述"这里有什么"，不描述"怎么点"。
- 保留的辅助文案属于**约束或后果**（如「邮箱是登录账号，暂不支持自行修改」），不是操作教学。

## 5. 无障碍

| 项 | 要求 |
|---|---|
| 对比度 | 正文 4.5:1（`npm run check:contrast` 全绿）；实心按钮用 `brand-cta` |
| 标题 | 视觉页头已删，但 `h1` 必须保留为 `sr-only`（读屏与文档结构） |
| 图标 | 只用 lucide，装饰性图标 `aria-hidden="true"`；纯图标按钮要有 `aria-label` |
| 键盘 | tabs 四向键 + Home/End；焦点可见；`?tab=` 可刷新可分享 |
| 减少动效 | 所有过渡带 `motion-reduce:transition-none`；编程滚动在 reduce 下用 `auto` |
| 图片 | 头像 `width/height` 固定，`alt` 用昵称，加载失败回落首字母 |

## 6. 已知陷阱（出现即回归）

1. **`min-h-screen` + `pt-navbar` 双写** → 页面比视口多出一整屏（100vh 又往下推了导航高度），短内容页会多滚一屏空白。根节点只能用 `min-h-[calc(100vh-var(--nav-total-height))]`，并让容器/网格/内容卡 `flex-1` 逐级撑满。
2. **`items-stretch` 会打断 sticky** → 左栏必须显式 `lg:self-start`。
3. **grid item `min-width: auto`** → 左栏不写 `min-w-0` 会在 390px 下把文档撑到 442px（整页缩放、右侧截断）。
4. **`parseFloat('calc(64px + 64px)')` = 64** → 移动端 `--nav-total-height` 是 calc 字符串，滚动偏移要用 `getComputedStyle(html).scrollPaddingTop`（已解析成 px）。
5. **`mode="out-in"` 的高度差** → 只读视图比编辑表单高，切换动画期间量的位置会偏；滚动定位要等 `--duration-base` 之后再量。
6. **`?tab=` 是分区唯一真相**：Header 的「个人中心 / 我的订单 / 收藏夹」三个入口都靠它直达；`?tab=info` 是 `settings` 的兼容别名。
7. **订单按钮可见性只认后端 `canPay/canCancel/canReceive/canRefund`**，状态文字只认 `orderStatusLabel`。
8. **`defineExpose({ openCreate })`**：收货地址的「新增地址」在页面表头，弹层仍在面板内；一条地址都没有时表头不出按钮（空态已有主按钮）。
9. **SSR / hydration**：`immediate` watch 必须写在它用到的所有 `ref` 声明之后；骨架条件用 `!loaded`。
10. **同一屏不许出现两个同义入口**：头像只有资料卡一个；「编辑资料」只有资料卡一个；数字只出现在账户概览一处。

## 7. 验收方式

```bash
# 只读/编辑模式 + 入口唯一 + 无注册时间 + 邮箱无底色 + 无溢出（24 项断言）
node .probe/profile-readonly-edit.probe.mjs
# 断点扫描（1920→375，10 个宽度，要求 0 横向溢出）
node .probe/profile-breakpoints.diag.mjs
# 三语 × 四分区 × 两视口截图 + 溢出/提示文案断言
node .probe/profile-redesign.shot.mjs --locales zh,en,fr --tabs settings,orders,favorites,addresses
# 交互：头像弹层 / 键盘 tabs / 老链接 / 概览直达 / 表头新增
node .probe/profile-redesign.interact.mjs
node .probe/profile-address-header-action.probe.mjs
# 对比度
cd nuxt-shop && npm run check:contrast
```
