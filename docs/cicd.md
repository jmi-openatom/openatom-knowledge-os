# GitHub CI/CD 配置指南

## 需要配置的 GitHub Variables 和 Secrets

在 GitHub 仓库 **Settings → Secrets and variables → Actions** 中配置以下内容。

### Variables（非敏感信息）

| Name | 示例值 | 说明 |
|------|--------|------|
| `DEPLOY_HOST` | `1.2.3.4` 或 `example.com` | 服务器 IP 或域名 |
| `DEPLOY_USER` | `root` 或 `ubuntu` | SSH 用户名 |
| `DEPLOY_PORT` | `22` | SSH 端口（可选，默认 22） |

### Secrets（敏感信息）

| Name | 示例值 | 说明 |
|------|--------|------|
| `DEPLOY_PASSWORD` | `your-password` | SSH 密码 |

## 服务器要求

- 已安装 Docker 和 Docker Compose
- SSH 可访问
- 开放端口：8080（后端）、9000/9001（MinIO）

## CI/CD 流程

### CI（每次 push/PR）
- `ci.yml`：后端测试、前端 typecheck+build、Docker 镜像构建验证

### Release（推送 `v*` 标签触发）
- `release.yml`：
  1. **后端**：构建 JAR → 构建 Docker 镜像 → SCP 上传到服务器 → SSH 远程部署
  2. **桌面端**：未签名 macOS arm64 DMG + Windows x64/arm64 NSIS 安装包
  3. **发布**：收集所有产物发布到 GitHub Release

## 触发发布

```bash
git tag v1.0.0
git push origin v1.0.0
```

## 服务器端首次准备

1. 安装 Docker 和 Docker Compose
2. 创建部署目录：`mkdir -p ~/openatom-deploy`
3. 首次部署由 CI/CD 自动完成（SCP 上传 + SSH 执行 deploy.sh）

## Electron 打包产物

| 平台 | 架构 | 格式 | 文件 |
|------|------|------|------|
| macOS | arm64 | DMG | `OpenAtom Knowledge OS-1.0.0-mac-arm64.dmg` |
| Windows | x64 | NSIS | `OpenAtom Knowledge OS-1.0.0-win-x64.exe` |
| Windows | arm64 | NSIS | `OpenAtom Knowledge OS-1.0.0-win-arm64.exe` |

> macOS 未配置 Apple Developer 签名证书，因此不发布 ZIP 和 `latest-mac.yml`，客户端也不会自动检查更新。macOS 用户通过 Release 下载 DMG 手动覆盖安装。Windows 仍可使用 electron-updater 自动更新；未签名时会提示 SmartScreen 警告。
