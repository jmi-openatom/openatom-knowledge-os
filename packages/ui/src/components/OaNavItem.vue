<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  active?: boolean
  compact?: boolean
  badge?: string | number
}>(), {
  active: false,
  compact: false,
})

defineEmits<{ click: [] }>()
</script>

<template>
  <button
    class="oa-nav-item oa-focus-ring"
    :class="{ 'is-active': active, 'is-compact': compact }"
    type="button"
    :aria-current="active ? 'page' : undefined"
    @click="$emit('click')"
  >
    <span class="oa-nav-item__indicator" aria-hidden="true" />
    <span class="oa-nav-item__icon"><slot name="icon" /></span>
    <span class="oa-nav-item__label">{{ label }}</span>
    <span v-if="badge !== undefined" class="oa-nav-item__badge">{{ badge }}</span>
  </button>
</template>

<style scoped>
.oa-nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  min-height: 44px;
  padding: 0 12px;
  overflow: hidden;
  border: 0;
  border-radius: var(--oa-radius-md);
  color: var(--oa-color-body);
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: color var(--oa-duration-fast), background var(--oa-duration-fast);
}
.oa-nav-item:hover { color: var(--oa-color-ink); background: rgb(245 247 250 / 82%); }
.oa-nav-item.is-active { color: #075db8; background: rgb(226 240 255 / 76%); }
.oa-nav-item__indicator {
  position: absolute;
  top: 10px;
  bottom: 10px;
  left: 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--oa-color-link);
  opacity: 0;
}
.oa-nav-item.is-active .oa-nav-item__indicator { opacity: 1; }
.oa-nav-item__icon { display: inline-flex; flex: none; font-size: 20px; }
.oa-nav-item__label { min-width: 0; flex: 1; overflow: hidden; font-size: 13px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.oa-nav-item__badge { min-width: 20px; padding: 1px 6px; border-radius: 99px; color: var(--oa-color-muted); background: rgb(0 0 0 / 5%); font-size: 11px; text-align: center; }
.oa-nav-item.is-compact { flex-direction: column; justify-content: center; gap: 4px; min-height: 64px; padding: 7px 4px; }
.oa-nav-item.is-compact .oa-nav-item__label { flex: none; width: 100%; font-size: 11px; text-align: center; }
.oa-nav-item.is-compact .oa-nav-item__indicator { top: 12px; bottom: 12px; }
.oa-nav-item.is-compact .oa-nav-item__badge { position: absolute; top: 5px; right: 6px; }
</style>
