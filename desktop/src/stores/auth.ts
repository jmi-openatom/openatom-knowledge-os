import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { KnowledgeUser } from '@/types'

const demoUser: KnowledgeUser = {
  id: 1,
  sub: 'demo-admin',
  name: '张同学',
  email: 'zhang@example.com',
  role: 'admin',
  roles: ['admin'],
  permissions: ['file:read', 'file:write', 'file:delete', 'doc:edit', 'ai:chat', 'admin:manage'],
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<KnowledgeUser | null>(null)
  const initialized = ref(false)
  const loading = ref(false)
  const error = ref('')

  const isAuthenticated = computed(() => Boolean(user.value))
  const isAdmin = computed(() => user.value?.role === 'admin')

  async function rejectNonAdmin() {
    await window.openatom?.auth.logout()
    localStorage.removeItem('openatom-demo-session')
    user.value = null
    error.value = '仅管理员可登录本系统，当前账号无权访问。'
  }

  async function restore() {
    if (initialized.value) return
    try {
      const session = await window.openatom?.auth.getSession()
      if (session?.user) {
        const normalized = normalizeUser(session.user)
        if (normalized.role !== 'admin') {
          await rejectNonAdmin()
        } else {
          user.value = normalized
        }
      } else if (localStorage.getItem('openatom-demo-session') === '1') {
        user.value = demoUser
      }
    } finally {
      initialized.value = true
    }
  }

  async function login() {
    loading.value = true
    error.value = ''
    try {
      if (!window.openatom) {
        throw new Error('浏览器预览不支持系统 OAuth 回调，请使用演示登录或 Electron 客户端。')
      }
      const session = await window.openatom.auth.login()
      const normalized = normalizeUser(session.user)
      if (normalized.role !== 'admin') {
        await rejectNonAdmin()
        throw new Error('仅管理员可登录本系统，当前账号无权访问。')
      }
      user.value = normalized
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : '登录失败'
      throw reason
    } finally {
      loading.value = false
    }
  }

  function loginDemo() {
    localStorage.setItem('openatom-demo-session', '1')
    user.value = demoUser
  }

  async function logout() {
    await window.openatom?.auth.logout()
    localStorage.removeItem('openatom-demo-session')
    user.value = null
  }

  function normalizeUser(input: OpenAtomUser): KnowledgeUser {
    const roles = input.roles || (input.role ? [input.role] : [])
    const permissions = input.permissions || []
    console.log('[Auth] normalizeUser input:', JSON.stringify({ roles, permissions }))
    // Check roles first - must be an explicit admin role
    const hasAdminRole = roles.some((r) => {
      const lower = r.toLowerCase()
      return lower === 'admin' || lower === 'super_admin' || lower === 'administrator'
        || lower.includes('社长') || lower === 'president' || lower === 'club_admin'
        || lower === 'system_admin' || lower === 'root'
    })
    // Only check permissions for admin if they have explicit admin-level permissions
    // Use exact match instead of includes to avoid false positives like form:manage
    const hasAdminPermission = !hasAdminRole && permissions.some((p) => {
      const lower = p.toLowerCase()
      return lower === 'admin' || lower === 'admin:manage'
        || lower === 'system:admin' || lower === 'super:admin'
        || lower === 'admin:all' || lower === '*'
    })
    const role: KnowledgeUser['role'] = (hasAdminRole || hasAdminPermission)
      ? 'admin'
      : roles.some((item) => ['leader', 'operations_lead', 'department_head'].includes(item.toLowerCase()))
        ? 'leader'
        : roles.includes('guest')
          ? 'guest'
          : 'member'
    console.log('[Auth] normalizeUser result: role =', role)
    return { ...input, role }
  }

  return { user, initialized, loading, error, isAuthenticated, isAdmin, restore, login, loginDemo, logout }
})
