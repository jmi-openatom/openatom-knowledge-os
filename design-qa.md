# Design QA

- source visual truth path: `docs/assets/ai-cockpit-reference.png`
- implementation screenshot path: `docs/assets/ai-cockpit-implementation.png`
- viewport: `1492 × 1054`
- state: 已登录、AI 指挥舱默认对话、活动策划知识范围
- full-view comparison evidence: `docs/assets/ai-cockpit-comparison.jpg`
- focused region comparison evidence: `docs/assets/ai-cockpit-sidebar-comparison.jpg`

## Findings

本轮没有剩余 P0、P1 或 P2 问题。

- 字体与排版：实现使用 Inter/苹方系统栈，标题、正文和等宽元信息层级与参考图一致；中文换行和操作标签无截断。
- 间距与布局：白色毛玻璃主侧栏、对话列表、中央问答区、右侧来源检查器和底部输入区均保持参考图的四栏结构。
- 颜色与令牌：页面以白色、`#fafafa`、`#171717` 和细灰边框为主，蓝色仅用于选中和关键操作；欢迎区使用独立生成的柔和网格渐变资产。
- 图像与资产：欢迎区使用项目内真实位图 `desktop/public/assets/ai-mesh-background.png`，图标统一使用 `@tabler/icons-vue`，未使用 Emoji、手绘 SVG 或占位图。
- 文案与内容：主要界面文案为自然中文，回答具备引用来源、知识范围、检索片段和原文入口。
- 交互：已验证演示登录、AI 提问、资料库后端数据加载、在线文档加载和组件库弹窗。

## Patches made

- 将最左侧黑色导航改为白色半透明毛玻璃，并保留蓝色活动指示条。
- 调整欢迎区为单一大尺度柔和网格渐变。
- 补齐来源检查器、知识范围弹窗、回答工具栏和可用底部输入器。
- 将资料库、在线文档、搜索和 AI 问答连接到后端 API，并保留离线演示降级。

## Follow-up polish

- P3：正式接入用户头像后，可替换当前演示头像首字母。
- P3：生产知识库数据增加后，右侧片段数量会从演示的 3 条动态扩展。

## Final result

final result: passed
