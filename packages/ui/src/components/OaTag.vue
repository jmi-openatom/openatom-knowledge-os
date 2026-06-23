<script setup lang="ts">
import type { OaTone } from '../types'

withDefaults(defineProps<{
  tone?: OaTone
  removable?: boolean
  selected?: boolean
}>(), {
  tone: 'neutral',
  removable: false,
  selected: false,
})

defineEmits<{ remove: [] }>()
</script>

<template>
  <span class="oa-tag" :class="[`oa-tag--${tone}`, { 'is-selected': selected }]">
    <span v-if="$slots.icon" class="oa-tag__icon"><slot name="icon" /></span>
    <slot />
    <button v-if="removable" type="button" aria-label="移除标签" @click="$emit('remove')">×</button>
  </span>
</template>

<style scoped>
.oa-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 26px;
  padding: 0 9px;
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-full);
  color: var(--oa-color-body);
  background: #fff;
  font-size: 12px;
  white-space: nowrap;
}
.oa-tag.is-selected,
.oa-tag--primary { border-color: #bfdbfb; color: #075db8; background: var(--oa-color-link-soft); }
.oa-tag--success { border-color: #bce8d1; color: var(--oa-color-success); background: var(--oa-color-success-soft); }
.oa-tag--warning { border-color: #f1d59d; color: var(--oa-color-warning); background: var(--oa-color-warning-soft); }
.oa-tag--danger { border-color: #f2bbbb; color: var(--oa-color-danger); background: var(--oa-color-danger-soft); }
.oa-tag__icon { display: inline-flex; font-size: 14px; }
.oa-tag button { padding: 0; border: 0; color: currentColor; background: transparent; cursor: pointer; font-size: 16px; }
</style>
