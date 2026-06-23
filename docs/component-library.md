# OpenAtom UI 组件库

`@openatom/ui` 是 OpenAtom Knowledge OS 的基础组件库。它不绑定业务接口，适合在 Electron、普通 Vue 3 Web 应用或后续管理后台中复用。

## 1. 设计原则

- **内容优先**：先用排版、留白和分隔线建立层级，避免把每个区域都做成卡片。
- **黑白为主**：主文字使用 `#171717`，页面背景使用 `#fafafa`，交互蓝只用于链接、选中和关键动作。
- **轻量层次**：边框为 `#ebebeb`，阴影由多层低透明度阴影组成。
- **中文可读性**：默认字体栈为 Inter、苹方、微软雅黑和系统字体。
- **桌面友好**：组件默认适合鼠标和键盘，同时保留不小于 32px 的交互高度。
- **无障碍**：表单具备标签、错误描述和键盘焦点；图标按钮必须提供 `label`。

## 2. 安装与引入

本项目使用 pnpm workspace，桌面端已经声明：

```json
{
  "dependencies": {
    "@openatom/ui": "workspace:*"
  }
}
```

应用入口引入样式：

```ts
import { createApp } from 'vue'
import '@openatom/ui/styles'
```

按需引入组件：

```vue
<script setup lang="ts">
import { OaButton, OaInput, useToast } from '@openatom/ui'

const { pushToast } = useToast()
</script>
```

## 3. 设计令牌

令牌文件位于 `packages/ui/src/styles/tokens.css`。

### 颜色

| 令牌 | 默认值 | 用途 |
| --- | --- | --- |
| `--oa-color-ink` | `#171717` | 标题、主要文字 |
| `--oa-color-body` | `#4d4d4d` | 正文、次级文字 |
| `--oa-color-muted` | `#888888` | 占位符、元信息 |
| `--oa-color-canvas` | `#ffffff` | 面板、输入框 |
| `--oa-color-canvas-soft` | `#fafafa` | 应用背景 |
| `--oa-color-hairline` | `#ebebeb` | 边框、分隔线 |
| `--oa-color-link` | `#0070f3` | 链接、选中、关键动作 |

### 圆角

| 令牌 | 值 | 推荐场景 |
| --- | --- | --- |
| `--oa-radius-sm` | `6px` | 按钮、输入框 |
| `--oa-radius-md` | `8px` | 导航项、标签 |
| `--oa-radius-lg` | `12px` | 面板、弹窗 |
| `--oa-radius-full` | `999px` | 徽标、胶囊标签 |

### 间距

使用 4px 基础网格：`4 / 8 / 12 / 16 / 24 / 32 / 40 / 48px`，对应 `--oa-space-1` 到 `--oa-space-8`。

## 4. 组件总览

| 组件 | 用途 |
| --- | --- |
| `OaButton` | 主按钮、次按钮、文字按钮 |
| `OaIconButton` | 工具栏和纯图标操作 |
| `OaInput` | 单行输入、搜索框 |
| `OaTextarea` | 多行文本和 AI 提问 |
| `OaSelect` | 原生可访问下拉选择 |
| `OaPanel` | 有边界的独立信息区域 |
| `OaGlassSidebar` | 白色毛玻璃应用侧栏 |
| `OaNavItem` | 主导航或二级导航项 |
| `OaTag` | 标签、筛选条件、知识范围 |
| `OaBadge` | 状态、数量和短元信息 |
| `OaAvatar` | 用户头像和在线状态 |
| `OaModal` | 表单弹窗、确认弹窗 |
| `OaToastViewport` | 全局轻提示容器 |
| `OaEmpty` | 空状态 |
| `OaSpinner` | 局部加载状态 |
| `OaDivider` | 水平、垂直或带文字分隔线 |

## 5. 组件 API

### OaButton

```vue
<OaButton tone="primary" size="md" :loading="saving">
  保存文档
</OaButton>

<OaButton variant="outline">
  取消
</OaButton>
```

| 属性 | 类型 | 默认值 |
| --- | --- | --- |
| `variant` | `solid \| outline \| ghost \| soft` | `solid` |
| `size` | `sm \| md \| lg` | `md` |
| `tone` | `neutral \| primary \| success \| warning \| danger` | `neutral` |
| `loading` | `boolean` | `false` |
| `disabled` | `boolean` | `false` |
| `block` | `boolean` | `false` |

插槽：`default`、`prefix`、`suffix`。

### OaIconButton

```vue
<OaIconButton label="复制回答">
  <IconCopy />
</OaIconButton>
```

`label` 必填，用作 `aria-label` 和原生提示。

### OaInput

```vue
<OaInput
  v-model="keyword"
  label="搜索资料"
  placeholder="输入文件名、标签或正文"
  clearable
>
  <template #prefix><IconSearch /></template>
</OaInput>
```

事件：`update:modelValue`、`clear`。支持 `hint`、`error`、`disabled` 和 `sm/md/lg` 三种尺寸。

### OaTextarea

```vue
<OaTextarea
  v-model="question"
  label="问题"
  placeholder="输入问题，检索社团知识…"
  :maxlength="2000"
/>
```

### OaSelect

```vue
<OaSelect
  v-model="scope"
  label="知识范围"
  :options="[
    { label: '全部资料', value: 'all' },
    { label: '活动策划', value: 'activity' }
  ]"
/>
```

### OaPanel

```vue
<OaPanel title="引用来源" description="本次回答检索到 6 个片段">
  <!-- 内容 -->
  <template #actions>
    <OaButton size="sm" variant="ghost">查看全部</OaButton>
  </template>
</OaPanel>
```

`OaPanel` 只用于真正独立的内容单元；普通列表应优先使用分隔线，而不是嵌套面板。

### OaGlassSidebar

```vue
<OaGlassSidebar :width="88">
  <template #brand>品牌区</template>
  <OaNavItem label="AI 问答" compact active>
    <template #icon><IconMessage /></template>
  </OaNavItem>
  <template #footer>用户区</template>
</OaGlassSidebar>
```

毛玻璃依赖 `backdrop-filter`。背景透明度、模糊和阴影已经封装，不要在业务页面重复实现一套。

### OaNavItem

| 属性 | 说明 |
| --- | --- |
| `label` | 导航名称，必填 |
| `active` | 当前页面状态 |
| `compact` | 图标在上、文字在下，适合窄侧栏 |
| `badge` | 数量或短状态 |

事件：`click`。图标通过 `icon` 插槽传入，项目统一使用 `@tabler/icons-vue`。

### OaTag / OaBadge

两者都支持 `neutral / primary / success / warning / danger`。`OaTag` 表示可交互的分类或范围；`OaBadge` 表示只读状态。

### OaModal

```vue
<OaModal
  :open="open"
  title="上传资料"
  description="支持 Word、Excel、PPT、PDF、Markdown 和 TXT"
  @close="open = false"
>
  <!-- 表单 -->
  <template #footer>
    <OaButton variant="outline" @click="open = false">取消</OaButton>
    <OaButton tone="primary">开始上传</OaButton>
  </template>
</OaModal>
```

按 `Esc` 或点击遮罩可关闭。重要且不可逆操作应额外增加二次确认。

### Toast

应用根节点放置一次：

```vue
<OaToastViewport />
```

任意组件中触发：

```ts
const { pushToast } = useToast()

pushToast({
  title: '上传完成',
  description: '活动策划.docx 已进入解析队列。',
  tone: 'success',
})
```

## 6. 图标规范

项目使用 `@tabler/icons-vue`：

```vue
<script setup lang="ts">
import { IconSearch, IconUpload } from '@tabler/icons-vue'
</script>
```

- 导航图标：20px，描边默认值。
- 工具栏图标：16–18px。
- 不使用 Emoji、文本符号或手绘 SVG 代替正式图标。
- 同一个动作始终使用同一图标。

## 7. 业务开发约定

1. 页面只能从 `@openatom/ui` 引入基础组件，不要复制组件源码。
2. 业务组件放在 `desktop/src/components`，基础组件放在 `packages/ui`。
3. 页面颜色优先使用 CSS 令牌，禁止散落新的十六进制色值。
4. 表单必须展示提交中、成功、失败三类状态。
5. 文件上传、AI 回答、自动保存等异步操作必须有可感知反馈。
6. 任何纯图标按钮都必须传入中文 `label`。
7. 新组件需要同步补充本文件，并在 `/ui-kit` 页面增加可交互示例。

## 8. 目录结构

```text
packages/ui/
├── src/
│   ├── components/
│   ├── composables/
│   ├── styles/
│   │   ├── index.css
│   │   └── tokens.css
│   ├── index.ts
│   └── types.ts
├── package.json
└── tsconfig.json
```

## 9. 手动新增组件

1. 在 `packages/ui/src/components` 新建 `OaXxx.vue`。
2. 在 `packages/ui/src/index.ts` 导出。
3. 使用现有设计令牌，不直接依赖业务 Store 或 API。
4. 为交互状态补齐 hover、focus、disabled、loading。
5. 在本组件文档中记录属性、事件、插槽和示例。
6. 执行：

```bash
pnpm --filter @openatom/ui typecheck
```

