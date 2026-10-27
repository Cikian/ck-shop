<template>
  <div>
    <!--
      密码输入框。
      把"眼睛图标 + 强度条"这些在每个页面都一样的部分收在这里，
      页面只关心 v-model 与错误文案。
    -->
    <label :for="inputId" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
      {{ label }}
      <span v-if="required" class="text-[var(--color-brand-600)]" aria-hidden="true">*</span>
    </label>

    <div class="relative">
      <UIcon
        name="lucide:key-round"
        class="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-muted)]"
        aria-hidden="true"
      />
      <input
        :id="inputId"
        v-model="model"
        :name="name"
        :type="revealed ? 'text' : 'password'"
        :autocomplete="autocomplete"
        :placeholder="placeholder"
        :required="required"
        :aria-invalid="invalid || undefined"
        :aria-describedby="describedBy"
        :disabled="disabled"
        class="h-12 w-full rounded-[var(--radius-input)] border bg-white pl-10 pr-12 text-sm text-[var(--color-ink)] transition-colors duration-200 placeholder:text-[var(--color-muted)] focus:outline-none focus:ring-2 disabled:cursor-not-allowed disabled:bg-[var(--color-line-soft)] motion-reduce:transition-none"
        :class="invalid
          ? 'border-[var(--color-error-600)] focus:border-[var(--color-error-600)] focus:ring-[var(--color-error-50)]'
          : 'border-[var(--color-line)] focus:border-[var(--color-brand-400)] focus:ring-[var(--color-brand-100)]'"
        @blur="emit('blur')"
      >
      <!--
        aria-label 随状态变化（契约 4.4 明确要求）：
        屏幕阅读器只会念出 aria-label，若固定成"显示密码"，
        用户在已显示状态下听到的还是"显示密码"，无法判断当前是什么状态。
      -->
      <button
        type="button"
        class="absolute right-1.5 top-1/2 flex h-9 w-9 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full text-[var(--color-muted)] transition-colors duration-200 hover:bg-[var(--color-brand-50)] hover:text-[var(--color-brand-600)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-500)] motion-reduce:transition-none"
        :aria-label="revealed ? t('auth.hidePassword') : t('auth.showPassword')"
        :aria-pressed="revealed"
        :title="revealed ? t('auth.hidePassword') : t('auth.showPassword')"
        @click="toggleRevealed"
      >
        <UIcon :name="revealed ? 'lucide:eye-off' : 'lucide:eye'" class="h-4 w-4" aria-hidden="true" />
      </button>
    </div>

    <!-- 强度提示：只在 showStrength 且用户已输入时出现，属于"轻量提示"，不阻断提交 -->
    <!-- id 必须与 aria-describedby 里的 strengthId 一致，否则读屏拿不到这段描述 -->
    <div v-if="showStrength && model" :id="strengthId" class="mt-1.5 flex items-center gap-2">
      <div class="flex flex-1 gap-1" aria-hidden="true">
        <span
          v-for="seg in 3"
          :key="seg"
          class="h-1 flex-1 rounded-full transition-colors duration-200 motion-reduce:transition-none"
          :class="seg <= strength ? strengthBarClass : 'bg-[var(--color-line)]'"
        />
      </div>
      <span class="shrink-0 text-[11px] font-bold" :class="strengthTextClass">
        {{ strengthLabel }}
      </span>
    </div>

    <!-- 字段级错误：契约 4.4 要求保留字段级错误，故不复用顶部的通用错误条 -->
    <p
      v-if="error"
      :id="errorId"
      role="alert"
      class="mt-1.5 flex items-start gap-1.5 text-[12px] font-medium text-[var(--color-error-600)]"
    >
      <UIcon name="lucide:circle-alert" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>{{ error }}</span>
    </p>
  </div>
</template>

<script setup lang="ts">
/**
 * 带可见性切换 + 强度提示 + 字段级错误的密码输入框。
 *
 * 设计取舍：强度提示条做在输入框**下方**而不是右侧浮层 ——
 * 浮层会遮挡相邻字段，而且在小屏上不可避免要弹窗，违背"轻量、不喧宾夺主"的要求。
 */
const props = withDefaults(defineProps<{
  label: string
  /** 原生 name 属性（浏览器密码管理器 / 表单语义需要） */
  name?: string
  placeholder?: string
  autocomplete?: string
  required?: boolean
  disabled?: boolean
  /** 字段级错误文案，非空即视为 invalid */
  error?: string
  /** 是否显示密码强度条（修改密码场景不需要） */
  showStrength?: boolean
  /** 强度档位：0=空 1=弱 2=中 3=强 */
  strength?: number
  /**
   * 明文显示状态。
   *
   * 登录/注册页需要把这个状态传给左侧插画（密码可见时角色会捂眼睛），
   * 所以支持 v-model:revealed；忘记密码等页面不传时，组件内部自己维护，
   * 行为与改造前完全一致。
   */
  revealed?: boolean
}>(), {
  name: 'password',
  placeholder: '',
  autocomplete: 'current-password',
  required: true,
  disabled: false,
  error: '',
  showStrength: false,
  strength: 0,
  revealed: undefined,
})

const emit = defineEmits<{
  blur: []
  'update:revealed': [value: boolean]
}>()

const model = defineModel<string>({ required: true })

const { t } = useI18n()

// 本地状态是唯一真源：父组件传了 revealed 时同步进来（v-model:revealed），
// 没传时也能独立切换 —— 忘记密码等旧调用方不需要改。
const revealed = ref(props.revealed ?? false)

watch(() => props.revealed, (value) => {
  if (value !== undefined) revealed.value = value
})

const toggleRevealed = () => {
  revealed.value = !revealed.value
  emit('update:revealed', revealed.value)
}

const inputId = `pwd-${useId()}`
const errorId = `${inputId}-error`
const strengthId = `${inputId}-strength`

const invalid = computed(() => !!props.error)

// 错误与强度条都可能作为 aria-describedby：合并成一个字符串，
// 而不是覆盖式赋值（后者会让屏幕阅读器只读到其中一个）
const describedBy = computed(() => {
  const ids: string[] = []
  if (invalid.value) ids.push(errorId)
  if (props.showStrength && model.value) ids.push(strengthId)
  return ids.length ? ids.join(' ') : undefined
})

const strengthLabel = computed(() => {
  if (props.strength <= 1) return t('auth.strengthWeak')
  if (props.strength === 2) return t('auth.strengthMedium')
  return t('auth.strengthStrong')
})

// 颜色只是辅助：文字标签始终存在，不依赖颜色单独传达信息（WCAG 1.4.1）
const strengthBarClass = computed(() => {
  if (props.strength <= 1) return 'bg-[var(--color-error-600)]'
  if (props.strength === 2) return 'bg-[var(--color-warning-600)]'
  return 'bg-[var(--color-success-600)]'
})

const strengthTextClass = computed(() => {
  if (props.strength <= 1) return 'text-[var(--color-error-600)]'
  if (props.strength === 2) return 'text-[var(--color-warning-600)]'
  return 'text-[var(--color-success-600)]'
})
</script>
