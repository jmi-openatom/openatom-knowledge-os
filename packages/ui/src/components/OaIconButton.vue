<script setup lang="ts">
import type { OaSize } from '../types'

withDefaults(defineProps<{
  label: string
  size?: OaSize
  variant?: 'outline' | 'ghost' | 'solid'
  active?: boolean
  disabled?: boolean
}>(), {
  size: 'md',
  variant: 'ghost',
  active: false,
  disabled: false,
})
</script>

<template>
  <button
    class="oa-icon-button oa-focus-ring"
    :class="[`oa-icon-button--${size}`, `oa-icon-button--${variant}`, { 'is-active': active }]"
    :aria-label="label"
    :title="label"
    :disabled="disabled"
    type="button"
  >
    <slot />
  </button>
</template>

<style scoped>
.oa-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  border: 1px solid transparent;
  border-radius: var(--oa-radius-sm);
  color: var(--oa-color-body);
  background: transparent;
  cursor: pointer;
  transition: color var(--oa-duration-fast), background var(--oa-duration-fast), border-color var(--oa-duration-fast);
}
.oa-icon-button:hover:not(:disabled),
.oa-icon-button.is-active { color: var(--oa-color-ink); background: var(--oa-color-canvas-inset); }
.oa-icon-button--outline { border-color: var(--oa-color-hairline); background: #fff; }
.oa-icon-button--solid { border-color: var(--oa-color-ink); color: #fff; background: var(--oa-color-ink); }
.oa-icon-button--sm { width: 32px; height: 32px; font-size: 16px; }
.oa-icon-button--md { width: 40px; height: 40px; font-size: 19px; }
.oa-icon-button--lg { width: 48px; height: 48px; font-size: 22px; }
.oa-icon-button:disabled { cursor: not-allowed; opacity: .42; }
</style>
