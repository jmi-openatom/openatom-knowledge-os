<script setup lang="ts">
import { ref } from 'vue'
import { IconBell, IconPlus, IconSearch, IconUpload } from '@tabler/icons-vue'
import {
  OaAvatar,
  OaBadge,
  OaButton,
  OaEmpty,
  OaIconButton,
  OaInput,
  OaModal,
  OaPanel,
  OaSelect,
  OaTag,
  OaTextarea,
  useToast,
} from '@openatom/ui'
import PageHeader from '@/components/PageHeader.vue'

const text = ref('')
const textarea = ref('')
const select = ref('all')
const modal = ref(false)
const { pushToast } = useToast()
</script>

<template>
  <div class="page">
    <PageHeader eyebrow="@OPENATOM/UI" title="组件库预览" description="交互示例与完整开发规范见 docs/component-library.md。" />
    <div class="kit-grid">
      <OaPanel title="按钮" description="关键动作、次级动作和工具操作">
        <div class="row">
          <OaButton tone="primary"><template #prefix><IconPlus :size="16" /></template>主要按钮</OaButton>
          <OaButton>默认按钮</OaButton>
          <OaButton variant="outline">次级按钮</OaButton>
          <OaButton variant="ghost">文字按钮</OaButton>
          <OaButton loading>提交中</OaButton>
          <OaIconButton label="通知" variant="outline"><IconBell :size="17" /></OaIconButton>
        </div>
      </OaPanel>
      <OaPanel title="表单">
        <div class="form-grid">
          <OaInput v-model="text" label="资料名称" placeholder="输入资料名称" clearable><template #prefix><IconSearch :size="17" /></template></OaInput>
          <OaSelect v-model="select" label="知识范围" :options="[{ label: '全部资料', value: 'all' }, { label: '活动策划', value: 'activity' }]" />
          <OaTextarea v-model="textarea" label="说明" placeholder="输入说明" :maxlength="200" />
        </div>
      </OaPanel>
      <OaPanel title="状态与标签">
        <div class="row"><OaTag>默认标签</OaTag><OaTag tone="primary">活动策划</OaTag><OaTag tone="success">已索引</OaTag><OaTag tone="warning">解析中</OaTag><OaBadge tone="danger" dot>失败</OaBadge><OaAvatar name="张同学" status="online" /></div>
      </OaPanel>
      <OaPanel title="反馈组件">
        <div class="row">
          <OaButton variant="outline" @click="modal = true">打开弹窗</OaButton>
          <OaButton variant="outline" @click="pushToast({ title: '操作成功', description: '组件库通知工作正常。', tone: 'success' })">显示 Toast</OaButton>
        </div>
        <OaEmpty title="暂无组件数据" description="空状态可以插入图标和操作按钮。">
          <OaButton size="sm"><template #prefix><IconUpload :size="15" /></template>上传资料</OaButton>
        </OaEmpty>
      </OaPanel>
    </div>
    <OaModal :open="modal" title="组件库弹窗" description="支持标题、说明、正文和底部操作区域" @close="modal = false">
      这里放置业务表单或确认内容。
      <template #footer><OaButton variant="outline" @click="modal = false">取消</OaButton><OaButton tone="primary" @click="modal = false">确认</OaButton></template>
    </OaModal>
  </div>
</template>

<style scoped>
.page { height: 100%; overflow-y: auto; background: var(--oa-color-canvas-soft); }
.kit-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; padding: 24px 32px 40px; }
.row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.form-grid { display: grid; gap: 14px; }
</style>
