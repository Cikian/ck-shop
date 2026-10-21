<template>
  <!--
    商品图片（原生 img + 加载失败兜底）。

    为什么不直接用 <img>：外链封面/轮播图会 403、会超时、会被 CDN 下线，
    默认行为是浏览器"碎图 + alt 文本"，在商品网格里非常刺眼。
    这里统一把失败态换成一个中性占位图标，并且 src 变化时自动复位失败标记
    （同一张 <img> 换商品后不该一直停在占位态）。

    为什么不用 NuxtImg：项目约定（见任务）不引入新图片管线/依赖，
    响应式 srcset 需要 image provider 配置，属于全局改造，不在组件层解决。

    class 透传：根元素是 img 或占位 span，两种情况下 class 都会合并到根上，
    调用方按原来的写法写尺寸/圆角/object-cover 即可。
  -->
  <img
    v-if="src && !failed"
    v-bind="$attrs"
    :src="src"
    :alt="alt"
    :width="width"
    :height="height"
    :loading="loading"
    :fetchpriority="fetchpriority"
    @error="failed = true"
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
  icon: 'lucide:image-off',
  iconClass: 'h-6 w-6',
})

/** 加载失败标记：src 变化时复位，避免"换了一张图还显示占位" */
const failed = ref(false)
watch(() => props.src, () => {
  failed.value = false
})
</script>
