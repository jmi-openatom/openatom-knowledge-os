<script setup lang="ts">
withDefaults(defineProps<{
  width?: number
  compact?: boolean
}>(), {
  width: 88,
  compact: false,
})
</script>

<template>
  <aside
    class="oa-glass-sidebar"
    :class="{ 'is-compact': compact }"
    :style="{ '--sidebar-width': `${width}px` }"
  >
    <div class="oa-glass-sidebar__wash" aria-hidden="true" />
    <header class="oa-glass-sidebar__header"><slot name="brand" /></header>
    <nav class="oa-glass-sidebar__nav" aria-label="主导航"><slot /></nav>
    <footer class="oa-glass-sidebar__footer"><slot name="footer" /></footer>
  </aside>
</template>

<style scoped>
.oa-glass-sidebar {
  --sidebar-width: 88px;
  position: relative;
  z-index: 20;
  display: flex;
  flex: 0 0 var(--sidebar-width);
  flex-direction: column;
  width: var(--sidebar-width);
  min-height: 100%;
  overflow: hidden;
  border-right: 1px solid rgb(225 228 233 / 92%);
  background: rgb(255 255 255 / 82%);
  box-shadow: 2px 0 12px rgb(20 30 50 / 3%), 8px 0 28px -18px rgb(20 30 50 / 20%);
  backdrop-filter: blur(26px) saturate(155%);
  -webkit-backdrop-filter: blur(26px) saturate(155%);
}
.oa-glass-sidebar__wash {
  position: absolute;
  inset: 0;
  z-index: -1;
  background: rgb(250 252 255 / 34%);
  pointer-events: none;
}
.oa-glass-sidebar__header {
  display: grid;
  min-height: 92px;
  place-items: center;
  padding: 14px 10px;
}
.oa-glass-sidebar__nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 5px;
  padding: 8px;
}
.oa-glass-sidebar__footer {
  display: grid;
  gap: 8px;
  padding: 12px 8px 16px;
}
</style>
