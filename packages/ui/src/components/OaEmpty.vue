<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  description?: string
}>(), {
  title: '暂无内容',
  description: '这里还没有可以显示的数据。',
})
</script>

<template>
  <div class="oa-empty">
    <div class="oa-empty__icon"><slot name="icon" /></div>
    <strong>{{ title }}</strong>
    <p>{{ description }}</p>
    <div v-if="$slots.default" class="oa-empty__action"><slot /></div>
  </div>
</template>

<style scoped>
.oa-empty { display: grid; min-height: 220px; padding: 32px; color: var(--oa-color-body); text-align: center; place-content: center; justify-items: center; }
.oa-empty__icon { display: grid; width: 48px; height: 48px; margin-bottom: 14px; border: 1px solid var(--oa-color-hairline); border-radius: 14px; color: var(--oa-color-muted); background: var(--oa-color-canvas-soft); font-size: 23px; place-items: center; }
.oa-empty strong { color: var(--oa-color-ink); font-size: 15px; }
.oa-empty p { max-width: 320px; margin: 7px 0 0; color: var(--oa-color-muted); font-size: 13px; line-height: 1.6; }
.oa-empty__action { margin-top: 18px; }
</style>
