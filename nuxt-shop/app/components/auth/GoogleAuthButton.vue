<template>
  <!--
    Google 登录按钮。
    - 官方 branded 样式：白底、Google 四色 logo、官方要求的字重与间距
    - logo 是**内联 SVG**（不用 emoji、不引外部图片）：外部图片一来多一次请求，
      二来一旦 CDN 挂掉按钮就变成空白方块
    - 未配置时（父组件传 disabledReason）按钮禁用并给出提示，不做"点了没反应"的假按钮
  -->
  <div class="flex flex-col gap-2">
    <button
      type="button"
      class="flex h-12 w-full items-center justify-center gap-3 rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white text-sm font-bold text-[var(--color-ink)] transition-colors duration-200 hover:border-[var(--color-brand-200)] hover:bg-[var(--color-brand-50)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-500)] disabled:cursor-not-allowed disabled:opacity-55 disabled:hover:border-[var(--color-line)] disabled:hover:bg-white motion-reduce:transition-none"
      :disabled="disabled || loading"
      :aria-busy="loading"
      :aria-describedby="disabledReason ? googleHintId : undefined"
      @click="emit('click')"
    >
      <UIcon
        v-if="loading"
        name="lucide:loader-circle"
        class="h-4 w-4 shrink-0 animate-spin motion-reduce:animate-none"
        aria-hidden="true"
      />
      <svg
        v-else
        class="h-[18px] w-[18px] shrink-0"
        viewBox="0 0 48 48"
        aria-hidden="true"
        focusable="false"
      >
        <path
          fill="#EA4335"
          d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
        />
        <path
          fill="#4285F4"
          d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
        />
        <path
          fill="#FBBC05"
          d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
        />
        <path
          fill="#34A853"
          d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
        />
      </svg>
      <span>{{ label }}</span>
    </button>

    <p
      v-if="disabledReason"
      :id="googleHintId"
      class="flex items-start gap-1.5 text-xs text-[var(--color-muted)]"
    >
      <UIcon name="lucide:info" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>{{ disabledReason }}</span>
    </p>
  </div>
</template>

<script setup lang="ts">
/**
 * Google 官方 branded 登录按钮（纯展示组件，不自己发请求）。
 *
 * 为什么把请求留给父页面：三个页面里只有登录页/注册页可能放它，
 * 而"跳转前要先拿到 authUrl"这件事依赖父页面的错误处理与 loading 状态。
 * 组件只负责外观 + 禁用态，职责单一。
 */
// 不接住返回值：模板里直接按名字使用 props（label / loading / disabled / disabledReason），
// 声明成 const props 却不读它在 lint 下是 unused-vars 报错。
withDefaults(defineProps<{
  /** 按钮文案（走 i18n，由父页面传入） */
  label: string
  /** 正在获取授权地址 */
  loading?: boolean
  /** 完全禁用（例如未配置 Google 登录） */
  disabled?: boolean
  /** 禁用原因，非空时在按钮下方显示一条说明 */
  disabledReason?: string
}>(), {
  loading: false,
  disabled: false,
  disabledReason: '',
})

const emit = defineEmits<{ click: [] }>()

// 用 useId 而不是写死 id：同一页面若出现两个 Google 按钮（未来可能的变体），
// 写死的 id 会造成 aria-describedby 指错元素。
const googleHintId = `google-hint-${useId()}`
</script>
