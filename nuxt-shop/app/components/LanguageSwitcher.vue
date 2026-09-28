<template>
  <select v-model="current" @change="onChange" class="border rounded px-2 py-1 text-sm">
    <option v-for="loc in locales" :key="loc.code" :value="loc.code">
      {{ loc.name }}
    </option>
  </select>
</template>

<script setup lang="ts">
const { locale, locales } = useI18n()
const switchLocalePath = useSwitchLocalePath()
const router = useRouter()
const current = ref(locale.value)

function onChange() {
  router.push(switchLocalePath(current.value))
}
</script>
