import { createRouter, createWebHashHistory } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import LoginView from '@/views/LoginView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/preview/:fileId', component: () => import('@/views/FilePreviewWindow.vue'), meta: { public: true } },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', redirect: '/chat' },
        { path: 'chat', component: () => import('@/views/ChatView.vue') },
        { path: 'files', component: () => import('@/views/FilesView.vue') },
        { path: 'documents', component: () => import('@/views/DocumentsView.vue') },
        { path: 'wiki', component: () => import('@/views/WikiView.vue') },
        { path: 'search', component: () => import('@/views/SearchView.vue') },
        { path: 'members', component: () => import('@/views/MembersView.vue') },
        { path: 'settings', component: () => import('@/views/SettingsView.vue') },
        { path: 'ui-kit', component: () => import('@/views/UiKitView.vue') },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const { useAuthStore } = await import('@/stores/auth')
  const auth = useAuthStore()
  if (!auth.initialized) await auth.restore()
  return auth.isAuthenticated ? true : '/login'
})

export default router
