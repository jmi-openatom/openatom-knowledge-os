<script setup lang="ts">
import { computed } from 'vue'
import type { OaSize } from '../types'

const props = withDefaults(defineProps<{
  modelValue?: string
  label?: string
  placeholder?: string
  hint?: string
  error?: string
  size?: OaSize
  type?: string
  disabled?: boolean
  clearable?: boolean
}>(), {
  modelValue: '',
  size: 'md',
  type: 'text',
  disabled: false,
  clearable: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  clear: []
}>()

const fieldId = `oa-input-${Math.random().toString(36).slice(2, 9)}`
const describedBy = computed(() => props.error || props.hint ? `${fieldId}-message` : undefined)

function clear() {
  emit('update:modelValue', '')
  emit('clear')
}
</script>

<template>
  <label class="oa-field" :for="fieldId">
    <span v-if="label" class="oa-field__label">{{ label }}</span>
    <span class="oa-input" :class="[`oa-input--${size}`, { 'has-error': error, 'is-disabled': disabled }]">
      <span v-if="$slots.prefix" class="oa-input__addon"><slot name="prefix" /></span>
      <input
        :id="fieldId"
        class="oa-input__control"
        :value="modelValue"
        :placeholder="placeholder"
        :type="type"
        :disabled="disabled"
        :aria-invalid="Boolean(error)"
        :aria-describedby="describedBy"
        @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      >
      <button
        v-if="clearable && modelValue && !disabled"
        class="oa-input__clear"
        type="button"
        aria-label="清空"
        @click="clear"
      >×</button>
      <span v-if="$slots.suffix" class="oa-input__addon"><slot name="suffix" /></span>
    </span>
    <span v-if="error || hint" :id="`${fieldId}-message`" class="oa-field__message" :class="{ 'is-error': error }">
      {{ error || hint }}
    </span>
  </label>
</template>

<style scoped>
.oa-field { display: grid; gap: 7px; color: var(--oa-color-ink); }
.oa-field__label { font-size: 13px; font-weight: 500; }
.oa-field__message { color: var(--oa-color-muted); font-size: 12px; line-height: 16px; }
.oa-field__message.is-error { color: var(--oa-color-danger); }
.oa-input {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
  background: #fff;
  transition: border-color var(--oa-duration-fast), box-shadow var(--oa-duration-fast);
}
.oa-input:focus-within { border-color: #80b8f7; box-shadow: 0 0 0 3px rgb(0 112 243 / 10%); }
.oa-input.has-error { border-color: #ef8a8a; box-shadow: 0 0 0 3px rgb(238 0 0 / 7%); }
.oa-input.is-disabled { background: var(--oa-color-canvas-inset); opacity: .72; }
.oa-input--sm { min-height: 32px; padding: 0 9px; }
.oa-input--md { min-height: 40px; padding: 0 11px; }
.oa-input--lg { min-height: 48px; padding: 0 13px; }
.oa-input__control { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; font-size: 14px; }
.oa-input__control::placeholder { color: #aaa; }
.oa-input__addon { display: inline-flex; flex: none; color: var(--oa-color-muted); font-size: 18px; }
.oa-input__clear { border: 0; color: var(--oa-color-muted); background: transparent; cursor: pointer; font-size: 20px; line-height: 1; }
</style>
