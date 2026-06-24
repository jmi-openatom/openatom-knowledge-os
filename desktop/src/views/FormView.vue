<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { IconDownload, IconEye, IconLock, IconPencil, IconPlus, IconX } from '@tabler/icons-vue'
import { OaButton, OaIconButton, OaInput, OaModal, OaSelect, OaTag, OaTextarea, useToast } from '@openatom/ui'
import PageHeader from '@/components/PageHeader.vue'
import {
  closeForm,
  createForm,
  exportFormSubmissions,
  getForm,
  getForms,
  getFormSubmissions,
  updateForm,
  fieldLabel,
  fieldOptions,
  formFields,
  formTitle,
  type FormField,
  type FormFieldOption,
  type FormSubmission,
  type SiteForm,
} from '@/services/api'

const { pushToast } = useToast()

const forms = ref<SiteForm[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const statusTone: Record<string, 'neutral' | 'success' | 'warning'> = {
  open: 'success',
  closed: 'warning',
}
const statusLabel: Record<string, string> = { open: '收集中', closed: '已关闭' }

// ---- create/edit modal ----
const modalOpen = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive({
  id: null as number | null,
  name: '',
  status: 'open' as SiteForm['status'],
  startAt: '',
  endAt: '',
  fields: [] as FormField[],
})

const fieldTypeOptions = [
  { label: '单行文本', value: 'text' },
  { label: '多行文本', value: 'textarea' },
  { label: '下拉选择', value: 'select' },
  { label: '数字', value: 'number' },
  { label: '日期', value: 'date' },
  { label: '手机号', value: 'phone' },
  { label: '邮箱', value: 'email' },
]

function resetForm() {
  form.id = null
  form.name = ''
  form.status = 'open'
  form.fields = [{ key: '', label: '', type: 'text', required: false }]
}

function openCreate() {
  resetForm()
  isEdit.value = false
  modalOpen.value = true
}

function openEdit(f: SiteForm) {
  isEdit.value = true
  form.id = f.id
  form.name = formTitle(f)
  form.status = (f.status ?? 'open') as SiteForm['status']
  form.fields = formFields(f).length ? formFields(f).map(f2 => ({ ...f2 })) : [{ key: '', label: '', type: 'text', required: false }]
  modalOpen.value = true
}

function addField() {
  form.fields.push({ key: '', label: '', type: 'text', required: false })
}

function removeField(index: number) {
  form.fields.splice(index, 1)
}

function getSelectOptionsString(): string {
  const sel = form.fields.find(f => f.type === 'select')
  if (!sel || !sel.options) return ''
  if (typeof sel.options[0] === 'object') {
    return (sel.options as FormFieldOption[]).map(o => o.label).join('，')
  }
  return (sel.options as string[]).join('，')
}

function updateSelectOptions(v: string) {
  const sel = form.fields.find(f => f.type === 'select')
  if (sel) {
    sel.options = v.split(/[,，]/).map(s => s.trim()).filter(Boolean).map(s => ({ label: s, value: s }))
  }
}

async function save() {
  if (!form.name.trim()) {
    pushToast({ title: '请填写表单标题', tone: 'warning' })
    return
  }
  const cleanFields = form.fields.filter(f => (f.label || '').trim() || (f.key || '').trim())
  if (cleanFields.length === 0) {
    pushToast({ title: '请至少添加一个字段', tone: 'warning' })
    return
  }
  // Auto-generate key from label if missing
  cleanFields.forEach(f => { if (!f.key) f.key = (f.label || '').trim() })
  saving.value = true
  try {
    const payload = { name: form.name.trim(), status: form.status as SiteForm['status'], fields: cleanFields }
    if (isEdit.value && form.id != null) {
      await updateForm(form.id, payload as Partial<SiteForm>)
      pushToast({ title: '表单已更新', tone: 'success' })
    } else {
      await createForm(payload as { name: string; fields: FormField[]; status?: string })
      pushToast({ title: '表单已创建', tone: 'success' })
    }
    modalOpen.value = false
    await load()
  } catch (err) {
    pushToast({ title: err instanceof Error ? err.message : '保存失败', tone: 'danger' })
  } finally {
    saving.value = false
  }
}

async function close(f: SiteForm) {
  try {
    await closeForm(f.id)
    pushToast({ title: '表单已关闭', tone: 'success' })
    await load()
  } catch (err) {
    pushToast({ title: err instanceof Error ? err.message : '关闭失败', tone: 'danger' })
  }
}

// ---- submissions modal ----
const subModalOpen = ref(false)
const currentForm = ref<SiteForm | null>(null)
const submissions = ref<FormSubmission[]>([])
const subLoading = ref(false)
const subError = ref<string | null>(null)

async function viewSubmissions(f: SiteForm) {
  currentForm.value = f
  subModalOpen.value = true
  subLoading.value = true
  subError.value = null
  submissions.value = []
  try {
    // Ensure we have full field definitions for rendering the table.
    if (!formFields(f).length) {
      const detail = await getForm(f.id)
      if (detail) currentForm.value = detail
    }
    const data = await getFormSubmissions(f.id, { pageSize: 100 })
    submissions.value = data?.list ?? []
  } catch (err) {
    subError.value = err instanceof Error ? err.message : '加载提交记录失败'
  } finally {
    subLoading.value = false
  }
}

const submissionColumns = computed(() => {
  const fields = currentForm.value ? formFields(currentForm.value) : []
  const cols: string[] = []
  if (fields.length) {
    cols.push(...fields.map(fld => fieldLabel(fld)))
  } else {
    submissions.value.forEach(s => {
      const d = s.data || s.formData || {}
      Object.keys(d).forEach(k => { if (!cols.includes(k)) cols.push(k) })
    })
  }
  cols.push('提交时间')
  return cols
})

async function exportExcel(f: SiteForm) {
  try {
    pushToast({ title: '正在导出 Excel…', tone: 'neutral' })
    const blob = await exportFormSubmissions(f.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${formTitle(f).replace(/[\\/?*[\]:]/g, '')}-提交记录.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    pushToast({ title: 'Excel 已下载', tone: 'success' })
  } catch (err) {
    pushToast({ title: err instanceof Error ? err.message : '导出失败', tone: 'danger' })
  }
}

function formatDate(iso?: string): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('zh-CN', { hour12: false })
}

function submissionData(s: FormSubmission): Record<string, unknown> {
  return (s.data || s.formData || {}) as Record<string, unknown>
}

function cellValue(s: FormSubmission, col: string): string {
  if (col === '提交时间') return formatDate(s.createdAt)
  // Try matching by field label first, then by key
  const fields = currentForm.value ? formFields(currentForm.value) : []
  const field = fields.find(f => fieldLabel(f) === col)
  const key = field?.key || col
  const d = submissionData(s)
  const v = d[key] ?? d[col]
  return v == null ? '' : String(v)
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const data = await getForms()
    forms.value = data ?? []
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载表单列表失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="FORM MANAGEMENT" title="表单系统" description="创建报名表/问卷，查看提交记录并导出 Excel，支持 AI 读取表单数据。">
      <OaButton tone="primary" @click="openCreate"><template #prefix><IconPlus :size="16" /></template>创建表单</OaButton>
    </PageHeader>

    <div class="content">
      <div v-if="loading" class="state">加载中…</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="!forms.length" class="state">暂无表单，点击右上角「创建表单」</div>

      <div v-else class="table">
        <div class="head">
          <span>表单</span><span>字段数</span><span>状态</span><span>创建时间</span><span>操作</span>
        </div>
        <div v-for="f in forms" :key="f.id" class="row">
          <div class="title-cell">
            <strong>{{ formTitle(f) }}</strong>
            <small>{{ formFields(f).map(fld => fieldLabel(fld)).join(' / ') || '无字段' }}</small>
          </div>
          <span class="muted">{{ formFields(f).length }}</span>
          <OaTag :tone="statusTone[f.status || ''] || 'neutral'">{{ statusLabel[f.status || ''] || f.status }}</OaTag>
          <time>{{ formatDate(f.createdAt) }}</time>
          <div class="actions">
            <OaIconButton label="编辑" size="sm" @click="openEdit(f)"><IconPencil :size="15" /></OaIconButton>
            <OaIconButton label="查看提交" size="sm" @click="viewSubmissions(f)"><IconEye :size="15" /></OaIconButton>
            <OaIconButton label="导出 Excel" size="sm" @click="exportExcel(f)"><IconDownload :size="15" /></OaIconButton>
            <OaIconButton v-if="f.status === 'open'" label="关闭表单" size="sm" tone="warning" @click="close(f)"><IconLock :size="15" /></OaIconButton>
          </div>
        </div>
      </div>
    </div>

    <!-- Create / edit modal -->
    <OaModal :open="modalOpen" :title="isEdit ? '编辑表单' : '创建表单'" :width="680" @close="modalOpen = false">
      <div class="form">
        <OaInput v-model="form.name" label="表单标题" placeholder="如：春季招新报名表" />
        <div class="form-row">
          <OaSelect v-model="form.status" label="状态" :options="[
            { label: '收集中', value: 'open' },
            { label: '已关闭', value: 'closed' },
          ]" />
        </div>
        <div class="fields-section">
          <div class="fields-head">
            <span>字段定义</span>
            <OaButton variant="soft" size="sm" @click="addField"><template #prefix><IconPlus :size="14" /></template>添加字段</OaButton>
          </div>
          <div v-for="(field, idx) in form.fields" :key="idx" class="field-row">
            <OaInput v-model="field.label" placeholder="字段显示名" />
            <OaInput v-model="field.key" placeholder="字段 key" />
            <OaSelect v-model="field.type" :options="fieldTypeOptions" />
            <label class="checkbox"><input v-model="field.required" type="checkbox" /> 必填</label>
            <OaButton variant="ghost" size="sm" tone="danger" @click="removeField(idx)"><IconX :size="14" /></OaButton>
          </div>
          <OaInput
            v-if="form.fields.some(f => f.type === 'select')"
            :model-value="getSelectOptionsString()"
            label="下拉选项（用逗号分隔，仅对选择类型生效）"
            placeholder="选项1，选项2，选项3"
            @update:model-value="updateSelectOptions"
          />
        </div>
      </div>
      <template #footer>
        <OaButton variant="soft" @click="modalOpen = false">取消</OaButton>
        <OaButton tone="primary" :loading="saving" @click="save">{{ isEdit ? '保存' : '创建' }}</OaButton>
      </template>
    </OaModal>

    <!-- Submissions modal -->
    <OaModal :open="subModalOpen" :title="`提交记录 — ${currentForm ? formTitle(currentForm) : ''}`" :width="860" @close="subModalOpen = false">
      <div v-if="subLoading" class="state">加载中…</div>
      <div v-else-if="subError" class="state error">{{ subError }}</div>
      <div v-else-if="!submissions.length" class="state">暂无提交记录</div>
      <div v-else class="sub-table">
        <div class="sub-head">
          <span v-for="col in submissionColumns" :key="col">{{ col }}</span>
        </div>
        <div v-for="s in submissions" :key="s.id" class="sub-row">
          <span v-for="col in submissionColumns" :key="col" :title="cellValue(s, col)">{{ cellValue(s, col) }}</span>
        </div>
      </div>
      <template #footer>
        <OaButton v-if="currentForm" tone="primary" @click="exportExcel(currentForm)">
          <template #prefix><IconDownload :size="15" /></template>导出 Excel
        </OaButton>
        <OaButton variant="soft" @click="subModalOpen = false">关闭</OaButton>
      </template>
    </OaModal>
  </div>
</template>

<style scoped>
.page { height: 100%; overflow-y: auto; background: var(--oa-color-canvas-soft); }
.content { display: grid; gap: 18px; padding: 24px 32px; }
.state { padding: 32px; text-align: center; color: var(--oa-color-muted); font-size: 13px; }
.state.error { color: var(--oa-color-danger); }
.table { overflow: hidden; border: 1px solid var(--oa-color-hairline); border-radius: 10px; background: #fff; }
.head, .row { display: grid; grid-template-columns: minmax(260px, 1.6fr) 80px 110px 170px 280px; gap: 16px; align-items: center; padding: 0 18px; }
.head { height: 42px; color: var(--oa-color-muted); background: #fafafa; font-family: var(--oa-font-mono); font-size: 10px; }
.row { min-height: 64px; border-top: 1px solid var(--oa-color-hairline); }
.title-cell strong { display: block; font-size: 12px; }
.title-cell small { display: block; color: var(--oa-color-muted); font-size: 10px; margin-top: 2px; }
.muted { color: var(--oa-color-muted); font-size: 11px; }
.actions { display: flex; gap: 2px; align-items: center; }
.form { display: grid; gap: 14px; }
.form-row { display: grid; grid-template-columns: 1fr; gap: 14px; }
.fields-section { display: grid; gap: 10px; }
.fields-head { display: flex; align-items: center; justify-content: space-between; font-size: 12px; color: var(--oa-color-body); }
.field-row { display: grid; grid-template-columns: 1.2fr 1fr 0.8fr 80px 40px; gap: 10px; align-items: center; }
.checkbox { display: flex; align-items: center; gap: 6px; font-size: 11px; color: var(--oa-color-body); }
.sub-table { overflow-x: auto; max-height: 60vh; overflow-y: auto; border: 1px solid var(--oa-color-hairline); border-radius: 8px; }
.sub-head, .sub-row { display: grid; grid-auto-columns: minmax(120px, 1fr); grid-auto-flow: column; gap: 12px; padding: 0 14px; }
.sub-head { height: 40px; align-items: center; color: var(--oa-color-muted); background: #fafafa; font-family: var(--oa-font-mono); font-size: 10px; }
.sub-row { min-height: 48px; align-items: center; border-top: 1px solid var(--oa-color-hairline); font-size: 11px; }
.sub-row span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
