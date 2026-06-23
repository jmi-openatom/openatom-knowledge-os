# OpenAtom Knowledge OS API

基础地址：

```text
http://127.0.0.1:8080/api
```

除健康检查外，请求需要：

```http
Authorization: Bearer ACCESS_TOKEN
```

本地 profile 会自动注入管理员身份，便于直接开发。

## 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/auth/me` | 同步并返回当前用户 |

## 文件

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/files` | `file:read` |
| POST | `/files` | `file:write` |
| GET | `/files/{id}/download` | `file:read` |
| DELETE | `/files/{id}` | `file:delete` |

上传使用 `multipart/form-data`：

```text
file: 二进制文件
tags: 活动,指南
```

## AI 问答

```http
POST /rag/chat
Content-Type: application/json
```

```json
{
  "question": "如何策划迎新活动？",
  "scope": "活动策划"
}
```

## 搜索

```http
GET /search?q=活动
```

## 在线文档

| 方法 | 路径 |
| --- | --- |
| GET | `/documents` |
| GET | `/documents/{id}` |
| POST | `/documents` |
| PUT | `/documents/{id}` |
| GET | `/documents/{id}/versions` |

## Wiki

| 方法 | 路径 |
| --- | --- |
| GET | `/wiki` |
| POST | `/wiki` |
| PUT | `/wiki/{id}` |

## 管理

需要 `admin:manage`：

| 方法 | 路径 |
| --- | --- |
| GET | `/admin/members` |
| PATCH | `/admin/members/{id}/role` |
| GET | `/admin/audit-logs` |
