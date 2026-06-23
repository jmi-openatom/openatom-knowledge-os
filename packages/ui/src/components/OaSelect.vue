<script setup lang="ts">
import type { SelectOption } from '../types'

withDefaults(defineProps<{
  modelValue?: string | number
  options: SelectOption[]
  label?: string
  placeholder?: string
  disabled?: boolean
}>(), {
  placeholder: '请选择',
  disabled: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const fieldId = `oa-select-${Math.random().toString(36).slice(2, 9)}`
</script>

<template>
  <label class="oa-select-field" :for="fieldId">
    <span v-if="label" class="oa-select-field__label">{{ label }}</span>
    <span class="oa-select-wrap">
      <select
        :id="fieldId"
        class="oa-select"
        :value="modelValue"
        :disabled="disabled"
        @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
      >
        <option value="" disabled>{{ placeholder }}</option>
        <option v-for="option in options" :key="option.value" :value="option.value" :disabled="option.disabled">
          {{ option.label }}
        </option>
      </select>
      <span class="oa-select__chevron">⌄</span>
    </span>
  </label>
</template>

<style scoped>
.oa-select-field { display: grid; gap: 7px; }
.oa-select-field__label { font-size: 13px; font-weight: 500; }
.oa-select-wrap { position: relative; display: block; }
.oa-select {
  width: 100%;
  height: 40px;
  padding: 0 34px 0 11px;
  appearance: none;
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
  outline: 0;
  background: #fff;
  cursor: pointer;
}
.oa-select:focus { border-color: #80b8f7; box-shadow: 0 0 0 3px rgb(0 112 243 / 10%); }
.oa-select__chevron { position: absolute; top: 50%; right: 12px; color: var(--oa-color-muted); pointer-events: none; transform: translateY(-58%); }
</style>
