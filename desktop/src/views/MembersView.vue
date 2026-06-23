<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { IconPlus, IconSearch } from '@tabler/icons-vue'
import { OaAvatar, OaBadge, OaButton, OaInput, OaSelect } from '@openatom/ui'
import PageHeader from '@/components/PageHeader.vue'
import { getMembers, updateMemberRole, type Member } from '@/services/api'

const keyword = ref('')
const members = ref<Member[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const updatingId = ref<number | null>(null)

const roleOptions = [
  { label: '管理员', value: 'admin' },
  { label: '负责人', value: 'leader' },
  { label: '普通成员', value: 'member' },
  { label: '访客', value: 'guest' },
]

async function loadMembers() {
  loading.value = true
  error.value = null
  try {
    const data = await getMembers()
    members.value = data
  } catch (err) {
    console.error('Failed to load members:', err)
    error.value = '加载成员列表失败，请检查网络连接'
  } finally {
    loading.value = false
  }
}

async function handleRoleChange(member: Member, newRole: string) {
  if (!member.id) return
  
  updatingId.value = member.id
  try {
    await updateMemberRole(member.id, newRole)
    // 更新本地状态
    member.role = newRole as Member['role']
  } catch (err) {
    console.error('Failed to update role:', err)
    error.value = '角色更新失败，请重试'
    // 重新加载以恢复原始状态
    await loadMembers()
  } finally {
    updatingId.value = null
  }
}

function formatDate(dateString: string): string {
  const date = new Date(dateString)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

onMounted(() => {
  loadMembers()
})
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="RBAC MANAGEMENT" title="成员与权限" description="通过角色和权限控制资料、文档与管理能力。">
      <OaButton tone="primary"><template #prefix><IconPlus :size="16" /></template>添加成员</OaButton>
    </PageHeader>
    <div class="members-content">
      <OaInput v-model="keyword" placeholder="搜索姓名或邮箱"><template #prefix><IconSearch :size="17" /></template></OaInput>
      
      <!-- Loading State -->
      <div v-if="loading" class="state-message">加载中...</div>
      
      <!-- Error State -->
      <div v-else-if="error" class="state-message error">{{ error }}</div>
      
      <!-- Empty State -->
      <div v-else-if="members.length === 0" class="state-message">暂无成员数据</div>
      
      <!-- Members Table -->
      <div v-else class="members-table">
        <div class="members-head"><span>成员</span><span>角色</span><span>最后登录</span><span>加入日期</span></div>
        <div v-for="member in members" :key="member.id" class="member-row">
          <div>
            <OaAvatar :name="member.name" :size="36" />
            <span>
              <strong>{{ member.name }}</strong>
              <small>{{ member.email }}</small>
            </span>
          </div>
          <OaSelect 
            :model-value="member.role" 
            :options="roleOptions"
            :disabled="updatingId === member.id"
            @update:model-value="(val) => handleRoleChange(member, val)"
          />
          <time>{{ member.lastLoginAt ? formatDate(member.lastLoginAt) : '从未登录' }}</time>
          <time>{{ formatDate(member.createdAt) }}</time>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { height: 100%; overflow-y: auto; background: var(--oa-color-canvas-soft); }
.members-content { display: grid; gap: 18px; padding: 24px 32px; }
.state-message { padding: 24px; text-align: center; color: var(--oa-color-muted); font-size: 13px; }
.state-message.error { color: var(--oa-color-danger); }
.members-table { overflow: hidden; border: 1px solid var(--oa-color-hairline); border-radius: 10px; background: #fff; }
.members-head, .member-row { display: grid; grid-template-columns: minmax(240px, 1fr) 180px 140px 140px; gap: 18px; align-items: center; padding: 0 18px; }
.members-head { height: 42px; color: var(--oa-color-muted); background: #fafafa; font-family: var(--oa-font-mono); font-size: 10px; }
.member-row { min-height: 70px; border-top: 1px solid var(--oa-color-hairline); }
.member-row > div:first-child { display: flex; align-items: center; gap: 10px; }
.member-row strong, .member-row small { display: block; }
.member-row strong { font-size: 12px; }
.member-row small, .member-row time { color: var(--oa-color-muted); font-size: 10px; }
</style>
