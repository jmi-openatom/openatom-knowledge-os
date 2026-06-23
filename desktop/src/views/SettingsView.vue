<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { IconBolt, IconCheck, IconExternalLink, IconLogout, IconRefresh, IconX } from '@tabler/icons-vue'
import { OaAvatar, OaBadge, OaButton, OaPanel, OaSelect, OaSpinner, OaTag, useToast } from '@openatom/ui'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { useAiStore } from '@/stores/ai'

const auth = useAuthStore()
const ai = useAiStore()
const router = useRouter()
const { pushToast } = useToast()
const isElectron = Boolean(window.openatom)

async function logout() {
  await auth.logout()
  router.replace('/login')
}

async function checkUpdate() {
  await window.openatom?.updater.check()
  pushToast({ title: window.openatom ? '正在检查更新' : '浏览器预览不支持自动更新', tone: 'primary' })
}

async function testAi() {
  const result = await ai.test()
  if (result?.configured) {
    pushToast({ title: 'AI 连接正常', description: `${result.provider} · ${result.model}`, tone: 'success' })
  } else {
    pushToast({ title: 'AI 连接失败', description: '请检查后端 AI 配置或 API Key', tone: 'danger' })
  }
}

const providerLabels: Record<string, string> = {
  mock: '本地规则型回答器（未配置 LLM）',
  deepseek: 'DeepSeek',
  'openai-compatible': 'OpenAI 兼容接口',
}

onMounted(() => ai.load())
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="APPLICATION SETTINGS" title="设置" description="管理账户、客户端和本地行为。" />
    <div class="settings-grid">
      <OaPanel title="账户信息" description="用户信息来自 OpenAtom 统一认证中心">
        <div class="account-row">
          <OaAvatar :name="auth.user?.name || '用户'" :src="auth.user?.avatar" :size="52" status="online" />
          <div><strong>{{ auth.user?.name }}</strong><p>{{ auth.user?.email || '未提供邮箱' }}</p><OaBadge tone="primary">{{ auth.user?.role }}</OaBadge></div>
        </div>
        <div class="permissions">
          <span>当前权限</span>
          <div><OaTag v-for="permission in auth.user?.permissions || ['file:read', 'doc:edit', 'ai:chat']" :key="permission">{{ permission }}</OaTag></div>
        </div>
        <template #footer>
          <OaButton variant="outline" tone="danger" @click="logout"><template #prefix><IconLogout :size="16" /></template>退出登录</OaButton>
        </template>
      </OaPanel>

      <OaPanel title="AI 知识助手" description="检测当前 LLM 配置与可用性">
        <div v-if="ai.loading" class="ai-loading"><OaSpinner :size="20" /> 正在读取配置…</div>
        <template v-else>
          <dl>
            <div><dt>服务提供方</dt><dd>{{ ai.status ? (providerLabels[ai.status.provider] || ai.status.provider) : '—' }}</dd></div>
            <div><dt>模型</dt><dd>{{ ai.status?.model || '—' }}</dd></div>
            <div><dt>接口地址</dt><dd class="mono">{{ ai.status?.baseUrl || '—' }}</dd></div>
            <div><dt>状态</dt>
              <dd>
                <OaBadge v-if="ai.status?.configured" tone="success" dot>已配置</OaBadge>
                <OaBadge v-else tone="warning" dot>未配置</OaBadge>
              </dd>
            </div>
          </dl>
          <div class="ai-actions">
            <OaButton variant="outline" @click="ai.load()"><template #prefix><IconRefresh :size="16" /></template>刷新状态</OaButton>
            <OaButton tone="primary" :loading="ai.testing" @click="testAi"><template #prefix><IconBolt :size="16" /></template>测试连接</OaButton>
          </div>
          <div v-if="ai.testing" class="ai-testing"><OaSpinner :size="16" /> 正在向 LLM 发送测试请求…</div>
          <div v-if="ai.error" class="ai-error"><IconX :size="15" /> {{ ai.error }}</div>
          <p class="ai-hint" v-if="ai.status && !ai.status.configured">
            当前为本地规则型回答器。在启动后端时设置环境变量即可启用 DeepSeek：<code>AI_PROVIDER=deepseek AI_API_KEY=sk-xxx</code>
          </p>
          <p class="ai-hint success" v-else-if="ai.status?.configured">
            <IconCheck :size="14" /> AI 已就绪，首页对话将使用真实 LLM 生成可追溯答案
          </p>
        </template>
      </OaPanel>

      <OaPanel title="客户端" description="自动更新和本地缓存设置">
        <dl>
          <div><dt>当前版本</dt><dd>1.0.0</dd></div>
          <div><dt>运行环境</dt><dd>{{ isElectron ? 'Electron 桌面端' : '浏览器预览' }}</dd></div>
          <div><dt>缓存目录</dt><dd>由操作系统安全管理</dd></div>
        </dl>
        <OaButton variant="outline" @click="checkUpdate"><template #prefix><IconRefresh :size="16" /></template>检查更新</OaButton>
      </OaPanel>

      <OaPanel title="开发者入口" description="组件库和接口文档">
        <div class="developer-links">
          <button @click="router.push('/ui-kit')"><span>组件库预览</span><IconExternalLink :size="16" /></button>
          <button><span>后端 OpenAPI</span><IconExternalLink :size="16" /></button>
        </div>
      </OaPanel>

      <OaPanel title="偏好设置">
        <OaSelect model-value="zh-CN" label="界面语言" :options="[{ label: '简体中文', value: 'zh-CN' }]" />
      </OaPanel>
    </div>
  </div>
</template>

<style scoped>
.page { height: 100%; overflow-y: auto; background: var(--oa-color-canvas-soft); }
.settings-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; max-width: 960px; padding: 24px 32px; }
.account-row { display: flex; align-items: center; gap: 14px; }
.account-row strong { font-size: 15px; }
.account-row p { margin: 4px 0 7px; color: var(--oa-color-muted); font-size: 11px; }
.permissions { margin-top: 18px; }
.permissions > span { color: var(--oa-color-muted); font-size: 11px; }
.permissions > div { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
dl { display: grid; gap: 12px; margin: 0 0 18px; font-size: 11px; }
dl div { display: flex; justify-content: space-between; gap: 20px; }
dt { color: var(--oa-color-muted); }
dd { margin: 0; }
dd.mono { font-family: var(--oa-font-mono); font-size: 10px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 220px; }
.developer-links { display: grid; gap: 6px; }
.developer-links button { display: flex; align-items: center; justify-content: space-between; min-height: 42px; padding: 0 10px; border: 0; border-radius: 6px; background: transparent; cursor: pointer; }
.developer-links button:hover { background: var(--oa-color-canvas-inset); }
.ai-loading { display: flex; align-items: center; gap: 10px; min-height: 80px; color: var(--oa-color-muted); font-size: 12px; }
.ai-actions { display: flex; gap: 8px; margin-bottom: 12px; }
.ai-testing { display: flex; align-items: center; gap: 8px; color: var(--oa-color-muted); font-size: 11px; margin-bottom: 10px; }
.ai-error { display: flex; align-items: center; gap: 6px; color: #c0392b; font-size: 11px; margin-bottom: 10px; }
.ai-hint { margin: 0; padding: 10px 12px; border-radius: 6px; background: var(--oa-color-canvas-inset); color: var(--oa-color-muted); font-size: 11px; line-height: 1.6; }
.ai-hint code { padding: 1px 5px; border-radius: 3px; background: #fff; font-family: var(--oa-font-mono); font-size: 10px; }
.ai-hint.success { display: flex; align-items: center; gap: 6px; color: var(--oa-color-success); background: var(--oa-color-success-soft); }
</style>
