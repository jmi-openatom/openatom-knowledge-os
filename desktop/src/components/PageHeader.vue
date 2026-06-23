<script setup lang="ts">
defineProps<{ eyebrow?: string; title: string; description?: string }>()
</script>

<template>
  <header class="page-header">
    <div>
      <span v-if="eyebrow">{{ eyebrow }}</span>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div class="page-header__actions"><slot /></div>
  </header>
</template>

<style scoped>
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 30px 32px 22px; border-bottom: 1px solid var(--oa-color-hairline); background: rgb(255 255 255 / 82%); }
.page-header span { color: var(--oa-color-muted); font-family: var(--oa-font-mono); font-size: 10px; letter-spacing: .8px; }
.page-header h1 { margin: 6px 0 0; font-size: 27px; font-weight: 600; letter-spacing: -1px; }
.page-header p { margin: 7px 0 0; color: var(--oa-color-body); font-size: 12px; }
.page-header__actions { display: flex; gap: 8px; }
</style>
