<script setup lang="ts">
withDefaults(defineProps<{
  src?: string
  name: string
  size?: number
  status?: 'online' | 'busy' | 'offline'
}>(), {
  size: 36,
  status: undefined,
})

function initials(name: string) {
  return name.trim().slice(0, 2).toUpperCase()
}
</script>

<template>
  <span class="oa-avatar" :style="{ width: `${size}px`, height: `${size}px` }" :title="name">
    <img v-if="src" :src="src" :alt="name">
    <span v-else class="oa-avatar__fallback">{{ initials(name) }}</span>
    <span v-if="status" class="oa-avatar__status" :class="`is-${status}`" />
  </span>
</template>

<style scoped>
.oa-avatar {
  position: relative;
  display: inline-grid;
  flex: none;
  overflow: visible;
  border: 1px solid rgb(0 0 0 / 7%);
  border-radius: 50%;
  background: #eaf1f8;
  place-items: center;
}
.oa-avatar img { width: 100%; height: 100%; border-radius: inherit; object-fit: cover; }
.oa-avatar__fallback { color: #30445a; font-size: 11px; font-weight: 600; }
.oa-avatar__status {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 9px;
  height: 9px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #aaa;
}
.oa-avatar__status.is-online { background: #1c9d68; }
.oa-avatar__status.is-busy { background: #e14b4b; }
</style>
