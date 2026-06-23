<script setup lang="ts">
withDefaults(defineProps<{ label?: string; vertical?: boolean }>(), {
  vertical: false,
})
</script>

<template>
  <div class="oa-divider" :class="{ 'is-vertical': vertical, 'has-label': label }" role="separator">
    <span v-if="label">{{ label }}</span>
  </div>
</template>

<style scoped>
.oa-divider { display: flex; align-items: center; width: 100%; height: 1px; margin: 12px 0; background: var(--oa-color-hairline); }
.oa-divider.has-label { gap: 10px; height: auto; color: var(--oa-color-muted); background: none; font-size: 11px; }
.oa-divider.has-label::before,
.oa-divider.has-label::after { height: 1px; flex: 1; background: var(--oa-color-hairline); content: ""; }
.oa-divider.is-vertical { width: 1px; height: 24px; margin: 0 12px; }
</style>
