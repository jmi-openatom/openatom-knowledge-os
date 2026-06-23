<script setup lang="ts">
import {computed} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {
  IconAtom2,
  IconBooks,
  IconFileText,
  IconFolders,
  IconMessageCircle,
  IconSearch,
  IconSettings,
  IconUsers,
} from '@tabler/icons-vue'
import {OaAvatar, OaGlassSidebar, OaNavItem, OaToastViewport} from '@openatom/ui'
import {useAuthStore} from '@/stores/auth'
import FilePreviewer from '@/components/FilePreviewer.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const items = computed(() => [
  {path: '/chat', label: '对话', icon: IconMessageCircle},
  {path: '/files', label: '资料库', icon: IconFolders},
  {path: '/wiki', label: 'Wiki', icon: IconBooks},
  {path: '/search', label: '搜索', icon: IconSearch},
  {path: '/documents', label: '在线文档', icon: IconFileText},
  ...(auth.isAdmin ? [{path: '/members', label: '成员', icon: IconUsers}] : []),
])
</script>

<template>
  <div class="top-header">
    JMI-OPENATOM
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
          @click="router.push(item.path)"
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
</template>

<style scoped>
.app-shell {
  display: flex;
  width: 100vw;
  height: 97vh;
  min-width: 0;
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
