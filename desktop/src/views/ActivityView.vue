<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { IconEdit, IconPlus, IconSearch, IconTrash } from '@tabler/icons-vue'
import { OaButton, OaInput, OaModal, OaSelect, OaTag, OaTextarea, useToast } from '@openatom/ui'
import PageHeader from '@/components/PageHeader.vue'
import {
  createActivity,
  deleteActivity,
  getActivities,
  updateActivity,
  type Activity,
} from '@/services/api'

const { pushToast } = useToast()

const keyword = ref('')
const statusFilter = ref('')
const activities = ref<Activity[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const error = ref<string | null>(null)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' },
  { label: '已关闭', value: 'closed' },
]

const statusTone: Record<string, 'neutral' | 'primary' | 'success' | 'warning'> = {
  draft: 'neutral',
  published: 'success',
  closed: 'warning',
}
const statusLabel: Record<string, string> = { draft: '草稿', published: '已发布', closed: '已关闭' }

const editing = ref<Activity | null>(null)
const modalOpen = ref(false)
const saving = ref(false)
const form = reactive({
  id: null as number | null,
  title: '',
  summary: '',
  descriptionMarkdown: '',
  activityAt: '',
  endAt: '',
  location: '',
  status: 'draft',
  participationPoints: '0',
  registrationRequired: false,
})

const deleteTarget = ref<Activity | null>(null)
const deleting = ref(false)

const isEdit = computed(() => form.id != null)

function resetForm() {
  form.id = null
  form.title = ''
  form.summary = ''
  form.descriptionMarkdown = ''
  form.activityAt = ''
  form.endAt = ''
  form.location = ''
  form.status = 'draft'
  form.participationPoints = '0'
  form.registrationRequired = false
}

function openCreate() {
  resetForm()
  editing.value = null
  modalOpen.value = true
}

function openEdit(activity: Activity) {
  editing.value = activity
  form.id = activity.id
  form.title = activity.title ?? ''
  form.summary = activity.summary ?? ''
  form.descriptionMarkdown = activity.descriptionMarkdown ?? ''
  form.activityAt = toLocalInput(activity.activityAt)
  form.endAt = toLocalInput(activity.endAt)
  form.location = activity.location ?? ''
  form.status = activity.status ?? 'draft'
  form.participationPoints = String(activity.participationPoints ?? 0)
  form.registrationRequired = Boolean(activity.registrationRequired)
  modalOpen.value = true
}

function toLocalInput(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function toIso(local: string): string {
  if (!local) return ''
  const d = new Date(local)
  return Number.isNaN(d.getTime()) ? '' : d.toISOString()
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const data = await getActivities({
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    activities.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载活动列表失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.title.trim()) {
    pushToast({ title: '请填写活动标题', tone: 'warning' })
    return
  }
  saving.value = true
  try {
    const payload: Partial<Activity> = {
      title: form.title.trim(),
      summary: form.summary.trim(),
      descriptionMarkdown: form.descriptionMarkdown,
      activityAt: toIso(form.activityAt),
      endAt: toIso(form.endAt),
      location: form.location.trim(),
      status: form.status as Activity['status'],
      participationPoints: Number(form.participationPoints) || 0,
      registrationRequired: Boolean(form.registrationRequired),
    }
    if (isEdit.value && form.id != null) {
      await updateActivity(form.id, payload)
      pushToast({ title: '活动已更新', tone: 'success' })
    } else {
      await createActivity(payload)
      pushToast({ title: '活动已创建', tone: 'success' })
    }
    modalOpen.value = false
    await load()
  } catch (err) {
    pushToast({ title: err instanceof Error ? err.message : '保存失败', tone: 'danger' })
  } finally {
    saving.value = false
  }
}

function confirmDelete(activity: Activity) {
  deleteTarget.value = activity
}

async function doDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deleteActivity(deleteTarget.value.id)
    pushToast({ title: '活动已删除', tone: 'success' })
    deleteTarget.value = null
    await load()
  } catch (err) {
    pushToast({ title: err instanceof Error ? err.message : '删除失败', tone: 'danger' })
  } finally {
    deleting.value = false
  }
}

function formatDate(iso?: string): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('zh-CN', { hour12: false })
}

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="ACTIVITY MANAGEMENT" title="活动管理" description="创建、编辑与发布社团活动，对接开放原子社团管理系统。">
      <OaButton tone="primary" @click="openCreate"><template #prefix><IconPlus :size="16" /></template>创建活动</OaButton>
    </PageHeader>

    <div class="content">
      <div class="filters">
        <OaInput v-model="keyword" placeholder="搜索活动标题" @keyup.enter="page = 1; load()">
          <template #prefix><IconSearch :size="17" /></template>
        </OaInput>
        <OaSelect v-model="statusFilter" :options="statusOptions" @update:model-value="page = 1; load()" />
        <OaButton variant="soft" @click="page = 1; load()">查询</OaButton>
      </div>

      <div v-if="loading" class="state">加载中…</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="!activities.length" class="state">暂无活动数据</div>

      <div v-else class="table">
        <div class="head">
          <span>活动</span><span>状态</span><span>时间</span><span>地点</span><span>积分</span><span>操作</span>
        </div>
        <div v-for="a in activities" :key="a.id" class="row">
          <div class="title-cell">
            <strong>{{ a.title }}</strong>
            <small>{{ a.summary || '无摘要' }}</small>
          </div>
          <OaTag :tone="statusTone[a.status] || 'neutral'">{{ statusLabel[a.status] || a.status }}</OaTag>
          <time>{{ formatDate(a.activityAt) }}</time>
          <span class="muted">{{ a.location || '—' }}</span>
          <span class="muted">{{ a.participationPoints ?? 0 }}</span>
          <div class="actions">
            <OaButton variant="ghost" size="sm" @click="openEdit(a)"><template #prefix><IconEdit :size="15" /></template>编辑</OaButton>
            <OaButton variant="ghost" size="sm" tone="danger" @click="confirmDelete(a)"><template #prefix><IconTrash :size="15" /></template>删除</OaButton>
          </div>
        </div>
      </div>
    </div>

    <OaModal :open="modalOpen" :title="isEdit ? '编辑活动' : '创建活动'" :width="620" @close="modalOpen = false">
      <div class="form">
        <OaInput v-model="form.title" label="活动标题" placeholder="如：开源沙龙第 8 期" />
        <OaInput v-model="form.summary" label="摘要" placeholder="一句话描述活动" />
        <div class="form-row">
          <OaInput v-model="form.activityAt" type="datetime-local" label="开始时间" />
          <OaInput v-model="form.endAt" type="datetime-local" label="结束时间" />
        </div>
        <div class="form-row">
          <OaInput v-model="form.location" label="地点" placeholder="如：教学楼 A203" />
          <OaInput v-model="form.participationPoints" type="number" label="参与积分" />
        </div>
        <div class="form-row">
          <OaSelect v-model="form.status" label="状态" :options="statusOptions.filter(o => o.value)" />
          <label class="checkbox"><input v-model="form.registrationRequired" type="checkbox" /> 需要报名</label>
        </div>
        <OaTextarea v-model="form.descriptionMarkdown" label="活动详情（Markdown）" :rows="4" placeholder="活动详细描述…" />
      </div>
      <template #footer>
        <OaButton variant="soft" @click="modalOpen = false">取消</OaButton>
        <OaButton tone="primary" :loading="saving" @click="save">{{ isEdit ? '保存' : '创建' }}</OaButton>
      </template>
    </OaModal>

    <OaModal :open="Boolean(deleteTarget)" title="删除活动" :width="420" @close="deleteTarget = null">
      <p class="confirm-text">确认删除活动「{{ deleteTarget?.title }}」？此操作不可撤销。</p>
      <template #footer>
        <OaButton variant="soft" @click="deleteTarget = null">取消</OaButton>
        <OaButton tone="danger" :loading="deleting" @click="doDelete">删除</OaButton>
      </template>
    </OaModal>
  </div>
</template>

<style scoped>
.page { height: 100%; overflow-y: auto; background: var(--oa-color-canvas-soft); }
.content { display: grid; gap: 18px; padding: 24px 32px; }
.filters { display: flex; gap: 12px; align-items: center; }
.filters :deep(.oa-input) { max-width: 280px; }
.state { padding: 32px; text-align: center; color: var(--oa-color-muted); font-size: 13px; }
.state.error { color: var(--oa-color-danger); }
.table { overflow: hidden; border: 1px solid var(--oa-color-hairline); border-radius: 10px; background: #fff; }
.head, .row { display: grid; grid-template-columns: minmax(240px, 1.6fr) 100px 180px 160px 70px 160px; gap: 16px; align-items: center; padding: 0 18px; }
.head { height: 42px; color: var(--oa-color-muted); background: #fafafa; font-family: var(--oa-font-mono); font-size: 10px; }
.row { min-height: 64px; border-top: 1px solid var(--oa-color-hairline); }
.title-cell strong { display: block; font-size: 12px; }
.title-cell small { display: block; color: var(--oa-color-muted); font-size: 10px; margin-top: 2px; }
.muted { color: var(--oa-color-muted); font-size: 11px; }
.actions { display: flex; gap: 4px; }
.form { display: grid; gap: 14px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.checkbox { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--oa-color-body); }
.confirm-text { margin: 0; font-size: 13px; line-height: 1.6; }
</style>
