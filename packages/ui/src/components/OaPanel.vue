<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  description?: string
  padding?: 'none' | 'sm' | 'md' | 'lg'
  elevated?: boolean
}>(), {
  padding: 'md',
  elevated: false,
})
</script>

<template>
  <section class="oa-panel" :class="[`oa-panel--${padding}`, { 'is-elevated': elevated }]">
    <header v-if="title || description || $slots.actions" class="oa-panel__header">
      <div>
        <h2 v-if="title">{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="oa-panel__actions"><slot name="actions" /></div>
    </header>
    <div class="oa-panel__body"><slot /></div>
    <footer v-if="$slots.footer" class="oa-panel__footer"><slot name="footer" /></footer>
  </section>
</template>

<style scoped>
.oa-panel {
  overflow: hidden;
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-lg);
  background: var(--oa-color-canvas);
}
.oa-panel.is-elevated { box-shadow: var(--oa-shadow-2); }
.oa-panel--sm { padding: 12px; }
.oa-panel--md { padding: 16px; }
.oa-panel--lg { padding: 24px; }
.oa-panel--none { padding: 0; }
.oa-panel__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.oa-panel__header h2 { margin: 0; font-size: 16px; font-weight: 600; letter-spacing: -.25px; }
.oa-panel__header p { margin: 5px 0 0; color: var(--oa-color-muted); font-size: 12px; line-height: 1.5; }
.oa-panel__actions { display: flex; align-items: center; gap: 8px; }
.oa-panel__footer { margin: 16px -16px -16px; padding: 12px 16px; border-top: 1px solid var(--oa-color-hairline); }
</style>
