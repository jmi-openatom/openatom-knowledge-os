<script setup lang="ts">
const props = withDefaults(defineProps<{
  modelValue?: string
  label?: string
  placeholder?: string
  rows?: number
  maxlength?: number
  disabled?: boolean
}>(), {
  modelValue: '',
  rows: 4,
  disabled: false,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const fieldId = `oa-textarea-${Math.random().toString(36).slice(2, 9)}`
</script>

<template>
  <label class="oa-textarea-field" :for="fieldId">
    <span v-if="label" class="oa-textarea-field__label">{{ label }}</span>
    <textarea
      :id="fieldId"
      class="oa-textarea oa-focus-ring"
      :value="modelValue"
      :rows="rows"
      :maxlength="maxlength"
      :placeholder="placeholder"
      :disabled="disabled"
      @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    />
    <span v-if="maxlength" class="oa-textarea-field__count">{{ props.modelValue.length }} / {{ maxlength }}</span>
  </label>
</template>

<style scoped>
.oa-textarea-field { position: relative; display: grid; gap: 7px; }
.oa-textarea-field__label { font-size: 13px; font-weight: 500; }
.oa-textarea {
  width: 100%;
  min-height: 96px;
  padding: 12px;
  resize: vertical;
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
  outline: 0;
  background: #fff;
  font-size: 14px;
  line-height: 1.6;
}
.oa-textarea:focus { border-color: #80b8f7; box-shadow: 0 0 0 3px rgb(0 112 243 / 10%); }
.oa-textarea-field__count { position: absolute; right: 10px; bottom: 8px; color: var(--oa-color-muted); font-size: 11px; }
</style>
