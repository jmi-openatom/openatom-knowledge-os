import { readonly, ref } from 'vue'
import type { ToastMessage } from '../types'

const messages = ref<ToastMessage[]>([])
let sequence = 0

function removeToast(id: number) {
  messages.value = messages.value.filter((item) => item.id !== id)
}

function pushToast(input: Omit<ToastMessage, 'id'>) {
  const toast: ToastMessage = {
    id: ++sequence,
    duration: 3200,
    tone: 'neutral',
    ...input,
  }
  messages.value.push(toast)

  if (toast.duration && toast.duration > 0) {
    window.setTimeout(() => removeToast(toast.id), toast.duration)
  }
  return toast.id
}

export function useToast() {
  return {
    messages: readonly(messages),
    pushToast,
    removeToast,
  }
}
