<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, nextTick, watch } from 'vue'
import { IconChevronDown, IconChevronRight, IconEdit, IconFileText, IconFolder, IconDeviceFloppy, IconX, IconPlus, IconTrash, IconFolderPlus, IconFilePlus } from '@tabler/icons-vue'
import { OaButton, OaModal, OaTag, OaTextarea, useToast } from '@openatom/ui'
import PageHeader from '@/components/PageHeader.vue'
import type { WikiNode } from '@/types'
import { getWikiTree, getWikiPage, updateWikiPage, createWikiPage, deleteWikiPage } from '@/services/api'

// State
const wikiTree = ref<WikiNode[]>([])
const expanded = ref(new Set<number>())
const activeId = ref<number | null>(null)
const currentPage = ref<WikiNode | null>(null)
const isLoading = ref(false)
const isSaving = ref(false)
const isEditing = ref(false)
const error = ref<string | null>(null)
const deleteModal = ref(false)
const isDeleting = ref(false)
const pendingDelete = ref<WikiNode | null>(null)
const { pushToast } = useToast()

// Context menu state
const ctxMenu = ref({ visible: false, x: 0, y: 0, parentNode: null as WikiNode | null })

// Input dialog state (replaces window.prompt which doesn't work in Electron)
const inputModal = ref({ visible: false, title: '', label: '', value: '', type: 'page' as 'page' | 'category', parentNode: null as WikiNode | null })
const inputModalRef = ref<HTMLInputElement | null>(null)
watch(() => inputModal.value.visible, (open) => {
  if (open) nextTick(() => inputModalRef.value?.focus())
})

// Edit form state
const editTitle = ref('')
const editContent = ref('')

// Computed
const breadcrumb = computed(() => {
  if (!currentPage.value) return '社团知识库'
  const parent = findParent(wikiTree.value, currentPage.value.id)
  return parent ? `社团知识库 / ${parent.title} / ${currentPage.value.title}` : `社团知识库 / ${currentPage.value.title}`
})

function findParent(nodes: WikiNode[], targetId: number): WikiNode | null {
  for (const node of nodes) {
    if (node.children?.some(child => child.id === targetId)) {
      return node
    }
    if (node.children) {
      const found = findParent(node.children, targetId)
      if (found) return found
    }
  }
  return null
}

// Load wiki tree on mount
async function loadWikiTree() {
  isLoading.value = true
  error.value = null
  try {
    const data = await getWikiTree()
    if (data) {
      wikiTree.value = data
      // Auto-expand first level
      expanded.value = new Set(data.map(node => node.id))
    }
  } catch (err) {
    error.value = '加载 Wiki 树失败，请检查网络连接'
    console.error('Failed to load wiki tree:', err)
  } finally {
    isLoading.value = false
  }
}

// Load page detail
async function loadPageDetail(id: number) {
  isLoading.value = true
  error.value = null
  try {
    const page = await getWikiPage(id)
    if (page) {
      currentPage.value = page
      activeId.value = id
      editTitle.value = page.title
      editContent.value = page.content || ''
      isEditing.value = false
    }
  } catch (err) {
    error.value = '加载页面详情失败'
    console.error('Failed to load page detail:', err)
  } finally {
    isLoading.value = false
  }
}

// Toggle category expand/collapse
function toggle(id: number) {
  if (expanded.value.has(id)) {
    expanded.value.delete(id)
  } else {
    expanded.value.add(id)
  }
  expanded.value = new Set(expanded.value)
}

// Handle page click
async function handlePageClick(node: WikiNode) {
  if (node.type === 'page') {
    await loadPageDetail(node.id)
  }
}

// Start editing
function startEdit() {
  if (!currentPage.value) return
  editTitle.value = currentPage.value.title
  editContent.value = currentPage.value.content || ''
  isEditing.value = true
}

// Cancel editing
function cancelEdit() {
  if (currentPage.value) {
    editTitle.value = currentPage.value.title
    editContent.value = currentPage.value.content || ''
  }
  isEditing.value = false
}

// Save page
async function savePage() {
  if (!currentPage.value || !editTitle.value.trim()) {
    error.value = '标题不能为空'
    return
  }

  isSaving.value = true
  error.value = null
  try {
    const updated = await updateWikiPage(currentPage.value.id, {
      title: editTitle.value.trim(),
      content: editContent.value,
      parentId: currentPage.value.parentId,
      type: currentPage.value.type,
    })
    
    if (updated) {
      currentPage.value = updated
      // Refresh tree to update titles
      await loadWikiTree()
      isEditing.value = false
    }
  } catch (err) {
    error.value = '保存失败，请重试'
    console.error('Failed to save page:', err)
  } finally {
    isSaving.value = false
  }
}

// Open input dialog for new page
function createNewPage(parentNode?: WikiNode | null) {
  inputModal.value = {
    visible: true,
    title: '新建页面',
    label: '页面标题',
    value: '',
    type: 'page',
    parentNode: parentNode !== undefined ? parentNode : (currentPage.value || null),
  }
}

// Open input dialog for new folder
function createNewFolder(parentNode?: WikiNode | null) {
  inputModal.value = {
    visible: true,
    title: '新建文件夹',
    label: '文件夹名称',
    value: '',
    type: 'category',
    parentNode: parentNode !== undefined ? parentNode : null,
  }
}

// Confirm create from input dialog
async function confirmCreate() {
  const title = inputModal.value.value.trim()
  if (!title) return

  const { type, parentNode } = inputModal.value
  const parentId = parentNode ? parentNode.id : null

  isSaving.value = true
  error.value = null
  try {
    const newNode = await createWikiPage({
      title,
      content: '',
      parentId,
      type,
    })

    if (newNode) {
      if (parentNode && parentNode.type === 'category') expanded.value.add(parentNode.id)
      await loadWikiTree()
      if (type === 'page') {
        await loadPageDetail(newNode.id)
      }
      pushToast({ title: type === 'category' ? '已创建文件夹' : '已创建页面', tone: 'success' })
    }
  } catch (err) {
    error.value = type === 'category' ? '创建文件夹失败' : '创建页面失败'
    pushToast({ title: '创建失败，请重试', tone: 'danger' })
    console.error('Failed to create:', err)
  } finally {
    isSaving.value = false
    inputModal.value.visible = false
  }
}

// Context menu handlers
function onTreeContextMenu(event: MouseEvent) {
  event.preventDefault()
  const target = event.target as HTMLElement
  const row = target.closest('.wiki-node-row') as HTMLElement | null
  let parentNode: WikiNode | null = null
  if (row) {
    const id = Number(row.dataset.nodeId)
    parentNode = id ? findNodeById(wikiTree.value, id) : null
  }
  ctxMenu.value = { visible: true, x: event.clientX, y: event.clientY, parentNode }
}

function findNodeById(nodes: WikiNode[], id: number): WikiNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children) {
      const found = findNodeById(node.children, id)
      if (found) return found
    }
  }
  return null
}

function closeCtxMenu() {
  ctxMenu.value.visible = false
}

function ctxNewPage() {
  const parent = ctxMenu.value.parentNode
  closeCtxMenu()
  createNewPage(parent)
}

function ctxNewFolder() {
  const parent = ctxMenu.value.parentNode
  closeCtxMenu()
  createNewFolder(parent)
}

// Delete current page
function requestDelete(node: WikiNode) {
  pendingDelete.value = node
  deleteModal.value = true
}

async function confirmDelete() {
  const node = pendingDelete.value
  if (!node) return
  isDeleting.value = true
  error.value = null
  try {
    await deleteWikiPage(node.id)
    pushToast({ title: node.type === 'category' ? '已删除分类及子页面' : '已删除页面', tone: 'success' })
    // Clear current page if it is the deleted node or a descendant of it
    if (currentPage.value && (currentPage.value.id === node.id || isDescendant(currentPage.value.id, node))) {
      currentPage.value = null
      activeId.value = null
    }
    pendingDelete.value = null
    deleteModal.value = false
    await loadWikiTree()
  } catch (err) {
    error.value = '删除失败，请重试'
    pushToast({ title: '删除失败，请重试', tone: 'danger' })
    console.error('Failed to delete page:', err)
  } finally {
    isDeleting.value = false
  }
}

// Whether targetId is inside node's subtree
function isDescendant(targetId: number, node: WikiNode): boolean {
  return (node.children || []).some(child => child.id === targetId || isDescendant(targetId, child))
}

// --- Drag & drop ---
const dragNodeId = ref<number | null>(null)
const dragOverId = ref<number | null>(null)

function onDragStart(event: DragEvent, node: WikiNode) {
  dragNodeId.value = node.id
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(node.id))
  }
}

function onDragOver(event: DragEvent, node: WikiNode) {
  if (dragNodeId.value === null || dragNodeId.value === node.id) return
  // Only allow drop onto categories, and not onto own descendant
  if (node.type !== 'category') return
  if (isDescendant(node.id, findNodeById(wikiTree.value, dragNodeId.value)!)) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dragOverId.value = node.id
}

function onDragLeave() {
  dragOverId.value = null
}

async function onDrop(event: DragEvent, targetNode: WikiNode) {
  event.preventDefault()
  const sourceId = dragNodeId.value
  dragOverId.value = null
  dragNodeId.value = null
  if (sourceId === null || sourceId === targetNode.id) return
  if (targetNode.type !== 'category') return
  const sourceNode = findNodeById(wikiTree.value, sourceId)
  if (!sourceNode) return
  if (isDescendant(targetNode.id, sourceNode)) return

  isSaving.value = true
  try {
    const updated = await updateWikiPage(sourceId, {
      title: sourceNode.title,
      content: sourceNode.content || '',
      parentId: targetNode.id,
      type: sourceNode.type,
    })
    if (updated) {
      expanded.value.add(targetNode.id)
      await loadWikiTree()
      pushToast({ title: `已移入「${targetNode.title}」`, tone: 'success' })
    }
  } catch (err) {
    pushToast({ title: '移动失败，请重试', tone: 'danger' })
    console.error('Failed to move node:', err)
  } finally {
    isSaving.value = false
  }
}

// Count descendants for a node (for delete confirmation warning)
function countDescendants(node: WikiNode): number {
  return (node.children || []).reduce((sum, child) => sum + 1 + countDescendants(child), 0)
}

// Format date
function formatDate(dateStr?: string): string {
  if (!dateStr) return '未知'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

// Simple markdown renderer (basic support)
function renderMarkdown(content: string): string {
  if (!content) return ''
  
  let html = content
    // Headers
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    // Bold
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    // Italic
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    // Blockquotes
    .replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>')
    // Line breaks
    .replace(/\n/g, '<br>')
  
  return html
}

// Lifecycle
onMounted(() => {
  loadWikiTree()
  window.addEventListener('click', closeCtxMenu)
  window.addEventListener('contextmenu', closeCtxMenuExceptTree)
})

onBeforeUnmount(() => {
  window.removeEventListener('click', closeCtxMenu)
  window.removeEventListener('contextmenu', closeCtxMenuExceptTree)
})

function closeCtxMenuExceptTree(event: MouseEvent) {
  const tree = document.querySelector('.wiki-tree-aside')
  if (tree && !tree.contains(event.target as Node)) closeCtxMenu()
}
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="WIKI KNOWLEDGE" title="社团 Wiki" description="将分散资料组织为可持续维护的知识体系。">
      <template v-if="!isEditing">
        <OaButton @click="createNewPage" :disabled="isSaving"><template #prefix><IconPlus :size="16" /></template>新建页面</OaButton>
        <template v-if="currentPage">
          <OaButton tone="primary" @click="startEdit" :disabled="isSaving"><template #prefix><IconEdit :size="16" /></template>编辑页面</OaButton>
          <OaButton tone="danger" @click="requestDelete(currentPage)" :disabled="isSaving"><template #prefix><IconTrash :size="16" /></template>删除页面</OaButton>
        </template>
      </template>
      <template v-else-if="isEditing">
        <OaButton @click="cancelEdit" :disabled="isSaving"><template #prefix><IconX :size="16" /></template>取消</OaButton>
        <OaButton tone="primary" @click="savePage" :disabled="isSaving">
          <template #prefix><IconDeviceFloppy :size="16" /></template>
          {{ isSaving ? '保存中...' : '保存' }}
        </OaButton>
      </template>
    </PageHeader>

    <!-- Error message -->
    <div v-if="error" class="error-banner">
      {{ error }}
      <button @click="error = null" class="close-error">×</button>
    </div>

    <div class="wiki-layout">
      <!-- Left sidebar: Tree -->
      <aside class="wiki-tree-aside" @contextmenu="onTreeContextMenu">
        <div v-if="isLoading && wikiTree.length === 0" class="loading-state">加载中...</div>
        <template v-else v-for="node in wikiTree" :key="node.id">
          <div v-if="node.type === 'category'" class="wiki-node-row" :class="{ 'drag-over': dragOverId === node.id }" :data-node-id="node.id" draggable="true" @dragstart="onDragStart($event, node)" @dragover="onDragOver($event, node)" @dragleave="onDragLeave" @drop="onDrop($event, node)">
            <button class="wiki-category" @click="toggle(node.id)">
              <component :is="expanded.has(node.id) ? IconChevronDown : IconChevronRight" :size="15" />
              <IconFolder :size="17" />
              <strong>{{ node.title }}</strong>
            </button>
            <button class="node-delete" title="删除分类" @click.stop="requestDelete(node)"><IconTrash :size="14" /></button>
          </div>
          <div v-else class="wiki-node-row" :data-node-id="node.id" draggable="true" @dragstart="onDragStart($event, node)">
            <button class="wiki-child" :class="{ active: node.id === activeId }" @click="handlePageClick(node)">
              <IconFileText :size="15" /> {{ node.title }}
            </button>
            <button class="node-delete" title="删除页面" @click.stop="requestDelete(node)"><IconTrash :size="14" /></button>
          </div>
          <div v-if="node.type === 'category' && expanded.has(node.id)" class="wiki-children">
            <div v-for="child in node.children" :key="child.id" class="wiki-node-row" :class="{ 'drag-over': dragOverId === child.id }" :data-node-id="child.id" draggable="true" @dragstart="onDragStart($event, child)" @dragover="onDragOver($event, child)" @dragleave="onDragLeave" @drop="onDrop($event, child)">
              <button
                :class="['wiki-child', { active: child.id === activeId }]"
                @click="handlePageClick(child)"
              >
                <IconFileText :size="15" /> {{ child.title }}
              </button>
              <button class="node-delete" :title="child.type === 'category' ? '删除分类' : '删除页面'" @click.stop="requestDelete(child)"><IconTrash :size="14" /></button>
            </div>
          </div>
        </template>
      </aside>

      <!-- Main content area -->
      <article>
        <div v-if="isLoading && !currentPage" class="loading-state">加载中...</div>
        <div v-else-if="!currentPage" class="empty-state">
          <p>请从左侧选择一个页面查看</p>
        </div>
        <template v-else>
          <!-- View mode -->
          <div v-if="!isEditing">
            <nav>{{ breadcrumb }}</nav>
            <h1>{{ currentPage.title }}</h1>
            <div class="meta">
              <OaTag>{{ findParent(wikiTree, currentPage.id)?.title || '未分类' }}</OaTag>
              <span v-if="currentPage.updatedByName">由 {{ currentPage.updatedByName }} 更新</span>
              <span v-if="currentPage.updatedAt">于 {{ formatDate(currentPage.updatedAt) }}</span>
            </div>
            <div class="content" v-html="renderMarkdown(currentPage.content || '')"></div>
          </div>

          <!-- Edit mode -->
          <div v-else class="edit-mode">
            <nav>{{ breadcrumb }}</nav>
            <input 
              v-model="editTitle" 
              class="edit-title-input" 
              placeholder="页面标题" 
              :disabled="isSaving"
            />
            <div class="meta">
              <OaTag>{{ findParent(wikiTree, currentPage.id)?.title || '未分类' }}</OaTag>
              <span>编辑模式</span>
            </div>
            <OaTextarea 
              v-model="editContent" 
              placeholder="输入页面内容（支持 Markdown）" 
              :rows="20"
              :disabled="isSaving"
              class="edit-content-textarea"
            />
          </div>
        </template>
      </article>

      <!-- Right sidebar: Context info -->
      <aside class="wiki-context">
        <h2>页面信息</h2>
        <dl v-if="currentPage">
          <div><dt>负责人</dt><dd>{{ currentPage.updatedByName || '未知' }}</dd></div>
          <div><dt>类型</dt><dd>{{ currentPage.type === 'category' ? '分类' : '页面' }}</dd></div>
          <div><dt>排序</dt><dd>{{ currentPage.sortOrder ?? 0 }}</dd></div>
        </dl>
        <div v-else class="empty-context">无选中页面</div>
      </aside>
    </div>

    <OaModal :open="deleteModal" :title="pendingDelete?.type === 'category' ? '删除分类' : '删除页面'" @close="deleteModal = false">
      <p v-if="pendingDelete?.type === 'category' && countDescendants(pendingDelete) > 0" class="delete-warn">
        「{{ pendingDelete?.title }}」包含 {{ countDescendants(pendingDelete) }} 个子页面，将一并删除。此操作不可撤销。
      </p>
      <p v-else>确定删除「{{ pendingDelete?.title || '' }}」吗？此操作不可撤销。</p>
      <template #footer>
        <OaButton variant="outline" @click="deleteModal = false" :disabled="isDeleting">取消</OaButton>
        <OaButton tone="danger" @click="confirmDelete" :loading="isDeleting">删除</OaButton>
      </template>
    </OaModal>

    <!-- Input dialog for new page/folder -->
    <OaModal :open="inputModal.visible" :title="inputModal.title" @close="inputModal.visible = false">
      <p class="input-hint">{{ inputModal.parentNode ? `位置：${inputModal.parentNode.title}` : '位置：根目录' }}</p>
      <input
        ref="inputModalRef"
        v-model="inputModal.value"
        class="input-modal-field"
        :placeholder="inputModal.label"
        :disabled="isSaving"
        @keydown.enter="confirmCreate"
        @keydown.esc="inputModal.visible = false"
      />
      <template #footer>
        <OaButton variant="outline" @click="inputModal.visible = false" :disabled="isSaving">取消</OaButton>
        <OaButton tone="primary" @click="confirmCreate" :loading="isSaving" :disabled="!inputModal.value.trim()">创建</OaButton>
      </template>
    </OaModal>

    <!-- Right-click context menu -->
    <Teleport to="body">
      <div v-if="ctxMenu.visible" class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }" @click.stop @contextmenu.prevent>
        <div class="ctx-menu__header">
          {{ ctxMenu.parentNode ? ctxMenu.parentNode.title : '根目录' }}
        </div>
        <button class="ctx-menu__item" @click="ctxNewFolder">
          <IconFolderPlus :size="16" /> 新建文件夹
        </button>
        <button class="ctx-menu__item" @click="ctxNewPage">
          <IconFilePlus :size="16" /> 新建页面
        </button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.page { height: 100%; overflow: hidden; background: #fff; }

/* Error banner */
.error-banner {
  margin: 0 20px 10px;
  padding: 10px 15px;
  background: #fee;
  color: #c33;
  border-radius: 6px;
  font-size: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.close-error {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: #c33;
  padding: 0 5px;
}

/* Loading and empty states */
.loading-state, .empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--oa-color-muted);
  font-size: 14px;
}
.empty-state p {
  margin: 0;
}

.wiki-layout { display: grid; grid-template-columns: 250px minmax(460px, 1fr) 230px; height: calc(100% - 104px); }
.wiki-layout > aside { padding: 20px 12px; overflow-y: auto; border-right: 1px solid var(--oa-color-hairline); }
.wiki-category, .wiki-children button, .wiki-child { display: flex; align-items: center; gap: 7px; width: 100%; min-height: 38px; padding: 0 9px; border: 0; border-radius: 6px; color: var(--oa-color-body); background: transparent; cursor: pointer; text-align: left; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.wiki-category:hover, .wiki-children button:hover, .wiki-children button.active, .wiki-child:hover, .wiki-child.active { color: var(--oa-color-ink); background: var(--oa-color-canvas-inset); }
.wiki-category strong { font-size: 12px; overflow: hidden; text-overflow: ellipsis; }
.wiki-children { padding-left: 21px; }
.wiki-children button, .wiki-child { font-size: 11px; }
.wiki-node-row { position: relative; display: flex; align-items: center; width: 100%; }
.wiki-node-row > button:first-child { flex: 1; min-width: 0; width: auto; }
.wiki-node-row[draggable="true"] { cursor: grab; }
.wiki-node-row[draggable="true"]:active { cursor: grabbing; }
.wiki-node-row.drag-over { outline: 2px dashed var(--oa-color-link, #087cf0); outline-offset: -2px; border-radius: 6px; background: rgb(8 124 240 / 6%); }
.node-delete { flex: none; display: flex; align-items: center; justify-content: center; width: 24px; height: 24px; margin-right: 4px; border: 0; border-radius: 5px; color: var(--oa-color-muted); background: transparent; cursor: pointer; opacity: .35; transition: opacity .15s, color .15s, background .15s; }
.wiki-node-row:hover .node-delete { opacity: 1; }
.node-delete:hover { color: #c0392b; background: rgb(192 57 43 / 8%); opacity: 1; }
.delete-warn { margin: 0; padding: 10px 12px; border-radius: 6px; background: #fff4e5; color: #b26a00; font-size: 12px; line-height: 1.6; }
.input-hint { margin: 0 0 12px; color: var(--oa-color-muted); font-size: 11px; }
.input-modal-field { width: 100%; padding: 10px 12px; font-size: 14px; border: 1px solid var(--oa-color-hairline); border-radius: 6px; outline: none; box-sizing: border-box; }
.input-modal-field:focus { border-color: var(--oa-color-primary); }

.wiki-layout > article { max-width: 800px; padding: 42px 56px; overflow-y: auto; }
.wiki-layout article nav { color: var(--oa-color-muted); font-size: 10px; }
.wiki-layout article h1 { margin: 18px 0 10px; font-size: 32px; letter-spacing: -1.2px; }
.meta { display: flex; align-items: center; gap: 10px; color: var(--oa-color-muted); font-size: 10px; }
.wiki-layout article p { color: var(--oa-color-body); font-size: 14px; line-height: 1.85; }

/* Content styles */
.content {
  margin-top: 20px;
}
.content :deep(h2) { margin-top: 28px; font-size: 18px; }
.content :deep(p) { color: var(--oa-color-body); font-size: 14px; line-height: 1.85; margin: 12px 0; }
.content :deep(blockquote) { 
  margin: 22px 0; 
  padding: 13px 16px; 
  border-left: 3px solid var(--oa-color-link); 
  color: var(--oa-color-body); 
  background: #f6faff; 
  font-size: 12px; 
  line-height: 1.7; 
}
.content :deep(strong) { font-weight: 600; }
.content :deep(em) { font-style: italic; }

/* Edit mode styles */
.edit-mode {
  margin-top: 20px;
}
.edit-title-input {
  width: 100%;
  padding: 10px 12px;
  font-size: 24px;
  font-weight: 600;
  border: 1px solid var(--oa-color-hairline);
  border-radius: 6px;
  margin: 18px 0 10px;
  outline: none;
}
.edit-title-input:focus {
  border-color: var(--oa-color-primary);
}
.edit-title-input:disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}
.edit-content-textarea {
  margin-top: 15px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.wiki-context { border-right: 0 !important; border-left: 1px solid var(--oa-color-hairline); background: #fafafa; }
.wiki-context h2 { margin: 0 0 12px; font-size: 12px; }
.wiki-context h2:not(:first-child) { margin-top: 26px; }
.wiki-context dl { display: grid; gap: 9px; margin: 0; font-size: 10px; }
.wiki-context dl div { display: flex; justify-content: space-between; }
.wiki-context dt { color: var(--oa-color-muted); }
.wiki-context dd { margin: 0; }
.wiki-context a { display: block; margin: 10px 0; color: var(--oa-color-link); font-size: 11px; text-decoration: none; }
.empty-context {
  color: var(--oa-color-muted);
  font-size: 12px;
  text-align: center;
  padding: 20px 0;
}
</style>

<style>
.ctx-menu {
  position: fixed;
  z-index: 9999;
  min-width: 168px;
  padding: 5px;
  border: 1px solid var(--oa-color-hairline, #e5e5e5);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(0 0 0 / 14%);
  font-size: 12px;
}
.ctx-menu__header {
  padding: 6px 10px 8px;
  color: var(--oa-color-muted, #888);
  font-size: 10px;
  border-bottom: 1px solid var(--oa-color-hairline, #e5e5e5);
  margin-bottom: 4px;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ctx-menu__item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 5px;
  color: var(--oa-color-ink, #292929);
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-size: 12px;
}
.ctx-menu__item:hover {
  background: var(--oa-color-canvas-inset, #f5f5f5);
}
</style>
