<script setup lang="ts">
import { computed, watch, ref } from 'vue'
import { IconDownload, IconFileText, IconAlertTriangle, IconEdit, IconDeviceFloppy } from '@tabler/icons-vue'
import { renderMarkdown } from '@/composables/useMarkdown'
import { OaBadge, OaButton, OaModal, OaSpinner } from '@openatom/ui'
import { useFilePreviewerStore } from '@/stores/filePreviewer'
import { apiClient, apiRequest } from '@/services/api'
import type { FilePreview } from '@/types'
import VueOfficeDocx from '@vue-office/docx'
import VueOfficeExcel from '@vue-office/excel'
import VueOfficePptx from '@vue-office/pptx'
import '@vue-office/docx/lib/index.css'
import '@vue-office/excel/lib/index.css'

const store = useFilePreviewerStore()

const blobUrl = ref('')
const officeData = ref<ArrayBuffer | null>(null)
const editing = ref(false)
const editContent = ref('')
const saving = ref(false)
const renderedMarkdown = computed(() => {
  if (!store.preview?.extractedText) return ''
  return renderMarkdown(store.preview.extractedText)
})

const canEdit = computed(() => {
  const t = store.preview?.previewType
  return t === 'markdown' || t === 'text'
})

function formatSize(size: number) {
  if (!size) return '—'
  return size > 1_000_000 ? `${(size / 1_000_000).toFixed(1)} MB` : `${Math.round(size / 1000)} KB`
}

function formatTime(dateStr: string) {
  if (!dateStr) return '—'
  return dateStr.replace('T', ' ').substring(0, 16)
}

// Load blob for PDF/image/office types
watch(() => store.preview, async (preview) => {
  blobUrl.value = ''
  officeData.value = null
  editing.value = false
  if (!preview) return
  if (preview.previewType === 'pdf' || preview.previewType === 'image') {
    try {
      const response = await apiClient.get(`/files/${preview.id}/download`, { responseType: 'blob' })
      blobUrl.value = URL.createObjectURL(response.data)
    } catch {
      // Fallback: show extracted text
    }
  } else if (preview.previewType === 'docx' || preview.previewType === 'xlsx' || preview.previewType === 'pptx') {
    try {
      const response = await apiClient.get(`/files/${preview.id}/download`, { responseType: 'arraybuffer' })
      let data = response.data
      // Electron may return Node.js Buffer instead of ArrayBuffer
      if (data && typeof data.buffer !== 'undefined' && !(data instanceof ArrayBuffer)) {
        data = data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength)
      }
      officeData.value = data
    } catch (err) {
      console.error('Office file download failed:', err)
      // Fallback: show extracted text
    }
  }
}, { immediate: true })

watch(() => store.open, (isOpen) => {
  if (!isOpen) {
    if (blobUrl.value) URL.revokeObjectURL(blobUrl.value)
    blobUrl.value = ''
    officeData.value = null
    editing.value = false
  }
})

async function handleDownload() {
  if (!store.preview) return
  await store.download(store.preview.id, store.preview.name)
}

function startEdit() {
  editContent.value = store.preview?.extractedText || ''
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  editContent.value = ''
}

async function saveEdit() {
  if (!store.preview) return
  saving.value = true
  try {
    const data = await apiRequest<FilePreview>({
      method: 'PUT',
      url: `/files/${store.preview.id}/content`,
      data: { content: editContent.value },
    })
    if (data) {
      store.preview = data
      editing.value = false
      editContent.value = ''
    }
  } catch (e) {
    store.error = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <OaModal
    :open="store.open"
    :title="store.preview?.name || '文件预览'"
    :description="store.preview ? `${store.preview.extension} · ${formatSize(store.preview.size)}` : ''"
    :width="860"
    @close="store.close()"
  >
    <div class="file-previewer">
      <!-- Loading -->
      <div v-if="store.loading" class="preview-loading">
        <OaSpinner :size="24" />
        <span>正在加载文件内容…</span>
      </div>

      <!-- Error -->
      <div v-else-if="store.error" class="preview-error">
        <IconAlertTriangle :size="24" />
        <p>{{ store.error }}</p>
      </div>

      <!-- Preview content -->
      <template v-else-if="store.preview">
        <!-- Metadata bar -->
        <div class="preview-meta">
          <div class="meta-item">
            <span class="meta-label">创建者</span>
            <span class="meta-value">{{ store.preview.createdBy || '—' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">更新时间</span>
            <span class="meta-value">{{ formatTime(store.preview.updatedAt) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">大小</span>
            <span class="meta-value">{{ formatSize(store.preview.size) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">状态</span>
            <OaBadge :tone="store.preview.status === 'READY' ? 'success' : 'warning'" dot>
              {{ store.preview.status === 'READY' ? '已就绪' : store.preview.status === 'PARSING' ? '解析中' : '其他' }}
            </OaBadge>
          </div>
        </div>

        <!-- Content area -->
        <div class="preview-content">
          <!-- Edit Mode -->
          <textarea
            v-if="editing"
            v-model="editContent"
            class="edit-area"
            :placeholder="store.preview?.previewType === 'markdown' ? '输入 Markdown 内容…' : '输入文本内容…'"
          />

          <!-- Markdown -->
          <div v-else-if="store.preview.previewType === 'markdown'" class="markdown-body" v-html="renderedMarkdown" />

          <!-- Plain text -->
          <pre v-else-if="store.preview.previewType === 'text'" class="text-body">{{ store.preview.extractedText || '（无文本内容）' }}</pre>

          <!-- PDF -->
          <iframe
            v-else-if="store.preview.previewType === 'pdf' && blobUrl"
            :src="blobUrl"
            class="pdf-viewer"
            title="PDF 预览"
          />

          <!-- Image -->
          <img
            v-else-if="store.preview.previewType === 'image' && blobUrl"
            :src="blobUrl"
            :alt="store.preview.name"
            class="image-viewer"
          >

          <!-- Word (docx) -->
          <VueOfficeDocx
            v-else-if="store.preview.previewType === 'docx' && officeData"
            :src="officeData"
            class="office-viewer"
          />

          <!-- Excel (xlsx) -->
          <VueOfficeExcel
            v-else-if="store.preview.previewType === 'xlsx' && officeData"
            :src="officeData"
            class="office-viewer"
          />

          <!-- PowerPoint (pptx) -->
          <VueOfficePptx
            v-else-if="store.preview.previewType === 'pptx' && officeData"
            :src="officeData"
            class="office-viewer"
          />

          <!-- Fallback: show extracted text -->
          <pre v-else-if="store.preview.extractedText" class="text-body">{{ store.preview.extractedText }}</pre>

          <!-- No content -->
          <div v-else class="preview-empty">
            <IconFileText :size="32" />
            <p>该文件暂无可预览的文本内容</p>
            <p class="preview-empty__hint">可尝试下载原文件查看</p>
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <template v-if="editing">
        <OaButton variant="outline" :disabled="saving" @click="cancelEdit">取消</OaButton>
        <OaButton tone="primary" :loading="saving" @click="saveEdit">
          <template #prefix><IconDeviceFloppy :size="16" /></template>
          保存
        </OaButton>
      </template>
      <template v-else>
        <OaButton v-if="canEdit" variant="outline" @click="startEdit">
          <template #prefix><IconEdit :size="16" /></template>
          编辑
        </OaButton>
        <OaButton variant="outline" @click="store.close()">关闭</OaButton>
        <OaButton tone="primary" :disabled="!store.preview" @click="handleDownload">
          <template #prefix><IconDownload :size="16" /></template>
          下载原文件
        </OaButton>
      </template>
    </template>
  </OaModal>
</template>

<style scoped>
.file-previewer { display: flex; flex-direction: column; gap: 16px; }
.preview-loading, .preview-error, .preview-empty {
  display: flex; flex-direction: column; align-items: center; gap: 12px; min-height: 300px; color: var(--oa-color-muted); font-size: 13px; place-content: center; place-items: center;
}
.preview-error { color: #c0392b; }
.preview-empty__hint { color: var(--oa-color-muted); font-size: 11px; }

.preview-meta {
  display: flex; flex-wrap: wrap; gap: 20px; padding: 12px 16px;
  border: 1px solid var(--oa-color-hairline); border-radius: 8px; background: var(--oa-color-canvas-soft);
}
.meta-item { display: flex; flex-direction: column; gap: 3px; }
.meta-label { color: var(--oa-color-muted); font-size: 10px; }
.meta-value { color: var(--oa-color-body); font-size: 12px; }

.preview-content {
  max-height: 60vh; overflow-y: auto;
  border: 1px solid var(--oa-color-hairline); border-radius: 8px;
  background: #fff;
}

.markdown-body { padding: 24px 28px; color: #2c2c2c; font-size: 14px; line-height: 1.8; }
.markdown-body :deep(h1) { margin: 0 0 16px; font-size: 22px; font-weight: 700; border-bottom: 2px solid #eee; padding-bottom: 8px; }
.markdown-body :deep(h2) { margin: 24px 0 12px; font-size: 17px; font-weight: 700; border-bottom: 1px solid #eee; padding-bottom: 5px; }
.markdown-body :deep(h3) { margin: 20px 0 10px; font-size: 15px; font-weight: 600; }
.markdown-body :deep(h4), .markdown-body :deep(h5), .markdown-body :deep(h6) { margin: 16px 0 8px; font-size: 14px; font-weight: 600; }
.markdown-body :deep(p) { margin: 0 0 12px; word-break: break-word; overflow-wrap: anywhere; }
.markdown-body :deep(ol), .markdown-body :deep(ul) { padding-left: 24px; margin: 0 0 12px; }
.markdown-body :deep(li) { margin: 4px 0; line-height: 1.75; }
.markdown-body :deep(li > p) { margin: 0; }
.markdown-body :deep(strong) { font-weight: 700; }
.markdown-body :deep(em) { font-style: italic; }
.markdown-body :deep(a) { color: #2563eb; text-decoration: none; border-bottom: 1px solid transparent; }
.markdown-body :deep(a:hover) { border-bottom-color: #2563eb; }
.markdown-body :deep(code) { padding: 2px 6px; border-radius: 4px; background: #f0f1f3; font-family: 'SF Mono', var(--oa-font-mono, monospace); font-size: 12px; color: #d63384; }
.markdown-body :deep(pre.hljs) { margin: 12px 0; padding: 14px 16px; border-radius: 8px; background: #1e293b; overflow-x: auto; }
.markdown-body :deep(pre.hljs code) { padding: 0; background: none; color: #e2e8f0; font-size: 12px; line-height: 1.6; }
.markdown-body :deep(blockquote) { margin: 12px 0; padding: 10px 16px; border-left: 4px solid #4a7eff; background: #f8f9fb; border-radius: 0 6px 6px 0; color: #555; }
.markdown-body :deep(blockquote p:last-child) { margin-bottom: 0; }
.markdown-body :deep(table) { width: 100%; margin: 12px 0; border-collapse: collapse; font-size: 13px; }
.markdown-body :deep(th), .markdown-body :deep(td) { padding: 6px 10px; border: 1px solid #e0e2e6; text-align: left; }
.markdown-body :deep(th) { background: #f7f8fa; font-weight: 600; }
.markdown-body :deep(hr) { border: 0; border-top: 1px solid #e0e2e6; margin: 20px 0; }
.markdown-body :deep(img) { max-width: 100%; border-radius: 6px; }

.text-body {
  margin: 0; padding: 20px 24px; white-space: pre-wrap; word-break: break-word;
  color: #333; font-family: var(--oa-font-mono); font-size: 12px; line-height: 1.7;
}

.pdf-viewer { width: 100%; height: 60vh; border: 0; }
.image-viewer { display: block; max-width: 100%; max-height: 60vh; margin: 0 auto; padding: 20px; object-fit: contain; }
.office-viewer { width: 100%; min-height: 400px; padding: 8px; }
.edit-area { width: 100%; min-height: 400px; max-height: 60vh; padding: 20px 24px; border: 0; outline: 0; resize: none; background: #fff; color: #333; font-family: var(--oa-font-mono); font-size: 13px; line-height: 1.8; }
</style>
