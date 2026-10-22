<template>
  <!--
    商品图片（原生 img + 响应式 srcset + 加载失败兜底）。

    为什么不直接用 <img>：外链封面/轮播图会 403、会超时、会被 CDN 下线，
    默认行为是浏览器"碎图 + alt 文本"，在商品网格里非常刺眼。
    这里统一把失败态换成一个中性占位图标，并且 src 变化时自动复位失败标记
    （同一张 <img> 换商品后不该一直停在占位态）。

    响应式：http(s) 图片默认走 BFF 的 /api/img?src=&w= 管线（sharp 缩放 + WebP），
    输出 srcset/sizes。宽度候选值按 width 属性推导（缩略图不会生成 1280 的大图）。
    data: / 相对路径 等无法安全缩放的图片自动跳过，保持原样。

    class 透传：根元素是 img 或占位 span，两种情况下 class 都会合并到根上，
    调用方按原来的写法写尺寸/圆角/object-cover 即可。
  -->
  <img
    v-if="src && !failed"
    v-bind="$attrs"
    :src="displaySrc"
    :srcset="srcset"
    :sizes="sizes"
    :alt="alt"
    :width="width"
    :height="height"
    :loading="loading"
    :fetchpriority="fetchpriority"
    decoding="async"
    @error="handleError"
  >
  <span
    v-else
    v-bind="$attrs"
    role="img"
    :aria-label="alt"
    class="flex items-center justify-center"
  >
    <UIcon :name="icon" :class="iconClass" aria-hidden="true" />
  </span>
</template>

<script setup lang="ts">
defineOptions({ inheritAttrs: false })

const props = withDefaults(defineProps<{
  /** 图片地址；空值直接渲染占位（不请求坏地址） */
  src?: string | null
  /** alt 文案；同时作为占位态的 aria-label，失败时读屏仍知道这里是什么图 */
  alt: string
  width?: number | string
  height?: number | string
  loading?: 'lazy' | 'eager'
  fetchpriority?: 'high' | 'low' | 'auto'
  /** 响应式布局下的显示宽度描述，例如 "(max-width: 640px) 50vw, 25vw" */
  sizes?: string
  /** 关闭响应式管线（头像、SVG、data URI 等场景可显式关闭） */
  responsive?: boolean
  /** 占位图标（默认"图片缺失"） */
  icon?: string
  /** 占位图标尺寸/颜色；头像等场景可覆盖成更小尺寸并继承父级文字色 */
  iconClass?: string
}>(), {
  src: '',
  width: undefined,
  height: undefined,
  loading: 'lazy',
  fetchpriority: 'auto',
  sizes: undefined,
  responsive: true,
  icon: 'lucide:image-off',
  iconClass: 'h-6 w-6',
})

/** 加载失败标记：src 变化时复位，避免"换了一张图还显示占位" */
const failed = ref(false)
/** 缩放接口失败时退回原图（只试一次，原图也失败才显示占位） */
const useOriginal = ref(false)

watch(() => props.src, () => {
  failed.value = false
  useOriginal.value = false
})

/** 只有 http(s) 的远程图片才走缩放接口（相对路径可能是本地静态资源） */
const canResize = computed(() => props.responsive !== false && /^https?:\/\//i.test(props.src || ''))

/** 宽度候选：有 width 时按 1x/2x 输出，否则给一组常用档位 */
const candidateWidths = computed(() => {
  const declared = Number(props.width)
  if (Number.isFinite(declared) && declared > 0) {
    const widths = [declared, declared * 2]
      .map(value => Math.min(1600, Math.round(value)))
      .filter(value => value >= 16)
    return [...new Set(widths)].sort((a, b) => a - b)
  }
  return [240, 480, 720, 960, 1280]
})

function resizedUrl(width: number): string {
  return `/api/img?src=${encodeURIComponent(props.src || '')}&w=${width}`
}

/** 默认 src 取中间档（640 附近），兼顾无 srcset 的浏览器与首屏体积 */
const displaySrc = computed(() => {
  if (!canResize.value || useOriginal.value) {
    return props.src || ''
  }
  const widths = candidateWidths.value
  const fallback = widths.find(width => width >= 640) ?? widths[widths.length - 1] ?? 640
  return resizedUrl(fallback)
})

const srcset = computed(() => {
  if (!canResize.value || useOriginal.value) {
    return undefined
  }
  return candidateWidths.value.map(width => `${resizedUrl(width)} ${width}w`).join(', ')
})

function handleError() {
  if (canResize.value && !useOriginal.value) {
    useOriginal.value = true
    return
  }
  failed.value = true
}
</script>
