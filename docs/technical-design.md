# OpenAtom Knowledge OS 技术设计文档

## 1. 架构目标

系统采用 Monorepo：

```text
jmi-openatom-knowledge-os/
├── backend/       Spring Boot 3.5 + Java 17
├── desktop/       Electron + Vue 3 + Vite + Pinia
├── packages/ui/   独立 Vue 基础组件库
├── docker/        MySQL、Redis、MinIO、Elasticsearch
└── docs/          业务、组件和技术文档
```

核心数据链路：

```text
Electron
  → OAuth2 Authorization Code + PKCE
  → Spring Boot API
  → MySQL（元数据、文档、Wiki、审计）
  → MinIO（原始文件）
  → Apache Tika（文本提取）
  → Elasticsearch（全文与模糊检索）
  → LLM（基于检索上下文生成答案）
```

## 2. 认证与安全

### Electron PKCE

1. Electron 主进程生成 `code_verifier`、`code_challenge`、`state`、`nonce`。
2. 使用系统浏览器打开：

```text
https://oauth.jmi-openatom.cn/api/v1/oauth/authorize
```

3. 本机只在登录期间监听 `127.0.0.1:47832/auth/callback`。
4. 校验 `state` 后，由主进程调用 `/oauth/token`。
5. Token 使用 Electron `safeStorage` 加密后保存在应用数据目录。
6. 渲染进程不能读取 Refresh Token，只能通过受限 IPC 获取 Access Token。

OAuth 客户端应注册为公开客户端：

| 字段 | 值 |
| --- | --- |
| Client ID | `openatom-knowledge-desktop` |
| Client Secret | 留空 |
| Redirect URI | `http://127.0.0.1:47832/auth/callback` |
| Grant Types | `authorization_code refresh_token` |
| Scopes | `openid profile email roles permissions` |

### 后端鉴权

后端将 Bearer Token 发送到 `/oauth/introspect`，构造 Spring Security 权限：

- `file:read`
- `file:write`
- `file:delete`
- `doc:edit`
- `ai:chat`
- `admin:manage`

本地 profile 默认开启开发用户；生产 profile 强制真实 Token。

## 3. 数据模型

| 表 | 用途 |
| --- | --- |
| `user_profile` | OIDC 用户镜像和本地业务角色 |
| `knowledge_file` | 文件元数据、解析文本、索引状态 |
| `knowledge_document` | 当前在线文档 |
| `document_version` | 在线文档历史版本 |
| `wiki_page` | Wiki 树和页面内容 |
| `audit_log` | 文件、文档、Wiki 等操作日志 |

Flyway 位于 `backend/src/main/resources/db/migration`。

## 4. 文件处理

1. 校验扩展名和 100MB 大小限制。
2. 原始内容写入本地目录或 MinIO。
3. 文件状态变为 `PARSING`。
4. Apache Tika 提取 Word、Excel、PPT、PDF、Markdown、TXT 文本。
5. 文本写入 MySQL。
6. 将标题、正文和标签写入 Elasticsearch。
7. 状态变为 `READY`；失败时记录可见原因。

本地 profile 使用 `backend/storage`，生产 profile 使用 MinIO。

## 5. 搜索与 RAG

`GET /api/search?q=活动` 使用 Elasticsearch `multi_match`：

- 标题权重 3
- 标签权重 2
- 正文权重 1
- 模糊匹配 `AUTO`
- 返回高亮片段

若 Elasticsearch 不可用，自动降级到 MySQL 正文包含查询。

`POST /api/rag/chat`：

1. 检索前 6 个片段。
2. 构造带编号的上下文。
3. 调用 OpenAI 兼容 `/chat/completions`。
4. 回答中要求使用 `[编号]` 引用。
5. 若未配置模型或请求失败，使用规则型本地回答器。

## 6. 缓存

- 本地 profile：简单内存缓存。
- 生产 profile：Redis。
- 后续可将 Token introspection、Wiki 树和热门搜索结果加入短 TTL 缓存。

## 7. 前端分层

```text
desktop/src/
├── components/  业务组件
├── data/        演示数据
├── layouts/     应用框架
├── services/    API 请求
├── stores/      Pinia 状态
├── views/       页面
└── electron/    主进程与安全 IPC
```

基础组件只能来自 `@openatom/ui`，手动开发规范见 `docs/component-library.md`。

## 8. 可扩展项

- 使用 pgvector、Milvus 或 Elasticsearch dense_vector 增加向量召回。
- 文档协作升级为 Yjs/WebSocket CRDT。
- 文件解析改为消息队列异步任务。
- 对不同社团增加 tenant/club_id 数据隔离。
- 对接对象存储病毒扫描和 DLP。
