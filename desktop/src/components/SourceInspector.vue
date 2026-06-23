<script setup lang="ts">
import { ref, watch } from 'vue'
import { IconChevronDown, IconExternalLink, IconEye, IconFileDescription, IconFolder, IconRefresh } from '@tabler/icons-vue'
import { OaBadge, OaButton, OaIconButton, OaPanel } from '@openatom/ui'
import { useFilePreviewerStore } from '@/stores/filePreviewer'
import type { SourceReference } from '@/types'

const props = defineProps<{
  sources: SourceReference[]
  scope: string
}>()

defineEmits<{ changeScope: [] }>()

const filePreviewer = useFilePreviewerStore()
const selectedSourceIndex = ref(0)

const selectedSource = ref<SourceReference | null>(null)

function selectSource(index: number) {
  selectedSourceIndex.value = index
  selectedSource.value = props.sources[index] || null
}

// Initialize selection when sources change
function ensureSelection() {
  if (props.sources.length > 0 && !selectedSource.value) {
    selectSource(0)
  }
  if (selectedSourceIndex.value >= props.sources.length && props.sources.length > 0) {
    selectSource(0)
  }
}

// Watch for source changes
watch(() => props.sources, () => {
  ensureSelection()
}, { immediate: true })

function openPreview(source: SourceReference) {
  filePreviewer.show(source.id)
}

function formatSize(size?: number) {
  if (!size) return '—'
  return size > 1_000_000 ? `${(size / 1_000_000).toFixed(1)} MB` : `${Math.round(size / 1000)} KB`
}

function formatTime(time?: string) {
  if (!time) return '—'
  return time.replace('T', ' ').substring(0, 16)
}

const scopeDescriptions: Record<string, string> = {
  '全部资料': '全部社团资料，包括活动策划、技术资料、项目文档和会议记录。',
  '活动策划': '与活动策划、执行、宣传、复盘相关的文档、模板、案例与资料。',
  '技术资料': '技术分享、开发规范、环境搭建指南和代码评审约定。',
  '项目文档': '项目立项、执行、结题归档相关文档和模板。',
  '会议记录': '社团例会、专项会议记录和决议事项。',
}
</script>

<template>
  <aside class="source-inspector">
    <OaPanel padding="md" title="知识范围">
      <template #actions><OaButton size="sm" variant="ghost" tone="primary" @click="$emit('changeScope')">修改</OaButton></template>
      <div class="scope-row">
        <span class="scope-icon"><IconFolder :size="18" /></span>
        <div><strong>{{ scope }}</strong><p>包含 {{ sources.length || 128 }} 个资料 · 最近更新 2 天前</p></div>
      </div>
      <p class="scope-description">{{ scopeDescriptions[scope] || scopeDescriptions['全部资料'] }}</p>
      <button class="text-link" type="button">展开 <IconChevronDown :size="14" /></button>
    </OaPanel>

    <OaPanel padding="md" :title="`检索到的内容`" :description="`${sources.length} 个高相关片段`">
      <template #actions><OaBadge>{{ sources.length }}</OaBadge></template>
      <div class="source-list">
        <button
          v-for="(source, index) in sources"
          :key="source.id"
          class="source-card"
          :class="{ 'is-active': index === selectedSourceIndex }"
          type="button"
          @click="selectSource(index)"
        >
          <div class="source-card__title">
            <span>{{ index + 1 }}</span>
            <strong>{{ source.title }}</strong>
            <OaBadge tone="success">{{ source.score }}%</OaBadge>
          </div>
          <small>来自 {{ source.path }}</small>
          <p>{{ source.excerpt }}</p>
        </button>
      </div>
      <button v-if="sources.length > 3" class="all-passages" type="button">查看全部片段 <IconChevronDown :size="14" /></button>
    </OaPanel>

    <OaPanel v-if="selectedSource || sources[0]" padding="md" title="来源文件详情">
      <template #actions>
        <OaIconButton label="预览文件" size="sm" @click="openPreview(selectedSource || sources[0])">
          <IconEye :size="16" />
        </OaIconButton>
      </template>
      <div class="file-summary">
        <span class="file-summary__icon"><IconFileDescription :size="22" /></span>
        <div>
          <strong>{{ (selectedSource || sources[0]).fileName }}</strong>
          <p>{{ (selectedSource || sources[0]).path }}</p>
        </div>
      </div>
      <dl>
        <div><dt>类型</dt><dd>{{ (selectedSource || sources[0]).type }}</dd></div>
        <div><dt>创建者</dt><dd>{{ (selectedSource || sources[0]).createdBy || '—' }}</dd></div>
        <div><dt>更新时间</dt><dd>{{ formatTime((selectedSource || sources[0]).updatedAt) }}</dd></div>
        <div><dt>文件大小</dt><dd>{{ formatSize((selectedSource || sources[0]).size) }}</dd></div>
        <div><dt>索引状态</dt><dd><OaBadge tone="success" dot>已就绪</OaBadge></dd></div>
      </dl>
      <div class="file-actions">
        <OaButton block variant="outline" @click="openPreview(selectedSource || sources[0])">
          <template #suffix><IconExternalLink :size="15" /></template>预览原文档
        </OaButton>
        <OaIconButton label="重新索引" variant="outline"><IconRefresh :size="17" /></OaIconButton>
      </div>
    </OaPanel>
  </aside>
</template>

<style scoped>
.source-inspector { display: flex; flex: 0 0 306px; flex-direction: column; gap: 12px; height: 100%; padding: 18px 14px; overflow-y: auto; border-left: 1px solid var(--oa-color-hairline); background: #fcfcfc; }
.scope-row, .file-summary { display: flex; align-items: flex-start; gap: 11px; }
.scope-icon, .file-summary__icon { display: grid; width: 34px; height: 34px; flex: none; border-radius: 8px; color: #7357d9; background: #f0edff; place-items: center; }
.scope-row strong, .file-summary strong { font-size: 12px; }
.scope-row p, .file-summary p { margin: 4px 0 0; color: var(--oa-color-muted); font-size: 10px; line-height: 1.4; }
.scope-description { margin: 13px 0 8px; color: var(--oa-color-body); font-size: 11px; line-height: 1.6; }
.text-link, .all-passages { display: inline-flex; align-items: center; gap: 3px; padding: 0; border: 0; color: var(--oa-color-link); background: none; cursor: pointer; font-size: 11px; }
.source-list { display: grid; gap: 7px; }
.source-card { display: block; width: 100%; padding: 10px; border: 1px solid var(--oa-color-hairline); border-radius: 7px; color: var(--oa-color-ink); background: #fff; cursor: pointer; text-align: left; transition: border-color .15s, box-shadow .15s; }
.source-card:hover { border-color: #bdd9f8; box-shadow: var(--oa-shadow-1); }
.source-card.is-active { border-color: var(--oa-color-link); box-shadow: 0 0 0 2px rgb(8 124 240 / 12%); }
.source-card__title { display: grid; grid-template-columns: 18px minmax(0, 1fr) auto; gap: 6px; align-items: center; }
.source-card__title > span { display: grid; width: 18px; height: 18px; border-radius: 50%; color: #fff; background: var(--oa-color-link); font-size: 10px; place-items: center; }
.source-card__title strong { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.source-card small { display: block; margin: 6px 0; overflow: hidden; color: var(--oa-color-muted); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.source-card p { display: -webkit-box; margin: 0; overflow: hidden; color: var(--oa-color-body); font-size: 10px; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.all-passages { width: 100%; justify-content: center; margin-top: 10px; }
.file-summary { margin-bottom: 14px; }
dl { display: grid; gap: 7px; margin: 0; }
dl div { display: grid; grid-template-columns: 64px 1fr; font-size: 10px; }
dt { color: var(--oa-color-muted); }
dd { margin: 0; color: var(--oa-color-body); }
.file-actions { display: flex; gap: 7px; margin-top: 15px; }
</style>
