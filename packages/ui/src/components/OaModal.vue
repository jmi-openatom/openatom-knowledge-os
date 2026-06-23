<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  description?: string
  width?: number
  closeOnMask?: boolean
}>(), {
  width: 520,
  closeOnMask: true,
})

const emit = defineEmits<{ close: [] }>()

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) emit('close')
}

watch(() => props.open, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})
window.addEventListener('keydown', onKeydown)
onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="oa-modal">
      <div v-if="open" class="oa-modal-layer" role="presentation" @mousedown.self="closeOnMask && emit('close')">
        <section class="oa-modal" role="dialog" aria-modal="true" :aria-label="title" :style="{ width: `${width}px` }">
          <header class="oa-modal__header">
            <div>
              <h2>{{ title }}</h2>
              <p v-if="description">{{ description }}</p>
            </div>
            <button type="button" aria-label="关闭" @click="emit('close')">×</button>
          </header>
          <div class="oa-modal__body"><slot /></div>
          <footer v-if="$slots.footer" class="oa-modal__footer"><slot name="footer" /></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.oa-modal-layer {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  padding: 24px;
  background: rgb(15 20 28 / 34%);
  backdrop-filter: blur(6px);
  place-items: center;
}
.oa-modal { max-width: 100%; overflow: hidden; border: 1px solid rgb(255 255 255 / 80%); border-radius: var(--oa-radius-lg); background: #fff; box-shadow: var(--oa-shadow-modal); }
.oa-modal__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding: 20px 22px 16px; border-bottom: 1px solid var(--oa-color-hairline); }
.oa-modal__header h2 { margin: 0; font-size: 18px; letter-spacing: -.35px; }
.oa-modal__header p { margin: 5px 0 0; color: var(--oa-color-muted); font-size: 12px; }
.oa-modal__header button { width: 30px; height: 30px; border: 0; border-radius: 6px; color: var(--oa-color-muted); background: transparent; cursor: pointer; font-size: 22px; }
.oa-modal__header button:hover { color: var(--oa-color-ink); background: var(--oa-color-canvas-inset); }
.oa-modal__body { padding: 22px; }
.oa-modal__footer { display: flex; justify-content: flex-end; gap: 8px; padding: 14px 22px; border-top: 1px solid var(--oa-color-hairline); background: var(--oa-color-canvas-soft); }
.oa-modal-enter-active, .oa-modal-leave-active { transition: opacity .18s; }
.oa-modal-enter-active .oa-modal, .oa-modal-leave-active .oa-modal { transition: transform .18s var(--oa-ease); }
.oa-modal-enter-from, .oa-modal-leave-to { opacity: 0; }
.oa-modal-enter-from .oa-modal, .oa-modal-leave-to .oa-modal { transform: translateY(8px) scale(.985); }
</style>
