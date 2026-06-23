import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { apiClient, apiRequest, formatMarkdown } from '@/services/api'
import type { ChatAnswer, Conversation, KnowledgeFile, SourceReference } from '@/types'

const EMPTY_ANSWER: ChatAnswer = { id: 0, question: '', answer: '', sources: [], createdAt: '' }

export const useKnowledgeStore = defineStore('knowledge', () => {
  const conversations = ref<Conversation[]>([])
  const activeConversationId = ref(0)
  const files = ref<KnowledgeFile[]>([])
  const loading = ref(false)
  const error = ref('')
  const streaming = ref(false)
  const formatting = ref(false)
  const streamingAnswer = ref<ChatAnswer | null>(null)
  const history = reactive(new Map<number, ChatAnswer[]>())
  let abortController: AbortController | null = null

  async function loadConversations() {
    const data = await apiRequest<any[]>({ url: '/chat/conversations' })
    if (data) {
      conversations.value = data.map((c) => ({
        id: c.id,
        title: c.title,
        updatedAt: c.updatedAt?.replace('T', ' ').substring(5, 16) || '',
        group: '最近',
      }))
    }
  }

  async function loadConversationMessages(id: number) {
    const data = await apiRequest<any[]>({ url: `/chat/conversations/${id}/messages` })
    if (data) {
      const messages: ChatAnswer[] = data.map((m) => ({
        id: m.id,
        question: m.question,
        answer: m.answer,
        sources: m.sources || [],
        createdAt: m.createdAt?.replace('T', ' ').substring(11, 16) || '',
      }))
      history.set(id, messages)
    }
  }

  async function ask(question: string, scope: string) {
    if (abortController) abortController.abort()
    abortController = new AbortController()
    loading.value = true
    streaming.value = true
    error.value = ''

    const newAnswer: ChatAnswer = {
      id: Date.now(),
      question,
      answer: '',
      sources: [],
      createdAt: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    }
    // Show streaming answer via dedicated ref (not answer.value)
    streamingAnswer.value = newAnswer

    // Determine or create conversation
    let convId = activeConversationId.value
    if (!convId) {
      try {
        const conv = await apiRequest<any>({ method: 'POST', url: '/chat/conversations', data: { title: question.length > 22 ? question.substring(0, 22) + '…' : question } })
        if (conv) {
          convId = conv.id
          activeConversationId.value = convId
          conversations.value.unshift({ id: convId, title: conv.title, updatedAt: newAnswer.createdAt, group: '最近' })
        }
      } catch (e) {
        convId = Date.now()
        activeConversationId.value = convId
      }
    }

    try {
      const token = await window.openatom?.auth.getAccessToken()
      const headers: Record<string, string> = { 'Content-Type': 'application/json' }
      if (token) headers.Authorization = `Bearer ${token}`

      const response = await fetch(`${apiClient.defaults.baseURL}/rag/chat/stream`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ question, scope }),
        signal: abortController.signal,
      })

      if (!response.ok) {
        const errBody = await response.text().catch(() => '')
        throw new Error(`请求失败（${response.status}）${errBody ? ': ' + errBody.substring(0, 100) : ''}`)
      }

      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (trimmed === '') {
            currentEvent = ''
            continue
          }
          if (trimmed.startsWith('event:')) {
            currentEvent = trimmed.slice(6).trim()
          } else if (trimmed.startsWith('data:')) {
            const data = trimmed.slice(5).trim()
            if (currentEvent === 'sources') {
              try {
                newAnswer.sources = JSON.parse(data)
                streamingAnswer.value = { ...newAnswer }
              } catch {}
            } else if (currentEvent === 'token') {
              newAnswer.answer += data
              streamingAnswer.value = { ...newAnswer }
            } else if (currentEvent === 'error') {
              error.value = data
            }
          }
        }
      }

      // 生成完成后，调用 AI 优化一遍排版再渲染与落库
      if (newAnswer.answer.trim()) {
        formatting.value = true
        try {
          const optimized = await formatMarkdown(newAnswer.answer)
          if (optimized && optimized.trim()) {
            newAnswer.answer = optimized
            streamingAnswer.value = { ...newAnswer }
          }
        } catch {
          // 优化失败则保留原文
        } finally {
          formatting.value = false
        }
      }

      // Add to local history
      const list = history.get(convId) || []
      history.set(convId, [...list, newAnswer])

      // Persist to backend
      try {
        await apiRequest({ method: 'POST', url: `/chat/conversations/${convId}/messages`, data: { question, answer: newAnswer.answer, sources: newAnswer.sources, title: question.length > 22 ? question.substring(0, 22) + '…' : question } })
      } catch (e) {
        // Persistence failed but answer is shown
      }

      // Update conversation list
      const conv = conversations.value.find((c) => c.id === convId)
      if (conv) conv.updatedAt = newAnswer.createdAt

      activeConversationId.value = convId
    } catch (e: any) {
      if (e.name !== 'AbortError') {
        if (e instanceof TypeError && e.message.includes('fetch')) {
          error.value = '无法连接后端服务，请确认后端已启动（http://127.0.0.1:8080）'
        } else {
          error.value = e instanceof Error ? e.message : 'AI 问答请求失败'
        }
      }
    } finally {
      loading.value = false
      streaming.value = false
      streamingAnswer.value = null
      abortController = null
    }
  }

  function cancelStream() {
    abortController?.abort()
  }

  async function loadConversation(id: number) {
    activeConversationId.value = id
    const historyList = history.get(id)
    if (!historyList && id) {
      await loadConversationMessages(id)
    }
  }

  async function loadFiles() {
    const response = await apiRequest<KnowledgeFile[]>({ url: '/files' })
    if (response) files.value = response
  }

  async function uploadFile(file: File, tags: string[] = []) {
    const form = new FormData()
    form.append('file', file)
    form.append('tags', tags.join(','))
    const response = await apiRequest<KnowledgeFile>({
      method: 'POST',
      url: '/files',
      data: form,
    })
    if (!response) throw new Error('无法连接后端服务，请确认后端已启动')
    files.value = [response, ...files.value.filter((item) => item.id !== response.id)]
    return response
  }

  async function deleteFile(id: number) {
    await apiRequest<void>({ method: 'DELETE', url: `/files/${id}` })
    files.value = files.value.filter((item) => item.id !== id)
  }

  async function createConversation() {
    activeConversationId.value = 0
  }

  async function deleteConversation(id: number) {
    await apiRequest<void>({ method: 'DELETE', url: `/chat/conversations/${id}` })
    conversations.value = conversations.value.filter((c) => c.id !== id)
    history.delete(id)
    if (activeConversationId.value === id) {
      activeConversationId.value = 0
    }
  }

  return { conversations, activeConversationId, files, loading, streaming, formatting, streamingAnswer, error, history, ask, cancelStream, loadConversation, loadFiles, loadConversations, uploadFile, deleteFile, createConversation, deleteConversation }
})
