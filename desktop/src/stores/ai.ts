import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getAiStatus, testAiConnection, type AiStatus } from '@/services/api'

export const useAiStore = defineStore('ai', () => {
  const status = ref<AiStatus | null>(null)
  const loading = ref(false)
  const testing = ref(false)
  const error = ref('')

  async function load() {
    loading.value = true
    error.value = ''
    try {
      status.value = await getAiStatus()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '无法获取 AI 状态'
      status.value = null
    } finally {
      loading.value = false
    }
  }

  async function test(): Promise<AiStatus | null> {
    testing.value = true
    error.value = ''
    try {
      const result = await testAiConnection()
      status.value = result
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'AI 连接测试失败'
      return null
    } finally {
      testing.value = false
    }
  }

  return { status, loading, testing, error, load, test }
})
