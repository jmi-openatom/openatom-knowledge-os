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
  const isAdmin = computed(() => user.value?.role === 'admin' || user.value?.role === 'leader')

  async function restore() {
    if (initialized.value) return
    try {
      const session = await window.openatom?.auth.getSession()
      if (session?.user) {
        user.value = normalizeUser(session.user)
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
      user.value = normalizeUser(session.user)
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
    // Check roles first, then fall back to permissions for admin detection
    const hasAdminRole = roles.some((r) => {
      const lower = r.toLowerCase()
      return lower === 'admin' || lower === 'super_admin' || lower === 'administrator'
        || lower.includes('社长') || lower === 'president' || lower === 'club_admin'
    })
    const hasAdminPermission = !hasAdminRole && permissions.some((p) => {
      const lower = p.toLowerCase()
      return lower.includes('admin') || lower.includes('manage')
        || lower.includes('delete') || lower.includes('system')
    })
    const role: KnowledgeUser['role'] = (hasAdminRole || hasAdminPermission)
      ? 'admin'
      : roles.some((item) => ['leader', 'operations_lead', 'department_head'].includes(item.toLowerCase()))
        ? 'leader'
        : roles.includes('guest')
          ? 'guest'
          : 'member'
    return { ...input, role }
  }

  return { user, initialized, loading, error, isAuthenticated, isAdmin, restore, login, loginDemo, logout }
})
