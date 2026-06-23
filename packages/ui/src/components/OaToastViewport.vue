<script setup lang="ts">
import { useToast } from '../composables/useToast'

const { messages, removeToast } = useToast()
</script>

<template>
  <Teleport to="body">
    <div class="oa-toast-viewport" aria-live="polite">
      <TransitionGroup name="oa-toast">
        <article v-for="message in messages" :key="message.id" class="oa-toast" :class="`oa-toast--${message.tone}`">
          <span class="oa-toast__marker" />
          <div>
            <strong>{{ message.title }}</strong>
            <p v-if="message.description">{{ message.description }}</p>
          </div>
          <button type="button" aria-label="关闭通知" @click="removeToast(message.id)">×</button>
        </article>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.oa-toast-viewport { position: fixed; z-index: 1200; top: 18px; right: 18px; display: grid; gap: 10px; width: min(360px, calc(100vw - 36px)); pointer-events: none; }
.oa-toast { display: grid; grid-template-columns: 4px 1fr auto; gap: 12px; padding: 14px; border: 1px solid var(--oa-color-hairline); border-radius: var(--oa-radius-md); background: rgb(255 255 255 / 94%); box-shadow: var(--oa-shadow-3); backdrop-filter: blur(18px); pointer-events: auto; }
.oa-toast__marker { width: 4px; height: 100%; min-height: 32px; border-radius: 4px; background: var(--oa-color-ink); }
.oa-toast--primary .oa-toast__marker { background: var(--oa-color-link); }
.oa-toast--success .oa-toast__marker { background: var(--oa-color-success); }
.oa-toast--warning .oa-toast__marker { background: var(--oa-color-warning); }
.oa-toast--danger .oa-toast__marker { background: var(--oa-color-danger); }
.oa-toast strong { font-size: 13px; }
.oa-toast p { margin: 4px 0 0; color: var(--oa-color-muted); font-size: 12px; line-height: 1.45; }
.oa-toast button { align-self: start; border: 0; color: var(--oa-color-muted); background: transparent; cursor: pointer; font-size: 18px; }
.oa-toast-enter-active, .oa-toast-leave-active { transition: all .2s var(--oa-ease); }
.oa-toast-enter-from, .oa-toast-leave-to { opacity: 0; transform: translateX(12px); }
</style>
