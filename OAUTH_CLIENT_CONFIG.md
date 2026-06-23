# OAuth 客户端配置指南

## 在 OAuth 管理后台注册应用

访问 OAuth 管理后台,进入:**认证应用 -> 新增应用**

### 配置信息

| 字段 | 值 | 说明 |
|------|-----|------|
| **应用名称** | OpenAtom Knowledge Desktop | 用于后台展示 |
| **Client ID** | `openatom-knowledge-desktop` | ⚠️ 必须完全一致 |
| **Client Secret** | (留空) | 桌面应用属于公开客户端,不需要 secret |
| **回调地址** | `http://127.0.0.1:47833/auth/callback` | ️ 必须完全匹配,包括端口 |
| **Scopes** | `openid profile email roles permissions` | 空格分隔 |
| **Grant Types** | `authorization_code refresh_token` | 空格分隔 |
| **状态** | ✅ 启用 | 确保应用处于启用状态 |

### 重要注意事项

1. **Client Secret 必须留空** - Electron 桌面应用属于公开客户端(Public Client),不应保存 client_secret
2. **回调地址精确匹配** - 协议、域名、端口、路径都必须完全一致
   - ✅ `http://127.0.0.1:47833/auth/callback`
   - ❌ `https://127.0.0.1:47833/auth/callback` (协议不同)
   - ❌ `http://localhost:47833/auth/callback` (域名不同)
   - ❌ `http://127.0.0.1:47832/auth/callback` (端口不同)
3. **Grant Types 必须包含** `authorization_code` - 否则无法使用授权码流程

## 验证配置

注册完成后,在浏览器中测试访问:

```
https://oauth.jmi-openatom.cn/api/v1/oauth/authorize?response_type=code&client_id=openatom-knowledge-desktop&redirect_uri=http%3A%2F%2F127.0.0.1%3A47833%2Fauth%2Fcallback&scope=openid+profile+email+roles+permissions&state=test123&nonce=test123&code_challenge=test123&code_challenge_method=S256
```

如果配置正确,应该看到登录页面而不是 400 错误。

## 本地环境变量

确保 `desktop/.env` 文件配置正确:

```bash
# OAuth2/OIDC 配置 (Electron 主进程使用)
OPENATOM_OIDC_ISSUER=https://oauth.jmi-openatom.cn/api/v1
OPENATOM_OIDC_CLIENT_ID=openatom-knowledge-desktop
OPENATOM_OIDC_CALLBACK_PORT=47833
```

## 重启应用

配置完成后,重启 Electron 应用:

```bash
cd desktop
pnpm dev
```
