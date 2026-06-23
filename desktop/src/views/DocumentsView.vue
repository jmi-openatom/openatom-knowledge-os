<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { IconDeviceFloppy, IconHistory, IconPlus, IconTrash } from '@tabler/icons-vue'
import { OaButton, OaEmpty, OaInput, OaModal, OaSpinner, OaTag, useToast } from '@openatom/ui'
import PageHeader from '@/components/PageHeader.vue'
import { apiRequest, createDocument, deleteDocument, getDocumentVersions } from '@/services/api'
import type { DocumentVersion, KnowledgeDocument } from '@/types'

const activeId = ref<number | null>(null)
const documentList = ref<KnowledgeDocument[]>([])
const title = ref('')
const content = ref('')
const dirty = ref(false)
const { pushToast } = useToast()
const active = computed(() => documentList.value.find((item) => item.id === activeId.value) || null)

// Modal states
const showVersionsModal = ref(false)
const showDeleteConfirmModal = ref(false)
const versions = ref<DocumentVersion[]>([])
const documentToDelete = ref<number | null>(null)
const loading = ref(false)
const initialLoading = ref(false)

onMounted(async () => {
  initialLoading.value = true
  try {
    const response = await apiRequest<KnowledgeDocument[]>({ url: '/documents' })
    if (response?.length) {
      documentList.value = response
      selectDocument(response[0].id)
    }
  } finally {
    initialLoading.value = false
  }
})

function selectDocument(id: number) {
  const document = documentList.value.find((item) => item.id === id)
  if (!document) return
  activeId.value = id
  title.value = document.title
  content.value = document.content
  dirty.value = false
}

async function save() {
  if (!activeId.value) return
  const response = await apiRequest<KnowledgeDocument>({
    method: 'PUT',
    url: `/documents/${activeId.value}`,
    data: {
      id: activeId.value,
      title: title.value,
      content: content.value,
      version: active.value?.version,
    },
  })
  if (response) documentList.value = documentList.value.map((item) => item.id === response.id ? response : item)
  dirty.value = false
  pushToast({ title: '文档已保存', description: `${title.value} · 自动生成新版本`, tone: 'success' })
}

async function handleCreateDocument() {
  loading.value = true
  try {
    const newDoc = await createDocument({ title: '未命名文档', content: '' })
    if (newDoc) {
      const doc = newDoc as KnowledgeDocument
      documentList.value.unshift(doc)
      selectDocument(doc.id)
      pushToast({ title: '文档创建成功', description: '开始编辑你的新文档', tone: 'success' })
    } else {
      pushToast({ title: '创建失败', description: '无法连接后端服务', tone: 'danger' })
    }
  } catch (error) {
    pushToast({ title: '创建失败', description: '无法创建新文档，请重试', tone: 'danger' })
  } finally {
    loading.value = false
  }
}

async function handleShowVersions() {
  if (!activeId.value) return
  loading.value = true
  try {
    const versionList = await getDocumentVersions(activeId.value)
    if (versionList) {
      versions.value = versionList as DocumentVersion[]
      showVersionsModal.value = true
    } else {
      versions.value = []
      showVersionsModal.value = true
    }
  } catch (error) {
    pushToast({ title: '获取失败', description: '无法加载版本历史', tone: 'danger' })
  } finally {
    loading.value = false
  }
}

function handleDeleteClick(id: number) {
  documentToDelete.value = id
  showDeleteConfirmModal.value = true
}

async function confirmDelete() {
  if (!documentToDelete.value) return
  loading.value = true
  try {
    await deleteDocument(documentToDelete.value)
    documentList.value = documentList.value.filter(doc => doc.id !== documentToDelete.value)
    if (activeId.value === documentToDelete.value && documentList.value.length > 0) {
      selectDocument(documentList.value[0].id)
    } else if (documentList.value.length === 0) {
      activeId.value = null
      title.value = ''
      content.value = ''
    }
    pushToast({ title: '删除成功', description: '文档已被永久删除', tone: 'success' })
    showDeleteConfirmModal.value = false
    documentToDelete.value = null
  } catch (error) {
    pushToast({ title: '删除失败', description: '无法删除文档，请重试', tone: 'danger' })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="ONLINE DOCUMENTS" title="在线文档" description="以 Markdown 为基础的社团协作文档空间。">
      <OaButton variant="outline" @click="handleShowVersions"><template #prefix><IconHistory :size="16" /></template>历史版本</OaButton>
      <OaButton tone="primary" :disabled="loading" @click="handleCreateDocument"><template #prefix><IconPlus :size="16" /></template>新建文档</OaButton>
    </PageHeader>
    <div class="documents-layout">
      <aside>
        <h2>文档列表</h2>
        <div v-if="initialLoading" class="loading-state"><OaSpinner :size="18" /> 加载中…</div>
        <template v-else>
          <div v-for="document in documentList" :key="document.id" class="doc-item">
            <button :class="{ active: document.id === activeId }" @click="selectDocument(document.id)">
              <div class="doc-info">
                <strong>{{ document.title }}</strong>
                <span>v{{ document.version }} · {{ document.updatedAt }}</span>
              </div>
            </button>
            <button class="delete-btn" @click="handleDeleteClick(document.id)" title="删除文档">
              <IconTrash :size="14" />
            </button>
          </div>
        </template>
      </aside>
      <main>
        <OaEmpty v-if="!active" title="暂无文档" description="点击右上角新建文档开始协作">
          <OaButton tone="primary" @click="handleCreateDocument"><template #prefix><IconPlus :size="16" /></template>新建文档</OaButton>
        </OaEmpty>
        <template v-else>
          <div class="editor-toolbar">
            <OaTag tone="success" dot>{{ dirty ? '待保存' : '已保存' }}</OaTag>
            <span>最近编辑：{{ active.updatedBy }} · {{ active.updatedAt }}</span>
            <OaButton size="sm" tone="primary" :disabled="!dirty" @click="save"><template #prefix><IconDeviceFloppy :size="15" /></template>保存</OaButton>
          </div>
          <OaInput v-model="title" size="lg" @update:model-value="dirty = true" />
          <textarea v-model="content" aria-label="文档内容" @input="dirty = true" />
        </template>
      </main>
    </div>

    <!-- Versions Modal -->
    <OaModal :open="showVersionsModal" title="历史版本" description="查看文档的修改记录" @close="showVersionsModal = false">
      <div v-if="versions.length === 0" style="text-align: center; color: var(--oa-color-muted); padding: 20px;">
        暂无版本记录
      </div>
      <div v-else class="versions-list">
        <div v-for="version in versions" :key="version.id" class="version-item">
          <div class="version-header">
            <strong>v{{ version.version }}</strong>
            <span class="version-time">{{ version.createdAt }}</span>
          </div>
          <div class="version-title">{{ version.title }}</div>
          <div class="version-author">作者: {{ version.createdByName }}</div>
        </div>
      </div>
      <template #footer>
        <OaButton @click="showVersionsModal = false">关闭</OaButton>
      </template>
    </OaModal>

    <!-- Delete Confirmation Modal -->
    <OaModal :open="showDeleteConfirmModal" title="确认删除" description="此操作不可恢复，确定要删除该文档吗？" @close="showDeleteConfirmModal = false">
      <p style="margin: 0; color: var(--oa-color-muted); font-size: 13px;">
        删除后，该文档及其所有版本历史都将被永久移除。
      </p>
      <template #footer>
        <OaButton variant="outline" @click="showDeleteConfirmModal = false">取消</OaButton>
        <OaButton tone="danger" :disabled="loading" @click="confirmDelete">
          {{ loading ? '删除中...' : '确认删除' }}
        </OaButton>
      </template>
    </OaModal>
  </div>
</template>

<style scoped>
.page { height: 100%; overflow: hidden; background: var(--oa-color-canvas-soft); }
.documents-layout { display: grid; grid-template-columns: 280px minmax(0, 1fr); height: calc(100% - 104px); }
.documents-layout > aside { padding: 20px 12px; overflow-y: auto; border-right: 1px solid var(--oa-color-hairline); background: #fff; }
.documents-layout aside h2 { margin: 0 10px 12px; color: var(--oa-color-muted); font-size: 11px; }
.loading-state { display: flex; align-items: center; gap: 8px; padding: 16px 10px; color: var(--oa-color-muted); font-size: 12px; }
.doc-item { display: flex; align-items: center; gap: 6px; padding: 2px; margin-bottom: 4px; }
.doc-item button:first-child { flex: 1; min-width: 0; padding: 11px 10px; border: 0; border-radius: 7px; background: transparent; cursor: pointer; text-align: left; }
.doc-item button:first-child:hover, .doc-item button:first-child.active { background: #f1f6fc; }
.doc-info { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.documents-layout aside strong { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; line-height: 1.3; }
.documents-layout aside span { color: var(--oa-color-muted); font-size: 9px; }
.delete-btn { display: flex; align-items: center; justify-content: center; width: 28px; height: 28px; padding: 0; border: 0; border-radius: 6px; color: var(--oa-color-muted); background: transparent; cursor: pointer; opacity: 0; transition: all 0.15s; flex-shrink: 0; }
.delete-btn:hover { color: #dc3545; background: rgb(220 53 69 / 10%); }
.doc-item:hover .delete-btn { opacity: 1; }
.documents-layout main { display: flex; min-width: 0; flex-direction: column; gap: 14px; padding: 22px 28px 28px; }
.editor-toolbar { display: flex; align-items: center; gap: 10px; }
.editor-toolbar > span { flex: 1; color: var(--oa-color-muted); font-size: 10px; }
.documents-layout textarea { min-height: 0; flex: 1; padding: 24px; resize: none; border: 1px solid var(--oa-color-hairline); border-radius: 10px; outline: 0; background: #fff; box-shadow: var(--oa-shadow-1); font-family: var(--oa-font-mono); font-size: 13px; line-height: 1.8; }
.documents-layout textarea:focus { border-color: #9bc7f8; }

/* Versions list styles */
.versions-list { display: grid; gap: 12px; max-height: 400px; overflow-y: auto; }
.version-item { padding: 14px; border: 1px solid var(--oa-color-hairline); border-radius: 8px; background: var(--oa-color-canvas-soft); }
.version-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.version-header strong { font-size: 13px; color: var(--oa-color-primary); }
.version-time { font-size: 11px; color: var(--oa-color-muted); }
.version-title { font-size: 12px; margin-bottom: 4px; color: var(--oa-color-ink); }
.version-author { font-size: 11px; color: var(--oa-color-muted); }
</style>
