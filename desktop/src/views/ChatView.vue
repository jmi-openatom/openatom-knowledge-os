<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { renderMarkdown as mdRender } from '@/composables/useMarkdown'
import {
  IconBookmark,
  IconCopy,
  IconPaperclip,
  IconRefresh,
  IconSend2,
  IconSparkles,
  IconSquare,
  IconThumbDown,
  IconThumbUp,
} from '@tabler/icons-vue'
import { OaButton, OaIconButton, OaModal, OaSelect, OaSpinner, OaTag, useToast } from '@openatom/ui'
import ConversationSidebar from '@/components/ConversationSidebar.vue'
import SourceInspector from '@/components/SourceInspector.vue'
import { useAuthStore } from '@/stores/auth'
import { useFilePreviewerStore } from '@/stores/filePreviewer'
import { useKnowledgeStore } from '@/stores/knowledge'
import { createWikiPage, organizeToWiki } from '@/services/api'
import type { ChatAnswer, WikiNode } from '@/types'

const auth = useAuthStore()
const knowledge = useKnowledgeStore()
const filePreviewer = useFilePreviewerStore()
const { pushToast } = useToast()
const question = ref('')
const scope = ref('活动策划')
const scopeModal = ref(false)
const savingToWiki = ref(false)
const chatScroll = ref<HTMLElement | null>(null)
const quickPrompts = [
  '活动策划流程有哪些关键步骤？',
  '如何写一份活动预算？',
  '社团招新有哪些好点子？',
  '往届迎新活动复盘重点',
]

onMounted(() => {
  knowledge.loadConversations()
  knowledge.loadFiles()
})

const fileCount = computed(() => knowledge.files.length)

// Current conversation's full Q&A history
const conversationHistory = computed<ChatAnswer[]>(() => {
  const id = knowledge.activeConversationId
  return knowledge.history.get(id) || []
})

const hasHistory = computed(() => conversationHistory.value.length > 0 || knowledge.streaming)

const currentSources = computed(() => {
  if (knowledge.streamingAnswer?.sources?.length) return knowledge.streamingAnswer.sources
  return lastAnswer.value?.sources || []
})

function preprocessMarkdown(text: string): string {
  if (!text) return ''
  return text
    // 修复非行首标题：在 ### 前加换行，并修复空格（如：规则：###一 → 规则：\n\n### 一）
    .replace(/([^\n])(#{1,6})(?!\s)(?=[^#\s])/g, '$1\n\n$2 ')
    // 修复行首标题标记后无空格（如：###一 → ### 一）
    .replace(/^(#{1,6})(?!\s)(?=[^#\s])/gm, '$1 ')
    // 修复行首列表标记后无空格（如：-项目 → - 项目）
    .replace(/^([-*+])(?!\s)(?=[^\s])/gm, '$1 ')
    // 引用标记后的中文短横线条目： [33]–xxx → [33]\n- xxx
    .replace(/\[(\d+)\]–/g, '[$1]\n- ')
    // 中文冒号后的短横线条目：：–xxx → ：\n- xxx
    .replace(/：–/g, '：\n- ')
    // 中文编号前加换行（一、二、三等），如果不是行首
    .replace(/([^\n])([一二三四五六七八九十]+、)/g, '$1\n\n$2')
    // 中文括号编号前加换行（（一）（二）等）
    .replace(/([^\n])（[一二三四五六七八九十]+）/g, '$1\n\n$2')
}

function renderMarkdown(text: string): string {
  return mdRender(preprocessMarkdown(text))
}

// Auto-scroll to bottom when history changes or loading/streaming
watch([conversationHistory, () => knowledge.loading, () => knowledge.streaming, () => knowledge.streamingAnswer?.answer], () => {
  nextTick(() => {
    if (chatScroll.value) chatScroll.value.scrollTop = chatScroll.value.scrollHeight
  })
}, { deep: true })

// Feedback state
const feedbackState = ref<'up' | 'down' | null>(null)

// Last completed answer (from history)
const lastAnswer = computed(() => {
  const hist = conversationHistory.value
  return hist.length > 0 ? hist[hist.length - 1] : null
})

async function send() {
  const value = question.value.trim()
  if (!value || knowledge.loading) return
  question.value = ''
  await knowledge.ask(value, scope.value)
}

async function regenerate() {
  if (!lastAnswer.value || knowledge.loading) return
  const convId = knowledge.activeConversationId
  const hist = knowledge.history.get(convId)
  if (hist && hist.length > 0) {
    knowledge.history.set(convId, hist.slice(0, -1))
  }
  await knowledge.ask(lastAnswer.value.question, scope.value)
}

async function saveToWiki() {
  if (!lastAnswer.value?.question || !lastAnswer.value?.answer) {
    pushToast({ title: '没有可收藏的内容', tone: 'warning' })
    return
  }
  if (savingToWiki.value) return
  savingToWiki.value = true

  try {
    const question = lastAnswer.value.question
    const answer = lastAnswer.value.answer

    pushToast({ title: 'AI 整理中…', tone: 'neutral' })
    const { title, markdown } = await organizeToWiki(question, answer)

    const page = await createWikiPage({
      title,
      content: markdown,
      parentId: null,
      type: 'page',
    })

    const safeName = (title || question).replace(/[\\/:*?"<>|]/g, '').slice(0, 60) || '问答收藏'
    const file = new File([markdown], `${safeName}.md`, { type: 'text/markdown' })
    await knowledge.uploadFile(file, ['问答收藏', 'AI整理'])

    pushToast({ title: page ? '已整理并收藏到 Wiki' : 'Wiki 写入失败，已生成 Markdown 文件', tone: page ? 'success' : 'warning' })
  } catch (error) {
    console.error('Failed to save to wiki:', error)
    pushToast({ title: '收藏失败，请重试', tone: 'danger' })
  } finally {
    savingToWiki.value = false
  }
}

function handleFeedback(type: 'up' | 'down') {
  feedbackState.value = feedbackState.value === type ? null : type
  const message = type === 'up' ? '感谢反馈！' : '我们会继续改进'
  pushToast({ title: message, tone: 'success' })
}

function copyAnswer(text: string) {
  navigator.clipboard.writeText(text)
  pushToast({ title: '已复制回答', tone: 'success' })
}
</script>

<template>
  <div class="chat-view">
    <ConversationSidebar />

    <section class="chat-canvas">
      <div v-if="!hasHistory" class="welcome-band">
        <img src="/assets/ai-mesh-background.png" alt="" aria-hidden="true">
        <div>
          <span class="welcome-band__eyebrow"><IconSparkles :size="15" /> 基于 {{ fileCount }} 份社团资料</span>
          <h1>你好，{{ auth.user?.name || '同学' }}</h1>
          <p>我是你的 AI 知识助手，基于社团知识库为你提供可靠且可追溯的答案。</p>
          <div class="quick-prompts">
            <button v-for="prompt in quickPrompts" :key="prompt" type="button" @click="question = prompt">{{ prompt }}</button>
          </div>
        </div>
      </div>

      <div ref="chatScroll" class="chat-scroll">
        <template v-for="(item, idx) in conversationHistory" :key="item.id">
          <div class="question-bubble">{{ item.question }}</div>
          <article class="answer-card">
            <div class="answer-card__avatar"><IconSparkles :size="20" /></div>
            <div class="answer-card__content">
              <div class="markdown-body" v-html="renderMarkdown(item.answer)" />
              <section v-if="item.sources && item.sources.length" class="inline-sources">
                <h2>引用来源</h2>
                <div>
                  <OaTag v-for="(source, sIndex) in item.sources" :key="source.id" tone="primary" class="source-tag" @click="filePreviewer.show(source.id)">
                    {{ sIndex + 1 }}　{{ source.title }}
                  </OaTag>
                </div>
              </section>
              <footer v-if="idx === conversationHistory.length - 1 && !knowledge.streaming">
                <time>{{ item.createdAt }}</time>
                <div class="answer-actions">
                  <OaIconButton label="复制回答" size="sm" @click="copyAnswer(item.answer)"><IconCopy :size="15" /></OaIconButton>
                  <OaIconButton label="重新生成" size="sm" :disabled="knowledge.loading" @click="regenerate"><IconRefresh :size="15" /></OaIconButton>
                  <OaIconButton label="回答有帮助" size="sm" :active="feedbackState === 'up'" @click="handleFeedback('up')"><IconThumbUp :size="15" /></OaIconButton>
                  <OaIconButton label="回答需改进" size="sm" :active="feedbackState === 'down'" @click="handleFeedback('down')"><IconThumbDown :size="15" /></OaIconButton>
                  <OaIconButton label="收藏到 Wiki" size="sm" :loading="savingToWiki" @click="saveToWiki"><IconBookmark :size="15" /></OaIconButton>
                </div>
              </footer>
              <footer v-else>
                <time>{{ item.createdAt }}</time>
                <div class="answer-actions">
                  <OaIconButton label="复制回答" size="sm" @click="copyAnswer(item.answer)"><IconCopy :size="15" /></OaIconButton>
                </div>
              </footer>
            </div>
          </article>
        </template>

        <!-- Streaming answer (shown during streaming, before added to history) -->
        <template v-if="knowledge.streamingAnswer">
          <div class="question-bubble">{{ knowledge.streamingAnswer.question }}</div>
          <article class="answer-card">
            <div class="answer-card__avatar"><IconSparkles :size="20" /></div>
            <div class="answer-card__content">
              <div class="markdown-body" v-html="renderMarkdown(knowledge.streamingAnswer.answer)" />
              <section v-if="knowledge.streamingAnswer.sources && knowledge.streamingAnswer.sources.length" class="inline-sources">
                <h2>引用来源</h2>
                <div>
                  <OaTag v-for="(source, sIndex) in knowledge.streamingAnswer.sources" :key="source.id" tone="primary" class="source-tag" @click="filePreviewer.show(source.id)">
                    {{ sIndex + 1 }}　{{ source.title }}
                  </OaTag>
                </div>
              </section>
              <footer>
                <time>{{ knowledge.streamingAnswer.createdAt }}</time>
                <div class="answer-actions">
                  <span v-if="knowledge.formatting" class="formatting-hint"><OaSpinner :size="13" /> 优化排版中…</span>
                  <OaIconButton v-else label="停止生成" size="sm" @click="knowledge.cancelStream()"><IconSquare :size="15" /></OaIconButton>
                </div>
              </footer>
            </div>
          </article>
        </template>

        <div v-if="knowledge.streaming && !knowledge.streamingAnswer" class="answer-loading"><OaSpinner :size="20" /> 正在检索资料…</div>
        <div v-else-if="knowledge.error" class="answer-error">{{ knowledge.error }}</div>
      </div>

      <div class="composer">
        <div class="composer__scope">
          <button type="button" @click="scopeModal = true">知识范围　<strong>{{ scope }}</strong></button>
          <OaTag>全部资料</OaTag>
        </div>
        <textarea
          v-model="question"
          rows="2"
          placeholder="输入问题，检索社团知识…"
          @keydown.enter.exact.prevent="send"
        />
        <div class="composer__actions">
          <OaIconButton label="附加文件" variant="outline"><IconPaperclip :size="18" /></OaIconButton>
          <span>Enter 发送 · Shift + Enter 换行</span>
          <OaButton v-if="knowledge.streaming" tone="danger" @click="knowledge.cancelStream()">
            <template #prefix><IconSquare :size="17" /></template>
            停止
          </OaButton>
          <OaButton v-else tone="primary" :disabled="!question.trim()" :loading="knowledge.loading" @click="send">
            <template #prefix><IconSend2 :size="17" /></template>
            发送
          </OaButton>
        </div>
      </div>
    </section>

    <SourceInspector :sources="currentSources" :scope="scope" @change-scope="scopeModal = true" />

    <OaModal :open="scopeModal" title="选择知识范围" description="限定 AI 本次检索的资料空间" @close="scopeModal = false">
      <OaSelect
        v-model="scope"
        label="资料空间"
        :options="[
          { label: '全部资料', value: '全部资料' },
          { label: '活动策划', value: '活动策划' },
          { label: '技术资料', value: '技术资料' },
          { label: '项目文档', value: '项目文档' },
          { label: '会议记录', value: '会议记录' }
        ]"
      />
      <template #footer>
        <OaButton variant="outline" @click="scopeModal = false">取消</OaButton>
        <OaButton tone="primary" @click="scopeModal = false">应用范围</OaButton>
      </template>
    </OaModal>
  </div>
</template>

<style scoped>
.chat-view { display: flex; height: 100%; min-width: 0; background: #fff; user-select: text; -webkit-user-select: text; }
.chat-canvas { position: relative; display: flex; min-width: 460px; flex: 1; flex-direction: column; overflow: hidden; background: #fff; }
.welcome-band { position: relative; min-height: 238px; margin: 18px 18px 0; overflow: hidden; border: 1px solid rgb(230 230 230 / 70%); border-radius: 12px; }
.welcome-band > img { position: absolute; inset: 0; width: 100%; height: 100%; opacity: .7; object-fit: cover; filter: saturate(.82) brightness(1.05); }
.welcome-band > div { position: relative; z-index: 1; padding: 48px 42px 30px; }
.welcome-band__eyebrow { display: inline-flex; align-items: center; gap: 6px; padding: 5px 9px; border: 1px solid rgb(255 255 255 / 70%); border-radius: 99px; background: rgb(255 255 255 / 55%); backdrop-filter: blur(10px); font-size: 11px; }
.welcome-band h1 { margin: 14px 0 7px; font-size: 30px; font-weight: 600; letter-spacing: -1.2px; }
.welcome-band p { margin: 0; color: var(--oa-color-body); font-size: 13px; }
.quick-prompts { display: flex; gap: 7px; margin-top: 24px; overflow-x: auto; }
.quick-prompts button { min-height: 34px; padding: 0 11px; border: 1px solid rgb(255 255 255 / 84%); border-radius: 6px; color: var(--oa-color-body); background: rgb(255 255 255 / 72%); backdrop-filter: blur(12px); cursor: pointer; font-size: 11px; white-space: nowrap; }
.quick-prompts button:hover { color: var(--oa-color-ink); background: rgb(255 255 255 / 92%); }
.chat-scroll { min-height: 0; flex: 1; padding: 20px 24px 12px; overflow-y: auto; }
.question-bubble { width: fit-content; max-width: 72%; margin: 0 0 14px auto; padding: 10px 14px; border: 1px solid #bcd9fa; border-radius: 8px 8px 2px 8px; color: #064f9f; background: #eef6ff; font-size: 13px; line-height: 1.6; word-break: break-word; }
.answer-card { display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 11px; margin-bottom: 24px; }
.answer-card__avatar { display: grid; width: 34px; height: 34px; border-radius: 50%; color: #fff; background: #087cf0; box-shadow: 0 5px 18px rgb(0 112 243 / 18%); place-items: center; }
.answer-card__content { min-width: 0; max-width: 100%; padding: 18px 20px 12px; border: 1px solid var(--oa-color-hairline); border-radius: 10px; background: #fff; overflow: hidden; }
.markdown-body { color: #292929; font-size: 13px; line-height: 1.85; word-break: break-word; overflow-wrap: anywhere; }
.markdown-body :deep(p) { margin: 0 0 12px; word-break: break-word; overflow-wrap: anywhere; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3), .markdown-body :deep(h4) { margin: 18px 0 10px; font-weight: 600; line-height: 1.35; }
.markdown-body :deep(h1) { font-size: 17px; }
.markdown-body :deep(h2) { font-size: 15px; }
.markdown-body :deep(h3) { font-size: 14px; }
.markdown-body :deep(h4) { font-size: 13px; }
.markdown-body :deep(ol), .markdown-body :deep(ul) { margin: 0 0 12px; padding-left: 22px; }
.markdown-body :deep(ol) { list-style: decimal; }
.markdown-body :deep(ul) { list-style: disc; }
.markdown-body :deep(li) { margin: 4px 0; line-height: 1.75; word-break: break-word; overflow-wrap: anywhere; }
.markdown-body :deep(li > p) { margin: 0; }
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(em) { font-style: italic; }
.markdown-body :deep(code) { padding: 2px 6px; border-radius: 4px; background: #f0f1f3; font-family: 'SF Mono', var(--oa-font-mono); font-size: 12px; color: #d63384; word-break: break-all; }
.markdown-body :deep(pre.hljs) { margin: 0 0 12px; padding: 14px 16px; overflow-x: auto; border-radius: 8px; background: #1e293b; }
.markdown-body :deep(pre.hljs code) { padding: 0; background: none; color: #e2e8f0; word-break: normal; font-size: 12px; }
.markdown-body :deep(blockquote) { margin: 0 0 12px; padding: 8px 14px; border-left: 3px solid var(--oa-color-link); color: var(--oa-color-body); background: #f6faff; border-radius: 0 6px 6px 0; }
.markdown-body :deep(table) { width: 100%; margin: 0 0 12px; border-collapse: collapse; }
.markdown-body :deep(th), .markdown-body :deep(td) { padding: 7px 12px; border: 1px solid var(--oa-color-hairline); font-size: 12px; text-align: left; word-break: break-word; }
.markdown-body :deep(th) { background: #fafafa; font-weight: 600; }
.markdown-body :deep(hr) { margin: 14px 0; border: 0; border-top: 1px solid var(--oa-color-hairline); }
.markdown-body :deep(a) { color: var(--oa-color-link); text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
.inline-sources { margin-top: 16px; padding-top: 13px; border-top: 1px solid var(--oa-color-hairline); }
.inline-sources h2 { margin: 0 0 9px; font-size: 11px; }
.inline-sources > div { display: flex; flex-wrap: wrap; gap: 6px; }
.source-tag { cursor: pointer; transition: transform .12s; }
.source-tag:hover { transform: translateY(-1px); }
.answer-card footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 10px; padding-top: 8px; border-top: 1px solid var(--oa-color-hairline); }
.answer-card time { color: var(--oa-color-muted); font-family: var(--oa-font-mono); font-size: 9px; }
.answer-actions { display: flex; gap: 2px; }
.formatting-hint { display: inline-flex; align-items: center; gap: 6px; color: var(--oa-color-muted); font-size: 10px; }
.answer-loading { display: flex; align-items: center; gap: 9px; margin: 16px 0 0 45px; color: var(--oa-color-muted); font-size: 11px; }
.answer-error { margin: 16px 0 0 45px; padding: 10px 14px; border-radius: 8px; color: #c0392b; background: #fdecea; font-size: 11px; }
.composer { margin: 0 18px 18px; padding: 11px; border: 1px solid var(--oa-color-hairline); border-radius: 10px; background: #fff; box-shadow: 0 8px 30px -20px rgb(0 0 0 / 30%); }
.composer__scope { display: flex; align-items: center; gap: 7px; margin-bottom: 6px; }
.composer__scope button { border: 0; color: var(--oa-color-body); background: transparent; cursor: pointer; font-size: 10px; }
.composer textarea { width: 100%; min-height: 48px; padding: 7px 4px; resize: none; border: 0; outline: 0; color: var(--oa-color-ink); background: transparent; font-size: 13px; line-height: 1.5; }
.composer textarea::placeholder { color: #aaa; }
.composer__actions { display: flex; align-items: center; gap: 8px; }
.composer__actions > span { flex: 1; color: var(--oa-color-muted); font-size: 9px; text-align: right; }
@media (max-width: 1240px) {
  .source-inspector { display: none; }
}
@media (max-width: 980px) {
  .conversation-sidebar { display: none; }
}
</style>
