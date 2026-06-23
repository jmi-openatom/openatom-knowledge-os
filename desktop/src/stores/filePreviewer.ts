import { ref } from 'vue'
import { defineStore } from 'pinia'
import { apiClient, apiRequest } from '@/services/api'
import type { FilePreview } from '@/types'

export const useFilePreviewerStore = defineStore('filePreviewer', () => {
  const open = ref(false)
  const loading = ref(false)
  const preview = ref<FilePreview | null>(null)
  const error = ref('')

  async function show(fileId: number) {
    // In Electron, open a dedicated preview window
    if (window.openatom?.preview) {
      window.openatom.preview.open(fileId)
      return
    }
    // Web fallback: use modal
    open.value = true
    loading.value = true
    error.value = ''
    preview.value = null
    try {
      const data = await apiRequest<FilePreview>({ url: `/files/${fileId}/preview` })
      if (data) {
        preview.value = data
      } else {
        error.value = '无法加载文件预览，请确认后端服务已启动'
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : '预览加载失败'
    } finally {
      loading.value = false
    }
  }

  function close() {
    open.value = false
    preview.value = null
    error.value = ''
  }

  async function download(fileId: number, fileName: string) {
    const response = await apiClient.get(`/files/${fileId}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fileName
    anchor.click()
    URL.revokeObjectURL(url)
  }

  return { open, loading, preview, error, show, close, download }
})
