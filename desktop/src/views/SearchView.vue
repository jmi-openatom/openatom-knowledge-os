<script setup lang="ts">
import { ref, watch } from 'vue'
import { IconEye, IconFileText, IconSearch, IconSparkles } from '@tabler/icons-vue'
import { OaBadge, OaEmpty, OaInput, OaSpinner, OaTag } from '@openatom/ui'
import PageHeader from '@/components/PageHeader.vue'
import { apiRequest } from '@/services/api'
import { useFilePreviewerStore } from '@/stores/filePreviewer'

interface SearchResult {
  id: number
  title: string
  highlight: string
  resourceType: string
  score: number
  tags: string[]
}

const filePreviewer = useFilePreviewerStore()
const query = ref('')
const results = ref<SearchResult[]>([])
const loading = ref(false)
const error = ref('')
let searchTimer = 0

watch(query, (value) => {
  window.clearTimeout(searchTimer)
  error.value = ''
  if (!value.trim()) {
    results.value = []
    return
  }
  searchTimer = window.setTimeout(async () => {
    loading.value = true
    try {
      const data = await apiRequest<SearchResult[]>({ url: '/search', params: { q: value } })
      if (data) {
        results.value = data
      } else {
        error.value = '无法连接后端搜索服务，请确认后端已启动'
        results.value = []
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : '搜索请求失败'
      results.value = []
    } finally {
      loading.value = false
    }
  }, 260)
}, { immediate: true })
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="ELASTICSEARCH" title="全局搜索" description="跨文件、在线文档和 Wiki 检索，并高亮命中内容。" />
    <div class="search-page">
      <OaInput v-model="query" size="lg" placeholder="输入关键词进行全文或语义搜索" clearable>
        <template #prefix><IconSearch :size="20" /></template>
        <template #suffix><span class="shortcut">⌘ K</span></template>
      </OaInput>
      <div class="search-tabs"><OaTag selected>全部</OaTag><OaTag>文件</OaTag><OaTag>在线文档</OaTag><OaTag>Wiki</OaTag></div>

      <div v-if="loading" class="search-status"><OaSpinner :size="18" /> 正在检索…</div>
      <div v-else-if="error" class="search-status error">{{ error }}</div>
      <div v-else-if="query.trim() && !loading" class="search-summary">
        <IconSparkles :size="15" /> 找到 {{ results.length }} 个结果，已按相关度排序
      </div>

      <div v-if="results.length" class="search-results">
        <article v-for="file in results" :key="file.id" class="result-row" @click="filePreviewer.show(file.id)">
          <span class="result-icon"><IconFileText :size="20" /></span>
          <div class="result-body">
            <h2>{{ file.title }}</h2>
            <p v-html="file.highlight" />
            <div class="result-meta">
              <OaTag v-for="tag in file.tags" :key="tag">{{ tag }}</OaTag>
            </div>
          </div>
          <div class="result-side">
            <OaBadge tone="success">相关度 {{ file.score }}%</OaBadge>
            <button class="preview-btn" type="button" @click.stop="filePreviewer.show(file.id)">
              <IconEye :size="15" />
              预览
            </button>
          </div>
        </article>
      </div>
      <OaEmpty v-else-if="!loading && !error && query.trim()" description="没有匹配的结果，换个关键词试试" />
      <OaEmpty v-else-if="!query.trim()" description="输入关键词开始搜索" />
    </div>
  </div>
</template>

<style scoped>
.page { height: 100%; overflow-y: auto; background: var(--oa-color-canvas-soft); }
.search-page { max-width: 920px; margin: 0 auto; padding: 34px 32px; }
.shortcut { padding: 2px 7px; border: 1px solid var(--oa-color-hairline); border-radius: 4px; color: var(--oa-color-muted); font-family: var(--oa-font-mono); font-size: 10px; }
.search-tabs { display: flex; gap: 7px; margin-top: 14px; }
.search-summary { display: flex; align-items: center; gap: 7px; margin: 28px 0 12px; color: var(--oa-color-muted); font-size: 11px; }
.search-status { display: flex; align-items: center; gap: 9px; margin: 28px 0 12px; color: var(--oa-color-muted); font-size: 11px; }
.search-status.error { color: #c0392b; }
.search-results { overflow: hidden; border: 1px solid var(--oa-color-hairline); border-radius: 10px; background: #fff; }
.result-row { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; gap: 13px; padding: 18px; border-top: 1px solid var(--oa-color-hairline); cursor: pointer; transition: background .12s; }
.result-row:first-child { border-top: 0; }
.result-row:hover { background: #fcfcfc; }
.result-icon { display: grid; width: 38px; height: 38px; border-radius: 8px; color: #276db4; background: #edf5ff; place-items: center; }
.result-body { min-width: 0; }
.result-body h2 { margin: 0; font-size: 14px; }
.result-body p { margin: 7px 0; color: var(--oa-color-body); font-size: 12px; line-height: 1.65; }
.result-body mark { padding: 0 2px; color: #704600; background: #fff0a8; }
.result-meta { display: flex; align-items: center; gap: 6px; color: var(--oa-color-muted); font-size: 9px; }
.result-side { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.preview-btn { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; border: 1px solid var(--oa-color-hairline); border-radius: 6px; color: var(--oa-color-link); background: #fff; cursor: pointer; font-size: 11px; transition: background .12s, border-color .12s; }
.preview-btn:hover { background: #f0f7ff; border-color: #bdd9f8; }
</style>
