<script setup lang="ts">
import type { OaSize, OaTone } from '../types'

withDefaults(defineProps<{
  variant?: 'solid' | 'outline' | 'ghost' | 'soft'
  size?: OaSize
  tone?: OaTone
  type?: 'button' | 'submit' | 'reset'
  loading?: boolean
  disabled?: boolean
  block?: boolean
}>(), {
  variant: 'solid',
  size: 'md',
  tone: 'neutral',
  type: 'button',
  loading: false,
  disabled: false,
  block: false,
})
</script>

<template>
  <button
    class="oa-button oa-focus-ring"
    :class="[`oa-button--${variant}`, `oa-button--${size}`, `oa-button--${tone}`, { 'is-block': block }]"
    :type="type"
    :disabled="disabled || loading"
  >
    <span v-if="loading" class="oa-button__spinner" aria-hidden="true" />
    <span v-else-if="$slots.prefix" class="oa-button__icon"><slot name="prefix" /></span>
    <span class="oa-button__label"><slot /></span>
    <span v-if="$slots.suffix" class="oa-button__icon"><slot name="suffix" /></span>
  </button>
</template>

<style scoped>
.oa-button {
  --button-bg: var(--oa-color-primary);
  --button-fg: var(--oa-color-primary-foreground);
  --button-border: var(--oa-color-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: auto;
  border: 1px solid var(--button-border);
  border-radius: var(--oa-radius-sm);
  color: var(--button-fg);
  background: var(--button-bg);
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: transform var(--oa-duration-fast) var(--oa-ease), box-shadow var(--oa-duration-fast), background var(--oa-duration-fast);
}
.oa-button:hover:not(:disabled) { box-shadow: var(--oa-shadow-2); transform: translateY(-1px); }
.oa-button:active:not(:disabled) { box-shadow: none; transform: translateY(0); }
.oa-button:disabled { cursor: not-allowed; opacity: .48; }
.oa-button--sm { min-height: 32px; padding: 0 11px; font-size: 13px; }
.oa-button--md { min-height: 40px; padding: 0 15px; font-size: 14px; }
.oa-button--lg { min-height: 48px; padding: 0 20px; border-radius: var(--oa-radius-md); font-size: 16px; }
.oa-button--outline { --button-bg: #fff; --button-fg: var(--oa-color-ink); --button-border: var(--oa-color-hairline); }
.oa-button--ghost { --button-bg: transparent; --button-fg: var(--oa-color-body); --button-border: transparent; }
.oa-button--ghost:hover:not(:disabled) { background: var(--oa-color-canvas-inset); box-shadow: none; }
.oa-button--soft { --button-bg: var(--oa-color-canvas-inset); --button-fg: var(--oa-color-ink); --button-border: transparent; }
.oa-button--primary { --button-bg: var(--oa-color-link); --button-fg: #fff; --button-border: var(--oa-color-link); }
.oa-button--success { --button-bg: var(--oa-color-success); --button-fg: #fff; --button-border: var(--oa-color-success); }
.oa-button--warning { --button-bg: var(--oa-color-warning); --button-fg: #fff; --button-border: var(--oa-color-warning); }
.oa-button--danger { --button-bg: var(--oa-color-danger); --button-fg: #fff; --button-border: var(--oa-color-danger); }
.oa-button.is-block { width: 100%; }
.oa-button__icon { display: inline-flex; font-size: 18px; }
.oa-button__spinner {
  width: 15px;
  height: 15px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: oa-spin .7s linear infinite;
}
@keyframes oa-spin { to { transform: rotate(360deg); } }
</style>
