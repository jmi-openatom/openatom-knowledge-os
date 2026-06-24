<script setup lang="ts">
import { useRouter } from 'vue-router'
import { IconAtom2, IconBrandOauth, IconCheck } from '@tabler/icons-vue'
import { OaButton, OaPanel } from '@openatom/ui'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function login() {
  try {
    await auth.login()
    router.replace('/chat')
  } catch {
    // The store exposes a localized error message.
  }
}

function demo() {
  auth.loginDemo()
  router.replace('/chat')
}
</script>

<template>
  <main class="login-view">
    <div class="login-drag-bar" data-tauri-drag-region></div>
    <div class="login-view__mesh" />
    <section class="login-copy">
      <span class="login-brand"><IconAtom2 :size="26" /> JMI-OPENATOM DESK</span>
      <h1>让社团知识，<br>真正可以被 AI 理解。</h1>
      <p>统一管理资料、在线文档与 Wiki，通过可信引用快速获得答案。</p>
      <ul>
        <li><IconCheck :size="16" /> OAuth2 + OIDC 安全登录</li>
        <li><IconCheck :size="16" /> 跨文件检索与 RAG 问答</li>
        <li><IconCheck :size="16" /> 完整权限、版本和审计能力</li>
        <li><IconCheck :size="16" /> 后台管理能力</li>
      </ul>
    </section>
    <OaPanel class="login-card" padding="lg" elevated>
      <span class="login-card__eyebrow">OPENATOM IDENTITY</span>
      <h2>登录工作台</h2>
      <p>将通过系统浏览器打开统一认证中心，并使用 PKCE 安全完成授权。</p>
      <OaButton tone="primary" size="lg" block :loading="auth.loading" @click="login">
        <template #prefix><IconBrandOauth :size="19" /></template>
        {{ auth.loading ? '正在等待浏览器完成授权…' : '使用 JMI-OPENATOM 统一登录' }}
      </OaButton>

      <p v-if="auth.error" class="login-error">
        {{ auth.error }}
        <br><button class="login-retry" type="button" @click="login">点击重试</button>
      </p>
      <br><br>
      <small>继续即表示你同意组织的数据与权限管理规范。</small>
    </OaPanel>
  </main>
</template>

<style scoped>
.login-view { position: relative; display: grid; grid-template-columns: minmax(0, 1.1fr) 430px; gap: 64px; min-height: 100vh; padding: 80px max(7vw, 48px); overflow: hidden; background: #fafafa; place-items: center stretch; }
.login-drag-bar { position: absolute; top: 0; left: 0; right: 0; height: 48px; -webkit-app-region: drag; z-index: 10; }
.login-view__mesh { position: absolute; top: -18%; right: -8%; width: 74%; height: 70%; opacity: .52; background: url('/assets/ai-mesh-background.png') center / cover no-repeat; pointer-events: none; }
.login-copy, .login-card { position: relative; z-index: 1; }
.login-brand { display: flex; align-items: center; gap: 9px; margin-bottom: 44px; font-weight: 600; }
.login-copy h1 { max-width: 720px; margin: 0; font-size: clamp(42px, 5vw, 72px); font-weight: 600; line-height: 1.04; letter-spacing: -3px; }
.login-copy > p { max-width: 600px; margin: 25px 0; color: var(--oa-color-body); font-size: 17px; line-height: 1.75; }
.login-copy ul { display: grid; gap: 12px; margin: 34px 0 0; padding: 0; list-style: none; }
.login-copy li { display: flex; align-items: center; gap: 9px; color: var(--oa-color-body); }
.login-card h2 { margin: 12px 0 7px; font-size: 25px; letter-spacing: -.8px; }
.login-card > p { margin: 0 0 24px; color: var(--oa-color-body); line-height: 1.65; }
.login-card__eyebrow { font-family: var(--oa-font-mono); font-size: 10px; letter-spacing: .8px; }
.demo-link { display: block; margin: 15px auto; border: 0; color: var(--oa-color-link); background: transparent; cursor: pointer; font-size: 12px; }
.login-card small { display: block; color: var(--oa-color-muted); font-size: 10px; text-align: center; }
.login-error { padding: 9px; border-radius: 6px; color: var(--oa-color-danger) !important; background: var(--oa-color-danger-soft); font-size: 11px; line-height: 1.6; }
.login-retry { margin-top: 6px; padding: 4px 12px; border: 1px solid var(--oa-color-danger); border-radius: 4px; color: var(--oa-color-danger); background: transparent; cursor: pointer; font-size: 11px; }
.login-retry:hover { background: var(--oa-color-danger-soft); }
@media (max-width: 900px) {
  .login-view { grid-template-columns: 1fr; align-content: center; padding: 48px 24px; }
  .login-copy { display: none; }
}
</style>
