<script setup lang="ts">
import { computed, watch, ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { IconDownload, IconFileText, IconAlertTriangle, IconX, IconEdit, IconDeviceFloppy, IconArrowLeft } from '@tabler/icons-vue'
import { apiClient, apiRequest } from '@/services/api'
import { renderMarkdown } from '@/composables/useMarkdown'
import type { FilePreview } from '@/types'
import VueOfficeDocx from '@vue-office/docx'
import VueOfficeExcel from '@vue-office/excel'
import VueOfficePptx from '@vue-office/pptx'
import '@vue-office/docx/lib/index.css'
import '@vue-office/excel/lib/index.css'

const route = useRoute()
const preview = ref<FilePreview | null>(null)
const loading = ref(false)
const error = ref('')
const blobUrl = ref('')
const officeData = ref<ArrayBuffer | null>(null)

const editing = ref(false)
const editContent = ref('')
const saving = ref(false)

const fileId = computed(() => Number(route.params.fileId))

const canEdit = computed(() => {
  const t = preview.value?.previewType
  return t === 'markdown' || t === 'text'
})

const renderedMarkdown = computed(() => {
  if (!preview.value?.extractedText) return ''
  return renderMarkdown(preview.value.extractedText)
})

function formatSize(size: number) {
  if (!size) return '—'
  return size > 1_000_000 ? `${(size / 1_000_000).toFixed(1)} MB` : `${Math.round(size / 1000)} KB`
}

function formatTime(dateStr: string) {
  if (!dateStr) return '—'
  return dateStr.replace('T', ' ').substring(0, 16)
}

async function loadPreview(id: number) {
  loading.value = true
  error.value = ''
  preview.value = null
  editing.value = false
  if (blobUrl.value) {
    URL.revokeObjectURL(blobUrl.value)
    blobUrl.value = ''
  }
  officeData.value = null
  try {
    const data = await apiRequest<FilePreview>({ url: `/files/${id}/preview` })
    if (data) {
      preview.value = data
      if (data.previewType === 'pdf' || data.previewType === 'image') {
        try {
          const response = await apiClient.get(`/files/${id}/download`, { responseType: 'blob' })
          blobUrl.value = URL.createObjectURL(response.data)
        } catch {
          // fallback to text
        }
      } else if (data.previewType === 'docx' || data.previewType === 'xlsx' || data.previewType === 'pptx') {
        try {
          const response = await apiClient.get(`/files/${id}/download`, { responseType: 'arraybuffer' })
          let raw = response.data
          // Electron may return Node.js Buffer instead of ArrayBuffer
          if (raw && typeof raw.buffer !== 'undefined' && !(raw instanceof ArrayBuffer)) {
            raw = raw.buffer.slice(raw.byteOffset, raw.byteOffset + raw.byteLength)
          }
          officeData.value = raw
        } catch (err) {
          console.error('Office file download failed:', err)
          // fallback to text
        }
      }
    } else {
      error.value = '无法加载文件预览，请确认后端服务已启动'
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '预览加载失败'
  } finally {
    loading.value = false
  }
}

async function handleDownload() {
  if (!preview.value) return
  const response = await apiClient.get(`/files/${preview.value.id}/download`, { responseType: 'blob' })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = preview.value.name
  anchor.click()
  URL.revokeObjectURL(url)
}

function handleClose() {
  window.close()
}

function startEdit() {
  editContent.value = preview.value?.extractedText || ''
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  editContent.value = ''
}

async function saveEdit() {
  if (!preview.value) return
  saving.value = true
  try {
    const data = await apiRequest<FilePreview>({
      method: 'PUT',
      url: `/files/${preview.value.id}/content`,
      data: { content: editContent.value },
    })
    if (data) {
      preview.value = data
      editing.value = false
      editContent.value = ''
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

// Load on mount
onMounted(() => {
  loadPreview(fileId.value)
  // Listen for navigation events from main process (when user opens another file in same window)
  window.openatom?.preview?.onNavigate((payload) => {
    if (payload.fileId) loadPreview(payload.fileId)
  })
})

// Reload when route param changes
watch(fileId, (id) => {
  if (id) loadPreview(id)
})

onUnmounted(() => {
  if (blobUrl.value) URL.revokeObjectURL(blobUrl.value)
})
</script>

<template>
  <div class="preview-window">
    <!-- Header bar -->
    <header class="preview-header">
      <div class="header-left">
        <h1 class="file-name">{{ preview?.name || '文件预览' }}</h1>
        <div class="file-meta">
          <span v-if="preview" class="meta-chip">{{ preview.extension }}</span>
          <span v-if="preview" class="meta-chip">{{ formatSize(preview.size) }}</span>
          <span v-if="preview" class="meta-chip">{{ preview.createdBy || '—' }}</span>
          <span v-if="preview" class="meta-chip">{{ formatTime(preview.updatedAt) }}</span>
          <span v-if="preview" class="meta-chip" :class="preview.status === 'READY' ? 'status-ready' : 'status-pending'">
            {{ preview.status === 'READY' ? '已就绪' : preview.status === 'PARSING' ? '解析中' : preview.status }}
          </span>
        </div>
      </div>
      <div class="header-actions">
        <button v-if="canEdit && !editing" class="header-btn" @click="startEdit" title="编辑">
          <IconEdit :size="18" />
        </button>
        <template v-if="editing">
          <button class="header-btn" :disabled="saving" @click="cancelEdit" title="取消编辑">
            <IconArrowLeft :size="18" />
          </button>
          <button class="header-btn save-btn" :disabled="saving" @click="saveEdit" title="保存">
            <IconDeviceFloppy :size="18" />
          </button>
        </template>
        <button class="header-btn" :disabled="!preview" @click="handleDownload" title="下载原文件">
          <IconDownload :size="18" />
        </button>
        <button class="header-btn close-btn" @click="handleClose" title="关闭窗口">
          <IconX :size="18" />
        </button>
      </div>
    </header>

    <!-- Content -->
    <main class="preview-main">
        <!-- Loading -->
        <div v-if="loading" class="state-box">
          <div class="spinner" />
          <p>正在加载文件内容…</p>
        </div>

        <!-- Error -->
        <div v-else-if="error" class="state-box error">
          <IconAlertTriangle :size="28" />
          <p>{{ error }}</p>
        </div>

        <!-- Edit Mode -->
        <template v-else-if="editing">
          <textarea
            v-model="editContent"
            class="edit-area"
            :placeholder="preview?.previewType === 'markdown' ? '输入 Markdown 内容…' : '输入文本内容…'"
          />
        </template>

        <!-- View Mode -->
        <template v-else-if="preview">
          <!-- Markdown -->
          <article
            v-if="preview.previewType === 'markdown'"
            class="markdown-body"
            v-html="renderedMarkdown"
          />

          <!-- Plain text -->
          <pre v-else-if="preview.previewType === 'text'" class="text-body">{{ preview.extractedText || '（无文本内容）' }}</pre>

          <!-- PDF -->
          <iframe
            v-else-if="preview.previewType === 'pdf' && blobUrl"
            :src="blobUrl"
            class="pdf-viewer"
            title="PDF 预览"
          />

          <!-- Image -->
          <img
            v-else-if="preview.previewType === 'image' && blobUrl"
            :src="blobUrl"
            :alt="preview.name"
            class="image-viewer"
          >

          <!-- Word (docx) -->
          <VueOfficeDocx
            v-else-if="preview.previewType === 'docx' && officeData"
            :src="officeData"
            class="office-viewer"
          />

          <!-- Excel (xlsx) -->
          <VueOfficeExcel
            v-else-if="preview.previewType === 'xlsx' && officeData"
            :src="officeData"
            class="office-viewer"
          />

          <!-- PowerPoint (pptx) -->
          <VueOfficePptx
            v-else-if="preview.previewType === 'pptx' && officeData"
            :src="officeData"
            class="office-viewer"
          />

          <!-- Fallback -->
          <pre v-else-if="preview.extractedText" class="text-body">{{ preview.extractedText }}</pre>

          <!-- No content -->
          <div v-else class="state-box">
            <IconFileText :size="32" />
            <p>该文件暂无可预览的文本内容</p>
            <p class="hint">可尝试下载原文件查看</p>
          </div>
        </template>
      </main>
  </div>
</template>

<style scoped>
.preview-window {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f6f7;
}

/* ---- Header ---- */
.preview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px;
  background: #fff;
  border-bottom: 1px solid #e4e6ea;
  -webkit-app-region: drag;
}

.header-left {
  min-width: 0;
  flex: 1;
}

.file-name {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.meta-chip {
  padding: 2px 8px;
  border-radius: 4px;
  background: #f0f1f3;
  color: #666;
  font-size: 11px;
  line-height: 18px;
}

.status-ready { background: #e6f4ea; color: #1a7d3a; }
.status-pending { background: #fff4e0; color: #9a6500; }

.header-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  -webkit-app-region: no-drag;
}

.header-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #555;
  cursor: pointer;
  transition: background .15s, color .15s;
}

.header-btn:hover {
  background: #eee;
  color: #1a1a1a;
}

.header-btn:disabled {
  opacity: .4;
  cursor: default;
}

.close-btn:hover {
  background: #fee;
  color: #c0392b;
}

.save-btn { color: #2563eb; }
.save-btn:hover { background: #eff6ff; color: #1d4ed8; }

/* ---- Edit Area ---- */
.edit-area {
  width: 100%;
  height: 100%;
  min-height: calc(100vh - 80px);
  padding: 32px 40px;
  border: 0;
  outline: 0;
  resize: none;
  background: #fff;
  color: #2c2c2c;
  font-family: 'SF Mono', 'Fira Code', var(--oa-font-mono, monospace);
  font-size: 14px;
  line-height: 1.8;
  box-shadow: inset 0 1px 0 #e4e6ea;
}

.edit-area:focus { box-shadow: inset 0 2px 0 #4a7eff; }

/* ---- Main ---- */
.preview-main {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  min-height: 300px;
  padding: 80px 24px;
  color: #999;
  font-size: 13px;
  place-content: center;
  place-items: center;
}

.state-box.error { color: #c0392b; }
.hint { font-size: 11px; color: #bbb; }

.spinner {
  width: 24px; height: 24px;
  border: 2.5px solid #e0e0e0;
  border-top-color: #4a7eff;
  border-radius: 50%;
  animation: spin .7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

/* ---- Markdown ---- */
.markdown-body {
  max-width: 820px;
  margin: 0 auto;
  padding: 32px 40px 64px;
  color: #2c2c2c;
  font-size: 15px;
  line-height: 1.8;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
}

.markdown-body :deep(h1) {
  margin: 0 0 20px;
  font-size: 26px;
  font-weight: 700;
  border-bottom: 2px solid #eee;
  padding-bottom: 10px;
}

.markdown-body :deep(h2) {
  margin: 32px 0 14px;
  font-size: 20px;
  font-weight: 700;
  border-bottom: 1px solid #eee;
  padding-bottom: 6px;
}

.markdown-body :deep(h3) {
  margin: 28px 0 12px;
  font-size: 17px;
  font-weight: 600;
}

.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin: 24px 0 10px;
  font-size: 15px;
  font-weight: 600;
}

.markdown-body :deep(p) {
  margin: 0 0 16px;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.markdown-body :deep(ol),
.markdown-body :deep(ul) {
  padding-left: 28px;
  margin: 0 0 16px;
}

.markdown-body :deep(li) {
  margin: 6px 0;
  line-height: 1.75;
}

.markdown-body :deep(li > p) { margin: 0; }

.markdown-body :deep(blockquote) {
  margin: 16px 0;
  padding: 12px 20px;
  border-left: 4px solid #4a7eff;
  background: #f8f9fb;
  border-radius: 0 8px 8px 0;
  color: #555;
}

.markdown-body :deep(blockquote p:last-child) { margin-bottom: 0; }

.markdown-body :deep(strong) { font-weight: 700; }

.markdown-body :deep(em) { font-style: italic; }

.markdown-body :deep(a) {
  color: #2563eb;
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color .15s;
}

.markdown-body :deep(a:hover) { border-bottom-color: #2563eb; }

.markdown-body :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: #f0f1f3;
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', var(--oa-font-mono, monospace);
  font-size: 13px;
  color: #d63384;
}

.markdown-body :deep(pre.hljs) {
  margin: 16px 0;
  padding: 16px 20px;
  border-radius: 8px;
  background: #1e293b;
  overflow-x: auto;
}

.markdown-body :deep(pre.hljs code) {
  padding: 0;
  background: none;
  color: #e2e8f0;
  font-size: 13px;
  line-height: 1.6;
}

.markdown-body :deep(table) {
  width: 100%;
  margin: 16px 0;
  border-collapse: collapse;
  font-size: 14px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 8px 12px;
  border: 1px solid #e0e2e6;
  text-align: left;
}

.markdown-body :deep(th) {
  background: #f7f8fa;
  font-weight: 600;
}

.markdown-body :deep(hr) {
  border: 0;
  border-top: 1px solid #e0e2e6;
  margin: 24px 0;
}

.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}

/* ---- Text ---- */
.text-body {
  max-width: 820px;
  margin: 0 auto;
  padding: 32px 40px 64px;
  white-space: pre-wrap;
  word-break: break-word;
  color: #333;
  font-family: 'SF Mono', 'Fira Code', var(--oa-font-mono, monospace);
  font-size: 13px;
  line-height: 1.7;
}

/* ---- PDF / Image ---- */
.pdf-viewer { width: 100%; height: calc(100vh - 72px); border: 0; }
.image-viewer { display: block; max-width: 95%; max-height: calc(100vh - 100px); margin: 24px auto; border-radius: 8px; object-fit: contain; }

/* ---- Office Viewer ---- */
.office-viewer {
  width: 100%;
  min-height: calc(100vh - 80px);
  padding: 24px;
  background: #fff;
}
</style>
