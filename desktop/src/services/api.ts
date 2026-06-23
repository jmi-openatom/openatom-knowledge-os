import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { WikiNode } from '@/types'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080/api',
  timeout: 12000,
})

apiClient.interceptors.request.use(async (config) => {
  const token = await window.openatom?.auth.getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if (error.response?.status === 401 && window.openatom) {
      const refreshed = await window.openatom.auth.refresh()
      if (refreshed && error.config) {
        const token = await window.openatom.auth.getAccessToken()
        error.config.headers.Authorization = `Bearer ${token}`
        return apiClient.request(error.config)
      }
    }
    throw error
  },
)

export async function apiRequest<T>(config: AxiosRequestConfig): Promise<T | null> {
  try {
    const response = await apiClient.request<T>(config)
    return response.data
  } catch (error) {
    const allowFallback = import.meta.env.VITE_ENABLE_MOCK_FALLBACK !== 'false'
    if (allowFallback && (!axios.isAxiosError(error) || !error.response)) return null
    if (axios.isAxiosError(error) && error.response) {
      const status = error.response.status
      const serverMessage = error.response.data?.message
      if (status === 401) throw new Error('登录已过期，请重新登录')
      if (status === 403) throw new Error('没有权限执行此操作')
      if (status === 404) throw new Error('请求的资源不存在')
      if (status === 413) throw new Error('文件过大，超过服务器限制')
      if (status >= 500) throw new Error(serverMessage || '服务器内部错误，请稍后重试')
      throw new Error(serverMessage || `请求失败（${status}）`)
    }
    throw new Error('无法连接后端服务，请确认后端已启动')
  }
}

// Wiki API functions
export async function getWikiTree(): Promise<WikiNode[] | null> {
  return apiRequest<WikiNode[]>({ method: 'GET', url: '/wiki' })
}

export async function getWikiPage(id: number): Promise<WikiNode | null> {
  return apiRequest<WikiNode>({ method: 'GET', url: `/wiki/${id}` })
}

export async function createWikiPage(data: Partial<WikiNode>): Promise<WikiNode | null> {
  return apiRequest<WikiNode>({ 
    method: 'POST', 
    url: '/wiki',
    data: {
      parentId: data.parentId ?? null,
      title: data.title,
      type: data.type ?? 'page',
      content: data.content ?? '',
      sortOrder: data.sortOrder ?? 0,
    }
  })
}

export async function updateWikiPage(id: number, data: Partial<WikiNode>): Promise<WikiNode | null> {
  return apiRequest<WikiNode>({ 
    method: 'PUT', 
    url: `/wiki/${id}`,
    data: {
      parentId: data.parentId ?? null,
      title: data.title,
      type: data.type ?? 'page',
      content: data.content ?? '',
      sortOrder: data.sortOrder ?? 0,
    }
  })
}

export async function deleteWikiPage(id: number): Promise<void> {
  await apiRequest<void>({ method: 'DELETE', url: `/wiki/${id}` })
}

// Document API helpers
export async function createDocument(data: { title?: string; content?: string }) {
  return apiRequest({ method: 'POST', url: '/documents', data })
}

export async function deleteDocument(id: number) {
  return apiRequest({ method: 'DELETE', url: `/documents/${id}` })
}

export async function getDocumentVersions(id: number) {
  return apiRequest({ method: 'GET', url: `/documents/${id}/versions` })
}

// Admin API functions
export interface Member {
  id: number
  subject: string
  name: string
  email: string
  role: 'admin' | 'leader' | 'member' | 'guest'
  avatar?: string
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
}

export async function getMembers(): Promise<Member[]> {
  const response = await apiClient.get<Member[]>('/admin/members')
  return response.data
}

export async function updateMemberRole(memberId: number, role: string): Promise<Member> {
  const response = await apiClient.patch<Member>(`/admin/members/${memberId}/role`, { role })
  return response.data
}

// AI status & test
export interface AiStatus {
  provider: string
  model: string
  configured: boolean
  baseUrl: string
}

export async function getAiStatus(): Promise<AiStatus> {
  const response = await apiClient.get<AiStatus>('/rag/status')
  return response.data
}

export async function testAiConnection(): Promise<AiStatus> {
  const response = await apiClient.post<AiStatus>('/rag/test')
  return response.data
}

export async function formatMarkdown(markdown: string): Promise<string> {
  const response = await apiClient.post<{ markdown: string }>(
    '/rag/format',
    { markdown },
    { timeout: 60000 },
  )
  return response.data?.markdown ?? markdown
}

export async function organizeToWiki(question: string, answer: string): Promise<{ title: string; markdown: string }> {
  const response = await apiClient.post<{ title: string; markdown: string }>(
    '/rag/organize',
    { question, answer },
    { timeout: 60000 },
  )
  return response.data ?? { title: question, markdown: answer }
}
