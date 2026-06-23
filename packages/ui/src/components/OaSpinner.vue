<script setup lang="ts">
withDefaults(defineProps<{ size?: number; label?: string }>(), {
  size: 22,
  label: '加载中',
})
</script>

<template>
  <span class="oa-spinner" role="status" :aria-label="label" :style="{ width: `${size}px`, height: `${size}px` }" />
</template>

<style scoped>
.oa-spinner {
  display: inline-block;
  border: 2px solid var(--oa-color-hairline);
  border-top-color: var(--oa-color-ink);
  border-radius: 50%;
  animation: oa-spinner .72s linear infinite;
}
@keyframes oa-spinner { to { transform: rotate(360deg); } }
</style>
