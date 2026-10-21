<template>
  <!--
    统一异步按钮。

    为什么要有它：同样的「点了要等一下」在项目里出现了三种写法 ——
    换文案 + 转圈、只换文案、只 disabled。只 disabled 的那几处用户会以为点击丢了，
    于是连点，反而制造重复请求。把 pending 的三件事（aria-busy / disabled / 视觉反馈）
    收在一个组件里，调用方只负责说"我在等"。

    契约（全站唯一）：
      - pending 时：显示 loader-circle（尊重 prefers-reduced-motion）+ pendingLabel（可选）+ disabled + aria-busy；
      - pending 时默认插槽不渲染（避免"旧文案 + 转圈"这种半吊子状态）；
      - 没有 pendingLabel 时只显示转圈（图标按钮用，例如收藏/刷新验证码）；
      - class 由调用方透传（Vue 自动合并到根 button），本组件不预设任何尺寸/配色；
      - 本组件**不依赖 i18n**：文案一律由调用方传入。
  -->
  <button
    :type="type"
    :disabled="disabled || pending"
    :aria-busy="pending ? 'true' : undefined"
    class="inline-flex items-center justify-center gap-2"
  >
    <template v-if="pending">
      <UIcon
        name="lucide:loader-circle"
        class="h-4 w-4 shrink-0 animate-spin motion-reduce:animate-none"
        aria-hidden="true"
      />
      <span v-if="pendingLabel">{{ pendingLabel }}</span>
    </template>
    <slot v-else />
  </button>
</template>

<script setup lang="ts">
/**
 * 异步按钮。
 *
 * 注意 type 的默认值是 'button'：本项目绝大多数按钮不是表单提交，
 * 默认 submit 会在任何一层 <form> 里意外触发表单提交（登录/地址/密码表单都有 form）。
 */
withDefaults(defineProps<{
  /** 请求进行中：转圈 + 禁用 + aria-busy */
  pending?: boolean
  /** 进行中文案（不传则只显示转圈，适合纯图标按钮） */
  pendingLabel?: string
  /** 常规禁用（与 pending 互不影响，任一为 true 都禁用） */
  disabled?: boolean
  /** 原生 type；表单里的提交按钮传 'submit' */
  type?: 'button' | 'submit'
}>(), {
  pending: false,
  pendingLabel: '',
  disabled: false,
  type: 'button',
})
</script>
