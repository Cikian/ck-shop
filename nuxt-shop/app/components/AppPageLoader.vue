<!--
  app/components/AppPageLoader.vue

  全屏加载遮罩：「螺旋搜索」（Spiral Search / 阿基米德螺旋 + 粒子拖尾）。

  来源：用户指定的参考脚本。这里**只保留动画本体**，去掉原脚本里与本站无关的内容
  （深色底、标题、Archimedean Spiral 标签、公式代码块、View All 按钮），
  配色换成站内 token：奶油底 + 沿螺旋跑动的珊瑚色彗星（粒子拖尾）。

  2026-10-29（用户反馈）：**去掉彗星后面那条淡色螺旋轨迹线**，只留移动的彗星本身。
  轨迹线的 path 还在模板里（粒子的视觉基准、也是 prefers-reduced-motion 下的静态提示），
  但样式里正常状态是 `display: none`（见 tailwind.css `.page-loader__trail`），
  所以画面上只有彗星 + 它的渐隐拖尾。呼吸、时序、无障碍降级均未改动。

  数学（与参考一致）：
    θ(t) = 3t
    r(t) = 8 + (1 - cos t)(10 + 4s)      ← s = detailScale，原脚本 4.5s 一次脉动
    x(t) = 50 + cos θ · r(t) · 0.70
    y(t) = 50 + sin θ · r(t) · 0.70

  与参考实现最大的不同：**不跑 requestAnimationFrame**。
    参考脚本每帧重算 480 段路径 + 50 个粒子；这里把曲线固化成一条 path，
    粒子用 CSS `offset-path` + `offset-distance` 沿它匀速跑（每个粒子负延时错开），
    "呼吸"用 `transform: scale` 近似 detailScale 脉动。
    好处：① 加载遮罩存在的意义就是覆盖"水合之前"那段 JS 还没跑起来的时间，
    纯 CSS 动画在水合前就能动，而 rAF 版本在那段时间里是静止的；
    ② 零 rAF、零字符串拼接，主线程完全不参与。

  无障碍：role="status" + aria-live + sr-only「加载中」；prefers-reduced-motion 下全部静止。
  时序：SSR 首帧渲染 → 水合完成淡出 → 移除；慢导航（>220ms）才出现；JS 崩了 14s 自动放开。
-->
<template>
  <div
    v-if="visible"
    class="page-loader"
    :class="{ 'page-loader--leaving': leaving }"
    role="status"
    aria-live="polite"
    aria-busy="true"
  >
    <svg
      class="page-loader__spiral"
      viewBox="0 0 100 100"
      fill="none"
      aria-hidden="true"
    >
      <g class="page-loader__group">
        <!-- 轨迹线：正常状态由 CSS 隐藏（display:none），只在 prefers-reduced-motion 下露出来 -->
        <path class="page-loader__trail" :d="spiralPath" stroke-linecap="round" stroke-linejoin="round" />

        <!-- 粒子：沿同一条曲线匀速跑，越靠后越小越淡（对应参考的 trailSpan 拖尾） -->
        <circle
          v-for="particle in particles"
          :key="particle.index"
          class="page-loader__particle"
          cx="50"
          cy="50"
          :r="particle.r"
          :opacity="particle.opacity"
          :style="{ animationDelay: particle.delay }"
        />
      </g>
    </svg>

    <span class="sr-only">{{ t('common.loading') }}</span>
  </div>
</template>

<script setup lang="ts">
const { t } = useI18n()
const nuxtApp = useNuxtApp()

/* ---------------- 螺旋参数（与参考脚本一致） ---------------- */
const TURNS = 3
const BASE_R = 8
const RADIUS_AMP = 10
const PULSE_AMP = 4
const SCALE = 0.7
const STEPS = 300
const PARTICLE_COUNT = 36
const TRAIL_SPAN = 0.16
const CYCLE_MS = 3000
/** 让粒子别从螺旋内圈起步（首帧就在曲线中段，观感更自然） */
const PHASE_MS = 1200

function spiralPoint(progress: number) {
  const t = progress * Math.PI * 2
  const angle = t * TURNS
  // detailScale 固定为 1：形状不再逐帧变形，改由 CSS 的 scale 呼吸近似
  const radius = BASE_R + (1 - Math.cos(t)) * (RADIUS_AMP + PULSE_AMP)
  return {
    x: 50 + Math.cos(angle) * radius * SCALE,
    y: 50 + Math.sin(angle) * radius * SCALE,
  }
}

/** SSR 期算好（确定性，服务端/客户端结果一致），首帧就有完整螺旋 */
const spiralPath = (() => {
  let d = ''
  for (let i = 0; i <= STEPS; i++) {
    const point = spiralPoint(i / STEPS)
    d += `${i === 0 ? 'M' : 'L'}${point.x.toFixed(2)} ${point.y.toFixed(2)}`
  }
  return d
})()

/** 每个粒子的静态外观 + 负延时（负延时 = 已经在曲线上跑了一段） */
const particles = Array.from({ length: PARTICLE_COUNT }, (_, index) => {
  const tailOffset = index / (PARTICLE_COUNT - 1)
  const fade = Math.pow(1 - tailOffset, 0.56)
  const lagMs = tailOffset * TRAIL_SPAN * CYCLE_MS
  return {
    index,
    r: (0.7 + Math.pow(fade, 1.25) * 2.3).toFixed(2),
    opacity: (0.04 + fade * 0.96).toFixed(3),
    delay: `${-(CYCLE_MS - lagMs + PHASE_MS).toFixed(0)}ms`,
  }
})

/* ---------------- 显示 / 隐藏（时序与之前一致） ---------------- */
const visible = ref(true)
const leaving = ref(false)

let removeTimer: ReturnType<typeof setTimeout> | null = null
let navShowTimer: ReturnType<typeof setTimeout> | null = null

function hide() {
  if (leaving.value) return
  leaving.value = true
  removeTimer = setTimeout(() => { visible.value = false }, 320)
}

function show() {
  if (removeTimer) { clearTimeout(removeTimer); removeTimer = null }
  leaving.value = false
  visible.value = true
}

onMounted(() => {
  // 首屏：等 Nuxt 完成水合再收（onMounted 时其它组件的事件监听还没装好）
  onNuxtReady(() => {
    requestAnimationFrame(() => hide())
  })

  // 客户端路由切换：只在"导航超过 220ms"时出现，避免快跳闪屏
  const offStart = nuxtApp.hook('page:loading:start', () => {
    navShowTimer = setTimeout(() => show(), 220)
  })
  const offEnd = nuxtApp.hook('page:loading:end', () => {
    if (navShowTimer) { clearTimeout(navShowTimer); navShowTimer = null }
    if (visible.value) hide()
  })

  onBeforeUnmount(() => {
    offStart()
    offEnd()
    if (navShowTimer) clearTimeout(navShowTimer)
    if (removeTimer) clearTimeout(removeTimer)
  })
})
</script>
