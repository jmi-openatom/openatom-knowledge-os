<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { IconDownload, IconEye, IconFile, IconFilter, IconSearch, IconTrash, IconUpload } from '@tabler/icons-vue'
import { OaBadge, OaButton, OaEmpty, OaIconButton, OaInput, OaModal, OaTag, useToast } from '@openatom/ui'
import PageHeader from '@/components/PageHeader.vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import { useFilePreviewerStore } from '@/stores/filePreviewer'
import { apiClient } from '@/services/api'

const store = useKnowledgeStore()
const filePreviewer = useFilePreviewerStore()
const keyword = ref('')
const uploadOpen = ref(false)
const selectedFile = ref<File | null>(null)
const { pushToast } = useToast()

const filtered = computed(() => store.files.filter((file) =>
  !keyword.value || file.name.toLowerCase().includes(keyword.value.toLowerCase()) || file.tags.some((tag) => tag.includes(keyword.value)),
))

onMounted(() => store.loadFiles())

function pickFile(event: Event) {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0] || null
}

async function upload() {
  if (!selectedFile.value) return
  const file = selectedFile.value
  try {
    const result = await store.uploadFile(file, ['手动上传'])
    if (!result) {
      pushToast({ title: '上传失败', description: '无法连接后端服务，请确认后端已启动', tone: 'danger' })
      return
    }
    pushToast({ title: '上传成功', description: `${file.name} · 已解析并加入资料库`, tone: 'success' })
    uploadOpen.value = false
    selectedFile.value = null
    await store.loadFiles()
  } catch (error) {
    pushToast({ title: '上传失败', description: error instanceof Error ? error.message : '请检查后端服务', tone: 'danger' })
  }
}

async function download(id: number, name: string) {
  const response = await apiClient.get(`/files/${id}/download`, { responseType: 'blob' })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  URL.revokeObjectURL(url)
}

async function remove(id: number) {
  await store.deleteFile(id)
  pushToast({ title: '文件已移入回收站', tone: 'success' })
}

function formatSize(size: number) {
  return size > 1_000_000 ? `${(size / 1_000_000).toFixed(1)} MB` : `${Math.round(size / 1000)} KB`
}
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="KNOWLEDGE FILES" title="资料库" description="统一上传、解析、索引和管理社团文件。">
      <OaButton variant="outline"><template #prefix><IconFilter :size="16" /></template>筛选</OaButton>
      <OaButton tone="primary" @click="uploadOpen = true"><template #prefix><IconUpload :size="16" /></template>上传资料</OaButton>
    </PageHeader>
    <div class="page-content">
      <OaInput v-model="keyword" placeholder="搜索文件名、标签或创建者" clearable>
        <template #prefix><IconSearch :size="17" /></template>
      </OaInput>
      <div class="file-table">
        <div class="file-table__head"><span>文件</span><span>状态</span><span>创建者</span><span>更新时间</span><span /></div>
        <div v-if="filtered.length" v-for="file in filtered" :key="file.id" class="file-row">
          <div class="file-name"><span><IconFile :size="18" /></span><div><strong>{{ file.name }}</strong><small>{{ file.extension }} · {{ formatSize(file.size) }}</small></div></div>
          <OaBadge style="width: 100px" :tone="file.status === 'READY' ? 'success' : file.status === 'PARSING' ? 'warning' : 'danger'" dot>
            {{ file.status === 'READY' ? '已就绪' : file.status === 'PARSING' ? '解析中' : '失败' }}
          </OaBadge>
          <span>{{ file.createdBy }}</span>
          <time>{{ file.updatedAt }}</time>
          <div class="row-actions">
            <OaIconButton label="预览" size="sm" @click="filePreviewer.show(file.id)"><IconEye :size="15" /></OaIconButton>
            <OaIconButton label="下载" size="sm" @click="download(file.id, file.name)"><IconDownload :size="15" /></OaIconButton>
            <OaIconButton label="删除" size="sm" @click="remove(file.id)"><IconTrash :size="15" /></OaIconButton>
          </div>
        </div>
        <OaEmpty v-else title="资料库为空" description="上传第一份资料，系统将自动解析并建立 AI 知识索引">
          <OaButton tone="primary" @click="uploadOpen = true"><template #prefix><IconUpload :size="16" /></template>上传资料</OaButton>
        </OaEmpty>
      </div>
    </div>
    <OaModal :open="uploadOpen" title="上传资料" description="上传后将自动解析文本、建立索引并进入 AI 知识库" @close="uploadOpen = false">
      <label class="upload-zone">
        <IconUpload :size="28" />
        <strong>{{ selectedFile?.name || '选择文件或拖放到这里' }}</strong>
        <span>支持 Word、Excel、PPT、PDF、Markdown、TXT，单文件最大 100MB</span>
        <input type="file" accept=".doc,.docx,.xls,.xlsx,.ppt,.pptx,.pdf,.md,.txt" @change="pickFile">
      </label>
      <template #footer>
        <OaButton variant="outline" @click="uploadOpen = false">取消</OaButton>
        <OaButton tone="primary" :disabled="!selectedFile" @click="upload">开始上传</OaButton>
      </template>
    </OaModal>
  </div>
</template>

<style scoped>
.page { height: 100%; overflow-y: auto; background: var(--oa-color-canvas-soft); }
.page-content { display: grid; gap: 18px; padding: 24px 32px; }
.file-table { overflow: hidden; border: 1px solid var(--oa-color-hairline); border-radius: 10px; background: #fff; }
.file-table__head, .file-row { display: grid; grid-template-columns: minmax(230px, 1.5fr) minmax(160px, 1fr) 100px 100px 142px 74px; gap: 12px; align-items: center; padding: 0 16px; }
.file-table__head { min-height: 42px; color: var(--oa-color-muted); background: #fafafa; font-family: var(--oa-font-mono); font-size: 10px; }
.file-row { min-height: 68px; border-top: 1px solid var(--oa-color-hairline); color: var(--oa-color-body); font-size: 11px; }
.file-row:hover { background: #fcfcfc; }
.file-name { display: flex; align-items: center; gap: 10px; min-width: 0; }
.file-name > span { display: grid; width: 34px; height: 34px; flex: none; border-radius: 8px; color: #276db4; background: #edf5ff; place-items: center; }
.file-name strong { display: block; overflow: hidden; color: var(--oa-color-ink); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.file-name small { color: var(--oa-color-muted); font-size: 9px; }
.tags, .row-actions { display: flex; gap: 5px; }
.file-row time { font-family: var(--oa-font-mono); font-size: 9px; }
.upload-zone { display: grid; min-height: 220px; padding: 28px; border: 1px dashed #b9c7d7; border-radius: 9px; color: var(--oa-color-muted); background: #fafcff; cursor: pointer; text-align: center; place-content: center; justify-items: center; }
.upload-zone strong { margin-top: 12px; color: var(--oa-color-ink); }
.upload-zone span { margin-top: 7px; font-size: 11px; }
.upload-zone input { display: none; }
</style>
