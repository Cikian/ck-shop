<template>
  <!--
    登录 / 注册页左侧品牌分栏。

    原稿是 React + shadcn 的紫色背景 + 紫/黑/橙/黄角色；这里做了三处必要改造：
      1) 配色换成本项目的「珊瑚玫红 × 奶油白」：深墨色底 + 珊瑚/浅粉/明黄/奶油色角色，
         既保留原稿的插画感，又不会和店铺端的品牌色脱节；
      2) 插画整体用 CSS 缩放，不写死 550×400 的像素画布，1024/1280/1536 断点都不溢出；
      3) 角色全部是装饰元素（aria-hidden），品牌名和底部导航才是可读内容。
  -->
  <aside class="auth-aside relative hidden h-dvh overflow-hidden lg:flex lg:flex-col lg:justify-between" :style="asideStyle">
    <!-- 背景装饰：网格 + 两团柔光，纯 CSS，不引图片 -->
    <div class="pointer-events-none absolute inset-0" aria-hidden="true">
      <div class="auth-grid absolute inset-0" :style="gridStyle"></div>
      <div class="absolute -right-24 -top-24 h-[26rem] w-[26rem] rounded-full bg-[var(--color-brand-500)]/25 blur-3xl"></div>
      <div class="absolute -bottom-32 -left-24 h-[30rem] w-[30rem] rounded-full bg-[var(--color-accent-400)]/10 blur-3xl"></div>
    </div>

    <!-- 品牌 -->
    <div class="relative z-10 flex items-center p-8 xl:p-12">
      <img
        src="/logo/lautira-lockup-white.svg"
        :alt="brandName"
        width="1006"
        height="312"
        class="h-10 w-auto"
      >
    </div>

    <!-- 插画角色 -->
    <div class="relative z-10 flex flex-1 items-center justify-center px-8 xl:px-12">
      <div class="auth-characters" aria-hidden="true">
        <div class="auth-stage" :style="stageStyle">
          <div class="auth-canvas" :style="canvasStyle">
            <!-- 后排：珊瑚色高角色 -->
            <div ref="backRef" class="char char--back" :style="[backBoxStyle, backStyle]">
              <div class="char-eyes char-eyes--back" :style="[backEyesBoxStyle, backEyesStyle]">
                <EyeBall
                  :size="18"
                  :pupil-size="7"
                  :max-distance="5"
                  eye-color="#fff"
                  pupil-color="var(--color-ink)"
                  :is-blinking="backBlinking"
                  :mouse-x="mouseX"
                  :mouse-y="mouseY"
                  :tracking="hasMouse"
                  :force-look-x="backLook.x"
                  :force-look-y="backLook.y"
                />
                <EyeBall
                  :size="18"
                  :pupil-size="7"
                  :max-distance="5"
                  eye-color="#fff"
                  pupil-color="var(--color-ink)"
                  :is-blinking="backBlinking"
                  :mouse-x="mouseX"
                  :mouse-y="mouseY"
                  :tracking="hasMouse"
                  :force-look-x="backLook.x"
                  :force-look-y="backLook.y"
                />
              </div>
            </div>

            <!-- 中排：浅粉色角色 -->
            <div ref="middleRef" class="char char--middle" :style="[middleBoxStyle, middleStyle]">
              <div class="char-eyes char-eyes--middle" :style="[middleEyesBoxStyle, middleEyesStyle]">
                <EyeBall
                  :size="16"
                  :pupil-size="6"
                  :max-distance="4"
                  eye-color="#fff"
                  pupil-color="var(--color-ink)"
                  :is-blinking="middleBlinking"
                  :mouse-x="mouseX"
                  :mouse-y="mouseY"
                  :tracking="hasMouse"
                  :force-look-x="middleLook.x"
                  :force-look-y="middleLook.y"
                />
                <EyeBall
                  :size="16"
                  :pupil-size="6"
                  :max-distance="4"
                  eye-color="#fff"
                  pupil-color="var(--color-ink)"
                  :is-blinking="middleBlinking"
                  :mouse-x="mouseX"
                  :mouse-y="mouseY"
                  :tracking="hasMouse"
                  :force-look-x="middleLook.x"
                  :force-look-y="middleLook.y"
                />
              </div>
            </div>

            <!-- 前排左：明黄半圆，只有瞳孔 -->
            <div ref="frontLeftRef" class="char char--front-left" :style="[frontLeftBoxStyle, frontLeftStyle]">
              <div class="char-eyes char-eyes--front-left" :style="[frontLeftEyesBoxStyle, frontLeftEyesStyle]">
                <EyeBall
                  :size="12"
                  :pupil-size="12"
                  :max-distance="5"
                  pupil-color="var(--color-ink)"
                  :mouse-x="mouseX"
                  :mouse-y="mouseY"
                  :tracking="hasMouse"
                  :force-look-x="frontLeftLook.x"
                  :force-look-y="frontLeftLook.y"
                  pupil-only
                />
                <EyeBall
                  :size="12"
                  :pupil-size="12"
                  :max-distance="5"
                  pupil-color="var(--color-ink)"
                  :mouse-x="mouseX"
                  :mouse-y="mouseY"
                  :tracking="hasMouse"
                  :force-look-x="frontLeftLook.x"
                  :force-look-y="frontLeftLook.y"
                  pupil-only
                />
              </div>
            </div>

            <!-- 前排右：奶油色角色，只有瞳孔 + 一条嘴线 -->
            <div ref="frontRightRef" class="char char--front-right" :style="[frontRightBoxStyle, frontRightStyle]">
              <div class="char-eyes char-eyes--front-right" :style="[frontRightEyesBoxStyle, frontRightEyesStyle]">
                <EyeBall
                  :size="12"
                  :pupil-size="12"
                  :max-distance="5"
                  pupil-color="var(--color-ink)"
                  :mouse-x="mouseX"
                  :mouse-y="mouseY"
                  :tracking="hasMouse"
                  :force-look-x="frontRightLook.x"
                  :force-look-y="frontRightLook.y"
                  pupil-only
                />
                <EyeBall
                  :size="12"
                  :pupil-size="12"
                  :max-distance="5"
                  pupil-color="var(--color-ink)"
                  :mouse-x="mouseX"
                  :mouse-y="mouseY"
                  :tracking="hasMouse"
                  :force-look-x="frontRightLook.x"
                  :force-look-y="frontRightLook.y"
                  pupil-only
                />
              </div>
              <div class="char-mouth" :style="[mouthBaseStyle, frontRightMouthStyle]"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部导航：只放真实存在的页面，不做原稿里的 Privacy/Terms 假链接 -->
    <div class="relative z-10 flex items-center gap-6 p-8 text-[13px] font-medium text-white/70 xl:p-12">
      <NuxtLinkLocale to="/" class="transition-colors duration-200 hover:text-white motion-reduce:transition-none">
        {{ t('common.home') }}
      </NuxtLinkLocale>
      <NuxtLinkLocale to="/categories" class="transition-colors duration-200 hover:text-white motion-reduce:transition-none">
        {{ t('nav.categories') }}
      </NuxtLinkLocale>
      <NuxtLinkLocale to="/cart" class="transition-colors duration-200 hover:text-white motion-reduce:transition-none">
        {{ t('nav.cart') }}
      </NuxtLinkLocale>
      <span class="ml-auto shrink-0 text-[11px] text-white/60">© {{ year }} {{ brandName }}</span>
    </div>
  </aside>
</template>

<script setup lang="ts">
import type { Ref } from 'vue'
import EyeBall from './EyeBall.vue'

/**
 * 品牌分栏。
 *
 * 页面只需要告诉它「用户是否正在输入」「密码是否可见」，其余动画
 * （眨眼、互相看、捂眼睛、偷看）全部在这里完成，登录/注册两页共用。
 */
const props = withDefaults(defineProps<{
  /** 表单内是否有焦点（输入中） */
  isTyping?: boolean
  /** 密码是否处于明文显示状态 */
  passwordVisible?: boolean
  /** 密码长度；配合 passwordVisible 判断是否要「捂眼睛」 */
  passwordLength?: number
}>(), {
  isTyping: false,
  passwordVisible: false,
  passwordLength: 0,
})

const { t } = useI18n()
const { storeName } = useStoreInfo()
const brandName = computed(() => storeName.value || t('shop.brandName'))
const year = new Date().getFullYear()

// ===== 鼠标追踪（整个组件只挂一个监听，rAF 节流） =====
const mouseX = ref(0)
const mouseY = ref(0)
const hasMouse = ref(false)
const reducedMotion = ref(false)
let frame = 0
let pendingX = 0
let pendingY = 0

const onMouseMove = (event: MouseEvent) => {
  pendingX = event.clientX
  pendingY = event.clientY
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    mouseX.value = pendingX
    mouseY.value = pendingY
    hasMouse.value = true
  })
}

// 窗口尺寸变化后，角色的 getBoundingClientRect 会变；用 tick 让 computed 重新算一次
const resizeTick = ref(0)
const onResize = () => {
  resizeTick.value += 1
}

// ===== 角色位置 =====
const backRef = ref<HTMLElement | null>(null)
const middleRef = ref<HTMLElement | null>(null)
const frontLeftRef = ref<HTMLElement | null>(null)
const frontRightRef = ref<HTMLElement | null>(null)

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value))

const getPosition = (element: HTMLElement | null) => {
  if (!element || !hasMouse.value) return { faceX: 0, faceY: 0, bodySkew: 0 }

  // 读取 tick，让视口变化也能触发重新计算
  void resizeTick.value

  const rect = element.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 3
  const deltaX = mouseX.value - centerX
  const deltaY = mouseY.value - centerY

  return {
    faceX: clamp(deltaX / 20, -15, 15),
    faceY: clamp(deltaY / 30, -10, 10),
    bodySkew: clamp(-deltaX / 120, -6, 6),
  }
}

const backPos = computed(() => getPosition(backRef.value))
const middlePos = computed(() => getPosition(middleRef.value))
const frontLeftPos = computed(() => getPosition(frontLeftRef.value))
const frontRightPos = computed(() => getPosition(frontRightRef.value))

// ===== 交互状态 =====
const isLookingAtEachOther = ref(false)
let lookTimer: ReturnType<typeof setTimeout> | undefined

watch(() => props.isTyping, (typing) => {
  clearTimeout(lookTimer)
  if (typing) {
    isLookingAtEachOther.value = true
    lookTimer = setTimeout(() => {
      isLookingAtEachOther.value = false
    }, 800)
  } else {
    isLookingAtEachOther.value = false
  }
})

/** 密码可见且有内容 → 角色「捂眼睛」 */
const coverEyes = computed(() => props.passwordVisible && props.passwordLength > 0)
/** 密码有内容但被遮住 → 角色伸长脖子偷看 */
const enteringPassword = computed(() => props.passwordLength > 0 && !props.passwordVisible)
const backTall = computed(() => props.isTyping || enteringPassword.value)

// ===== 眨眼 =====
const backBlinking = ref(false)
const middleBlinking = ref(false)

const createBlinker = (target: Ref<boolean>) => {
  let next: ReturnType<typeof setTimeout> | undefined
  let off: ReturnType<typeof setTimeout> | undefined

  const loop = () => {
    next = setTimeout(() => {
      target.value = true
      off = setTimeout(() => {
        target.value = false
        loop()
      }, 150)
    }, 3000 + Math.random() * 4000)
  }

  loop()

  return () => {
    clearTimeout(next)
    clearTimeout(off)
  }
}

let stopBackBlink: (() => void) | undefined
let stopMiddleBlink: (() => void) | undefined

// ===== 珊瑚色角色偷看 =====
const isPeeking = ref(false)
let peekTimer: ReturnType<typeof setTimeout> | undefined
let peekOffTimer: ReturnType<typeof setTimeout> | undefined

const clearPeek = () => {
  clearTimeout(peekTimer)
  clearTimeout(peekOffTimer)
  peekTimer = undefined
  peekOffTimer = undefined
  isPeeking.value = false
}

const schedulePeek = () => {
  clearTimeout(peekTimer)
  if (!coverEyes.value || reducedMotion.value) return

  peekTimer = setTimeout(() => {
    isPeeking.value = true
    peekOffTimer = setTimeout(() => {
      isPeeking.value = false
      schedulePeek()
    }, 800)
  }, 2000 + Math.random() * 3000)
}

watch(coverEyes, (covering) => {
  if (covering && !reducedMotion.value) schedulePeek()
  else clearPeek()
})

// ===== 静态样式 =====
/*
 * 角色的几何/配色放在内联 style，而不是 scoped CSS：
 * 这些值本来就是参考稿里的固定像素坐标，内联后不用给每个选择器加
 * scoped 属性，构建产物里的 CSS 体积也更可控（项目有 240KB 预算守卫）。
 * 动态部分（height / transform / left / top）仍在下面的 computed 里覆盖。
 */
const asideStyle = {
  background:
    'radial-gradient(ellipse at 18% 18%, rgba(255, 77, 109, 0.22), transparent 52%), '
    + 'radial-gradient(ellipse at 82% 78%, rgba(255, 213, 74, 0.10), transparent 48%), '
    + 'linear-gradient(155deg, var(--color-ink) 0%, var(--color-brand-950) 55%, var(--color-brand-900) 100%)',
}

const gridStyle = {
  backgroundImage:
    'linear-gradient(to right, rgba(255, 255, 255, 0.045) 1px, transparent 1px), '
    + 'linear-gradient(to bottom, rgba(255, 255, 255, 0.045) 1px, transparent 1px)',
  backgroundSize: '28px 28px',
  WebkitMaskImage: 'radial-gradient(ellipse at 50% 42%, #000 0%, transparent 78%)',
  maskImage: 'radial-gradient(ellipse at 50% 42%, #000 0%, transparent 78%)',
}

const stageStyle = {
  position: 'relative',
  width: '100%',
  aspectRatio: '550 / 400',
}

const canvasStyle = {
  position: 'absolute',
  bottom: '0',
  left: '50%',
  width: '550px',
  height: '400px',
  transform: 'translateX(-50%) scale(var(--auth-char-scale, 0.78))',
  transformOrigin: 'bottom center',
}

const charBaseStyle = {
  position: 'absolute',
  bottom: '0',
  transformOrigin: 'bottom center',
  transition: 'height 700ms ease-in-out, transform 700ms ease-in-out',
}

const backBoxStyle = {
  ...charBaseStyle,
  left: '70px',
  width: '180px',
  backgroundColor: 'var(--color-brand-500)',
  borderRadius: '10px 10px 0 0',
  zIndex: '1',
}

const middleBoxStyle = {
  ...charBaseStyle,
  left: '240px',
  width: '120px',
  height: '310px',
  backgroundColor: 'var(--color-brand-200)',
  borderRadius: '8px 8px 0 0',
  zIndex: '2',
}

const frontLeftBoxStyle = {
  ...charBaseStyle,
  left: '0',
  width: '240px',
  height: '200px',
  backgroundColor: 'var(--color-accent-400)',
  borderRadius: '120px 120px 0 0',
  zIndex: '3',
}

const frontRightBoxStyle = {
  ...charBaseStyle,
  left: '310px',
  width: '140px',
  height: '230px',
  backgroundColor: 'var(--color-cream)',
  borderRadius: '70px 70px 0 0',
  zIndex: '4',
}

const eyesBaseStyle = {
  position: 'absolute',
  display: 'flex',
  transition: 'left 700ms ease-in-out, top 700ms ease-in-out',
}

const backEyesBoxStyle = { ...eyesBaseStyle, gap: '32px' }
const middleEyesBoxStyle = { ...eyesBaseStyle, gap: '24px' }
const frontLeftEyesBoxStyle = { ...eyesBaseStyle, gap: '32px', transitionDuration: '200ms' }
const frontRightEyesBoxStyle = { ...eyesBaseStyle, gap: '24px', transitionDuration: '200ms' }

const mouthBaseStyle = {
  position: 'absolute',
  width: '80px',
  height: '4px',
  borderRadius: '9999px',
  backgroundColor: 'var(--color-ink)',
  transition: 'left 200ms ease-out, top 200ms ease-out',
}

// ===== 动态样式 =====
const backStyle = computed(() => ({
  height: backTall.value ? '440px' : '400px',
  transform: coverEyes.value
    ? 'skewX(0deg)'
    : isLookingAtEachOther.value
      ? `skewX(${backPos.value.bodySkew - 12}deg) translateX(40px)`
      : props.isTyping || enteringPassword.value
        ? `skewX(${backPos.value.bodySkew - 12}deg) translateX(40px)`
        : `skewX(${backPos.value.bodySkew}deg)`,
}))

const backEyesStyle = computed(() => {
  if (coverEyes.value) return { left: '20px', top: '35px' }
  if (isLookingAtEachOther.value) return { left: '55px', top: '65px' }
  return { left: `${45 + backPos.value.faceX}px`, top: `${40 + backPos.value.faceY}px` }
})

const backLook = computed<{ x?: number; y?: number }>(() => {
  if (coverEyes.value) return isPeeking.value ? { x: 4, y: 5 } : { x: -4, y: -4 }
  if (isLookingAtEachOther.value) return { x: 3, y: 4 }
  return {}
})

const middleStyle = computed(() => ({
  transform: coverEyes.value
    ? 'skewX(0deg)'
    : isLookingAtEachOther.value
      ? `skewX(${middlePos.value.bodySkew * 1.5 + 10}deg) translateX(20px)`
      : props.isTyping || enteringPassword.value
        ? `skewX(${middlePos.value.bodySkew * 1.5}deg)`
        : `skewX(${middlePos.value.bodySkew}deg)`,
}))

const middleEyesStyle = computed(() => {
  if (coverEyes.value) return { left: '10px', top: '28px' }
  if (isLookingAtEachOther.value) return { left: '32px', top: '12px' }
  return { left: `${26 + middlePos.value.faceX}px`, top: `${32 + middlePos.value.faceY}px` }
})

const middleLook = computed<{ x?: number; y?: number }>(() => {
  if (coverEyes.value) return { x: -4, y: -4 }
  if (isLookingAtEachOther.value) return { x: 0, y: -4 }
  return {}
})

const frontLeftStyle = computed(() => ({
  transform: coverEyes.value ? 'skewX(0deg)' : `skewX(${frontLeftPos.value.bodySkew}deg)`,
}))

const frontLeftEyesStyle = computed(() => (
  coverEyes.value
    ? { left: '50px', top: '85px' }
    : { left: `${82 + frontLeftPos.value.faceX}px`, top: `${90 + frontLeftPos.value.faceY}px` }
))

const frontLeftLook = computed<{ x?: number; y?: number }>(() => (
  coverEyes.value ? { x: -5, y: -4 } : {}
))

const frontRightStyle = computed(() => ({
  transform: coverEyes.value ? 'skewX(0deg)' : `skewX(${frontRightPos.value.bodySkew}deg)`,
}))

const frontRightEyesStyle = computed(() => (
  coverEyes.value
    ? { left: '20px', top: '35px' }
    : { left: `${52 + frontRightPos.value.faceX}px`, top: `${40 + frontRightPos.value.faceY}px` }
))

const frontRightMouthStyle = computed(() => (
  coverEyes.value
    ? { left: '10px', top: '88px' }
    : { left: `${40 + frontRightPos.value.faceX}px`, top: `${88 + frontRightPos.value.faceY}px` }
))

const frontRightLook = computed<{ x?: number; y?: number }>(() => (
  coverEyes.value ? { x: -5, y: -4 } : {}
))

// ===== 生命周期 =====
onMounted(() => {
  reducedMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reducedMotion.value) return

  window.addEventListener('mousemove', onMouseMove, { passive: true })
  window.addEventListener('resize', onResize, { passive: true })
  stopBackBlink = createBlinker(backBlinking)
  stopMiddleBlink = createBlinker(middleBlinking)

  if (coverEyes.value) schedulePeek()
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('resize', onResize)
  if (frame) cancelAnimationFrame(frame)
  clearTimeout(lookTimer)
  clearPeek()
  stopBackBlink?.()
  stopMiddleBlink?.()
})
</script>

<style scoped>
/* ===== 插画画布 =====
 * 原稿是固定 550×400。这里保留原坐标（角色位置/眼睛位置都不用重算），
 * 只把整块画布按断点缩放；外层 stage 用 aspect-ratio 占位，缩放不改变布局。
 * 静态几何/底色放在内联 style（见 script 的 asideStyle / gridStyle / ...），
 * 这里只保留必须用媒体查询的断点缩放。
 */
.auth-characters {
  width: 100%;
  max-width: 550px;
}

@media (min-width: 1280px) {
  .auth-canvas {
    --auth-char-scale: 0.95;
  }
}

@media (min-width: 1536px) {
  .auth-canvas {
    --auth-char-scale: 1;
  }
}

/* 超宽屏（1920 左右）让插画跟着放大一点，否则 960px 左栏里只占 57% 会显得空 */
@media (min-width: 1700px) {
  .auth-characters {
    max-width: 640px;
  }

  .auth-canvas {
    --auth-char-scale: 1.15;
  }
}

</style>
