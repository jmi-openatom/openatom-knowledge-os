<script setup lang="ts">
import { computed } from 'vue'
import { IconPlus, IconTrash, IconTrashX } from '@tabler/icons-vue'
import { OaButton, OaIconButton, useToast } from '@openatom/ui'
import { useKnowledgeStore } from '@/stores/knowledge'

const knowledge = useKnowledgeStore()
const { pushToast } = useToast()

const grouped = computed(() => {
  const groups: Record<string, typeof knowledge.conversations> = {}
  for (const conv of knowledge.conversations) {
    const g = conv.group || '最近'
    if (!groups[g]) groups[g] = []
    groups[g].push(conv)
  }
  return groups
})

const groupNames = computed(() => Object.keys(grouped.value))

async function handleDelete(id: number, title: string) {
  try {
    await knowledge.deleteConversation(id)
    pushToast({ title: '对话已删除', description: title, tone: 'success' })
  } catch (e) {
    pushToast({ title: '删除失败', tone: 'danger' })
  }
}
</script>

<template>
  <aside class="conversation-sidebar">
    <header>
      <div>
        <h1>AI 知识助手</h1>
        <p>基于社团资料生成可信答案</p>
      </div>
    </header>
    <OaButton block @click="knowledge.createConversation()">
      <template #prefix><IconPlus :size="17" /></template>
      新建对话
    </OaButton>

    <div class="conversation-list">
      <section v-for="group in groupNames" :key="group">
        <h2>{{ group }}</h2>
        <div
          v-for="item in grouped[group]"
          :key="item.id"
          class="conversation-row-wrapper"
          :class="{ 'is-active': item.id === knowledge.activeConversationId }"
        >
          <button
            class="conversation-row"
            type="button"
            @click="knowledge.loadConversation(item.id)"
          >
            <span>{{ item.title }}</span>
            <time>{{ item.updatedAt }}</time>
          </button>
          <button class="delete-btn" type="button" @click.stop="handleDelete(item.id, item.title)" title="删除对话">
            <IconTrash :size="14" />
          </button>
        </div>
      </section>
      <div v-if="knowledge.conversations.length === 0" class="empty-hint">
        还没有对话，输入问题开始吧
      </div>
    </div>
  </aside>
</template>

<style scoped>
.conversation-sidebar { display: flex; flex: 0 0 264px; flex-direction: column; min-width: 0; height: 100%; padding: 22px 16px 14px; border-right: 1px solid var(--oa-color-hairline); background: rgb(255 255 255 / 82%); }
.conversation-sidebar header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
.conversation-sidebar h1 { margin: 0; font-size: 16px; font-weight: 600; letter-spacing: -.35px; }
.conversation-sidebar header p { margin: 5px 0 0; color: var(--oa-color-muted); font-size: 11px; }
.conversation-list { min-height: 0; flex: 1; margin: 15px -8px 0; overflow-y: auto; }
.conversation-list section + section { margin-top: 19px; }
.conversation-list h2 { margin: 0 9px 7px; color: var(--oa-color-muted); font-size: 11px; font-weight: 500; }
.conversation-row-wrapper { display: flex; align-items: center; gap: 2px; padding: 0 2px; border: 1px solid transparent; border-radius: 7px; }
.conversation-row-wrapper:hover { background: var(--oa-color-canvas-inset); }
.conversation-row-wrapper.is-active { border-color: #cfe3fb; color: #075db8; background: #edf6ff; }
.conversation-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; flex: 1; width: 100%; min-height: 42px; padding: 0 9px; border: 0; border-radius: 6px; color: inherit; background: transparent; cursor: pointer; text-align: left; }
.conversation-row span { align-self: center; overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.conversation-row time { align-self: center; color: var(--oa-color-muted); font-family: var(--oa-font-mono); font-size: 10px; }
.delete-btn { display: flex; align-items: center; justify-content: center; width: 26px; height: 26px; padding: 0; border: 0; border-radius: 5px; color: var(--oa-color-muted); background: transparent; cursor: pointer; opacity: 0; transition: opacity .15s, color .15s; flex-shrink: 0; }
.conversation-row-wrapper:hover .delete-btn { opacity: 1; }
.delete-btn:hover { color: #dc3545; background: rgb(220 53 69 / 10%); }
.empty-hint { padding: 20px 10px; color: var(--oa-color-muted); font-size: 11px; text-align: center; }
</style>
