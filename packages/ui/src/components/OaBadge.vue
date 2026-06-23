<script setup lang="ts">
import type { OaTone } from '../types'

withDefaults(defineProps<{ tone?: OaTone; dot?: boolean }>(), {
  tone: 'neutral',
  dot: false,
})
</script>

<template>
  <span class="oa-badge" :class="`oa-badge--${tone}`">
    <span v-if="dot" class="oa-badge__dot" />
    <slot />
  </span>
</template>

<style scoped>
.oa-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 22px;
  padding: 0 7px;
  border-radius: var(--oa-radius-full);
  color: var(--oa-color-body);
  background: var(--oa-color-canvas-inset);
  font-size: 11px;
  font-weight: 500;
}
.oa-badge__dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.oa-badge--primary { color: #075db8; background: var(--oa-color-link-soft); }
.oa-badge--success { color: var(--oa-color-success); background: var(--oa-color-success-soft); }
.oa-badge--warning { color: var(--oa-color-warning); background: var(--oa-color-warning-soft); }
.oa-badge--danger { color: var(--oa-color-danger); background: var(--oa-color-danger-soft); }
</style>
