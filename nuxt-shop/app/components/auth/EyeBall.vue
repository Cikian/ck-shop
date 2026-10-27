<template>
  <!--
    插画角色的「眼球 / 瞳孔」。

    之所以单独抽一个组件：同一页有 8 只眼睛，每只都要根据自己的
    getBoundingClientRect() 计算瞳孔偏移。写在父组件里会变成 8 份重复的
    template ref + 计算逻辑；抽出来只多一个文件，没有引入任何第三方依赖。

    · pupilOnly=true：只画瞳孔（参考原稿里橙色 / 黄色角色，没有白色眼白）
    · tracking=false：SSR / 减少动效时保持居中，不做鼠标追踪
  -->
  <div
    ref="root"
    class="eye"
    :class="{ 'eye--pupil-only': pupilOnly }"
    :style="{
      width: `${size}px`,
      height: blinking ? '2px' : `${size}px`,
      backgroundColor: pupilOnly ? 'transparent' : eyeColor,
    }"
  >
    <div
      v-if="!blinking"
      class="pupil"
      :style="{
        width: `${pupilSize}px`,
        height: `${pupilSize}px`,
        backgroundColor: pupilColor,
        transform: `translate(${offset.x}px, ${offset.y}px)`,
      }"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 眼睛组件。
 *
 * 只接收鼠标坐标（由父组件统一监听一次 mousemove 并节流到 rAF），
 * 不自己注册 window 监听器 —— 否则 8 只眼睛会挂 8 个全局监听。
 */
const props = withDefaults(defineProps<{
  size?: number
  pupilSize?: number
  maxDistance?: number
  eyeColor?: string
  pupilColor?: string
  isBlinking?: boolean
  forceLookX?: number
  forceLookY?: number
  mouseX?: number
  mouseY?: number
  tracking?: boolean
  pupilOnly?: boolean
}>(), {
  size: 18,
  pupilSize: 7,
  maxDistance: 5,
  eyeColor: '#fff',
  pupilColor: 'var(--color-ink)',
  isBlinking: false,
  forceLookX: undefined,
  forceLookY: undefined,
  mouseX: 0,
  mouseY: 0,
  tracking: false,
  pupilOnly: false,
})

const root = ref<HTMLElement | null>(null)

/** 只有白色眼白才眨眼；纯瞳孔角色（橙/黄）保持静止 */
const blinking = computed(() => props.isBlinking && !props.pupilOnly)

const offset = computed(() => {
  // 强制视线优先：角色互相看 / 捂眼睛时不再追踪鼠标
  if (props.forceLookX !== undefined && props.forceLookY !== undefined) {
    return { x: props.forceLookX, y: props.forceLookY }
  }

  // SSR 阶段没有 rect，也没有鼠标；先居中，水合后再跟随
  if (!props.tracking || !root.value) return { x: 0, y: 0 }

  const rect = root.value.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2
  const deltaX = props.mouseX - centerX
  const deltaY = props.mouseY - centerY
  const distance = Math.min(Math.hypot(deltaX, deltaY), props.maxDistance)
  const angle = Math.atan2(deltaY, deltaX)

  return { x: Math.cos(angle) * distance, y: Math.sin(angle) * distance }
})
</script>

<style scoped>
.eye {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 9999px;
  overflow: hidden;
  transition: height 150ms ease, background-color 150ms ease;
}

/* 纯瞳孔不裁剪，瞳孔才能移出 12px 的小圆 */
.eye--pupil-only {
  overflow: visible;
}

.pupil {
  border-radius: 9999px;
  transition: transform 100ms ease-out;
  will-change: transform;
}

@media (prefers-reduced-motion: reduce) {
  .eye,
  .pupil {
    transition: none;
  }
}
</style>
