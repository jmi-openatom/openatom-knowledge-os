<script setup lang="ts">
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {
  IconAtom2,
  IconBell,
  IconBooks,
  IconCalendarEvent,
  IconForms,
  IconFileText,
  IconFolders,
  IconMessageCircle,
  IconMinus,
  IconSearch,
  IconSettings,
  IconShieldCog,
  IconSquare,
  IconUsers,
  IconX,
} from '@tabler/icons-vue'
import {OaAvatar, OaGlassSidebar, OaNavItem, OaToastViewport} from '@openatom/ui'
import {useAuthStore} from '@/stores/auth'
import FilePreviewer from '@/components/FilePreviewer.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isMaximized = ref(false)
const isWindows = window.openatom?.platform === 'win32'

const items = computed(() => [
  {path: '/chat', label: '对话', icon: IconMessageCircle},
  {path: '/files', label: '资料库', icon: IconFolders},
  {path: '/wiki', label: 'Wiki', icon: IconBooks},
  {path: '/search', label: '搜索', icon: IconSearch},
  {path: '/documents', label: '在线文档', icon: IconFileText},
  // ...(auth.isAdmin ? [{path: '/activities', label: '活动', icon: IconCalendarEvent}] : []),
  // ...(auth.isAdmin ? [{path: '/notifications', label: '通知', icon: IconBell}] : []),
  ...(auth.isAdmin ? [{path: '/forms', label: '表单', icon: IconForms}] : []),
  ...(auth.isAdmin ? [{path: '/members', label: '成员', icon: IconUsers}] : []),
  ...(auth.isAdmin ? [{path: '/admin', label: '管理后台', icon: IconShieldCog}] : []),
])

function handleNavClick(item: { path: string }) {
  if (item.path === '/admin') {
    window.openatom?.admin?.open()
  } else {
    router.push(item.path)
  }
}

function winMinimize() {
  window.openatom?.windowControl.minimize()
}
async function winMaximize() {
  await window.openatom?.windowControl.maximize()
  isMaximized.value = await window.openatom?.windowControl.isMaximized() ?? false
}
function winClose() {
  window.openatom?.windowControl.close()
}
</script>

<template>
  <div class="app-root">
    <div v-if="isWindows" class="top-header">
      <span class="top-header__title">JMI-OPENATOM</span>
      <div class="window-controls">
        <button class="win-btn" @click="winMinimize" title="最小化"><IconMinus :size="14" /></button>
        <button class="win-btn" @click="winMaximize" :title="isMaximized ? '还原' : '最大化'">
          <IconSquare :size="12" v-if="!isMaximized" />
          <IconSquare :size="14" v-else style="stroke-width: 1" />
        </button>
        <button class="win-btn win-btn--close" @click="winClose" title="关闭"><IconX :size="14" /></button>
      </div>
    </div>
    <div class="app-shell">

    <!-- Header bar for window dragging -->
    <div class="app-headerbar" data-tauri-drag-region></div>

    <OaGlassSidebar :width="96">
      <template #brand>
        <button class="brand" type="button" aria-label="OpenAtom Knowledge OS" @click="router.push('/chat')">
          <IconAtom2 :size="28" :stroke-width="1.7"/>
          <span>OPENATOM</span>
        </button>
      </template>

      <OaNavItem
          v-for="item in items"
          :key="item.path"
          :label="item.label"
          :active="route.path === item.path"
          compact
          @click="handleNavClick(item)"
      >
        <template #icon>
          <component :is="item.icon" :size="21" :stroke-width="1.7"/>
        </template>
      </OaNavItem>

      <template #footer>
        <OaNavItem label="设置" :active="route.path === '/settings'" compact @click="router.push('/settings')">
          <template #icon>
            <IconSettings :size="21" :stroke-width="1.7"/>
          </template>
        </OaNavItem>
        <button class="profile-button" type="button" @click="router.push('/settings')">
          <OaAvatar :name="auth.user?.name || '用户'" :src="auth.user?.avatar" :size="34" status="online"/>
          <span>{{ auth.user?.name || '用户' }}</span>
        </button>
      </template>
    </OaGlassSidebar>
    <main class="app-shell__main">
      <RouterView/>
    </main>
    <FilePreviewer/>
    <OaToastViewport/>
    </div>
  </div>
</template>

<style scoped>
.app-root {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}
.app-shell {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--oa-color-canvas-soft);
}

.app-headerbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 28px;
  z-index: 1000;
  -webkit-app-region: drag;
  cursor: default;

}
.top-header{
  height: 35px;
  background-color: #efefef;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 500;
  -webkit-app-region: drag;
  position: relative;
  flex-shrink: 0;
}
.top-header__title {
  flex: 1;
  text-align: center;
  pointer-events: none;
}
.window-controls {
  display: flex;
  position: absolute;
  right: 0;
  top: 0;
  height: 100%;
  -webkit-app-region: no-drag;
}
.win-btn {
  display: grid;
  place-items: center;
  width: 46px;
  height: 100%;
  border: none;
  background: transparent;
  color: #333;
  cursor: pointer;
  transition: background .12s;
}
.win-btn:hover {
  background: rgba(0,0,0,.06);
}
.win-btn--close:hover {
  background: #e81123;
  color: #fff;
}

.app-shell__main {
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.brand {
  display: grid;
  gap: 4px;
  border: 0;
  color: var(--oa-color-ink);
  background: transparent;
  cursor: pointer;
  place-items: center;
}

.brand span {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .3px;
}

.profile-button {
  display: grid;
  gap: 5px;
  padding: 8px 4px;
  border: 0;
  border-radius: 9px;
  color: var(--oa-color-body);
  background: transparent;
  cursor: pointer;
  place-items: center;
}

.profile-button:hover {
  background: rgb(0 0 0 / 4%);
}

.profile-button span {
  max-width: 74px;
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
