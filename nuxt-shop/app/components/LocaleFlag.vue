<template>
  <svg
    v-if="flag"
    :class="sizeClass"
    :title="title"
    viewBox="0 0 24 16"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    focusable="false"
    class="shrink-0 self-center rounded-[3px] ring-1 ring-black/10"
  >
    <!--
      旗帜一律用**内联 SVG** 绘制：Windows 没有系统国旗字体，emoji 国旗（区域指示符序列）
      会退化成两个方框字母，而 SVG 不依赖字体、跨平台渲染一致。
      ring-1 ring-black/10 是必需的：白底旗（如日旗）在白色下拉面板里会糊成一块，
      给一圈内描边才能看出边界。

      注意：self-center 不能删 —— Nuxt UI 的下拉菜单项是 `display:flex; align-items:flex-start`，
      而文字行高（20px）比旗帜（14px）高 —— 两者都从顶部对齐时，旗帜中心会比文字中心
      高 3px（实测 77 vs 80），看起来就是「旗帜和文字没对齐」。
      加 self-center 让旗帜在自己的 flex 行内垂直居中，中心线就与文字一致（同实测 80）。
      对本来就是 items-center 的容器（移动端列表、顶栏按钮）无副作用。
    -->

    <!-- 美国：13 道红白横条 + 左上角蓝底星区 -->
    <template v-if="flag === 'us'">
      <rect width="24" height="16" fill="#ffffff" />
      <g fill="#b22234">
        <rect y="0" width="24" height="1.23" />
        <rect y="2.46" width="24" height="1.23" />
        <rect y="4.92" width="24" height="1.23" />
        <rect y="7.38" width="24" height="1.23" />
        <rect y="9.85" width="24" height="1.23" />
        <rect y="12.31" width="24" height="1.23" />
        <rect y="14.77" width="24" height="1.23" />
      </g>
      <rect width="10" height="8.62" fill="#3c3b6e" />
      <g fill="#ffffff">
        <circle cx="1.6" cy="1.5" r="0.45" />
        <circle cx="3.6" cy="1.5" r="0.45" />
        <circle cx="5.6" cy="1.5" r="0.45" />
        <circle cx="7.6" cy="1.5" r="0.45" />
        <circle cx="2.6" cy="3.1" r="0.45" />
        <circle cx="4.6" cy="3.1" r="0.45" />
        <circle cx="6.6" cy="3.1" r="0.45" />
        <circle cx="8.6" cy="3.1" r="0.45" />
        <circle cx="1.6" cy="4.7" r="0.45" />
        <circle cx="3.6" cy="4.7" r="0.45" />
        <circle cx="5.6" cy="4.7" r="0.45" />
        <circle cx="7.6" cy="4.7" r="0.45" />
        <circle cx="2.6" cy="6.3" r="0.45" />
        <circle cx="4.6" cy="6.3" r="0.45" />
        <circle cx="6.6" cy="6.3" r="0.45" />
        <circle cx="8.6" cy="6.3" r="0.45" />
      </g>
    </template>

    <!-- 中国：红底 + 左上角一大四小五角星 -->
    <template v-else-if="flag === 'cn'">
      <rect width="24" height="16" fill="#de2910" />
      <g fill="#ffde00">
        <polygon points="4.2,1.6 5.06,3.6 7.22,3.78 5.58,5.2 6.06,7.32 4.2,6.2 2.34,7.32 2.82,5.2 1.18,3.78 3.34,3.6" />
        <polygon points="8.4,1.2 8.78,2.06 9.72,2.14 9.02,2.76 9.22,3.68 8.4,3.2 7.58,3.68 7.78,2.76 7.08,2.14 8.02,2.06" />
        <polygon points="10.2,3.1 10.58,3.96 11.52,4.04 10.82,4.66 11.02,5.58 10.2,5.1 9.38,5.58 9.58,4.66 8.88,4.04 9.82,3.96" />
        <polygon points="10.2,5.9 10.58,6.76 11.52,6.84 10.82,7.46 11.02,8.38 10.2,7.9 9.38,8.38 9.58,7.46 8.88,6.84 9.82,6.76" />
        <polygon points="8.4,7.8 8.78,8.66 9.72,8.74 9.02,9.36 9.22,10.28 8.4,9.8 7.58,10.28 7.78,9.36 7.08,8.74 8.02,8.66" />
      </g>
    </template>

    <!-- 法国：蓝(#002395) / 白 / 红(#ED2939) 三竖条，由左至右 -->
    <template v-else-if="flag === 'fr'">
      <rect x="0" width="8" height="16" fill="#002395" />
      <rect x="8" width="8" height="16" fill="#ffffff" />
      <rect x="16" width="8" height="16" fill="#ED2939" />
    </template>
  </svg>
</template>

<script setup lang="ts">
// 注意：`~~/utils/...` 在 Nuxt 4 里解析到**项目根**的 utils/（本组件旁边是 app/ 目录），
// 所以这里必须用相对路径指向 app/utils/locale-flag.ts，否则运行时会报
// "Cannot find module '~~/utils/locale-flag'"。
import { flagLabel, resolveLocaleFlag } from '../utils/locale-flag'

/**
 * 语言旗帜（内联 SVG）。
 *
 * 为什么是 SVG 而不是 emoji：Windows 上没有国旗字体，emoji 国旗序列必然退化成
 * 两个方框字母（用户截图里的「US / CN / FR」），SVG 跨平台一致且不依赖字体。
 *
 * 无障碍：旗帜是**装饰元素**（紧邻永远有可见的语言名），因此外层 aria-hidden="true"；
 * 国家名放在 title 里供鼠标悬停查看，不用 aria-label —— 否则读屏会把国家名和语言名
 * 各念一遍，产生重复播报。
 */
const props = withDefaults(defineProps<{
  /** i18n locale code（en / zh / fr）；未知值不渲染任何东西 */
  code: string
  /** 尺寸：md 用于桌面下拉，sm 用于移动端列表 */
  size?: 'md' | 'sm'
}>(), {
  size: 'md',
})

const flag = computed(() => resolveLocaleFlag(props.code))
const title = computed(() => flagLabel(props.code))

// 尺寸走 class 而不是内联 style，和仓库其它组件的写法保持一致
const SIZE_CLASS: Record<'md' | 'sm', string> = {
  md: 'h-[14px] w-5',
  sm: 'h-4 w-6',
}
const sizeClass = computed(() => SIZE_CLASS[props.size])
</script>
