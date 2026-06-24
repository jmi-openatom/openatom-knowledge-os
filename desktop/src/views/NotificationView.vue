<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { IconBell, IconPlus, IconSearch, IconTrash } from '@tabler/icons-vue'
import { OaButton, OaInput, OaModal, OaSelect, OaTag, OaTextarea, useToast } from '@openatom/ui'
import PageHeader from '@/components/PageHeader.vue'
import {
  deleteNotification,
  getAdminNotifications,
  sendNotification,
  type NotificationItem,
} from '@/services/api'

const { pushToast } = useToast()

const keyword = ref('')
const notifications = ref<NotificationItem[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const typeOptions = [
  { label: '活动通知', value: 'activity' },
  { label: '系统公告', value: 'system' },
  { label: '普通通知', value: 'notice' },
]
const typeTone: Record<string, 'primary' | 'neutral' | 'warning'> = {
  activity: 'primary',
  system: 'warning',
  notice: 'neutral',
}
const typeLabel: Record<string, string> = { activity: '活动', system: '系统', notice: '通知' }

const modalOpen = ref(false)
const saving = ref(false)
const form = reactive({
  title: '',
  content: '',
  type: 'activity',
  isAll: true,
  receiverUserIdsText: '',
})

const deleteTarget = ref<NotificationItem | null>(null)
const deleting = ref(false)

function resetForm() {
  form.title = ''
  form.content = ''
  form.type = 'activity'
  form.isAll = true
  form.receiverUserIdsText = ''
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const data = await getAdminNotifications({ keyword: keyword.value || undefined, pageSize: 50 })
    notifications.value = data?.list ?? []
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载通知列表失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.title.trim() || !form.content.trim()) {
    pushToast({ title: '请填写通知标题和内容', tone: 'warning' })
    return
  }
  if (!form.isAll && !form.receiverUserIdsText.trim()) {
    pushToast({ title: '非全员通知需指定接收人 ID', tone: 'warning' })
    return
  }
  saving.value = true
  try {
    const receiverUserIds = form.isAll
      ? undefined
      : form.receiverUserIdsText
          .split(/[,，\s]+/)
          .map((s) => Number(s.trim()))
          .filter((n) => Number.isFinite(n) && n > 0)
    await sendNotification({
      title: form.title.trim(),
      content: form.content.trim(),
      type: form.type,
      isAll: form.isAll,
      receiverUserIds,
    })
    pushToast({ title: '通知已发送', tone: 'success' })
    modalOpen.value = false
    resetForm()
    await load()
  } catch (err) {
    pushToast({ title: err instanceof Error ? err.message : '发送失败', tone: 'danger' })
  } finally {
    saving.value = false
  }
}

async function doDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deleteNotification(deleteTarget.value.id)
    pushToast({ title: '通知已删除', tone: 'success' })
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
    <PageHeader eyebrow="NOTIFICATION MANAGEMENT" title="通知管理" description="向社团成员发送活动、系统通知，并管理历史通知。">
      <OaButton tone="primary" @click="resetForm(); modalOpen = true">
        <template #prefix><IconPlus :size="16" /></template>发送通知
      </OaButton>
    </PageHeader>

    <div class="content">
      <div class="filters">
        <OaInput v-model="keyword" placeholder="搜索通知标题" @keyup.enter="load()">
          <template #prefix><IconSearch :size="17" /></template>
        </OaInput>
        <OaButton variant="soft" @click="load">查询</OaButton>
      </div>

      <div v-if="loading" class="state">加载中…</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="!notifications.length" class="state">暂无通知</div>

      <div v-else class="table">
        <div class="head">
          <span>通知</span><span>类型</span><span>接收范围</span><span>发送时间</span><span>操作</span>
        </div>
        <div v-for="n in notifications" :key="n.id" class="row">
          <div class="title-cell">
            <strong>{{ n.title }}</strong>
            <small>{{ n.content }}</small>
          </div>
          <OaTag :tone="typeTone[n.type || ''] || 'neutral'">
            <template #icon><IconBell :size="12" /></template>{{ typeLabel[n.type || ''] || n.type || '通知' }}
          </OaTag>
          <span class="muted">{{ n.isAll ? '全员' : '指定成员' }}</span>
          <time>{{ formatDate(n.createdAt) }}</time>
          <div class="actions">
            <OaButton variant="ghost" size="sm" tone="danger" @click="deleteTarget = n">
              <template #prefix><IconTrash :size="15" /></template>删除
            </OaButton>
          </div>
        </div>
      </div>
    </div>

    <OaModal :open="modalOpen" title="发送通知" :width="560" @close="modalOpen = false">
      <div class="form">
        <OaInput v-model="form.title" label="标题" placeholder="通知标题" />
        <OaTextarea v-model="form.content" label="内容" :rows="4" placeholder="通知正文…" />
        <div class="form-row">
          <OaSelect v-model="form.type" label="类型" :options="typeOptions" />
          <label class="checkbox"><input v-model="form.isAll" type="checkbox" /> 全员发送</label>
        </div>
        <OaInput
          v-if="!form.isAll"
          v-model="form.receiverUserIdsText"
          label="接收人 ID（逗号分隔）"
          placeholder="如：1, 2, 3"
        />
      </div>
      <template #footer>
        <OaButton variant="soft" @click="modalOpen = false">取消</OaButton>
        <OaButton tone="primary" :loading="saving" @click="save">发送</OaButton>
      </template>
    </OaModal>

    <OaModal :open="Boolean(deleteTarget)" title="删除通知" :width="420" @close="deleteTarget = null">
      <p class="confirm-text">确认删除通知「{{ deleteTarget?.title }}」？</p>
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
.head, .row { display: grid; grid-template-columns: minmax(280px, 1.6fr) 120px 120px 180px 110px; gap: 16px; align-items: center; padding: 0 18px; }
.head { height: 42px; color: var(--oa-color-muted); background: #fafafa; font-family: var(--oa-font-mono); font-size: 10px; }
.row { min-height: 64px; border-top: 1px solid var(--oa-color-hairline); }
.title-cell strong { display: block; font-size: 12px; }
.title-cell small { display: block; color: var(--oa-color-muted); font-size: 10px; margin-top: 2px; }
.muted { color: var(--oa-color-muted); font-size: 11px; }
.actions { display: flex; gap: 4px; }
.form { display: grid; gap: 14px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; align-items: end; }
.checkbox { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--oa-color-body); padding-bottom: 10px; }
.confirm-text { margin: 0; font-size: 13px; line-height: 1.6; }
</style>
