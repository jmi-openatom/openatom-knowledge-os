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

// ---- Management proxy (activities & notifications) ----
// These endpoints proxy to the external openatom-system, which wraps responses in a
// Result<T> envelope: { code, message, data, traceId }. We unwrap and surface business
// errors as thrown Error messages.

interface Result<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageData<T> {
  list: T[]
  page: number
  pageSize: number
  total: number
}

export interface Activity {
  id: number
  title: string
  summary?: string
  descriptionMarkdown?: string
  activityAt?: string
  endAt?: string
  location?: string
  status: 'draft' | 'published' | 'closed'
  coverUrl?: string
  registrationRequired?: boolean
  registrationStartAt?: string
  registrationEndAt?: string
  participationPoints?: number
  createdAt?: string
}

export interface NotificationItem {
  id: number
  title: string
  content: string
  type?: string
  isAll?: boolean
  receiverUserIds?: number[]
  createdAt?: string
}

async function managementRequest<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.request<Result<T>>(config)
  const result = response.data
  if (result && result.code !== 0) {
    throw new Error(result.message || '操作失败')
  }
  return (result?.data ?? (null as unknown)) as T
}

// Normalize list responses that may be either a direct array or a PageDataVO object.
function normalizePage<T>(raw: unknown): PageData<T> {
  if (Array.isArray(raw)) {
    return { list: raw as T[], page: 1, pageSize: raw.length, total: raw.length }
  }
  if (raw && typeof raw === 'object' && Array.isArray((raw as PageData<T>).list)) {
    return raw as PageData<T>
  }
  return { list: [], page: 1, pageSize: 0, total: 0 }
}

// Activities
export async function getActivities(params?: {
  keyword?: string
  status?: string
  page?: number
  pageSize?: number
}): Promise<PageData<Activity> | null> {
  const raw = await managementRequest<unknown>({ method: 'GET', url: '/management/activities', params })
  return normalizePage<Activity>(raw)
}

export async function getActivity(id: number): Promise<Activity | null> {
  return managementRequest<Activity>({ method: 'GET', url: `/management/activities/${id}` })
}

export async function createActivity(data: Partial<Activity>): Promise<Activity | null> {
  return managementRequest<Activity>({ method: 'POST', url: '/management/activities', data })
}

export async function updateActivity(id: number, data: Partial<Activity>): Promise<Activity | null> {
  return managementRequest<Activity>({ method: 'PATCH', url: `/management/activities/${id}`, data })
}

export async function deleteActivity(id: number): Promise<void> {
  await managementRequest<unknown>({ method: 'DELETE', url: `/management/activities/${id}` })
}

// Notifications
export async function getAdminNotifications(params?: {
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<PageData<NotificationItem> | null> {
  const raw = await managementRequest<unknown>({
    method: 'GET',
    url: '/management/notifications/admin',
    params,
  })
  return normalizePage<NotificationItem>(raw)
}

export async function sendNotification(data: {
  title: string
  content: string
  type?: string
  isAll?: boolean
  receiverUserIds?: number[]
}): Promise<NotificationItem | null> {
  return managementRequest<NotificationItem>({
    method: 'POST',
    url: '/management/notifications/admin',
    data,
  })
}

export async function deleteNotification(id: number): Promise<void> {
  await managementRequest<unknown>({ method: 'DELETE', url: `/management/notifications/admin/${id}` })
}

export async function getUnreadNotificationCount(): Promise<number> {
  const result = await managementRequest<number>({ method: 'GET', url: '/management/notifications/unread-count' })
  return typeof result === 'number' ? result : 0
}

export async function markNotificationRead(id: number): Promise<void> {
  await managementRequest<unknown>({ method: 'POST', url: `/management/notifications/${id}/read` })
}

// ---- Forms (站点表单) ----
// Matches the external system schema:
//   - form uses `name` (not title), `formSchema` (JSON string, not fields array)
//   - status: open / closed
//   - field uses `key`/`label` (not name), options: [{label, value}]

export interface FormFieldOption {
  label: string
  value: string
}

export interface FormField {
  key?: string
  type: 'text' | 'textarea' | 'select' | 'number' | 'date' | 'phone' | 'email'
  label?: string
  required?: boolean
  options?: string[] | FormFieldOption[]
  placeholder?: string
}

export interface SiteForm {
  id: number
  name?: string
  status?: 'open' | 'closed'
  formSchema?: string
  startAt?: string
  endAt?: string
  loginRequired?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface FormSubmission {
  id: number
  formId?: number
  submitterName?: string
  data?: Record<string, unknown>
  formData?: Record<string, unknown>
  createdAt?: string
}

// Get a unified form title from the API's `name` field.
export function formTitle(form: SiteForm): string {
  return form.name || '未命名表单'
}

// Get a unified field list regardless of whether the API used `fields` or `formSchema` (JSON string).
export function formFields(form: SiteForm): FormField[] {
  if (form.formSchema) {
    try {
      const parsed = JSON.parse(form.formSchema)
      if (Array.isArray(parsed)) return parsed as FormField[]
    } catch {
      return []
    }
  }
  return []
}

// Get a field's display label (uses `label`, falls back to `name` or `key`).
export function fieldLabel(field: FormField): string {
  return field.label || field.key || '未命名字段'
}

// Normalize a field's options to string labels.
export function fieldOptions(field: FormField): string[] {
  if (!field.options) return []
  if (typeof field.options[0] === 'string') return field.options as string[]
  return (field.options as FormFieldOption[]).map((o) => o.label || o.value)
}

// Serialize a fields array back into the `formSchema` JSON string the API expects.
function serializeFormSchema(fields: FormField[]): string {
  return JSON.stringify(fields.map((f) => ({
    key: f.key || f.label || '',
    type: f.type,
    label: f.label || f.key || '',
    required: Boolean(f.required),
    placeholder: f.placeholder || '',
    options: Array.isArray(f.options) && typeof f.options[0] === 'object'
      ? f.options
      : Array.isArray(f.options) ? f.options.map((label) => ({ label, value: label })) : undefined,
  })))
}

export async function getForms(clubId = 1): Promise<SiteForm[] | null> {
  const raw = await managementRequest<unknown>({
    method: 'GET',
    url: `/management/clubs/${clubId}/site-forms`,
  })
  if (Array.isArray(raw)) return raw as SiteForm[]
  if (raw && Array.isArray((raw as PageData<SiteForm>).list)) return (raw as PageData<SiteForm>).list
  return null
}

export async function getForm(formId: number): Promise<SiteForm | null> {
  return managementRequest<SiteForm>({ method: 'GET', url: `/management/site-forms/${formId}` })
}

export async function createForm(data: {
  name: string
  fields: FormField[]
  status?: string
  startAt?: string
  endAt?: string
}, clubId = 1): Promise<SiteForm | null> {
  return managementRequest<SiteForm>({
    method: 'POST',
    url: `/management/clubs/${clubId}/site-forms`,
    data: {
      name: data.name,
      formSchema: serializeFormSchema(data.fields),
      status: data.status || 'open',
      startAt: data.startAt,
      endAt: data.endAt,
    },
  })
}

export async function updateForm(formId: number, data: Partial<SiteForm> & { fields?: FormField[] }): Promise<SiteForm | null> {
  const payload: Record<string, unknown> = { ...data }
  if (data.fields) payload.formSchema = serializeFormSchema(data.fields)
  delete payload.fields
  return managementRequest<SiteForm>({ method: 'PATCH', url: `/management/site-forms/${formId}`, data: payload })
}

export async function publishForm(formId: number): Promise<SiteForm | null> {
  return managementRequest<SiteForm>({ method: 'POST', url: `/management/site-forms/${formId}/publish` })
}

export async function closeForm(formId: number): Promise<SiteForm | null> {
  return managementRequest<SiteForm>({ method: 'POST', url: `/management/site-forms/${formId}/close` })
}

export async function getFormSubmissions(formId: number, params?: {
  page?: number
  pageSize?: number
}): Promise<PageData<FormSubmission> | null> {
  const raw = await managementRequest<unknown>({
    method: 'GET',
    url: `/management/site-forms/${formId}/submissions`,
    params,
  })
  return normalizePage<FormSubmission>(raw)
}

export async function submitForm(formId: number, data: Record<string, unknown>): Promise<FormSubmission | null> {
  return managementRequest<FormSubmission>({
    method: 'POST',
    url: `/management/forms/${formId}/submissions`,
    data,
  })
}

export async function exportFormSubmissions(formId: number): Promise<Blob> {
  const response = await apiClient.get(`/management/site-forms/${formId}/submissions/export`, {
    responseType: 'blob',
  })
  return response.data
}
